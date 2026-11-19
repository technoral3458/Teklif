"""Fuar standı donatıları: ekran / TV, masalar, sandalye, tabure, koltuk, kanepe, sehpa, depo odası,
broşürlük, bitki. Bunlar kabin değildir (TitaniumCam üretmez); siparişe kalem olarak yazılır.

Katalog yöneticinin elindedir (stand_donati tablosu; ilk açılışta VARSAYILAN ile dolar). Müşteri ne
istediğini ve sandalye / koltuk modelini seçer; seçimler "set"lere dönüşür (ör. 4 kişilik toplantı seti =
masa + 4 sandalye). Set, plana tek dikdörtgen olarak yerleşir (stand modülleriyle aynı x, y, yon kuralı);
içindeki parçaların yeri setin kendi çerçevesinde sabittir.

Set çerçevesi (mm): u soldan sağa 0..en, v arkadan öne 0..derinlik; set "on"a bakarken önü +v'dir.
Parça açısı (derece) 0 = öne (+v) bakar. Plan açısı: on 0, sol 90, arka 180, sag -90 (SVG rotate ile aynı).
"""
import math

# grup → (müşteriye görünen ad, kullanılabilir 3B modeller)
GRUPLAR = {
    "sandalye": ("Sandalye", ["sandalye"]),
    "tabure": ("Bar taburesi", ["tabure"]),
    "koltuk": ("Berjer / tekli koltuk", ["berjer"]),
    "kanepe": ("Kanepe", ["kanepe"]),
    "toplanti_masasi": ("Toplantı masası", ["masa_yuvarlak", "masa_kare"]),
    "bar_masasi": ("Bar masası", ["masa_yuvarlak", "masa_kare"]),
    "sehpa": ("Sehpa", ["masa_yuvarlak", "masa_kare"]),
    "ekran": ("Ekran / TV", ["tv", "led"]),
    "tv_standi": ("TV ayaklı standı", ["tv_standi"]),
    "vitrin": ("Cam vitrin", ["cam_vitrin"]),
    "depo_odasi": ("Depo odası", ["depo"]),
    "brosurluk": ("Broşürlük", ["brosurluk"]),
    "bitki": ("Saksı bitki", ["bitki"]),
}
MODEL_AD = {"sandalye": "Sandalye", "tabure": "Tabure", "berjer": "Berjer", "kanepe": "Kanepe",
            "masa_yuvarlak": "Yuvarlak masa", "masa_kare": "Dikdörtgen masa", "tv": "TV", "led": "LED ekran",
            "tv_standi": "TV standı", "cam_vitrin": "Cam vitrin", "depo": "Depo odası", "brosurluk": "Broşürlük", "bitki": "Bitki"}

