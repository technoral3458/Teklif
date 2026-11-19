"""Fuar standı tasarımı: girdi (ölçü, açık cepheler, duvar yüksekliği) → alternatif yerleşimler →
her modül TitaniumCam motorunda kabin olarak kurulur (hazir_katalog.kur ile).

Plan koordinatı (mm, üstten bakış): x soldan sağa (0..en), y arka duvardan ön cepheye (0..derinlik).
Modülün `yon`u açık / kapaklı yüzünün baktığı taraftır: "on" (koridora), "arka", "sol", "sag".
Kabinden olmayan kalemler (duvar paneli, zemin, aydınlatma, grafik) sipariş bilgisi olarak taşınır.

Alternatifleri yapay zekâ (Claude) önerir; anahtar yoksa ya da cevap kullanılamazsa kurallı üretici
devreye girer. Her iki durumda da sonuç aynı doğrulamadan geçer: ölçüler sınırlara çekilir, standın
dışına taşan modül içeri alınır, çakışan modül çıkarılır.
"""
import itertools
import json
import logging
import math
import os

import stand_donati as SD
from hazir_katalog import USTLU, dikey, goz, kapak, olcu, raf, yatay

log = logging.getLogger("stand")

CEPHELER = ("on", "sol", "sag", "arka")
CEPHE_AD = {"on": "Ön", "sol": "Sol", "sag": "Sağ", "arka": "Arka"}
YONLER = CEPHELER
SINIR = {"en": (2000, 20000), "derinlik": (2000, 20000), "yukseklik": (2400, 5000)}
EN_COK_MODUL = 14

# ── Stand modülleri (her biri bir kabin) ────────────────────────────────────
# sinir: (en, boy, derinlik) aralıkları mm; raf / kapak: (en az, en çok)
MODULLER = {
    "banko": {"ad": "Karşılama bankosu", "sablon": "Alt Dolap", "renk": "#0f766e",
              "aciklama": "Ziyaretçiyi karşılayan tezgâh. Arkası (personel tarafı) raflı.",
              "en": (1000, 2400), "boy": (950, 1150), "derinlik": (500, 650), "raf": (1, 2), "kapak": (0, 0),
              "varsayilan": {"en": 1600, "boy": 1050, "derinlik": 600, "raf": 1, "kapak": 0}},
    "raf_unitesi": {"ad": "Açık teşhir rafı", "sablon": "Boy Dolap", "renk": "#2563eb",
                    "aciklama": "Ürün sergilemek için açık raflı boy ünite.",
                    "en": (600, 1200), "boy": (1800, 2600), "derinlik": (300, 450), "raf": (3, 6), "kapak": (0, 0),
                    "varsayilan": {"en": 1000, "boy": 2200, "derinlik": 400, "raf": 4, "kapak": 0}},
    "vitrin_dolabi": {"ad": "Alt kapaklı teşhir dolabı", "sablon": "Boy Dolap", "renk": "#7c3aed",
                      "aciklama": "Üstte açık teşhir rafları, altta kapaklı saklama.",
                      "en": (600, 1200), "boy": (1800, 2600), "derinlik": (350, 500), "raf": (1, 4), "kapak": (1, 2),
                      "varsayilan": {"en": 1000, "boy": 2200, "derinlik": 400, "raf": 2, "kapak": 2}},
    "depo_dolabi": {"ad": "Depo dolabı", "sablon": "Boy Dolap", "renk": "#475569",
                    "aciklama": "Kapaklı, raflı saklama dolabı (katalog, ikram, çanta).",
                    "en": (600, 1200), "boy": (1800, 2600), "derinlik": (450, 600), "raf": (2, 5), "kapak": (1, 2),
                    "varsayilan": {"en": 900, "boy": 2100, "derinlik": 560, "raf": 4, "kapak": 2}},
    "ikram_tezgahi": {"ad": "İkram tezgâhı", "sablon": "Alt Dolap", "renk": "#be123c",
                      "aciklama": "İkram / mutfak köşesi: kapaklı alt dolap ve tezgâh (kahve makinesi, su ısıtıcı için).",
                      "en": (800, 2400), "boy": (850, 950), "derinlik": (550, 650), "raf": (1, 1), "kapak": (1, 2),
                      "varsayilan": {"en": 1200, "boy": 900, "derinlik": 600, "raf": 1, "kapak": 2}},
    "alt_teshir": {"ad": "Teşhir / sunum ünitesi", "sablon": "Alt Dolap", "renk": "#d97706",
                   "aciklama": "Bel hizasında ürün sunum ünitesi; altı raflı ya da kapaklı.",
                   "en": (600, 2000), "boy": (750, 1000), "derinlik": (400, 800), "raf": (0, 2), "kapak": (0, 2),
                   "varsayilan": {"en": 1200, "boy": 900, "derinlik": 600, "raf": 1, "kapak": 0}},
}

# Kabinden olmayan kalemler (birim fiyatları yönetici girer: Ayarlar → Stand tasarımı)
EKSTRA_KALEMLER = {
    "duvar": {"ad": "Duvar paneli", "birim": "m²"},
    "zemin": {"ad": "Yükseltilmiş zemin / halı", "birim": "m²"},
    "spot": {"ad": "Spot aydınlatma", "birim": "adet"},
    "grafik": {"ad": "Logo / grafik baskı", "birim": "m²"},
    "truss": {"ad": "Truss tavan (alüminyum, spot askılı)", "birim": "m"},
    "banner": {"ad": "Askılı tavan banner'ı", "birim": "adet"},
}


def yapi_dogrula(ham):
    """Stand yapısı seçimleri: truss tavan, askılı banner, ikram köşesi (logo dosya adı router'da doğrulanır)."""
    e = lambda k: str((ham or {}).get(k, "")) in ("1", "on", "true", "True")   # noqa: E731
    return {"truss": e("truss"), "banner": e("banner"), "ikram": e("ikram"), "logo": (ham or {}).get("logo") or ""}


def _sayi(v, varsayilan):
    try:
        return float(str(v).replace(",", "."))
    except (TypeError, ValueError):
        return float(varsayilan)


def _kis(v, ab):
    return max(ab[0], min(ab[1], v))


