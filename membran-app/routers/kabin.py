"""
ONLINE KABİN — müşteri dolabını tarayıcıda tasarlar, sipariş verir; yönetici onaylar;
onaylı satır .tkabin olarak TitaniumCam'e (Kabin → Dosya → Aç) aktarılır.

Hesabın tamamı TitaniumCam motorundadır (kabin_servis). Bu modül:
  - taslağı sunucuda tutar (tarayıcı yalnız işlem yollar: "şu noktadaki göze raf ekle"),
  - her işlemden önce sürüm saklar (en az 50 geri alma),
  - alan yetkilerini (müşteri / yönetici) ayardan uygular,
  - doktor hatası olan dolabın siparişe çevrilmesini engeller,
  - siparişte .tkabin, motor sürümü ve tanım sürümünü dondurur.
"""
import json
import os
import time
from collections import defaultdict, deque

from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse, Response
from fastapi.templating import Jinja2Templates

import db
import kabin_istemci as KI
from config import BASE_DIR
from routers.magaza import current_customer

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))

YONETICI = "@yonetici"
GERI_ALMA = 50

# ── Alan yetkileri (varsayılan) — yönetici /membrane/kabin/ayar'dan değiştirir ──
# M: müşteri ve yönetici · Y: yalnız yönetici. Aşama 2/3 alanları burada yok.
VARSAYILAN_YETKI = {
    # Genel
    "en": "M", "boy": "M", "derinlik": "M", "adet": "M", "aciklama": "M", "kabinno": "M",
    "ayaktipi": "M", "ayakyuksekligi": "M",
    "baza.on": "M", "baza.arka": "M", "baza.on_geriye": "M", "baza.arka_geriye": "M",
    # Gövde
    "paneller.sol": "M", "paneller.sag": "M", "paneller.ust": "M", "paneller.alt": "M",
    "solust": "M", "sagust": "M", "solalt": "M", "sagalt": "M",
    "govde.malzeme": "M", "govde.kalinlik": "M", "govde.bant_on": "M",
    "oto_bant": "M", "oto_bant_arka": "M",
    # Bağlantı
    "baglanti_kodu": "Y", "baglanti_sablonu": "Y", "donanim": "Y", "mentesesiz": "Y",
    # Boşluk
    "k_soldanbosluk": "Y", "k_sagdanbosluk": "Y", "k_usttenbosluk": "Y", "k_alttanbosluk": "Y",
    "yanyanakapakarasi": "Y", "ustustekapakarasi": "Y", "icten_kapak_boslugu": "Y",
    "k_tacaltibosluk": "Y", "cakarkbosluk": "Y", "garkgirmepayi": "Y",
    # Kapama
    **{f"yan_kapama.{f}": "M" for f in ("sol", "sag", "zemine", "tavana", "tavan", "on_tasma",
                                        "arka_tasma", "ust_tasma", "malzeme", "bant_on", "bol",
                                        "bol_yukseklik")},
    **{f"dolgu.{f}": "M" for f in ("ust", "ust_konum", "ust_boyut", "sol", "sag", "yan_boyut",
                                   "braket", "malzeme")},
    # Ekle
    **{f"ekle.{t}": "M" for t in ("dikeybolme", "yataybolme", "raf", "arkalik", "dikeykayit",
                                  "yataykayit", "kapak", "cekmece", "usttabla")},
    # Parça işleme
    "duzenle.sil": "M", "parca.malzeme": "M", "parca.bant": "M", "parca.damar": "M",
    "parca.onek": "Y", "parca.arkaek": "Y",
}


def _ayar(anahtar, varsayilan):
    r = db.one("SELECT deger FROM kabin_ayar WHERE anahtar=?", (anahtar,))
    if not r:
        return varsayilan
    try:
        return json.loads(r["deger"])
    except ValueError:
        return varsayilan


def _ayar_yaz(anahtar, deger):
    db.execute("INSERT INTO kabin_ayar (anahtar, deger) VALUES (?,?) "
               "ON CONFLICT(anahtar) DO UPDATE SET deger=excluded.deger",
               (anahtar, json.dumps(deger, ensure_ascii=False)))


def yetkiler():
    y = dict(VARSAYILAN_YETKI)
    for k, v in (_ayar("yetkiler", {}) or {}).items():
        if k in y and v in ("M", "Y"):
            y[k] = v
    return y


def izinler(rol):
    y = yetkiler()
    return {k for k, v in y.items() if rol == "yonetici" or v == "M"}


def sinirlar(sablon):
    """Müşteri ölçü aralığı (şablon başına). Motor 50–10000 kabul eder."""
    s = {"en": [50, 10000], "boy": [50, 10000], "derinlik": [50, 10000]}
    for f, ab in ((_ayar(f"sinir.{sablon}", {}) or {}).items()):
        if f in s and isinstance(ab, list) and len(ab) == 2:
            s[f] = [float(ab[0]), float(ab[1])]
    return s


