"""Fuar standı tasarımı (müşteri) ve ayarları (yönetici).

Akış: /stand (ölçü, açık cepheler, duvar yüksekliği) → alternatifler (yapay zekâ ya da kurallı) →
her alternatifin modülleri motorda kabin olarak kurulur → müşteri bir alternatif seçer → modüller
sepete kabin olarak düşer (stand_id ile) → sipariş verilince standın kabin dışı kalemleri (duvar,
zemin, aydınlatma, grafik) siparişe stand bilgisi olarak yazılır ve TitaniumCam API'sinde görünür.
"""
import json
import logging
import os
import re

from fastapi import APIRouter, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

import db
import hazir_katalog as HK
import kabin_istemci as KI
import stand as S
import stand_donati as SD
from config import BASE_DIR
from routers.kabin import (_ayar, _ayar_yaz, _gorsel, _kaydet, fiyat_ayar, fiyat_hesapla, izinler,
                           oto_arkalik)
from routers.magaza import GUEST_COOKIE, _ctx, _ensure_guest_token, _owner, current_customer

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))
log = logging.getLogger("stand")

SAATLIK_SINIR = 8          # sahip başına saatte en çok üretim (yapay zekâ maliyeti)


# ── Ayarlar ─────────────────────────────────────────────────────────────────
def stand_ayar():
    a = _ayar("stand", {}) or {}
    birim = {k: float(a.get("birim", {}).get(k) or 0) for k in S.EKSTRA_KALEMLER}
    return {"acik": a.get("acik", True) is not False, "ai": a.get("ai", True) is not False,
            "gunluk": int(a.get("gunluk") or 200), "birim": birim}


def katalog(hepsi=False):
    """Donatı kataloğu (kod → kayıt). Boşsa varsayılanlarla doldurulur."""
    if not db.one("SELECT 1 FROM stand_donati LIMIT 1"):
        for i, d in enumerate(SD.varsayilan_katalog().values()):
            db.execute("INSERT OR IGNORE INTO stand_donati (kod, ad, grup, model, en, derinlik, yukseklik, renk, fiyat, aktif, sira) "
                       "VALUES (?,?,?,?,?,?,?,?,?,?,?)", tuple(d[k] for k in SD.ALANLAR) + (0, 1, i))
    rows = db.query("SELECT * FROM stand_donati " + ("" if hepsi else "WHERE aktif=1 ") + "ORDER BY sira, kod")
    return {r["kod"]: r for r in rows}


def ai_anahtari():
    """Anthropic API anahtarı: önce ortam değişkeni, yoksa yöneticinin girdiği (ekranda gösterilmez)."""
    return os.environ.get("ANTHROPIC_API_KEY") or (_ayar("stand_ai", {}) or {}).get("anahtar") or ""


def _hizli_mi(sahip):
    n = db.one("SELECT COUNT(*) n FROM stand_proje WHERE sahip=? AND created_at > datetime('now','-1 hour')", (sahip,))
    return (n or {}).get("n", 0) >= SAATLIK_SINIR


def _gunluk_ai():
    n = db.one("SELECT COUNT(*) n FROM stand_proje WHERE kaynak='ai' AND created_at > datetime('now','-1 day')")
    return (n or {}).get("n", 0)


