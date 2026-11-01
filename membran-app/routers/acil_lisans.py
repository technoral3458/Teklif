"""Acil lisans: TitaniumCam uçları (anahtarsız; yetki acil kodun kendisi) + yönetici ekranı.

POST /api/titanium/v1/license/emergency   acil kodla 7 günlük imzalı lisans
POST /api/titanium/v1/license/event       acil lisansla açılış kaydı
/membrane/acil-lisans                     yönetici (liste, rapor, CSV, ayarlar, anahtar)
"""
import datetime as dt
import hashlib
import hmac
import json
import logging
import os

from fastapi import APIRouter, Request, Form, UploadFile, File
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

import acil_lisans as AL
import db
import eposta
from config import BASE_DIR, SECRET_KEY

log = logging.getLogger("acil_lisans")
router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))


def _j(durum, govde):
    return JSONResponse(govde, status_code=durum, headers={"Cache-Control": "no-store"})


async def _govde(request):
    """4 KB sınırı: büyük gövde okunmadan reddedilir."""
    try:
        if int(request.headers.get("content-length") or 0) > AL.MAX_GOVDE:
            return None
    except ValueError:
        return None
    ham = await request.body()
    return ham if len(ham) <= AL.MAX_GOVDE else None


def _bildir(bildirim, ip, ulke, sehir, istek_kodu):
    r, olay, neden, bitis = bildirim
    a = AL.ayar()
    satirlar = [f"Müşteri: {r['musteri']}", f"Lisans no: {r['lisans_no']}", f"Olay: {olay}" + (f" ({neden})" if neden else ""),
                f"Zaman: {AL.simdi().strftime('%Y-%m-%d %H:%M')} (İstanbul)", f"IP: {ip}",
                f"Ülke: {ulke or '-'}", f"Şehir (yaklaşık): {sehir or '-'}", f"İstek kodu: {istek_kodu}",
                f"Bitiş: {bitis or '-'}", "", "Yönetim: /membrane/acil-lisans"]
    eposta.gonder(a["eposta"], f"Acil lisans: {r['musteri']} ({olay})", "\n".join(satirlar))


@router.post("/api/titanium/v1/license/emergency")
async def acil(request: Request):
    try:
        if not AL.https_mi(request):
            return _j(403, {"error": "https"})
        ip = AL.gercek_ip(request)
        if AL.hata_limiti_asildi(ip):                                     # 1
            return _j(429, {"error": "rate"})
        try:
            ham = await _govde(request)
            if ham is None:
                raise AL.Ret(400, {"error": "bad_request"}, hata_say=True)
            kod_ozet, makine, istek_kodu, surum = AL.istek_coz(ham)        # 2
            ulke, sehir = AL.konum(ip)
            durum, govde, bildirim = await run_in_threadpool(              # 3-7
                AL.etkinlestir, kod_ozet, makine, istek_kodu, surum, ip, ulke, sehir)
        except AL.Ret as r:
            if r.hata_say:
                AL.say_ekle(ip, "hata")
            return _j(r.durum, r.govde)
        if bildirim:
            _bildir(bildirim, ip, ulke, sehir, istek_kodu)
        return _j(durum, govde)
    except Exception as e:                       # iz ve ayrıntı dönmez; acil kod günlüğe girmez
        log.error("acil lisans hatası: %s", type(e).__name__)
        return _j(500, {"error": "server"})


@router.post("/api/titanium/v1/license/event")
async def olay(request: Request):
    try:
        if not AL.https_mi(request):
            return _j(403, {"error": "https"})
        ip = AL.gercek_ip(request)
        if AL.olay_limiti_asildi(ip):
            return _j(429, {"error": "rate"})
        AL.say_ekle(ip, "olay")
        ham = await _govde(request)
        try:
            g = json.loads(ham) if ham is not None else None
        except (ValueError, UnicodeDecodeError):
            g = None
        if not isinstance(g, dict):
            return _j(400, {"error": "bad_request"})
        ln, ol, ik, sur = g.get("lisans_no"), g.get("olay"), g.get("istek_kodu", ""), g.get("surum", "")
        if (not isinstance(ln, str) or not ln or len(ln) > 60 or ol != "acilis"
                or not isinstance(ik, str) or len(ik) > 200 or not isinstance(sur, str) or len(sur) > 40):
            return _j(400, {"error": "bad_request"})
        if not db.one("SELECT 1 FROM acil_lisanslar WHERE lisans_no=?", (ln,)):
            return _j(404, {"error": "license"})
        ulke, sehir = AL.konum(ip)
        conn = db.get_conn()
        try:
            AL.olay_yaz(conn, ln, "acilis", ip, ulke, sehir, ik, sur)
            conn.commit()
        finally:
            conn.close()
        return _j(200, {"ok": True})
    except Exception as e:
        log.error("acil olay hatası: %s", type(e).__name__)
        return _j(500, {"error": "server"})