# ── basit hız sınırı (kullanıcı başına dakikada 240 işlem) ──
_HIZ = defaultdict(deque)


def _hiz_asildi(sahip):
    q, simdi = _HIZ[sahip], time.time()
    while q and simdi - q[0] > 60:
        q.popleft()
    if len(q) >= 240:
        return True
    q.append(simdi)
    return False


# ══════════════════════════════════════════════════════════════════════════
# Taslak + sürüm (geri alma)
# ══════════════════════════════════════════════════════════════════════════
def _taslak(tid, sahip):
    t = db.one("SELECT * FROM kabin_taslak WHERE id=?", (tid,))
    if not t or t["musteri"] != sahip:
        return None
    return t


def _surum_ekle(tid, imlec, kabin_json):
    """Yeni hal: ileri dalını sil, imlec+1'e yaz, 50'den eskiyi buda."""
    db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=? AND sira>?", (tid, imlec))
    yeni = imlec + 1
    db.execute("INSERT INTO kabin_taslak_surum (taslak_id, sira, kabin_json) VALUES (?,?,?)",
               (tid, yeni, kabin_json))
    db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=? AND sira<?", (tid, yeni - GERI_ALMA))
    return yeni


def _kaydet(t, view, surum=True):
    kj = json.dumps(view["kabin"], ensure_ascii=False)
    ozet = json.dumps({**view["ozet"], "en": view["govde"]["en"], "boy": view["govde"]["boy"],
                       "derinlik": view["govde"]["derinlik"], "adet": view["ozellik"]["adet"]})
    imlec = _surum_ekle(t["id"], t["imlec"], kj) if surum else t["imlec"]
    db.execute("UPDATE kabin_taslak SET kabin_json=?, imlec=?, hata_var=?, ozet_json=?, "
               "updated_at=datetime('now') WHERE id=?",
               (kj, imlec, 1 if view["hata_var"] else 0, ozet, t["id"]))
    t.update(kabin_json=kj, imlec=imlec)


def _durum(t):
    tid, im = t["id"], t["imlec"]
    geri = db.one("SELECT 1 FROM kabin_taslak_surum WHERE taslak_id=? AND sira=?", (tid, im - 1))
    ileri = db.one("SELECT 1 FROM kabin_taslak_surum WHERE taslak_id=? AND sira=?", (tid, im + 1))
    t2 = db.one("SELECT ad, sepette, panel_taban FROM kabin_taslak WHERE id=?", (tid,))
    return {"geri": bool(geri), "ileri": bool(ileri), "ad": t2["ad"], "sepette": bool(t2["sepette"]),
            "panel": bool(t2["panel_taban"])}


def _cevap(view, t, rol):
    out = {k: v for k, v in view.items() if k != "kabin"}
    if rol == "yonetici":
        out["kabin"] = view["kabin"]
    out["durum"] = _durum(t)
    return out


def _hata(e):
    return JSONResponse({"error": e.mesaj}, status_code=e.kod if e.kod in (413, 422, 429, 503, 504) else 422)


def _sinir_kontrol(t, islemler, rol):
    if rol == "yonetici":
        return
    s = sinirlar(t["sablon"])
    for isl in islemler:
        if not isinstance(isl, dict):
            continue
        kaynak = isl if isl.get("tip") == "olcu" else (isl.get("alanlar") or {}) if isl.get("tip") == "ozellik" else {}
        for f in ("en", "boy", "derinlik"):
            if f in kaynak:
                try:
                    v = float(str(kaynak[f]).replace(",", "."))
                except (TypeError, ValueError):
                    raise KI.ServisHatasi(f"{f}: sayı bekleniyor")
                lo, hi = s[f]
                if not (lo <= v <= hi):
                    ad = {"en": "En", "boy": "Boy", "derinlik": "Derinlik"}[f]
                    raise KI.ServisHatasi(f"{ad} {lo:g}–{hi:g} mm arasında olmalı")


async def _goster(t, rol):
    return await KI.islem(json.loads(t["kabin_json"]), [{"tip": "goster"}], rol, izinler(rol))


# ══════════════════════════════════════════════════════════════════════════
# Editör uçları (müşteri: /kabin/t/..., yönetici: /membrane/kabin/t/...)
# ══════════════════════════════════════════════════════════════════════════
def _sahip(request, rol):
    if rol == "yonetici":
        return YONETICI if getattr(request.state, "user", None) else None
    c = current_customer(request)
    return c["username"] if c else None


