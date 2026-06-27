"""Konfigüratör veri katmanı: yönetici fiyatları, renkler ve gardırop fiyatı."""
import db


def get_prices():
    """{key: value} fiyat sözlüğü."""
    return {r["key"]: r["value"] for r in db.query("SELECT key, value FROM cfg_prices")}


def get_prices_full():
    return db.query("SELECT * FROM cfg_prices ORDER BY seq, key")


def get_colors(active_only=True):
    sql = "SELECT * FROM cfg_colors"
    if active_only:
        sql += " WHERE active=1"
    sql += " ORDER BY seq, id"
    return db.query(sql)


def _color(colors, cid, default_premium=0.0):
    for c in colors:
        if str(c["id"]) == str(cid):
            return c
    return {"premium_pct": default_premium, "hex": "#d8b88a", "name": ""}


def price_gardirop(p):
    """
    Gardırop fiyatı (dökümlü). p: ölçü + opsiyonlar.
      W,H,D (mm), doors, door_type (kapaksiz|duz|camli|desenli|surgu),
      base_type (bazali|ayakli|yok), back (0/1), shelves, drawers,
      hanging (0/1), handles (0/1), two_tone (0/1),
      body_color, door_color, door_color2 (renk id)
    """
    P = get_prices()
    colors = db.query("SELECT * FROM cfg_colors")

    def num(k, d=0):
        try:
            return float(p.get(k, d))
        except (TypeError, ValueError):
            return d

    W = num("W"); H = num("H"); D = num("D")
    doors = int(num("doors", 0))
    door_type = p.get("door_type", "duz")
    base_type = p.get("base_type", "bazali")
    back = int(num("back", 1))
    shelves = int(num("shelves", 0))
    drawers = int(num("drawers", 0))
    hanging = int(num("hanging", 0))
    handles = int(num("handles", 1))
    two_tone = int(num("two_tone", 0))

    m2 = lambda a: a / 1_000_000.0

    # Gövde: 2 yan + üst + alt + orta bölme
    carcass_m2 = m2(2 * H * D + 2 * W * D + H * D)
    body_cost = carcass_m2 * P.get("body_per_m2", 0)
    back_cost = m2(W * H) * P.get("back_per_m2", 0) if back else 0

    # Kapak
    door_area = m2(W * H)
    door_rate = {
        "duz": P.get("door_flat_per_m2", 0),
        "camli": P.get("door_glass_per_m2", 0),
        "desenli": P.get("door_pattern_per_m2", 0),
        "surgu": P.get("door_flat_per_m2", 0) + P.get("door_sliding_extra_per_m2", 0),
    }.get(door_type, 0)
    door_cost = 0 if door_type == "kapaksiz" else door_area * door_rate

    shelves_cost = shelves * P.get("shelf_each", 0)
    drawers_cost = drawers * P.get("drawer_each", 0)
    base_cost = {"bazali": P.get("base_plinth", 0), "ayakli": P.get("legs_set", 0)}.get(base_type, 0)
    hanging_cost = P.get("hanging_rail", 0) if hanging else 0
    handles_cost = (doors * P.get("handle_each", 0)) if (handles and door_type != "kapaksiz") else 0

    # Renk ek oranları
    body_prem = _color(colors, p.get("body_color"))["premium_pct"] / 100.0
    door_prem = _color(colors, p.get("door_color"))["premium_pct"] / 100.0
    body_cost *= (1 + body_prem)
    back_cost *= (1 + body_prem)
    door_cost *= (1 + door_prem)
    if two_tone and door_type != "kapaksiz":
        door_cost *= (1 + P.get("two_tone_extra_pct", 0) / 100.0)

    total = (P.get("assembly_base", 0) + body_cost + back_cost + door_cost +
             shelves_cost + drawers_cost + base_cost + hanging_cost + handles_cost)

    return {
        "items": [
            {"label": "Gövde", "value": round(body_cost)},
            {"label": "Arkalık", "value": round(back_cost)},
            {"label": "Kapak", "value": round(door_cost)},
            {"label": "Raf", "value": round(shelves_cost)},
            {"label": "Çekmece", "value": round(drawers_cost)},
            {"label": "Baza/Ayak", "value": round(base_cost)},
            {"label": "Askı borusu", "value": round(hanging_cost)},
            {"label": "Kulp", "value": round(handles_cost)},
            {"label": "Taban/montaj", "value": round(P.get("assembly_base", 0))},
        ],
        "total": round(total, 2),
    }
