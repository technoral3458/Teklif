"""Yönetici ayarları: sipariş akışı (onay şekli), müşteri istisnaları, hesap (kullanıcı adı / şifre)."""
import os

from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

import db
from auth import hash_password, verify_password, sign_session
from config import BASE_DIR
from routers.kabin import akis_ayar, _ayar_yaz

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))


def _sayfa(request, mesaj="", hata=""):
    musteriler = db.query("SELECT id, username, full_name, phone, onay_sekli, created_at, "
                          "(SELECT COUNT(*) FROM cabinet_orders o WHERE o.musteri=u.username) kabin_siparis "
                          "FROM membrane_users u WHERE role='customer' ORDER BY id DESC")
    return templates.TemplateResponse(request, "ayarlar.html", {
        "request": request, "akis": akis_ayar(), "musteriler": musteriler,
        "kullanici": request.state.user, "mesaj": mesaj, "hata": hata})


@router.get("/titaniumcam", response_class=HTMLResponse)
async def yonetim_kok():
    return RedirectResponse("/titaniumcam/kabin", status_code=303)


@router.get("/titaniumcam/admin", response_class=HTMLResponse)
async def yonetici(request: Request):
    """Yönetici merkezi: yeni sistemin bütün yönetim bölümleri tek sayfada."""
    say = lambda sql, a=(): (db.one(sql, a) or {}).get("n", 0)  # noqa: E731
    sayilar = {
        "bekleyen": say("SELECT COUNT(*) n FROM cabinet_orders WHERE status='bekliyor'"),
        "siparis": say("SELECT COUNT(*) n FROM cabinet_orders"),
        "model": say("SELECT COUNT(*) n FROM kabin_model WHERE acik=1"),
        "musteri": say("SELECT COUNT(*) n FROM membrane_users WHERE role='customer'"),
        "anahtar": say("SELECT COUNT(*) n FROM api_keys WHERE revoked_at IS NULL OR revoked_at=''"),
        "lisans": say("SELECT COUNT(*) n FROM acil_lisanslar"),
    }
    return templates.TemplateResponse(request, "yonetici.html", {"request": request, "s": sayilar, "akis": akis_ayar()})


@router.get("/titaniumcam/ayarlar", response_class=HTMLResponse)
async def ayarlar(request: Request, mesaj: str = ""):
    return _sayfa(request, mesaj=mesaj)


@router.post("/titaniumcam/ayarlar/akis")
async def akis_kaydet(kabin_onay: str = Form("yonetici"), magaza_asama: str = Form("havuz")):
    _ayar_yaz("siparis_akisi", {
        "kabin_onay": kabin_onay if kabin_onay in ("yonetici", "otomatik") else "yonetici",
        "magaza_asama": magaza_asama if magaza_asama in ("havuz", "muhasebe", "planlama") else "havuz"})
    return RedirectResponse("/titaniumcam/ayarlar?mesaj=Sipariş akışı kaydedildi", status_code=303)


@router.post("/titaniumcam/ayarlar/firma")
async def firma_kaydet(ad: str = Form("")):
    ad = " ".join(ad.split())[:60]
    if ad:
        _ayar_yaz("firma", {"ad": ad})
    return RedirectResponse("/titaniumcam/ayarlar?mesaj=Firma adı kaydedildi", status_code=303)


@router.post("/titaniumcam/ayarlar/musteri/{uid}")
async def musteri_onay(uid: int, onay_sekli: str = Form("")):
    db.execute("UPDATE membrane_users SET onay_sekli=? WHERE id=? AND role='customer'",
               (onay_sekli if onay_sekli in ("", "yonetici", "otomatik") else "", uid))
    return RedirectResponse("/titaniumcam/ayarlar?mesaj=Müşteri ayarı kaydedildi#musteriler", status_code=303)


@router.post("/titaniumcam/ayarlar/hesap", response_class=HTMLResponse)
async def hesap(request: Request, mevcut: str = Form(...), yeni_ad: str = Form(""),
                yeni_sifre: str = Form(""), yeni_sifre2: str = Form("")):
    ad = request.state.user
    u = db.one("SELECT * FROM membrane_users WHERE username=? AND role='admin'", (ad,))
    if not u or not verify_password(mevcut, u["password_hash"]):
        return _sayfa(request, hata="Mevcut şifre yanlış")
    yeni_ad = yeni_ad.strip() or ad
    if yeni_ad != ad:
        if len(yeni_ad) < 3 or db.one("SELECT 1 FROM membrane_users WHERE username=?", (yeni_ad,)):
            return _sayfa(request, hata="Bu kullanıcı adı kullanılamaz")
    if yeni_sifre or yeni_sifre2:
        if yeni_sifre != yeni_sifre2:
            return _sayfa(request, hata="Yeni şifreler aynı değil")
        if len(yeni_sifre) < 8:
            return _sayfa(request, hata="Yeni şifre en az 8 karakter olmalı")
        db.execute("UPDATE membrane_users SET password_hash=? WHERE id=?", (hash_password(yeni_sifre), u["id"]))
    if yeni_ad != ad:
        db.execute("UPDATE membrane_users SET username=? WHERE id=?", (yeni_ad, u["id"]))
    resp = RedirectResponse("/titaniumcam/ayarlar?mesaj=Hesap güncellendi", status_code=303)
    resp.set_cookie("session", sign_session(yeni_ad), httponly=True, samesite="lax", max_age=60 * 60 * 24 * 7)
    return resp