async def _editor_sayfa(request, tid, rol, taban):
    sahip = _sahip(request, rol)
    if not sahip:
        return RedirectResponse("/magaza/giris?next=/kabin", status_code=303)
    t = _taslak(tid, sahip)
    if not t:
        return RedirectResponse("/membrane/kabin" if rol == "yonetici" else "/kabin", status_code=303)
    try:
        bil = await KI.bilgi()
    except KI.ServisHatasi as e:
        return HTMLResponse(f"<p style='font-family:sans-serif;padding:2rem'>{e.mesaj}. "
                            f"<a href='/kabin'>Geri dön</a></p>", status_code=503)
    satir = None
    if t["siparis_satir_id"]:
        satir = db.one("SELECT l.*, o.order_no, o.status FROM cabinet_order_lines l "
                       "JOIN cabinet_orders o ON o.id=l.order_id WHERE l.id=?", (t["siparis_satir_id"],))
    return templates.TemplateResponse(request, "kabin_editor.html", {
        "request": request, "t": t, "rol": rol, "taban": taban, "bilgi": bil,
        "izinler": sorted(izinler(rol)), "sinir": sinirlar(t["sablon"]), "satir": satir,
        "geri_url": (f"/membrane/kabin/siparis/{satir['order_id']}" if satir else "/membrane/kabin")
        if rol == "yonetici" else "/kabin",
    })


async def _veri(request, tid, rol):
    sahip = _sahip(request, rol)
    t = _taslak(tid, sahip) if sahip else None
    if not t:
        return JSONResponse({"error": "Taslak bulunamadı"}, status_code=404)
    try:
        view = await _goster(t, rol)
    except KI.ServisHatasi as e:
        return _hata(e)
    return JSONResponse(_cevap(view, t, rol))


async def _islem(request, tid, rol):
    sahip = _sahip(request, rol)
    t = _taslak(tid, sahip) if sahip else None
    if not t:
        return JSONResponse({"error": "Taslak bulunamadı"}, status_code=404)
    if _hiz_asildi(sahip):
        return JSONResponse({"error": "Çok hızlı işlem, biraz bekleyin"}, status_code=429)
    ham = await request.body()
    if len(ham) > 100_000:
        return JSONResponse({"error": "İstek çok büyük"}, status_code=413)
    try:
        govde = json.loads(ham)
    except ValueError:
        return JSONResponse({"error": "Geçersiz istek"}, status_code=400)
    if not isinstance(govde, dict):
        return JSONResponse({"error": "Geçersiz istek"}, status_code=400)
    isl = govde.get("islem")
    islemler = isl if isinstance(isl, list) else [isl]
    if any(not isinstance(i, dict) or i.get("tip") in ("yeni", "yeni_uid") for i in islemler):
        return JSONResponse({"error": "Geçersiz işlem"}, status_code=400)
    onizle = bool(govde.get("onizle"))
    panel = bool(govde.get("panel"))
    try:
        _sinir_kontrol(t, islemler, rol)
        if panel:                         # canlı panel: açılış hali + panel değerleri, sürüm birikmez
            if not t["panel_taban"]:
                db.execute("UPDATE kabin_taslak SET panel_taban=kabin_json WHERE id=?", (t["id"],))
                t["panel_taban"] = t["kabin_json"]
            taban = json.loads(t["panel_taban"])
        else:
            taban = json.loads(t["kabin_json"])
        view = await KI.islem(taban, islemler, rol, izinler(rol))
    except KI.ServisHatasi as e:
        return _hata(e)
    if not onizle:
        _kaydet(t, view, surum=not panel)
    return JSONResponse(_cevap(view, t, rol))


async def _gecmis(request, tid, rol, yon):
    sahip = _sahip(request, rol)
    t = _taslak(tid, sahip) if sahip else None
    if not t:
        return JSONResponse({"error": "Taslak bulunamadı"}, status_code=404)
    hedef = t["imlec"] + (-1 if yon == "geri" else 1)
    s = db.one("SELECT kabin_json FROM kabin_taslak_surum WHERE taslak_id=? AND sira=?", (t["id"], hedef))
    if not s:
        return JSONResponse({"error": "Geri alınacak adım yok" if yon == "geri" else "İleri alınacak adım yok"},
                            status_code=422)
    db.execute("UPDATE kabin_taslak SET kabin_json=?, imlec=?, panel_taban='' WHERE id=?",
               (s["kabin_json"], hedef, t["id"]))
    t.update(kabin_json=s["kabin_json"], imlec=hedef)
    try:
        view = await _goster(t, rol)
    except KI.ServisHatasi as e:
        return _hata(e)
    _kaydet(t, view, surum=False)
    return JSONResponse(_cevap(view, t, rol))


