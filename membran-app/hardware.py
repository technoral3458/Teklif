"""
Donanım & delik motoru (Aşama C).

Patlatılmış panellere üretim deliklerini koordinatlarıyla yerleştirir:
- Gövde birleştirme: minifix (Ø15 kovan, yan yüze) + dübel (Ø8)
- Raf pimi: System32 mantığı, Ø5
- Menteşe: kapakta Ø35 yuva
- Arkalık kanalı: Type 3 freze (kanallı arkalıkta yan/taban panelde)

Panel düzlemi: x = 0..L (boy), y = 0..W (en). mm.
op: {kind:'v'|'h'|'groove', x, y, d, depth, face, purpose, [x2,y2]}
  v     = dikey delik (panel yüzüne)  -> DWD Type 2
  h     = yatay delik (panel kenarına) -> DWD Type 1 ; face: sol/sag/on/arka
  groove= kanal/freze                  -> DWD Type 3
"""

# Donanım standartları (mm) — yönetici sonra ayarlar
HW = {
    "connector_inset": 37,   # birleştirme bağlantısı kenardan ofset
    "cam_d": 15, "cam_depth": 13,
    "dowel_d": 8, "dowel_depth": 13,
    "pin_d": 5, "pin_depth": 10,
    "shelf_pin_inset": 37,
    "hinge_d": 35, "hinge_depth": 12, "hinge_edge": 22, "hinge_end": 100,
    "back_groove_d": 4, "back_groove_depth": 8,
}


def _v(x, y, d, depth, purpose, face="ic"):
    return {"kind": "v", "x": round(x), "y": round(y), "d": d, "depth": depth,
            "face": face, "purpose": purpose}


def _h(x, y, d, depth, purpose, face):
    return {"kind": "h", "x": round(x), "y": round(y), "d": d, "depth": depth,
            "face": face, "purpose": purpose}


def _groove(x, y, x2, y2, d, depth, purpose):
    return {"kind": "groove", "x": round(x), "y": round(y), "x2": round(x2), "y2": round(y2),
            "d": d, "depth": depth, "face": "arka", "purpose": purpose}


def drill_panels(spec, result):
    """result = cabinet.explode(...); panellere ops ekler, listesini döner."""
    s = result["spec"]
    t = float(s["t"]); D = float(s["D"]); W = float(s["W"])
    ch = result["summary"]["carcass_h"]
    ci = HW["connector_inset"]
    shelves = int(s.get("shelves", 0))
    back_groove = s["back_method"] == "groove"
    back_axis = D - float(s["back_inset"])     # arkalık kanal ekseni (derinlikte)

    out = []
    for p in result["panels"]:
        ops = []
        name, L, Wp = p["name"], p["L"], p["W"]

        if p["kind"] == "side":
            # Gövde birleştirme: alt (x=t/2) ve üst (x=ch-t/2) eklem hatları
            for jx in (t / 2, ch - t / 2):
                ops.append(_v(jx, D / 2, HW["cam_d"], HW["cam_depth"], "Minifix kovan"))
                ops.append(_v(jx, ci, HW["dowel_d"], HW["dowel_depth"], "Dübel"))
                ops.append(_v(jx, D - ci, HW["dowel_d"], HW["dowel_depth"], "Dübel"))
            # Raf pimleri (her raf için ön+arka)
            for i in range(shelves):
                sy = (ch) * (i + 1) / (shelves + 1)
                ops.append(_v(sy, HW["shelf_pin_inset"], HW["pin_d"], HW["pin_depth"], "Raf pimi"))
                ops.append(_v(sy, back_axis - HW["shelf_pin_inset"], HW["pin_d"], HW["pin_depth"], "Raf pimi"))
            # Arkalık kanalı (yan panelde, derinlik ekseninde, boyunca)
            if back_groove:
                ops.append(_groove(0, back_axis, L, back_axis, HW["back_groove_d"], HW["back_groove_depth"], "Arkalık kanalı"))

        elif p["kind"] in ("bottom", "top"):
            # İki uç (sol/sağ) yan panellere bağlanır: kenara yatay delikler
            for edge, ex in (("sol", 0), ("sag", L)):
                ops.append(_h(ex, D / 2, HW["dowel_d"], 34, "Minifix gövde", edge))
                ops.append(_h(ex, ci, HW["dowel_d"], 24, "Dübel", edge))
                ops.append(_h(ex, D - ci, HW["dowel_d"], 24, "Dübel", edge))
            if back_groove and p["kind"] == "bottom":
                ops.append(_groove(0, back_axis, L, back_axis, HW["back_groove_d"], HW["back_groove_depth"], "Arkalık kanalı"))

        elif p["kind"] == "door":
            # Menteşe yuvaları (bir uzun kenar boyunca)
            ys = [HW["hinge_end"], L - HW["hinge_end"]]
            if L > 1200:
                ys.insert(1, L / 2)
            for hy in ys:
                ops.append(_v(hy, HW["hinge_edge"], HW["hinge_d"], HW["hinge_depth"], "Menteşe yuvası"))

        # raf / arkalık / çekmece: ayrı delik gerektirmez (pime oturur / kanala girer)
        out.append({**p, "ops": ops, "op_count": len(ops)})

    # toplamlar
    totals = {}
    for p in out:
        for op in p["ops"]:
            key = op["purpose"]
            totals[key] = totals.get(key, 0) + p["qty"]
    return {"panels": out, "totals": totals}
