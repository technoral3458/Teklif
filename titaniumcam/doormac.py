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
    """Turns'ten (içe aktarılan .adoormac) tek kapalı profil segmentleri."""
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


def path_points(segs: list) -> list:
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


def _paths_v105(ev: dict) -> list:
    """AdoorMain (VBA) birebir portu: düz alt + kemerli üst profil, iki kayıt
    (kenar çıtası), ve Ara'ya göre kaset bölme çizgileri (mullion)."""
    v = ev["vals"]; W = ev["width"]; L = ev["length"]
    Sol = v["Sol_Kenar"]; Sag = v["Sag_Kenar"]; Alt = v["Alt_Kenar"]; Ust = v["Üst_Kenar"]
    Yay = v["Yay_Yuksekligi"]; Ara = v["Ara"]; yuz = v["Yuz"]; R = v["Yarı_Cap"]
    midX = (W - (Sol + Sag)) / 2 + Sol
    cY = L - Ust - R                      # üst yay merkezinin y'si
    P1 = (Sol, Alt); P2 = (W - Sag, Alt)  # düz alt kenar
    P3 = (W - Sag, L - Ust - Yay); P4 = (Sol, L - Ust - Yay)
    profile = [
        {"kind": "line", "a": P1, "b": P2},
        {"kind": "line", "a": P2, "b": P3},
        {"kind": "arc", "a": P3, "b": P4, "c": (midX, cY), "r": R},  # üst kemer (CCW)
        {"kind": "line", "a": P4, "b": P1},
    ]
    paths = [{"closed": True, "segs": profile, "role": "profil"}]
    # Geo2 / Geo3: kenar kayıtları (tam boy dikey)
    paths.append({"closed": False, "role": "kayit",
                  "segs": [{"kind": "line", "a": (yuz, 0), "b": (yuz, L)}]})
    paths.append({"closed": False, "role": "kayit",
                  "segs": [{"kind": "line", "a": (W - yuz, 0), "b": (W - yuz, L)}]})
    # Kaset bölme çizgileri (stripes) — profile göre üstten kırpılı
    inner = W - 2 * yuz
    n_fp = int(inner / Ara) + 1 if Ara > 0 else 1
    w_fp = inner / n_fp if n_fp else inner
    for n in range(1, n_fp):
        xm = yuz + w_fp * n
        d = R * R - (xm - midX) ** 2
        ytop = cY + math.sqrt(d) if d > 0 else (L - Ust - Yay)
        paths.append({"closed": False, "role": "kaset",
                      "segs": [{"kind": "line", "a": (xm, Alt), "b": (xm, ytop)}]})
    return paths


# ------- parametrik model yardımcıları (fotoğraflardaki desenler) -------
def _rect(x0, y0, x1, y1):
    return [{"kind": "line", "a": (x0, y0), "b": (x1, y0)},
            {"kind": "line", "a": (x1, y0), "b": (x1, y1)},
            {"kind": "line", "a": (x1, y1), "b": (x0, y1)},
            {"kind": "line", "a": (x0, y1), "b": (x0, y0)}]


def _frame(x0, y0, x1, y1, role="cerceve"):
    return {"closed": True, "role": role, "segs": _rect(x0, y0, x1, y1)}


def _clip_line(slope, b, x0, y0, x1, y1):
    """y = slope*x + b doğrusunu [x0,x1]x[y0,y1] dikdörtgenine kırpar."""
    c = []
    for x in (x0, x1):
        y = slope * x + b
        if y0 - 1e-6 <= y <= y1 + 1e-6:
            c.append((x, y))
    for y in (y0, y1):
        x = (y - b) / slope
        if x0 - 1e-6 <= x <= x1 + 1e-6:
            c.append((x, y))
    c = list({(round(p[0], 3), round(p[1], 3)) for p in c})
    if len(c) < 2:
        return None
    best = max(((a, d) for i, a in enumerate(c) for d in c[i + 1:]),
              key=lambda pq: (pq[0][0] - pq[1][0]) ** 2 + (pq[0][1] - pq[1][1]) ** 2)
    return best