async def _panel(request, tid, rol, ne):
    """Kabin özellikleri paneli: ac (açılış halini sakla) · tamam (tek geri alma adımı) · iptal."""
    sahip = _sahip(request, rol)
    t = _taslak(tid, sahip) if sahip else None
    if not t:
        return JSONResponse({"error": "Taslak bulunamadı"}, status_code=404)
    if ne == "ac":
        db.execute("UPDATE kabin_taslak SET panel_taban=kabin_json WHERE id=?", (t["id"],))
        return JSONResponse({"ok": True})
    if ne == "iptal" and t["panel_taban"]:
        db.execute("UPDATE kabin_taslak SET kabin_json=panel_taban, panel_taban='' WHERE id=?", (t["id"],))
        t["kabin_json"] = t["panel_taban"]
    elif ne == "tamam" and t["panel_taban"]:
        if t["kabin_json"] != t["panel_taban"]:
            imlec = _surum_ekle(t["id"], t["imlec"], t["kabin_json"])
            db.execute("UPDATE kabin_taslak SET imlec=? WHERE id=?", (imlec, t["id"]))
            t["imlec"] = imlec
        db.execute("UPDATE kabin_taslak SET panel_taban='' WHERE id=?", (t["id"],))
    try:
        view = await _goster(t, rol)
    except KI.ServisHatasi as e:
        return _hata(e)
    _kaydet(t, view, surum=False)
    return JSONResponse(_cevap(view, t, rol))


async def _ad(request, tid, rol):
    sahip = _sahip(request, rol)
    t = _taslak(tid, sahip) if sahip else None
    if not t:
        return JSONResponse({"error": "Taslak bulunamadı"}, status_code=404)
    try:
        ad = str((await request.json()).get("ad", ""))[:80].strip()
    except ValueError:
        ad = ""
    db.execute("UPDATE kabin_taslak SET ad=? WHERE id=?", (ad or "Dolap", t["id"]))
    return JSONResponse({"ok": True, "ad": ad or "Dolap"})


def _editor_rotalari(on_ek, rol):
    taban = on_ek + "/t/{tid}"

    @router.get(taban, response_class=HTMLResponse, name=f"editor_{rol}")
    async def _e(request: Request, tid: int):
        return await _editor_sayfa(request, tid, rol, on_ek + f"/t/{tid}")

    @router.get(taban + "/veri", name=f"veri_{rol}")
    async def _v(request: Request, tid: int):
        return await _veri(request, tid, rol)

    @router.post(taban + "/islem", name=f"islem_{rol}")
    async def _i(request: Request, tid: int):
        return await _islem(request, tid, rol)

    @router.post(taban + "/geri", name=f"geri_{rol}")
    async def _g(request: Request, tid: int):
        return await _gecmis(request, tid, rol, "geri")

    @router.post(taban + "/ileri", name=f"ileri_{rol}")
    async def _il(request: Request, tid: int):
        return await _gecmis(request, tid, rol, "ileri")

    @router.post(taban + "/panel/{ne}", name=f"panel_{rol}")
    async def _p(request: Request, tid: int, ne: str):
        if ne not in ("ac", "tamam", "iptal"):
            return JSONResponse({"error": "Geçersiz"}, status_code=400)
        return await _panel(request, tid, rol, ne)

    @router.post(taban + "/ad", name=f"ad_{rol}")
    async def _a(request: Request, tid: int):
        return await _ad(request, tid, rol)


_editor_rotalari("/kabin", "musteri")
_editor_rotalari("/membrane/kabin", "yonetici")


# ══════════════════════════════════════════════════════════════════════════
# Müşteri: taslaklar, sepet, sipariş
# ══════════════════════════════════════════════════════════════════════════
def _musteri_ctx(request, cust, **kw):
    from routers.magaza import _ctx
    return _ctx(request, cust, **kw)


@router.get("/kabin", response_class=HTMLResponse)
async def kabin_ana(request: Request, hata: str = ""):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris?next=/kabin", status_code=303)
    u = cust["username"]
    taslaklar = db.query("SELECT * FROM kabin_taslak WHERE musteri=? ORDER BY updated_at DESC", (u,))
    for t in taslaklar:
        t["ozet"] = json.loads(t["ozet_json"] or "{}")
    siparisler = db.query("SELECT o.*, (SELECT COUNT(*) FROM cabinet_order_lines l WHERE l.order_id=o.id) satir "
                          "FROM cabinet_orders o WHERE musteri=? ORDER BY id DESC", (u,))
    return templates.TemplateResponse(request, "kabin_ana.html", _musteri_ctx(
        request, cust, taslaklar=taslaklar, sepet=[t for t in taslaklar if t["sepette"]],
        siparisler=siparisler, hata=hata))


