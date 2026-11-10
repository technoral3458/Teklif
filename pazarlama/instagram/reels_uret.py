#!/usr/bin/env python3
"""Titanium Cam Instagram Reels (1080x1920) üretici."""
import os, subprocess, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1]            # 1080p animasyonların klasörü
ASSETS = sys.argv[2]         # website/public/assets
OUT = sys.argv[3]            # çıktı mp4
W, H, FPS = 1080, 1920, 30
F = '/usr/share/fonts/opentype/inter/'
def font(name, size): return ImageFont.truetype(F + name + '.otf', size)
XB, B, SB, M = 'Inter-ExtraBold', 'Inter-Bold', 'Inter-SemiBold', 'Inter-Medium'
WHITE, AMBER, CYAN, SOFT = (244, 247, 252), (255, 179, 0), (34, 211, 238), (170, 186, 208)

def background():
    g = Image.new('RGB', (1, 256))
    for y in range(256):
        t = y / 255
        g.putpixel((0, y), (int(13 - 6 * t), int(27 - 15 * t), int(54 - 30 * t)))
    bg = g.resize((W, H), Image.BILINEAR)
    glow = Image.new('RGB', (W, H), (0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse((-420, -380, 620, 660), fill=(36, 84, 190))
    d.ellipse((560, 1150, 1500, 2090), fill=(8, 96, 128))
    glow = glow.filter(ImageFilter.GaussianBlur(220))
    return add(bg, glow)

def add(a, b):
    from PIL import ImageChops
    return ImageChops.add(a, b.point(lambda v: int(v * 0.55)))

BG = background()

def lines(draw, xy, rows, size, spacing=1.08, anchor='la'):
    """rows: [[(metin, renk), ...], ...] — satır içi renkli parçalar."""
    x0, y = xy
    f = font(XB, size)
    for row in rows:
        total = sum(draw.textlength(t, font=f) for t, _ in row)
        x = x0 - total / 2 if anchor == 'ma' else x0
        for t, c in row:
            draw.text((x, y), t, font=f, fill=c)
            x += draw.textlength(t, font=f)
        y += int(size * spacing)
    return y

def chip(draw, xy, text, anchor='la'):
    f = font(B, 34)
    w = draw.textlength(text, font=f) + 56
    x, y = xy
    if anchor == 'ma': x -= w / 2
    draw.rounded_rectangle((x, y, x + w, y + 68), 34, fill=(30, 58, 138), outline=(96, 140, 255), width=3)
    draw.text((x + 28, y + 13), text, font=f, fill=(206, 222, 255))

def brand(draw, y=1790):
    logo = Image.open(os.path.join(ASSETS, 'img/logo/logo_128.png')).convert('RGBA').resize((56, 56), Image.LANCZOS)
    f = font(SB, 34)
    tw = draw.textlength('titaniumcam.com', font=f)
    x = (W - (56 + 16 + tw)) / 2
    return logo, (int(x), y), (x + 72, y + 8), f

CARD = (40, 640, 1040, 1540)   # video kartı (x0,y0,x1,y1)

def module_frame(idx, tag, sub):
    """Arka plan + kart çerçevesi (video için şeffaf pencere) + sabit yazılar."""
    im = BG.convert('RGBA')
    d = ImageDraw.Draw(im)
    x0, y0, x1, y1 = CARD
    shadow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x0 - 6, y0 + 14, x1 + 6, y1 + 34), 48, fill=(0, 0, 0, 150))
    im.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(26)))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((x0 - 4, y0 - 4, x1 + 4, y1 + 4), 44, fill=(64, 110, 220))
    hole = Image.new('L', (W, H), 255)
    ImageDraw.Draw(hole).rounded_rectangle(CARD, 40, fill=0)
    im.putalpha(hole)
    d = ImageDraw.Draw(im)
    chip(d, (64, 268), f'{idx:02d} / 05  ·  {tag}')
    f = font(SB, 44)
    d.text((W / 2, 1586), sub, font=f, fill=SOFT, anchor='ma')
    # ilerleme çizgisi
    seg = (W - 128 - 4 * 14) / 5
    for i in range(5):
        xa = 64 + i * (seg + 14)
        d.rounded_rectangle((xa, 1700, xa + seg, 1708), 4, fill=CYAN if i < idx else (52, 70, 104))
    logo, lp, tp, bf = brand(d)
    im.alpha_composite(logo, lp)
    ImageDraw.Draw(im).text(tp, 'titaniumcam.com', font=bf, fill=(214, 224, 240))
    return im

def title_layer(rows, y=362, size=104):
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    lines(ImageDraw.Draw(im), (64, y), rows, size)
    return im

def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode:
        print(r.stderr[-2500:]); sys.exit(1)

