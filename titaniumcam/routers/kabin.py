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
import re
import os
import time
from collections import defaultdict, deque

from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse, Response
from fastapi.templating import Jinja2Templates

import db
import kabin_istemci as KI
from config import BASE_DIR
from routers.magaza import current_customer, GUEST_COOKIE, _ensure_guest_token

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))

YONETICI = "@yonetici"
GERI_ALMA = 50

# ── Alan yetkileri (varsayılan) — yönetici /titaniumcam/kabin/ayar'dan değiştirir ──
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
    **{f"ekle.{t}": "M" for t in ("dikeybolme", "yataybolme", "raf", "kapak", "cekmece")},
    # teknik elemanlar: müşteri seçmez (arkalık şablona göre otomatik eklenir)
    **{f"ekle.{t}": "Y" for t in ("arkalik", "dikeykayit", "yataykayit", "usttabla")},
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


FIRMA_VARSAYILAN = {"ad": "Titanium"}


def firma_bilgisi():
    f = dict(FIRMA_VARSAYILAN)
    f.update({k: v for k, v in (_ayar("firma", {}) or {}).items() if v})
    parca = f["ad"].rsplit(" ", 1)
    f["ad1"], f["ad2"] = (parca[0], parca[1]) if len(parca) == 2 else (f["ad"], "")
    return f


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


# ── Sipariş akışı: onay şekli (yönetici: /titaniumcam/ayarlar) ──
def akis_ayar():
    a = _ayar("siparis_akisi", {}) or {}
    return {"kabin_onay": a.get("kabin_onay") if a.get("kabin_onay") in ("yonetici", "otomatik") else "yonetici",
            "magaza_asama": a.get("magaza_asama") if a.get("magaza_asama") in ("havuz", "muhasebe", "planlama")
            else "havuz"}


def kabin_onay_sekli(musteri):
    """Müşteri istisnası varsa o, yoksa genel ayar: 'yonetici' | 'otomatik'."""
    u = db.one("SELECT onay_sekli FROM membrane_users WHERE username=?", (musteri,))
    if u and u.get("onay_sekli") in ("yonetici", "otomatik"):
        return u["onay_sekli"]
    return akis_ayar()["kabin_onay"]


# ── Müşteri dolabı: şablon başına otomatik arkalık (müşteri arkalık seçmez) ──
def oto_arkalik(sablon, bil):
    """Şablona otomatik eklenecek arkalık şablonu adı ('' = arkalık yok)."""
    a = ((_ayar("musteri_kabin", {}) or {}).get("arkalik") or {})
    if sablon in a:
        return a[sablon] or ""
    sab = next((x for x in bil["kabin_sablonlari"] if x["ad"] == sablon), None)
    return ((sab or {}).get("alt_sablonlar") or {}).get("arkalik") or ""


# ── Kabin tanımları: birim fiyat + müşteriye açık (yönetici: /titaniumcam/kabin/tanimlar) ──
TANIM_TURLERI = ("sablon", "malzeme", "bant", "ray", "mentese", "arkalik", "kulp", "baglanti", "donati")
DONATI = [("kanal", "Kanal", "m"), ("mentese", "Menteşe", "adet"), ("kulp", "Kulp", "adet"),
          ("ray", "Çekmece rayı (çift)", "çift"), ("ayak", "Ayak", "adet")]


def tanim_tablosu():
    return {(r["tur"], r["kod"]): r for r in db.query("SELECT * FROM kabin_tanim")}


def kapali_kodlar():
    out = {}
    for r in db.query("SELECT tur, kod FROM kabin_tanim WHERE acik=0"):
        out.setdefault(r["tur"], []).append(r["kod"])
    return out


KI.KAPALI_KODLAR = kapali_kodlar          # müşteri isteklerinde motor servisi de denetler


def fiyat_ayar():
    a = _ayar("fiyat", {}) or {}
    return {"kar": float(a.get("kar", 0) or 0), "goster": bool(a.get("goster", False))}


def fiyat_hesapla(maliyet):
    """Motorun maliyet miktarları × yöneticinin birim fiyatları. Fiyatı girilmemiş kalem
    'eksik'e yazılır; eksik varken müşteriye toplam gösterilmez."""
    T = tanim_tablosu()
    kalemler, eksik = [], []
    for m in maliyet or []:
        tur, _, kod = m["anahtar"].partition(":")
        bf = float((T.get((tur, kod)) or {}).get("fiyat") or 0)
        if bf <= 0:
            eksik.append(m["ad"])
        kalemler.append({**m, "birim_fiyat": bf, "tutar": round(m["miktar"] * bf, 2)})
    a = fiyat_ayar()
    ara = round(sum(k["tutar"] for k in kalemler), 2)
    return {"kalemler": kalemler, "ara_toplam": ara, "kar_orani": a["kar"],
            "toplam": round(ara * (1 + a["kar"] / 100.0), 2), "eksik": eksik}


def _fiyat_ayar_yaz(f):
    try:
        kar = max(-90.0, min(500.0, float(str(f.get("kar", "0")).replace(",", ".") or 0)))
    except ValueError:
        kar = 0.0
    _ayar_yaz("fiyat", {"kar": kar, "goster": f.get("goster") == "1"})


def maliyet_ozeti(fiyat):
    """Kalemleri gruplar: malzeme / kenar bandı / bağlantı / donatı (Maliyetler ekranı)."""
    g = {"malzeme": 0.0, "bant": 0.0, "baglanti": 0.0, "donati": 0.0}
    for k in fiyat["kalemler"]:
        tur = k["anahtar"].partition(":")[0]
        g[tur if tur in g else "donati"] += k["tutar"]
    return {k: round(v, 2) for k, v in g.items()}


def musteri_fiyati(fiyat):
    """Müşteriye gösterilecek toplam (ayar kapalıysa ya da eksik fiyat varsa None)."""
    if not fiyat_ayar()["goster"] or fiyat["eksik"] or fiyat["toplam"] <= 0:
        return None
    return fiyat["toplam"]


# ── Malzeme görünümü (yalnız 3B gerçekçi çizim için; üretime etkisi yok) ──
DOKULAR = (("mat", "Mat"), ("parlak", "Parlak lake"), ("ahsap", "Ahşap desen"))


def _gorunum_varsayilan(m):
    ad = (m.get("ad") or m["kod"]).lower()
    if "arkal" in ad or (m.get("kalinlik") or 18) < 10:
        return {"ad": "Arkalık", "renk": "#ece8e1", "doku": "mat"}
    if "sunta" in ad or "suntalam" in ad:
        return {"ad": "Meşe", "renk": "#c49a6c", "doku": "ahsap"}
    return {"ad": "Beyaz", "renk": "#f2f0eb", "doku": "mat"}


