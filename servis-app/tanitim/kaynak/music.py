# -*- coding: utf-8 -*-
"""Tanıtım videosu için özgün fon müziği (numpy ile sentezlenir)."""
import numpy as np, wave, os, struct

SR = 44100
DUR = 66.0
N = int(SR * DUR)
HERE = os.path.dirname(os.path.abspath(__file__))
rng = np.random.default_rng(11)

L = np.zeros(N); R = np.zeros(N)

def add(buf_l, buf_r, sig, at, pan=0.0, gain=1.0):
    i = int(at * SR)
    if i >= N: return
    s = sig[:N - i] * gain
    gl, gr = np.sqrt((1 - pan) / 2) * 1.414, np.sqrt((1 + pan) / 2) * 1.414
    buf_l[i:i + len(s)] += s * gl
    buf_r[i:i + len(s)] += s * gr

def env(n, a, d, s_lvl, r, sus):
    """attack/decay/sustain/release zarfı (saniye)."""
    e = np.zeros(n)
    ai, di, si, ri = int(a * SR), int(d * SR), int(sus * SR), int(r * SR)
    p = 0
    if ai: e[p:p + ai] = np.linspace(0, 1, ai); p += ai
    if di: e[p:p + di] = np.linspace(1, s_lvl, di); p += di
    if si: e[p:p + si] = s_lvl; p += si
    if ri and p < n: e[p:p + ri] = np.linspace(s_lvl, 0, min(ri, n - p))
    return e

def lowpass(x, cutoff):
    """tek kutuplu alçak geçiren"""
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x); acc = 0.0
    # vektörleştirilemez; kısa sinyaller için yeterince hızlı
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y

def lp_sweep(x, c0, c1):
    a = np.exp(-2 * np.pi * np.linspace(c0, c1, len(x)) / SR)
    y = np.empty_like(x); acc = 0.0
    for i in range(len(x)):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y

def saw(f, n, detune=0.0):
    t = np.arange(n) / SR
    if np.isscalar(f):
        ph = (f * (1 + detune)) * t
    else:
        ph = np.cumsum(f * (1 + detune)) / SR
    return 2 * (ph % 1.0) - 1

# ------------------------------------------------------------------ enstrümanlar
def kick(amp=1.0):
    n = int(.42 * SR); t = np.arange(n) / SR
    f = 48 + 90 * np.exp(-t * 34)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7.5)
    click = rng.normal(0, 1, n) * np.exp(-t * 320) * .25
    return (body + click) * amp

def hat(dur=.045, amp=.16, open_=False):
    n = int((.22 if open_ else dur) * SR); t = np.arange(n) / SR
    noise = rng.normal(0, 1, n)
    hp = np.diff(np.concatenate([[0], noise]))           # basit yüksek geçiren
    hp = lowpass(hp, 9000)                               # tizi yumuşat
    return hp * np.exp(-t * (14 if open_ else 95)) * amp * 2.2

def clap(amp=.26):
    n = int(.3 * SR); t = np.arange(n) / SR
    out = np.zeros(n)
    for k, d in enumerate((0, .009, .018, .028)):
        i = int(d * SR)
        seg = rng.normal(0, 1, n - i) * np.exp(-np.arange(n - i) / SR * (60 if k < 3 else 13))
        out[i:] += seg * (1.0 if k < 3 else .8)
    hp = lowpass(np.diff(np.concatenate([[0], out])), 7000)
    return hp * amp * 2.4

def ks_pluck(freq, dur, amp=.5, damp=.4):
    """Karplus-Strong telli ses"""
    nper = max(2, int(SR / freq))
    buf = rng.normal(0, 1, nper)
    n = int(dur * SR); out = np.empty(n)
    idx = 0
    for i in range(n):
        v = buf[idx]
        out[i] = v
        nxt = buf[(idx + 1) % nper]
        buf[idx] = (v + nxt) * .5 * (1 - damp * .012)
        idx = (idx + 1) % nper
    t = np.arange(n) / SR
    return out * np.exp(-t * 2.1) * amp

def bass(freq, dur, amp=.42):
    n = int(dur * SR)
    sig = saw(freq, n) * .55 + np.sin(2 * np.pi * freq * np.arange(n) / SR) * .45
    sig = lowpass(sig, 280)
    e = env(n, .006, .05, .72, max(.04, dur * .3), max(0, dur - .1))
    return sig * e[:n] * amp

def pad(freqs, dur, amp=.17, cut=1500):
    n = int(dur * SR)
    sig = np.zeros(n)
    for f in freqs:
        sig += saw(f, n, .0035) + saw(f, n, -.0035)
    sig = lowpass(sig / (len(freqs) * 2), cut)
    e = env(n, .5, .3, .85, .9, max(0, dur - 1.7))
    return sig * e[:n] * amp

def riser(dur=2.6, amp=.3):
    n = int(dur * SR); t = np.arange(n) / SR
    noise = rng.normal(0, 1, n)
    sw = lp_sweep(noise, 300, 9000)
    tone = np.sin(2 * np.pi * np.cumsum(np.linspace(220, 880, n)) / SR) * .3
    return (sw * .8 + tone) * (t / dur) ** 2 * amp

def whoosh(dur=.7, amp=.22):
    n = int(dur * SR); t = np.arange(n) / SR
    noise = rng.normal(0, 1, n)
    sw = lp_sweep(noise, 6000, 500)
    return sw * np.exp(-((t - dur * .35) / (dur * .3)) ** 2) * amp

def impact(amp=.6):
    n = int(1.7 * SR); t = np.arange(n) / SR
    sub = np.sin(2 * np.pi * np.cumsum(60 + 40 * np.exp(-t * 12)) / SR) * np.exp(-t * 4.2)
    noise = lowpass(rng.normal(0, 1, n), 2200) * np.exp(-t * 9) * .5
    return (sub + noise) * amp

