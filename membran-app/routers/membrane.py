"""Membran Kapak Yönetim Sistemi - tüm HTTP uçları."""
import json
import os

import httpx
from fastapi import APIRouter, Request, Form, UploadFile, File
from fastapi.responses import (HTMLResponse, RedirectResponse, JSONResponse,
                               PlainTextResponse, Response)
from fastapi.templating import Jinja2Templates
from starlette.concurrency import run_in_threadpool
from xml.etree import ElementTree

import db
from config import BASE_DIR
from costs import material_cost_per_m2, total_cost_per_m2, door_cost
from nesting import nest
from gcode import generate_model_nc, generate_job_nc
import templates_lib
import cfg
import products as products_lib
import dxf_utils

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))


# --------------------------------------------------------------------------
# Yardımcılar
# --------------------------------------------------------------------------
def get_rates():
    """{'USD': 32.1, ...} biçiminde kur sözlüğü."""
    return {r["currency"]: r["rate_to_try"] for r in
            db.query("SELECT currency, rate_to_try FROM membrane_rates")}


def get_materials():
    return db.query("SELECT * FROM membrane_materials ORDER BY id")


def get_model_bundle(mid):
    """Model + sabitler + ops + her op'un hareketleri."""
    model = db.one("SELECT * FROM membrane_cap_models WHERE id=?", (mid,))
    if not model:
        return None
    try:
        model["_constants"] = json.loads(model.get("constants_json") or "{}")
    except Exception:
        model["_constants"] = {}
    ops = db.query("SELECT * FROM membrane_cap_ops WHERE model_id=? ORDER BY seq, id", (mid,))
    moves = {}
    for op in ops:
        moves[op["id"]] = db.query(
            "SELECT * FROM membrane_cap_moves WHERE op_id=? ORDER BY seq, id", (op["id"],))
    return {"model": model, "ops": ops, "moves": moves}


def _f(form, key, default=""):
    v = form.get(key)
    return default if v is None else v


# ==========================================================================
# MODÜL 1: MALİYET
# ==========================================================================
@router.get("/membrane", response_class=HTMLResponse)
async def membrane_home(request: Request):
    rates = get_rates()
    materials = get_materials()
    for m in materials:
        m["cost_per_m2"] = material_cost_per_m2(m, rates)
    total_m2 = total_cost_per_m2(materials, rates)
    lists = db.query("SELECT * FROM membrane_lists ORDER BY id DESC")
    for lst in lists:
        doors = db.query("SELECT * FROM membrane_doors WHERE list_id=?", (lst["id"],))
        lst["door_count"] = sum(int(d["quantity"]) for d in doors)
        lst["total_cost"] = sum(door_cost(d, total_m2)["total_cost"] for d in doors)
    return templates.TemplateResponse(request, "membrane.html", {
        "request": request, "rates": rates, "materials": materials,
        "total_m2": total_m2, "lists": lists,
    })


@router.post("/membrane/rates/fetch")
async def rates_fetch():
    try:
        def _fetch():
            resp = httpx.get("https://www.tcmb.gov.tr/kurlar/today.xml", timeout=8)
            root = ElementTree.fromstring(resp.content)
            out = {}
            for item in root.findall(".//Currency"):
                code = item.get("CurrencyCode")
                if code in ("USD", "EUR", "GBP"):
                    selling = item.findtext("ForexSelling") or item.findtext("BanknoteSelling")
                    if selling:
                        out[code] = float(selling.replace(",", "."))
            return out
        rates = await run_in_threadpool(_fetch)
        for code, rate in rates.items():
            db.execute("INSERT INTO membrane_rates (currency, rate_to_try, updated_at) "
                       "VALUES (?, ?, datetime('now')) "
                       "ON CONFLICT(currency) DO UPDATE SET rate_to_try=excluded.rate_to_try, "
                       "updated_at=datetime('now')", (code, rate))
    except Exception:
        pass
    return RedirectResponse("/membrane", status_code=303)


@router.post("/membrane/rates/save")
async def rates_save(usd: float = Form(0), eur: float = Form(0), gbp: float = Form(0)):
    for code, rate in (("USD", usd), ("EUR", eur), ("GBP", gbp)):
        if rate > 0:
            db.execute("INSERT INTO membrane_rates (currency, rate_to_try, updated_at) "
                       "VALUES (?, ?, datetime('now')) "
                       "ON CONFLICT(currency) DO UPDATE SET rate_to_try=excluded.rate_to_try, "
                       "updated_at=datetime('now')", (code, rate))
    return RedirectResponse("/membrane", status_code=303)


@router.post("/membrane/material/save")
async def material_save(request: Request):
    f = await request.form()
    mid = int(_f(f, "id", "0") or 0)
    args = (
        _f(f, "name"), _f(f, "material_type", "other"), float(_f(f, "price", "0") or 0),
        _f(f, "currency", "TRY"), _f(f, "unit", "m2"),
        float(_f(f, "sheet_width", "0") or 0), float(_f(f, "sheet_height", "0") or 0),
        float(_f(f, "usage_per_m2", "1") or 1), _f(f, "notes", ""),
    )
    if mid > 0:
        db.execute("UPDATE membrane_materials SET name=?, material_type=?, price=?, currency=?, "
                   "unit=?, sheet_width=?, sheet_height=?, usage_per_m2=?, notes=? WHERE id=?",
                   args + (mid,))
    else:
        db.execute("INSERT INTO membrane_materials (name, material_type, price, currency, unit, "
                   "sheet_width, sheet_height, usage_per_m2, notes) VALUES (?,?,?,?,?,?,?,?,?)", args)
    return RedirectResponse("/membrane", status_code=303)


