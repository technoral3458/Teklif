"""VARDAR - sipariş & üretim takip sistemi (üretim aşaması havuzları)."""
import os

import json

from fastapi import APIRouter, Request, Form, UploadFile, File
from fastapi.responses import HTMLResponse, RedirectResponse, Response
from fastapi.templating import Jinja2Templates

import db
import doormac
from config import BASE_DIR

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))

# Üretim aşamaları (havuzlar) - sıralı akış
STAGES = [
    ("havuz", "Sipariş Havuzu"),
    ("muhasebe", "Muhasebe Onayı"),
    ("planlama", "Üretim Planlama"),
    ("cnc", "CNC"),
    ("tutkal", "Tutkal"),
    ("pres", "Pres"),
    ("paketleme", "Paketleme"),
    ("fabrika_sevk", "Fabrika Sevkiyat Havuzu"),
    ("sube_sevk", "Şube Sevkiyat Havuzu"),
    ("teslim", "Teslim Edilmiş"),
]
STAGE_KEYS = [s[0] for s in STAGES]
STAGE_LABEL = dict(STAGES)


def _counts():
    rows = db.query("SELECT stage, COUNT(*) c FROM vardar_orders GROUP BY stage")
    cm = {r["stage"]: r["c"] for r in rows}
    return {k: cm.get(k, 0) for k in STAGE_KEYS}


@router.get("/membrane/vardar")
async def vardar_home():
    return RedirectResponse("/membrane/vardar/havuz/havuz", status_code=303)


@router.get("/membrane/vardar/havuz/{stage}", response_class=HTMLResponse)
async def vardar_havuz(request: Request, stage: str, q: str = ""):
    if stage not in STAGE_KEYS:
        stage = "havuz"
    sql = "SELECT * FROM vardar_orders WHERE stage=?"
    args = [stage]
    if q:
        like = f"%{q}%"
        sql += " AND (customer LIKE ? OR sn LIKE ? OR model LIKE ? OR color LIKE ? OR city LIKE ?)"
        args += [like, like, like, like, like]
    sql += " ORDER BY id DESC"
    orders = db.query(sql, tuple(args))
    total_area = sum(o["area"] or 0 for o in orders)
    total_amount = sum(o["amount"] or 0 for o in orders)
    idx = STAGE_KEYS.index(stage)
    return templates.TemplateResponse(request, "vardar_havuz.html", {
        "request": request, "stages": STAGES, "counts": _counts(),
        "stage": stage, "stage_label": STAGE_LABEL[stage], "orders": orders, "q": q,
        "total_area": round(total_area, 2), "total_amount": round(total_amount, 2),
        "is_first": idx == 0, "is_last": idx == len(STAGE_KEYS) - 1,
        "next_label": STAGE_LABEL[STAGE_KEYS[idx + 1]] if idx < len(STAGE_KEYS) - 1 else "",
    })


@router.get("/membrane/vardar/yeni", response_class=HTMLResponse)
async def vardar_yeni(request: Request):
    return templates.TemplateResponse(request, "vardar_yeni.html", {
        "request": request, "stages": STAGES, "counts": _counts(),
    })


@router.post("/membrane/vardar/yeni")
async def vardar_yeni_kaydet(request: Request):
    f = await request.form()
    def g(k, d=""):
        v = f.get(k)
        return d if v is None else v
    nid = db.execute(
        "INSERT INTO vardar_orders (customer, city, area, model, color, color_group, material, "
        "entered_by, amount, term_date, stage) VALUES (?,?,?,?,?,?,?,?,?,?,'havuz')",
        (g("customer"), g("city"), float(g("area", "0") or 0), g("model"), g("color"),
         g("color_group"), g("material", "MEMBRAN"), g("entered_by"),
         float(g("amount", "0") or 0), g("term_date")))
    db.execute("UPDATE vardar_orders SET sn=? WHERE id=?", (f"10 2606 {2000 + nid}", nid))
    return RedirectResponse("/membrane/vardar/havuz/havuz", status_code=303)


@router.post("/membrane/vardar/order/{oid}/ilerlet")
async def vardar_ilerlet(oid: int):
    o = db.one("SELECT stage FROM vardar_orders WHERE id=?", (oid,))
    if o and o["stage"] in STAGE_KEYS:
        i = STAGE_KEYS.index(o["stage"])
        if i < len(STAGE_KEYS) - 1:
            nxt = STAGE_KEYS[i + 1]
            extra = ", approve_date=datetime('now')" if nxt == "planlama" else ""
            db.execute(f"UPDATE vardar_orders SET stage=?{extra} WHERE id=?", (nxt, oid))
    return RedirectResponse(f"/membrane/vardar/havuz/{o['stage'] if o else 'havuz'}", status_code=303)