# ── Motorda kurma ───────────────────────────────────────────────────────────
async def _kur(alt, bil):
    """Alternatifin modüllerini kabin olarak kurar. Kurulamayan modül çıkarılır (not düşülür)."""
    iz = izinler("yonetici")

    async def islem(kabin, islemler):
        return await KI.islem(kabin, islemler, "yonetici", iz)

    kurulan, kalan = {}, []
    for temsilci, idx in S.gruplar(alt["moduller"]):
        t = S.tarif(temsilci)
        try:
            view = await HK.kur(t, islem, ark=oto_arkalik(t["sablon"], bil))
            if view["hata_var"]:
                raise ValueError("doktor hatası")
        except (KI.ServisHatasi, ValueError, KeyError) as e:
            log.warning("stand modülü kurulamadı: %s %s", temsilci["tip"], getattr(e, "mesaj", e))
            alt["notlar"].append(f"{temsilci['ad']} üretime uygun kurulamadığı için çıkarıldı")
            continue
        f = fiyat_hesapla(view.get("maliyet"))
        kurulan[S.modul_anahtari(temsilci)] = {
            "ad": temsilci["ad"], "tip": temsilci["tip"], "sablon": t["sablon"], "kabin": view["kabin"],
            "olcu": [temsilci["en"], temsilci["boy"], temsilci["derinlik"]],
            "ozet": view["ozet"], "fiyat": f["toplam"], "fiyat_eksik": bool(f["eksik"]),
            "gorsel": _gorsel(view, t["sablon"])}
        kalan += [alt["moduller"][i] for i in idx]
    alt["moduller"] = [m for m in alt["moduller"] if m in kalan]
    alt["kabinler"] = []
    for temsilci, idx in S.gruplar(alt["moduller"]):
        k = dict(kurulan[S.modul_anahtari(temsilci)])
        k["adet"], k["moduller"] = len(idx), idx
        alt["kabinler"].append(k)
    return alt


def fiyatlar(alt):
    """Kabinler + kabin dışı kalemler. Müşteriye toplam yalnız her şeyin fiyatı varsa ve ayar açıksa."""
    ekstra, ekstra_eksik = S.ekstra_fiyatla(alt["ekstralar"], stand_ayar()["birim"])
    kabin = round(sum(k["fiyat"] * k["adet"] for k in alt["kabinler"]), 2)
    kabin_eksik = any(k["fiyat_eksik"] or k["fiyat"] <= 0 for k in alt["kabinler"])
    goster = fiyat_ayar()["goster"]
    return {"ekstralar": ekstra, "donatilar": [e for e in ekstra if e["kod"].startswith("don:")], "kabin": kabin, "ekstra": round(sum(e["tutar"] for e in ekstra), 2),
            "toplam": round(kabin + sum(e["tutar"] for e in ekstra), 2),
            "kabin_goster": goster and not kabin_eksik, "toplam_goster": goster and not kabin_eksik and not ekstra_eksik}


def _proje(sid, sahip):
    r = db.one("SELECT * FROM stand_proje WHERE id=? AND sahip=?", (sid, sahip)) if sahip else None
    if not r:
        return None
    r["girdi"] = json.loads(r["girdi_json"])
    r["alternatifler"] = json.loads(r["alternatifler_json"])
    return r


# ── Müşteri sayfaları ───────────────────────────────────────────────────────
def _form_sayfa(request, cust, hata="", girdi=None):
    owner, _g, _c = _owner(request)
    gecmis = db.query("SELECT id, girdi_json, secilen, created_at FROM stand_proje WHERE sahip=? "
                      "ORDER BY id DESC LIMIT 6", (owner,)) if owner else []
    for p in gecmis:
        p["girdi"] = json.loads(p["girdi_json"])
        p["tip"] = S.stand_tipi(p["girdi"]["acik"])
    K = katalog()
    g = girdi or {"en": 6000, "derinlik": 4000, "yukseklik": 3000, "acik": ["on"]}
    g.setdefault("donati", SD.donati_dogrula({"ekran": "tv_55", "toplanti": 4, "bar": 1, "oturma": "berjer",
                                               "depo": "1", "brosur": 1, "bitki": 2}, K))
    gruplar = {grup: SD.grup_ogeleri(K, grup) for grup in SD.GRUPLAR}
    return templates.TemplateResponse(request, "stand_form.html", _ctx(
        request, cust, hata=hata, g=g, gecmis=gecmis, ayar=stand_ayar(), gruplar=gruplar,
        fiyat_goster=fiyat_ayar()["goster"]))


@router.get("/stand", response_class=HTMLResponse)
async def stand_form(request: Request):
    return _form_sayfa(request, current_customer(request))