@router.post("/membrane/material/delete")
async def material_delete(id: int = Form(...)):
    db.execute("DELETE FROM membrane_materials WHERE id=?", (id,))
    return RedirectResponse("/membrane", status_code=303)


@router.post("/membrane/list/save")
async def list_save(request: Request):
    f = await request.form()
    lid = int(_f(f, "id", "0") or 0)
    if lid > 0:
        db.execute("UPDATE membrane_lists SET name=?, notes=? WHERE id=?",
                   (_f(f, "name"), _f(f, "notes", ""), lid))
    else:
        lid = db.execute("INSERT INTO membrane_lists (name, notes) VALUES (?, ?)",
                         (_f(f, "name"), _f(f, "notes", "")))
    return RedirectResponse(f"/membrane/list/{lid}", status_code=303)


@router.post("/membrane/list/delete")
async def list_delete(id: int = Form(...)):
    db.execute("DELETE FROM membrane_doors WHERE list_id=?", (id,))
    db.execute("DELETE FROM membrane_lists WHERE id=?", (id,))
    return RedirectResponse("/membrane", status_code=303)


def _list_context(request, lid, template):
    lst = db.one("SELECT * FROM membrane_lists WHERE id=?", (lid,))
    if not lst:
        return None
    rates = get_rates()
    cost_per_m2 = total_cost_per_m2(get_materials(), rates)
    doors = db.query("SELECT * FROM membrane_doors WHERE list_id=? ORDER BY id", (lid,))
    total = 0.0
    total_area = 0.0
    for d in doors:
        c = door_cost(d, cost_per_m2)
        d.update(c)
        total += c["total_cost"]
        total_area += c["area_m2"] * int(d["quantity"])
    return templates.TemplateResponse(request, template, {
        "request": request, "list": lst, "doors": doors,
        "cost_per_m2": cost_per_m2, "total_cost": total, "total_area": total_area,
    })


@router.get("/membrane/list/{lid}", response_class=HTMLResponse)
async def list_detail(request: Request, lid: int):
    ctx = _list_context(request, lid, "list_detail.html")
    return ctx or RedirectResponse("/membrane", status_code=303)


@router.get("/membrane/list/{lid}/print", response_class=HTMLResponse)
async def list_print(request: Request, lid: int):
    ctx = _list_context(request, lid, "list_print.html")
    return ctx or RedirectResponse("/membrane", status_code=303)


@router.post("/membrane/list/{lid}/door/save")
async def door_save(lid: int, request: Request):
    f = await request.form()
    did = int(_f(f, "id", "0") or 0)
    args = (_f(f, "project_name", ""), _f(f, "door_name", ""),
            float(_f(f, "width_mm", "0") or 0), float(_f(f, "height_mm", "0") or 0),
            int(_f(f, "quantity", "1") or 1))
    if did > 0:
        db.execute("UPDATE membrane_doors SET project_name=?, door_name=?, width_mm=?, "
                   "height_mm=?, quantity=? WHERE id=?", args + (did,))
    else:
        db.execute("INSERT INTO membrane_doors (list_id, project_name, door_name, width_mm, "
                   "height_mm, quantity) VALUES (?,?,?,?,?,?)", (lid,) + args)
    return RedirectResponse(f"/membrane/list/{lid}", status_code=303)


@router.post("/membrane/list/{lid}/door/delete")
async def door_delete(lid: int, id: int = Form(...)):
    db.execute("DELETE FROM membrane_doors WHERE id=? AND list_id=?", (id, lid))
    return RedirectResponse(f"/membrane/list/{lid}", status_code=303)


@router.post("/membrane/list/{lid}/door/clear")
async def door_clear(lid: int):
    db.execute("DELETE FROM membrane_doors WHERE list_id=?", (lid,))
    return RedirectResponse(f"/membrane/list/{lid}", status_code=303)


@router.post("/membrane/list/{lid}/door/scan")
async def door_scan(lid: int, image: UploadFile = File(...)):
    from scan import scan_image
    try:
        data = await image.read()
        doors = await run_in_threadpool(scan_image, data, image.content_type or "image/jpeg")
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    added = 0
    for d in doors:
        if d["width_mm"] > 0 and d["height_mm"] > 0:
            db.execute("INSERT INTO membrane_doors (list_id, door_name, width_mm, height_mm, "
                       "quantity) VALUES (?,?,?,?,?)",
                       (lid, d["door_name"], d["width_mm"], d["height_mm"], d["quantity"]))
            added += 1
    return JSONResponse({"added": added, "doors": doors})


