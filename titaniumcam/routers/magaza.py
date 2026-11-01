"""
MAĞAZA - müşteriye açık online sipariş akışı (Faz 1).

Akış: kayıt/giriş → ürün yapılandır (canlı fiyat) → sepet → ödeme (mock)
      → ödeme başarılı → Vardar sipariş havuzuna otomatik düşer.

Admin tarafından tamamen ayrıdır:
- Müşteri oturumu ayrı çerezde tutulur: 'csession' (admin: 'session').
- Fiyat ve katalog admin'in girdiği cfg_prices / cfg_products üzerinden gelir.
"""
import json
import os
import uuid

from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse, JSONResponse
from fastapi.templating import Jinja2Templates

import db
import cfg
import products as products_lib
from auth import hash_password, verify_password, sign_session, verify_session
from config import BASE_DIR, GOOGLE_CLIENT_ID, SOSYAL_TEST

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))

CUST_COOKIE = "csession"   # giriş yapmış müşteri
GUEST_COOKIE = "gcart"     # misafir sepet anahtarı


# --------------------------------------------------------------------------
# Müşteri / misafir oturumu
# --------------------------------------------------------------------------
def current_customer(request: Request):
    """Geçerli müşteri satırını döner (role='customer'), yoksa None."""
    username = verify_session(request.cookies.get(CUST_COOKIE, ""))
    if not username:
        return None
    u = db.one("SELECT * FROM membrane_users WHERE username=? AND role='customer'", (username,))
    return u


def _owner(request: Request):
    """Sepet sahibi anahtarı. (key, is_guest, cust)
    Giriş yapmışsa kullanıcı adı; değilse misafir çerez anahtarı (yoksa None)."""
    cust = current_customer(request)
    if cust:
        return cust["username"], False, cust
    token = request.cookies.get(GUEST_COOKIE, "")
    return (f"guest:{token}" if token else None), True, None


def _ensure_guest_token(request: Request):
    """Misafir için sepet anahtarı üretir; (owner_key, yeni_token_or_None)."""
    token = request.cookies.get(GUEST_COOKIE, "")
    new = None
    if not token:
        token = uuid.uuid4().hex
        new = token
    return f"guest:{token}", new


def _absorb_guest_cart(request: Request, username: str):
    """Misafirken eklenen sepet ürünlerini giriş yapan kullanıcıya aktar."""
    token = request.cookies.get(GUEST_COOKIE, "")
    if token:
        db.execute("UPDATE shop_cart SET customer_user=? WHERE customer_user=?",
                   (username, f"guest:{token}"))
        # misafirken tasarlanan dolaplar (Online Kabin) da hesaba geçer
        db.execute("UPDATE kabin_taslak SET musteri=? WHERE musteri=?",
                   (username, f"guest:{token}"))


def _cart(owner):
    if not owner:
        return []
    return db.query("SELECT * FROM shop_cart WHERE customer_user=? ORDER BY id", (owner,))


def _cart_count(owner):
    if not owner:
        return 0
    r = db.one("SELECT COUNT(*) c FROM shop_cart WHERE customer_user=?", (owner,))
    return r["c"] if r else 0


def _ctx(request, cust, **extra):
    owner, _is_guest, _c = _owner(request)
    dolap = db.one("SELECT COUNT(*) c FROM kabin_taslak WHERE musteri=? AND sepette=1", (owner,)) if owner else None
    base = {"request": request, "cust": cust, "google_client_id": GOOGLE_CLIENT_ID, "sosyal_test": SOSYAL_TEST,
            "cart_count": _cart_count(owner) + (dolap["c"] if dolap else 0)}
    base.update(extra)
    return base


# --------------------------------------------------------------------------
# Kayıt / Giriş / Çıkış
# --------------------------------------------------------------------------
@router.get("/magaza/kayit", response_class=HTMLResponse)
async def kayit_page(request: Request, next: str = "/magaza"):
    if current_customer(request):
        return RedirectResponse("/magaza", status_code=303)
    return templates.TemplateResponse(request, "magaza_kayit.html",
                                      _ctx(request, None, error="", next=next))