@router.post("/membrane/vardar/order/{oid}/geri")
async def vardar_geri(oid: int):
    o = db.one("SELECT stage FROM vardar_orders WHERE id=?", (oid,))
    if o and o["stage"] in STAGE_KEYS:
        i = STAGE_KEYS.index(o["stage"])
        if i > 0:
            db.execute("UPDATE vardar_orders SET stage=? WHERE id=?", (STAGE_KEYS[i - 1], oid))
    return RedirectResponse(f"/membrane/vardar/havuz/{o['stage'] if o else 'havuz'}", status_code=303)


@router.post("/membrane/vardar/order/{oid}/sil")
async def vardar_sil(oid: int, stage: str = Form("havuz")):
    db.execute("DELETE FROM vardar_orders WHERE id=?", (oid,))
    return RedirectResponse(f"/membrane/vardar/havuz/{stage}", status_code=303)


# ==========================================================================
# KAPAK MODELLERİ (parametrik - AlphaCAM/AlphaDOOR makroları)
# ==========================================================================
def _door(did):
    r = db.one("SELECT * FROM vardar_doors WHERE id=?", (did,))
    if not r:
        return None, None
    return r, json.loads(r["def_json"])


def _solve(model, qp):
    """Query paramlardan width/length + değişken override alıp çözer + çizim üretir."""
    width = float(qp.get("width", model["width"] or 500))
    length = float(qp.get("length", model["length"] or 720))
    overrides = {}
    for v in model["vars"]:
        if v["formula"] is None and v["name"] in qp:
            try:
                overrides[v["name"]] = float(qp[v["name"]])
            except ValueError:
                pass
    ev = doormac.evaluate(model, width, length, overrides)
    segs = doormac.segments(ev["pts"])
    pts = doormac.profile_points(segs)
    scale = 320.0 / max(width, length)
    poly = " ".join(f"{x * scale:.1f},{(length - y) * scale:.1f}" for x, y in pts)
    return ev, {
        "poly": poly, "pw": width * scale, "ph": length * scale,
        "width": width, "length": length, "overrides": overrides,
    }


@router.get("/membrane/kapak", response_class=HTMLResponse)
async def kapak_list(request: Request):
    doors = db.query("SELECT id, name, created_at FROM vardar_doors ORDER BY name")
    return templates.TemplateResponse(request, "kapak_list.html", {
        "request": request, "doors": doors, "stages": STAGES, "counts": _counts(),
    })


@router.get("/membrane/kapak/{did}", response_class=HTMLResponse)
async def kapak_detail(request: Request, did: int):
    row, model = _door(did)
    if not model:
        return RedirectResponse("/membrane/kapak", status_code=303)
    ev, draw = _solve(model, request.query_params)
    return templates.TemplateResponse(request, "kapak_detail.html", {
        "request": request, "door": row, "model": model, "ev": ev, "draw": draw,
    })


@router.get("/membrane/kapak/{did}/gcode")
async def kapak_gcode(request: Request, did: int):
    row, model = _door(did)
    if not model:
        return RedirectResponse("/membrane/kapak", status_code=303)
    ev, draw = _solve(model, request.query_params)
    depth = float(request.query_params.get("depth", 8))
    nc = doormac.gcode(row["name"], ev, depth=depth)
    fn = f"{row['name']}_{int(draw['width'])}x{int(draw['length'])}.nc"
    return Response(nc, media_type="text/plain",
                    headers={"Content-Disposition": f'attachment; filename="{fn}"'})


@router.post("/membrane/kapak/import")
async def kapak_import(name: str = Form(""), macro: UploadFile = File(...)):
    raw = (await macro.read())
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        text = raw.decode("latin-5", errors="replace")  # Türkçe Windows kodlaması
    model = doormac.parse_adoormac(text)
    dname = name.strip() or (macro.filename or "Kapak").rsplit(".", 1)[0]
    db.execute("INSERT INTO vardar_doors (name, def_json) VALUES (?,?)",
               (dname, json.dumps(model)))
    return RedirectResponse("/membrane/kapak", status_code=303)