# ==========================================================================
# MODÜL 2: KAPAK MODEL / NC
# ==========================================================================
@router.get("/membrane/caps", response_class=HTMLResponse)
async def caps_list(request: Request):
    models = db.query("SELECT * FROM membrane_cap_models ORDER BY id DESC")
    return templates.TemplateResponse(request, "caps.html", {"request": request, "models": models})


@router.post("/membrane/caps/save")
async def caps_save(request: Request):
    f = await request.form()
    mid = int(_f(f, "id", "0") or 0)
    args = (_f(f, "name"), _f(f, "description", ""), int(_f(f, "tool_no", "1") or 1),
            int(_f(f, "spindle_speed", "18000") or 18000), int(_f(f, "feed_xy", "3000") or 3000),
            int(_f(f, "feed_z", "1000") or 1000), float(_f(f, "safe_z", "5") or 5),
            _f(f, "constants_json", "{}") or "{}")
    if mid > 0:
        db.execute("UPDATE membrane_cap_models SET name=?, description=?, tool_no=?, "
                   "spindle_speed=?, feed_xy=?, feed_z=?, safe_z=?, constants_json=? WHERE id=?",
                   args + (mid,))
    else:
        mid = db.execute("INSERT INTO membrane_cap_models (name, description, tool_no, "
                         "spindle_speed, feed_xy, feed_z, safe_z, constants_json) "
                         "VALUES (?,?,?,?,?,?,?,?)", args)
    return RedirectResponse(f"/membrane/caps/{mid}", status_code=303)


@router.post("/membrane/caps/delete")
async def caps_delete(id: int = Form(...)):
    ops = db.query("SELECT id FROM membrane_cap_ops WHERE model_id=?", (id,))
    for op in ops:
        db.execute("DELETE FROM membrane_cap_moves WHERE op_id=?", (op["id"],))
    db.execute("DELETE FROM membrane_cap_ops WHERE model_id=?", (id,))
    db.execute("DELETE FROM membrane_cap_models WHERE id=?", (id,))
    return RedirectResponse("/membrane/caps", status_code=303)


@router.get("/membrane/caps/tools")
async def caps_tools():
    return JSONResponse(db.query("SELECT * FROM membrane_tools ORDER BY tool_no, id"))


@router.post("/membrane/caps/tools/save")
async def caps_tools_save(request: Request):
    f = await request.form()
    tid = int(_f(f, "id", "0") or 0)
    args = (_f(f, "name"), int(_f(f, "tool_no", "1") or 1), float(_f(f, "diameter", "6") or 6),
            float(_f(f, "length", "0") or 0), int(_f(f, "feed_xy", "3000") or 3000),
            int(_f(f, "feed_z", "1000") or 1000), _f(f, "notes", ""))
    if tid > 0:
        db.execute("UPDATE membrane_tools SET name=?, tool_no=?, diameter=?, length=?, "
                   "feed_xy=?, feed_z=?, notes=? WHERE id=?", args + (tid,))
    else:
        db.execute("INSERT INTO membrane_tools (name, tool_no, diameter, length, feed_xy, "
                   "feed_z, notes) VALUES (?,?,?,?,?,?,?)", args)
    return JSONResponse({"ok": True})


@router.post("/membrane/caps/tools/delete")
async def caps_tools_delete(id: int = Form(...)):
    db.execute("DELETE FROM membrane_tools WHERE id=?", (id,))
    return JSONResponse({"ok": True})


@router.get("/membrane/caps/{mid}", response_class=HTMLResponse)
async def caps_editor(request: Request, mid: int):
    bundle = get_model_bundle(mid)
    if not bundle:
        return RedirectResponse("/membrane/caps", status_code=303)
    tools = db.query("SELECT * FROM membrane_tools ORDER BY tool_no, id")
    return templates.TemplateResponse(request, "cap_editor.html", {
        "request": request, "model": bundle["model"], "ops": bundle["ops"],
        "moves": bundle["moves"], "tools": tools,
    })


@router.get("/membrane/caps/{mid}/ops")
async def caps_ops(mid: int):
    bundle = get_model_bundle(mid)
    if not bundle:
        return JSONResponse([], status_code=404)
    ops = bundle["ops"]
    for op in ops:
        op["moves"] = bundle["moves"].get(op["id"], [])
    return JSONResponse(ops)


@router.post("/membrane/caps/{mid}/op/save")
async def op_save(mid: int, request: Request):
    f = await request.form()
    oid = int(_f(f, "id", "0") or 0)
    args = (_f(f, "name", ""), int(_f(f, "tool_no", "1") or 1),
            int(_f(f, "tool_id", "0") or 0) or None, _f(f, "depth", "-T"),
            _f(f, "feed", ""), _f(f, "ref_corner", "BL"), int(_f(f, "seq", "0") or 0),
            _f(f, "op_type", "inner"), _f(f, "comp_mode", "none"), _f(f, "offset_side", "center"))
    if oid > 0:
        db.execute("UPDATE membrane_cap_ops SET name=?, tool_no=?, tool_id=?, depth=?, feed=?, "
                   "ref_corner=?, seq=?, op_type=?, comp_mode=?, offset_side=? WHERE id=?",
                   args + (oid,))
    else:
        oid = db.execute("INSERT INTO membrane_cap_ops (model_id, name, tool_no, tool_id, depth, "
                         "feed, ref_corner, seq, op_type, comp_mode, offset_side) "
                         "VALUES (?,?,?,?,?,?,?,?,?,?,?)", (mid,) + args)
    return JSONResponse({"ok": True, "id": oid})