@router.post("/magaza/kayit", response_class=HTMLResponse)
async def kayit_submit(request: Request, full_name: str = Form(...), phone: str = Form(""),
                       username: str = Form(...), password: str = Form(...),
                       next: str = Form("/magaza")):
    username = username.strip().lower()
    if not username or not password:
        return templates.TemplateResponse(request, "magaza_kayit.html",
                                          _ctx(request, None, error="E-posta ve şifre zorunlu.", next=next))
    exists = db.one("SELECT id FROM membrane_users WHERE username=?", (username,))
    if exists:
        return templates.TemplateResponse(request, "magaza_kayit.html",
                                          _ctx(request, None, error="Bu e-posta zaten kayıtlı. Giriş yapın.", next=next))
    db.execute(
        "INSERT INTO membrane_users (username, password_hash, role, full_name, email, phone) "
        "VALUES (?,?, 'customer', ?, ?, ?)",
        (username, hash_password(password), full_name.strip(), username, phone.strip()))
    _absorb_guest_cart(request, username)
    resp = RedirectResponse(next if next.startswith("/magaza") else "/magaza", status_code=303)
    resp.set_cookie(CUST_COOKIE, sign_session(username), httponly=True,
                    samesite="lax", max_age=60 * 60 * 24 * 30)
    resp.delete_cookie(GUEST_COOKIE)
    return resp


@router.get("/magaza/giris", response_class=HTMLResponse)
async def giris_page(request: Request, next: str = "/magaza"):
    if current_customer(request):
        return RedirectResponse("/magaza", status_code=303)
    return templates.TemplateResponse(request, "magaza_giris.html",
                                      _ctx(request, None, error="", next=next))


@router.post("/magaza/giris", response_class=HTMLResponse)
async def giris_submit(request: Request, username: str = Form(...), password: str = Form(...),
                       next: str = Form("/magaza")):
    username = username.strip().lower()
    u = db.one("SELECT * FROM membrane_users WHERE username=? AND role='customer'", (username,))
    if u and verify_password(password, u["password_hash"]):
        _absorb_guest_cart(request, username)
        resp = RedirectResponse(next if next.startswith("/magaza") else "/magaza", status_code=303)
        resp.set_cookie(CUST_COOKIE, sign_session(username), httponly=True,
                        samesite="lax", max_age=60 * 60 * 24 * 30)
        resp.delete_cookie(GUEST_COOKIE)
        return resp
    return templates.TemplateResponse(request, "magaza_giris.html",
                                      _ctx(request, None, error="E-posta veya şifre hatalı.", next=next))


@router.get("/magaza/cikis")
async def cikis():
    resp = RedirectResponse("/?cikis=1", status_code=303)    # cikis=1: Google otomatik girişi kapatır
    resp.delete_cookie(CUST_COOKIE)
    return resp


def _guvenli_sonraki(next_url, varsayilan="/"):
    return next_url if isinstance(next_url, str) and (next_url == "/" or next_url.startswith(("/magaza", "/kabin"))) \
        and not next_url.startswith("//") else varsayilan


async def google_dogrula(token):
    """Google ID token'ını Google'a doğrulatır. Döner: {email, name, sub} ya da ValueError."""
    if not GOOGLE_CLIENT_ID:
        raise ValueError("Google girişi ayarlı değil")
    if not isinstance(token, str) or len(token) > 5000:
        raise ValueError("Geçersiz Google cevabı")
    import time
    import httpx
    try:
        async with httpx.AsyncClient(timeout=10) as c:
            r = await c.get("https://oauth2.googleapis.com/tokeninfo", params={"id_token": token})
    except httpx.HTTPError:
        raise ValueError("Google'a ulaşılamadı")
    if r.status_code != 200:
        raise ValueError("Google girişi doğrulanamadı")
    d = r.json()
    if d.get("aud") != GOOGLE_CLIENT_ID:
        raise ValueError("Google girişi bu siteye ait değil")
    if d.get("iss") not in ("accounts.google.com", "https://accounts.google.com"):
        raise ValueError("Google girişi doğrulanamadı")
    if str(d.get("email_verified")).lower() != "true" or not d.get("email"):
        raise ValueError("Google e-postası doğrulanmamış")
    if int(d.get("exp", 0)) < time.time():
        raise ValueError("Google girişinin süresi dolmuş")
    return {"email": d["email"].strip().lower(), "name": d.get("name", ""), "sub": d.get("sub", "")}


