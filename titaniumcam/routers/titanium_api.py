"""
TitaniumCam API — /api/titanium/v1

TitaniumCam (atölyedeki program) onaylı dolap siparişlerini buradan çeker:
    GET  /api/titanium/v1/cabinet-orders?status=approved&unclaimed=1&station=CNC-1
    POST /api/titanium/v1/cabinet-orders/{id}/claim     {"station","app","version"}
    POST /api/titanium/v1/cabinet-orders/{id}/status    {"station","status","note"}

Kimlik: "Authorization: Bearer <anahtar>" + "X-Titanium-Station: <istasyon>".
Anahtar yönetici panelinden (/titaniumcam/api) üretilir; veritabanında yalnız SHA-256 özeti durur.
Yalnız HTTPS (nginx X-Forwarded-Proto). Anahtar başına dakikada 120 istek.
Hata biçimi: {"ok": false, "error": "..."}  (+ claim çakışmasında "claimed_by").
"""
import hashlib
import json
import os
import secrets
import time
from collections import defaultdict, deque

from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

import db
from config import BASE_DIR

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))

ONEK = "/api/titanium/v1"
DAKIKA_LIMIT = 120
DURUMLAR = ("nested", "in_production", "done", "released")
# Yerel deneme için HTTP'ye izin (canlıda kapalı kalmalı)
HTTP_IZIN = os.environ.get("TITANIUM_API_HTTP", "") == "1"


def _hata(mesaj, kod, **ek):
    return JSONResponse({"ok": False, "error": mesaj, **ek}, status_code=kod)


def anahtar_ozeti(anahtar):
    return hashlib.sha256(anahtar.encode()).hexdigest()


_HIZ = defaultdict(deque)


def _dogrula(request, izin="kabin"):
    """Döner: (anahtar_satiri, istasyon) ya da JSONResponse (hata)."""
    proto = request.headers.get("x-forwarded-proto", request.url.scheme)
    if proto != "https" and not HTTP_IZIN:
        return _hata("Yalnız HTTPS", 403)
    yetki = request.headers.get("authorization", "")
    if not yetki.lower().startswith("bearer "):
        return _hata("Yetkisiz", 401)
    k = db.one("SELECT * FROM api_keys WHERE ozet=?", (anahtar_ozeti(yetki[7:].strip()),))
    if not k or k["revoked_at"]:
        return _hata("Yetkisiz", 401)
    if not k[izin]:
        return _hata("Bu anahtarın " + ("kabin" if izin == "kabin" else "kapak") + " izni yok", 403)
    q, simdi = _HIZ[k["id"]], time.time()
    while q and simdi - q[0] > 60:
        q.popleft()
    if len(q) >= DAKIKA_LIMIT:
        return _hata("Dakikada en çok 120 istek", 429)
    q.append(simdi)
    db.execute("UPDATE api_keys SET last_used_at=datetime('now') WHERE id=?", (k["id"],))
    istasyon = (request.headers.get("x-titanium-station") or "").strip()[:40]
    return k, istasyon


def _siparis_id(id_):
    """'K-1001' ya da '1001' -> 1001"""
    s = str(id_).strip()
    if s.upper().startswith("K-"):
        s = s[2:]
    return int(s) if s.isdigit() else None


def _zaman(t):
    """SQLite 'YYYY-MM-DD HH:MM:SS' (yerel saat) -> ISO 8601 +03:00"""
    return (t.replace(" ", "T") + "+03:00") if t else None


def _siparis_json(o):
    lines = []
    for l in db.query("SELECT * FROM cabinet_order_lines WHERE order_id=? ORDER BY line_no", (o["id"],)):
        tk = json.loads(l["tkabin"])
        tk["kabin"]["adet"] = int(l["qty"])          # qty ile tkabin.kabin.adet aynı olmalı
        lines.append({"line_no": l["line_no"], "cabinet_no": l["cabinet_no"], "name": l["name"],
                      "qty": int(l["qty"]), "width_mm": float(l["width_mm"]), "height_mm": float(l["height_mm"]),
                      "depth_mm": float(l["depth_mm"]), "engine_version": l["engine_version"],
                      "definitions_version": l["definitions_version"], "note": l["note"] or "", "tkabin": tk})
    return {"id": f"K-{o['id']}", "order_no": o["order_no"], "customer": o["musteri_ad"] or o["musteri"],
            "project": o["proje"] or "", "approved_at": _zaman(o["approved_at"]), "note": o["note"] or "",
            "claimed_by": o["claimed_by"] or None, "production_status": o["production_status"] or None,
            "changed_after_claim": bool(o["degisti"]), "lines": lines}


async def _govde(request):
    try:
        g = await request.json()
        return g if isinstance(g, dict) else {}
    except ValueError:
        return None


def _log(oid, istasyon, olay, note=""):
    db.execute("INSERT INTO cabinet_order_log (order_id, station, olay, note) VALUES (?,?,?,?)",
               (oid, istasyon, olay, str(note)[:500]))


# ══════════════════════════════════════════════════════════════════════════
# Uçlar
# ══════════════════════════════════════════════════════════════════════════
@router.get(ONEK + "/cabinet-orders")
async def cabinet_orders(request: Request, status: str = "approved", unclaimed: int = 1, station: str = ""):
    d = _dogrula(request)
    if isinstance(d, JSONResponse):
        return d
    if status != "approved":
        return _hata("Yalnız status=approved desteklenir", 400)
    sql = "SELECT * FROM cabinet_orders WHERE status='onayli'"
    args = []
    if unclaimed:
        sql += " AND claimed_by=''"
    elif station:
        sql += " AND claimed_by IN ('', ?)"
        args.append(station.strip()[:40])
    rows = db.query(sql + " ORDER BY approved_at, id", tuple(args))
    return {"orders": [_siparis_json(o) for o in rows]}