@router.post("/membrane/caps/op/{oid}/delete")
async def op_delete(oid: int):
    db.execute("DELETE FROM membrane_cap_moves WHERE op_id=?", (oid,))
    db.execute("DELETE FROM membrane_cap_ops WHERE id=?", (oid,))
    return JSONResponse({"ok": True})


@router.post("/membrane/caps/op/{oid}/move/save")
async def move_save(oid: int, request: Request):
    f = await request.form()
    mvid = int(_f(f, "id", "0") or 0)
    args = (_f(f, "move_type", "line"), _f(f, "x", "0"), _f(f, "y", "0"),
            _f(f, "cx", "0"), _f(f, "cy", "0"), _f(f, "r", "0"), int(_f(f, "seq", "0") or 0))
    if mvid > 0:
        db.execute("UPDATE membrane_cap_moves SET move_type=?, x=?, y=?, cx=?, cy=?, r=?, seq=? "
                   "WHERE id=?", args + (mvid,))
    else:
        mvid = db.execute("INSERT INTO membrane_cap_moves (op_id, move_type, x, y, cx, cy, r, seq) "
                          "VALUES (?,?,?,?,?,?,?,?)", (oid,) + args)
    return JSONResponse({"ok": True, "id": mvid})


@router.post("/membrane/caps/move/{mvid}/delete")
async def move_delete(mvid: int):
    db.execute("DELETE FROM membrane_cap_moves WHERE id=?", (mvid,))
    return JSONResponse({"ok": True})


@router.post("/membrane/caps/{mid}/generate")
async def caps_generate(mid: int, request: Request):
    bundle = get_model_bundle(mid)
    if not bundle:
        return JSONResponse({"error": "Model bulunamadı"}, status_code=404)
    body = await request.json()
    W = float(body.get("W", 400)); H = float(body.get("H", 600)); T = float(body.get("T", 18))
    extra = body.get("extra") or {}
    bundle["model"]["_constants"].update(extra)
    bundle["model"]["_T"] = T
    nc, sim = generate_model_nc(bundle["model"], bundle["ops"], bundle["moves"], W, H, T)
    return JSONResponse({"nc": nc, "sim": sim, "LPX": W, "LPY": H, "LPZ": T})


@router.get("/membrane/caps/{mid}/download")
async def caps_download(mid: int, W: float = 400, H: float = 600, T: float = 18):
    bundle = get_model_bundle(mid)
    if not bundle:
        return PlainTextResponse("Model bulunamadı", status_code=404)
    nc, _ = generate_model_nc(bundle["model"], bundle["ops"], bundle["moves"], W, H, T)
    name = (bundle["model"]["name"] or "model").replace(" ", "_")
    return Response(nc, media_type="text/plain",
                    headers={"Content-Disposition": f'attachment; filename="{name}.nc"'})


# ---- DXF (bonus) ----
@router.post("/membrane/caps/dxf_preview")
async def dxf_preview(file: UploadFile = File(...)):
    if not dxf_utils.dxf_available():
        return JSONResponse({"error": "ezdxf kurulu değil"}, status_code=400)
    try:
        data = await file.read()
        return JSONResponse(dxf_utils.parse_dxf(data))
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)


# ==========================================================================
# MODÜL 3: NESTING
# ==========================================================================
@router.get("/membrane/jobs", response_class=HTMLResponse)
async def jobs_list(request: Request):
    jobs = db.query("SELECT * FROM membrane_cap_jobs ORDER BY id DESC")
    for j in jobs:
        items = db.query("SELECT * FROM membrane_cap_job_items WHERE job_id=?", (j["id"],))
        j["piece_count"] = sum(int(i["qty"]) for i in items)
    return templates.TemplateResponse(request, "jobs.html", {"request": request, "jobs": jobs})


@router.post("/membrane/jobs/save")
async def jobs_save(request: Request):
    f = await request.form()
    jid = int(_f(f, "id", "0") or 0)
    args = (_f(f, "name"), _f(f, "notes", ""), float(_f(f, "sheet_w", "2800") or 2800),
            float(_f(f, "sheet_h", "1100") or 1100), float(_f(f, "margin", "5") or 5))
    if jid > 0:
        db.execute("UPDATE membrane_cap_jobs SET name=?, notes=?, sheet_w=?, sheet_h=?, margin=? "
                   "WHERE id=?", args + (jid,))
    else:
        jid = db.execute("INSERT INTO membrane_cap_jobs (name, notes, sheet_w, sheet_h, margin) "
                         "VALUES (?,?,?,?,?)", args)
    return RedirectResponse(f"/membrane/jobs/{jid}", status_code=303)