DONATI_ALANLARI = ("ekran", "ekran_adet", "toplanti", "bar", "oturma", "depo", "brosur", "bitki", "sandalye", "tabure",
                   "koltuk", "kanepe", "toplanti_masasi", "bar_masasi", "sehpa")


@router.post("/stand/uret")
async def stand_uret(request: Request):
    f = await request.form()
    cust = current_customer(request)
    ham = {"en": f.get("en"), "derinlik": f.get("derinlik"), "yukseklik": f.get("yukseklik"),
           "acik": f.getlist("acik"), "sektor": f.get("sektor", ""), "istek": f.get("istek", "")}
    try:                                  # form metre ile gelir
        olc = {k: round(float(str(ham[k]).replace(",", ".")) * 1000) for k in ("en", "derinlik", "yukseklik")}
    except (TypeError, ValueError):
        return _form_sayfa(request, cust, hata="Ölçüleri metre olarak girin (ör. 6 ya da 4,5)")
    try:
        girdi = S.girdi_dogrula(acik=ham["acik"], sektor=ham["sektor"], istek=ham["istek"], **olc)
    except ValueError as e:
        return _form_sayfa(request, cust, hata=str(e))
    K = katalog()
    girdi["donati"] = SD.donati_dogrula({k: f.get(k) for k in DONATI_ALANLARI}, K)
    ayar = stand_ayar()
    if not ayar["acik"]:
        return _form_sayfa(request, cust, hata="Stand tasarımı şu an kapalı")
    yeni_token = None
    if cust:
        sahip = cust["username"]
    else:
        sahip, yeni_token = _ensure_guest_token(request)
    if _hizli_mi(sahip):
        return _form_sayfa(request, cust, hata="Çok sık tasarım ürettiniz; biraz sonra tekrar deneyin.")
    anahtar = ai_anahtari() if ayar["ai"] and _gunluk_ai() < ayar["gunluk"] else ""
    alternatifler, kaynak, neden = await S.alternatifler_uret(girdi, anahtar, K)
    try:
        bil = await KI.bilgi()
        kurulmus = [await _kur(a, bil) for a in alternatifler]
    except KI.ServisHatasi as e:
        return _form_sayfa(request, cust, hata=e.mesaj)
    kurulmus = [a for a in kurulmus if a["kabinler"]]
    if not kurulmus:
        return _form_sayfa(request, cust, hata="Bu ölçülerle stand kurulamadı; ölçüleri büyütüp tekrar deneyin.")
    if neden:
        girdi["uyari"] = neden
    sid = db.execute("INSERT INTO stand_proje (sahip, girdi_json, alternatifler_json, kaynak) VALUES (?,?,?,?)",
                     (sahip, json.dumps(girdi, ensure_ascii=False), json.dumps(kurulmus, ensure_ascii=False), kaynak))
    resp = RedirectResponse(f"/stand/{sid}", status_code=303)
    if yeni_token:
        resp.set_cookie(GUEST_COOKIE, yeni_token, httponly=True, samesite="lax", max_age=60 * 60 * 24 * 30)
    return resp


def _sahne(alt):
    """3B stand sahnesi için hafif veri: modül yerleşimi + her kabinin gerçekçi görseli."""
    return {"moduller": [{k: m[k] for k in ("tip", "en", "boy", "derinlik", "x", "y", "yon")} for m in alt["moduller"]],
            "donatilar": [p for st in alt.get("donatilar", []) for p in SD.parcalar_plan(st)],
            "kabinler": [{"gorsel": k["gorsel"], "moduller": k["moduller"]} for k in alt["kabinler"]]}


