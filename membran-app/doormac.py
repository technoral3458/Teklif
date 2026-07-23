"""
Parametrik kapak modeli motoru — AlphaCAM/AlphaDOOR makro uyumlu.

.adoormac makrosunu okur (TURN'lü iç geometri + $VAR değişkenler + formüller),
değişkenleri (Sol_Kenar, Yay_Yuksekligi, hesaplanan Yarı_Cap ...) verilen
panel ölçüsüne (width/length) göre çözer, iç profili (çizgi + yay segmentleri)
üretir ve CNC 'MC kodu' (G-code / NC) yazar.

Not: $VAR değeri '=' ile başlıyorsa formüldür (örn. Yarı_Cap). Diğerleri
kullanıcı-düzenlenebilir sayısal varsayılanlardır.
"""
import math
import re

# --- Örnek: V105 (kullanıcının AlphaCAM makrosundan) ---
V105_MACRO = """$VERSION 1.10
$GEO 1
2
$TURN 1
1
Sol_Kenar
Alt_Kenar + Yay_Yuksekligi



0
0
0
$TURN 2
2
( width - ( Sol_Kenar + Sag_Kenar ) ) / 2 + Sol_Kenar
Alt_Kenar + Yarı_Cap
Yarı_Cap



0
0
0
$TURN 3
1
width - Sag_Kenar
Alt_Kenar + Yay_Yuksekligi



0
0
0
$TURN 4
1
width - Sag_Kenar
length - Üst_Kenar - Yay_Yuksekligi



0
0
0
$TURN 5
2
( width - ( Sol_Kenar + Sag_Kenar ) ) / 2 + Sol_Kenar
length - Üst_Kenar - Yarı_Cap
Yarı_Cap



0
0
0
$TURN 6
1
Sol_Kenar
length - Üst_Kenar - Yay_Yuksekligi



0
0
0
$ENDGEO
$VAR
Sol_Kenar
60.000000
$VAR
Sag_Kenar
60.000000
$VAR
Alt_Kenar
60.000000
$VAR
Üst_Kenar
60.000000
$VAR
Yay_Yuksekligi
35.000000
$VAR
Yarı_Cap
=( ( width - ( Sol_Kenar + Sag_Kenar ) ) ^2 + ( 4 * Yay_Yuksekligi ^2 ) ) / ( 8 * Yay_Yuksekligi )
$VAR
Ara
60.000000
$VAR
Yuz
104.000000
$LENGTH
750.000000
$WIDTH
500.000000
$CORNERRAD
2.000000
"""


def parse_adoormac(text: str) -> dict:
    lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    n = len(lines)
    i = 0
    turns, vars_ = [], []
    length = width = cornerrad = None
    while i < n:
        s = lines[i].strip()
        if s.startswith("$TURN"):
            i += 1
            block = []
            while i < n and not lines[i].strip().startswith("$"):
                block.append(lines[i])
                i += 1
            def g(k):
                return block[k].strip() if k < len(block) else ""
            tt = g(0)
            turns.append({"type": int(tt) if tt.isdigit() else 1,
                          "x": g(1), "y": g(2), "r": g(3)})
            continue
        if s == "$VAR":
            name = lines[i + 1].strip()
            val = lines[i + 2].strip()
            if val.startswith("="):
                vars_.append({"name": name, "formula": val[1:].strip(), "value": None})
            else:
                vars_.append({"name": name, "formula": None, "value": float(val)})
            i += 3
            continue
        if s == "$LENGTH":
            length = float(lines[i + 1]); i += 2; continue
        if s == "$WIDTH":
            width = float(lines[i + 1]); i += 2; continue
        if s == "$CORNERRAD":
            cornerrad = float(lines[i + 1]); i += 2; continue
        i += 1
    return {"turns": turns, "vars": vars_, "length": length, "width": width,
            "cornerrad": cornerrad}


def _aliases(model):
    names = [v["name"] for v in model["vars"]] + ["width", "length"]
    uniq = sorted(set(names), key=len, reverse=True)
    return {nm: f"__v{i}" for i, nm in enumerate(uniq)}


def _prep(expr, alias):
    e = expr.replace("^", "**")
    for nm in sorted(alias, key=len, reverse=True):
        e = re.sub(r"(?<!\w)" + re.escape(nm) + r"(?!\w)", alias[nm], e)
    return e