def girdi_dogrula(en, derinlik, yukseklik, acik, sektor="", istek=""):
    """Form girdisi → temiz girdi. Ölçüler mm. ValueError: kullanıcıya gösterilecek mesaj."""
    g = {"en": _sayi(en, 0), "derinlik": _sayi(derinlik, 0), "yukseklik": _sayi(yukseklik, 0)}
    for k, (a, b) in SINIR.items():
        if not a <= g[k] <= b:
            ad = {"en": "Genişlik", "derinlik": "Derinlik", "yukseklik": "Duvar yüksekliği"}[k]
            raise ValueError(f"{ad} {a / 1000:g}–{b / 1000:g} m arasında olmalı")
    acik = [c for c in CEPHELER if c in set(acik or [])]
    if not acik:
        raise ValueError("En az bir cephe açık olmalı (koridora bakan taraf)")
    g["acik"] = acik
    g["sektor"] = str(sektor or "").strip()[:120]
    g["istek"] = str(istek or "").strip()[:600]
    return g


def stand_tipi(acik):
    return {1: "Sıra stand (tek cephe açık)", 2: "Köşe stand (iki cephe açık)",
            3: "Yarımada stand (üç cephe açık)", 4: "Ada stand (dört cephe açık)"}[len(acik)]


# ── Yerleşim doğrulama ──────────────────────────────────────────────────────
def ayak_izi(m):
    """Modülün plandaki dikdörtgeni (x, y, w, h)."""
    if m["yon"] in ("on", "arka"):
        return m["x"], m["y"], m["en"], m["derinlik"]
    return m["x"], m["y"], m["derinlik"], m["en"]


def _cakisir(a, b, pay=1.0):
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax + aw - pay > bx and bx + bw - pay > ax and ay + ah - pay > by and by + bh - pay > ay


def modul_duzelt(ham, girdi):
    """Tek modülü sınırlara çeker. Bilinmeyen tip → None."""
    tip = str(ham.get("tip", ""))
    t = MODULLER.get(tip)
    if not t:
        return None
    v = t["varsayilan"]
    m = {"tip": tip, "ad": (str(ham.get("ad") or t["ad"]).strip() or t["ad"])[:60],
         "yon": ham.get("yon") if ham.get("yon") in YONLER else "on"}
    for k in ("en", "boy", "derinlik"):
        m[k] = round(_kis(_sayi(ham.get(k), v[k]), t[k]))
    m["boy"] = min(m["boy"], max(t["boy"][0], girdi["yukseklik"] - 100))     # duvardan yüksek olamaz
    for k in ("raf", "kapak"):
        m[k] = int(_kis(round(_sayi(ham.get(k), v[k])), t[k]))
    if tip in ("vitrin_dolabi", "depo_dolabi") and m["en"] <= 700:
        m["kapak"] = 1
    m["x"], m["y"] = _sayi(ham.get("x"), 0), _sayi(ham.get("y"), 0)
    return m


def yerlesim_duzelt(moduller, girdi):
    """Modülleri doğrular. Döner: (kabul edilen modüller, notlar)."""
    W, D = girdi["en"], girdi["derinlik"]
    kabul, notlar = [], []
    for ham in (moduller or [])[:EN_COK_MODUL * 2]:
        m = modul_duzelt(ham if isinstance(ham, dict) else {}, girdi)
        if not m:
            continue
        x, y, w, h = ayak_izi(m)
        if w > W or h > D:
            notlar.append(f"{m['ad']} standa sığmadığı için çıkarıldı")
            continue
        m["x"], m["y"] = round(_kis(x, (0, W - w))), round(_kis(y, (0, D - h)))
        if any(_cakisir(ayak_izi(m), ayak_izi(k)) for k in kabul):
            notlar.append(f"{m['ad']} başka bir modülle çakıştığı için çıkarıldı")
            continue
        if len(kabul) >= EN_COK_MODUL:
            notlar.append("En fazla 14 modül")
            break
        kabul.append(m)
    return kabul, notlar


def ekstralar(girdi, alt, donatilar=(), katalog=None):
    """Kabinden olmayan kalemlerin miktarları (duvar, zemin, spot, grafik + donatılar)."""
    W, D, H = girdi["en"] / 1000, girdi["derinlik"] / 1000, girdi["yukseklik"] / 1000
    kapali = [c for c in CEPHELER if c not in girdi["acik"]]
    duvar = sum((W if c in ("on", "arka") else D) * H for c in kapali)
    alan = W * D
    spot = int(alt.get("spot") or 0) or max(2, round(alan / 3))
    grafik = _sayi(alt.get("grafik_m2"), 0) or (round(min(duvar, W * 1.0), 2) if kapali else round(min(alan / 6, 3), 2))
    k = []
    if duvar:
        k.append({"kod": "duvar", "miktar": round(duvar, 2),
                  "detay": ", ".join(f"{CEPHE_AD[c]} duvar {(W if c in ('on', 'arka') else D):g} × {H:g} m" for c in kapali)})
    k.append({"kod": "zemin", "miktar": round(alan, 2), "detay": f"{W:g} × {D:g} m"})
    k.append({"kod": "spot", "miktar": spot, "detay": ""})
    k.append({"kod": "grafik", "miktar": round(grafik, 2), "detay": "Logo / marka yüzeyi"})
    yapi = girdi.get("yapi") or {}
    if yapi.get("truss"):
        k.append({"kod": "truss", "miktar": round(2 * (W + D), 2), "detay": f"Stand çevresi, {H + 0.3:g} m yükseklikte"})
    if yapi.get("banner"):
        cap = round(min(3.0, min(W, D) * 0.6), 1)
        k.append({"kod": "banner", "miktar": 1, "detay": f"Yuvarlak Ø{cap:g} m × 1 m, stand ortasında"})
    for kk in k:
        kk.update(EKSTRA_KALEMLER[kk["kod"]])
    return SD.kalemler(donatilar, katalog or {}) + k


