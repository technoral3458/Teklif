# -*- coding: utf-8 -*-
"""Tanıtım videosu için demo görseller: fişler, teknik çizimler, imza."""
import math, os, random
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")
os.makedirs(OUT, exist_ok=True)
random.seed(7)

FONT_DIRS = ["/usr/share/fonts", "/usr/local/share/fonts"]

def find_font(*names):
    for d in FONT_DIRS:
        for root, _, files in os.walk(d):
            for f in files:
                for n in names:
                    if f.lower() == n.lower():
                        return os.path.join(root, f)
    return None

SANS = find_font("DejaVuSans.ttf", "LiberationSans-Regular.ttf")
SANS_B = find_font("DejaVuSans-Bold.ttf", "LiberationSans-Bold.ttf")
MONO = find_font("DejaVuSansMono.ttf", "LiberationMono-Regular.ttf") or SANS
MONO_B = find_font("DejaVuSansMono-Bold.ttf", "LiberationMono-Bold.ttf") or SANS_B
print("fonts:", SANS, MONO)

def f(path, size):
    return ImageFont.truetype(path, size) if path else ImageFont.load_default()

def tr_money(v):
    s = f"{v:,.2f}".replace(",", "@").replace(".", ",").replace("@", ".")
    return s

# ---------------------------------------------------------------- fişler
def receipt(name, title, lines, total, date, w=620, extra=None):
    h = 300 + 46 * len(lines) + (120 if extra else 0)
    img = Image.new("RGB", (w, h), (247, 245, 240))
    d = ImageDraw.Draw(img)
    # kağıt dokusu
    for i in range(2600):
        x, y = random.randrange(w), random.randrange(h)
        c = random.randint(232, 250)
        d.point((x, y), fill=(c, c - 2, c - 6))
    # üst/alt tırtıklı kenar
    for x in range(0, w, 18):
        d.polygon([(x, 0), (x + 9, 13), (x + 18, 0)], fill=(255, 255, 255))
        d.polygon([(x, h), (x + 9, h - 13), (x + 18, h)], fill=(255, 255, 255))
    fb, fr, fm = f(MONO_B, 30), f(MONO, 23), f(MONO_B, 25)
    d.text((w // 2, 44), title, font=fb, fill=(28, 28, 32), anchor="mt")
    d.text((w // 2, 86), name, font=f(MONO, 20), fill=(90, 90, 96), anchor="mt")
    d.line([(36, 124), (w - 36, 124)], fill=(150, 150, 155), width=2)
    y = 150
    for label, amount in lines:
        d.text((40, y), label[:26], font=fr, fill=(40, 40, 46))
        d.text((w - 40, y), tr_money(amount), font=fr, fill=(40, 40, 46), anchor="rt")
        y += 46
    d.line([(36, y + 8), (w - 36, y + 8)], fill=(150, 150, 155), width=2)
    y += 28
    d.text((40, y), "TOPLAM", font=fm, fill=(20, 20, 24))
    d.text((w - 40, y), tr_money(total) + " TL", font=fm, fill=(20, 20, 24), anchor="rt")
    y += 56
    if extra:
        for line in extra:
            d.text((40, y), line, font=f(MONO, 19), fill=(95, 95, 100))
            y += 30
        y += 10
    d.text((40, y), date, font=f(MONO, 19), fill=(110, 110, 115))
    d.text((w - 40, y), "FİŞ NO: " + str(random.randint(100000, 999999)),
           font=f(MONO, 19), fill=(110, 110, 115), anchor="rt")
    # barkod
    bx = 60
    by = y + 44
    while bx < w - 60:
        bw = random.choice([3, 3, 5, 8])
        d.rectangle([bx, by, bx + bw, by + 42], fill=(35, 35, 40))
        bx += bw + random.choice([4, 6, 9])
    img.save(os.path.join(OUT, f"fis_{len(os.listdir(OUT))+1}_{title.split()[0].lower()}.png"))
    return img

receipt("ORGANİZE SAN. BÖL. / BURSA", "GRAND OTEL", [("Oda - 1 gece", 1180.00), ("Kahvaltı", 160.00), ("KDV %10", 134.00)], 1474.00, "17.09.2026 08:12")
receipt("AKARYAKIT İST. - İNEGÖL", "PETROL", [("Motorin 42,30 L", 2031.26), ("", 0.0)][:1], 2031.26, "16.09.2026 06:41", extra=["Litre fiyatı: 48,02 TL", "Plaka: 16 KDR 34"])
receipt("KUZEY MARMARA OTOYOLU", "HGS GEÇİŞ", [("Gişe 12 - Gişe 27", 184.50)], 184.50, "16.09.2026 07:55")
receipt("MARKET - İNEGÖL ŞUBESİ", "MARKET", [("Su 5 L x2", 48.00), ("Sandviç x2", 170.00), ("Kahve x2", 110.00)], 328.00, "16.09.2026 12:30")

# ---------------------------------------------- teknik çizimler (fotoğraf yerine)
def tech_plate(filename, kind, badge):
    W, H = 900, 680
    img = Image.new("RGB", (W, H), (14, 22, 31))
    d = ImageDraw.Draw(img)
    for y in range(H):  # degrade
        t = y / H
        d.line([(0, y), (W, y)], fill=(int(14 + 10 * t), int(22 + 14 * t), int(31 + 20 * t)))
    for x in range(0, W, 45):   # ızgara
        d.line([(x, 0), (x, H)], fill=(255, 255, 255, 8) if False else (24, 36, 48))
    for y in range(0, H, 45):
        d.line([(0, y), (W, y)], fill=(24, 36, 48))
    line = (182, 214, 236)
    acc = (255, 185, 92)
    red = (226, 59, 78)
    cx, cy = W // 2, H // 2 + 10

    if kind == "bearing":   # çatlak rulman
        for r, wd in ((250, 5), (205, 3), (120, 3), (82, 5)):
            d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=line, width=wd)
        for i in range(12):
            a = i * math.pi / 6
            bx, by = cx + 162 * math.cos(a), cy + 162 * math.sin(a)
            d.ellipse([bx - 30, by - 30, bx + 30, by + 30], outline=line, width=3)
        pts = [(cx + 205, cy - 10), (cx + 232, cy - 34), (cx + 214, cy - 52), (cx + 248, cy - 78)]
        d.line(pts, fill=red, width=7, joint="curve")
        d.ellipse([cx + 196, cy - 24, cx + 260, cy + 40], outline=red, width=4)
    elif kind == "motor":   # motor + kablolama
        d.rounded_rectangle([cx - 230, cy - 130, cx + 150, cy + 130], 22, outline=line, width=5)
        for i in range(7):
            x = cx - 200 + i * 52
            d.line([(x, cy - 118), (x, cy + 118)], fill=(70, 104, 132), width=3)
        d.rounded_rectangle([cx + 150, cy - 55, cx + 230, cy + 55], 10, outline=line, width=4)
        d.line([(cx + 230, cy), (cx + 300, cy)], fill=acc, width=6)
        for i, off in enumerate((-60, 0, 60)):
            d.arc([cx - 300, cy + off - 40, cx - 180, cy + off + 40], 90, 270, fill=acc, width=5)
        d.ellipse([cx - 60, cy - 60, cx + 60, cy + 60], outline=acc, width=4)
    elif kind == "board":   # elektronik kart
        d.rounded_rectangle([90, 110, W - 90, H - 110], 16, outline=line, width=5)
        for i in range(9):
            y = 170 + i * 42
            d.line([(130, y), (W - 230, y)], fill=(70, 110, 140), width=3)
            d.ellipse([W - 226, y - 7, W - 212, y + 7], outline=line, width=3)
        d.rounded_rectangle([150, 250, 340, 420], 8, outline=acc, width=4)
        d.text((158, 258), "PLC", font=f(SANS_B, 26), fill=acc)
        for i in range(6):
            d.rectangle([400 + i * 60, 300, 440 + i * 60, 360], outline=line, width=3)
        d.ellipse([W - 300, H - 230, W - 230, H - 160], outline=red, width=5)
        d.line([(W - 296, H - 226), (W - 234, H - 164)], fill=red, width=5)
    elif kind == "part":    # yedek parça
        d.rounded_rectangle([cx - 190, cy - 110, cx + 190, cy + 110], 18, outline=line, width=5)
        d.ellipse([cx - 120, cy - 70, cx + 20, cy + 70], outline=line, width=4)
        d.ellipse([cx - 80, cy - 30, cx - 20, cy + 30], outline=acc, width=4)
        for i in range(5):
            d.line([(cx + 60, cy - 70 + i * 35), (cx + 160, cy - 70 + i * 35)], fill=line, width=3)
        d.text((cx - 180, cy + 130), "6208-2RS / SKF", font=f(MONO, 24), fill=(140, 170, 194))

    # köşe çerçeveleri + rozet
    for (ox, oy, sx, sy) in ((40, 40, 1, 1), (W - 40, 40, -1, 1), (40, H - 40, 1, -1), (W - 40, H - 40, -1, -1)):
        d.line([(ox, oy), (ox + 46 * sx, oy)], fill=(120, 160, 190), width=4)
        d.line([(ox, oy), (ox, oy + 46 * sy)], fill=(120, 160, 190), width=4)
    bf = f(SANS_B, 28)
    tw = d.textlength(badge, font=bf)
    d.rounded_rectangle([40, H - 110, 40 + tw + 44, H - 54], 14, fill=(15, 76, 117))
    d.text((62, H - 82), badge, font=bf, fill=(236, 244, 250), anchor="lm")
    img.save(os.path.join(OUT, filename))

tech_plate("foto_ariza.png", "bearing", "ARIZA")
tech_plate("foto_oncesi.png", "board", "İŞLEM ÖNCESİ")
tech_plate("foto_sonrasi.png", "motor", "İŞLEM SONRASI")
tech_plate("foto_parca.png", "part", "PARÇA")

# ---------------------------------------------------------------- imza
def signature():
    W, H = 760, 300
    img = Image.new("RGB", (W, H), (255, 255, 255))
    d = ImageDraw.Draw(img)
    ink = (22, 34, 58)
    def stroke(pts, width=7):
        sm = []
        for i in range(len(pts) - 1):
            x0, y0 = pts[i]; x1, y1 = pts[i + 1]
            for s in range(24):
                t = s / 24
                sm.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
        d.line(sm, fill=ink, width=width, joint="curve")
    stroke([(70, 200), (105, 95), (140, 215), (175, 100), (205, 190)])
    stroke([(205, 190), (250, 120), (300, 205), (355, 110), (410, 195), (470, 120)])
    stroke([(470, 120), (520, 190), (575, 105), (630, 185), (690, 120)], 6)
    stroke([(120, 245), (640, 232)], 4)
    img.save(os.path.join(OUT, "imza.png"))
signature()
print("assets:", sorted(os.listdir(OUT)))
