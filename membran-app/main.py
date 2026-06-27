"""Membran Kapak Yönetim Sistemi - FastAPI uygulaması."""
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles

import db
from auth import verify_session
from config import BASE_DIR


@asynccontextmanager
async def lifespan(app):
    db.init()
    yield


app = FastAPI(title="Membran Kapak Yönetim Sistemi", lifespan=lifespan)

# Giriş gerektirmeyen yollar
PUBLIC_PATHS = ("/login", "/logout", "/static")


@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    path = request.url.path
    user = verify_session(request.cookies.get("session", ""))
    request.state.user = user
    is_public = any(path == p or path.startswith(p + "/") or path.startswith(p)
                    for p in PUBLIC_PATHS)
    if not is_public and user is None:
        return RedirectResponse("/login", status_code=303)
    return await call_next(request)


os.makedirs(os.path.join(BASE_DIR, "static"), exist_ok=True)
app.mount("/static", StaticFiles(directory=os.path.join(BASE_DIR, "static")), name="static")

from routers.auth_routes import router as auth_router  # noqa: E402
from routers.membrane import router as membrane_router  # noqa: E402

app.include_router(auth_router)
app.include_router(membrane_router)


@app.get("/")
async def root():
    return RedirectResponse("/membrane")
