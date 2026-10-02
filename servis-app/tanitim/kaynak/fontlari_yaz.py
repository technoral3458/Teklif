# -*- coding: utf-8 -*-
"""gf.css içindeki woff2'leri indirir ve faces.css üretir."""
import os, re, urllib.request
HERE = os.path.dirname(os.path.abspath(__file__))
LAT = ("U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,"
       "U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD")
EXT = ("U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,"
       "U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF")
css = open(os.path.join(HERE, "gf.css"), encoding="utf-8").read()
os.makedirs(os.path.join(HERE, "fonts"), exist_ok=True)
faces, seen = [], set()
for b in css.split("@font-face")[1:]:
    fam = re.search(r"font-family:\s*'([^']+)'", b)
    wt = re.search(r"font-weight:\s*(\d+)", b)
    url = re.search(r"url\((https://[^)]+\.woff2)\)", b)
    rng = re.search(r"unicode-range:\s*([^;]+);", b)
    if not (fam and wt and url):
        continue
    ur = rng.group(1) if rng else ""
    sub = "ext" if "U+0100" in ur else ("lat" if "U+0000" in ur else None)
    if sub is None:
        continue
    key = (fam.group(1), wt.group(1), sub)
    if key in seen:
        continue
    seen.add(key)
    fn = f"{fam.group(1).replace(' ', '')}-{wt.group(1)}-{sub}.woff2"
    urllib.request.urlretrieve(url.group(1), os.path.join(HERE, "fonts", fn))
    faces.append(f"@font-face{{font-family:'{fam.group(1)}';font-style:normal;font-weight:{wt.group(1)};"
                 f"src:url('fonts/{fn}') format('woff2');unicode-range:{EXT if sub == 'ext' else LAT};font-display:block;}}")
faces.append("@font-face{font-family:'Material Symbols Rounded';font-style:normal;font-weight:400;"
             "src:url('fonts/MaterialSymbolsRounded.woff2') format('woff2');font-display:block;}")
open(os.path.join(HERE, "faces.css"), "w", encoding="utf-8").write("\n".join(faces) + "\n")
print(len(faces), "font yüzü yazıldı")
