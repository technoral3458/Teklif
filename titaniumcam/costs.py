"""Maliyet hesaplama yardımcıları (Modül 1)."""


def material_cost_per_m2(mat, rates):
    """Bir malzemenin m² başına TL maliyeti."""
    currency = mat.get("currency", "TRY")
    if currency == "TRY":
        rate = 1.0
    else:
        rate = rates.get(currency, 1.0)
    price_try = float(mat.get("price", 0)) * rate
    unit = mat.get("unit", "m2")

    if unit == "plaka":
        area_m2 = (float(mat.get("sheet_width", 0)) / 100.0) * \
                  (float(mat.get("sheet_height", 0)) / 100.0)
        return price_try / area_m2 if area_m2 > 0 else 0.0
    elif unit == "m2":
        return price_try
    else:  # kg, lt, adet
        return price_try * float(mat.get("usage_per_m2", 1) or 1)


def total_cost_per_m2(materials, rates):
    return sum(material_cost_per_m2(m, rates) for m in materials)


def door_cost(door, cost_per_m2):
    area_m2 = (float(door["width_mm"]) / 1000.0) * (float(door["height_mm"]) / 1000.0)
    unit_cost = cost_per_m2 * area_m2
    total = unit_cost * int(door.get("quantity", 1))
    return {"area_m2": area_m2, "unit_cost": unit_cost, "total_cost": total}