@router.post("/membrane/jobs/delete")
async def jobs_delete(id: int = Form(...)):
    db.execute("DELETE FROM membrane_cap_job_items WHERE job_id=?", (id,))
    db.execute("DELETE FROM membrane_cap_jobs WHERE id=?", (id,))
    return RedirectResponse("/membrane/jobs", status_code=303)


@router.get("/membrane/jobs/{jid}", response_class=HTMLResponse)
async def job_detail(request: Request, jid: int):
    job = db.one("SELECT * FROM membrane_cap_jobs WHERE id=?", (jid,))
    if not job:
        return RedirectResponse("/membrane/jobs", status_code=303)
    items = db.query("SELECT * FROM membrane_cap_job_items WHERE job_id=? ORDER BY seq, id", (jid,))
    models = db.query("SELECT id, name FROM membrane_cap_models ORDER BY name")
    return templates.TemplateResponse(request, "job_detail.html", {
        "request": request, "job": job, "items": items, "models": models,
    })


@router.post("/membrane/jobs/{jid}/item/save")
async def item_save(jid: int, request: Request):
    f = await request.form()
    iid = int(_f(f, "id", "0") or 0)
    model_id = int(_f(f, "model_id", "0") or 0) or None
    model_name = _f(f, "model_name", "")
    if model_id and not model_name:
        m = db.one("SELECT name FROM membrane_cap_models WHERE id=?", (model_id,))
        model_name = m["name"] if m else ""
    args = (model_id, model_name, float(_f(f, "cap_w", "400") or 400),
            float(_f(f, "cap_h", "600") or 600), int(_f(f, "qty", "1") or 1),
            _f(f, "notes", ""), int(_f(f, "seq", "0") or 0))
    if iid > 0:
        db.execute("UPDATE membrane_cap_job_items SET model_id=?, model_name=?, cap_w=?, cap_h=?, "
                   "qty=?, notes=?, seq=? WHERE id=?", args + (iid,))
    else:
        db.execute("INSERT INTO membrane_cap_job_items (job_id, model_id, model_name, cap_w, "
                   "cap_h, qty, notes, seq) VALUES (?,?,?,?,?,?,?,?)", (jid,) + args)
    return RedirectResponse(f"/membrane/jobs/{jid}", status_code=303)


@router.post("/membrane/jobs/{jid}/item/delete")
async def item_delete(jid: int, id: int = Form(...)):
    db.execute("DELETE FROM membrane_cap_job_items WHERE id=? AND job_id=?", (id, jid))
    return RedirectResponse(f"/membrane/jobs/{jid}", status_code=303)


def _job_nest(jid):
    job = db.one("SELECT * FROM membrane_cap_jobs WHERE id=?", (jid,))
    if not job:
        return None, None, None
    items = db.query("SELECT * FROM membrane_cap_job_items WHERE job_id=? ORDER BY seq, id", (jid,))
    nest_items = [{"w": i["cap_w"], "h": i["cap_h"], "qty": int(i["qty"]),
                   "label": i["model_name"] or f"{i['cap_w']:.0f}x{i['cap_h']:.0f}",
                   "model_id": i["model_id"]} for i in items]
    placements, sheet_count, util = nest(
        nest_items, job["sheet_w"], job["sheet_h"], job["margin"], allow_rotate=False)
    return job, placements, {"sheet_count": sheet_count, "util": util}


@router.post("/membrane/jobs/{jid}/nest")
async def job_nest(jid: int):
    job, placements, meta = _job_nest(jid)
    if job is None:
        return JSONResponse({"error": "İş bulunamadı"}, status_code=404)
    total = len(placements)
    return JSONResponse({
        "placements": placements, "sheet_count": meta["sheet_count"],
        "util_pct": meta["util"], "sheet_w": job["sheet_w"], "sheet_h": job["sheet_h"],
        "total_pieces": total,
    })


@router.post("/membrane/jobs/{jid}/nest_nc")
async def job_nest_nc(jid: int):
    job, placements, meta = _job_nest(jid)
    if job is None:
        return JSONResponse({"error": "İş bulunamadı"}, status_code=404)
    models_data = {}
    for pl in placements:
        mid = pl.get("model_id")
        if mid and mid not in models_data:
            bundle = get_model_bundle(mid)
            if bundle:
                bundle["model"]["_T"] = 18
                models_data[mid] = {"model": bundle["model"], "ops": bundle["ops"],
                                    "moves": bundle["moves"]}
    nc, sim = generate_job_nc(job, placements, models_data)
    return JSONResponse({"nc": nc, "sim": sim, "placements": placements,
                         "sheet_w": job["sheet_w"], "sheet_h": job["sheet_h"],
                         "sheet_count": meta["sheet_count"], "util_pct": meta["util"]})


@router.get("/membrane/jobs/{jid}/nc")
async def job_nc_download(jid: int):
    job, placements, meta = _job_nest(jid)
    if job is None:
        return PlainTextResponse("İş bulunamadı", status_code=404)
    models_data = {}
    for pl in placements:
        mid = pl.get("model_id")
        if mid and mid not in models_data:
            bundle = get_model_bundle(mid)
            if bundle:
                bundle["model"]["_T"] = 18
                models_data[mid] = {"model": bundle["model"], "ops": bundle["ops"],
                                    "moves": bundle["moves"]}
    nc, _ = generate_job_nc(job, placements, models_data)
    name = (job["name"] or "nesting").replace(" ", "_")
    return Response(nc, media_type="text/plain",
                    headers={"Content-Disposition": f'attachment; filename="{name}.nc"'})


