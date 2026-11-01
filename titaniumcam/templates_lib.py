"""
Parametrik mobilya şablonları (konfigüratör motoru).

Her şablon, parçaları (panel/raf/kapak/çekmece...) formüllerle tanımlar.
Formüller W,H,D (mm) ve şablonun kendi parametrelerine (doors, shelves...)
bağlıdır. Tekrarlar (repeat) ile tek tanım N parça üretir (raflar, kapaklar).
İstemci (Three.js) ve sunucu (fiyat) aynı formülleri kullanır.

Koordinat: x merkez (-W/2..W/2), y taban 0..H, z derinlik (-D/2..D/2). mm.
PT = panel kalınlığı (18 mm), sabit olarak sağlanır.
"""
from expr import eval_expr

PANEL_THICKNESS = 18

# Renk seçenekleri (her şablonda ortak)
COLORS = ["natural", "oak", "walnut", "white", "anthracite"]
COLOR_PREMIUM = {"natural": 0.0, "oak": 0.05, "walnut": 0.10,
                 "white": 0.03, "anthracite": 0.08}


TEMPLATES = [
    # ------------------------------------------------------------------
    {
        "id": "moduler",
        "category": "Modüler Dolap / Raf",
        "name": "Modüler Açık Raf",
        "description": "Kolonlu açık raf sistemi. Kolon ve raf sayısı ayarlanır.",
        "params": [
            {"key": "W", "label": "Genişlik (mm)", "type": "range", "min": 600, "max": 3000, "step": 50, "default": 1600},
            {"key": "H", "label": "Yükseklik (mm)", "type": "range", "min": 600, "max": 2400, "step": 50, "default": 2000},
            {"key": "D", "label": "Derinlik (mm)", "type": "range", "min": 250, "max": 600, "step": 10, "default": 350},
            {"key": "columns", "label": "Kolon sayısı", "type": "range", "min": 1, "max": 6, "step": 1, "default": 3},
            {"key": "shelves", "label": "Raf sayısı (kat)", "type": "range", "min": 2, "max": 8, "step": 1, "default": 5},
        ],
        "parts": [
            {"mat": "panel", "repeat": [["c", "columns+1"]],
             "x": "-W/2 + c*(W/columns)", "y": "H/2", "z": "0",
             "w": "PT", "h": "H", "d": "D"},
            {"mat": "shelf", "repeat": [["c", "columns"], ["s", "shelves"]],
             "x": "-W/2 + c*(W/columns) + (W/columns)/2",
             "y": "s*(H-PT)/(shelves-1) + PT/2",
             "w": "(W/columns) - PT", "h": "PT", "d": "D"},
        ],
        "price_expr": "500 + (((columns+1)*H*D + shelves*W*D)/1000000)*900",
    },
    # ------------------------------------------------------------------
    {
        "id": "tv",
        "category": "TV Ünitesi",
        "name": "TV Ünitesi (Alt Dolap)",
        "description": "Kapaklı alt dolap. Genişlik ve kapak sayısı ayarlanır.",
        "params": [
            {"key": "W", "label": "Genişlik (mm)", "type": "range", "min": 1000, "max": 3000, "step": 50, "default": 1800},
            {"key": "H", "label": "Yükseklik (mm)", "type": "range", "min": 400, "max": 700, "step": 10, "default": 500},
            {"key": "D", "label": "Derinlik (mm)", "type": "range", "min": 300, "max": 500, "step": 10, "default": 400},
            {"key": "doors", "label": "Kapak sayısı", "type": "range", "min": 1, "max": 5, "step": 1, "default": 3},
        ],
        "parts": [
            {"mat": "panel", "x": "0", "y": "PT/2", "z": "0", "w": "W", "h": "PT", "d": "D"},
            {"mat": "panel", "x": "0", "y": "H-PT/2", "z": "0", "w": "W", "h": "PT", "d": "D"},
            {"mat": "panel", "x": "-W/2+PT/2", "y": "H/2", "z": "0", "w": "PT", "h": "H", "d": "D"},
            {"mat": "panel", "x": "W/2-PT/2", "y": "H/2", "z": "0", "w": "PT", "h": "H", "d": "D"},
            {"mat": "back", "x": "0", "y": "H/2", "z": "-D/2+PT/2", "w": "W", "h": "H", "d": "PT"},
            {"mat": "panel", "repeat": [["i", "doors-1"]],
             "x": "-W/2 + (i+1)*(W/doors)", "y": "H/2", "z": "0", "w": "PT", "h": "H", "d": "D"},
            {"mat": "door", "repeat": [["i", "doors"]],
             "x": "-W/2 + i*(W/doors) + (W/doors)/2", "y": "H/2", "z": "D/2-PT/2",
             "w": "(W/doors)-8", "h": "H-8", "d": "PT"},
        ],
        "price_expr": "500 + ((2*W*D + 2*H*D + 2*W*H + (doors-1)*H*D)/1000000)*900 + doors*200",
    },
    # ------------------------------------------------------------------
    {
        "id": "gardirop",
        "category": "Gardırop",
        "name": "Gardırop (Kapaklı Dolap)",
        "description": "Tam boy kapaklı dolap. Askılık + raf + çekmece bölmeli.",
        "params": [
            {"key": "W", "label": "Genişlik (mm)", "type": "range", "min": 800, "max": 2400, "step": 50, "default": 1500},
            {"key": "H", "label": "Yükseklik (mm)", "type": "range", "min": 1800, "max": 2600, "step": 50, "default": 2200},
            {"key": "D", "label": "Derinlik (mm)", "type": "range", "min": 500, "max": 650, "step": 10, "default": 600},
            {"key": "doors", "label": "Kapak sayısı", "type": "range", "min": 2, "max": 4, "step": 1, "default": 3},
            {"key": "shelves", "label": "Raf (sağ bölme)", "type": "range", "min": 0, "max": 6, "step": 1, "default": 4},
            {"key": "drawers", "label": "Çekmece (sol alt)", "type": "range", "min": 0, "max": 4, "step": 1, "default": 2},
        ],
        "parts": [
            {"mat": "panel", "x": "0", "y": "PT/2", "z": "0", "w": "W", "h": "PT", "d": "D"},
            {"mat": "panel", "x": "0", "y": "H-PT/2", "z": "0", "w": "W", "h": "PT", "d": "D"},
            {"mat": "panel", "x": "-W/2+PT/2", "y": "H/2", "z": "0", "w": "PT", "h": "H", "d": "D"},
            {"mat": "panel", "x": "W/2-PT/2", "y": "H/2", "z": "0", "w": "PT", "h": "H", "d": "D"},
            {"mat": "back", "x": "0", "y": "H/2", "z": "-D/2+PT/2", "w": "W", "h": "H", "d": "PT"},
            {"mat": "panel", "x": "0", "y": "H/2", "z": "0", "w": "PT", "h": "H", "d": "D"},
            {"mat": "shelf", "repeat": [["s", "shelves"]],
             "x": "W/4", "y": "PT + (s+1)*(H-2*PT)/(shelves+1)",
             "w": "W/2 - PT", "h": "PT", "d": "D-PT"},
            {"mat": "metal", "x": "-W/4", "y": "H-220", "z": "0", "w": "W/2 - PT", "h": "30", "d": "30"},
            {"mat": "drawer", "repeat": [["k", "drawers"]],
             "x": "-W/4", "y": "PT + 110 + k*180", "z": "D/2-PT/2",
             "w": "W/2 - PT - 12", "h": "160", "d": "PT"},
            {"mat": "door", "repeat": [["i", "doors"]],
             "x": "-W/2 + i*(W/doors) + (W/doors)/2", "y": "H/2", "z": "D/2-PT/2",
             "w": "(W/doors)-8", "h": "H-8", "d": "PT"},
        ],
        "price_expr": "500 + ((2*W*D + 2*H*D + 2*W*H + H*D + shelves*(W/2)*D + drawers*(W/2)*180)/1000000)*900 + doors*250 + drawers*650",
    },
]

_BY_ID = {t["id"]: t for t in TEMPLATES}


def list_templates():
    return TEMPLATES


def get_template(tid):
    return _BY_ID.get(tid)


def price_template(tid, params):
    """Sunucu tarafı yetkili fiyat."""
    tpl = get_template(tid)
    if not tpl:
        return {"total": 0}
    variables = {"PT": PANEL_THICKNESS}
    for p in tpl["params"]:
        key = p["key"]
        try:
            variables[key] = float(params.get(key, p.get("default", 0)))
        except (TypeError, ValueError):
            variables[key] = p.get("default", 0)
    material = eval_expr(tpl["price_expr"], variables)
    color = params.get("color", "natural")
    premium = COLOR_PREMIUM.get(color, 0.0)
    total = material * (1 + premium)
    return {"material": round(material, 2), "premium_pct": round(premium * 100, 1),
            "total": round(total, 2)}