@router.get("/kabin/yeni", response_class=HTMLResponse)
async def kabin_yeni_sayfa(request: Request):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris?next=/kabin/yeni", status_code=303)
    try:
        bil = await KI.bilgi()
    except KI.ServisHatasi as e:
        return RedirectResponse(f"/kabin?hata={e.mesaj}", status_code=303)
    return templates.TemplateResponse(request, "kabin_yeni.html", _musteri_ctx(
        request, cust, sablonlar=bil["kabin_sablonlari"]))


@router.post("/kabin/yeni")
async def kabin_yeni(request: Request, sablon: str = Form(...)):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris?next=/kabin/yeni", status_code=303)
    try:
        bil = await KI.bilgi()
        sab = next((s for s in bil["kabin_sablonlari"] if s["ad"] == sablon), None)
        if not sab:
            raise KI.ServisHatasi("Şablon bulunamadı")
        s = sinirlar(sablon)
        olc = {f: min(max(float(sab[f]), s[f][0]), s[f][1]) for f in ("en", "boy", "derinlik")}
        view = await KI.islem(None, [{"tip": "yeni", "sablon": sablon, **olc}], "musteri", izinler("musteri"))
    except KI.ServisHatasi as e:
        return RedirectResponse(f"/kabin?hata={e.mesaj}", status_code=303)
    tid = db.execute("INSERT INTO kabin_taslak (musteri, ad, sablon, kabin_json) VALUES (?,?,?,?)",
                     (cust["username"], sablon, sablon, json.dumps(view["kabin"], ensure_ascii=False)))
    t = db.one("SELECT * FROM kabin_taslak WHERE id=?", (tid,))
    db.execute("INSERT INTO kabin_taslak_surum (taslak_id, sira, kabin_json) VALUES (?,?,?)",
               (tid, 0, t["kabin_json"]))
    _kaydet(t, view, surum=False)
    return RedirectResponse(f"/kabin/t/{tid}", status_code=303)


@router.post("/kabin/t/{tid}/kopyala")
async def kabin_kopyala(request: Request, tid: int):
    cust = current_customer(request)
    t = _taslak(tid, cust["username"]) if cust else None
    if not t:
        return RedirectResponse("/kabin", status_code=303)
    try:                                  # kopyada uid yenilenir
        view = await KI.islem(json.loads(t["kabin_json"]), [{"tip": "yeni_uid"}], "musteri", izinler("musteri"))
    except KI.ServisHatasi as e:
        return RedirectResponse(f"/kabin?hata={e.mesaj}", status_code=303)
    yid = db.execute("INSERT INTO kabin_taslak (musteri, ad, sablon, kabin_json) VALUES (?,?,?,?)",
                     (t["musteri"], (t["ad"] or "Dolap") + " (kopya)", t["sablon"],
                      json.dumps(view["kabin"], ensure_ascii=False)))
    y = db.one("SELECT * FROM kabin_taslak WHERE id=?", (yid,))
    db.execute("INSERT INTO kabin_taslak_surum (taslak_id, sira, kabin_json) VALUES (?,?,?)",
               (yid, 0, y["kabin_json"]))
    _kaydet(y, view, surum=False)
    return RedirectResponse(f"/kabin/t/{yid}", status_code=303)


@router.post("/kabin/t/{tid}/sil")
async def kabin_sil(request: Request, tid: int):
    cust = current_customer(request)
    t = _taslak(tid, cust["username"]) if cust else None
    if t:
        db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=?", (tid,))
        db.execute("DELETE FROM kabin_taslak WHERE id=?", (tid,))
    return RedirectResponse("/kabin", status_code=303)


@router.post("/kabin/t/{tid}/sepet")
async def kabin_sepete(request: Request, tid: int):
    """Sepete ekle: adet uygulanır, doktor hatası varsa reddedilir."""
    cust = current_customer(request)
    t = _taslak(tid, cust["username"]) if cust else None
    if not t:
        return JSONResponse({"error": "Taslak bulunamadı"}, status_code=404)
    try:
        adet = int((await request.json()).get("adet", 1))
    except (ValueError, TypeError, AttributeError):
        return JSONResponse({"error": "Adet 1–999 olmalı"}, status_code=422)
    if not 1 <= adet <= 999:
        return JSONResponse({"error": "Adet 1–999 olmalı"}, status_code=422)
    try:
        view = await KI.islem(json.loads(t["kabin_json"]),
                              [{"tip": "ozellik", "alanlar": {"adet": adet}}], "musteri", izinler("musteri"))
    except KI.ServisHatasi as e:
        return _hata(e)
    _kaydet(t, view, surum=True)
    if view["hata_var"]:
        return JSONResponse({"error": "Dolapta hata var, sepete eklenemez: " +
                             "; ".join(b["mesaj"] for b in view["doktor"] if b["seviye"] == "hata")},
                            status_code=422)
    db.execute("UPDATE kabin_taslak SET sepette=1 WHERE id=?", (tid,))
    return JSONResponse({"ok": True})


