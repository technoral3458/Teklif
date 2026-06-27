"""Müşteriye açık (girişsiz) teklif sayfası ve uçları."""
import json
import os

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.templating import Jinja2Templates

import db
import cfg
import products as products_lib
from config import BASE_DIR

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))


@router.get("/teklif", response_class=HTMLResponse)
async def teklif_public(request: Request):
    return templates.TemplateResponse(request, "teklif_public.html", {
        "request": request,
        "products": products_lib.list_products(only_enabled=True),
        "colors": cfg.get_colors(),
    })


@router.post("/teklif/price")
async def teklif_price(request: Request):
    return JSONResponse(cfg.price_cabinet(await request.json()))


@router.post("/teklif/submit")
async def teklif_submit(request: Request):
    body = await request.json()
    params = body.get("params", {})
    contact = body.get("contact", {})
    pricing = cfg.price_cabinet(params)
    store = {"template": body.get("product", ""), "params": params,
             "source": "customer", "contact": contact}
    qid = db.execute(
        "INSERT INTO membrane_shelf_quotes (name, customer, params_json, price) VALUES (?,?,?,?)",
        (contact.get("name", "") + " (talep)", contact.get("phone", ""),
         json.dumps(store), pricing["total"]),
    )
    return JSONResponse({"ok": True, "id": qid, "price": pricing["total"]})
