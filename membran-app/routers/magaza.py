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

from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse, JSONResponse
from fastapi.templating import Jinja2Templates

import db
import cfg
import products as products_lib
from auth import hash_password, verify_password, sign_session, verify_session
from config import BASE_DIR

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))

CUST_COOKIE = "csession"


# --------------------------------------------------------------------------
# Müşteri oturumu
# --------------------------------------------------------------------------
def current_customer(request: Request):
    """Geçerli müşteri satırını döner (role='customer'), yoksa None."""
    username = verify_session(request.cookies.get(CUST_COOKIE, ""))
    if not username:
        return None
    u = db.one("SELECT * FROM membrane_users WHERE username=? AND role='customer'", (username,))
    return u


def _cart(username):
    return db.query("SELECT * FROM shop_cart WHERE customer_user=? ORDER BY id", (username,))


def _cart_count(username):
    r = db.one("SELECT COUNT(*) c FROM shop_cart WHERE customer_user=?", (username,))
    return r["c"] if r else 0


def _ctx(request, cust, **extra):
    base = {"request": request, "cust": cust,
            "cart_count": _cart_count(cust["username"]) if cust else 0}
    base.update(extra)
    return base


# --------------------------------------------------------------------------
# Kayıt / Giriş / Çıkış
# --------------------------------------------------------------------------
@router.get("/magaza/kayit", response_class=HTMLResponse)
async def kayit_page(request: Request):
    if current_customer(request):
        return RedirectResponse("/magaza", status_code=303)
    return templates.TemplateResponse(request, "magaza_kayit.html",
                                      _ctx(request, None, error=""))


@router.post("/magaza/kayit", response_class=HTMLResponse)
async def kayit_submit(request: Request, full_name: str = Form(...), phone: str = Form(""),
                       username: str = Form(...), password: str = Form(...)):
    username = username.strip().lower()
    if not username or not password:
        return templates.TemplateResponse(request, "magaza_kayit.html",
                                          _ctx(request, None, error="E-posta ve şifre zorunlu."))
    exists = db.one("SELECT id FROM membrane_users WHERE username=?", (username,))
    if exists:
        return templates.TemplateResponse(request, "magaza_kayit.html",
                                          _ctx(request, None, error="Bu e-posta zaten kayıtlı. Giriş yapın."))
    db.execute(
        "INSERT INTO membrane_users (username, password_hash, role, full_name, email, phone) "
        "VALUES (?,?, 'customer', ?, ?, ?)",
        (username, hash_password(password), full_name.strip(), username, phone.strip()))
    resp = RedirectResponse("/magaza", status_code=303)
    resp.set_cookie(CUST_COOKIE, sign_session(username), httponly=True,
                    samesite="lax", max_age=60 * 60 * 24 * 30)
    return resp


@router.get("/magaza/giris", response_class=HTMLResponse)
async def giris_page(request: Request):
    if current_customer(request):
        return RedirectResponse("/magaza", status_code=303)
    return templates.TemplateResponse(request, "magaza_giris.html",
                                      _ctx(request, None, error=""))


@router.post("/magaza/giris", response_class=HTMLResponse)
async def giris_submit(request: Request, username: str = Form(...), password: str = Form(...)):
    username = username.strip().lower()
    u = db.one("SELECT * FROM membrane_users WHERE username=? AND role='customer'", (username,))
    if u and verify_password(password, u["password_hash"]):
        resp = RedirectResponse("/magaza", status_code=303)
        resp.set_cookie(CUST_COOKIE, sign_session(username), httponly=True,
                        samesite="lax", max_age=60 * 60 * 24 * 30)
        return resp
    return templates.TemplateResponse(request, "magaza_giris.html",
                                      _ctx(request, None, error="E-posta veya şifre hatalı."))


@router.get("/magaza/cikis")
async def cikis():
    resp = RedirectResponse("/magaza", status_code=303)
    resp.delete_cookie(CUST_COOKIE)
    return resp