def _musteri_oturumu(request, email, ad, url):
    """E-postası doğrulanmış kişiyi müşteri olarak açar/giriş yaptırır."""
    u = db.one("SELECT * FROM membrane_users WHERE username=?", (email,))
    if u and u["role"] != "customer":
        return None                                         # yönetici hesabıyla çakışma: reddet
    if not u:
        db.execute(
            "INSERT INTO membrane_users (username, password_hash, role, full_name, email) "
            "VALUES (?,?, 'customer', ?, ?)",
            (email, hash_password(uuid.uuid4().hex), (ad or "").strip() or email.split("@")[0], email))
    _absorb_guest_cart(request, email)
    return url


@router.post("/magaza/google")
async def google_giris(request: Request):
    """Google Identity Services (One Tap / 'Google ile devam') geri çağrısı."""
    try:
        govde = await request.json()
    except ValueError:
        return JSONResponse({"error": "Geçersiz istek"}, status_code=400)
    try:
        g = await google_dogrula(govde.get("credential"))
    except ValueError as e:
        return JSONResponse({"error": str(e)}, status_code=401)
    url = _musteri_oturumu(request, g["email"], g["name"], _guvenli_sonraki(govde.get("next")))
    if not url:
        return JSONResponse({"error": "Bu e-posta ile müşteri girişi yapılamaz"}, status_code=403)
    resp = JSONResponse({"ok": True, "url": url})
    resp.set_cookie(CUST_COOKIE, sign_session(g["email"]), httponly=True,
                    samesite="lax", max_age=60 * 60 * 24 * 30)
    resp.delete_cookie(GUEST_COOKIE)
    return resp


@router.post("/magaza/sosyal")
async def sosyal(request: Request, provider: str = Form(...), email: str = Form(...),
                 full_name: str = Form(""), next: str = Form("/magaza/odeme")):
    """TEST modu: şifresiz e-posta ile giriş. Yalnız SOSYAL_TEST=1 iken çalışır;
    canlıda kapalıdır (aksi halde herkes başkasının e-postasıyla girebilir)."""
    if not SOSYAL_TEST:
        return RedirectResponse("/magaza/giris", status_code=303)
    email = email.strip().lower()
    if not email or "@" not in email:
        return RedirectResponse("/magaza/odeme", status_code=303)
    if not _musteri_oturumu(request, email, full_name, "/"):
        return RedirectResponse("/magaza/giris", status_code=303)
    resp = RedirectResponse(next if next.startswith("/magaza") else "/magaza/odeme", status_code=303)
    resp.set_cookie(CUST_COOKIE, sign_session(email), httponly=True,
                    samesite="lax", max_age=60 * 60 * 24 * 30)
    resp.delete_cookie(GUEST_COOKIE)
    return resp


# --------------------------------------------------------------------------
# Katalog + ürün yapılandırma
# --------------------------------------------------------------------------
@router.get("/magaza", response_class=HTMLResponse)
async def magaza_home(request: Request):
    cust = current_customer(request)
    prods = products_lib.list_products(only_enabled=True)
    dolap_hata = ""
    try:                                  # Online Kabin şablonları motor servisinden gelir
        import kabin_istemci as KI
        from routers.kabin import musteri_bilgisi, magaza_modelleri
        dolaplar = musteri_bilgisi(await KI.bilgi())["kabin_sablonlari"]
        modeller = magaza_modelleri()
    except Exception as e:
        dolaplar, modeller = [], []
        dolap_hata = getattr(e, "mesaj", "") or "Kabin motoru şu an çalışmıyor"
    owner, _g, _c = _owner(request)
    r = db.one("SELECT COUNT(*) c FROM kabin_taslak WHERE musteri=?", (owner,)) if owner else None
    return templates.TemplateResponse(request, "magaza_home.html",
                                      _ctx(request, cust, products=prods, dolaplar=dolaplar, modeller=modeller,
                                           dolap_hata=dolap_hata, taslak_sayisi=r["c"] if r else 0))