# (kod, ad, grup, model, en, derinlik, yükseklik, renk)
VARSAYILAN = [
    ("sandalye_ofis", "Ofis sandalyesi (siyah)", "sandalye", "sandalye", 470, 500, 850, "#1f2937"),
    ("sandalye_tasarim", "Tasarım sandalye (beyaz)", "sandalye", "sandalye", 450, 500, 820, "#f1f5f9"),
    ("sandalye_ahsap", "Ahşap sandalye (ceviz)", "sandalye", "sandalye", 450, 500, 820, "#8b5e3c"),
    ("tabure_siyah", "Bar taburesi (siyah)", "tabure", "tabure", 400, 400, 760, "#111827"),
    ("tabure_beyaz", "Bar taburesi (beyaz)", "tabure", "tabure", 400, 400, 760, "#f8fafc"),
    ("berjer_gri", "Berjer (gri kumaş)", "koltuk", "berjer", 760, 760, 800, "#6b7280"),
    ("berjer_deri", "Berjer (kahve deri)", "koltuk", "berjer", 780, 780, 820, "#7c4a2d"),
    ("berjer_hardal", "Berjer (hardal kadife)", "koltuk", "berjer", 760, 760, 800, "#ca8a04"),
    ("kanepe_ikili", "İkili kanepe (gri)", "kanepe", "kanepe", 1500, 820, 800, "#64748b"),
    ("kanepe_uclu", "Üçlü kanepe (lacivert)", "kanepe", "kanepe", 2000, 850, 800, "#1e3a8a"),
    ("toplanti_yuvarlak", "Yuvarlak toplantı masası Ø90", "toplanti_masasi", "masa_yuvarlak", 900, 900, 740, "#f8fafc"),
    ("toplanti_dikdortgen", "Toplantı masası 160×80", "toplanti_masasi", "masa_kare", 1600, 800, 740, "#f8fafc"),
    ("bar_masasi_60", "Bar masası Ø60", "bar_masasi", "masa_yuvarlak", 600, 600, 1050, "#f8fafc"),
    ("sehpa_60", "Orta sehpa Ø60", "sehpa", "masa_yuvarlak", 600, 600, 450, "#f8fafc"),
    ("tv_43", 'TV 43"', "ekran", "tv", 970, 60, 560, "#0b0f14"),
    ("tv_55", 'TV 55"', "ekran", "tv", 1230, 60, 710, "#0b0f14"),
    ("tv_65", 'TV 65"', "ekran", "tv", 1450, 60, 830, "#0b0f14"),
    ("tv_75", 'TV 75"', "ekran", "tv", 1680, 60, 960, "#0b0f14"),
    ("led_2x15", "LED ekran 2 × 1,5 m", "ekran", "led", 2000, 150, 1500, "#0b0f14"),
    ("tv_standi", "TV ayaklı standı", "tv_standi", "tv_standi", 800, 600, 1600, "#334155"),
    ("cam_vitrin", "Cam vitrin (ışıklı, boy)", "vitrin", "cam_vitrin", 800, 450, 1900, "#bfdbfe"),
    ("cam_vitrin_alcak", "Alçak cam vitrin (tezgâh)", "vitrin", "cam_vitrin", 1000, 500, 1000, "#bfdbfe"),
    ("depo_odasi", "Depo odası (kapılı, 1 × 1,5 m)", "depo_odasi", "depo", 1000, 1500, 2500, "#f4f3f1"),
    ("brosurluk", "Broşürlük (ayaklı)", "brosurluk", "brosurluk", 320, 320, 1500, "#94a3b8"),
    ("bitki", "Saksı bitki (yapay)", "bitki", "bitki", 500, 500, 1300, "#15803d"),
]
ALANLAR = ("kod", "ad", "grup", "model", "en", "derinlik", "yukseklik", "renk")

TOPLANTI = (0, 2, 4, 6, 8)
OTURMA = ("", "berjer", "kanepe")


def varsayilan_katalog():
    return {r[0]: {**dict(zip(ALANLAR, r)), "fiyat": 0.0, "aktif": 1} for r in VARSAYILAN}


def _int(v, ab, vars=0):
    try:
        return max(ab[0], min(ab[1], int(float(str(v).replace(",", ".")))))
    except (TypeError, ValueError):
        return vars


def grup_ogeleri(katalog, grup):
    return [d for d in katalog.values() if d["grup"] == grup and d.get("aktif", 1)]


def donati_dogrula(ham, katalog, alan_m2=24):
    """Formdaki donatı seçimleri → temiz seçim. Seçilen model aktif değilse grubun ilki."""
    ham = ham or {}

    def model(grup, kod):
        g = grup_ogeleri(katalog, grup)
        if not g:
            return ""
        return kod if any(d["kod"] == kod for d in g) else g[0]["kod"]

    s = {
        "ekran": model("ekran", ham.get("ekran")) if ham.get("ekran") else "",
        "ekran_adet": _int(ham.get("ekran_adet"), (1, 3), 1),
        "toplanti": _int(ham.get("toplanti"), (0, 8), 0),
        "bar": _int(ham.get("bar"), (0, 4), 0),
        "oturma": ham.get("oturma") if ham.get("oturma") in OTURMA else "",
        "depo": str(ham.get("depo", "")) in ("1", "on", "true", "True"),
        "brosur": _int(ham.get("brosur"), (0, 3), 0),
        "bitki": _int(ham.get("bitki"), (0, 6), 0),
        "vitrin": _int(ham.get("vitrin"), (0, 4), 0),
        "vitrin_model": model("vitrin", ham.get("vitrin_model")),
    }
    if s["toplanti"] not in TOPLANTI:
        s["toplanti"] = min(TOPLANTI, key=lambda n: abs(n - s["toplanti"]))
    for g in ("sandalye", "tabure", "koltuk", "kanepe", "toplanti_masasi", "bar_masasi", "sehpa"):
        s[g] = model(g, ham.get(g))
    if not s["ekran"]:
        s["ekran_adet"] = 0
    return s


