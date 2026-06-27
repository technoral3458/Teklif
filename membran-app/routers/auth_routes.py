"""Giriş / çıkış uçları."""
import os

from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

import db
from auth import verify_password, sign_session
from config import BASE_DIR

router = APIRouter()
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))


@router.get("/login", response_class=HTMLResponse)
async def login_page(request: Request):
    if verify_session_cookie(request):
        return RedirectResponse("/membrane", status_code=303)
    return templates.TemplateResponse(request, "login.html", {"request": request, "error": ""})


@router.post("/login", response_class=HTMLResponse)
async def login_submit(request: Request, username: str = Form(...), password: str = Form(...)):
    user = db.one("SELECT * FROM membrane_users WHERE username=?", (username,))
    if user and verify_password(password, user["password_hash"]):
        resp = RedirectResponse("/membrane", status_code=303)
        resp.set_cookie("session", sign_session(username), httponly=True,
                        samesite="lax", max_age=60 * 60 * 24 * 7)
        return resp
    return templates.TemplateResponse(
        request, "login.html",
        {"request": request, "error": "Kullanıcı adı veya şifre hatalı"},
    )


@router.get("/logout")
async def logout():
    resp = RedirectResponse("/login", status_code=303)
    resp.delete_cookie("session")
    return resp


def verify_session_cookie(request: Request):
    from auth import verify_session
    return verify_session(request.cookies.get("session", ""))