@router.get("/stand/{sid}", response_class=HTMLResponse)
async def stand_goster(request: Request, sid: int):
    owner, _g, cust = _owner(request)
    p = _proje(sid, owner)
    if not p:
        return RedirectResponse("/stand", status_code=303)
    for a in p["alternatifler"]:
        a["svg"] = S.plan_svg(p["girdi"], a["moduller"], donatilar=a.get("donatilar", []))
        a["fiyat"] = fiyatlar(a)
    sahneler = [_sahne(a) for a in p["alternatifler"]]
    gorunum = {}
    try:
        from routers.kabin import gorunumler
        gorunum = gorunumler(await KI.bilgi())
    except KI.ServisHatasi:
        pass
    return templates.TemplateResponse(request, "stand_sonuc.html", _ctx(
        request, cust, p=p, g=p["girdi"], tip=S.stand_tipi(p["girdi"]["acik"]), CEPHE_AD=S.CEPHE_AD,
        MODULLER=S.MODULLER, sahneler=sahneler, gorunum=gorunum))


@router.post("/stand/{sid}/sec/{i}")
async def stand_sec(request: Request, sid: int, i: int):
    """Alternatifi seçer: modülleri müşterinin dolapları olarak sepete koyar (önceki seçim sepetten çıkar)."""
    owner, _g, _cust = _owner(request)
    p = _proje(sid, owner)
    if not p or not 0 <= i < len(p["alternatifler"]):
        return RedirectResponse("/stand", status_code=303)
    alt = p["alternatifler"][i]
    iz = izinler("musteri")
    eklenen = []
    try:
        for k in alt["kabinler"]:
            view = await KI.islem(k["kabin"], [{"tip": "yeni_uid"}, {"tip": "ozellik", "alanlar": {"adet": k["adet"]}}],
                                  "musteri", iz)
            eklenen.append((k, view))
    except KI.ServisHatasi as e:
        return RedirectResponse(f"/stand/{sid}?hata={e.mesaj}", status_code=303)
    for t in db.query("SELECT id FROM kabin_taslak WHERE musteri=? AND stand_id=? AND sepette=1", (owner, sid)):
        db.execute("DELETE FROM kabin_taslak_surum WHERE taslak_id=?", (t["id"],))
        db.execute("DELETE FROM kabin_taslak WHERE id=?", (t["id"],))
    for k, view in eklenen:
        tid = db.execute("INSERT INTO kabin_taslak (musteri, ad, sablon, kabin_json, stand_id, sepette) "
                         "VALUES (?,?,?,?,?,1)",
                         (owner, f"Stand #{sid} · {k['ad']}", k["sablon"],
                          json.dumps(view["kabin"], ensure_ascii=False), sid))
        t = db.one("SELECT * FROM kabin_taslak WHERE id=?", (tid,))
        db.execute("INSERT INTO kabin_taslak_surum (taslak_id, sira, kabin_json) VALUES (?,?,?)",
                   (tid, 0, t["kabin_json"]))
        _kaydet(t, view, surum=False)
    db.execute("UPDATE stand_proje SET secilen=? WHERE id=?", (i, sid))
    return RedirectResponse("/magaza/sepet", status_code=303)


# ── Sepet ve sipariş yardımcıları (routers/magaza.py, routers/kabin.py kullanır) ──
def secili_stand(sid):
    """Seçilmiş alternatifin sipariş özeti (kabin dışı kalemler fiyatlarıyla). Seçim yoksa None."""
    r = db.one("SELECT * FROM stand_proje WHERE id=?", (sid,))
    if not r or r["secilen"] < 0:
        return None
    alt = json.loads(r["alternatifler_json"])[r["secilen"]]
    g = json.loads(r["girdi_json"])
    f = fiyatlar(alt)
    st = {"id": sid, "girdi": {k: g[k] for k in ("en", "derinlik", "yukseklik", "acik", "sektor")},
          "tip": S.stand_tipi(g["acik"]),
          "alternatif": {"ad": alt["ad"], "konsept": alt["konsept"], "ekstralar": f["ekstralar"],
                         "donatilar": alt.get("donatilar", []),
                         "moduller": [{k: m[k] for k in ("tip", "ad", "en", "boy", "derinlik", "x", "y", "yon")}
                                      for m in alt["moduller"]]},
          "fiyat": {"kabin": f["kabin"], "ekstra": f["ekstra"], "toplam": f["toplam"],
                    "goster": f["toplam_goster"]}}
    st["ozet"] = S.ozet_metin(st)
    return st