def gorunumler(bil):
    """Her malzeme kodu için {ad, renk, doku}: yöneticinin ayarı, yoksa addan tahmin."""
    kayit = _ayar("gorunum", {}) or {}
    out = {}
    for m in bil["malzemeler"]:
        g = _gorunum_varsayilan(m)
        g.update({k: v for k, v in (kayit.get(m["kod"]) or {}).items() if v})
        out[m["kod"]] = g
    return out


def musteri_bilgisi(bil):
    """Motor tanım listelerinden müşteriye kapalı olanları çıkarır (editör ve vitrin için)."""
    k = {t: set(v) for t, v in kapali_kodlar().items()}
    acik = lambda tur, kod: kod not in k.get(tur, ())  # noqa: E731
    b = dict(bil)
    b["kabin_sablonlari"] = [x for x in bil["kabin_sablonlari"] if acik("sablon", x["ad"])]
    for alan, tur in (("malzemeler", "malzeme"), ("bantlar", "bant"), ("raylar", "ray"), ("menteseler", "mentese")):
        b[alan] = [x for x in bil[alan] if acik(tur, x["kod"])]
    b["arkaliklar"] = [x for x in bil["arkaliklar"] if acik("arkalik", x["ad"])]
    b["kulplar"] = {ad: v for ad, v in bil["kulplar"].items() if acik("kulp", ad)}
    es = dict(bil["eleman_sablonlari"])
    for tur in ("arkalik", "kulp", "cekmece"):
        es[tur] = [a for a in es.get(tur, []) if acik(tur, a)]
    b["eleman_sablonlari"] = es
    return b


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
                       "derinlik": view["govde"]["derinlik"], "adet": view["ozellik"]["adet"],
                       "fiyat": musteri_fiyati(fiyat_hesapla(view.get("maliyet")))})
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
    out = {k: v for k, v in view.items() if k not in ("kabin", "maliyet")}
    f = fiyat_hesapla(view.get("maliyet"))
    if rol == "yonetici":
        out["kabin"] = view["kabin"]
        out["fiyat"] = f                     # yönetici: kalem kalem, eksik fiyatlar
    else:
        out["fiyat"] = {"toplam": musteri_fiyati(f)}
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
# Editör uçları (müşteri: /kabin/t/..., yönetici: /titaniumcam/kabin/t/...)
# ══════════════════════════════════════════════════════════════════════════
def _sahip(request, rol):
    """Taslak sahibi: yönetici · giriş yapmış müşteri · misafir (çerez anahtarı).
    Üye olmayan da tasarlar; giriş yalnız sipariş verirken istenir."""
    if rol == "yonetici":
        return YONETICI if getattr(request.state, "user", None) else None
    return musteri_sahip(request)


def musteri_sahip(request):
    c = current_customer(request)
    if c:
        return c["username"]
    token = request.cookies.get(GUEST_COOKIE, "")
    return f"guest:{token}" if token else None


