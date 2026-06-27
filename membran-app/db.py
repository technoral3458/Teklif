"""SQLite veri katmanı - şema ve yardımcı sorgu fonksiyonları."""
import os
import sqlite3

from config import DB_PATH
from auth import hash_password

SCHEMA = """
-- ===== Modül 1: Maliyet =====
CREATE TABLE IF NOT EXISTS membrane_materials (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    material_type TEXT DEFAULT 'other',
    price REAL DEFAULT 0,
    currency TEXT DEFAULT 'TRY',
    unit TEXT DEFAULT 'm2',
    sheet_width REAL DEFAULT 0,
    sheet_height REAL DEFAULT 0,
    usage_per_m2 REAL DEFAULT 1,
    notes TEXT DEFAULT '',
    created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS membrane_rates (
    currency TEXT PRIMARY KEY,
    rate_to_try REAL DEFAULT 1,
    updated_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS membrane_lists (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    notes TEXT DEFAULT '',
    created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS membrane_doors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    list_id INTEGER REFERENCES membrane_lists(id),
    project_name TEXT DEFAULT '',
    door_name TEXT DEFAULT '',
    width_mm REAL NOT NULL,
    height_mm REAL NOT NULL,
    quantity INTEGER DEFAULT 1,
    created_at TEXT DEFAULT (datetime('now'))
);

-- ===== Modül 2: Kapak Model / NC =====
CREATE TABLE IF NOT EXISTS membrane_cap_models (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    tool_no INTEGER DEFAULT 1,
    spindle_speed INTEGER DEFAULT 18000,
    feed_xy INTEGER DEFAULT 3000,
    feed_z INTEGER DEFAULT 1000,
    safe_z REAL DEFAULT 5.0,
    constants_json TEXT DEFAULT '{}',
    created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS membrane_tools (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    tool_no INTEGER DEFAULT 1,
    diameter REAL DEFAULT 6.0,
    length REAL DEFAULT 0,
    feed_xy INTEGER DEFAULT 3000,
    feed_z INTEGER DEFAULT 1000,
    notes TEXT DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS membrane_cap_ops (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    model_id INTEGER NOT NULL REFERENCES membrane_cap_models(id),
    name TEXT DEFAULT '',
    tool_no INTEGER DEFAULT 1,
    tool_id INTEGER REFERENCES membrane_tools(id),
    depth TEXT DEFAULT '-T',
    feed TEXT DEFAULT '',
    ref_corner TEXT DEFAULT 'BL',
    seq INTEGER DEFAULT 0,
    op_type TEXT DEFAULT 'inner',
    comp_mode TEXT DEFAULT 'none',
    offset_side TEXT DEFAULT 'center'
);

CREATE TABLE IF NOT EXISTS membrane_cap_moves (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    op_id INTEGER NOT NULL REFERENCES membrane_cap_ops(id),
    move_type TEXT DEFAULT 'line',
    x TEXT DEFAULT '0',
    y TEXT DEFAULT '0',
    cx TEXT DEFAULT '0',
    cy TEXT DEFAULT '0',
    r TEXT DEFAULT '0',
    seq INTEGER DEFAULT 0
);

-- ===== Modül 3: Nesting =====
CREATE TABLE IF NOT EXISTS membrane_cap_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    notes TEXT DEFAULT '',
    sheet_w REAL DEFAULT 2800,
    sheet_h REAL DEFAULT 1100,
    margin REAL DEFAULT 5,
    created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS membrane_cap_job_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES membrane_cap_jobs(id),
    model_id INTEGER REFERENCES membrane_cap_models(id),
    model_name TEXT DEFAULT '',
    cap_w REAL NOT NULL DEFAULT 400,
    cap_h REAL NOT NULL DEFAULT 600,
    qty INTEGER DEFAULT 1,
    notes TEXT DEFAULT '',
    seq INTEGER DEFAULT 0
);

-- ===== Modül 5: 3B Konfigüratör (teklifler) =====
CREATE TABLE IF NOT EXISTS membrane_shelf_quotes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT DEFAULT '',
    customer TEXT DEFAULT '',
    params_json TEXT DEFAULT '{}',
    price REAL DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now'))
);

-- ===== Kullanıcılar (yönetici girişi) =====
CREATE TABLE IF NOT EXISTS membrane_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT DEFAULT 'admin',
    created_at TEXT DEFAULT (datetime('now'))
);
"""

DEFAULT_RATES = [("USD", 1.0), ("EUR", 1.0), ("GBP", 1.0)]


def get_conn():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init():
    conn = get_conn()
    conn.executescript(SCHEMA)
    for code, rate in DEFAULT_RATES:
        conn.execute(
            "INSERT OR IGNORE INTO membrane_rates (currency, rate_to_try) VALUES (?, ?)",
            (code, rate),
        )
    # İlk açılışta yönetici hesabı oluştur (yoksa)
    row = conn.execute("SELECT COUNT(*) FROM membrane_users").fetchone()
    if row[0] == 0:
        username = os.environ.get("ADMIN_USER", "admin")
        password = os.environ.get("ADMIN_PASSWORD", "admin123")
        conn.execute(
            "INSERT INTO membrane_users (username, password_hash, role) VALUES (?, ?, 'admin')",
            (username, hash_password(password)),
        )
    conn.commit()
    conn.close()


def query(sql, args=()):
    conn = get_conn()
    cur = conn.execute(sql, args)
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


def one(sql, args=()):
    rows = query(sql, args)
    return rows[0] if rows else None


def execute(sql, args=()):
    conn = get_conn()
    cur = conn.execute(sql, args)
    conn.commit()
    last = cur.lastrowid
    conn.close()
    return last