ENC = ['-r', str(FPS), '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', '-an']
TXT = "fade=in:st=0.12:d=0.38:alpha=1"
SLIDE = "overlay=0:'if(lt(t,0.5),46*pow(1-t/0.5,3),0)'"

def scene_module(i, name, tag, rows, sub, start, speed, dur, scale_h):
    frame, title = f'{HERE}/m{i}_frame.png', f'{HERE}/m{i}_title.png'
    module_frame(i, tag, sub).save(frame); title_layer(rows).save(title)
    cw, ch = CARD[2] - CARD[0], CARD[3] - CARD[1]
    vf = (f"[0:v]trim=start={start}:duration={dur * speed},setpts=(PTS-STARTPTS)/{speed},"
          f"scale=-2:{scale_h}:flags=lanczos,crop={min(cw, 9999)}:{min(ch, scale_h)}:(iw-{cw})/2:(ih-{min(ch, scale_h)})/2,"
          f"pad={cw}:{ch}:0:(oh-ih)/2:color=0x16181d,"
          f"zoompan=z='1+0.035*on/{int(dur * FPS)}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s={cw}x{ch}:fps={FPS}[v];"
          f"color=c=0x16181d:s={W}x{H}:r={FPS}:d={dur}[base];[base][v]overlay={CARD[0]}:{CARD[1]}[a];"
          f"[a][1:v]overlay=0:0[b];[2:v]{TXT}[t];[b][t]{SLIDE}:shortest=0,trim=duration={dur},setsar=1[out]")
    out = f'{HERE}/s_m{i}.mp4'
    run(['ffmpeg', '-v', 'error', '-y', '-i', f'{SRC}/{name}_1080p.mp4', '-loop', '1', '-t', str(dur), '-i', frame,
         '-loop', '1', '-t', str(dur), '-i', title, '-filter_complex', vf, '-map', '[out]', '-t', str(dur)] + ENC + [out])
    return out, dur

def scene_hook(dur=2.7):
    shade = Image.new('RGBA', (W, H), (4, 8, 18, 0))
    px = shade.load()
    for y in range(H):
        a = int(150 + 85 * abs(y - H * 0.45) / (H * 0.55))
        for x in range(W): px[x, y] = (4, 8, 18, min(238, a))
    d = ImageDraw.Draw(shade)
    chip(d, (W / 2, 560), 'MOBİLYA ATÖLYELERİ İÇİN', anchor='ma')
    shade.save(f'{HERE}/hook_shade.png')
    t = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    lines(ImageDraw.Draw(t), (W / 2, 690), [[('Atölyende hâlâ', WHITE)], [('3 ayrı program', AMBER)], [('mı kullanıyorsun?', WHITE)]], 112, anchor='ma')
    t.save(f'{HERE}/hook_title.png')
    img = os.path.join(ASSETS, 'img/render/mutfak_render.webp')
    Image.open(img).convert('RGB').save(f'{HERE}/hook_bg.png')
    n = int(dur * FPS)
    vf = (f"[0:v]scale=-2:{H}:flags=lanczos,zoompan=z='1.06+0.06*on/{n}':x='iw/2-(iw/zoom/2)+120*on/{n}':y='ih/2-(ih/zoom/2)':d=1:s={W}x{H}:fps={FPS}[bg];"
          f"[bg][1:v]overlay=0:0[a];[2:v]{TXT}[t];[a][t]{SLIDE},trim=duration={dur},setsar=1[out]")
    # zoompan girişini 9:16'ya kırp
    vf = vf.replace(f"scale=-2:{H}:flags=lanczos,", f"scale=-2:{H}:flags=lanczos,crop={W}:{H},")
    out = f'{HERE}/s_hook.mp4'
    run(['ffmpeg', '-v', 'error', '-y', '-loop', '1', '-t', str(dur), '-i', f'{HERE}/hook_bg.png', '-loop', '1', '-t', str(dur), '-i', f'{HERE}/hook_shade.png',
         '-loop', '1', '-t', str(dur), '-i', f'{HERE}/hook_title.png', '-filter_complex', vf, '-map', '[out]', '-t', str(dur)] + ENC + [out])
    return out, dur

def scene_card(key, dur, build):
    """Tam ekran yazı sahnesi: sabit arka plan + animasyonlu yazı katmanı."""
    BG.save(f'{HERE}/{key}_bg.png')
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0)); build(layer); layer.save(f'{HERE}/{key}_t.png')
    vf = f"[1:v]{TXT}[t];[0:v][t]{SLIDE},trim=duration={dur},setsar=1[out]"
    out = f'{HERE}/s_{key}.mp4'
    run(['ffmpeg', '-v', 'error', '-y', '-loop', '1', '-t', str(dur), '-i', f'{HERE}/{key}_bg.png', '-loop', '1', '-t', str(dur), '-i', f'{HERE}/{key}_t.png',
         '-filter_complex', vf, '-map', '[out]', '-t', str(dur)] + ENC + [out])
    return out, dur