# ------------------------------------------------------------------ düzen
BPM = 100.0
BEAT = 60.0 / BPM          # 0.6 sn
BAR = BEAT * 4             # 2.4 sn
PROG = [  # (bas, pad akoru)
    (110.00, [220.00, 261.63, 329.63]),   # Am
    (87.31,  [174.61, 220.00, 261.63]),   # F
    (130.81, [261.63, 329.63, 392.00]),   # C
    (98.00,  [196.00, 246.94, 293.66]),   # G
]
ARP = [[440.00, 523.25, 659.26], [349.23, 440.00, 523.25],
       [523.25, 659.26, 783.99], [392.00, 493.88, 587.33]]

SCENES = [0.0, 6.4, 13.0, 21.6, 29.2, 36.8, 45.2, 52.6, 57.8, 66.0]
GROOVE_IN, BREAK_IN, BREAK_OUT, OUTRO = 6.4, 36.8, 45.2, 57.8

nbars = int(DUR / BAR) + 1
for b in range(nbars):
    t0 = b * BAR
    if t0 > DUR: break
    root, chord = PROG[b % 4]
    arp = ARP[b % 4]
    breaking = BREAK_IN <= t0 < BREAK_OUT
    outro = t0 >= OUTRO

    # pad — baştan sona
    add(L, R, pad(chord, BAR + .6, amp=.21 if t0 < GROOVE_IN else .26), t0, 0, 1)
    if t0 >= GROOVE_IN - BAR:
        add(L, R, pad([f * 2 for f in chord], BAR + .6, amp=.08, cut=2600), t0, .25, 1)

    # arpej
    if t0 < GROOVE_IN or breaking or outro:
        for s in range(8):
            at = t0 + s * BEAT / 2
            if at > DUR: break
            f = arp[s % 3] * (2 if (s // 3) % 2 and not outro else 1)
            add(L, R, ks_pluck(f, .9, amp=.24), at, (-.4 if s % 2 else .4), 1)
    else:
        for s in (0, 3, 6):
            at = t0 + s * BEAT / 2
            add(L, R, ks_pluck(arp[(s // 3) % 3], .8, amp=.19), at, .3, 1)

    # ritim + bas
    if t0 >= GROOVE_IN and not breaking and not outro:
        for beat in range(4):
            at = t0 + beat * BEAT
            add(L, R, kick(.74 if beat % 2 == 0 else .6), at)
            add(L, R, hat(amp=.13 if beat % 2 else .17), at + BEAT / 2, .18)
            add(L, R, hat(amp=.09), at + BEAT / 4, -.2)
            if beat in (1, 3) and t0 >= BREAK_OUT:
                add(L, R, clap(.34), at)
        add(L, R, bass(root, BEAT * 1.6), t0)
        add(L, R, bass(root, BEAT * .9), t0 + BEAT * 2)
        add(L, R, bass(root * 1.5, BEAT * .8), t0 + BEAT * 3)
    elif breaking:
        add(L, R, bass(root, BAR * .9, amp=.3), t0)
        add(L, R, hat(open_=True, amp=.1), t0 + BEAT * 2, .3)
    else:   # kapanış: yarım tempo, dolgun ama sakin
        add(L, R, bass(root / 2, BAR * .95, amp=.34), t0)
        if t0 < DUR - 2.6:
            add(L, R, kick(.66), t0)
            add(L, R, kick(.54), t0 + BEAT * 2)
            add(L, R, hat(amp=.13), t0 + BEAT, .2)
            add(L, R, hat(amp=.13), t0 + BEAT * 3, -.2)
        add(L, R, pad(chord, BAR + .8, amp=.14, cut=2200), t0, -.2, 1)

# sahne geçişleri: whoosh + vuruş
for i, s in enumerate(SCENES[:-1]):
    if s == 0:
        add(L, R, impact(.55), .25)
        continue
    add(L, R, whoosh(.8, .2), max(0, s - .45))
    add(L, R, impact(.42 if s != OUTRO else .72), s)
add(L, R, riser(2.8, .26), BREAK_OUT - 2.8)
add(L, R, riser(2.2, .22), OUTRO - 2.2)
add(L, R, hat(open_=True, amp=.22), OUTRO, 0)

# ------------------------------------------------------------------ bitiriş
def reverb(x, amount=.22):
    out = x.copy()
    for delay, g in ((.047, .32), (.071, .26), (.113, .2), (.167, .14), (.233, .1)):
        d = int(delay * SR)
        out[d:] += x[:-d] * g * amount * 2.2
    return out

L = reverb(L); R = reverb(R)
# genel zarf: giriş fade + kapanış fade
fade_in = np.clip(np.arange(N) / (SR * 1.2), 0, 1)
fade_out = np.clip((N - np.arange(N)) / (SR * 2.0), 0, 1)
L *= fade_in * fade_out; R *= fade_in * fade_out

peak = max(np.abs(L).max(), np.abs(R).max())
L = np.tanh(L / peak * 1.5) * .82
R = np.tanh(R / peak * 1.5) * .82

stereo = np.empty(N * 2, dtype=np.int16)
stereo[0::2] = np.int16(np.clip(L, -1, 1) * 32000)
stereo[1::2] = np.int16(np.clip(R, -1, 1) * 32000)
path = os.path.join(HERE, "muzik.wav")
with wave.open(path, "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes(stereo.tobytes())
print("yazıldı:", path, f"{os.path.getsize(path)/1e6:.1f} MB, {DUR:.0f} sn")
