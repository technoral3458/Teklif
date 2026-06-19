"""Membran Kapak Yönetim Sistemi - FastAPI uygulaması."""
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles

import db
from config import BASE_DIR


@asynccontextmanager
async def lifespan(app):
    db.init()
    yield


app = FastAPI(title="Membran Kapak Yönetim Sistemi", lifespan=lifespan)

os.makedirs(os.path.join(BASE_DIR, "static"), exist_ok=True)
app.mount("/static", StaticFiles(directory=os.path.join(BASE_DIR, "static")), name="static")

from routers.membrane import router as membrane_router  # noqa: E402

app.include_router(membrane_router)


@app.get("/")
async def root():
    return RedirectResponse("/membrane")
