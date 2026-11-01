"""Hazır kabin kataloğu: online mağazalarda en çok satılan dolap tiplerinin TitaniumCam motoruyla
kurulmuş tarifleri (komodin, şifonyer, TV ünitesi, gardırop, kitaplık, ayakkabılık, mutfak, banyo).

Ölçüler, Türkiye'deki büyük mobilya sitelerinde yaygın satılan ürünlerin ölçü aralıklarından seçildi
(komodin 45–50 cm genişlik / 45 cm derinlik — en kısa çekmece rayı 40 cm'e sığmaz —, şifonyer 80 cm, TV ünitesi 120 / 150 / 180 cm, ...).
Bütün hesap motordadır; burada yalnız adımlar vardır. Yönetici: Kabinler → Hazır katalog.

Adım biçimi: motor işlemi (dict). "hedef" verilirse bölme seçicisinden nokta hesaplanır:
  "hepsi"          tek / en büyük bölme
  "sol" "orta" "sag"   sütun (soldan sağa); "sol:ust" / "sag:alt" sütundaki en üst / en alt bölme
  "*"              bütün bölmeler (kapak bütün dolabı kapatır)
"""
import re

KULP = {"sablon": "Ust orta 128"}
AYAK = lambda h: {"tip": "ozellik", "alanlar": {"ayaktipi": "AYAK", "ayakyuksekligi": h}}  # noqa: E731
GOVDE = lambda kod: {"tip": "ozellik", "alanlar": {"govde.malzeme": kod}}               # noqa: E731
ONLER = lambda kod: {"tip": "parca", "idler": ["tum:on"], "alanlar": {"malzeme": kod}}  # noqa: E731


USTLU = {"tip": "ozellik", "alanlar": {"paneller.ust": True}}   # mobilya: üst tabla (mutfak alt dolabında tezgâh olur)


def raf(hedef, n=1):
    return {"tip": "ekle", "tur": "raf", "hedef": hedef, "adet": n}


def dikey(hedef, n=1):
    return {"tip": "ekle", "tur": "dikeybolme", "hedef": hedef, "adet": n}


def yatay(hedef, n=1):
    return {"tip": "ekle", "tur": "yataybolme", "hedef": hedef, "adet": n}


def kapak(hedef, n=1, yon=None):
    d = {"tip": "ekle", "tur": "kapak", "hedef": hedef, "adet": n, "kulp": KULP}
    if yon:
        d["menteseler"] = [yon]
    return d


def cekmece(hedef, n):
    return {"tip": "ekle", "tur": "cekmece", "hedef": hedef, "adet": n, "sablon": "Mandalli 500", "ray": "oto", "kulp": KULP}


def goz(hedef, yon, deger):
    """Bölme ölçüsü: hedef bölmenin genişliği (x) ya da yüksekliği (y) mm."""
    return {"tip": "goz_olcu", "hedef": hedef, "yon": yon, "deger": deger}


def olcu(en, boy, derinlik):
    return {"tip": "olcu", "en": en, "boy": boy, "derinlik": derinlik}