@router.get("/magaza/urun/{pid}", response_class=HTMLResponse)
async def urun_config(request: Request, pid: str):
    # Misafir de tasarlayabilir — giriş yalnızca ödeme aşamasında istenir.
    cust = current_customer(request)
    prod = products_lib.get(pid)
    if not prod:
        return RedirectResponse("/magaza", status_code=303)
    return templates.TemplateResponse(request, "magaza_urun.html",
                                      _ctx(request, cust, prod=prod, colors=cfg.get_colors()))


@router.post("/magaza/fiyat")
async def magaza_fiyat(request: Request):
    """Canlı fiyat (JSON). Admin'in girdiği cfg_prices üzerinden hesaplar."""
    body = await request.json()
    return JSONResponse(cfg.price_cabinet(body.get("params", body)))


@router.post("/magaza/sepet/ekle")
async def sepet_ekle(request: Request):
    # Misafir de sepete ekleyebilir; giriş yoksa misafir çerezi atanır.
    cust = current_customer(request)
    new_token = None
    if cust:
        owner = cust["username"]
    else:
        owner, new_token = _ensure_guest_token(request)
    body = await request.json()
    pid = body.get("product", "")
    params = body.get("params", {})
    prod = products_lib.get(pid)
    pname = (prod or {}).get("name", "Ürün")
    pricing = cfg.price_cabinet(params)
    db.execute(
        "INSERT INTO shop_cart (customer_user, product_id, product_name, params_json, price, qty) "
        "VALUES (?,?,?,?,?,1)",
        (owner, pid, pname, json.dumps(params), pricing["total"]))
    resp = JSONResponse({"ok": True, "count": _cart_count(owner), "price": pricing["total"]})
    if new_token:
        resp.set_cookie(GUEST_COOKIE, new_token, httponly=True, samesite="lax",
                        max_age=60 * 60 * 24 * 7)
    return resp


# --------------------------------------------------------------------------
# Sepet
# --------------------------------------------------------------------------
@router.get("/magaza/sepet", response_class=HTMLResponse)
async def sepet(request: Request):
    owner, _g, cust = _owner(request)
    items = _cart(owner)
    for it in items:
        it["params"] = json.loads(it["params_json"] or "{}")
    total = sum((it["price"] or 0) * (it["qty"] or 1) for it in items)
    dolaplar = db.query("SELECT * FROM kabin_taslak WHERE musteri=? AND sepette=1 ORDER BY id",
                        (owner,)) if owner else []
    for d in dolaplar:
        d["ozet"] = json.loads(d["ozet_json"] or "{}")
    return templates.TemplateResponse(request, "magaza_sepet.html",
                                      _ctx(request, cust, items=items, total=round(total, 2),
                                           dolaplar=dolaplar))


@router.post("/magaza/sepet/sil")
async def sepet_sil(request: Request, id: int = Form(...)):
    owner, _g, _c = _owner(request)
    if owner:
        db.execute("DELETE FROM shop_cart WHERE id=? AND customer_user=?", (id, owner))
    return RedirectResponse("/magaza/sepet", status_code=303)


# --------------------------------------------------------------------------
# Ödeme (mock) → Vardar sipariş havuzuna düşür
# --------------------------------------------------------------------------
@router.get("/magaza/odeme", response_class=HTMLResponse)
async def odeme_page(request: Request, misafir: int = 0):
    owner, is_guest, cust = _owner(request)
    items = _cart(owner)
    if not items:
        return RedirectResponse("/magaza/sepet", status_code=303)
    total = round(sum((it["price"] or 0) * (it["qty"] or 1) for it in items), 2)
    # Giriş ekranı ÖDEME aşamasında çıkar: misafir ve "misafir devam" seçilmediyse.
    if cust is None and not misafir:
        return templates.TemplateResponse(request, "magaza_hesap.html",
                                          _ctx(request, None, items=items, total=total))
    return templates.TemplateResponse(request, "magaza_odeme.html",
                                      _ctx(request, cust, items=items, total=total, guest=(cust is None)))


