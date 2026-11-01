"""Eski sistemin tabloları bir kez yedeklenip silinir; yeni sistemin verisi kalır."""
import os
import sqlite3
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def test_eski_tablolar_silinir_yedeklenir():
    import config
    import db
    eski_yol = db.DB_PATH
    d = tempfile.mkdtemp()
    config.DB_PATH = db.DB_PATH = os.path.join(d, "titaniumcam.db")
    try:
        c = sqlite3.connect(db.DB_PATH)
        c.execute("CREATE TABLE vardar_orders (id INTEGER PRIMARY KEY, customer TEXT)")
        c.execute("INSERT INTO vardar_orders (customer) VALUES ('Eski Müşteri')")
        c.execute("CREATE TABLE membrane_materials (id INTEGER PRIMARY KEY)")
        c.execute("CREATE TABLE membrane_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, "
                  "password_hash TEXT NOT NULL, role TEXT DEFAULT 'admin', created_at TEXT DEFAULT (datetime('now')))")
        c.execute("INSERT INTO membrane_users (username, password_hash, role) VALUES ('musteri@x.com', 'h', 'customer')")
        c.commit(); c.close()
        db.init()
        tablolar = {r["name"] for r in db.query("SELECT name FROM sqlite_master WHERE type='table'")}
        assert not tablolar & set(db.ESKI_TABLOLAR)
        assert {"kabin_taslak", "cabinet_orders", "acil_lisanslar", "api_keys"} <= tablolar
        assert db.one("SELECT 1 FROM membrane_users WHERE username='musteri@x.com'")      # kullanıcı kalır
        yedek = [f for f in os.listdir(d) if f.startswith("eski_sistem_yedegi_")]
        assert len(yedek) == 1
        y = sqlite3.connect(os.path.join(d, yedek[0]))
        assert y.execute("SELECT customer FROM vardar_orders").fetchone()[0] == "Eski Müşteri"
        y.close()
        db.init()                                       # ikinci açılış: yeni yedek yok, hata yok
        assert len([f for f in os.listdir(d) if f.startswith("eski_sistem_yedegi_")]) == 1
    finally:
        config.DB_PATH = db.DB_PATH = eski_yol
