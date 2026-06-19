"""Ortam değişkenleri ve basit .env yükleyici (harici bağımlılık yok)."""
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))


def load_env():
    """Varsa .env dosyasını os.environ içine yükler (zaten tanımlı olanları ezmez)."""
    path = os.path.join(BASE_DIR, ".env")
    if not os.path.exists(path):
        return
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            key, value = key.strip(), value.strip().strip('"').strip("'")
            os.environ.setdefault(key, value)


load_env()

ANTHROPIC_API_KEY = os.environ.get("ANTHROPIC_API_KEY", "")
ANTHROPIC_MODEL = os.environ.get("ANTHROPIC_MODEL", "claude-sonnet-4-6")
SECRET_KEY = os.environ.get("SECRET_KEY", "dev-secret")
DB_PATH = os.path.join(BASE_DIR, "data", "membrane.db")