def _safe_eval(expr, ns):
    return eval(expr, {"__builtins__": {}}, ns)  # noqa: S307 (güvenilir makro)


def evaluate(model: dict, width: float, length: float, overrides: dict = None) -> dict:
    overrides = overrides or {}
    alias = _aliases(model)
    ns = {alias["width"]: float(width), alias["length"]: float(length)}
    for v in model["vars"]:
        if v["formula"] is None:
            raw = overrides.get(v["name"], v["value"])
            ns[alias[v["name"]]] = float(raw)
    for _ in range(6):  # formülleri bağımlılık sırasında çöz
        for v in model["vars"]:
            a = alias[v["name"]]
            if v["formula"] is not None and a not in ns:
                try:
                    ns[a] = _safe_eval(_prep(v["formula"], alias), ns)
                except Exception:
                    pass
    vals = {v["name"]: ns.get(alias[v["name"]]) for v in model["vars"]}
    pts = []
    for t in model["turns"]:
        x = _safe_eval(_prep(t["x"], alias), ns)
        y = _safe_eval(_prep(t["y"], alias), ns)
        r = _safe_eval(_prep(t["r"], alias), ns) if t["r"] else None
        pts.append({"type": t["type"], "x": x, "y": y, "r": r})
    return {"vals": vals, "pts": pts, "width": float(width), "length": float(length)}


def segments(pts: list) -> list:
    segs = []
    first = last = pending = None
    for p in pts:
        if p["type"] == 2:
            pending = p
            continue
        v = (p["x"], p["y"])
        if last is None:
            first = v
        else:
            if pending:
                segs.append({"kind": "arc", "a": last, "b": v,
                             "c": (pending["x"], pending["y"]), "r": pending["r"]})
            else:
                segs.append({"kind": "line", "a": last, "b": v})
            pending = None
        last = v
    if last and first:
        if pending:
            segs.append({"kind": "arc", "a": last, "b": first,
                         "c": (pending["x"], pending["y"]), "r": pending["r"]})
        else:
            segs.append({"kind": "line", "a": last, "b": first})
    return segs


def _sample_arc(a, b, c, r, steps=28):
    (ax, ay), (bx, by), (cx, cy) = a, b, c
    a0 = math.atan2(ay - cy, ax - cx)
    a1 = math.atan2(by - cy, bx - cx)
    cross = (ax - cx) * (by - cy) - (ay - cy) * (bx - cx)
    if cross > 0 and a1 < a0:
        a1 += 2 * math.pi
    if cross < 0 and a1 > a0:
        a1 -= 2 * math.pi
    return [(cx + r * math.cos(a0 + (a1 - a0) * k / steps),
             cy + r * math.sin(a0 + (a1 - a0) * k / steps)) for k in range(steps + 1)]


def profile_points(segs: list) -> list:
    pts = []
    for s in segs:
        if s["kind"] == "line":
            if not pts:
                pts.append(s["a"])
            pts.append(s["b"])
        else:
            arc = _sample_arc(s["a"], s["b"], s["c"], s["r"])
            pts.extend(arc if not pts else arc[1:])
    return pts


def gcode(name: str, ev: dict, depth=8.0, feed=3000, plunge=1200, safe=6.0) -> str:
    segs = segments(ev["pts"])
    if not segs:
        return "(bos geometri)"
    start = segs[0]["a"]
    L = ["%", f"({name}  {ev['width']:.0f}x{ev['length']:.0f} mm  derinlik={depth}mm)",
         "G21 G90 G17", f"G0 Z{safe:.1f}",
         f"G0 X{start[0]:.2f} Y{start[1]:.2f}", f"G1 Z{-depth:.2f} F{plunge}"]
    for s in segs:
        bx, by = s["b"]
        if s["kind"] == "line":
            L.append(f"G1 X{bx:.2f} Y{by:.2f} F{feed}")
        else:
            ax, ay = s["a"]; cx, cy = s["c"]
            cross = (ax - cx) * (by - cy) - (ay - cy) * (bx - cx)
            gg = "G3" if cross > 0 else "G2"
            L.append(f"{gg} X{bx:.2f} Y{by:.2f} I{cx - ax:.2f} J{cy - ay:.2f} F{feed}")
    L += [f"G0 Z{safe:.1f}", "M30", "%"]
    return "\n".join(L)


def default_models():
    return [("V-105", parse_adoormac(V105_MACRO))]