# ==========================================================================
# MODÜL 5: 3B RAF KONFİGÜRATÖRÜ
# ==========================================================================
@router.get("/membrane/configurator", response_class=HTMLResponse)
async def configurator(request: Request):
    quotes = db.query("SELECT * FROM membrane_shelf_quotes ORDER BY id DESC LIMIT 50")
    for q in quotes:
        try:
            q["params"] = json.loads(q.get("params_json") or "{}")
        except Exception:
            q["params"] = {}
    return templates.TemplateResponse(request, "configurator.html", {
        "request": request, "quotes": quotes,
        "tpl_data": templates_lib.list_templates(),
        "colors": templates_lib.COLORS,
    })


@router.post("/membrane/configurator/price")
async def configurator_price(request: Request):
    body = await request.json()
    return JSONResponse(templates_lib.price_template(body.get("template"), body.get("params", {})))


@router.post("/membrane/configurator/quote")
async def configurator_quote(request: Request):
    body = await request.json()
    tid = body.get("template")
    params = body.get("params", {})
    pricing = templates_lib.price_template(tid, params)
    tpl = templates_lib.get_template(tid)
    store = {"template": tid, "params": params, "color": params.get("color")}
    qid = db.execute(
        "INSERT INTO membrane_shelf_quotes (name, customer, params_json, price) "
        "VALUES (?,?,?,?)",
        (body.get("name", "") or (tpl["name"] if tpl else ""),
         body.get("customer", ""), json.dumps(store), pricing["total"]),
    )
    return JSONResponse({"ok": True, "id": qid, "price": pricing["total"]})


@router.post("/membrane/configurator/delete")
async def configurator_delete(id: int = Form(...)):
    db.execute("DELETE FROM membrane_shelf_quotes WHERE id=?", (id,))
    return RedirectResponse("/membrane/configurator", status_code=303)


# ==========================================================================
# YÖNETİCİ PANELİ (fiyat / renk yönetimi)
# ==========================================================================
@router.get("/membrane/admin", response_class=HTMLResponse)
async def admin_index(request: Request):
    return templates.TemplateResponse(request, "admin_index.html", {"request": request})


@router.get("/membrane/admin/prices", response_class=HTMLResponse)
async def admin_prices(request: Request):
    return templates.TemplateResponse(request, "admin_prices.html", {
        "request": request, "prices": cfg.get_prices_full(),
    })


@router.post("/membrane/admin/prices")
async def admin_prices_save(request: Request):
    form = await request.form()
    for row in cfg.get_prices_full():
        val = form.get(row["key"])
        if val is not None:
            try:
                db.execute("UPDATE cfg_prices SET value=? WHERE key=?", (float(val), row["key"]))
            except ValueError:
                pass
    return RedirectResponse("/membrane/admin/prices", status_code=303)


@router.get("/membrane/admin/colors", response_class=HTMLResponse)
async def admin_colors(request: Request):
    return templates.TemplateResponse(request, "admin_colors.html", {
        "request": request, "colors": cfg.get_colors(active_only=False),
    })


@router.post("/membrane/admin/colors/save")
async def admin_colors_save(request: Request):
    f = await request.form()
    cid = int(_f(f, "id", "0") or 0)
    args = (_f(f, "name", ""), _f(f, "hex", "#d8b88a"),
            float(_f(f, "premium_pct", "0") or 0), int(_f(f, "seq", "0") or 0),
            1 if f.get("active") else 0)
    if cid > 0:
        db.execute("UPDATE cfg_colors SET name=?, hex=?, premium_pct=?, seq=?, active=? WHERE id=?",
                   args + (cid,))
    else:
        db.execute("INSERT INTO cfg_colors (name, hex, premium_pct, seq, active) VALUES (?,?,?,?,?)", args)
    return RedirectResponse("/membrane/admin/colors", status_code=303)


@router.post("/membrane/admin/colors/delete")
async def admin_colors_delete(id: int = Form(...)):
    db.execute("DELETE FROM cfg_colors WHERE id=?", (id,))
    return RedirectResponse("/membrane/admin/colors", status_code=303)


# ==========================================================================
# GARDIROP KONFİGÜRATÖRÜ (pilot - tam opsiyonlu)
# ==========================================================================
@router.get("/membrane/gardirop")
async def gardirop():
    # Faz 2 ile çok-ürünlü konfigüratöre taşındı
    return RedirectResponse("/membrane/urunler", status_code=307)


@router.post("/membrane/gardirop/price")
async def gardirop_price(request: Request):
    params = await request.json()
    return JSONResponse(cfg.price_gardirop(params))


