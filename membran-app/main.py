"""Membran Kapak Yönetim Sistemi - FastAPI uygulaması."""
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


app = FastAPI(title="Membran Kapak Yönetim Sistemi", lifespan=lifespan)

# Giriş gerektirmeyen yollar (müşteri mağazası + teklif sayfası dahil).
# /magaza ve /kabin kendi müşteri oturumunu ('csession') içeride denetler.
PUBLIC_PATHS = ("/login", "/logout", "/static", "/teklif", "/magaza", "/kabin",
                "/api/titanium", "/gizlilik", "/kosullar")   # API: oturum değil Bearer anahtarı (routers/titanium_api.py)


@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    path = request.url.path
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
from routers.public_routes import router as public_router  # noqa: E402
from routers.vardar import router as vardar_router  # noqa: E402
from routers.membrane import router as membrane_router  # noqa: E402
from routers.magaza import router as magaza_router  # noqa: E402
from routers.kabin import router as kabin_router  # noqa: E402
from routers.titanium_api import router as titanium_router  # noqa: E402

app.include_router(auth_router)
app.include_router(public_router)
app.include_router(vardar_router)
app.include_router(membrane_router)
app.include_router(magaza_router)
app.include_router(kabin_router)
app.include_router(titanium_router)


# Ana sayfa = mağaza (müşteri vitrini). Yönetici paneli: /login → /membrane
from routers.magaza import magaza_home  # noqa: E402

app.add_api_route("/", magaza_home, methods=["GET"], response_class=HTMLResponse)