def alternatif_duzelt(ham, girdi, setler=(), katalog=None):
    moduller, notlar = yerlesim_duzelt(ham.get("moduller"), girdi)
    if not moduller:
        return None
    if (girdi.get("yapi") or {}).get("ikram") and not any(m["tip"] == "ikram_tezgahi" for m in moduller):
        ik = _ikram_yer(girdi, moduller)
        if ik:
            moduller.append(ik)
        else:
            notlar.append("İkram tezgâhı standa sığmadı")
    yer = {d.get("set"): d for d in (ham.get("donatilar") or []) if isinstance(d, dict)}
    donatilar, n2 = setleri_yerlestir(girdi, setler, moduller, yer, katalog or {})
    return {"ad": (str(ham.get("ad") or "Alternatif").strip() or "Alternatif")[:60],
            "konsept": str(ham.get("konsept") or "").strip()[:400],
            "moduller": moduller, "donatilar": donatilar, "notlar": notlar + n2,
            "ekstralar": ekstralar(girdi, ham, donatilar, katalog)}


# ── Donatı setlerinin yerleşimi ─────────────────────────────────────────────
TERS = {"on": "arka", "arka": "on", "sag": "sol", "sol": "sag"}
SET_SIRA = ("depo", "ekran", "vitrin", "toplanti", "oturma", "bar", "brosur", "bitki")


def _kenara_degiyor(iz, cephe, W, D):
    x, y, w, h = iz
    return {"arka": y <= 1, "on": y + h >= D - 1, "sol": x <= 1, "sag": x + w >= W - 1}[cephe]


def _acik_uzaklik(iz, girdi):
    x, y, w, h = iz
    W, D = girdi["en"], girdi["derinlik"]
    u = {"arka": y, "on": D - y - h, "sol": x, "sag": W - x - w}
    return min(u[c] for c in girdi["acik"])


def _duvara_sirt(st, girdi):
    t = TERS[st["yon"]]
    return t not in girdi["acik"] and _kenara_degiyor(ayak_izi(st), t, girdi["en"], girdi["derinlik"])


def _puan(st, girdi):
    """Kurallı yerleşim puanı (küçük iyi). None: bu yer olmaz."""
    W, D = girdi["en"], girdi["derinlik"]
    iz = ayak_izi(st)
    x, y, w, h = iz
    kapali = [c for c in CEPHELER if c not in girdi["acik"]]
    acik_u = _acik_uzaklik(iz, girdi)
    tur = st["tur"]
    if tur == "depo" and not kapali:        # ada stand: ortada, koridordan uzak
        return -acik_u
    if tur in ("depo",) or (tur == "ekran" and st.get("montaj") == "duvar"):
        if not _duvara_sirt(st, girdi):
            return None
        if tur == "depo":
            return -2000 * sum(_kenara_degiyor(iz, c, W, D) for c in kapali) - acik_u
        t = TERS[st["yon"]]
        orta = (x + w / 2 - W / 2) if t in ("arka", "on") else (y + h / 2 - D / 2)
        return abs(orta)
    if tur == "vitrin":                     # duvara yaslı, koridordan görünür; duvar yoksa koridora bakar
        if kapali:
            return (0 if _duvara_sirt(st, girdi) else 4000) + 0.3 * acik_u
        return acik_u - (400 if st["yon"] in girdi["acik"] else 0)
    if tur == "toplanti":
        return -acik_u
    if tur == "oturma":
        return abs(acik_u - 500) - (400 if st["yon"] in girdi["acik"] else 0)
    if tur == "bar":
        return abs(acik_u - 300)
    if tur == "brosur":                     # ana koridorda, ortaya (bankonun yanına) yakın
        ana = next(c for c in ("on", "sol", "sag", "arka") if c in girdi["acik"])
        u = {"arka": y, "on": D - y - h, "sol": x, "sag": W - x - w}[ana]
        orta = abs(x + w / 2 - W / 2) if ana in ("on", "arka") else abs(y + h / 2 - D / 2)
        return u + 0.3 * orta
    if tur == "bitki":
        return min(math.hypot(cx - (x + w / 2), cy - (y + h / 2)) for cx in (0, W) for cy in (0, D))
    if tur == "ekran":                      # ayaklı: ortada, koridora bakar
        return math.hypot(x + w / 2 - W / 2, y + h / 2 - D / 2) - (500 if st["yon"] in girdi["acik"] else 0)
    return 0


def _yer_bul(girdi, st, dolu):
    W, D = girdi["en"], girdi["derinlik"]
    adim = max(100, math.ceil(max(W, D) / 60 / 100) * 100)
    pay = 100 if st["tur"] in ("bitki", "brosur") else 0 if st["tur"] in ("depo", "ekran", "vitrin") else 300
    yonler = ("on",) if st["tur"] in ("toplanti", "bitki", "brosur") else ("on", "sag") if st["tur"] == "bar" else YONLER
    en_iyi = None
    for yon in yonler:
        w, h = (st["en"], st["derinlik"]) if yon in ("on", "arka") else (st["derinlik"], st["en"])
        if w > W or h > D:
            continue
        xs = sorted(set(list(range(0, int(W - w) + 1, adim)) + [W - w]))
        ys = sorted(set(list(range(0, int(D - h) + 1, adim)) + [D - h]))
        for x in xs:
            for y in ys:
                aday = {**st, "x": x, "y": y, "yon": yon}
                if any(_cakisir((x - pay, y - pay, w + 2 * pay, h + 2 * pay), k) for k in dolu):
                    continue
                p = _puan(aday, girdi)
                if p is not None and (en_iyi is None or p < en_iyi[0]):
                    en_iyi = (p, aday)
    return en_iyi[1] if en_iyi else None


def _ikram_yer(girdi, mevcut):
    """İkram tezgâhı: duvarlı köşeye (yoksa ortaya), mevcut modül / setlere çarpmadan."""
    v = MODULLER["ikram_tezgahi"]["varsayilan"]
    a = _yer_bul(girdi, {"id": "ikram", "tur": "depo", "ad": "İkram", "en": v["en"], "derinlik": v["derinlik"]},
                 [ayak_izi(m) for m in mevcut])
    return modul_duzelt({"tip": "ikram_tezgahi", **v, "x": a["x"], "y": a["y"], "yon": a["yon"]}, girdi) if a else None