# ── setler ──────────────────────────────────────────────────────────────────
def _p(d, u, v, aci=0.0):
    return {"kod": d["kod"], "ad": d["ad"], "model": d["model"], "en": d["en"], "derinlik": d["derinlik"],
            "yukseklik": d["yukseklik"], "renk": d["renk"], "u": round(u, 1), "v": round(v, 1), "aci": round(aci, 1)}


def _toplanti(masa, sandalye, n):
    T, Td = masa["en"], masa["derinlik"]
    sd = sandalye["derinlik"]
    if masa["model"] == "masa_yuvarlak":
        r = T / 2 + sd / 2 - 40
        boy = T + 2 * (sd - 40) + 100
        c = boy / 2
        parca = [_p(masa, c, c)]
        for i in range(n):
            a = 2 * math.pi * i / n + math.pi / n          # sandalye masaya bakar
            u, v = c + r * math.sin(a), c + r * math.cos(a)
            parca.append(_p(sandalye, u, v, 180 - math.degrees(a)))    # masanın merkezine bakar
        return boy, boy, parca
    en, der = T + (2 * (sd - 40) if n > 4 else 0) + 100, Td + 2 * (sd - 40) + 100
    cu, cv = en / 2, der / 2
    parca = [_p(masa, cu, cv)]
    yan = n // 2 if n <= 4 else (n - 2) // 2
    for i in range(yan):                                  # uzun kenarlar
        u = cu - T / 2 + T * (i + 0.5) / yan
        parca.append(_p(sandalye, u, cv - Td / 2 - sd / 2 + 40, 0))      # arkada, öne bakar
        parca.append(_p(sandalye, u, cv + Td / 2 + sd / 2 - 40, 180))    # önde, arkaya bakar
    if n > 4:                                             # uçlar
        parca.append(_p(sandalye, cu - T / 2 - sd / 2 + 40, cv, -90))
        parca.append(_p(sandalye, cu + T / 2 + sd / 2 - 40, cv, 90))
    return en, der, parca


def setler(secim, katalog, girdi):
    """Seçimden yerleştirilecek setler. Her set: {id, tur, ad, en, derinlik, parcalar, tercih}."""
    K = katalog
    out = []
    if secim.get("depo") and "depo_odasi" in K:
        d = K["depo_odasi"]
        out.append({"id": "depo", "tur": "depo", "ad": "Depo odası", "en": d["en"], "derinlik": d["derinlik"],
                    "parcalar": [_p(d, d["en"] / 2, d["derinlik"] / 2)]})
    if secim.get("ekran") in K:
        e = K[secim["ekran"]]
        for i in range(secim.get("ekran_adet") or 1):
            out.append({"id": f"ekran_{i + 1}", "tur": "ekran", "ad": e["ad"], "en": e["en"] + 100,
                        "derinlik": 150, "montaj": "duvar", "parcalar": [_p(e, (e["en"] + 100) / 2, 75)]})
    if secim.get("toplanti") and secim.get("toplanti_masasi") in K and secim.get("sandalye") in K:
        en, der, parca = _toplanti(K[secim["toplanti_masasi"]], K[secim["sandalye"]], secim["toplanti"])
        out.append({"id": "toplanti", "tur": "toplanti", "ad": f"Toplantı alanı ({secim['toplanti']} kişi)",
                    "en": round(en), "derinlik": round(der), "parcalar": parca})
    if secim.get("oturma") and secim.get("sehpa") in K:
        s = K[secim["sehpa"]]
        if secim["oturma"] == "kanepe" and secim.get("kanepe") in K:
            k = K[secim["kanepe"]]
            en, der = max(k["en"], s["en"]) + 100, k["derinlik"] + 150 + s["derinlik"] + 50
            parca = [_p(k, en / 2, k["derinlik"] / 2 + 50), _p(s, en / 2, k["derinlik"] + 150 + s["derinlik"] / 2)]
            out.append({"id": "oturma", "tur": "oturma", "ad": "Bekleme alanı (kanepe)", "en": round(en),
                        "derinlik": round(der), "parcalar": parca})
        elif secim["oturma"] == "berjer" and secim.get("koltuk") in K:
            k = K[secim["koltuk"]]
            en = 2 * k["en"] + s["en"] + 200
            der = max(k["derinlik"], s["derinlik"]) + 100
            parca = [_p(k, k["en"] / 2 + 50, der / 2, -25), _p(s, en / 2, der / 2 + 60),
                     _p(k, en - k["en"] / 2 - 50, der / 2, 25)]
            out.append({"id": "oturma", "tur": "oturma", "ad": "Bekleme alanı (2 berjer)", "en": round(en),
                        "derinlik": round(der), "parcalar": parca})
    if secim.get("bar_masasi") in K and secim.get("tabure") in K:
        b, t = K[secim["bar_masasi"]], K[secim["tabure"]]
        for i in range(secim.get("bar") or 0):
            en = b["en"] + 2 * t["en"] + 60
            der = max(b["derinlik"], t["derinlik"]) + 60
            parca = [_p(b, en / 2, der / 2), _p(t, t["en"] / 2 + 10, der / 2, -90), _p(t, en - t["en"] / 2 - 10, der / 2, 90)]
            out.append({"id": f"bar_{i + 1}", "tur": "bar", "ad": "Bar masası + 2 tabure", "en": round(en),
                        "derinlik": round(der), "parcalar": parca})
    if secim.get("vitrin_model") in K:
        d = K[secim["vitrin_model"]]
        for i in range(secim.get("vitrin") or 0):
            out.append({"id": f"vitrin_{i + 1}", "tur": "vitrin", "ad": d["ad"], "en": d["en"], "derinlik": d["derinlik"],
                        "parcalar": [_p(d, d["en"] / 2, d["derinlik"] / 2)]})
    for grup, tur, adet in (("brosurluk", "brosur", secim.get("brosur") or 0), ("bitki", "bitki", secim.get("bitki") or 0)):
        g = grup_ogeleri(K, grup)
        if g:
            d = g[0]
            for i in range(adet):
                out.append({"id": f"{tur}_{i + 1}", "tur": tur, "ad": d["ad"], "en": d["en"], "derinlik": d["derinlik"],
                            "parcalar": [_p(d, d["en"] / 2, d["derinlik"] / 2)]})
    return out