MODELLER = [
    # ── Yatak odası ──
    {"ad": "2 Çekmeceli Komodin", "kategori": "Yatak odası", "sablon": "Alt Dolap",
     "aciklama": "Yatak başı için kompakt komodin. 50 cm genişlik, iki geniş çekmece.",
     "adimlar": [olcu(500, 550, 450), AYAK(100), USTLU, cekmece("hepsi", 2), GOVDE("SUNTA18")]},
    {"ad": "3 Çekmeceli Komodin", "kategori": "Yatak odası", "sablon": "Alt Dolap",
     "aciklama": "Dar alanlar için 45 cm genişliğinde, üç çekmeceli komodin.",
     "adimlar": [olcu(450, 650, 450), AYAK(100), USTLU, cekmece("hepsi", 3)]},
    {"ad": "4 Çekmeceli Şifonyer", "kategori": "Yatak odası", "sablon": "Alt Dolap",
     "aciklama": "80 cm genişlik, dört derin çekmece. Yatak odasının vazgeçilmezi.",
     "adimlar": [olcu(800, 950, 450), AYAK(100), USTLU, cekmece("hepsi", 4), GOVDE("SUNTA18")]},
    {"ad": "5 Çekmeceli Dar Şifonyer", "kategori": "Yatak odası", "sablon": "Alt Dolap",
     "aciklama": "60 cm genişliğinde, beş çekmeceli şifonyer.",
     "adimlar": [olcu(600, 1150, 450), AYAK(100), USTLU, cekmece("hepsi", 5)]},
    {"ad": "2 Kapaklı Gardırop", "kategori": "Yatak odası", "sablon": "Boy Dolap",
     "aciklama": "100 cm genişlik, üstte raf, altta askı bölümü; iki kapaklı.",
     "adimlar": [olcu(1000, 2000, 580), AYAK(100), raf("hepsi", 1), goz("hepsi:alt", "y", 1450),
                 kapak("*", 2)]},
    {"ad": "3 Kapaklı Gardırop", "kategori": "Yatak odası", "sablon": "Boy Dolap",
     "aciklama": "135 cm genişlik: iki kapaklı askılık + tek kapaklı raflı bölüm.",
     "adimlar": [olcu(1350, 2000, 580), AYAK(100), dikey("hepsi", 1), goz("sol", "x", 880),
                 raf("sol", 1), goz("sol:alt", "y", 1450), raf("sag", 4),
                 kapak("sol:*", 2), kapak("sag:*", 1, "SAG")]},
    # ── Salon ──
    {"ad": "TV Ünitesi 180 cm", "kategori": "Salon", "sablon": "Alt Dolap",
     "aciklama": "İki yanda kapaklı dolap, ortada açık raflı bölme. En çok tercih edilen 180 cm.",
     "adimlar": [olcu(1800, 500, 400), AYAK(100), USTLU, dikey("hepsi", 2), raf("orta", 1),
                 kapak("sol", 1, "SOL"), kapak("sag", 1, "SAG"), GOVDE("SUNTA18")]},
    {"ad": "TV Ünitesi 150 cm Çekmeceli", "kategori": "Salon", "sablon": "Alt Dolap",
     "aciklama": "150 cm; iki kapak ve ortada çekmece.",
     "adimlar": [olcu(1500, 500, 450), AYAK(100), USTLU, dikey("hepsi", 2), cekmece("orta", 1),
                 kapak("sol", 1, "SOL"), kapak("sag", 1, "SAG")]},
    {"ad": "TV Ünitesi 120 cm", "kategori": "Salon", "sablon": "Alt Dolap",
     "aciklama": "Küçük salonlar için 120 cm; bir kapaklı, bir açık raflı bölüm.",
     "adimlar": [olcu(1200, 450, 400), AYAK(100), USTLU, dikey("hepsi", 1), kapak("sol", 1, "SOL"),
                 raf("sag", 1), GOVDE("SUNTA18")]},
    {"ad": "Kitaplık 5 Raflı", "kategori": "Salon", "sablon": "Boy Dolap",
     "aciklama": "80 × 180 cm, 30 cm derinlikte açık kitaplık.",
     "adimlar": [olcu(800, 1800, 300), AYAK(60), raf("hepsi", 4), GOVDE("SUNTA18")]},
    {"ad": "Alt Kapaklı Kitaplık", "kategori": "Salon", "sablon": "Boy Dolap",
     "aciklama": "Üstte açık raflar, altta kapaklı dolap.",
     "adimlar": [olcu(800, 1800, 300), AYAK(60), yatay("hepsi", 1), goz("hepsi:alt", "y", 700),
                 raf("hepsi:ust", 2), kapak("hepsi:alt", 2)]},
    # ── Antre ──
    {"ad": "Ayakkabılık 2 Kapaklı", "kategori": "Antre", "sablon": "Alt Dolap",
     "aciklama": "80 cm genişlik, 35 cm derinlik; üç raflı ayakkabılık.",
     "adimlar": [olcu(800, 1000, 350), AYAK(80), USTLU, raf("hepsi", 3), kapak("*", 2)]},
    # ── Mutfak ──
    {"ad": "Mutfak Alt Dolap 80 cm", "kategori": "Mutfak", "sablon": "Alt Dolap",
     "aciklama": "İki kapaklı, raflı standart mutfak alt dolabı.",
     "adimlar": [olcu(800, 860, 560), raf("hepsi", 1), kapak("*", 2)]},
    {"ad": "Mutfak Çekmeceli Alt Dolap 60 cm", "kategori": "Mutfak", "sablon": "Alt Dolap",
     "aciklama": "Üç çekmeceli mutfak alt dolabı.",
     "adimlar": [olcu(600, 860, 560), cekmece("hepsi", 3)]},
    {"ad": "Mutfak Üst Dolap 80 cm", "kategori": "Mutfak", "sablon": "Ust Dolap",
     "aciklama": "İki kapaklı, raflı mutfak üst dolabı.",
     "adimlar": [olcu(800, 720, 320), raf("hepsi", 1), kapak("*", 2)]},
    {"ad": "Kiler Dolabı 60 cm", "kategori": "Mutfak", "sablon": "Boy Dolap",
     "aciklama": "Beş raflı, tek kapaklı boy kiler dolabı.",
     "adimlar": [olcu(600, 2100, 560), raf("hepsi", 4), kapak("*", 1, "SAG")]},
    # ── Banyo ──
    {"ad": "Banyo Boy Dolabı", "kategori": "Banyo", "sablon": "Boy Dolap",
     "aciklama": "40 cm genişlik, 30 cm derinlik; dar banyo boy dolabı.",
     "adimlar": [olcu(400, 1700, 300), AYAK(60), raf("hepsi", 4), kapak("*", 1, "SAG")]},
]