# ══════════════════════════════════════════════════════════════════════════
# Yönetici ekranı (giriş: main.py ara katmanı; CSRF: oturuma bağlı jeton)
# ══════════════════════════════════════════════════════════════════════════
def _csrf(request):
    oturum = request.cookies.get("session", "")
    return hmac.new(SECRET_KEY.encode(), ("csrf-acil|" + oturum).encode(), hashlib.sha256).hexdigest()


def _csrf_tamam(request, jeton):
    return bool(jeton) and hmac.compare_digest(_csrf(request), jeton)


def _git(sekme, mesaj="", hata=""):
    from urllib.parse import urlencode
    q = {"sekme": sekme}
    if mesaj:
        q["mesaj"] = mesaj
    if hata:
        q["hata"] = hata
    return RedirectResponse("/membrane/acil-lisans?" + urlencode(q), status_code=303)


def _liste():
    gun = AL.bugun()
    out = []
    for r in db.query("SELECT * FROM acil_lisanslar ORDER BY musteri, lisans_no"):
        sb = AL.tarih_oku(r["son_bitis"])
        ab = AL.tarih_oku(r["bitis"])
        r["kalan"] = (sb - gun).days if sb and sb >= gun else None
        if r["kapali"]:
            r["durum"], r["renk"] = "Kapalı", "red"
        elif ab and ab < gun:
            r["durum"], r["renk"] = "Ana lisans bitti", "red"
        elif r["kalan"] is not None:
            r["durum"], r["renk"] = "Acil lisans kullanımda", "amber"
        else:
            r["durum"], r["renk"] = "Hazır", "emerald"
        out.append(r)
    return out


@router.get("/membrane/acil-lisans", response_class=HTMLResponse)
async def y_sayfa(request: Request, sekme: str = "liste", mesaj: str = "", hata: str = "",
                  musteri: str = "", bas: str = "", son: str = ""):
    sekme = sekme if sekme in ("liste", "rapor", "yukle", "ayar", "anahtar") else "liste"
    olaylar = []
    if sekme == "rapor":
        olaylar = db.query(
            "SELECT o.*, COALESCE(l.musteri,'') musteri FROM acil_olaylar o "
            "LEFT JOIN acil_lisanslar l ON l.lisans_no=o.lisans_no "
            "WHERE (?='' OR l.musteri LIKE ?) AND (?='' OR substr(o.zaman,1,10)>=?) AND (?='' OR substr(o.zaman,1,10)<=?) "
            "ORDER BY o.id DESC LIMIT 500",
            (musteri, f"%{musteri}%", bas, bas, son, son))
    try:
        acik, anahtar_hata = AL.acik_anahtar_hex(), ""
    except Exception as e:
        acik, anahtar_hata = None, f"Anahtar okunamadı ({type(e).__name__}). Klasör: {AL.ANAHTAR_DIR}"
    return templates.TemplateResponse(request, "acil_lisans.html", {
        "request": request, "sekme": sekme, "mesaj": mesaj, "hata": hata, "csrf": _csrf(request),
        "liste": _liste() if sekme == "liste" else [], "olaylar": olaylar,
        "f": {"musteri": musteri, "bas": bas, "son": son}, "ayar": AL.ayar(),
        "acik": acik, "anahtar_hata": anahtar_hata, "anahtar_dir": AL.ANAHTAR_DIR,
        "eposta_hata": eposta.son_hata(), "smtp": eposta.ayarli_mi(),
        "geoip": AL.geoip_var(),
        "bugun": AL.tarih(AL.bugun())})