@router.get(ONEK + "/cabinet-orders/{id_}")
async def cabinet_order(request: Request, id_: str):
    d = _dogrula(request)
    if isinstance(d, JSONResponse):
        return d
    oid = _siparis_id(id_)
    o = db.one("SELECT * FROM cabinet_orders WHERE id=? AND status='onayli'", (oid,)) if oid else None
    if not o:
        return _hata("Sipariş yok ya da onaylı değil", 404)
    return _siparis_json(o)


@router.post(ONEK + "/cabinet-orders/{id_}/claim")
async def claim(request: Request, id_: str):
    d = _dogrula(request)
    if isinstance(d, JSONResponse):
        return d
    _k, baslik_ist = d
    g = await _govde(request)
    if g is None:
        return _hata("Geçersiz JSON", 400)
    istasyon = str(g.get("station") or baslik_ist).strip()[:40]
    if not istasyon:
        return _hata("station gerekli", 400)
    oid = _siparis_id(id_)
    o = db.one("SELECT id FROM cabinet_orders WHERE id=? AND status='onayli'", (oid,)) if oid else None
    if not o:
        return _hata("Sipariş yok ya da onaylı değil", 404)
    # Tek ifadede (atomik): yalnız boşsa ya da zaten bu istasyondaysa yazılır
    db.execute("UPDATE cabinet_orders SET claimed_by=?, claimed_at=CASE WHEN claimed_by='' "
               "THEN datetime('now','localtime') ELSE claimed_at END, production_status=CASE WHEN claimed_by='' "
               "THEN 'claimed' ELSE production_status END WHERE id=? AND claimed_by IN ('', ?)",
               (istasyon, oid, istasyon))
    o = db.one("SELECT claimed_by, claimed_at FROM cabinet_orders WHERE id=?", (oid,))
    if o["claimed_by"] != istasyon:
        return _hata("Başka istasyon aldı", 409, claimed_by=o["claimed_by"])
    _log(oid, istasyon, "claim", f"{g.get('app', '')} {g.get('version', '')}".strip())
    return {"ok": True, "id": f"K-{oid}", "claimed_by": istasyon, "claimed_at": _zaman(o["claimed_at"])}


@router.post(ONEK + "/cabinet-orders/{id_}/status")
async def durum(request: Request, id_: str):
    d = _dogrula(request)
    if isinstance(d, JSONResponse):
        return d
    _k, baslik_ist = d
    g = await _govde(request)
    if g is None:
        return _hata("Geçersiz JSON", 400)
    istasyon = str(g.get("station") or baslik_ist).strip()[:40]
    yeni = g.get("status")
    if yeni not in DURUMLAR:
        return _hata("status: " + ", ".join(DURUMLAR), 400)
    oid = _siparis_id(id_)
    o = db.one("SELECT * FROM cabinet_orders WHERE id=? AND status='onayli'", (oid,)) if oid else None
    if not o:
        return _hata("Sipariş yok ya da onaylı değil", 404)
    if o["claimed_by"] != istasyon:
        return _hata("Sipariş bu istasyonda değil", 409, claimed_by=o["claimed_by"] or None)
    if yeni == "released":                       # havuza geri koy
        db.execute("UPDATE cabinet_orders SET claimed_by='', claimed_at='', production_status='released' "
                   "WHERE id=?", (oid,))
    else:
        db.execute("UPDATE cabinet_orders SET production_status=? WHERE id=?", (yeni, oid))
    _log(oid, istasyon, yeni, g.get("note", ""))
    return {"ok": True, "id": f"K-{oid}", "status": yeni}


# ══════════════════════════════════════════════════════════════════════════
# Yönetici: API anahtarları
# ══════════════════════════════════════════════════════════════════════════
@router.get("/titaniumcam/api", response_class=HTMLResponse)
async def y_api(request: Request):
    keys = db.query("SELECT id, ad, onek, kapak, kabin, created_at, last_used_at, revoked_at "
                    "FROM api_keys ORDER BY id DESC")
    return templates.TemplateResponse(request, "api_anahtarlar.html", {
        "request": request, "keys": keys, "yeni": None, "onek": ONEK})


@router.post("/titaniumcam/api/yeni", response_class=HTMLResponse)
async def y_api_yeni(request: Request, ad: str = Form(""), kabin: str = Form(""), kapak: str = Form("")):
    anahtar = "tc_" + secrets.token_urlsafe(32)
    db.execute("INSERT INTO api_keys (ad, onek, ozet, kapak, kabin) VALUES (?,?,?,?,?)",
               (ad.strip()[:60] or "TitaniumCam", anahtar[:10], anahtar_ozeti(anahtar),
                1 if kapak else 0, 1 if kabin else 0))
    keys = db.query("SELECT id, ad, onek, kapak, kabin, created_at, last_used_at, revoked_at "
                    "FROM api_keys ORDER BY id DESC")
    # Anahtar yalnız bu cevapta görünür; bir daha gösterilmez
    return templates.TemplateResponse(request, "api_anahtarlar.html", {
        "request": request, "keys": keys, "yeni": anahtar, "onek": ONEK})


@router.post("/titaniumcam/api/{kid}/iptal")
async def y_api_iptal(kid: int):
    db.execute("UPDATE api_keys SET revoked_at=datetime('now') WHERE id=? AND revoked_at=''", (kid,))
    return RedirectResponse("/titaniumcam/api", status_code=303)