def _paths_raised1(ev):
    """Düz kabartma kaset — iç içe çerçeveler (fotoğraf 3, 8)."""
    W = ev["width"]; L = ev["length"]; v = ev["vals"]
    k = v["kenar"]; ara = v["ara"]; n = max(int(v["cerceve"]), 1)
    return [_frame(k + i * ara, k + i * ara, W - k - i * ara, L - k - i * ara) for i in range(n)]


def _paths_raised2(ev):
    """İki kaset — üstte büyük, altta küçük panel (fotoğraf 5)."""
    W = ev["width"]; L = ev["length"]; v = ev["vals"]
    k = v["kenar"]; u = v["ust"]; o = v["orta"]; n = max(int(v["cerceve"]), 1); ara = v["ara"]
    inner = L - 2 * u - o
    bot = inner * 0.4; top = inner - bot
    panels = [(k, u, W - k, u + bot), (k, u + bot + o, W - k, L - u)]
    out = []
    for (x0, y0, x1, y1) in panels:
        for i in range(n):
            d = i * ara
            out.append(_frame(x0 + d, y0 + d, x1 - d, y1 - d))
    return out


def _paths_hourglass(ev):
    """Kum saati — dış çerçeve + içeri kavisli (konkav) kenarlı panel (fotoğraf 4)."""
    W = ev["width"]; L = ev["length"]; v = ev["vals"]
    k = v["kenar"]; bel = max(v["bel"], 10); pad = v["panel"]
    out = [_frame(k, k, W - k, L - k)]
    xL = k + pad; xR = W - k - pad; yT = L - k - pad; yB = k + pad
    mid = (yT + yB) / 2
    chord = yT - yB
    R = (chord ** 2 + 4 * bel ** 2) / (8 * bel)
    cLx = xL + bel - R      # sol kenar merkezi (sola), içeri (+x) kavis
    cRx = xR - bel + R      # sağ kenar merkezi (sağa), içeri (-x) kavis
    segs = [
        {"kind": "line", "a": (xL, yT), "b": (xR, yT)},
        {"kind": "arc", "a": (xR, yT), "b": (xR, yB), "c": (cRx, mid), "r": R},
        {"kind": "line", "a": (xR, yB), "b": (xL, yB)},
        {"kind": "arc", "a": (xL, yB), "b": (xL, yT), "c": (cLx, mid), "r": R},
    ]
    out.append({"closed": True, "role": "desen", "segs": segs})
    return out


def _paths_diamond(ev):
    """Baklava petek — çerçeve + 45° çapraz ızgara (fotoğraf 7)."""
    W = ev["width"]; L = ev["length"]; v = ev["vals"]
    k = v["kenar"]; adim = max(v["adim"], 20)
    out = [_frame(k, k, W - k, L - k)]
    m = k + 40
    x0, y0, x1, y1 = m, m, W - m, L - m
    out.append(_frame(x0, y0, x1, y1))
    b = math.floor((y0 - x1) / adim) * adim
    while b <= y1 - x0:
        seg = _clip_line(1, b, x0, y0, x1, y1)
        if seg:
            out.append({"closed": False, "role": "desen",
                        "segs": [{"kind": "line", "a": seg[0], "b": seg[1]}]})
        b += adim
    b = math.floor((y0 + x0) / adim) * adim
    while b <= y1 + x1:
        seg = _clip_line(-1, b, x0, y0, x1, y1)
        if seg:
            out.append({"closed": False, "role": "desen",
                        "segs": [{"kind": "line", "a": seg[0], "b": seg[1]}]})
        b += adim
    return out


def _paths_modern(ev):
    """Modern çizgi — üstte dikey oluklar + altta kademeli çerçeve (fotoğraf 1)."""
    W = ev["width"]; L = ev["length"]; v = ev["vals"]
    sol = v["sol"]; ara = v["ara"]; n = max(int(v["cizgi"]), 1); bel = v["bel"]
    out = []
    for i in range(n):
        x = sol + i * ara
        ytop = L - v["ust"] - i * v["kademe"]
        out.append({"closed": False, "role": "desen",
                    "segs": [{"kind": "line", "a": (x, bel), "b": (x, ytop)}]})
    # alt kademeli yatay çizgiler
    for j, yy in enumerate((bel, bel - v["kademe"])):
        out.append({"closed": False, "role": "desen",
                    "segs": [{"kind": "line", "a": (sol, yy), "b": (W - v["sag"] - j * 90, yy)}]})
    return out


