"""SQLite veri katmanı - şema ve yardımcı sorgu fonksiyonları."""
import os
import sqlite3

from config import DB_PATH
from auth import hash_password

SCHEMA = """
-- ===== Kullanıcılar (yönetici girişi) =====
CREATE TABLE IF NOT EXISTS membrane_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT DEFAULT 'admin',
    created_at TEXT DEFAULT (datetime('now'))
);

-- ===== ONLINE KABİN (TitaniumCam motoru) =====
-- Müşteri taslağı: güncel kabin JSON sunucuda durur (tarayıcı yalnız işlem yollar)
CREATE TABLE IF NOT EXISTS kabin_taslak (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    musteri TEXT NOT NULL,             -- müşteri kullanıcı adı; yönetici satır düzenlemesi: '@yonetici'
    ad TEXT DEFAULT '',
    sablon TEXT DEFAULT '',
    kabin_json TEXT NOT NULL,
    imlec INTEGER DEFAULT 0,           -- geri alma: kabin_taslak_surum.sira (güncel hal)
    panel_taban TEXT DEFAULT '',       -- kabin özellikleri paneli açılış hali (İptal buraya döner)
    sepette INTEGER DEFAULT 0,
    hata_var INTEGER DEFAULT 0,
    ozet_json TEXT DEFAULT '{}',
    siparis_satir_id INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS kabin_taslak_surum (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    taslak_id INTEGER NOT NULL,
    sira INTEGER NOT NULL,
    kabin_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_kabin_surum ON kabin_taslak_surum (taslak_id, sira);
-- Yönetici ayarları: alan yetkileri, şablon başına ölçü sınırları (anahtar -> JSON)
CREATE TABLE IF NOT EXISTS kabin_ayar (
    anahtar TEXT PRIMARY KEY,
    deger TEXT NOT NULL
);
-- Kabin tanımları: tür + kod başına birim fiyat ve müşteriye açık mı (yönetici ekranı)
-- tur: sablon | malzeme | bant | ray | mentese | arkalik | kulp | baglanti | donati
CREATE TABLE IF NOT EXISTS kabin_tanim (
    tur TEXT NOT NULL,
    kod TEXT NOT NULL,
    fiyat REAL DEFAULT 0,
    acik INTEGER DEFAULT 1,
    PRIMARY KEY (tur, kod)
);
-- Acil lisans servisi (acil kodun kendisi saklanmaz: yalnız SHA-256 özeti)
CREATE TABLE IF NOT EXISTS acil_lisanslar (
    lisans_no TEXT PRIMARY KEY,
    musteri TEXT DEFAULT '',
    moduller TEXT DEFAULT '',
    bitis TEXT DEFAULT '',
    kod_ozet TEXT NOT NULL UNIQUE,
    kapali INTEGER DEFAULT 0,
    elle_izin INTEGER DEFAULT 0,
    son_istek_kodu TEXT DEFAULT '',
    son_verilis TEXT DEFAULT '',
    son_bitis TEXT DEFAULT ''
);
CREATE TABLE IF NOT EXISTS acil_olaylar (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    lisans_no TEXT,
    olay TEXT NOT NULL,
    ip TEXT DEFAULT '',
    ulke TEXT DEFAULT '',
    sehir TEXT DEFAULT '',
    istek_kodu TEXT DEFAULT '',
    surum TEXT DEFAULT '',
    zaman TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_acil_olay_zaman ON acil_olaylar(zaman);
CREATE TABLE IF NOT EXISTS acil_sayac (
    ip TEXT NOT NULL,
    tur TEXT NOT NULL,
    zaman TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_acil_sayac ON acil_sayac(ip, tur, zaman);
-- TitaniumCam API anahtarları: yalnız SHA-256 özeti saklanır, anahtar bir kez gösterilir
CREATE TABLE IF NOT EXISTS api_keys (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ad TEXT DEFAULT '',
    onek TEXT DEFAULT '',              -- tanımak için ilk karakterler (gizli değil)
    ozet TEXT UNIQUE NOT NULL,
    kapak INTEGER DEFAULT 0,           -- kapak siparişleri izni
    kabin INTEGER DEFAULT 1,           -- kabin siparişleri izni
    created_at TEXT DEFAULT (datetime('now')),
    last_used_at TEXT DEFAULT '',
    revoked_at TEXT DEFAULT ''
);
-- Kabin siparişi üretim olayları (claim / durum)
CREATE TABLE IF NOT EXISTS cabinet_order_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL,
    station TEXT DEFAULT '',
    olay TEXT DEFAULT '',
    note TEXT DEFAULT '',
    created_at TEXT DEFAULT (datetime('now'))
);
-- Yöneticinin tasarlayıp mağazada sattığı hazır dolap modelleri
CREATE TABLE IF NOT EXISTS kabin_model (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ad TEXT NOT NULL,
    aciklama TEXT DEFAULT '',
    sablon TEXT DEFAULT '',
    kabin_json TEXT NOT NULL,
    onizleme_json TEXT DEFAULT '{}',     -- vitrin kartı çizimi (gövde + 2B parçalar)
    ozet_json TEXT DEFAULT '{}',
    acik INTEGER DEFAULT 0,              -- mağazada satışta mı
    sira INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);
-- Kapak siparişinin karşılığı
CREATE TABLE IF NOT EXISTS cabinet_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_no TEXT DEFAULT '',
    musteri TEXT NOT NULL,
    musteri_ad TEXT DEFAULT '',
    proje TEXT DEFAULT '',
    note TEXT DEFAULT '',
    status TEXT DEFAULT 'bekliyor',    -- bekliyor | onayli | reddedildi
    approved_at TEXT DEFAULT '',
    approved_by TEXT DEFAULT '',
    claimed_by TEXT DEFAULT '',
    claimed_at TEXT DEFAULT '',
    production_status TEXT DEFAULT '',
    degisti INTEGER DEFAULT 0,         -- havuza alındıktan sonra değişti uyarısı
    created_at TEXT DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS cabinet_order_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL,
    line_no INTEGER NOT NULL,
    cabinet_no TEXT DEFAULT '',
    name TEXT DEFAULT '',
    qty INTEGER DEFAULT 1,
    width_mm REAL DEFAULT 0,
    height_mm REAL DEFAULT 0,
    depth_mm REAL DEFAULT 0,
    tkabin TEXT NOT NULL,              -- dondurulmuş {"format":"tkabin","schema":1,"kabin":{...}}
    engine_version TEXT DEFAULT '',
    definitions_version TEXT DEFAULT '',
    note TEXT DEFAULT '',
    ozet_json TEXT DEFAULT '{}'
);
"""

