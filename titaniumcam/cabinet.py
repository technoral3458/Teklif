"""
Yapısal kabinet modeli (CAD/CAM üretim hattı - Aşama A & B).

Dış ölçü (G×Y×D) + yapım kurallarından her panelin gerçek ölçüsünü türetir
('patlat'). Çıktı: üretilebilir parça listesi (ölçü, malzeme, kenar bandı,
damar, adet). Bu liste sonraki aşamalarda donanım/delik, nesting ve CNC/DWD
için temel olur.

Koordinat/ölçü birimi: mm. 'L' = boy (genelde damar yönü), 'W' = en.
edges: {'on','arka','sol','sag'} -> o kenara bant var/yok.
"""
from dataclasses import dataclass, field, asdict
from typing import Dict, List


# Varsayılan yapım standardı (yönetici değiştirir)
DEFAULTS = {
    "t": 18.0,              # gövde panel kalınlığı
    "door_t": 18.0,         # kapak kalınlığı
    "back_method": "groove",  # groove (kanallı) | nailed (çakma)
    "back_t": 8.0,          # arkalık kalınlığı
    "groove_depth": 9.0,    # kanal derinliği
    "back_inset": 15.0,     # arkalık geri çekme (kanal ekseni)
    "gap": 3.0,             # kapak fugası / pay
    "shelf_setback": 20.0,  # raf ön/arka geri çekme
    "base_type": "plinth",  # plinth (baza) | legs (ayak) | none
    "base_h": 100.0,        # baza/ayak yüksekliği
    "door_type": "overlay", # overlay (taşmalı) | inset (içten)
    "material": "MDFLAM 18mm",
}

NO_EDGES = {"on": False, "arka": False, "sol": False, "sag": False}


@dataclass
class Panel:
    name: str
    qty: int
    L: float                 # boy (mm)
    W: float                 # en (mm)
    t: float                 # kalınlık (mm)
    material: str = "MDFLAM 18mm"
    grain: bool = True       # damar boy yönünde mi
    kind: str = "panel"      # side|bottom|top|shelf|back|door|drawer
    edges: Dict[str, bool] = field(default_factory=lambda: dict(NO_EDGES))
    note: str = ""

    @property
    def area_m2(self) -> float:
        return (self.L / 1000.0) * (self.W / 1000.0)

    @property
    def edge_count(self) -> int:
        return sum(1 for v in self.edges.values() if v)

    def edge_label(self) -> str:
        names = {"on": "Ön", "arka": "Arka", "sol": "Sol", "sag": "Sağ"}
        on = [names[k] for k, v in self.edges.items() if v]
        if not on:
            return "—"
        if len(on) == 4:
            return "4 kenar"
        return ", ".join(on)


def _edges(on=False, arka=False, sol=False, sag=False):
    return {"on": on, "arka": arka, "sol": sol, "sag": sag}


def explode(spec: dict) -> dict:
    """Kabinet tarifini parça listesine ('patlat') çevirir."""
    s = dict(DEFAULTS)
    s.update({k: v for k, v in spec.items() if v is not None})

    W = float(s["W"]); H = float(s["H"]); D = float(s["D"])
    t = float(s["t"]); mat = s["material"]
    doors = int(s.get("doors", 2)); shelves = int(s.get("shelves", 1))
    drawers = int(s.get("drawers", 0))
    gap = float(s["gap"]); gd = float(s["groove_depth"])
    base_type = s["base_type"]; base_h = float(s["base_h"]) if base_type != "none" else 0.0

    ch = H - base_h            # gövde (carcass) yüksekliği
    panels: List[Panel] = []

    # 1) Yanlar
    panels.append(Panel("Yan", 2, round(ch), round(D), t, mat, True, "side",
                        _edges(on=True), "Ön kenar bantlı"))
    # 2) Taban
    panels.append(Panel("Taban", 1, round(W - 2 * t), round(D), t, mat, False, "bottom",
                        _edges(on=True)))
    # 3) Üst kayıt (ön + arka) — tezgah bağlantısı için
    panels.append(Panel("Üst kayıt", 2, round(W - 2 * t), 100, t, mat, False, "top",
                        _edges(), "Ön/arka kayıt"))
    # 4) Raflar
    if shelves > 0:
        panels.append(Panel("Raf", shelves, round(W - 2 * t - 1),
                            round(D - float(s["back_inset"]) - float(s["shelf_setback"])),
                            t, mat, False, "shelf", _edges(on=True)))
    # 5) Arkalık
    back_mat = f"Arkalık {int(float(s['back_t']))}mm"
    if s["back_method"] == "groove":
        panels.append(Panel("Arkalık", 1, round(ch - 2 * t + 2 * gd), round(W - 2 * t + 2 * gd),
                            float(s["back_t"]), back_mat, False, "back", _edges(), "Kanala oturur"))
    else:
        panels.append(Panel("Arkalık", 1, round(ch), round(W), float(s["back_t"]),
                            back_mat, False, "back", _edges(), "Sırttan çakma"))

    # 6) Kapaklar
    if doors > 0:
        if s["door_type"] == "overlay":
            dL = ch - 2 * gap
            dW = (W - (doors + 1) * gap) / doors
        else:  # inset
            dL = ch - 2 * t - 2 * gap
            dW = (W - 2 * t - (doors + 1) * gap) / doors
        panels.append(Panel("Kapak", doors, round(dL), round(dW), float(s["door_t"]),
                            mat, True, "door", _edges(True, True, True, True), "4 kenar bantlı"))

    # 7) Çekmece önleri (varsa, alt bölmede)
    if drawers > 0:
        dh = (ch - (drawers + 1) * gap) / drawers
        panels.append(Panel("Çekmece ön", drawers, round(dh), round(W - 2 * gap),
                            float(s["door_t"]), mat, True, "drawer",
                            _edges(True, True, True, True), "4 kenar bantlı"))

    total_area = sum(p.area_m2 * p.qty for p in panels)
    total_pieces = sum(p.qty for p in panels)
    total_edge_m = sum(
        ((p.L if p.edges["on"] else 0) + (p.L if p.edges["arka"] else 0)
         + (p.W if p.edges["sol"] else 0) + (p.W if p.edges["sag"] else 0)) / 1000.0 * p.qty
        for p in panels)

    return {
        "spec": s,
        "panels": [asdict(p) | {"area_m2": round(p.area_m2, 3),
                                "edge_label": p.edge_label(), "edge_count": p.edge_count}
                   for p in panels],
        "summary": {
            "pieces": total_pieces,
            "area_m2": round(total_area, 3),
            "edge_m": round(total_edge_m, 2),
            "carcass_h": round(ch),
        },
    }