@router.post("/membrane/acil-lisans/islem")
async def y_islem(request: Request, csrf: str = Form(""), lisans_no: str = Form(...), ne: str = Form(...)):
    if not _csrf_tamam(request, csrf):
        return HTMLResponse("Geçersiz istek (CSRF)", status_code=403)
    r = db.one("SELECT * FROM acil_lisanslar WHERE lisans_no=?", (lisans_no,))
    if not r:
        return _git("liste", hata="Lisans bulunamadı")
    if ne == "kapali":
        db.execute("UPDATE acil_lisanslar SET kapali=? WHERE lisans_no=?", (0 if r["kapali"] else 1, lisans_no))
        return _git("liste", f"{r['musteri']}: " + ("açıldı" if r["kapali"] else "kapatıldı"))
    if ne == "izin":
        db.execute("UPDATE acil_lisanslar SET elle_izin=? WHERE lisans_no=?", (0 if r["elle_izin"] else 1, lisans_no))
        return _git("liste", f"{r['musteri']}: " + ("tek seferlik izin geri alındı" if r["elle_izin"]
                                                     else "bir kez izin verildi (sıklık sınırı bir kez atlanır)"))
    return _git("liste", hata="Geçersiz işlem")


@router.post("/membrane/acil-lisans/yukle")
async def y_yukle(request: Request, csrf: str = Form(""), dosya: UploadFile = File(...)):
    if not _csrf_tamam(request, csrf):
        return HTMLResponse("Geçersiz istek (CSRF)", status_code=403)
    ham = await dosya.read(2_000_001)
    if len(ham) > 2_000_000:
        return _git("yukle", hata="Dosya çok büyük (en çok 2 MB)")
    try:
        s = AL.csv_yukle(ham.decode("utf-8"))
    except UnicodeDecodeError:
        return _git("yukle", hata="Dosya UTF-8 değil")
    except ValueError as e:
        return _git("yukle", hata=str(e))
    m = f"{s['eklenen']} lisans eklendi, {s['guncellenen']} güncellendi."
    h = "; ".join(f"satır {n}: {neden}" for n, neden in s["hatalar"][:10])
    if len(s["hatalar"]) > 10:
        h += f" … (+{len(s['hatalar']) - 10})"
    return _git("yukle", m, (f"{len(s['hatalar'])} satır alınmadı: " + h) if s["hatalar"] else "")


@router.post("/membrane/acil-lisans/ayar")
async def y_ayar(request: Request, csrf: str = Form(""), sure: str = Form("7"), aralik: str = Form("90"), eposta_: str = Form("", alias="eposta")):
    if not _csrf_tamam(request, csrf):
        return HTMLResponse("Geçersiz istek (CSRF)", status_code=403)
    try:
        AL.ayar_yaz(sure, aralik, eposta_)
    except ValueError as e:
        return _git("ayar", hata=str(e))
    return _git("ayar", "Ayarlar kaydedildi")


@router.post("/membrane/acil-lisans/eposta-dene")
async def y_eposta_dene(request: Request, csrf: str = Form("")):
    if not _csrf_tamam(request, csrf):
        return HTMLResponse("Geçersiz istek (CSRF)", status_code=403)
    a = AL.ayar()
    if not a["eposta"]:
        return _git("ayar", hata="Önce bildirim e-postasını kaydedin")
    try:
        await run_in_threadpool(eposta.gonder_simdi, a["eposta"], "Acil lisans: deneme e-postası",
                                "Bu bir deneme e-postasıdır. Acil lisans bildirimleri bu adrese gelecek.")
        eposta.hata_temizle()
    except Exception as e:
        return _git("ayar", hata=f"E-posta gönderilemedi: {type(e).__name__}: {str(e)[:200]}")
    return _git("ayar", f"Deneme e-postası gönderildi: {a['eposta']}")


@router.post("/membrane/acil-lisans/anahtar")
async def y_anahtar(request: Request, csrf: str = Form(""), onay: str = Form("")):
    if not _csrf_tamam(request, csrf):
        return HTMLResponse("Geçersiz istek (CSRF)", status_code=403)
    if onay != "EVET":
        return _git("anahtar", hata="Onay kutusuna EVET yazın")
    try:
        await run_in_threadpool(AL.anahtar_uret)
    except Exception as e:
        return _git("anahtar", hata=f"Anahtar üretilemedi: {e}")
    log.warning("Acil lisans imza anahtarı üretildi (%s)", dt.datetime.now().isoformat(timespec="seconds"))
    return _git("anahtar", "Anahtar üretildi. Açık anahtarı programa yazdırın ve özel anahtarı yedekleyin.")