async def _editor_sayfa(request, tid, rol, taban):
    sahip = _sahip(request, rol)
    t = _taslak(tid, sahip) if sahip else None
    if not t:
        return RedirectResponse("/titaniumcam/kabin" if rol == "yonetici" else "/magaza", status_code=303)
    try:
        bil = await KI.bilgi()
    except KI.ServisHatasi as e:
        return HTMLResponse(f"<p style='font-family:sans-serif;padding:2rem'>{e.mesaj}. "
                            f"<a href='/kabin'>Geri dön</a></p>", status_code=503)
    satir = None
    if t["siparis_satir_id"]:
        satir = db.one("SELECT l.*, o.order_no, o.status FROM cabinet_order_lines l "
                       "JOIN cabinet_orders o ON o.id=l.order_id WHERE l.id=?", (t["siparis_satir_id"],))
    # müşteri: sade ekran · yönetici: ayrıntılı editör
    gbil = bil if rol == "yonetici" else musteri_bilgisi(bil)
    return templates.TemplateResponse(request, "kabin_editor.html" if rol == "yonetici" else "kabin_basit.html", {
        "request": request, "t": t, "rol": rol, "taban": taban,
        "bilgi": gbil,
        "izinler": sorted(izinler(rol)), "sinir": sinirlar(t["sablon"]), "satir": satir,
        "geri_url": (f"/titaniumcam/kabin/siparis/{satir['order_id']}" if satir else
                     KATALOG if t.get("model_id") else "/titaniumcam/kabin")
        if rol == "yonetici" else "/",
        "fiyat_ayar": fiyat_ayar(), "gorunum": gorunumler(gbil),
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
_editor_rotalari("/titaniumcam/kabin", "yonetici")


# ══════════════════════════════════════════════════════════════════════════
# Müşteri: taslaklar, sepet, sipariş
# ══════════════════════════════════════════════════════════════════════════
def _musteri_ctx(request, cust, **kw):
    from routers.magaza import _ctx
    return _ctx(request, cust, **kw)


@router.get("/kabin", response_class=HTMLResponse)
async def kabin_ana(request: Request, hata: str = ""):
    cust = current_customer(request)
    u = musteri_sahip(request) or "-"
    taslaklar = db.query("SELECT * FROM kabin_taslak WHERE musteri=? ORDER BY updated_at DESC", (u,))
    for t in taslaklar:
        t["ozet"] = json.loads(t["ozet_json"] or "{}")
    siparisler = db.query("SELECT o.*, (SELECT COUNT(*) FROM cabinet_order_lines l WHERE l.order_id=o.id) satir "
                          "FROM cabinet_orders o WHERE musteri=? ORDER BY id DESC", (u,)) if cust else []
    return templates.TemplateResponse(request, "kabin_ana.html", _musteri_ctx(
        request, cust, taslaklar=taslaklar, sepet=[t for t in taslaklar if t["sepette"]],
        siparisler=siparisler, hata=hata))


@router.get("/kabin/yeni")
async def kabin_yeni_sayfa():
    return RedirectResponse("/#kabinler", status_code=303)       # müşteri yalnız katalogdan başlar


@router.post("/kabin/yeni")
async def kabin_yeni(request: Request, sablon: str = Form(""), model: int = Form(0)):
    """Müşteri dolabı: yöneticinin yayınladığı kabinden (vitrin). Boş şablon yalnız iç kullanım / testler içindir."""
    cust = current_customer(request)
    yeni_token = None
    if cust:
        sahip = cust["username"]
    else:                                 # misafir: sepet çereziyle tanınır
        sahip, yeni_token = _ensure_guest_token(request)
    ad = sablon
    try:
        if model:
            m = db.one("SELECT * FROM kabin_model WHERE id=? AND acik=1", (model,))
            if not m:
                raise KI.ServisHatasi("Model bulunamadı")
            view = await KI.islem(json.loads(m["kabin_json"]), [{"tip": "yeni_uid"}], "musteri", izinler("musteri"))
            sablon, ad = m["sablon"], m["ad"]
        else:
            bil = await KI.bilgi()
            sab = next((s for s in musteri_bilgisi(bil)["kabin_sablonlari"] if s["ad"] == sablon), None)
            if not sab:
                raise KI.ServisHatasi("Şablon bulunamadı")
            s = sinirlar(sablon)
            olc = {f: min(max(float(sab[f]), s[f][0]), s[f][1]) for f in ("en", "boy", "derinlik")}
            islemler = [{"tip": "yeni", "sablon": sablon, **olc}]
            ark = oto_arkalik(sablon, bil)
            izin = izinler("musteri")
            if ark and ark not in kapali_kodlar().get("arkalik", []):
                # arkalık en başta eklenir (motorda sonuç aynı); ölçü değişince arkalık da uyar
                islemler.append({"tip": "ekle", "tur": "arkalik", "sablon": ark,
                                 "noktalar": [[olc["en"] / 2, min(100.0, olc["boy"] / 4)]]})
                izin = izin | {"ekle.arkalik"}
            view = await KI.islem(None, islemler, "musteri", izin)
    except KI.ServisHatasi as e:
        return RedirectResponse(f"/kabin?hata={e.mesaj}", status_code=303)
    tid = db.execute("INSERT INTO kabin_taslak (musteri, ad, sablon, kabin_json) VALUES (?,?,?,?)",
                     (sahip, ad, sablon, json.dumps(view["kabin"], ensure_ascii=False)))
    t = db.one("SELECT * FROM kabin_taslak WHERE id=?", (tid,))
    db.execute("INSERT INTO kabin_taslak_surum (taslak_id, sira, kabin_json) VALUES (?,?,?)",
               (tid, 0, t["kabin_json"]))
    _kaydet(t, view, surum=False)
    resp = RedirectResponse(f"/kabin/t/{tid}", status_code=303)
    if yeni_token:
        resp.set_cookie(GUEST_COOKIE, yeni_token, httponly=True, samesite="lax", max_age=60 * 60 * 24 * 30)
    return resp


@router.post("/kabin/t/{tid}/kopyala")
async def kabin_kopyala(request: Request, tid: int):
    sahip = musteri_sahip(request)
    t = _taslak(tid, sahip) if sahip else None
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
    sahip = musteri_sahip(request)
    t = _taslak(tid, sahip) if sahip else None
    if t:
        db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=?", (tid,))
        db.execute("DELETE FROM kabin_taslak WHERE id=?", (tid,))
    return RedirectResponse("/kabin", status_code=303)


@router.post("/kabin/t/{tid}/sepet")
async def kabin_sepete(request: Request, tid: int):
    """Sepete ekle: adet uygulanır, doktor hatası varsa reddedilir."""
    sahip = musteri_sahip(request)
    t = _taslak(tid, sahip) if sahip else None
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
    return JSONResponse({"ok": True, "sepet": "/magaza/sepet"})


@router.post("/kabin/t/{tid}/sepetten")
async def kabin_sepetten(request: Request, tid: int):
    sahip = musteri_sahip(request)
    t = _taslak(tid, sahip) if sahip else None
    if t:
        db.execute("UPDATE kabin_taslak SET sepette=0 WHERE id=?", (tid,))
    return RedirectResponse(request.headers.get("referer") or "/magaza/sepet", status_code=303)


def _tkabin(kabin):
    return {"format": "tkabin", "schema": 1, "kabin": kabin}


def _satir_alanlari(view):
    k = view["kabin"]
    return {"qty": int(k.get("adet", 1)), "width_mm": float(k["en"]), "height_mm": float(k["boy"]),
            "depth_mm": float(k["derinlik"]), "cabinet_no": k.get("kabinno", "") or "",
            "tkabin": json.dumps(_tkabin(k), ensure_ascii=False),
            "engine_version": view["motor_surumu"], "definitions_version": view["tanim_surumu"],
            "ozet_json": json.dumps({**view["ozet"], "fiyat": fiyat_hesapla(view.get("maliyet"))["toplam"],
                                     "fiyat_eksik": bool(fiyat_hesapla(view.get("maliyet"))["eksik"])})}


@router.post("/kabin/siparis")
async def kabin_siparis(request: Request, proje: str = Form(""), note: str = Form("")):
    cust = current_customer(request)
    if not cust:                          # tasarım serbest; sipariş verirken giriş / üyelik
        return RedirectResponse("/magaza/giris?next=/magaza/sepet", status_code=303)
    sepet = db.query("SELECT * FROM kabin_taslak WHERE musteri=? AND sepette=1 ORDER BY id", (cust["username"],))
    if not sepet:
        return RedirectResponse("/magaza/sepet", status_code=303)
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
    if kabin_onay_sekli(cust["username"]) == "otomatik":   # onaylı → TitaniumCam havuzuna (API) düşer
        db.execute("UPDATE cabinet_orders SET status='onayli', approved_at=datetime('now','localtime'), "
                   "approved_by='otomatik' WHERE id=?", (oid,))
        db.execute("INSERT INTO cabinet_order_log (order_id, station, olay, note) VALUES (?,?,?,?)",
                   (oid, "", "otomatik_onay", "Onay şekli: otomatik"))
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


@router.get("/titaniumcam/kabin", response_class=HTMLResponse)
async def y_liste(request: Request, durum: str = "", mesaj: str = ""):
    sql = ("SELECT o.*, (SELECT COUNT(*) FROM cabinet_order_lines l WHERE l.order_id=o.id) satir, "
           "(SELECT COALESCE(SUM(qty),0) FROM cabinet_order_lines l WHERE l.order_id=o.id) dolap "
           "FROM cabinet_orders o")
    args = ()
    if durum in DURUM_AD:
        sql += " WHERE status=?"
        args = (durum,)
    orders = db.query(sql + " ORDER BY id DESC", args)
    return templates.TemplateResponse(request, "kabin_y_liste.html", {
        "request": request, "orders": orders, "durum": durum, "DURUM_AD": DURUM_AD, "mesaj": mesaj})


@router.get("/titaniumcam/kabin/siparis/{oid}", response_class=HTMLResponse)
async def y_siparis(request: Request, oid: int, hata: str = "", mesaj: str = ""):
    o = db.one("SELECT * FROM cabinet_orders WHERE id=?", (oid,))
    if not o:
        return RedirectResponse("/titaniumcam/kabin", status_code=303)
    lines = db.query("SELECT * FROM cabinet_order_lines WHERE order_id=? ORDER BY line_no", (oid,))
    for l in lines:
        l["ozet"] = json.loads(l["ozet_json"] or "{}")
        l.pop("tkabin", None)
    return templates.TemplateResponse(request, "kabin_y_siparis.html", {
        "request": request, "o": o, "lines": lines, "DURUM_AD": DURUM_AD, "hata": hata, "mesaj": mesaj})


@router.get("/titaniumcam/kabin/satir/{lid}/veri")
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
    view["fiyat"] = fiyat_hesapla(view.pop("maliyet", None))
    return JSONResponse(view)


@router.get("/titaniumcam/kabin/satir/{lid}/tkabin")
async def y_tkabin(lid: int):
    l = db.one("SELECT l.*, o.order_no FROM cabinet_order_lines l JOIN cabinet_orders o ON o.id=l.order_id "
               "WHERE l.id=?", (lid,))
    if not l:
        return RedirectResponse("/titaniumcam/kabin", status_code=303)
    ad = "".join(c if (c.isascii() and c.isalnum()) else "_" for c in f"{l['order_no']}_{l['line_no']}_{l['name']}")
    return Response(l["tkabin"], media_type="application/json",
                    headers={"Content-Disposition": f'attachment; filename="{ad}.tkabin"'})


@router.post("/titaniumcam/kabin/siparis/{oid}/onayla")
async def y_onayla(request: Request, oid: int):
    o = db.one("SELECT * FROM cabinet_orders WHERE id=?", (oid,))
    if not o or o["status"] != "bekliyor":
        return RedirectResponse(f"/titaniumcam/kabin/siparis/{oid}", status_code=303)
    try:                                  # onaydan önce her satır motordan yeniden geçer
        for l in db.query("SELECT * FROM cabinet_order_lines WHERE order_id=?", (oid,)):
            view = await KI.islem(json.loads(l["tkabin"])["kabin"], [{"tip": "goster"}], "yonetici",
                                  izinler("yonetici"))
            if view["hata_var"]:
                return RedirectResponse(f"/titaniumcam/kabin/siparis/{oid}?hata=Satır {l['line_no']}: "
                                        "dolapta hata var, onaylanamaz", status_code=303)
    except KI.ServisHatasi as e:
        return RedirectResponse(f"/titaniumcam/kabin/siparis/{oid}?hata={e.mesaj}", status_code=303)
    db.execute("UPDATE cabinet_orders SET status='onayli', approved_at=datetime('now','localtime'), "
               "approved_by=? WHERE id=?", (getattr(request.state, "user", "") or "", oid))
    return RedirectResponse(f"/titaniumcam/kabin/siparis/{oid}", status_code=303)


@router.post("/titaniumcam/kabin/siparis/{oid}/reddet")
async def y_reddet(oid: int):
    db.execute("UPDATE cabinet_orders SET status='reddedildi' WHERE id=? AND status='bekliyor'", (oid,))
    return RedirectResponse(f"/titaniumcam/kabin/siparis/{oid}", status_code=303)


@router.post("/titaniumcam/kabin/siparis/{oid}/havuza-geri")
async def y_havuza_geri(oid: int):
    db.execute("UPDATE cabinet_orders SET claimed_by='', claimed_at='', production_status='released' "
               "WHERE id=?", (oid,))
    return RedirectResponse(f"/titaniumcam/kabin/siparis/{oid}", status_code=303)


def _siparise(oid, mesaj="", hata=""):
    from urllib.parse import urlencode
    q = urlencode({k: v for k, v in (("mesaj", mesaj), ("hata", hata)) if v})
    return RedirectResponse(f"/titaniumcam/kabin/siparis/{oid}" + ("?" + q if q else ""), status_code=303)


def _degisti_isaretle(oid):
    """Üretime alınmış (TitaniumCam'in çektiği) sipariş değişirse uyarı bayrağı."""
    db.execute("UPDATE cabinet_orders SET degisti=1 WHERE id=? AND claimed_by<>''", (oid,))


def _siparisi_sil(oid):
    for l in db.query("SELECT id FROM cabinet_order_lines WHERE order_id=?", (oid,)):
        for t in db.query("SELECT id FROM kabin_taslak WHERE musteri=? AND siparis_satir_id=?", (YONETICI, l["id"])):
            db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=?", (t["id"],))
            db.execute("DELETE FROM kabin_taslak WHERE id=?", (t["id"],))
    db.execute("DELETE FROM cabinet_order_lines WHERE order_id=?", (oid,))
    db.execute("DELETE FROM cabinet_order_log WHERE order_id=?", (oid,))
    db.execute("DELETE FROM cabinet_orders WHERE id=?", (oid,))


@router.post("/titaniumcam/kabin/siparis/{oid}/sil")
async def y_siparis_sil(oid: int):
    o = db.one("SELECT order_no FROM cabinet_orders WHERE id=?", (oid,))
    if o:
        _siparisi_sil(oid)
    from urllib.parse import quote
    return RedirectResponse("/titaniumcam/kabin?mesaj=" + quote(f"{(o or {}).get('order_no', '')} silindi"), status_code=303)


@router.post("/titaniumcam/kabin/siparis/{oid}/bilgi")
async def y_siparis_bilgi(oid: int, proje: str = Form(""), note: str = Form(""), musteri_ad: str = Form("")):
    if not db.one("SELECT 1 FROM cabinet_orders WHERE id=?", (oid,)):
        return RedirectResponse("/titaniumcam/kabin", status_code=303)
    db.execute("UPDATE cabinet_orders SET proje=?, note=?, musteri_ad=COALESCE(NULLIF(?, ''), musteri_ad) WHERE id=?",
               (proje.strip()[:120], note.strip()[:1000], musteri_ad.strip()[:120], oid))
    return _siparise(oid, "Sipariş bilgileri kaydedildi")


@router.post("/titaniumcam/kabin/siparis/{oid}/bekliyor")
async def y_onay_geri(oid: int):
    """Onaylı ya da reddedilmiş siparişi yeniden 'onay bekliyor'a al (TitaniumCam henüz almadıysa)."""
    o = db.one("SELECT * FROM cabinet_orders WHERE id=?", (oid,))
    if not o:
        return RedirectResponse("/titaniumcam/kabin", status_code=303)
    if o["claimed_by"]:
        return _siparise(oid, hata="TitaniumCam bu siparişi üretime almış; önce 'Havuza geri al'")
    db.execute("UPDATE cabinet_orders SET status='bekliyor', approved_at='', approved_by='' WHERE id=?", (oid,))
    return _siparise(oid, "Sipariş yeniden onay bekliyor; düzeltip onaylayabilirsiniz")


@router.post("/titaniumcam/kabin/satir/{lid}/adet")
async def y_satir_adet(lid: int, adet: int = Form(1)):
    l = db.one("SELECT * FROM cabinet_order_lines WHERE id=?", (lid,))
    if not l:
        return RedirectResponse("/titaniumcam/kabin", status_code=303)
    adet = max(1, min(999, adet))
    db.execute("UPDATE cabinet_order_lines SET qty=? WHERE id=?", (adet, lid))
    _degisti_isaretle(l["order_id"])
    return _siparise(l["order_id"], f"{l['line_no']}. dolap: adet {adet}")


@router.post("/titaniumcam/kabin/satir/{lid}/sil")
async def y_satir_sil(lid: int):
    l = db.one("SELECT * FROM cabinet_order_lines WHERE id=?", (lid,))
    if not l:
        return RedirectResponse("/titaniumcam/kabin", status_code=303)
    if db.one("SELECT COUNT(*) n FROM cabinet_order_lines WHERE order_id=?", (l["order_id"],))["n"] <= 1:
        return await y_siparis_sil(l["order_id"])             # son dolap: sipariş tümden silinir
    for t in db.query("SELECT id FROM kabin_taslak WHERE musteri=? AND siparis_satir_id=?", (YONETICI, lid)):
        db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=?", (t["id"],))
        db.execute("DELETE FROM kabin_taslak WHERE id=?", (t["id"],))
    db.execute("DELETE FROM cabinet_order_lines WHERE id=?", (lid,))
    _degisti_isaretle(l["order_id"])
    return _siparise(l["order_id"], f"{l['line_no']}. dolap ({l['name']}) siparişten çıkarıldı")


@router.post("/titaniumcam/kabin/satir/{lid}/duzenle")
async def y_satir_duzenle(lid: int):
    """Yönetici onaydan önce dolabı açıp düzeltir (satıra bağlı yönetici taslağı)."""
    l = db.one("SELECT l.*, o.status FROM cabinet_order_lines l JOIN cabinet_orders o ON o.id=l.order_id "
               "WHERE l.id=?", (lid,))
    if not l:
        return RedirectResponse("/titaniumcam/kabin", status_code=303)
    if l["status"] != "bekliyor":
        return RedirectResponse(f"/titaniumcam/kabin/siparis/{l['order_id']}?hata=Onaylı sipariş değiştirilemez",
                                status_code=303)
    t = db.one("SELECT * FROM kabin_taslak WHERE musteri=? AND siparis_satir_id=?", (YONETICI, lid))
    if t:
        db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=?", (t["id"],))
        db.execute("DELETE FROM kabin_taslak WHERE id=?", (t["id"],))
    kj = json.dumps(json.loads(l["tkabin"])["kabin"], ensure_ascii=False)
    tid = db.execute("INSERT INTO kabin_taslak (musteri, ad, sablon, kabin_json, siparis_satir_id) "
                     "VALUES (?,?,?,?,?)", (YONETICI, l["name"], json.loads(kj).get("sablon", ""), kj, lid))
    db.execute("INSERT INTO kabin_taslak_surum (taslak_id, sira, kabin_json) VALUES (?,?,?)", (tid, 0, kj))
    return RedirectResponse(f"/titaniumcam/kabin/t/{tid}", status_code=303)


@router.post("/titaniumcam/kabin/t/{tid}/satira-kaydet")
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
    return JSONResponse({"ok": True, "url": f"/titaniumcam/kabin/siparis/{l['order_id']}"})


@router.get("/titaniumcam/kabin/ayar", response_class=HTMLResponse)
async def y_ayar(request: Request, kayit: int = 0):
    try:
        bil = await KI.bilgi()
        sablonlar = [s["ad"] for s in bil["kabin_sablonlari"]]
    except KI.ServisHatasi:
        sablonlar = []
    return templates.TemplateResponse(request, "kabin_y_ayar.html", {
        "request": request, "yetki": yetkiler(), "sablonlar": sablonlar,
        "sinirlar": {s: sinirlar(s) for s in sablonlar}, "kayit": kayit})


@router.post("/titaniumcam/kabin/ayar")
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
    return RedirectResponse("/titaniumcam/kabin/ayar?kayit=1", status_code=303)


# ══════════════════════════════════════════════════════════════════════════
# Yönetici: kabin tanımları — birim fiyatlar ve müşterinin kullanabilecekleri
# ══════════════════════════════════════════════════════════════════════════
EKLE_TURLERI = [("dikeybolme", "Dikey bölme"), ("yataybolme", "Yatay bölme"), ("raf", "Raf"),
                ("arkalik", "Arkalık"), ("dikeykayit", "Dikey kayıt"), ("yataykayit", "Yatay kayıt"),
                ("kapak", "Kapak"), ("cekmece", "Çekmece"), ("usttabla", "Üst tabla")]


@router.get("/titaniumcam/kabin/tanimlar", response_class=HTMLResponse)
async def y_tanimlar(request: Request, kayit: int = 0):
    try:
        bil = await KI.bilgi()
    except KI.ServisHatasi as e:
        return HTMLResponse(f"<p style='padding:2rem;font-family:sans-serif'>{e.mesaj}</p>", status_code=503)
    T = tanim_tablosu()
    v = lambda tur, kod: T.get((tur, kod)) or {"fiyat": 0, "acik": 1}  # noqa: E731
    bolumler = [
        ("sablon", "Dolap şablonları", "", [(x["ad"], x["ad"], f"{x['boy']:g}×{x['en']:g}×{x['derinlik']:g}")
                                          for x in bil["kabin_sablonlari"]], False),
        ("malzeme", "Malzemeler", "₺/m²", [(x["kod"], x["ad"], f"{x.get('kalinlik') or ''} mm")
                                           for x in bil["malzemeler"]], True),
        ("bant", "Kenar bantları", "₺/m", [(x["kod"], x["ad"], "") for x in bil["bantlar"]], True),
        ("ray", "Çekmece rayları", "", [(x["kod"], x["ad"], f"kasa {x.get('kasa_derinlik') or ''}")
                                         for x in bil["raylar"]], False),
        ("mentese", "Menteşe tipleri", "", [(x["kod"], x["ad"], "") for x in bil["menteseler"]], False),
        ("arkalik", "Arkalık şablonları", "", [(x["ad"], x["ad"], f"{x['tip']} {x['kalinlik']:g} mm")
                                               for x in bil["arkaliklar"]], False),
        ("kulp", "Kulp şablonları", "", [(a, a, "") for a in bil["kulplar"]], False),
    ]
    yet = yetkiler()
    return templates.TemplateResponse(request, "kabin_y_tanimlar.html", {
        "request": request, "bolumler": bolumler, "v": v, "donati": DONATI, "kayit": kayit,
        "fiyat": fiyat_ayar(), "ekle": [(t, ad, yet.get(f"ekle.{t}") == "M") for t, ad in EKLE_TURLERI],
        "sinirlar": {x["ad"]: sinirlar(x["ad"]) for x in bil["kabin_sablonlari"]},
        "gorunum": gorunumler(bil), "dokular": DOKULAR})


@router.post("/titaniumcam/kabin/tanimlar")
async def y_tanimlar_kaydet(request: Request):
    f = await request.form()
    satirlar = {}
    for anahtar, deger in f.multi_items():
        parca = anahtar.split(":", 2)                 # f:tur:kod (fiyat) · a:tur:kod (açık)
        if len(parca) != 3 or parca[0] not in ("f", "a") or parca[1] not in TANIM_TURLERI:
            continue
        r = satirlar.setdefault((parca[1], parca[2]), {"fiyat": None, "acik": 0})
        if parca[0] == "f" and str(deger).strip():     # fiyat yalnız gönderildiyse (Maliyetler ekranı)
            try:
                r["fiyat"] = max(0.0, float(str(deger).replace(",", ".")))
            except ValueError:
                pass
        else:
            r["acik"] = 1
    gor = _ayar("gorunum", {}) or {}
    for anahtar, deger in f.multi_items():           # g:kod:alan (malzeme görünümü)
        parca = anahtar.split(":", 2)
        if len(parca) != 3 or parca[0] != "g" or parca[2] not in ("ad", "renk", "doku"):
            continue
        deger = str(deger).strip()[:40]
        if parca[2] == "renk" and not re.fullmatch(r"#[0-9a-fA-F]{6}", deger):
            continue
        if parca[2] == "doku" and deger not in dict(DOKULAR):
            continue
        gor.setdefault(parca[1][:80], {})[parca[2]] = deger
    _ayar_yaz("gorunum", gor)
    for (tur, kod), r in satirlar.items():
        if tur == "donati" or tur == "baglanti":
            r["acik"] = 1                              # fiyat kalemi; müşteri seçmez
        if r["fiyat"] is None:
            db.execute("INSERT INTO kabin_tanim (tur, kod, fiyat, acik) VALUES (?,?,0,?) "
                       "ON CONFLICT(tur, kod) DO UPDATE SET acik=excluded.acik", (tur, kod[:80], r["acik"]))
        else:
            db.execute("INSERT INTO kabin_tanim (tur, kod, fiyat, acik) VALUES (?,?,?,?) "
                       "ON CONFLICT(tur, kod) DO UPDATE SET fiyat=excluded.fiyat, acik=excluded.acik",
                       (tur, kod[:80], r["fiyat"], r["acik"]))
    if "kar" in f:
        _fiyat_ayar_yaz(f)
    y = _ayar("yetkiler", {}) or {}
    for t, _ad in EKLE_TURLERI:
        y[f"ekle.{t}"] = "M" if f.get(f"e:{t}") == "1" else "Y"
    _ayar_yaz("yetkiler", y)
    for sab in {k[1] for k in satirlar if k[0] == "sablon"}:
        d = {}
        for alan in ("en", "boy", "derinlik"):
            try:
                lo = max(50.0, float(f.get(f"s:{sab}:{alan}:min", 50)))
                hi = min(10000.0, float(f.get(f"s:{sab}:{alan}:max", 10000)))
            except ValueError:
                continue
            if lo <= hi:
                d[alan] = [lo, hi]
        _ayar_yaz(f"sinir.{sab}", d)
    return RedirectResponse("/titaniumcam/kabin/tanimlar?kayit=1", status_code=303)


# ══════════════════════════════════════════════════════════════════════════
# Yönetici: Kabinler (satıştaki kabin kataloğu + maliyetler)
# Kabin = yöneticinin editörde tasarladığı model; vitrinde gerçekçi 3B görselle satılır.
# ══════════════════════════════════════════════════════════════════════════
KATALOG = "/titaniumcam/kabinler"


def _git_katalog(mesaj="", sekme=""):
    from urllib.parse import urlencode
    q = {k: v for k, v in (("sekme", sekme), ("mesaj", mesaj)) if v}
    return RedirectResponse(KATALOG + ("?" + urlencode(q) if q else ""), status_code=303)


def _ust_mu(sablon):
    return bool(re.match(r"(?i)^(ust|üst)\b", sablon or ""))


def _onizleme(view):
    """Küçük 2B çizim verisi (delik vb. yok)."""
    return {"govde": view["govde"], "gozler": [],
            "parcalar2b": [{k: p[k] for k in ("id", "tur", "katman", "kutu", "menteseyeri", "kulp")
                            if k in p} for p in view["parcalar2b"]]}


def _gorsel(view, sablon):
    """Gerçekçi 3B görsel için en küçük veri: gövde, parça köşeleri, kapak / çekmece kulpları."""
    oz = view.get("ozellik") or {}
    yuvarla = lambda k: [[round(c, 1) for c in q] for q in k]  # noqa: E731
    return {"govde": view["govde"], "ust": _ust_mu(sablon),
            "ozellik": {k: oz.get(k) for k in ("govde.malzeme", "baza.on_geriye")},
            "parcalar3b": [{"id": p["id"], "eleman": p.get("eleman", ""), "tur": p["tur"],
                            "malzeme": p.get("malzeme", ""), "koseler": yuvarla(p["koseler"])}
                           for p in view["parcalar3b"]],
            "parcalar2b": [{k: p[k] for k in ("id", "tur", "kutu", "kulp") if k in p}
                           for p in view["parcalar2b"] if p["tur"] in ("kapak", "cekmece")]}


def _model_gorunumu_yaz(mid, view, sablon):
    db.execute("UPDATE kabin_model SET onizleme_json=?, ozet_json=?, gorsel_json=?, maliyet_json=? WHERE id=?",
               (json.dumps(_onizleme(view)), json.dumps(view["ozet"]), json.dumps(_gorsel(view, sablon)),
                json.dumps(view.get("maliyet") or []), mid))


async def _katalog(sadece_satista):
    """Modeller + görsel + fiyat. Görseli olmayan (eski) model bir kez motordan hesaplanır."""
    out = []
    for m in db.query("SELECT * FROM kabin_model" + (" WHERE acik=1" if sadece_satista else "") + " ORDER BY sira, id"):
        if not m.get("gorsel_json"):
            try:
                view = await KI.islem(json.loads(m["kabin_json"]), [{"tip": "goster"}], "yonetici", izinler("yonetici"))
                _model_gorunumu_yaz(m["id"], view, m["sablon"])
                m.update(gorsel_json=json.dumps(_gorsel(view, m["sablon"])), ozet_json=json.dumps(view["ozet"]),
                         maliyet_json=json.dumps(view.get("maliyet") or []))
            except KI.ServisHatasi:
                pass
        m["gorsel"] = json.loads(m.get("gorsel_json") or "{}")
        m["ozet"] = json.loads(m.get("ozet_json") or "{}")
        m["onizleme"] = json.loads(m.get("onizleme_json") or "{}")
        m["fiyat"] = fiyat_hesapla(json.loads(m.get("maliyet_json") or "[]"))
        m["grup"] = (m.get("kategori") or "").strip() or m["sablon"]
        out.append(m)
    return out


def _gruplar(modeller):
    g = {}
    for m in modeller:
        g.setdefault(m["grup"], []).append(m)
    return list(g.items())


def _kullanilan_gorunum(modeller, bil):
    """Yalnız vitrindeki kabinlerin kullandığı malzemelerin rengi / dokusu."""
    kodlar = {p.get("malzeme") for m in modeller for p in (m["gorsel"].get("parcalar3b") or [])}
    return {k: v for k, v in gorunumler(bil).items() if k in kodlar}


async def magaza_modelleri():
    """Vitrin: satıştaki kabinler (gruplu), müşteri fiyatı (gösterim açık ve eksik yoksa)."""
    modeller = await _katalog(True)
    for m in modeller:
        m["musteri_fiyat"] = musteri_fiyati(m["fiyat"])
        m.pop("kabin_json", None)
        m.pop("fiyat", None)
    return modeller


@router.get("/titaniumcam/kabin/modeller")
async def y_modeller_eski():
    return RedirectResponse(KATALOG, status_code=303)


@router.get(KATALOG, response_class=HTMLResponse)
async def y_kabinler(request: Request, sekme: str = "katalog", mesaj: str = ""):
    try:
        bil = await KI.bilgi()
    except KI.ServisHatasi as e:
        return HTMLResponse(f"<p style='padding:2rem;font-family:sans-serif'>{e.mesaj}</p>", status_code=503)
    sekme = sekme if sekme in ("katalog", "maliyet") else "katalog"
    modeller = await _katalog(False)
    for m in modeller:
        m["grup_toplam"] = maliyet_ozeti(m["fiyat"])
        m.pop("kabin_json", None)
    T = tanim_tablosu()
    fy = lambda tur, kod: float((T.get((tur, kod)) or {}).get("fiyat") or 0)  # noqa: E731
    birim = [
        ("Malzemeler", "malzeme", "₺/m²", [(x["kod"], x["ad"], f"{x.get('kalinlik') or ''} mm") for x in bil["malzemeler"]]),
        ("Kenar bantları", "bant", "₺/m", [(x["kod"], x["ad"], "") for x in bil["bantlar"]]),
        ("Donatı", "donati", "", [(k, ad, b) for k, ad, b in DONATI]),
        ("Bağlantı elemanları", "baglanti", "₺/adet", [(x["kod"], x["ad"], "") for x in bil["baglantilar"]]),
    ]
    ark = {x["ad"]: oto_arkalik(x["ad"], bil) for x in bil["kabin_sablonlari"]}
    import hazir_katalog as HK
    var = {m["ad"] for m in modeller}
    hazir = [{"ad": m["ad"], "kategori": m["kategori"], "aciklama": m["aciklama"], "var": m["ad"] in var} for m in HK.MODELLER]
    return templates.TemplateResponse(request, "kabinler.html", {"hazir": hazir,
        "request": request, "sekme": sekme, "mesaj": mesaj, "modeller": modeller, "gruplar": _gruplar(modeller),
        "sablonlar": bil["kabin_sablonlari"], "arkaliklar": bil["arkaliklar"], "oto_ark": ark,
        "gorunum": gorunumler(bil), "fiyat": fiyat_ayar(), "birim": birim, "fy": fy,
        "kategoriler": sorted({m["grup"] for m in modeller} | {x["ad"] for x in bil["kabin_sablonlari"]})})


@router.post(KATALOG + "/fiyatlar")
async def y_kabin_fiyatlar(request: Request):
    """Maliyetler: yalnız birim fiyatlar + kâr (müşteriye açık / kapalı ayarına dokunmaz)."""
    f = await request.form()
    for anahtar, deger in f.multi_items():
        parca = anahtar.split(":", 2)
        if len(parca) != 3 or parca[0] != "f" or parca[1] not in TANIM_TURLERI:
            continue
        try:
            v = max(0.0, float(str(deger).replace(",", ".") or 0))
        except ValueError:
            continue
        db.execute("INSERT INTO kabin_tanim (tur, kod, fiyat, acik) VALUES (?,?,?,1) "
                   "ON CONFLICT(tur, kod) DO UPDATE SET fiyat=excluded.fiyat", (parca[1], parca[2][:80], v))
    _fiyat_ayar_yaz(f)
    return _git_katalog("Fiyatlar kaydedildi", "maliyet")


@router.post(KATALOG + "/hazir-katalog")
async def y_hazir_katalog(request: Request):
    """Hazır katalogdaki seçilen kabinleri motorla kurar ve (isteğe bağlı) satışa açar.
    Aynı adlı kabin varsa atlanır (iki kez basılırsa çoğalmaz)."""
    import hazir_katalog as HK
    f = await request.form()
    secilen = set(f.getlist("ad"))
    satis = 1 if f.get("satis") else 0
    try:
        bil = await KI.bilgi()
    except KI.ServisHatasi as e:
        return _git_katalog(e.mesaj)
    iz = izinler("yonetici")

    async def islem(kabin, islemler):
        return await KI.islem(kabin, islemler, "yonetici", iz)

    var = {m["ad"] for m in db.query("SELECT ad FROM kabin_model")}
    sira = (db.one("SELECT COALESCE(MAX(sira), 0) n FROM kabin_model") or {}).get("n", 0)
    eklenen, atlanan, hatali = 0, 0, []
    for m in HK.MODELLER:
        if m["ad"] not in secilen:
            continue
        if m["ad"] in var:
            atlanan += 1
            continue
        try:
            view = await HK.kur(m, islem, ark=oto_arkalik(m["sablon"], bil))
            if view["hata_var"]:
                raise ValueError("doktor hatası")
        except (KI.ServisHatasi, ValueError, KeyError) as e:
            hatali.append(f"{m['ad']} ({getattr(e, 'mesaj', e)})")
            continue
        sira += 1
        mid = db.execute("INSERT INTO kabin_model (ad, aciklama, sablon, kategori, kabin_json, acik, sira) VALUES (?,?,?,?,?,?,?)",
                         (m["ad"], m["aciklama"], m["sablon"], m["kategori"],
                          json.dumps(view["kabin"], ensure_ascii=False), satis, sira))
        _model_gorunumu_yaz(mid, view, m["sablon"])
        eklenen += 1
    msj = f"{eklenen} kabin eklendi" + (" ve satışa açıldı" if satis and eklenen else "")
    if atlanan:
        msj += f" · {atlanan} kabin zaten vardı"
    if hatali:
        msj += " · kurulamayan: " + ", ".join(hatali)
    return _git_katalog(msj)


@router.post("/titaniumcam/kabin/modeller/arkalik")
async def y_oto_arkalik(request: Request):
    f = await request.form()
    a = {k[4:]: str(v)[:80] for k, v in f.items() if k.startswith("ark:")}
    _ayar_yaz("musteri_kabin", {**(_ayar("musteri_kabin", {}) or {}), "arkalik": a})
    return _git_katalog("Otomatik arkalık kaydedildi")


async def _model_taslak(mid, kabin, ad, sablon):
    tid = db.execute("INSERT INTO kabin_taslak (musteri, ad, sablon, kabin_json, model_id) VALUES (?,?,?,?,?)",
                     (YONETICI, ad, sablon, json.dumps(kabin, ensure_ascii=False), mid))
    db.execute("INSERT INTO kabin_taslak_surum (taslak_id, sira, kabin_json) VALUES (?,?,?)",
               (tid, 0, json.dumps(kabin, ensure_ascii=False)))
    return tid


@router.post("/titaniumcam/kabin/modeller/yeni")
async def y_model_yeni(ad: str = Form(...), sablon: str = Form(...), kategori: str = Form("")):
    try:
        bil = await KI.bilgi()
        islemler = [{"tip": "yeni", "sablon": sablon}]
        sab = next((x for x in bil["kabin_sablonlari"] if x["ad"] == sablon), None)
        if not sab:
            raise KI.ServisHatasi("Şablon bulunamadı")
        ark = oto_arkalik(sablon, bil)
        if ark:
            islemler.append({"tip": "ekle", "tur": "arkalik", "sablon": ark,
                             "noktalar": [[sab["en"] / 2, min(100.0, sab["boy"] / 4)]]})
        view = await KI.islem(None, islemler, "yonetici", izinler("yonetici"))
    except KI.ServisHatasi as e:
        return _git_katalog(e.mesaj)
    ad = ad.strip()[:80] or sablon
    sira = (db.one("SELECT COALESCE(MAX(sira), 0) + 1 n FROM kabin_model") or {}).get("n", 0)
    mid = db.execute("INSERT INTO kabin_model (ad, sablon, kategori, kabin_json, sira) VALUES (?,?,?,?,?)",
                     (ad, sablon, kategori.strip()[:60], json.dumps(view["kabin"], ensure_ascii=False), sira))
    _model_gorunumu_yaz(mid, view, sablon)
    tid = await _model_taslak(mid, view["kabin"], ad, sablon)
    return RedirectResponse(f"/titaniumcam/kabin/t/{tid}", status_code=303)


@router.post("/titaniumcam/kabin/modeller/{mid}/duzenle")
async def y_model_duzenle(mid: int):
    m = db.one("SELECT * FROM kabin_model WHERE id=?", (mid,))
    if not m:
        return _git_katalog()
    for t in db.query("SELECT id FROM kabin_taslak WHERE musteri=? AND model_id=?", (YONETICI, mid)):
        db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=?", (t["id"],))
        db.execute("DELETE FROM kabin_taslak WHERE id=?", (t["id"],))
    tid = await _model_taslak(mid, json.loads(m["kabin_json"]), m["ad"], m["sablon"])
    return RedirectResponse(f"/titaniumcam/kabin/t/{tid}", status_code=303)


@router.post("/titaniumcam/kabin/t/{tid}/modele-kaydet")
async def y_modele_kaydet(request: Request, tid: int):
    t = _taslak(tid, YONETICI)
    if not t or not t["model_id"]:
        return JSONResponse({"error": "Taslak bulunamadı"}, status_code=404)
    try:
        view = await _goster(t, "yonetici")
    except KI.ServisHatasi as e:
        return _hata(e)
    if view["hata_var"]:
        return JSONResponse({"error": "Dolapta hata var, kabin kaydedilemez"}, status_code=422)
    m = db.one("SELECT sablon FROM kabin_model WHERE id=?", (t["model_id"],))
    db.execute("UPDATE kabin_model SET kabin_json=?, ad=?, updated_at=datetime('now') WHERE id=?",
               (t["kabin_json"], db.one("SELECT ad FROM kabin_taslak WHERE id=?", (tid,))["ad"] or "Kabin", t["model_id"]))
    _model_gorunumu_yaz(t["model_id"], view, (m or {}).get("sablon", t["sablon"]))
    return JSONResponse({"ok": True, "url": KATALOG + "?mesaj=Kabin kaydedildi"})


@router.post("/titaniumcam/kabin/modeller/{mid}/guncelle")
async def y_model_guncelle(request: Request, mid: int):
    f = await request.form()
    try:
        sira = int(f.get("sira") or 0)
    except ValueError:
        sira = 0
    db.execute("UPDATE kabin_model SET ad=COALESCE(NULLIF(?, ''), ad), aciklama=?, sira=?, acik=?, kategori=? WHERE id=?",
               (str(f.get("ad", "")).strip()[:80], str(f.get("aciklama", "")).strip()[:300], sira,
                1 if f.get("acik") else 0, str(f.get("kategori", "")).strip()[:60], mid))
    return _git_katalog("Kaydedildi")


@router.post("/titaniumcam/kabin/modeller/{mid}/satis")
async def y_model_satis(mid: int):
    db.execute("UPDATE kabin_model SET acik=1-acik WHERE id=?", (mid,))
    m = db.one("SELECT ad, acik FROM kabin_model WHERE id=?", (mid,)) or {"ad": "", "acik": 0}
    return _git_katalog(f"{m['ad']}: " + ("satışa açıldı" if m["acik"] else "satıştan kaldırıldı"))


@router.post("/titaniumcam/kabin/modeller/{mid}/kopyala")
async def y_model_kopyala(mid: int):
    m = db.one("SELECT * FROM kabin_model WHERE id=?", (mid,))
    if not m:
        return _git_katalog()
    db.execute("INSERT INTO kabin_model (ad, aciklama, sablon, kategori, kabin_json, onizleme_json, ozet_json, "
               "gorsel_json, maliyet_json, acik, sira) VALUES (?,?,?,?,?,?,?,?,?,0,?)",
               ((m["ad"] + " (kopya)")[:80], m["aciklama"], m["sablon"], m.get("kategori", ""), m["kabin_json"],
                m["onizleme_json"], m["ozet_json"], m.get("gorsel_json", ""), m.get("maliyet_json", ""), m["sira"] + 1))
    return _git_katalog(f"{m['ad']} kopyalandı (satışta değil)")


@router.post("/titaniumcam/kabin/modeller/{mid}/sil")
async def y_model_sil(mid: int):
    db.execute("DELETE FROM kabin_model WHERE id=?", (mid,))
    return _git_katalog("Kabin silindi")
