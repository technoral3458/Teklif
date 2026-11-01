"""
Parametrik ifade değerlendirici.

Koordinatlar (x, y, depth, r) matematiksel ifade olabilir:
  LPX / W = genişlik, LPY / H = yükseklik, LPZ / T = kalınlık,
  ve modelde tanımlı sabitler (R, OFFSET ...).
Örnek: "LPX/2", "LPY-10", "sqrt(R*2)", "-T", "LPX*0.85"

Not: Spec'teki naive string-replace yöntemi 'T' harfini SQRT içinde de
değiştirip ifadeyi bozardı. Bunun yerine değişkenleri eval namespace'ine
koyup ifadeyi doğrudan değerlendiriyoruz (güvenli, __builtins__ kapalı).
"""
import math

# İfade büyük harfe çevrildiği için fonksiyon adlarını da büyük harf veriyoruz.
SAFE_NAMES = {
    "SIN": math.sin, "COS": math.cos, "TAN": math.tan,
    "SQRT": math.sqrt, "ABS": abs, "PI": math.pi,
    "ROUND": round, "MIN": min, "MAX": max,
    # küçük harf kullanımına da izin ver
    "sin": math.sin, "cos": math.cos, "tan": math.tan,
    "sqrt": math.sqrt, "abs": abs, "pi": math.pi,
    "round": round, "min": min, "max": max,
}

ALIASES = {"W": "LPX", "H": "LPY", "T": "LPZ"}


def eval_expr(expr, variables=None):
    """İfadeyi sayıya çevirir. Hata olursa 0.0 döner."""
    variables = variables or {}
    if expr is None:
        return 0.0
    expr = str(expr).strip()
    if expr == "":
        return 0.0

    ns = dict(SAFE_NAMES)
    # Değişkenleri ekle (hem verilen ad hem büyük harf)
    for key, val in variables.items():
        try:
            fval = float(val)
        except (TypeError, ValueError):
            continue
        ns[key] = fval
        ns[key.upper()] = fval

    # W/H/T takma adlarını LPX/LPY/LPZ'ye bağla
    for alias, target in ALIASES.items():
        if target in ns and alias not in ns:
            ns[alias] = ns[target]
        if target.upper() in ns and alias.upper() not in ns:
            ns[alias.upper()] = ns[target.upper()]

    for candidate in (expr.upper(), expr):
        try:
            return float(eval(candidate, {"__builtins__": {}}, ns))
        except Exception:
            continue
    return 0.0


def build_variables(W, H, T, constants=None):
    """Standart değişken sözlüğü oluşturur."""
    variables = {
        "LPX": float(W or 0), "LPY": float(H or 0), "LPZ": float(T or 0),
        "W": float(W or 0), "H": float(H or 0), "T": float(T or 0),
    }
    if constants:
        for key, val in constants.items():
            try:
                variables[key] = float(val)
            except (TypeError, ValueError):
                pass
    return variables