def setleri_yerlestir(girdi, setler, moduller, yer=None, katalog=None):
    """Setleri yerleştirir: önce verilen (yapay zekâ) yer, olmazsa kurallı arama. Döner: (yerleşik setler, notlar)."""
    W, D = girdi["en"], girdi["derinlik"]
    yer = yer or {}
    dolu = [ayak_izi(m) for m in moduller]
    out, notlar = [], []
    for st in sorted(setler, key=lambda s: SET_SIRA.index(s["tur"])):
        st = dict(st)
        if st["tur"] == "ekran" and not any(c not in girdi["acik"] for c in CEPHELER):
            st = SD.tv_standli(st, katalog or {})
        aday = None
        y0 = yer.get(st["id"])
        if y0 and y0.get("yon") in YONLER:
            if st["tur"] == "ekran" and y0.get("montaj") == "ayakli":
                st = SD.tv_standli(st, katalog or {})
            a = {**st, "x": _sayi(y0.get("x"), 0), "y": _sayi(y0.get("y"), 0), "yon": y0["yon"]}
            x, y, w, h = ayak_izi(a)
            if w <= W and h <= D:
                a["x"], a["y"] = round(_kis(x, (0, W - w))), round(_kis(y, (0, D - h)))
                if a["tur"] == "ekran" and a.get("montaj") == "duvar" and not _duvara_sirt(a, girdi):
                    a = {**SD.tv_standli(a, katalog or {}), "x": a["x"], "y": a["y"], "yon": a["yon"]}
                    x, y, w, h = ayak_izi(a)
                    a["x"], a["y"] = round(_kis(x, (0, W - w))), round(_kis(y, (0, D - h)))
                if not any(_cakisir(ayak_izi(a), k) for k in dolu):
                    aday = a
        if not aday:
            aday = _yer_bul(girdi, st, dolu)
        if not aday and st["tur"] == "ekran" and st.get("montaj") == "duvar":
            aday = _yer_bul(girdi, SD.tv_standli(st, katalog or {}), dolu)
        if not aday:
            notlar.append(f"{st['ad']} standa sığmadı")
            continue
        out.append(aday)
        dolu.append(ayak_izi(aday))
    return out, notlar


