"""
Modüler raf konfigüratörü - fiyat hesabı (sunucu tarafı, yetkili).

Aynı formül configurator.html içindeki JS'te de var (canlı önizleme için).
Değiştirirsen ikisini birlikte güncelle.
"""

# Fiyat sabitleri (TL)
BASE = 500.0            # taban (işçilik/montaj)
WOOD_PER_M2 = 900.0     # raf tablası (lamine/MDF) m²
FRAME_PER_M = 180.0     # metal çerçeve (profil) metre
DRAWER_EACH = 650.0     # çekmece adet
DOOR_EACH = 450.0       # kapak adet

COLOR_PREMIUM = {       # renk/kaplama ek oranı
    "natural": 0.0, "oak": 0.05, "walnut": 0.10, "black": 0.08, "white": 0.03,
}


def price_shelf(p):
    """p: {W,H,D (mm), columns, shelves, color, drawers, doors} -> fiyat dökümü."""
    W = float(p.get("W", 0)); H = float(p.get("H", 0)); D = float(p.get("D", 0))
    C = max(1, int(p.get("columns", 1)))
    S = max(0, int(p.get("shelves", 0)))
    color = p.get("color", "natural")
    drawers = int(p.get("drawers", 0))
    doors = int(p.get("doors", 0))

    bay_w = W / C
    boards = C * S
    area_each = (bay_w / 1000.0) * (D / 1000.0)
    wood_cost = boards * area_each * WOOD_PER_M2

    frame_len = ((C + 1) * (H / 1000.0) * 2) + (4 * (W / 1000.0)) + (4 * (D / 1000.0))
    frame_cost = frame_len * FRAME_PER_M

    acc_cost = drawers * DRAWER_EACH + doors * DOOR_EACH

    subtotal = BASE + wood_cost + frame_cost + acc_cost
    premium = COLOR_PREMIUM.get(color, 0.0)
    total = subtotal * (1 + premium)

    return {
        "wood_cost": round(wood_cost, 2),
        "frame_cost": round(frame_cost, 2),
        "acc_cost": round(acc_cost, 2),
        "base": BASE,
        "premium_pct": round(premium * 100, 1),
        "total": round(total, 2),
        "boards": boards,
        "frame_len_m": round(frame_len, 2),
    }
