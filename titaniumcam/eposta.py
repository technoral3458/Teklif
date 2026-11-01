"""E-posta gönderimi (SMTP, .env'den). Cevabı bekletmez: arka planda gönderir.

.env:
  SMTP_HOST=mail.ornek.com
  SMTP_PORT=465            # 465 = SSL, 587 = STARTTLS
  SMTP_USER=bildirim@ornek.com
  SMTP_PASS=...
  SMTP_FROM=bildirim@ornek.com   # boşsa SMTP_USER

Gönderilemezse hata günlüğe ve kabin_ayar('eposta_hata') kaydına yazılır; yönetici ekranı uyarır.
"""
import datetime as dt
import json
import logging
import os
import smtplib
import ssl
import threading
from email.message import EmailMessage
from zoneinfo import ZoneInfo

import db

log = logging.getLogger("eposta")
_son = {"is": None}              # testler bekleyebilsin


def ayarli_mi():
    return bool(os.environ.get("SMTP_HOST", "").strip())


def _hata_yaz(mesaj):
    db.execute("INSERT INTO kabin_ayar (anahtar, deger) VALUES ('eposta_hata', ?) "
               "ON CONFLICT(anahtar) DO UPDATE SET deger=excluded.deger",
               (json.dumps({"zaman": dt.datetime.now(ZoneInfo("Europe/Istanbul")).strftime("%Y-%m-%d %H:%M"), "mesaj": mesaj[:300]}),))


def hata_temizle():
    db.execute("DELETE FROM kabin_ayar WHERE anahtar='eposta_hata'")


def son_hata():
    r = db.one("SELECT deger FROM kabin_ayar WHERE anahtar='eposta_hata'")
    try:
        return json.loads(r["deger"]) if r else None
    except ValueError:
        return None


def gonder_simdi(alici, konu, govde):
    """Eşzamanlı gönderim. Hata olursa istisna fırlatır."""
    host = os.environ.get("SMTP_HOST", "").strip()
    if not host:
        raise RuntimeError("SMTP ayarı yok (.env: SMTP_HOST)")
    port = int(os.environ.get("SMTP_PORT", "465") or 465)
    kul, sif = os.environ.get("SMTP_USER", ""), os.environ.get("SMTP_PASS", "")
    m = EmailMessage()
    m["From"] = os.environ.get("SMTP_FROM", "").strip() or kul
    m["To"] = alici
    m["Subject"] = konu
    m.set_content(govde)
    bag = ssl.create_default_context()
    if port == 465:
        s = smtplib.SMTP_SSL(host, port, timeout=20, context=bag)
    else:
        s = smtplib.SMTP(host, port, timeout=20)
        s.starttls(context=bag)
    try:
        if kul:
            s.login(kul, sif)
        s.send_message(m)
    finally:
        try:
            s.quit()
        except Exception:
            pass


def gonder(alici, konu, govde):
    """Arka planda gönderir; çağıran beklemez."""
    def is_():
        try:
            if not alici:
                raise RuntimeError("Bildirim e-postası ayarlanmamış")
            gonder_simdi(alici, konu, govde)
            hata_temizle()
        except Exception as e:
            log.error("E-posta gönderilemedi: %s: %s", type(e).__name__, e)
            _hata_yaz(f"{type(e).__name__}: {e}")
    t = threading.Thread(target=is_, daemon=True)
    _son["is"] = t
    t.start()
    return t
