"""
Ürün kayıt defteri (konfigüratör).

Her ürün bir 'layout' (3B yerleşim mantığı) + ölçü aralıkları + hangi
opsiyonların geçerli olduğunu tanımlar. Fiyatlandırma cfg_prices (yönetici)
üzerinden ortaktır. Yönetici, cfg_products tablosundan ürünleri aç/kapatır
ve adını değiştirebilir.

layout:
  'wardrobe' = dikey dolap (orta bölme, sol askılık, sağ raf) — gardırop, vestiyer
  'lowboard' = yatay dolap (bölmeli, kapak/çekmece sırası) — TV ünitesi, mutfak
"""
import json
import db

REGISTRY = [
    {"id": "gardirop", "name": "Gardırop", "layout": "wardrobe",
     "dims": {"W": [800, 2400, 1500], "H": [1800, 2600, 2200], "D": [500, 650, 600]},
     "opt": {"doors": [2, 4, 3], "shelves": [0, 6, 4], "drawers": [0, 4, 2],
             "door_type": 1, "base": 1, "back": 1, "hanging": 1, "handles": 1, "two_tone": 1}},
    {"id": "tv", "name": "TV Ünitesi", "layout": "lowboard",
     "dims": {"W": [1000, 3000, 1800], "H": [400, 700, 500], "D": [300, 550, 400]},
     "opt": {"doors": [0, 5, 3], "shelves": [0, 3, 0], "drawers": [0, 3, 1],
             "door_type": 1, "base": 1, "back": 1, "hanging": 0, "handles": 1, "two_tone": 1}},
    {"id": "vestiyer", "name": "Vestiyer / Boy Dolabı", "layout": "wardrobe",
     "dims": {"W": [400, 1200, 800], "H": [1800, 2400, 2000], "D": [350, 600, 400]},
     "opt": {"doors": [1, 3, 2], "shelves": [0, 8, 5], "drawers": [0, 3, 1],
             "door_type": 1, "base": 1, "back": 1, "hanging": 1, "handles": 1, "two_tone": 1}},
    {"id": "mutfak", "name": "Mutfak Alt Dolabı", "layout": "lowboard",
     "dims": {"W": [300, 3000, 1200], "H": [700, 950, 720], "D": [500, 650, 580]},
     "opt": {"doors": [0, 6, 2], "shelves": [0, 3, 1], "drawers": [0, 6, 2],
             "door_type": 1, "base": 1, "back": 1, "hanging": 0, "handles": 1, "two_tone": 1}},
]

_BY_ID = {p["id"]: p for p in REGISTRY}


def seed():
    """cfg_products tablosunu ilk açılışta doldur (aç/kapa + ad için)."""
    for seqi, p in enumerate(REGISTRY):
        db.execute(
            "INSERT OR IGNORE INTO cfg_products (id, name, enabled, seq) VALUES (?,?,1,?)",
            (p["id"], p["name"], seqi),
        )


def _overrides():
    rows = db.query("SELECT * FROM cfg_products")
    return {r["id"]: r for r in rows}


def list_products(only_enabled=True):
    """Kayıt defteri + yönetici override (ad/enabled) birleşik."""
    ov = _overrides()
    out = []
    for p in REGISTRY:
        o = ov.get(p["id"], {})
        if only_enabled and o and not o.get("enabled", 1):
            continue
        merged = dict(p)
        if o.get("name"):
            merged["name"] = o["name"]
        merged["enabled"] = o.get("enabled", 1)
        merged["seq"] = o.get("seq", 0)
        out.append(merged)
    out.sort(key=lambda x: x.get("seq", 0))
    return out


def get(pid):
    return _BY_ID.get(pid)
