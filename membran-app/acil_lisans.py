"""Acil lisans servisi: çekirdek (anahtar, imza, karar sırası, CSV, IP / konum).

TitaniumCam bilgisayara bağlı lisansla çalışır. Bilgisayar bozulursa müşteri elindeki acil kodu
yeni bilgisayarda girer; program kodu bu siteye gönderir, site o bilgisayara bağlı kısa süreli
(varsayılan 7 gün) Ed25519 imzalı lisans döner.

Bağlayıcı kurallar (docs/acil_lisans.md):
- Acil kodun kendisi hiçbir yerde saklanmaz / yazılmaz; yalnız SHA-256 özeti.
- İmza anahtarı bu servise özeldir; özel anahtar web kökü dışında, 0600 izinle durur,
  hiçbir ekrana / günlüğe / depoya girmez ve ÜZERİNE YAZILMAZ.
- Tarihler YYYY-MM-DD, Europe/Istanbul.
"""
import base64
import csv
import datetime as dt
import hashlib
import io
import ipaddress
import json
import logging
import os
import re
import sqlite3
from zoneinfo import ZoneInfo

import db
from config import BASE_DIR

log = logging.getLogger("acil_lisans")
IST = ZoneInfo("Europe/Istanbul")

ANAHTAR_DIR = os.environ.get("ACIL_ANAHTAR_DIR", "/etc/membran/acil_lisans")
ANAHTAR_DOSYA = "ozel_anahtar.pem"
GUVENILEN_PROXY = {x.strip() for x in os.environ.get("ACIL_GUVENILEN_PROXY", "127.0.0.1,::1").split(",") if x.strip()}
HTTP_IZIN = os.environ.get("ACIL_HTTP", "") == "1"          # yalnız yerel deneme
GEOIP_DB = os.environ.get("GEOIP_DB", "/var/lib/GeoIP/dbip-city-lite.mmdb")

MAX_GOVDE = 4096
HATA_LIMIT = 10           # IP başına saatte hatalı deneme
OLAY_LIMIT = 60           # IP başına saatte event isteği
VARSAYILAN = {"sure": 7, "aralik": 90, "eposta": ""}
MAKINE_ALAN = ("u", "a", "g", "d")
_HEX16 = re.compile(r"^[0-9a-f]{16}$")
_HEX64 = re.compile(r"^[0-9a-f]{64}$")
_TARIH = re.compile(r"^\d{4}-\d{2}-\d{2}$")


# ── zaman ──────────────────────────────────────────────────────────────────
def simdi():
    return dt.datetime.now(IST)


def bugun():
    return simdi().date()


def tarih(d):
    return d.isoformat() if d else ""


def tarih_oku(s):
    return dt.date.fromisoformat(s) if s else None


# ── ayarlar ────────────────────────────────────────────────────────────────
def ayar():
    r = db.one("SELECT deger FROM kabin_ayar WHERE anahtar='acil_lisans'")
    a = dict(VARSAYILAN)
    if r:
        try:
            a.update(json.loads(r["deger"]))
        except ValueError:
            pass
    return a


def ayar_yaz(sure, aralik, eposta):
    sure, aralik = int(sure), int(aralik)
    if not 1 <= sure <= 15:
        raise ValueError("Süre 1-15 gün olmalı")
    if not 1 <= aralik <= 3650:
        raise ValueError("Aralık 1-3650 gün olmalı")
    eposta = (eposta or "").strip()
    if eposta and not re.fullmatch(r"[^@\s,;]+@[^@\s,;]+\.[^@\s,;]+", eposta):
        raise ValueError("E-posta adresi geçersiz")
    db.execute("INSERT INTO kabin_ayar (anahtar, deger) VALUES ('acil_lisans', ?) "
               "ON CONFLICT(anahtar) DO UPDATE SET deger=excluded.deger",
               (json.dumps({"sure": sure, "aralik": aralik, "eposta": eposta}),))


# ── anahtar ────────────────────────────────────────────────────────────────
def _yol():
    d = os.path.realpath(ANAHTAR_DIR)
    if d == os.path.realpath(BASE_DIR) or d.startswith(os.path.realpath(BASE_DIR) + os.sep):
        raise RuntimeError("Anahtar klasörü uygulama klasörünün içinde olamaz")
    return os.path.join(d, ANAHTAR_DOSYA)


def _ozel():
    from cryptography.hazmat.primitives import serialization
    yol = _yol()
    if not os.path.exists(yol):
        return None
    with open(yol, "rb") as f:
        return serialization.load_pem_private_key(f.read(), password=None)