def answer(layer):
    d = ImageDraw.Draw(layer)
    logo = Image.open(os.path.join(ASSETS, 'img/logo/logo_256.png')).convert('RGBA').resize((200, 200), Image.LANCZOS)
    layer.alpha_composite(logo, ((W - 200) // 2, 560))
    lines(d, (W / 2, 820), [[('Tasarımdan', WHITE)], [('makineye,', WHITE)], [('tek program', AMBER)]], 124, anchor='ma')

def cta(layer):
    d = ImageDraw.Draw(layer)
    logo = Image.open(os.path.join(ASSETS, 'img/logo/logo_256.png')).convert('RGBA').resize((176, 176), Image.LANCZOS)
    layer.alpha_composite(logo, ((W - 176) // 2, 380))
    d.text((W / 2, 590), 'Titanium Cam', font=font(XB, 100), fill=WHITE, anchor='ma')
    d.text((W / 2, 716), 'Mobilya üretimi için CNC yazılımı', font=font(M, 42), fill=SOFT, anchor='ma')
    y = 850
    for big, small in [('32', 'hazır kapak modeli'), ('9', 'hazır makine profili'), ('5', 'dil desteği')]:
        d.rounded_rectangle((150, y, 930, y + 112), 26, fill=(17, 30, 58), outline=(54, 78, 128), width=2)
        d.text((230, y + 56), big, font=font(XB, 68), fill=CYAN, anchor='mm')
        d.text((320, y + 56), small, font=font(SB, 44), fill=WHITE, anchor='lm')
        y += 134
    d.rounded_rectangle((150, 1300, 930, 1430), 65, fill=(37, 110, 240))
    d.text((W / 2, 1365), 'Ücretsiz demo iste', font=font(XB, 60), fill=WHITE, anchor='mm')
    d.text((W / 2, 1486), 'titaniumcam.com', font=font(B, 64), fill=AMBER, anchor='ma')

scenes = [scene_hook(), scene_card('answer', 1.9, answer)]
MODS = [
    ('kapak_parametrik', 'KAPAK',   [[('32 hazır model,', WHITE)], [('her ölçüde', AMBER)]],              'Ölçüyü yaz, kapak yeniden çizilsin', 1.0, 3.6, 900),
    ('kabin_3b',         'KABİN',   [[('Dolabı 3B kur,', WHITE)], [('parçalar hazır', AMBER)]],            'Delikler ve bağlantılar otomatik',   1.0, 3.3, 900),
    ('mutfak_3b',        'MUTFAK',  [[('Mutfağı planla,', WHITE)], [('3B gör', AMBER)]],                   'Plan, cephe ve 3B aynı anda',        0.5, 3.4, 900),
    ('nesting_yerlesim', 'NESTING', [[('Parçalar en az', WHITE)], [('levhaya yerleşir', AMBER)]],          'Etiketler ve ebatlama listesi hazır', 0.2, 2.5, 760),
    ('cizim_simulasyon', 'MAKİNE',  [[('Kesimi önce', WHITE)], [('simülasyonda gör', AMBER)]],             'NC kodu makinene hazır',             1.5, 3.6, 900),
]
for i, (name, tag, rows, sub, start, speed, sh) in enumerate(MODS, 1):
    scenes.append(scene_module(i, name, tag, rows, sub, start, speed, 3.0, sh))
scenes.append(scene_card('cta', 4.0, cta))

# Sahneleri kısa geçişlerle birleştir
X = 0.3
inputs, fc, off, prev = [], [], 0.0, '[0:v]'
for k, (p, dur) in enumerate(scenes):
    inputs += ['-i', p]
    if k:
        off += scenes[k - 1][1] - X
        tr = 'slideup' if 2 <= k <= 6 else 'fade'
        fc.append(f"{prev}[{k}:v]xfade=transition={tr}:duration={X}:offset={off:.3f}[x{k}]"); prev = f'[x{k}]'
total = sum(d for _, d in scenes) - X * (len(scenes) - 1)
fc.append(f"{prev}fade=in:st=0:d=0.25,format=yuv420p[v]")
run(['ffmpeg', '-v', 'error', '-y'] + inputs + ['-f', 'lavfi', '-t', f'{total:.3f}', '-i', 'anullsrc=r=44100:cl=stereo',
     '-filter_complex', ';'.join(fc), '-map', '[v]', '-map', f'{len(scenes)}:a', '-r', str(FPS), '-c:v', 'libx264', '-preset', 'slow', '-crf', '19',
     '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart', '-t', f'{total:.3f}', OUT])
print('süre', round(total, 2), 'sn →', OUT)