@router.post("/kabin/t/{tid}/sepetten")
async def kabin_sepetten(request: Request, tid: int):
    cust = current_customer(request)
    t = _taslak(tid, cust["username"]) if cust else None
    if t:
        db.execute("UPDATE kabin_taslak SET sepette=0 WHERE id=?", (tid,))
    return RedirectResponse("/kabin", status_code=303)


def _tkabin(kabin):
    return {"format": "tkabin", "schema": 1, "kabin": kabin}


def _satir_alanlari(view):
    k = view["kabin"]
    return {"qty": int(k.get("adet", 1)), "width_mm": float(k["en"]), "height_mm": float(k["boy"]),
            "depth_mm": float(k["derinlik"]), "cabinet_no": k.get("kabinno", "") or "",
            "tkabin": json.dumps(_tkabin(k), ensure_ascii=False),
            "engine_version": view["motor_surumu"], "definitions_version": view["tanim_surumu"],
            "ozet_json": json.dumps(view["ozet"])}


@router.post("/kabin/siparis")
async def kabin_siparis(request: Request, proje: str = Form(""), note: str = Form("")):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris?next=/kabin", status_code=303)
    sepet = db.query("SELECT * FROM kabin_taslak WHERE musteri=? AND sepette=1 ORDER BY id", (cust["username"],))
    if not sepet:
        return RedirectResponse("/kabin?hata=Sepet boş", status_code=303)
    satirlar = []
    try:
        for t in sepet:                   # her dolap motordan yeniden geçer; satır başına yeni uid
            view = await KI.islem(json.loads(t["kabin_json"]), [{"tip": "yeni_uid"}], "musteri",
                                  izinler("musteri"))
            if view["hata_var"]:
                return RedirectResponse(f"/kabin?hata={t['ad']}: dolapta hata var, sipariş verilemez",
                                        status_code=303)
            satirlar.append((t, view))
    except KI.ServisHatasi as e:
        return RedirectResponse(f"/kabin?hata={e.mesaj}", status_code=303)
    oid = db.execute("INSERT INTO cabinet_orders (musteri, musteri_ad, proje, note) VALUES (?,?,?,?)",
                     (cust["username"], cust["full_name"] or cust["username"], proje.strip()[:120],
                      note.strip()[:1000]))
    db.execute("UPDATE cabinet_orders SET order_no=? WHERE id=?", (f"KB{100000 + oid}", oid))
    for i, (t, view) in enumerate(satirlar, 1):
        a = _satir_alanlari(view)
        db.execute("INSERT INTO cabinet_order_lines (order_id, line_no, cabinet_no, name, qty, width_mm, "
                   "height_mm, depth_mm, tkabin, engine_version, definitions_version, ozet_json) "
                   "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                   (oid, i, a["cabinet_no"] or f"K-{i}", t["ad"] or t["sablon"], a["qty"], a["width_mm"],
                    a["height_mm"], a["depth_mm"], a["tkabin"], a["engine_version"],
                    a["definitions_version"], a["ozet_json"]))
        db.execute("UPDATE kabin_taslak SET sepette=0 WHERE id=?", (t["id"],))
    return RedirectResponse(f"/kabin/siparis/{oid}", status_code=303)


@router.get("/kabin/siparis/{oid}", response_class=HTMLResponse)
async def kabin_siparis_goster(request: Request, oid: int):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris?next=/kabin", status_code=303)
    o = db.one("SELECT * FROM cabinet_orders WHERE id=? AND musteri=?", (oid, cust["username"]))
    if not o:
        return RedirectResponse("/kabin", status_code=303)
    lines = db.query("SELECT id, line_no, cabinet_no, name, qty, width_mm, height_mm, depth_mm, ozet_json "
                     "FROM cabinet_order_lines WHERE order_id=? ORDER BY line_no", (oid,))
    for l in lines:
        l["ozet"] = json.loads(l["ozet_json"] or "{}")
    return templates.TemplateResponse(request, "kabin_siparis.html", _musteri_ctx(
        request, cust, o=o, lines=lines))


# ══════════════════════════════════════════════════════════════════════════
# Yönetici: onay ekranı, satır düzenleme, .tkabin
# ══════════════════════════════════════════════════════════════════════════
DURUM_AD = {"bekliyor": "Onay bekliyor", "onayli": "Onaylı", "reddedildi": "Reddedildi"}