def acik_anahtar_hex():
    """Açık anahtar (64 hex) ya da None (anahtar yok)."""
    from cryptography.hazmat.primitives import serialization
    k = _ozel()
    if k is None:
        return None
    return k.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw).hex()


def anahtar_uret():
    """Tek sefer: dosya varsa ASLA üzerine yazmaz (O_EXCL)."""
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    yol = _yol()
    os.makedirs(os.path.dirname(yol), mode=0o700, exist_ok=True)
    pem = Ed25519PrivateKey.generate().private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
    try:
        fd = os.open(yol, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        raise RuntimeError("Anahtar zaten var; üzerine yazılmaz")
    with os.fdopen(fd, "wb") as f:
        f.write(pem)
    return acik_anahtar_hex()


def ham_bayt(lisans_no, musteri, makine, verilis, bitis, moduller):
    """İmzalanan ham bayt: alan sırası programla aynı (deneme vektörü bire bir)."""
    nesne = {"urun": "cam", "lisans_no": lisans_no, "musteri": musteri, "eposta": "",
             "makine": makine, "verilis": verilis, "bitis": bitis,
             "moduller": list(moduller or []), "acil": True}
    return json.dumps(nesne, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def imzala(ham, ozel=None):
    ozel = ozel or _ozel()
    if ozel is None:
        raise RuntimeError("Anahtar yok")
    return json.dumps({"veri": base64.b64encode(ham).decode(),
                       "imza": base64.b64encode(ozel.sign(ham)).decode()})


def kod_ozeti(kod):
    """Program kodu tiresiz, büyük harfle gönderir; özet bu metnin SHA-256'sı (küçük hex)."""
    return hashlib.sha256(kod.encode("utf-8")).hexdigest()


def modul_listesi(s):
    return [m.strip() for m in (s or "").split(",") if m.strip()]


# ── istek doğrulama ────────────────────────────────────────────────────────
class Ret(Exception):
    def __init__(self, durum, govde, hata_say=False):
        super().__init__(govde.get("error"))
        self.durum, self.govde, self.hata_say = durum, govde, hata_say


def istek_coz(ham):
    """Gövdeyi doğrular. Döner: (kod_ozet, makine, istek_kodu, surum). Ret(400)."""
    bad = Ret(400, {"error": "bad_request"}, hata_say=True)
    if len(ham) > MAX_GOVDE:
        raise bad
    try:
        g = json.loads(ham)
    except (ValueError, UnicodeDecodeError):
        raise bad
    if not isinstance(g, dict):
        raise bad
    kod, mak, ik, surum = g.get("acil_kod"), g.get("makine"), g.get("istek_kodu"), g.get("surum", "")
    if not isinstance(kod, str):
        raise bad
    kod = re.sub(r"[\s-]", "", kod).upper()
    if not 8 <= len(kod) <= 64:
        raise bad
    if not isinstance(mak, dict) or not mak or any(k not in MAKINE_ALAN for k in mak):
        raise bad
    if not all(isinstance(v, str) and _HEX16.match(v) for v in mak.values()):
        raise bad
    if not isinstance(ik, str) or not ik.strip() or len(ik) > 200:
        raise bad
    if not isinstance(surum, str) or len(surum) > 40:
        raise bad
    return kod_ozeti(kod), mak, ik, surum


# ── IP / konum ─────────────────────────────────────────────────────────────
def gercek_ip(request):
    """X-Real-IP / X-Forwarded-For yalnız güvenilen proxy'den (aynı sunucudaki nginx) okunur."""
    es = request.client.host if request.client else ""
    if es in GUVENILEN_PROXY:
        aday = (request.headers.get("x-real-ip") or "").strip()
        if not aday:
            xff = [x.strip() for x in (request.headers.get("x-forwarded-for") or "").split(",") if x.strip()]
            aday = xff[-1] if xff else ""
        try:
            return str(ipaddress.ip_address(aday))
        except ValueError:
            pass
    return es


def https_mi(request):
    es = request.client.host if request.client else ""
    proto = request.headers.get("x-forwarded-proto", "") if es in GUVENILEN_PROXY else ""
    return HTTP_IZIN or (proto or request.url.scheme) == "https"


_geo = {"okuyucu": None, "denendi": False}


def konum(ip):
    """Yerel GeoIP veritabanından (ülke, şehir); IP dışarı gönderilmez. Bulunamazsa ("", "")."""
    if not _geo["denendi"]:
        _geo["denendi"] = True
        try:
            import maxminddb
            if os.path.exists(GEOIP_DB):
                _geo["okuyucu"] = maxminddb.open_database(GEOIP_DB)
        except Exception as e:                     # kütüphane / dosya yok: konum boş kalır
            log.warning("GeoIP açılamadı: %s", type(e).__name__)
    r = _geo["okuyucu"]
    if not r or not ip:
        return "", ""
    try:
        k = r.get(ip) or {}
    except Exception:
        return "", ""
    ad = lambda x: ((x or {}).get("names") or {}).get("tr") or ((x or {}).get("names") or {}).get("en") or ""  # noqa: E731
    return (ad(k.get("country")) or (k.get("country") or {}).get("iso_code", ""))[:60], ad(k.get("city"))[:80]


def geoip_var():
    konum("")
    return _geo["okuyucu"] is not None


# ── sayaçlar (2 süreç çalıştığı için veritabanında) ────────────────────────
def _say(ip, tur):
    esik = (simdi() - dt.timedelta(hours=1)).strftime("%Y-%m-%d %H:%M:%S")
    return db.one("SELECT COUNT(*) n FROM acil_sayac WHERE ip=? AND tur=? AND zaman>=?", (ip, tur, esik))["n"]


def say_ekle(ip, tur):
    db.execute("INSERT INTO acil_sayac (ip, tur, zaman) VALUES (?,?,?)", (ip, tur, simdi().strftime("%Y-%m-%d %H:%M:%S")))
    if tur == "olay":                          # ara sıra eski kayıtları sil
        db.execute("DELETE FROM acil_sayac WHERE zaman < ?", ((simdi() - dt.timedelta(days=2)).strftime("%Y-%m-%d %H:%M:%S"),))


def hata_limiti_asildi(ip):
    return _say(ip, "hata") >= HATA_LIMIT


def olay_limiti_asildi(ip):
    return _say(ip, "olay") >= OLAY_LIMIT


# ── olay kaydı ─────────────────────────────────────────────────────────────
def olay_yaz(conn, lisans_no, olay, ip, ulke, sehir, istek_kodu, surum):
    conn.execute("INSERT INTO acil_olaylar (lisans_no, olay, ip, ulke, sehir, istek_kodu, surum, zaman) "
                 "VALUES (?,?,?,?,?,?,?,?)",
                 (lisans_no, olay, ip, ulke, sehir, (istek_kodu or "")[:200], (surum or "")[:40],
                  simdi().strftime("%Y-%m-%d %H:%M:%S")))


# ── karar sırası (bölüm 3, adım 3-7) ──────────────────────────────────────
def etkinlestir(kod_ozet, makine, istek_kodu, surum, ip, ulke, sehir):
    """Tek veritabanı işlemi (BEGIN IMMEDIATE = yazma kilidi): aynı anda gelen iki istek
    iki yeni süre alamaz. Döner: (durum, govde, bildirim | None)."""
    a = ayar()
    ozel = _ozel()
    if ozel is None:
        return 503, {"error": "unavailable"}, None
    conn = sqlite3.connect(db.DB_PATH, timeout=15, isolation_level=None)
    conn.row_factory = sqlite3.Row
    try:
        conn.execute("BEGIN IMMEDIATE")
        r = conn.execute("SELECT * FROM acil_lisanslar WHERE kod_ozet=?", (kod_ozet,)).fetchone()
        if r is None:                                                      # 3
            olay_yaz(conn, None, "kod_hatali", ip, ulke, sehir, istek_kodu, surum)
            conn.execute("COMMIT")
            raise Ret(404, {"error": "code"}, hata_say=True)
        r = dict(r)
        gun = bugun()
        ln = r["lisans_no"]
        ana_bitis = tarih_oku(r["bitis"])
        if r["kapali"] or (ana_bitis and ana_bitis < gun):                 # 4
            olay_yaz(conn, ln, "reddedildi", ip, ulke, sehir, istek_kodu, surum)
            conn.execute("COMMIT")
            neden = "lisans kapalı" if r["kapali"] else "ana lisansın süresi dolmuş"
            return 403, {"error": "blocked"}, (r, "reddedildi", neden, "")
        son_bitis = tarih_oku(r["son_bitis"])
        if r["son_istek_kodu"] == istek_kodu and son_bitis and gun <= son_bitis:   # 5
            ham = ham_bayt(ln, r["musteri"], makine, r["son_verilis"], r["son_bitis"], modul_listesi(r["moduller"]))
            olay_yaz(conn, ln, "yeniden", ip, ulke, sehir, istek_kodu, surum)
            conn.execute("COMMIT")
            return 200, {"lisans": imzala(ham, ozel), "bitis": r["son_bitis"]}, None
        son_verilis = tarih_oku(r["son_verilis"])
        if not r["elle_izin"] and son_verilis and gun < son_verilis + dt.timedelta(days=a["aralik"]):   # 6
            sonraki = tarih(son_verilis + dt.timedelta(days=a["aralik"]))
            olay_yaz(conn, ln, "reddedildi", ip, ulke, sehir, istek_kodu, surum)
            conn.execute("COMMIT")
            return 409, {"error": "limit", "sonraki": sonraki}, (r, "reddedildi", f"sıklık sınırı (sonraki: {sonraki})", "")
        bitis = gun + dt.timedelta(days=a["sure"])                         # 7
        if ana_bitis and ana_bitis < bitis:
            bitis = ana_bitis                     # acil lisans ana lisansın bitişini aşmaz
        verilis_s, bitis_s = tarih(gun), tarih(bitis)
        ham = ham_bayt(ln, r["musteri"], makine, verilis_s, bitis_s, modul_listesi(r["moduller"]))
        lisans = imzala(ham, ozel)
        conn.execute("UPDATE acil_lisanslar SET son_istek_kodu=?, son_verilis=?, son_bitis=?, elle_izin=0 "
                     "WHERE lisans_no=?", (istek_kodu, verilis_s, bitis_s, ln))
        olay_yaz(conn, ln, "etkinlestirme", ip, ulke, sehir, istek_kodu, surum)
        conn.execute("COMMIT")
        return 200, {"lisans": lisans, "bitis": bitis_s}, (r, "etkinlestirme", "", bitis_s)
    except Ret:
        raise
    except Exception:
        if conn.in_transaction:
            conn.execute("ROLLBACK")
        raise
    finally:
        conn.close()


# ── CSV (Lisans Aracı) ─────────────────────────────────────────────────────
CSV_BASLIK = ["lisans_no", "musteri", "moduller", "bitis", "kod_ozet"]


def csv_yukle(metin):
    """lisans_no'ya göre ekler / günceller; dosyada olmayan silinmez; kapali, elle_izin, son_*
    alanlarına dokunmaz. Döner: {eklenen, guncellenen, hatalar: [(satır, neden)]}."""
    oku = csv.reader(io.StringIO(metin.lstrip("﻿")))
    try:
        baslik = [h.strip() for h in next(oku)]
    except StopIteration:
        raise ValueError("Dosya boş")
    if baslik != CSV_BASLIK:
        raise ValueError("Başlık satırı şu olmalı: " + ",".join(CSV_BASLIK))
    sonuc = {"eklenen": 0, "guncellenen": 0, "hatalar": []}
    conn = sqlite3.connect(db.DB_PATH, timeout=15, isolation_level=None)
    try:
        conn.execute("BEGIN IMMEDIATE")
        for no, satir in enumerate(oku, start=2):
            if not any(c.strip() for c in satir):
                continue
            if len(satir) != 5:
                sonuc["hatalar"].append((no, "5 sütun olmalı"))
                continue
            ln, mus, mod, bit, oz = (c.strip() for c in satir)
            oz = oz.lower()
            if not ln or len(ln) > 60:
                sonuc["hatalar"].append((no, "lisans_no boş ya da çok uzun"))
                continue
            if not _HEX64.match(oz):
                sonuc["hatalar"].append((no, "kod_ozet 64 karakter hex olmalı"))
                continue
            if bit and not (_TARIH.match(bit) and _gecerli_tarih(bit)):
                sonuc["hatalar"].append((no, "bitis YYYY-MM-DD olmalı"))
                continue
            mod = ",".join(modul_listesi(mod))
            baska = conn.execute("SELECT lisans_no FROM acil_lisanslar WHERE kod_ozet=? AND lisans_no<>?", (oz, ln)).fetchone()
            if baska:
                sonuc["hatalar"].append((no, "bu kod özeti başka lisansta"))
                continue
            var = conn.execute("SELECT 1 FROM acil_lisanslar WHERE lisans_no=?", (ln,)).fetchone()
            if var:
                conn.execute("UPDATE acil_lisanslar SET musteri=?, moduller=?, bitis=?, kod_ozet=? WHERE lisans_no=?",
                             (mus[:120], mod[:200], bit, oz, ln))
                sonuc["guncellenen"] += 1
            else:
                conn.execute("INSERT INTO acil_lisanslar (lisans_no, musteri, moduller, bitis, kod_ozet) VALUES (?,?,?,?,?)",
                             (ln, mus[:120], mod[:200], bit, oz))
                sonuc["eklenen"] += 1
        conn.execute("COMMIT")
    except Exception:
        if conn.in_transaction:
            conn.execute("ROLLBACK")
        raise
    finally:
        conn.close()
    return sonuc


def _gecerli_tarih(s):
    try:
        dt.date.fromisoformat(s)
        return True
    except ValueError:
        return False