# Mevcut veritabanlarına sonradan eklenen kolonlar (idempotent).
MIGRATIONS = [
    ("membrane_users", "full_name", "TEXT DEFAULT ''"),
    ("membrane_users", "email", "TEXT DEFAULT ''"),
    ("membrane_users", "phone", "TEXT DEFAULT ''"),
    ("membrane_users", "onay_sekli", "TEXT DEFAULT ''"),   # müşteri bazlı onay şekli istisnası
    ("kabin_taslak", "model_id", "INTEGER DEFAULT 0"),      # yöneticinin düzenlediği hazır model
]

# Kaldırılan eski sistemin tabloları (maliyet, kapak modelleri, nesting, Vardar, eski mağaza / konfigüratör)
ESKI_TABLOLAR = (
    "membrane_cap_job_items", "membrane_cap_jobs", "membrane_cap_moves", "membrane_cap_ops",
    "membrane_tools", "membrane_cap_models", "membrane_doors", "membrane_lists", "membrane_rates",
    "membrane_materials", "membrane_shelf_quotes", "cfg_prices", "cfg_colors", "cfg_products",
    "cfg_templates", "vardar_orders", "vardar_doors", "shop_cart",
)


def _migrate(conn):
    for table, col, decl in MIGRATIONS:
        try:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {col} {decl}")
        except sqlite3.OperationalError:
            pass  # kolon zaten var
    # eski varsayılan firma adı → Titanium
    conn.execute("UPDATE kabin_ayar SET deger=? WHERE anahtar='firma' AND deger LIKE '%Ersan Mobilya%'",
                 ('{"ad": "Titanium"}',))


def _eski_sistemi_sil(conn):
    """Eski sistemin tablolarını bir kez yedekleyip siler (yedek: data/eski_sistem_yedegi_*.db)."""
    var = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")]
    sil = [t for t in ESKI_TABLOLAR if t in var]
    if not sil:
        return
    import datetime
    yedek = os.path.join(os.path.dirname(DB_PATH), "eski_sistem_yedegi_" + datetime.date.today().strftime("%Y%m%d") + ".db")
    if not os.path.exists(yedek):
        hedef = sqlite3.connect(yedek)
        conn.commit()
        conn.backup(hedef)
        hedef.close()
    conn.execute("PRAGMA foreign_keys=OFF")
    for t in sil:
        conn.execute(f"DROP TABLE IF EXISTS {t}")
    conn.commit()
    conn.execute("VACUUM")


def get_conn():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init():
    conn = get_conn()
    conn.executescript(SCHEMA)
    _migrate(conn)
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
    _eski_sistemi_sil(conn)
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
