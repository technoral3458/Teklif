"""TitaniumCam: online dolap tasarım ve sipariş sistemi (FastAPI)."""
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles

import db
from auth import verify_session
from config import BASE_DIR


@asynccontextmanager
async def lifespan(app):
    db.init()
    yield


app = FastAPI(title="Online Sipariş Sistemi", lifespan=lifespan)

# Giriş gerektirmeyen yollar. /magaza ve /kabin kendi müşteri oturumunu ('csession') içeride denetler.
PUBLIC_PATHS = ("/login", "/logout", "/static", "/magaza", "/kabin",
                "/api/titanium", "/gizlilik", "/kosullar")   # API: oturum değil Bearer anahtarı (routers/titanium_api.py)


@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    path = request.url.path
    if path == "/membrane" or path.startswith("/membrane/"):      # eski yönetim adresleri
        yeni = "/titaniumcam" + path[len("/membrane"):]
        if request.url.query:
            yeni += "?" + request.url.query
        return RedirectResponse(yeni, status_code=308)
    user = verify_session(request.cookies.get("session", ""))
    request.state.user = user
    is_public = path == "/" or any(path == p or path.startswith(p + "/") or path.startswith(p)
                                   for p in PUBLIC_PATHS)          # "/" = mağaza ana sayfası
    if not is_public and user is None:
        return RedirectResponse("/login", status_code=303)
    return await call_next(request)


os.makedirs(os.path.join(BASE_DIR, "static"), exist_ok=True)
app.mount("/static", StaticFiles(directory=os.path.join(BASE_DIR, "static")), name="static")

from routers.auth_routes import router as auth_router  # noqa: E402
from routers.magaza import router as magaza_router  # noqa: E402
from routers.kabin import router as kabin_router  # noqa: E402
from routers.titanium_api import router as titanium_router  # noqa: E402
from routers.ayarlar import router as ayarlar_router  # noqa: E402
from routers.acil_lisans import router as acil_router  # noqa: E402

app.include_router(auth_router)
app.include_router(magaza_router)
app.include_router(kabin_router)
app.include_router(titanium_router)
app.include_router(ayarlar_router)
app.include_router(acil_router)


# Firma adı (Ayarlar → Firma) tüm şablonlarda {{ firma().ad }} olarak kullanılır
import routers.auth_routes  # noqa: E402,F401
import routers.magaza, routers.kabin, routers.titanium_api, routers.ayarlar, routers.acil_lisans  # noqa: E402,F401
from routers.kabin import firma_bilgisi  # noqa: E402

for _m in (routers.auth_routes, routers.magaza, routers.kabin, routers.titanium_api, routers.ayarlar, routers.acil_lisans):
    _m.templates.env.globals["firma"] = firma_bilgisi

# Ana sayfa = mağaza (müşteri vitrini). Yönetim: /login → /titaniumcam/kabin
from routers.magaza import magaza_home  # noqa: E402

app.add_api_route("/", magaza_home, methods=["GET"], response_class=HTMLResponse)