@router.get("/membrane/kabin", response_class=HTMLResponse)
async def y_liste(request: Request, durum: str = ""):
    sql = ("SELECT o.*, (SELECT COUNT(*) FROM cabinet_order_lines l WHERE l.order_id=o.id) satir, "
           "(SELECT COALESCE(SUM(qty),0) FROM cabinet_order_lines l WHERE l.order_id=o.id) dolap "
           "FROM cabinet_orders o")
    args = ()
    if durum in DURUM_AD:
        sql += " WHERE status=?"
        args = (durum,)
    orders = db.query(sql + " ORDER BY id DESC", args)
    return templates.TemplateResponse(request, "kabin_y_liste.html", {
        "request": request, "orders": orders, "durum": durum, "DURUM_AD": DURUM_AD})


@router.get("/membrane/kabin/siparis/{oid}", response_class=HTMLResponse)
async def y_siparis(request: Request, oid: int, hata: str = ""):
    o = db.one("SELECT * FROM cabinet_orders WHERE id=?", (oid,))
    if not o:
        return RedirectResponse("/membrane/kabin", status_code=303)
    lines = db.query("SELECT * FROM cabinet_order_lines WHERE order_id=? ORDER BY line_no", (oid,))
    for l in lines:
        l["ozet"] = json.loads(l["ozet_json"] or "{}")
        l.pop("tkabin", None)
    return templates.TemplateResponse(request, "kabin_y_siparis.html", {
        "request": request, "o": o, "lines": lines, "DURUM_AD": DURUM_AD, "hata": hata})


@router.get("/membrane/kabin/satir/{lid}/veri")
async def y_satir_veri(request: Request, lid: int):
    l = db.one("SELECT tkabin FROM cabinet_order_lines WHERE id=?", (lid,))
    if not l:
        return JSONResponse({"error": "Satır yok"}, status_code=404)
    try:
        view = await KI.islem(json.loads(l["tkabin"])["kabin"], [{"tip": "goster"}], "yonetici",
                              izinler("yonetici"))
    except KI.ServisHatasi as e:
        return _hata(e)
    view.pop("kabin", None)
    return JSONResponse(view)


@router.get("/membrane/kabin/satir/{lid}/tkabin")
async def y_tkabin(lid: int):
    l = db.one("SELECT l.*, o.order_no FROM cabinet_order_lines l JOIN cabinet_orders o ON o.id=l.order_id "
               "WHERE l.id=?", (lid,))
    if not l:
        return RedirectResponse("/membrane/kabin", status_code=303)
    ad = "".join(c if (c.isascii() and c.isalnum()) else "_" for c in f"{l['order_no']}_{l['line_no']}_{l['name']}")
    return Response(l["tkabin"], media_type="application/json",
                    headers={"Content-Disposition": f'attachment; filename="{ad}.tkabin"'})


@router.post("/membrane/kabin/siparis/{oid}/onayla")
async def y_onayla(request: Request, oid: int):
    o = db.one("SELECT * FROM cabinet_orders WHERE id=?", (oid,))
    if not o or o["status"] != "bekliyor":
        return RedirectResponse(f"/membrane/kabin/siparis/{oid}", status_code=303)
    try:                                  # onaydan önce her satır motordan yeniden geçer
        for l in db.query("SELECT * FROM cabinet_order_lines WHERE order_id=?", (oid,)):
            view = await KI.islem(json.loads(l["tkabin"])["kabin"], [{"tip": "goster"}], "yonetici",
                                  izinler("yonetici"))
            if view["hata_var"]:
                return RedirectResponse(f"/membrane/kabin/siparis/{oid}?hata=Satır {l['line_no']}: "
                                        "dolapta hata var, onaylanamaz", status_code=303)
    except KI.ServisHatasi as e:
        return RedirectResponse(f"/membrane/kabin/siparis/{oid}?hata={e.mesaj}", status_code=303)
    db.execute("UPDATE cabinet_orders SET status='onayli', approved_at=datetime('now','localtime'), "
               "approved_by=? WHERE id=?", (getattr(request.state, "user", "") or "", oid))
    return RedirectResponse(f"/membrane/kabin/siparis/{oid}", status_code=303)


@router.post("/membrane/kabin/siparis/{oid}/reddet")
async def y_reddet(oid: int):
    db.execute("UPDATE cabinet_orders SET status='reddedildi' WHERE id=? AND status='bekliyor'", (oid,))
    return RedirectResponse(f"/membrane/kabin/siparis/{oid}", status_code=303)


@router.post("/membrane/kabin/siparis/{oid}/havuza-geri")
async def y_havuza_geri(oid: int):
    db.execute("UPDATE cabinet_orders SET claimed_by='', claimed_at='', production_status='released' "
               "WHERE id=?", (oid,))
    return RedirectResponse(f"/membrane/kabin/siparis/{oid}", status_code=303)