@router.post("/magaza/odeme")
async def odeme_submit(request: Request, full_name: str = Form(""), email: str = Form(""),
                       city: str = Form(""), address: str = Form(""), phone: str = Form("")):
    owner, is_guest, cust = _owner(request)
    items = _cart(owner)
    if not items:
        return RedirectResponse("/magaza/sepet", status_code=303)

    # --- MOCK ÖDEME --- (gerçek sağlayıcı sonra bağlanacak: iyzico/PayTR)
    # Burada kart her zaman "onaylandı" kabul edilir.
    if cust:
        buyer_name = cust["full_name"] or cust["username"]
        buyer_user = cust["username"]
        buyer_email = cust["email"] or cust["username"]
        buyer_phone = phone.strip() or (cust["phone"] or "")
    else:
        buyer_name = full_name.strip() or "Misafir Müşteri"
        buyer_user = ""           # misafir (hesapsız)
        buyer_email = email.strip().lower()
        buyer_phone = phone.strip()

    from routers.kabin import akis_ayar
    from datetime import datetime
    asama = akis_ayar()["magaza_asama"]          # yönetici ayarı: siparişin düşeceği Vardar aşaması
    first_sn = ""
    for it in items:
        params = json.loads(it["params_json"] or "{}")
        try:
            W = float(params.get("W", 0)); H = float(params.get("H", 0))
            area = round((W / 1000.0) * (H / 1000.0), 3)
        except (TypeError, ValueError):
            area = 0
        nid = db.execute(
            "INSERT INTO vardar_orders (customer, customer_user, phone, city, area, model, "
            "material, entered_by, amount, source, config_json, paid, stage, approve_date) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,1,?,?)",
            (buyer_name, buyer_user, buyer_phone, city, area, it["product_name"],
             "MOBİLYA", "MAĞAZA (online)", it["price"] or 0, "magaza",
             json.dumps({"product": it["product_id"], "params": params,
                         "email": buyer_email, "address": address, "guest": is_guest}),
             asama, datetime.now().strftime("%Y-%m-%d %H:%M:%S") if asama == "planlama" else ""))
        sn = f"MG {5000 + nid}"
        db.execute("UPDATE vardar_orders SET sn=? WHERE id=?", (sn, nid))
        if not first_sn:
            first_sn = sn

    db.execute("DELETE FROM shop_cart WHERE customer_user=?", (owner,))
    resp = RedirectResponse(f"/magaza/tamam?sn={first_sn}", status_code=303)
    if is_guest:
        resp.delete_cookie(GUEST_COOKIE)
    return resp


@router.get("/magaza/tamam", response_class=HTMLResponse)
async def tamam(request: Request, sn: str = ""):
    cust = current_customer(request)
    return templates.TemplateResponse(request, "magaza_tamam.html",
                                      _ctx(request, cust, sn=sn))


# --------------------------------------------------------------------------
# Siparişlerim
# --------------------------------------------------------------------------
@router.get("/magaza/siparislerim", response_class=HTMLResponse)
async def siparislerim(request: Request):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris", status_code=303)
    from routers.vardar import STAGE_LABEL
    orders = db.query("SELECT * FROM vardar_orders WHERE customer_user=? ORDER BY id DESC",
                      (cust["username"],))
    for o in orders:
        o["stage_label"] = STAGE_LABEL.get(o["stage"], o["stage"])
    kabin = db.query("SELECT o.*, (SELECT COALESCE(SUM(qty),0) FROM cabinet_order_lines l "
                     "WHERE l.order_id=o.id) dolap FROM cabinet_orders o WHERE musteri=? ORDER BY id DESC",
                     (cust["username"],))
    return templates.TemplateResponse(request, "magaza_siparisler.html",
                                      _ctx(request, cust, orders=orders, kabin=kabin))


# Yasal sayfalar (Google giriş onay ekranı bunları ister)
@router.get("/gizlilik", response_class=HTMLResponse)
async def gizlilik(request: Request):
    return templates.TemplateResponse(request, "magaza_yasal.html",
                                      _ctx(request, current_customer(request), tur="gizlilik",
                                           baslik="Gizlilik Politikası"))


@router.get("/kosullar", response_class=HTMLResponse)
async def kosullar(request: Request):
    return templates.TemplateResponse(request, "magaza_yasal.html",
                                      _ctx(request, current_customer(request), tur="kosullar",
                                           baslik="Kullanım Koşulları"))