def sepet_standlari(dolaplar):
    """Sepetteki stand modüllerinin standları (plan + kabin dışı kalemler)."""
    out = []
    for sid in sorted({d.get("stand_id") or 0 for d in dolaplar} - {0}):
        st = secili_stand(sid)
        if st:
            st["svg"] = S.plan_svg(st["girdi"], st["alternatif"]["moduller"], donatilar=st["alternatif"].get("donatilar", []))
            out.append(st)
    return out


def plan_svg(st):
    return S.plan_svg(st["girdi"], st["alternatif"]["moduller"], buyuk=True,
                      donatilar=st["alternatif"].get("donatilar", []))


# ── Yönetici ────────────────────────────────────────────────────────────────
@router.get("/titaniumcam/stand", response_class=HTMLResponse)
async def y_stand(request: Request, mesaj: str = ""):
    projeler = db.query("SELECT id, sahip, girdi_json, kaynak, secilen, created_at FROM stand_proje ORDER BY id DESC LIMIT 50")
    for p in projeler:
        p["girdi"] = json.loads(p["girdi_json"])
        p["tip"] = S.stand_tipi(p["girdi"]["acik"])
    anahtar = ai_anahtari()
    return templates.TemplateResponse(request, "stand_ayar.html", {
        "request": request, "ayar": stand_ayar(), "mesaj": mesaj, "projeler": projeler,
        "ekstra": S.EKSTRA_KALEMLER, "anahtar_var": bool(anahtar), "donatilar": list(katalog(hepsi=True).values()),
        "GRUPLAR": SD.GRUPLAR, "MODEL_AD": SD.MODEL_AD,
        "anahtar_ortam": bool(os.environ.get("ANTHROPIC_API_KEY")),
        "anahtar_son": anahtar[-4:] if anahtar else "", "gunluk_ai": _gunluk_ai(), "model": S.MODEL})


@router.post("/titaniumcam/stand/ayar")
async def y_stand_ayar(request: Request):
    f = await request.form()
    birim = {}
    for k in S.EKSTRA_KALEMLER:
        try:
            birim[k] = max(0.0, float(str(f.get(f"b:{k}", "0")).replace(",", ".") or 0))
        except ValueError:
            birim[k] = 0.0
    try:
        gunluk = max(0, min(10000, int(f.get("gunluk") or 200)))
    except ValueError:
        gunluk = 200
    _ayar_yaz("stand", {"acik": f.get("acik") == "1", "ai": f.get("ai") == "1", "gunluk": gunluk, "birim": birim})
    yeni = str(f.get("anahtar") or "").strip()
    if f.get("anahtar_sil") == "1":
        _ayar_yaz("stand_ai", {})
    elif yeni:
        if not yeni.startswith("sk-ant-") or len(yeni) > 300:
            return RedirectResponse("/titaniumcam/stand?mesaj=Anahtar geçersiz görünüyor (sk-ant- ile başlamalı)", status_code=303)
        _ayar_yaz("stand_ai", {"anahtar": yeni})
    return RedirectResponse("/titaniumcam/stand?mesaj=Stand ayarları kaydedildi", status_code=303)


@router.post("/titaniumcam/stand/dene")
async def y_stand_dene():
    """Anahtarı küçük bir istekle dener (yönetici). Anahtar ekrana / kayda yazılmaz."""
    anahtar = ai_anahtari()
    if not anahtar:
        return RedirectResponse("/titaniumcam/stand?mesaj=Önce API anahtarını girin", status_code=303)
    g = S.girdi_dogrula(6000, 4000, 3000, ["on", "sol"])
    try:
        ham = await S.ai_alternatifler(g, anahtar)
        n = len([a for a in (S.alternatif_duzelt(h, g) for h in ham) if a])
        msj = f"Yapay zekâ çalışıyor: {n} alternatif üretti"
    except Exception as e:  # noqa: BLE001 — yöneticiye yalnız hata türü gösterilir
        msj = f"Yapay zekâ çalışmadı ({type(e).__name__}). Anahtarı ve hesap bakiyesini kontrol edin."
    return RedirectResponse(f"/titaniumcam/stand?mesaj={msj}", status_code=303)