@router.post("/membrane/kabin/satir/{lid}/duzenle")
async def y_satir_duzenle(lid: int):
    """Yönetici onaydan önce dolabı açıp düzeltir (satıra bağlı yönetici taslağı)."""
    l = db.one("SELECT l.*, o.status FROM cabinet_order_lines l JOIN cabinet_orders o ON o.id=l.order_id "
               "WHERE l.id=?", (lid,))
    if not l:
        return RedirectResponse("/membrane/kabin", status_code=303)
    if l["status"] != "bekliyor":
        return RedirectResponse(f"/membrane/kabin/siparis/{l['order_id']}?hata=Onaylı sipariş değiştirilemez",
                                status_code=303)
    t = db.one("SELECT * FROM kabin_taslak WHERE musteri=? AND siparis_satir_id=?", (YONETICI, lid))
    if t:
        db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=?", (t["id"],))
        db.execute("DELETE FROM kabin_taslak WHERE id=?", (t["id"],))
    kj = json.dumps(json.loads(l["tkabin"])["kabin"], ensure_ascii=False)
    tid = db.execute("INSERT INTO kabin_taslak (musteri, ad, sablon, kabin_json, siparis_satir_id) "
                     "VALUES (?,?,?,?,?)", (YONETICI, l["name"], json.loads(kj).get("sablon", ""), kj, lid))
    db.execute("INSERT INTO kabin_taslak_surum (taslak_id, sira, kabin_json) VALUES (?,?,?)", (tid, 0, kj))
    return RedirectResponse(f"/membrane/kabin/t/{tid}", status_code=303)


@router.post("/membrane/kabin/t/{tid}/satira-kaydet")
async def y_satira_kaydet(request: Request, tid: int):
    t = _taslak(tid, YONETICI)
    if not t or not t["siparis_satir_id"]:
        return JSONResponse({"error": "Taslak bulunamadı"}, status_code=404)
    l = db.one("SELECT l.*, o.status, o.claimed_by FROM cabinet_order_lines l "
               "JOIN cabinet_orders o ON o.id=l.order_id WHERE l.id=?", (t["siparis_satir_id"],))
    if not l or l["status"] != "bekliyor":
        return JSONResponse({"error": "Onaylı sipariş değiştirilemez"}, status_code=422)
    try:
        view = await _goster(t, "yonetici")
    except KI.ServisHatasi as e:
        return _hata(e)
    if view["hata_var"]:
        return JSONResponse({"error": "Dolapta hata var, satıra kaydedilemez"}, status_code=422)
    a = _satir_alanlari(view)
    db.execute("UPDATE cabinet_order_lines SET qty=?, width_mm=?, height_mm=?, depth_mm=?, tkabin=?, "
               "engine_version=?, definitions_version=?, ozet_json=? WHERE id=?",
               (a["qty"], a["width_mm"], a["height_mm"], a["depth_mm"], a["tkabin"], a["engine_version"],
                a["definitions_version"], a["ozet_json"], l["id"]))
    if l["claimed_by"]:                   # havuza alınmışsa işaret silinmez, uyarı çıkar
        db.execute("UPDATE cabinet_orders SET degisti=1 WHERE id=?", (l["order_id"],))
    return JSONResponse({"ok": True, "url": f"/membrane/kabin/siparis/{l['order_id']}"})


@router.get("/membrane/kabin/ayar", response_class=HTMLResponse)
async def y_ayar(request: Request, kayit: int = 0):
    try:
        bil = await KI.bilgi()
        sablonlar = [s["ad"] for s in bil["kabin_sablonlari"]]
    except KI.ServisHatasi:
        sablonlar = []
    return templates.TemplateResponse(request, "kabin_y_ayar.html", {
        "request": request, "yetki": yetkiler(), "sablonlar": sablonlar,
        "sinirlar": {s: sinirlar(s) for s in sablonlar}, "kayit": kayit})


@router.post("/membrane/kabin/ayar")
async def y_ayar_kaydet(request: Request):
    f = await request.form()
    y = {k: f.get(f"y:{k}") for k in VARSAYILAN_YETKI if f.get(f"y:{k}") in ("M", "Y")}
    _ayar_yaz("yetkiler", y)
    try:
        sablonlar = [s["ad"] for s in (await KI.bilgi())["kabin_sablonlari"]]
    except KI.ServisHatasi:
        sablonlar = []
    for s in sablonlar:
        d = {}
        for alan in ("en", "boy", "derinlik"):
            try:
                lo = max(50.0, float(f.get(f"s:{s}:{alan}:min", 50)))
                hi = min(10000.0, float(f.get(f"s:{s}:{alan}:max", 10000)))
            except ValueError:
                continue
            if lo <= hi:
                d[alan] = [lo, hi]
        _ayar_yaz(f"sinir.{s}", d)
    return RedirectResponse("/membrane/kabin/ayar?kayit=1", status_code=303)