@router.post("/membrane/gardirop/quote")
async def gardirop_quote(request: Request):
    body = await request.json()
    params = body.get("params", {})
    pricing = cfg.price_gardirop(params)
    store = {"template": "gardirop_pro", "params": params}
    qid = db.execute(
        "INSERT INTO membrane_shelf_quotes (name, customer, params_json, price) VALUES (?,?,?,?)",
        (body.get("name", "") or "Gardırop", body.get("customer", ""),
         json.dumps(store), pricing["total"]),
    )
    return JSONResponse({"ok": True, "id": qid, "price": pricing["total"]})


@router.post("/membrane/gardirop/delete")
async def gardirop_delete(id: int = Form(...)):
    db.execute("DELETE FROM membrane_shelf_quotes WHERE id=?", (id,))
    return RedirectResponse("/membrane/urunler", status_code=303)


# ==========================================================================
# ÇOK ÜRÜNLÜ KONFİGÜRATÖR (Faz 2)
# ==========================================================================
@router.get("/membrane/urunler", response_class=HTMLResponse)
async def urunler(request: Request):
    prods = products_lib.list_products(only_enabled=True)
    valid_ids = {p["id"] for p in prods} | {"gardirop_pro"}
    quotes = []
    for q in db.query("SELECT * FROM membrane_shelf_quotes ORDER BY id DESC LIMIT 40"):
        try:
            store = json.loads(q.get("params_json") or "{}")
        except Exception:
            store = {}
        if store.get("template") in valid_ids:
            q["params"] = store.get("params", {})
            q["product"] = store.get("template")
            quotes.append(q)
    return templates.TemplateResponse(request, "urunler.html", {
        "request": request, "products": prods, "colors": cfg.get_colors(), "quotes": quotes,
    })


@router.post("/membrane/urunler/price")
async def urunler_price(request: Request):
    return JSONResponse(cfg.price_cabinet(await request.json()))


@router.post("/membrane/urunler/quote")
async def urunler_quote(request: Request):
    body = await request.json()
    params = body.get("params", {})
    pricing = cfg.price_cabinet(params)
    store = {"template": body.get("product", "gardirop"), "params": params}
    qid = db.execute(
        "INSERT INTO membrane_shelf_quotes (name, customer, params_json, price) VALUES (?,?,?,?)",
        (body.get("name", "") or "Ürün", body.get("customer", ""),
         json.dumps(store), pricing["total"]),
    )
    return JSONResponse({"ok": True, "id": qid, "price": pricing["total"]})


@router.post("/membrane/urunler/delete")
async def urunler_delete(id: int = Form(...)):
    db.execute("DELETE FROM membrane_shelf_quotes WHERE id=?", (id,))
    return RedirectResponse("/membrane/urunler", status_code=303)


DOOR_LABELS = {"duz": "Düz", "camli": "Camlı", "desenli": "Desenli",
               "surgu": "Sürgü", "kapaksiz": "Kapaksız"}
BASE_LABELS = {"bazali": "Bazalı", "ayakli": "Ayaklı", "yok": "Bazasız"}


@router.get("/membrane/teklif/{qid}/print", response_class=HTMLResponse)
async def teklif_print(request: Request, qid: int):
    q = db.one("SELECT * FROM membrane_shelf_quotes WHERE id=?", (qid,))
    if not q:
        return RedirectResponse("/membrane/urunler", status_code=303)
    store = json.loads(q.get("params_json") or "{}")
    p = store.get("params", {})
    pricing = cfg.price_cabinet(p)
    cmap = {str(c["id"]): c["name"] for c in db.query("SELECT id, name FROM cfg_colors")}
    prod_name = (store.get("product_name")
                 or (products_lib.get(store.get("template", "")) or {}).get("name")
                 or store.get("template", "Ürün"))
    details = [
        ("Ürün", prod_name),
        ("Ölçü (G×Y×D)", f"{p.get('W','?')} × {p.get('H','?')} × {p.get('D','?')} mm"),
        ("Kapak tipi", DOOR_LABELS.get(p.get("door_type"), p.get("door_type", "-"))),
        ("Gövde tipi", BASE_LABELS.get(p.get("base_type"), p.get("base_type", "-"))),
        ("Kapak / Raf / Çekmece", f"{p.get('doors',0)} / {p.get('shelves',0)} / {p.get('drawers',0)}"),
        ("Gövde rengi", cmap.get(str(p.get("body_color")), "-")),
        ("Kapak rengi", cmap.get(str(p.get("door_color")), "-")),
    ]
    if p.get("two_tone"):
        details.append(("2. kapak rengi", cmap.get(str(p.get("door_color2")), "-")))
    contact = store.get("contact", {})
    return templates.TemplateResponse(request, "teklif_print.html", {
        "request": request, "q": q, "details": details,
        "items": [i for i in pricing["items"] if i["value"] > 0],
        "total": pricing["total"], "contact": contact,
    })


# ==========================================================================
# NO-CODE ŞABLON EDİTÖRÜ
# ==========================================================================
@router.get("/membrane/admin/sablonlar", response_class=HTMLResponse)
async def admin_sablonlar(request: Request):
    rows = db.query("SELECT id, name, enabled FROM cfg_templates ORDER BY seq, id")
    return templates.TemplateResponse(request, "admin_sablonlar.html", {"request": request, "rows": rows})