@router.post("/titaniumcam/stand/donati")
async def y_donati_kaydet(request: Request):
    """Donatı kataloğu toplu kaydı (ad, ölçü, renk, fiyat, aktif). Satır silme ayrı."""
    f = await request.form()
    K = katalog(hepsi=True)
    for kod, d in K.items():
        if f.get(f"{kod}:ad") is None:
            continue
        def sayi(alan, ab):
            try:
                return max(ab[0], min(ab[1], float(str(f.get(f"{kod}:{alan}", d[alan])).replace(",", "."))))
            except ValueError:
                return d[alan]
        renk = str(f.get(f"{kod}:renk") or d["renk"])
        db.execute("UPDATE stand_donati SET ad=?, en=?, derinlik=?, yukseklik=?, renk=?, fiyat=?, aktif=? WHERE kod=?",
                   (" ".join(str(f.get(f"{kod}:ad")).split())[:80] or d["ad"], sayi("en", (100, 6000)),
                    sayi("derinlik", (40, 6000)), sayi("yukseklik", (100, 5000)),
                    renk if re.fullmatch(r"#[0-9a-fA-F]{6}", renk) else d["renk"], sayi("fiyat", (0, 10_000_000)),
                    1 if f.get(f"{kod}:aktif") == "1" else 0, kod))
    return RedirectResponse("/titaniumcam/stand?mesaj=Donatı kataloğu kaydedildi#donatilar", status_code=303)


@router.post("/titaniumcam/stand/donati/yeni")
async def y_donati_yeni(ad: str = Form(""), grup: str = Form(""), model: str = Form("")):
    """Yeni model (ör. başka renk sandalye). Ölçüler grubun ilk modelinden başlar."""
    ad = " ".join(ad.split())[:80]
    if not ad or grup not in SD.GRUPLAR:
        return RedirectResponse("/titaniumcam/stand?mesaj=Ad ve grup gerekli#donatilar", status_code=303)
    model = model if model in SD.GRUPLAR[grup][1] else SD.GRUPLAR[grup][1][0]
    ornek = next((d for d in katalog(hepsi=True).values() if d["grup"] == grup), None) or \
        {"en": 500, "derinlik": 500, "yukseklik": 800, "renk": "#64748b"}
    kod = re.sub(r"[^a-z0-9]+", "_", ad.lower().translate(str.maketrans("çğıöşüâî", "cgiosuai"))).strip("_")[:40] or "donati"
    while db.one("SELECT 1 FROM stand_donati WHERE kod=?", (kod,)):
        kod += "_2"
    sira = (db.one("SELECT COALESCE(MAX(sira),0)+1 n FROM stand_donati") or {}).get("n", 0)
    db.execute("INSERT INTO stand_donati (kod, ad, grup, model, en, derinlik, yukseklik, renk, fiyat, aktif, sira) "
               "VALUES (?,?,?,?,?,?,?,?,0,1,?)",
               (kod, ad, grup, model, ornek["en"], ornek["derinlik"], ornek["yukseklik"], ornek["renk"], sira))
    return RedirectResponse(f"/titaniumcam/stand?mesaj={ad} eklendi#donatilar", status_code=303)


@router.post("/titaniumcam/stand/donati/{kod}/sil")
async def y_donati_sil(kod: str):
    if kod in ("tv_standi", "depo_odasi"):    # sistem kalemleri: kapatılabilir, silinmez
        return RedirectResponse("/titaniumcam/stand?mesaj=Bu kalem silinemez, pasif yapabilirsiniz#donatilar", status_code=303)
    db.execute("DELETE FROM stand_donati WHERE kod=?", (kod,))
    return RedirectResponse("/titaniumcam/stand?mesaj=Silindi#donatilar", status_code=303)
