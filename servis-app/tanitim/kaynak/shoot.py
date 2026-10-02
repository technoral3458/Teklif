# -*- coding: utf-8 -*-
"""promo.html sahnelerini kare kare yakalar."""
import argparse, os, sys, time
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
# Hazır Chromium varsa onu kullan, yoksa Playwright kendi indirdiğini açsın
CHROME = next((p for p in ("/opt/pw-browsers/chromium-1194/chrome-linux/chrome",)
               if os.path.exists(p)), None)
FPS = 30

ap = argparse.ArgumentParser()
ap.add_argument("--times", help="virgülle ayrılmış saniyeler (önizleme)")
ap.add_argument("--out", default="frames")
ap.add_argument("--all", action="store_true")
ap.add_argument("--fps", type=int, default=FPS)
a = ap.parse_args()

out = os.path.join(HERE, a.out)
os.makedirs(out, exist_ok=True)

with sync_playwright() as pw:
    br = pw.chromium.launch(executable_path=CHROME, args=[
        "--force-color-profile=srgb", "--font-render-hinting=none",
        "--disable-lcd-text", "--hide-scrollbars", "--disable-dev-shm-usage",
    ])
    pg = br.new_page(viewport={"width": 1080, "height": 1920}, device_scale_factor=1)
    errs = []
    pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
    pg.on("pageerror", lambda e: errs.append("PAGEERROR: " + str(e)))
    pg.goto("file://" + os.path.join(HERE, "promo.html"))
    pg.wait_for_function("window.READY === true", timeout=20000)
    pg.evaluate("document.fonts.ready")
    pg.wait_for_timeout(1200)
    total = pg.evaluate("window.TOTAL")
    print(f"toplam süre: {total:.2f} sn", flush=True)
    if errs:
        print("!! konsol:", errs[:8], flush=True)

    if a.all:
        n = int(total * a.fps)
        t0 = time.time()
        for i in range(n):
            pg.evaluate("t => window.frame(t)", i / a.fps)
            pg.screenshot(path=os.path.join(out, f"f{i:05d}.jpg"), type="jpeg", quality=94)
            if i % 100 == 0 and i:
                el = time.time() - t0
                print(f"  {i}/{n} kare — {el:.0f}sn geçti, tahmini kalan {el/i*(n-i):.0f}sn", flush=True)
        print(f"{n} kare yazıldı ({time.time()-t0:.0f} sn)", flush=True)
    else:
        times = [float(x) for x in (a.times or "1,3,8,14,20,28,35,43,50,56,62").split(",")]
        for t in times:
            pg.evaluate("t => window.frame(t)", t)
            pg.wait_for_timeout(60)
            pg.screenshot(path=os.path.join(out, f"p{t:06.2f}.png"))
        print("önizleme:", times, flush=True)
    if errs:
        print("!! konsol hataları:", errs[:10], flush=True)
    br.close()