# ── bölme seçici ───────────────────────────────────────────────────────────
def _merkez(z):
    return [(z["x1"] + z["x2"]) / 2, (z["y1"] + z["y2"]) / 2]


def _sutunlar(gozler):
    s = {}
    for z in gozler:
        s.setdefault((round(z["x1"]), round(z["x2"])), []).append(z)
    return [s[k] for k in sorted(s)]


def secici(view, hedef):
    """Seçiciden motor noktaları (2B: x soldan, y üstten). Döner: [[x, y], ...]."""
    gozler = view["gozler"]
    if not gozler:
        raise ValueError("Bölme yok")
    if hedef == "*":
        return [_merkez(z) for z in gozler]
    sutun_ad, _, satir = hedef.partition(":")
    sut = _sutunlar(gozler)
    if sutun_ad == "hepsi":
        grup = gozler
    else:
        i = {"sol": 0, "sag": len(sut) - 1, "orta": len(sut) // 2}.get(sutun_ad)
        if i is None:
            raise ValueError(f"Bilinmeyen seçici: {hedef}")
        grup = sut[i]
    if satir == "*":
        return [_merkez(z) for z in grup]
    if satir == "ust":
        z = min(grup, key=lambda z: z["y1"])
    elif satir == "alt":
        z = max(grup, key=lambda z: z["y1"])
    else:
        z = max(grup, key=lambda z: (z["x2"] - z["x1"]) * (z["y2"] - z["y1"]))
    return [_merkez(z)]


async def kur(model, islem, ark=None, sab=None):
    """Tarifi motorda adım adım kurar. islem(kabin, islemler) -> view. Döner: view."""
    view = await islem(None, [{"tip": "yeni", "sablon": model["sablon"]}])
    if ark:
        g = view["govde"]
        view = await islem(view["kabin"], [{"tip": "ekle", "tur": "arkalik", "sablon": ark,
                                            "noktalar": [[g["en"] / 2, min(100.0, g["boy"] / 4)]]}])
    for a in model["adimlar"]:
        a = dict(a)
        hedef = a.pop("hedef", None)
        if a.get("tur") == "cekmece" and a.pop("ray", None) == "oto":
            v = await islem(view["kabin"], [{"tip": "ray_kontrol", "sablon": a["sablon"]}])
            uygun = [k for k, ok in v["sonuc"]["raylar"].items() if ok]
            if not uygun:
                raise ValueError(f"{model['ad']}: bu derinliğe uygun çekmece rayı yok")
            a["secenek"] = {"raytipi": uygun[0]}
        if hedef:
            n = secici(view, hedef)
            if a["tip"] == "goz_olcu":
                a["nokta"] = n[0]
            else:
                a["noktalar"] = n
        view = await islem(view["kabin"], [a])
    return view


def kod_ad(ad):
    return re.sub(r"\s+", " ", ad).strip()