PROGRAMS = {
    "v105": _paths_v105, "raised1": _paths_raised1, "raised2": _paths_raised2,
    "hourglass": _paths_hourglass, "diamond": _paths_diamond, "modern": _paths_modern,
}


def build_paths(model: dict, ev: dict) -> list:
    fn = PROGRAMS.get(model.get("program"))
    if fn:
        return fn(ev)
    return [{"closed": True, "role": "profil", "segs": segments(ev["pts"])}]


def dxf(name: str, ev: dict, paths: list) -> str:
    """Kapak geometrisini DXF'e (LINE + ARC) çevirir — AutoCAD/CNC uyumlu."""
    out = ["0", "SECTION", "2", "HEADER", "9", "$INSUNITS", "70", "4", "0", "ENDSEC",
           "0", "SECTION", "2", "ENTITIES"]
    for p in paths:
        for s in p["segs"]:
            ax, ay = s["a"]; bx, by = s["b"]
            if s["kind"] == "line":
                out += ["0", "LINE", "8", "KAPAK",
                        "10", f"{ax:.3f}", "20", f"{ay:.3f}", "30", "0",
                        "11", f"{bx:.3f}", "21", f"{by:.3f}", "31", "0"]
            else:
                cx, cy = s["c"]; r = s["r"]
                sa = math.degrees(math.atan2(ay - cy, ax - cx))
                ea = math.degrees(math.atan2(by - cy, bx - cx))
                if (ax - cx) * (by - cy) - (ay - cy) * (bx - cx) < 0:  # DXF yayı CCW
                    sa, ea = ea, sa
                out += ["0", "ARC", "8", "KAPAK",
                        "10", f"{cx:.3f}", "20", f"{cy:.3f}", "30", "0",
                        "40", f"{r:.3f}", "50", f"{sa:.3f}", "51", f"{ea:.3f}"]
    out += ["0", "ENDSEC", "0", "EOF"]
    return "\n".join(out)


def gcode(name: str, ev: dict, paths: list, depth=8.0, feed=3000, plunge=1200, safe=6.0) -> str:
    out = ["%", f"({name}  {ev['width']:.0f}x{ev['length']:.0f} mm  derinlik={depth}mm)",
           "G21 G90 G17"]
    for path in paths:
        segs = path["segs"]
        if not segs:
            continue
        sx, sy = segs[0]["a"]
        out += [f"G0 Z{safe:.1f}", f"G0 X{sx:.2f} Y{sy:.2f}", f"G1 Z{-depth:.2f} F{plunge}"]
        for s in segs:
            bx, by = s["b"]
            if s["kind"] == "line":
                out.append(f"G1 X{bx:.2f} Y{by:.2f} F{feed}")
            else:
                ax, ay = s["a"]; cx, cy = s["c"]
                cross = (ax - cx) * (by - cy) - (ay - cy) * (bx - cx)
                gg = "G3" if cross > 0 else "G2"
                out.append(f"{gg} X{bx:.2f} Y{by:.2f} I{cx - ax:.2f} J{cy - ay:.2f} F{feed}")
    out += [f"G0 Z{safe:.1f}", "M30", "%"]
    return "\n".join(out)


def _mk(program, W, L, **vars):
    return {"program": program, "width": float(W), "length": float(L), "turns": [],
            "vars": [{"name": k, "formula": None, "value": float(val)} for k, val in vars.items()]}


def default_models():
    m = parse_adoormac(V105_MACRO)
    m["program"] = "v105"   # tam VBA geometrisi (profil + kayıt + kaset)
    return [
        ("V-105 (Kemerli)", m),
        ("Düz Kaset", _mk("raised1", 820, 2010, kenar=70, ara=14, cerceve=3)),
        ("İki Kaset", _mk("raised2", 820, 2010, kenar=90, ust=120, orta=120, cerceve=2, ara=16)),
        ("Kum Saati", _mk("hourglass", 820, 2010, kenar=70, panel=45, bel=130)),
        ("Baklava Petek", _mk("diamond", 820, 2010, kenar=90, adim=120)),
        ("Modern Çizgi", _mk("modern", 820, 2010, sol=140, ara=55, cizgi=3, bel=760,
                             ust=120, kademe=70, sag=120)),
    ]