def tv_standli(st, katalog):
    """Duvara asılamayan ekran: ayaklı stand ile (iz derinleşir)."""
    if st["tur"] != "ekran" or st.get("montaj") == "ayakli":
        return st
    s = katalog.get("tv_standi")
    e = st["parcalar"][0]
    der = (s or {}).get("derinlik", 600)
    en = max(st["en"], (s or {}).get("en", 800))
    parca = [dict(e, u=en / 2, v=der / 2 + 40)]
    if s:
        parca.insert(0, _p(s, en / 2, der / 2))
    return {**st, "en": en, "derinlik": der, "montaj": "ayakli", "parcalar": parca}


# ── plan dönüşümü ──────────────────────────────────────────────────────────
YON_ACI = {"on": 0.0, "sol": 90.0, "arka": 180.0, "sag": -90.0}


def parcalar_plan(st):
    """Yerleşmiş setin parçaları plan koordinatında: merkez (x, y) ve açı (derece)."""
    phi = YON_ACI[st["yon"]]
    w, h = (st["en"], st["derinlik"]) if st["yon"] in ("on", "arka") else (st["derinlik"], st["en"])
    cx, cy = st["x"] + w / 2, st["y"] + h / 2
    c, s = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    out = []
    for p in st["parcalar"]:
        u, v = p["u"] - st["en"] / 2, p["v"] - st["derinlik"] / 2
        out.append({**p, "x": round(cx + u * c - v * s, 1), "y": round(cy + u * s + v * c, 1),
                    "aci": round(phi + p["aci"], 1), "montaj": st.get("montaj", "")})
    return out


def kalemler(yerlesik, katalog):
    """Siparişe yazılacak donatı kalemleri: kod başına adet × katalog fiyatı."""
    say = {}
    for st in yerlesik:
        for p in st["parcalar"]:
            say[p["kod"]] = say.get(p["kod"], 0) + 1
    out = []
    for kod, n in say.items():
        d = katalog.get(kod) or {}
        out.append({"kod": "don:" + kod, "ad": d.get("ad", kod), "miktar": n, "birim": "adet",
                    "detay": GRUPLAR.get(d.get("grup"), ("",))[0], "fiyat": float(d.get("fiyat") or 0)})
    sira = list(GRUPLAR)
    out.sort(key=lambda k: sira.index((katalog.get(k["kod"][4:]) or {}).get("grup", "bitki")))
    return out
