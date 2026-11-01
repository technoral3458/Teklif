"""
Kimlik doğrulama yardımcıları (harici bağımlılık yok, stdlib).

- Şifre: PBKDF2-HMAC-SHA256 (tuzlu).
- Oturum: SECRET_KEY ile imzalı çerez (HMAC).
"""
import base64
import hashlib
import hmac
import os

from config import SECRET_KEY

_ITERATIONS = 100_000


def hash_password(password: str, salt: bytes = None) -> str:
    salt = salt or os.urandom(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, _ITERATIONS)
    return base64.b64encode(salt).decode() + "$" + base64.b64encode(dk).decode()


def verify_password(password: str, stored: str) -> bool:
    try:
        salt_b64, dk_b64 = stored.split("$")
        salt = base64.b64decode(salt_b64)
        dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, _ITERATIONS)
        return hmac.compare_digest(base64.b64encode(dk).decode(), dk_b64)
    except Exception:
        return False


def sign_session(username: str) -> str:
    sig = hmac.new(SECRET_KEY.encode(), username.encode(), hashlib.sha256).hexdigest()
    return f"{username}|{sig}"


def verify_session(cookie: str):
    """Geçerliyse kullanıcı adını, değilse None döner."""
    if not cookie or "|" not in cookie:
        return None
    try:
        username, sig = cookie.rsplit("|", 1)
        expected = hmac.new(SECRET_KEY.encode(), username.encode(), hashlib.sha256).hexdigest()
        if hmac.compare_digest(sig, expected):
            return username
    except Exception:
        pass
    return None