# ── Kurallı üretici (yapay zekâ yokken ya da yanıt kullanılamadığında) ───────
def _duvar_dizisi(girdi, cephe, tip, adet, bas=0.0, bos=0.0, **p):
    """Kapalı bir duvar boyunca yan yana modüller (yüzü standın içine bakar)."""
    W, D = girdi["en"], girdi["derinlik"]
    v = {**MODULLER[tip]["varsayilan"], **p}
    if cephe in ("sol", "sag") and "arka" not in girdi["acik"]:
        bas = max(bas, 650)               # arka duvardaki modüllerin önünden başlar (köşede çakışmasın)
    uzun =(W if cephe in ("arka", "on") else D) - bas - bos
    adet = max(0, min(adet, int(uzun // v["en"])))
    out = []
    for i in range(adet):
        s = bas + i * v["en"]
        if cephe == "arka":
            out.append({"tip": tip, **v, "x": s, "y": 0, "yon": "on"})
        elif cephe == "on":
            out.append({"tip": tip, **v, "x": s, "y": D - v["derinlik"], "yon": "arka"})
        elif cephe == "sol":
            out.append({"tip": tip, **v, "x": 0, "y": s, "yon": "sag"})
        else:
            out.append({"tip": tip, **v, "x": W - v["derinlik"], "y": s, "yon": "sol"})
    return out


def _banko_yerlestir(girdi, mevcut):
    """Bankoyu bir açık cepheye (öncelik: ön), içeri bakacak şekilde, diğer modüllere çarpmadan koyar.
    Önünde geçiş payı bırakır; küçük standlarda pay azalır."""
    W, D = girdi["en"], girdi["derinlik"]
    v = dict(MODULLER["banko"]["varsayilan"])
    izler = [ayak_izi(modul_duzelt(m, girdi)) for m in mevcut]
    cepheler = sorted(girdi["acik"], key=("on", "sol", "sag", "arka").index)
    for gecis, cephe, en, icer, oran in itertools.product(
            (600, 300, 0), cepheler, (1600, 1200, 1000), (300, 0), (0.5, 0.25, 0.75, 0.0, 1.0)):
        kenar = W if cephe in ("on", "arka") else D
        if en > kenar - 200:
            continue
        s = (kenar - en) * oran
        m = {"on": {"x": s, "y": D - v["derinlik"] - icer, "yon": "arka"},
             "arka": {"x": s, "y": icer, "yon": "on"},
             "sol": {"x": icer, "y": s, "yon": "sag"},
             "sag": {"x": W - v["derinlik"] - icer, "y": s, "yon": "sol"}}[cephe]
        m = {"tip": "banko", **v, "en": en, **m}
        x, y, w, h = ayak_izi(modul_duzelt(m, girdi))
        if not any(_cakisir((x - gecis, y - gecis, w + 2 * gecis, h + 2 * gecis), k) for k in izler):
            return m
    return None


def kurali_alternatifler(girdi, setler=(), katalog=None):
    W, D = girdi["en"], girdi["derinlik"]
    acik = set(girdi["acik"])
    kapali = [c for c in ("arka", "sol", "sag", "on") if c not in acik]
    V = lambda t: MODULLER[t]["varsayilan"]          # noqa: E731
    cx, cy = W / 2, D / 2
    alt = []

    # 1) Klasik: duvar boyunca raflar + köşede depo; koridorda banko
    m = []
    if kapali:
        m += _duvar_dizisi(girdi, kapali[0], "depo_dolabi", 1)
        m += _duvar_dizisi(girdi, kapali[0], "raf_unitesi", 6, bas=V("depo_dolabi")["en"] + 100)
    else:                                 # ada: ortada sırt sırta depo + raf çekirdeği
        m += [{"tip": "depo_dolabi", **V("depo_dolabi"), "x": cx - 900, "y": cy - 560, "yon": "arka"},
              {"tip": "raf_unitesi", **V("raf_unitesi"), "x": cx - 1000, "y": cy, "yon": "on"}]
        if W >= 4500:
            m += [{"tip": "depo_dolabi", **V("depo_dolabi"), "x": cx, "y": cy - 560, "yon": "arka"},
                  {"tip": "raf_unitesi", **V("raf_unitesi"), "x": cx, "y": cy, "yon": "on"}]
    alt.append({"ad": "Klasik", "konsept": "Kapalı duvar boyunca ürün rafları, köşede kapaklı depo; "
                "koridor tarafında ziyaretçiyi karşılayan banko.", "moduller": m})

    # 2) Açık teşhir: duvarlarda vitrin dolapları, ortada sunum adaları
    m = []
    for c in kapali[:2]:
        m += _duvar_dizisi(girdi, c, "vitrin_dolabi", 4, bas=200)
    ada = V("alt_teshir")
    sayi = 2 if W >= 5000 else 1
    for i in range(sayi):
        m.append({"tip": "alt_teshir", **ada, "x": W * (i + 1) / (sayi + 1) - ada["en"] / 2,
                  "y": cy - ada["derinlik"] / 2, "yon": "on"})
    alt.append({"ad": "Açık teşhir", "konsept": "Duvarlarda alt kapaklı teşhir dolapları, ortada ürün sunum "
                "adaları; ferah, dolaşılabilir bir stand.", "moduller": m})

    # 3) Sade: iki raf ünitesi + depo + banko
    m = []
    if kapali:
        uzun = W if kapali[0] in ("arka", "on") else D
        depo = V("depo_dolabi")["en"]
        if len(kapali) > 1:
            m += _duvar_dizisi(girdi, kapali[0], "raf_unitesi", 2, bas=max(0, (uzun - 2000) / 2))
            m += _duvar_dizisi(girdi, kapali[1], "depo_dolabi", 1)
        else:                             # tek duvar: raflar ortada, depo uçta
            m += _duvar_dizisi(girdi, kapali[0], "raf_unitesi", 2, bas=max(0, (uzun - depo - 2000) / 2), bos=depo)
            m += _duvar_dizisi(girdi, kapali[0], "depo_dolabi", 1, bas=uzun - depo)
    else:
        m.append({"tip": "depo_dolabi", **V("depo_dolabi"), "x": cx - 450, "y": cy - 280, "yon": "arka"})
    alt.append({"ad": "Sade", "konsept": "Az modülle hesaplı stand: karşılama bankosu, teşhir rafları ve bir "
                "depo dolabı. Ziyaretçi alanı geniş.", "moduller": m})

    # Sıra: koridorda banko → duvara yaslanan setler (depo odası, ekran) → duvar modülleri → serbest setler
    duvar_set = [st for st in setler if st["tur"] in ("depo", "ekran", "vitrin")]
    serbest_set = [st for st in setler if st["tur"] not in ("depo", "ekran", "vitrin")]
    for a in alt:                         # kurallı yerleşimde sığmayan modül sessizce düşer
        banko = _banko_yerlestir(girdi, [])
        once = [modul_duzelt(banko, girdi)] if banko else []
        y1, _ = setleri_yerlestir(girdi, duvar_set, once, {}, katalog)
        if (girdi.get("yapi") or {}).get("ikram"):
            ik = _ikram_yer(girdi, once + y1)
            if ik:
                once.append(ik)
        izler = [ayak_izi(k) for k in once + y1]
        mod, _ = yerlesim_duzelt(a["moduller"], girdi)
        a["moduller"] = [m for m in mod if not any(_cakisir(ayak_izi(m), k) for k in izler)] + once
        y2, eksik = setleri_yerlestir(girdi, serbest_set, a["moduller"] + y1, {}, katalog)
        # müşterinin istediği set sığmadıysa ortaya en yakın modülden başlayarak yer aç (banko kalır)
        while eksik and any(m["tip"] != "banko" for m in a["moduller"]):
            sil = min((m for m in a["moduller"] if m["tip"] != "banko"),
                      key=lambda m: math.hypot(m["x"] + ayak_izi(m)[2] / 2 - W / 2, m["y"] + ayak_izi(m)[3] / 2 - D / 2))
            a["moduller"].remove(sil)
            y2b, eksik_b = setleri_yerlestir(girdi, serbest_set, a["moduller"] + y1, {}, katalog)
            if len(eksik_b) < len(eksik):
                y2, eksik = y2b, eksik_b
        a["donatilar"] = [{"set": k["id"], "x": k["x"], "y": k["y"], "yon": k["yon"],
                           "montaj": k.get("montaj", "")} for k in y1 + y2]
    return alt


# ── Yapay zekâ (Claude) ─────────────────────────────────────────────────────
MODEL = os.environ.get("STAND_AI_MODEL", "claude-opus-5-5")

_SEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": ["alternatifler"],
    "properties": {"alternatifler": {"type": "array", "items": {
        "type": "object", "additionalProperties": False,
        "required": ["ad", "konsept", "spot", "grafik_m2", "moduller", "donatilar"],
        "properties": {
            "ad": {"type": "string"}, "konsept": {"type": "string"},
            "spot": {"type": "integer"}, "grafik_m2": {"type": "number"},
            "moduller": {"type": "array", "items": {
                "type": "object", "additionalProperties": False,
                "required": ["tip", "ad", "en", "boy", "derinlik", "raf", "kapak", "x", "y", "yon"],
                "properties": {
                    "tip": {"type": "string", "enum": list(MODULLER)}, "ad": {"type": "string"},
                    "en": {"type": "number"}, "boy": {"type": "number"}, "derinlik": {"type": "number"},
                    "raf": {"type": "integer"}, "kapak": {"type": "integer"},
                    "x": {"type": "number"}, "y": {"type": "number"},
                    "yon": {"type": "string", "enum": list(YONLER)}}}},
            "donatilar": {"type": "array", "items": {
                "type": "object", "additionalProperties": False,
                "required": ["set", "x", "y", "yon", "montaj"],
                "properties": {
                    "set": {"type": "string"}, "x": {"type": "number"}, "y": {"type": "number"},
                    "yon": {"type": "string", "enum": list(YONLER)},
                    "montaj": {"type": "string", "enum": ["duvar", "ayakli", ""]}}}}}}}},
}


def _sistem_istemi():
    tipler = "\n".join(
        f"- {k}: {t['ad']} — {t['aciklama']} en {t['en'][0]}–{t['en'][1]}, boy {t['boy'][0]}–{t['boy'][1]}, "
        f"derinlik {t['derinlik'][0]}–{t['derinlik'][1]} mm, raf {t['raf'][0]}–{t['raf'][1]}, "
        f"kapak {t['kapak'][0]}–{t['kapak'][1]}"
        for k, t in MODULLER.items())
    return f"""Sen fuar standı tasarlayan bir iç mimarsın. Stand modülleri bir mobilya atölyesinde kabin olarak üretilecek.
Kullanılabilecek modül tipleri (ölçüler mm, bu aralıkların dışına çıkma):
{tipler}

Plan koordinatı (mm, üstten bakış): x soldan sağa 0..genişlik, y arka kenardan ön kenara 0..derinlik.
Bir modülün x, y değeri plandaki dikdörtgeninin sol-üst köşesidir. yon, modülün açık / kapaklı yüzünün baktığı yöndür
(on, arka, sol, sag). yon on ya da arka ise plandaki dikdörtgen en × derinlik, sol ya da sag ise derinlik × en'dir.

Kurallar:
- Modüller stand sınırının içinde kalmalı ve birbirine binmemeli.
- Kapalı (duvarlı) kenarlara boy modülleri (raf_unitesi, vitrin_dolabi, depo_dolabi) sırtı duvara gelecek şekilde dizilir;
  yüzleri standın içine bakar. Açık kenarlara (koridor) boy modülü sırtını dönmez, ziyaretçinin görüşünü kapatmaz.
- Karşılama bankosu koridora yakın durur; bankonun raflı yüzü (yon) standın içine, personel tarafına bakar.
- Ziyaretçinin dolaşması için modüller arasında en az 900 mm geçiş bırak. Standın yarısından fazlasını doldurma.
- Her alternatif belirgin biçimde farklı bir fikir olsun (ör. ürün teşhiri ağırlıklı, toplantı / karşılama ağırlıklı, sade ve ekonomik).
- Donatı setleri (masa, sandalye, koltuk, ekran, depo odası…) kullanıcı mesajında id ve plan ölçüsüyle verilir; her setin
  yerini donatilar listesinde ver (set = id). Set ölçüsü değişmez; modüller gibi x, y, yon kuralıyla yerleşir ve hiçbir şeye binmez.
  Ekran duvara asılacaksa (montaj duvar) sırtı duvarlı kenara değmeli ve önünde ziyaretçinin bakacağı boşluk olmalı;
  duvar yoksa montaj ayakli (ayaklı stand, plan derinliği 600 mm olur). Depo odası duvarlı bir köşeye, kapısı (yon) standın içine.
  Toplantı alanı koridordan uzak, sakin tarafta; bekleme koltukları ve bar masaları koridora yakın, davetkâr.
- ad kısa (2–3 kelime), konsept 1–2 cümle, Türkçe. spot: önerilen spot aydınlatma sayısı. grafik_m2: logo / grafik baskı alanı.
Tam olarak 3 alternatif üret."""


def _kullanici_istemi(girdi, setler=()):
    acik = ", ".join(CEPHE_AD[c] for c in girdi["acik"])
    kapali = ", ".join(CEPHE_AD[c] for c in CEPHELER if c not in girdi["acik"]) or "yok"
    s = (f"Stand: genişlik {girdi['en']:g} mm, derinlik {girdi['derinlik']:g} mm, duvar yüksekliği {girdi['yukseklik']:g} mm.\n"
         f"{stand_tipi(girdi['acik'])}. Açık (koridora bakan) cepheler: {acik}. Duvarlı cepheler: {kapali}.")
    if girdi.get("sektor"):
        s += f"\nFirmanın sektörü / ürünleri: {girdi['sektor']}"
    if girdi.get("istek"):
        s += f"\nMüşterinin isteği: {girdi['istek']}"
    if (girdi.get("yapi") or {}).get("ikram"):
        s += "\nMüşteri ikram / mutfak köşesi istedi: bir ikram_tezgahi modülünü duvarlı bir kenara, sakin tarafa koy."
    if setler:
        s += "\nYerleştirilecek donatı setleri (plan ölçüsü yon on iken en × derinlik):"
        for st in setler:
            s += f"\n- {st['id']}: {st['ad']}, {st['en']:g} × {st['derinlik']:g} mm"
    else:
        s += "\nDonatı seti istenmedi (donatilar boş liste)."
    return s


async def ai_alternatifler(girdi, anahtar, setler=()):
    """Claude'dan 3 alternatif ister. Döner: ham alternatif listesi. Hata: istisna (çağıran kurallıya düşer)."""
    import anthropic
    client = anthropic.AsyncAnthropic(api_key=anahtar, timeout=120.0, max_retries=1)
    r = await client.beta.messages.create(
        model=MODEL, max_tokens=16000,
        betas=["server-side-fallback-2026-07-01"], fallbacks="default",
        output_config={"effort": "low", "format": {"type": "json_schema", "schema": _SEMA}},
        system=_sistem_istemi(),
        messages=[{"role": "user", "content": _kullanici_istemi(girdi, setler)}],
    )
    if r.stop_reason == "refusal":
        raise RuntimeError("Yapay zekâ isteği geri çevirdi")
    if r.stop_reason == "max_tokens":
        raise RuntimeError("Yapay zekâ cevabı yarım kaldı")
    metin = next((b.text for b in r.content if b.type == "text"), "")
    return json.loads(metin)["alternatifler"]


async def alternatifler_uret(girdi, anahtar=None, katalog=None):
    """Döner: (alternatifler, kaynak 'ai' | 'kural', not). Yapay zekâ başarısızsa kurallı üretilir."""
    katalog = katalog if katalog is not None else SD.varsayilan_katalog()
    setler = SD.setler(girdi.get("donati") or {}, katalog, girdi)
    duzelt = lambda h: alternatif_duzelt(h, girdi, setler, katalog)   # noqa: E731
    neden = ""
    if anahtar:
        try:
            ham = await ai_alternatifler(girdi, anahtar, setler)
            alt = [a for a in (duzelt(h) for h in ham[:4]) if a]
            if alt:
                return alt, "ai", ""
            neden = "yapay zekâ kullanılabilir yerleşim vermedi"
        except Exception as e:          # ağ, anahtar, kota, biçim: müşteri yine de tasarım görür
            log.warning("stand yapay zekâ hatası: %s", type(e).__name__)
            neden = "yapay zekâ şu an kullanılamadı"
    alt = [a for a in (duzelt(h) for h in kurali_alternatifler(girdi, setler, katalog)) if a]
    return alt, "kural", neden


# ── Motor tarifleri ─────────────────────────────────────────────────────────
def tarif(m):
    """Modül → hazir_katalog tarif biçimi ({ad, sablon, adimlar})."""
    t = MODULLER[m["tip"]]
    a = [olcu(m["en"], m["boy"], m["derinlik"])]
    tip = m["tip"]
    if tip == "banko":
        a.append(USTLU)
        if m["en"] > 1300:
            a += [dikey("hepsi", 1), raf("sol", m["raf"]), raf("sag", m["raf"])]
        else:
            a.append(raf("hepsi", m["raf"]))
    elif tip == "alt_teshir":
        a.append(USTLU)
        if m["raf"]:
            a.append(raf("hepsi", m["raf"]))
        if m["kapak"]:
            a.append(kapak("*", m["kapak"]))
    elif tip == "raf_unitesi":
        a.append(raf("hepsi", m["raf"]))
    elif tip == "ikram_tezgahi":
        a += [USTLU, raf("hepsi", 1), kapak("*", m["kapak"], None if m["kapak"] > 1 else "SAG")]
    elif tip == "vitrin_dolabi":
        a += [yatay("hepsi", 1), goz("hepsi:alt", "y", 800), raf("hepsi:ust", m["raf"]),
              kapak("hepsi:alt", m["kapak"], None if m["kapak"] > 1 else "SAG")]
    elif tip == "depo_dolabi":
        a += [raf("hepsi", m["raf"]), kapak("*", m["kapak"], None if m["kapak"] > 1 else "SAG")]
    return {"ad": m["ad"], "sablon": t["sablon"], "adimlar": a}


def modul_anahtari(m):
    return (m["tip"], m["en"], m["boy"], m["derinlik"], m["raf"], m["kapak"])


def gruplar(moduller):
    """Aynı ölçü ve donanımdaki modüller tek kabin satırı olur (adet). Döner: [(temsilci, [indeksler])]."""
    g = {}
    for i, m in enumerate(moduller):
        g.setdefault(modul_anahtari(m), (m, []))[1].append(i)
    return list(g.values())


def ekstra_fiyatla(kalemler, birim):
    """Birim fiyatlar (yönetici) ile tutar. Fiyatı girilmeyen kalem 'eksik' sayılır."""
    out, eksik = [], False
    for k in kalemler:
        bf = _sayi(k["fiyat"], 0) if "fiyat" in k else _sayi((birim or {}).get(k["kod"]), 0)
        eksik |= bf <= 0
        out.append({**k, "birim_fiyat": bf, "tutar": round(k["miktar"] * bf, 2)})
    return out, eksik


# ── Plan çizimi (SVG, sunucuda) ─────────────────────────────────────────────
def _e(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;"))


def _parca_svg(p, fs):
    """Donatı parçası (merkezde, öne +y bakar) — üstten görünüş."""
    w, h, renk, m = p["en"], p["derinlik"], p["renk"], p["model"]
    koyu = renk.lower() in ("#0b0f14", "#111827", "#1f2937")
    cizgi = "#334155"
    sw = fs / 12
    if m in ("masa_yuvarlak",):
        sekil = f'<circle r="{w / 2:g}" fill="#ffffff" stroke="{cizgi}" stroke-width="{sw:g}"/>'
    elif m == "masa_kare":
        sekil = f'<rect x="{-w / 2:g}" y="{-h / 2:g}" width="{w:g}" height="{h:g}" rx="{fs / 8:g}" fill="#ffffff" stroke="{cizgi}" stroke-width="{sw:g}"/>'
    elif m in ("sandalye", "tabure", "berjer", "kanepe"):
        r = min(w, h) / (2 if m == "tabure" else 5)
        sekil = (f'<rect x="{-w / 2:g}" y="{-h / 2:g}" width="{w:g}" height="{h:g}" rx="{r:g}" fill="{renk}" '
                 f'fill-opacity="{0.9 if koyu else 0.75}" stroke="{cizgi}" stroke-width="{sw:g}"/>')
        if m != "tabure":                 # arkalık (arka kenar)
            sekil += f'<rect x="{-w / 2:g}" y="{-h / 2:g}" width="{w:g}" height="{h * 0.22:g}" rx="{r:g}" fill="{cizgi}" fill-opacity=".55"/>'
        if m in ("berjer", "kanepe"):     # kolçaklar
            k = w * (0.14 if m == "berjer" else 0.08)
            sekil += (f'<rect x="{-w / 2:g}" y="{-h / 2:g}" width="{k:g}" height="{h:g}" rx="{r:g}" fill="{cizgi}" fill-opacity=".35"/>'
                      f'<rect x="{w / 2 - k:g}" y="{-h / 2:g}" width="{k:g}" height="{h:g}" rx="{r:g}" fill="{cizgi}" fill-opacity=".35"/>')
    elif m in ("tv", "led"):
        kal = max(h, fs / 2.2)
        sekil = f'<rect x="{-w / 2:g}" y="{-kal / 2:g}" width="{w:g}" height="{kal:g}" rx="{fs / 12:g}" fill="#0b0f14"/>'
        if p.get("montaj") != "ayakli":
            sekil = f'<g transform="translate(0 {-(150 - kal) / 2:g})">{sekil}</g>'
    elif m == "tv_standi":
        sekil = (f'<rect x="{-w / 2:g}" y="{-h / 2:g}" width="{w:g}" height="{h:g}" rx="{fs / 8:g}" fill="#94a3b8" fill-opacity=".35" stroke="{cizgi}" stroke-width="{sw:g}"/>')
    elif m == "depo":
        kapi = min(800, w - 100)
        sekil = (f'<rect x="{-w / 2:g}" y="{-h / 2:g}" width="{w:g}" height="{h:g}" fill="#e2e8f0" stroke="{cizgi}" stroke-width="{fs / 5:g}"/>'
                 f'<line x1="{-kapi / 2:g}" y1="{h / 2:g}" x2="{kapi / 2:g}" y2="{h / 2:g}" stroke="#e2e8f0" stroke-width="{fs / 4:g}"/>'
                 f'<path d="M {-kapi / 2:g} {h / 2:g} L {-kapi / 2:g} {h / 2 - kapi:g} A {kapi:g} {kapi:g} 0 0 1 {kapi / 2:g} {h / 2:g}" fill="none" stroke="{cizgi}" stroke-width="{sw:g}" stroke-dasharray="{fs / 4:g} {fs / 6:g}"/>'
                 f'<text y="{fs * 0.1:g}" font-size="{fs * 0.55:g}" fill="#475569" text-anchor="middle">DEPO</text>')
    elif m == "bitki":
        sekil = (f'<circle r="{w / 2:g}" fill="#16a34a" fill-opacity=".75"/>'
                 f'<circle r="{w / 4:g}" fill="#15803d"/>')
    else:                                 # broşürlük
        sekil = f'<rect x="{-w / 2:g}" y="{-h / 2:g}" width="{w:g}" height="{h:g}" fill="#cbd5e1" stroke="{cizgi}" stroke-width="{sw:g}"/>'
    return (f'<g transform="translate({p["x"]:g} {p["y"]:g}) rotate({p["aci"]:g})"><title>{_e(p["ad"])}</title>{sekil}</g>')


def plan_svg(girdi, moduller, buyuk=False, donatilar=()):
    """Üstten plan: duvarlar (koyu), koridorlar, modüller (ön yüz çizgisiyle) ve donatılar."""
    W, D = girdi["en"], girdi["derinlik"]
    pay = max(W, D) * 0.09
    fs = max(W, D) / (38 if buyuk else 30)
    p = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{-pay:g} {-pay:g} {W + 2 * pay:g} {D + 2 * pay:g}" '
         f'class="w-full h-auto" role="img" aria-label="Stand planı">',
         f'<rect x="0" y="0" width="{W:g}" height="{D:g}" fill="#f8fafc" stroke="#cbd5e1" stroke-width="{fs / 8:g}" stroke-dasharray="{fs / 2:g} {fs / 3:g}"/>']
    kal = max(W, D) / 70
    kenar = {"arka": (0, -kal, W, kal), "on": (0, D, W, kal), "sol": (-kal, -kal, kal, D + 2 * kal),
             "sag": (W, -kal, kal, D + 2 * kal)}
    for c, (x, y, w, h) in kenar.items():
        if c in girdi["acik"]:
            tx, ty = {"arka": (W / 2, -pay * 0.35), "on": (W / 2, D + pay * 0.65),
                      "sol": (-pay * 0.45, D / 2), "sag": (W + pay * 0.45, D / 2)}[c]
            rot = f' transform="rotate(-90 {tx:g} {ty:g})"' if c in ("sol", "sag") else ""
            p.append(f'<text x="{tx:g}" y="{ty:g}" font-size="{fs * 0.8:g}" fill="#94a3b8" text-anchor="middle"{rot}>koridor</text>')
        else:
            p.append(f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" fill="#334155"/>')
    for m in moduller:
        x, y, w, h = ayak_izi(m)
        renk = MODULLER[m["tip"]]["renk"]
        p.append(f'<g><title>{_e(m["ad"])} · {m["en"]:g}×{m["boy"]:g}×{m["derinlik"]:g} mm</title>'
                 f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" rx="{fs / 6:g}" fill="{renk}" fill-opacity=".18" stroke="{renk}" stroke-width="{fs / 7:g}"/>')
        # ön yüz çizgisi
        c = {"on": (x, y + h, x + w, y + h), "arka": (x, y, x + w, y), "sol": (x, y, x, y + h), "sag": (x + w, y, x + w, y + h)}[m["yon"]]
        p.append(f'<line x1="{c[0]:g}" y1="{c[1]:g}" x2="{c[2]:g}" y2="{c[3]:g}" stroke="{renk}" stroke-width="{fs / 2.5:g}" stroke-linecap="round"/></g>')
    for st in donatilar:
        for parca in SD.parcalar_plan(st):
            p.append(_parca_svg(parca, fs))
    yapi = girdi.get("yapi") or {}
    if yapi.get("truss"):
        t = 150
        p.append(f'<rect x="{t:g}" y="{t:g}" width="{W - 2 * t:g}" height="{D - 2 * t:g}" fill="none" stroke="#94a3b8" '
                 f'stroke-width="{fs / 5:g}" stroke-dasharray="{fs / 1.5:g} {fs / 3:g}"><title>Truss tavan</title></rect>')
    if yapi.get("banner"):
        r = min(3000, min(W, D) * 0.6) / 2
        p.append(f'<circle cx="{W / 2:g}" cy="{D / 2:g}" r="{r:g}" fill="none" stroke="#059669" stroke-width="{fs / 6:g}" '
                 f'stroke-dasharray="{fs / 2:g} {fs / 4:g}"><title>Askılı banner (tavanda)</title></circle>')
    p.append(f'<text x="{W:g}" y="{D + pay * 0.65:g}" font-size="{fs * 0.75:g}" fill="#64748b" text-anchor="end">{W / 1000:g} × {D / 1000:g} m</text>' if "on" not in girdi["acik"] else
             f'<text x="{W:g}" y="{-pay * 0.35:g}" font-size="{fs * 0.75:g}" fill="#64748b" text-anchor="end">{W / 1000:g} × {D / 1000:g} m</text>')
    p.append("</svg>")
    return "".join(p)


def ozet_metin(stand):
    """Sipariş notu / API için okunur stand özeti."""
    g, alt = stand["girdi"], stand["alternatif"]
    satir = [f"FUAR STANDI #{stand['id']} — {alt['ad']}",
             f"Ölçü: {g['en'] / 1000:g} × {g['derinlik'] / 1000:g} m, duvar yüksekliği {g['yukseklik'] / 1000:g} m · "
             f"{stand_tipi(g['acik'])} · açık cepheler: {', '.join(CEPHE_AD[c] for c in g['acik'])}"]
    if (g.get("yapi") or {}).get("logo"):
        satir.append(f"Logo dosyası: /stand/logo/{g['yapi']['logo']}")
    for k in alt["ekstralar"]:
        satir.append(f"- {k['ad']}: {k['miktar']:g} {k['birim']}" + (f" ({k['detay']})" if k.get("detay") else ""))
    return "\n".join(satir)