# --------------------------------------------------------------------------
# Katalog + ürün yapılandırma
# --------------------------------------------------------------------------
@router.get("/magaza", response_class=HTMLResponse)
async def magaza_home(request: Request):
    cust = current_customer(request)
    prods = products_lib.list_products(only_enabled=True)
    return templates.TemplateResponse(request, "magaza_home.html",
                                      _ctx(request, cust, products=prods))


@router.get("/magaza/urun/{pid}", response_class=HTMLResponse)
async def urun_config(request: Request, pid: str):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris", status_code=303)
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
    cust = current_customer(request)
    if not cust:
        return JSONResponse({"ok": False, "error": "giris"}, status_code=401)
    body = await request.json()
    pid = body.get("product", "")
    params = body.get("params", {})
    prod = products_lib.get(pid)
    pname = (prod or {}).get("name", "Ürün")
    pricing = cfg.price_cabinet(params)
    db.execute(
        "INSERT INTO shop_cart (customer_user, product_id, product_name, params_json, price, qty) "
        "VALUES (?,?,?,?,?,1)",
        (cust["username"], pid, pname, json.dumps(params), pricing["total"]))
    return JSONResponse({"ok": True, "count": _cart_count(cust["username"]),
                         "price": pricing["total"]})


# --------------------------------------------------------------------------
# Sepet
# --------------------------------------------------------------------------
@router.get("/magaza/sepet", response_class=HTMLResponse)
async def sepet(request: Request):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris", status_code=303)
    items = _cart(cust["username"])
    for it in items:
        it["params"] = json.loads(it["params_json"] or "{}")
    total = sum((it["price"] or 0) * (it["qty"] or 1) for it in items)
    return templates.TemplateResponse(request, "magaza_sepet.html",
                                      _ctx(request, cust, items=items, total=round(total, 2)))


@router.post("/magaza/sepet/sil")
async def sepet_sil(request: Request, id: int = Form(...)):
    cust = current_customer(request)
    if cust:
        db.execute("DELETE FROM shop_cart WHERE id=? AND customer_user=?", (id, cust["username"]))
    return RedirectResponse("/magaza/sepet", status_code=303)


# --------------------------------------------------------------------------
# Ödeme (mock) → Vardar sipariş havuzuna düşür
# --------------------------------------------------------------------------
@router.get("/magaza/odeme", response_class=HTMLResponse)
async def odeme_page(request: Request):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris", status_code=303)
    items = _cart(cust["username"])
    if not items:
        return RedirectResponse("/magaza/sepet", status_code=303)
    total = sum((it["price"] or 0) * (it["qty"] or 1) for it in items)
    return templates.TemplateResponse(request, "magaza_odeme.html",
                                      _ctx(request, cust, items=items, total=round(total, 2)))


@router.post("/magaza/odeme")
async def odeme_submit(request: Request, city: str = Form(""), address: str = Form(""),
                       phone: str = Form("")):
    cust = current_customer(request)
    if not cust:
        return RedirectResponse("/magaza/giris", status_code=303)
    items = _cart(cust["username"])
    if not items:
        return RedirectResponse("/magaza/sepet", status_code=303)

    # --- MOCK ÖDEME --- (gerçek sağlayıcı sonra bağlanacak: iyzico/PayTR)
    # Burada kart her zaman "onaylandı" kabul edilir.
    phone = phone.strip() or (cust["phone"] or "")
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
            "material, entered_by, amount, source, config_json, paid, stage) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,1,'havuz')",
            (cust["full_name"] or cust["username"], cust["username"], phone, city,
             area, it["product_name"], "MOBİLYA", "MAĞAZA (online)",
             it["price"] or 0, "magaza", json.dumps({"product": it["product_id"],
                                                      "params": params, "address": address})))
        sn = f"MG {5000 + nid}"
        db.execute("UPDATE vardar_orders SET sn=? WHERE id=?", (sn, nid))
        if not first_sn:
            first_sn = sn

    db.execute("DELETE FROM shop_cart WHERE customer_user=?", (cust["username"],))
    return RedirectResponse(f"/magaza/tamam?sn={first_sn}", status_code=303)


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
    return templates.TemplateResponse(request, "magaza_siparisler.html",
                                      _ctx(request, cust, orders=orders))