@router.post("/membrane/admin/sablonlar/new")
async def admin_sablon_new():
    blank = {"category": "Özel", "params": [
        {"key": "W", "label": "Genişlik (mm)", "min": 300, "max": 3000, "default": 1000},
        {"key": "H", "label": "Yükseklik (mm)", "min": 300, "max": 2400, "default": 800},
        {"key": "D", "label": "Derinlik (mm)", "min": 200, "max": 700, "default": 400},
    ], "parts": [], "price_expr": "500"}
    tid = db.execute("INSERT INTO cfg_templates (name, enabled, def_json) VALUES (?,?,?)",
                     ("Yeni Şablon", 1, json.dumps(blank)))
    return RedirectResponse(f"/membrane/admin/sablon/{tid}", status_code=303)


@router.get("/membrane/admin/sablon/{tid}", response_class=HTMLResponse)
async def admin_sablon_edit(request: Request, tid: int):
    t = db.one("SELECT * FROM cfg_templates WHERE id=?", (tid,))
    if not t:
        return RedirectResponse("/membrane/admin/sablonlar", status_code=303)
    try:
        deff = json.loads(t.get("def_json") or "{}")
    except Exception:
        deff = {}
    return templates.TemplateResponse(request, "admin_sablon_edit.html", {
        "request": request, "t": t, "deff": deff, "colors": cfg.get_colors(),
    })


@router.post("/membrane/admin/sablon/{tid}")
async def admin_sablon_save(tid: int, request: Request):
    body = await request.json()
    db.execute("UPDATE cfg_templates SET name=?, enabled=?, def_json=? WHERE id=?",
               (body.get("name", "Şablon"), 1 if body.get("enabled", True) else 0,
                json.dumps(body.get("def", {})), tid))
    return JSONResponse({"ok": True})


@router.post("/membrane/admin/sablon/{tid}/delete")
async def admin_sablon_delete(tid: int):
    db.execute("DELETE FROM cfg_templates WHERE id=?", (tid,))
    return RedirectResponse("/membrane/admin/sablonlar", status_code=303)


@router.get("/membrane/sablon/{tid}", response_class=HTMLResponse)
async def sablon_konfig(request: Request, tid: int):
    t = db.one("SELECT * FROM cfg_templates WHERE id=?", (tid,))
    if not t:
        return RedirectResponse("/membrane/admin/sablonlar", status_code=303)
    deff = json.loads(t.get("def_json") or "{}")
    return templates.TemplateResponse(request, "sablon_konfig.html", {
        "request": request, "t": t, "deff": deff, "colors": cfg.get_colors(),
    })


@router.post("/membrane/sablon/{tid}/price")
async def sablon_price(tid: int, request: Request):
    t = db.one("SELECT def_json FROM cfg_templates WHERE id=?", (tid,))
    deff = json.loads(t.get("def_json") or "{}") if t else {}
    return JSONResponse(cfg.price_custom(deff, await request.json()))


@router.post("/membrane/sablon/{tid}/quote")
async def sablon_quote(tid: int, request: Request):
    t = db.one("SELECT * FROM cfg_templates WHERE id=?", (tid,))
    deff = json.loads(t.get("def_json") or "{}") if t else {}
    body = await request.json()
    params = body.get("params", {})
    pricing = cfg.price_custom(deff, params)
    store = {"template": f"tpl{tid}", "product_name": t["name"] if t else "Şablon", "params": params}
    qid = db.execute(
        "INSERT INTO membrane_shelf_quotes (name, customer, params_json, price) VALUES (?,?,?,?)",
        (body.get("name", "") or (t["name"] if t else "Şablon"), body.get("customer", ""),
         json.dumps(store), pricing["total"]))
    return JSONResponse({"ok": True, "id": qid, "price": pricing["total"]})


@router.get("/membrane/admin/talepler", response_class=HTMLResponse)
async def admin_talepler(request: Request):
    rows = []
    for q in db.query("SELECT * FROM membrane_shelf_quotes ORDER BY id DESC LIMIT 100"):
        try:
            store = json.loads(q.get("params_json") or "{}")
        except Exception:
            store = {}
        if store.get("source") == "customer":
            q["contact"] = store.get("contact", {})
            q["params"] = store.get("params", {})
            rows.append(q)
    return templates.TemplateResponse(request, "admin_talepler.html", {"request": request, "rows": rows})


@router.get("/membrane/admin/products", response_class=HTMLResponse)
async def admin_products(request: Request):
    return templates.TemplateResponse(request, "admin_products.html", {
        "request": request, "products": products_lib.list_products(only_enabled=False),
    })


@router.post("/membrane/admin/products/save")
async def admin_products_save(request: Request):
    f = await request.form()
    for p in products_lib.REGISTRY:
        name = f.get("name_" + p["id"])
        enabled = 1 if f.get("enabled_" + p["id"]) else 0
        if name is not None:
            db.execute("UPDATE cfg_products SET name=?, enabled=? WHERE id=?",
                       (name, enabled, p["id"]))
    return RedirectResponse("/membrane/admin/products", status_code=303)
