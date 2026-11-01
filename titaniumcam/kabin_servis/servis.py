"""
Online Kabin — TitaniumCam motor servisi (iç ağ, durumsuz).

Bu dosya TitaniumCam'in Python motorunu SARAR, hesap yapmaz. Ölçü, delik,
bağlantı ve parça hesabının tamamı motordadır (cabinet_v2). Motor kodu bu
repoda DEĞİLDİR; sunucuda ayrı bir klasörde durur:

    KABIN_MOTOR_DIR   motor kodunun klasörü (içinde cabinet_v2/, i18n.py)
    KABIN_TANIM_DIR   firma tanım klasörü (içinde kabin_tanimlar.json) — sabit,
                      kullanıcıdan gelmez. Boşsa motorun varsayılan tanimlar.json'u.

Yalnız 127.0.0.1'de dinler; yalnız web sunucusu çağırır. Her istek
kabin JSON + işlem alır, yeni kabin JSON + görünüm verisi döner.
"""
from __future__ import annotations

import copy
import hashlib
import json
import math
import os
import sys
import threading
from dataclasses import is_dataclass, fields
from enum import Enum
from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

MOTOR_DIR = os.environ.get("KABIN_MOTOR_DIR", "")
TANIM_KLASORU = os.environ.get("KABIN_TANIM_DIR", "") or None
if not MOTOR_DIR or not os.path.isdir(os.path.join(MOTOR_DIR, "cabinet_v2")):
    raise RuntimeError("KABIN_MOTOR_DIR motor klasörünü göstermiyor (cabinet_v2 bulunamadı)")
sys.path.insert(0, MOTOR_DIR)

from cabinet_v2.engine import new_cabinet, regenerate  # noqa: E402
from cabinet_v2.geometry import world as W  # noqa: E402
from cabinet_v2.interactive import compile as C, doktor as D, engine as E, model as M, rapor as R, tanimlar as TN  # noqa: E402
from cabinet_v2.interactive.kurallar import kural_seti  # noqa: E402
from cabinet_v2.preview.egri import gorunum_panelleri  # noqa: E402
from cabinet_v2.reports import hardware as H  # noqa: E402

# Süreç başına TEK tanım seti (Tanimlar yüklenince engine.RAY_TANIMLARI süreç genelinde değişir)
tn = TN.Tanimlar.yukle(TANIM_KLASORU)
rs = kural_seti(tn)
_KILIT = threading.Lock()            # motor iş parçacığı güvenli sayılmaz: tek tek çalıştır

MAX_ISTEK = 2_000_000                # bayt
MAX_ELEMAN = 300
OLCU_MIN, OLCU_MAX = 50.0, 10000.0


def _surum(yollar: list[str]) -> str:
    h = hashlib.sha1()
    for y in yollar:
        if y and os.path.isfile(y):
            with open(y, "rb") as f:
                h.update(f.read())
    return h.hexdigest()[:12]


def _motor_dosyalari() -> list[str]:
    out = []
    for kok, _d, dosyalar in os.walk(os.path.join(MOTOR_DIR, "cabinet_v2")):
        for d in dosyalar:
            if d.endswith((".py", ".json")):
                out.append(os.path.join(kok, d))
    return sorted(out)


MOTOR_SURUMU = os.environ.get("KABIN_MOTOR_SURUM") or _surum(_motor_dosyalari())
TANIM_SURUMU = _surum([TN.VARSAYILAN_DOSYA] + ([os.path.join(TANIM_KLASORU, TN.KULLANICI_DOSYA)]
                                                if TANIM_KLASORU else []))

app = FastAPI(title="Kabin motor servisi", docs_url=None, redoc_url=None, openapi_url=None)


class Hata(Exception):
    """Kullanıcı hatası: 422, mesaj aynen gösterilir, dolap değişmez."""


# Müşteriye KAPALI kodlar (yönetici tanım ekranından): {"malzeme": [...], "bant": [...], ...}.
# Boş = kısıt yok (yönetici). İstekler _KILIT altında tek tek işlendiği için modül düzeyinde.
KAPALI: dict = {}


def _acik_mi(tur: str, kod: str) -> bool:
    return kod not in KAPALI.get(tur, ())


# ══════════════════════════════════════════════════════════════════════════
# Doğrulama
# ══════════════════════════════════════════════════════════════════════════
def _sonlu(v: Any, yol: str = "") -> None:
    """NaN / Infinity hiçbir yerde kabul edilmez."""
    if isinstance(v, float) and not math.isfinite(v):
        raise Hata(f"Geçersiz sayı: {yol or 'değer'}")
    if isinstance(v, dict):
        for k, x in v.items():
            _sonlu(x, f"{yol}.{k}")
    elif isinstance(v, list):
        for i, x in enumerate(v):
            _sonlu(x, f"{yol}[{i}]")


def _sayi(v: Any, ad: str, lo: float = -100000.0, hi: float = 100000.0) -> float:
    if isinstance(v, bool) or not isinstance(v, (int, float, str)):
        raise Hata(f"{ad}: sayı bekleniyor")
    try:
        f = float(str(v).replace(",", "."))
    except ValueError:
        raise Hata(f"{ad}: sayı bekleniyor")
    if not math.isfinite(f):
        raise Hata(f"{ad}: geçersiz sayı")
    if f < lo or f > hi:
        raise Hata(f"{ad}: {lo:g} ile {hi:g} arasında olmalı")
    return f


def _tam(v: Any, ad: str, lo: int, hi: int) -> int:
    f = _sayi(v, ad, lo, hi)
    if abs(f - round(f)) > 1e-9:
        raise Hata(f"{ad}: tam sayı olmalı")
    return int(round(f))


def _enum(cls: type[Enum], v: Any, ad: str) -> Enum:
    if isinstance(v, cls):
        return v
    if not isinstance(v, str) or v not in cls.__members__:
        raise Hata(f"{ad}: geçersiz seçim")
    return cls[v]


def _sec(v: Any, secenekler: tuple[str, ...], ad: str) -> str:
    if not isinstance(v, str) or v not in secenekler:
        raise Hata(f"{ad}: geçersiz seçim")
    return v


def _malzeme(v: Any, ad: str = "Malzeme") -> str:
    kodlar = {str(m.get("kod")) for m in tn.malzemeler()}
    if not isinstance(v, str) or v not in kodlar:
        raise Hata(f"{ad}: tanımda olmayan malzeme")
    if not _acik_mi("malzeme", v):
        raise Hata(f"{ad}: bu malzeme seçilemez")
    return v


def _bant_kodu(v: Any, ad: str = "Bant") -> str:
    if v in ("", None):
        return ""
    kodlar = {str(b.get("kod")) for b in tn.bantlar()}
    if not isinstance(v, str) or v not in kodlar:
        raise Hata(f"{ad}: tanımda olmayan bant")
    if not _acik_mi("bant", v):
        raise Hata(f"{ad}: bu bant seçilemez")
    return v


def _bant(obj: Any, v: Any) -> None:
    """KenarBant: {on, arka, ust, alt, sol, sag} -> bant kodu."""
    if not isinstance(v, dict):
        raise Hata("Bant: geçersiz")
    for k, x in v.items():
        if k not in ("on", "arka", "ust", "alt", "sol", "sag"):
            raise Hata("Bant: geçersiz kenar")
        setattr(obj.bant, k, _bant_kodu(x))


def _yazi(v: Any, ad: str, n: int = 120) -> str:
    if not isinstance(v, str):
        raise Hata(f"{ad}: metin bekleniyor")
    return v.strip()[:n]


def _bool(v: Any, ad: str) -> bool:
    if isinstance(v, bool):
        return v
    if v in (0, 1):
        return bool(v)
    raise Hata(f"{ad}: evet/hayır bekleniyor")


# Müşteri taslağında her işlemde boşaltılan alanlar (makineye doğrudan giden veri)
YASAK_UST = {"parca_cizimleri": {}, "manueldelikler": [], "delik_ozel": [], "bag_ozel": []}


def _takim_temizle(v: Any) -> Any:
    if isinstance(v, dict):
        out = {}
        for k, x in v.items():
            if k == "takim" or k.endswith("_takim") or k.endswith("_takimi"):
                out[k] = "" if isinstance(x, str) else x
            else:
                out[k] = _takim_temizle(x)
        return out
    if isinstance(v, list):
        return [_takim_temizle(x) for x in v]
    return v


def _yasaklari_bosalt(d: dict) -> dict:
    d = _takim_temizle(d)
    for k, bos in YASAK_UST.items():
        if k in d:
            d[k] = copy.deepcopy(bos)
    return d


def _esit(a: Any, b: Any) -> bool:
    if isinstance(a, float) or isinstance(b, float):
        try:
            return abs(float(a) - float(b)) < 1e-6
        except (TypeError, ValueError):
            return False
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(_esit(a[k], b[k]) for k in a)
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(_esit(x, y) for x, y in zip(a, b))
    return a == b


def kabin_yukle(d: Any, rol: str) -> Any:
    """Kabin JSON -> Kabin. Bilinmeyen alan, uydurma enum, NaN reddedilir
    (from_dict bunları sessizce yutar: gidiş-dönüş karşılaştırması yakalar)."""
    if not isinstance(d, dict):
        raise Hata("Geçersiz kabin verisi")
    _sonlu(d, "kabin")
    if rol != "yonetici":
        d = _yasaklari_bosalt(d)
    try:
        k = M.Kabin.from_dict(copy.deepcopy(d))
    except Exception:
        raise Hata("Geçersiz kabin verisi")
    if not _esit(k.to_dict(), d):
        raise Hata("Geçersiz kabin verisi (bilinmeyen alan ya da değer)")
    if k.tip is not M.KabinTipi.NORMAL:
        raise Hata("Bu dolap tipi web'de desteklenmiyor")
    if sum(1 for _ in k.elemanlar()) > MAX_ELEMAN:
        raise Hata("Çok fazla parça")
    return k


# ══════════════════════════════════════════════════════════════════════════
# Alan izinleri (kabin özellikleri)
# ══════════════════════════════════════════════════════════════════════════
# anahtar -> uygulayıcı. Hangi anahtarın hangi role açık olduğunu web sunucusu
# (yönetici ayarından) 'izinler' listesiyle verir; listede olmayan alan yazılmaz.
def _govde(k: Any, alan: str, v: Any) -> None:
    for p in k.paneller.values():
        if alan == "malzeme":
            p.malzeme = _malzeme(v)
            p.kalinlik = float(tn.kalinlik(p.malzeme, p.kalinlik))
        elif alan == "kalinlik":
            p.kalinlik = _sayi(v, "Kalınlık", 3, 60)
        elif alan == "bant_on":
            p.bant.on = _bant_kodu(v, "Ön bant")


def _baglanti_kodu(v: Any) -> str:
    if v == "":
        return ""
    kodlar = {str(b.get("kod")) for b in tn.baglantilar()}
    if v not in kodlar:
        raise Hata("Bağlantı elemanı tanımda yok")
    return v


ALANLAR: dict[str, Any] = {
    # Genel
    "adet": lambda k, v: setattr(k, "adet", _tam(v, "Adet", 1, 999)),
    "aciklama": lambda k, v: setattr(k, "aciklama", _yazi(v, "Açıklama")),
    "kabinno": lambda k, v: setattr(k, "kabinno", _yazi(v, "Kabin no", 40)),
    "ayaktipi": lambda k, v: setattr(k, "ayaktipi", _enum(M.AyakTipi, v, "Ayak / Baza")),
    "ayakyuksekligi": lambda k, v: setattr(k, "ayakyuksekligi", _sayi(v, "Ayak yüksekliği", 0, 500)),
    "baza.on": lambda k, v: setattr(k.baza, "on", _bool(v, "Ön baza")),
    "baza.arka": lambda k, v: setattr(k.baza, "arka", _bool(v, "Arka baza")),
    "baza.on_geriye": lambda k, v: setattr(k.baza, "on_geriye", _sayi(v, "Ön geri çekme", 0, 300)),
    "baza.arka_geriye": lambda k, v: setattr(k.baza, "arka_geriye", _sayi(v, "Arka geri çekme", 0, 300)),
    # Gövde
    "paneller.sol": lambda k, v: setattr(k.paneller["sol"], "mevcut", _bool(v, "Sol")),
    "paneller.sag": lambda k, v: setattr(k.paneller["sag"], "mevcut", _bool(v, "Sağ")),
    "paneller.ust": lambda k, v: setattr(k.paneller["ust"], "mevcut", _bool(v, "Üst")),
    "paneller.alt": lambda k, v: setattr(k.paneller["alt"], "mevcut", _bool(v, "Alt")),
    "solust": lambda k, v: setattr(k, "solust", _kose(v)),
    "sagust": lambda k, v: setattr(k, "sagust", _kose(v)),
    "solalt": lambda k, v: setattr(k, "solalt", _kose(v)),
    "sagalt": lambda k, v: setattr(k, "sagalt", _kose(v)),
    "govde.malzeme": lambda k, v: _govde(k, "malzeme", v),
    "govde.kalinlik": lambda k, v: _govde(k, "kalinlik", v),
    "govde.bant_on": lambda k, v: _govde(k, "bant_on", v),
    "oto_bant": lambda k, v: setattr(k, "oto_bant", _bant_kodu(v, "Otomatik bant")),
    "oto_bant_arka": lambda k, v: setattr(k, "oto_bant_arka", _bool(v, "Arka kenarlar")),
    # Bağlantı (yönetici)
    "baglanti_kodu": lambda k, v: setattr(k, "baglanti_kodu", _baglanti_kodu(v)),
    "baglanti_sablonu": lambda k, v: setattr(k, "baglanti_sablonu", _sec(
        v, ("",) + tuple(tn.baglanti_sablon_adlari()), "Yerleşim tablosu")),
    "donanim": lambda k, v: setattr(k, "donanim", _yazi(v, "Yedek donanım", 30)),
    "mentesesiz": lambda k, v: setattr(k, "mentesesiz", not _bool(v, "Menteşe deliği")),
    # Boşluk (yönetici)
    **{f: (lambda f: lambda k, v: setattr(k, f, _sayi(v, f, 0, 100)))(f) for f in (
        "k_soldanbosluk", "k_sagdanbosluk", "k_usttenbosluk", "k_alttanbosluk",
        "yanyanakapakarasi", "ustustekapakarasi", "icten_kapak_boslugu",
        "k_tacaltibosluk", "cakarkbosluk", "garkgirmepayi")},
    # Kapama
    "yan_kapama.sol": lambda k, v: setattr(k.yan_kapama, "sol", _bool(v, "Sol kapama")),
    "yan_kapama.sag": lambda k, v: setattr(k.yan_kapama, "sag", _bool(v, "Sağ kapama")),
    "yan_kapama.zemine": lambda k, v: setattr(k.yan_kapama, "zemine", _bool(v, "Zemine")),
    "yan_kapama.tavana": lambda k, v: setattr(k.yan_kapama, "tavana", _bool(v, "Tavana")),
    "yan_kapama.tavan": lambda k, v: setattr(k.yan_kapama, "tavan", _sayi(v, "Tavan", 0, 10000)),
    "yan_kapama.on_tasma": lambda k, v: setattr(k.yan_kapama, "on_tasma", _sayi(v, "Ön taşma", 0, 500)),
    "yan_kapama.arka_tasma": lambda k, v: setattr(k.yan_kapama, "arka_tasma", _sayi(v, "Arka taşma", 0, 500)),
    "yan_kapama.ust_tasma": lambda k, v: setattr(k.yan_kapama, "ust_tasma", _sayi(v, "Üst taşma", 0, 500)),
    "yan_kapama.malzeme": lambda k, v: (setattr(k.yan_kapama, "malzeme", _malzeme(v)),
                                        setattr(k.yan_kapama, "kalinlik", float(tn.kalinlik(v, 18.0)))),
    "yan_kapama.bant_on": lambda k, v: setattr(k.yan_kapama.bant, "on", _bant_kodu(v)),
    "yan_kapama.bol": lambda k, v: setattr(k.yan_kapama, "bol", _bool(v, "Bölme")),
    "yan_kapama.bol_yukseklik": lambda k, v: setattr(k.yan_kapama, "bol_yukseklik", _sayi(v, "Bölme yüksekliği", 50, 10000)),
    "dolgu.ust": lambda k, v: setattr(k.dolgu, "ust", _bool(v, "Üst/alt dolgu")),
    "dolgu.ust_konum": lambda k, v: setattr(k.dolgu, "ust_konum", _enum(M.DolguKonum, v, "Dolgu konumu")),
    "dolgu.ust_boyut": lambda k, v: setattr(k.dolgu, "ust_boyut", _sayi(v, "Dolgu ölçüsü", 10, 2000)),
    "dolgu.sol": lambda k, v: setattr(k.dolgu, "sol", _bool(v, "Sol dolgu")),
    "dolgu.sag": lambda k, v: setattr(k.dolgu, "sag", _bool(v, "Sağ dolgu")),
    "dolgu.yan_boyut": lambda k, v: setattr(k.dolgu, "yan_boyut", _sayi(v, "Dolgu ölçüsü", 10, 2000)),
    "dolgu.braket": lambda k, v: setattr(k.dolgu, "braket", _sayi(v, "Braket", 0, 500)),
    "dolgu.malzeme": lambda k, v: (setattr(k.dolgu, "malzeme", _malzeme(v)),
                                   setattr(k.dolgu, "kalinlik", float(tn.kalinlik(v, 18.0)))),
}
OLCU_ALANLARI = ("en", "boy", "derinlik")


def _kose(v: Any) -> Any:
    if v == "GONYE":                      # gönye: Aşama 2, yalnız yönetici
        raise Hata("Gönye birleşim bu aşamada yok")
    return _enum(M.KoseBirlesim, v, "Köşe birleşimi")


def ozellikler(k: Any) -> dict[str, Any]:
    """Kabin özellikleri panelinin güncel değerleri (düz anahtarlar)."""
    p = k.paneller
    g = p["sol"]
    return {
        "en": k.en, "boy": k.boy, "derinlik": k.derinlik, "adet": k.adet,
        "aciklama": k.aciklama, "kabinno": k.kabinno,
        "ayaktipi": k.ayaktipi.name, "ayakyuksekligi": k.ayakyuksekligi,
        "baza.on": k.baza.on, "baza.arka": k.baza.arka,
        "baza.on_geriye": k.baza.on_geriye, "baza.arka_geriye": k.baza.arka_geriye,
        "paneller.sol": p["sol"].mevcut, "paneller.sag": p["sag"].mevcut,
        "paneller.ust": p["ust"].mevcut, "paneller.alt": p["alt"].mevcut,
        "solust": k.solust.name, "sagust": k.sagust.name, "solalt": k.solalt.name, "sagalt": k.sagalt.name,
        "govde.malzeme": g.malzeme, "govde.kalinlik": g.kalinlik, "govde.bant_on": g.bant.on,
        "oto_bant": k.oto_bant, "oto_bant_arka": k.oto_bant_arka,
        "baglanti_kodu": k.baglanti_kodu, "baglanti_sablonu": k.baglanti_sablonu,
        "donanim": k.donanim, "mentesesiz": not k.mentesesiz,
        **{f: getattr(k, f) for f in (
            "k_soldanbosluk", "k_sagdanbosluk", "k_usttenbosluk", "k_alttanbosluk",
            "yanyanakapakarasi", "ustustekapakarasi", "icten_kapak_boslugu",
            "k_tacaltibosluk", "cakarkbosluk", "garkgirmepayi")},
        **{f"yan_kapama.{f}": getattr(k.yan_kapama, f) for f in (
            "sol", "sag", "zemine", "tavana", "tavan", "on_tasma", "arka_tasma", "ust_tasma",
            "malzeme", "bol", "bol_yukseklik")},
        "yan_kapama.bant_on": k.yan_kapama.bant.on,
        **{f"dolgu.{f}": (getattr(k.dolgu, f).name if isinstance(getattr(k.dolgu, f), Enum)
                          else getattr(k.dolgu, f)) for f in (
            "ust", "ust_konum", "ust_boyut", "sol", "sag", "yan_boyut", "braket", "malzeme")},
    }


# ══════════════════════════════════════════════════════════════════════════
# Eleman şablonu + pencere seçenekleri
# ══════════════════════════════════════════════════════════════════════════
TUR_SABLON = {"dikeybolme": "dikeybolme", "yataybolme": "yataybolme", "raf": "raf",
              "arkalik": "arkalik", "dikeykayit": "dikeykayit", "yataykayit": "yataykayit",
              "kapak": "kapak", "cekmece": "cekmece", "usttabla": None, "aski": "raf"}
GOZ_GEREKMEZ = {"usttabla"}
BIRLESEN = {"kapak", "cekmece", "arkalik"}       # çoklu gözle tek bölge olurlar

# tür -> {alan: doğrulayıcı}
_SAYI = lambda lo, hi: (lambda v, ad: _sayi(v, ad, lo, hi))  # noqa: E731
SECENEK = {
    "ortak": {"malzeme": lambda v, a: _malzeme(v), "kalinlik": _SAYI(3, 60)},
    "raf": {"rafkenarboslukpayi": _SAYI(0, 50), "geriyecek": _SAYI(0, 300)},
    "aski": {"aski_cap": _SAYI(10, 60), "aski_ustten": _SAYI(10, 500)},
    "arkalik": {"bosluk_sol": _SAYI(0, 200), "bosluk_sag": _SAYI(0, 200),
                "bosluk_ust": _SAYI(0, 200), "bosluk_alt": _SAYI(0, 200)},
    "dikeykayit": {"eni": _SAYI(20, 1000),
                   "konum": lambda v, a: _enum(M.KayitKonum, _sec(v, ("ON", "ARKA"), a), a),
                   "durus": lambda v, a: _sec(v, ("YAN", "ON"), a)},
    "yataykayit": {"kayit_eni": _SAYI(20, 1000),
                   "kayit_yer": lambda v, a: _enum(M.KayitKonum, _sec(v, ("UST", "ALT"), a), a),
                   "kayitkonum": lambda v, a: _enum(M.KayitKonum, _sec(v, ("ON", "ARKA"), a), a),
                   "kayit_durus": lambda v, a: _sec(v, ("YATIK", "DIK"), a),
                   "kayit_durus_arka": lambda v, a: _sec(v, ("", "YATIK", "DIK"), a)},
    "kapak": {"tip": lambda v, a: _enum(M.KapakTipi, v, a),
              "yarim_bindirme": _bool,
              "katlanma": lambda v, a: _sec(v, ("", "SOL", "SAG", "KARSILIKLI"), a),
              "mentesetipi": lambda v, a: _sec(v, tuple(str(m["kod"]) for m in tn.menteseler()
                                                       if _acik_mi("mentese", str(m["kod"]))), a),
              "mentese_adet": lambda v, a: _tam(v, a, 0, 12),
              "mentese_ust": _SAYI(0, 1000), "mentese_alt": _SAYI(0, 1000),
              "mentese_artim": _SAYI(50, 3000),
              "profil_kulp": lambda v, a: _sec(v, ("",) + tuple(E.KULP_PROFILLERI), a),
              "profil_yerlesim": lambda v, a: _sec(v, ("TAM", "CUBUK"), a),
              "profil_kenar": lambda v, a: _sec(v, ("OTO", "UST", "ALT", "SOL", "SAG"), a),
              "profil_boy": _SAYI(20, 3000)},
    "cekmece": {"tip": lambda v, a: _enum(M.KapakTipi, v, a),
                "ic": _bool,
                "raytipi": lambda v, a: _sec(v, tuple(str(r["kod"]) for r in tn.raylar()
                                                   if _acik_mi("ray", str(r["kod"]))), a),
                "kasa_birlesim": lambda v, a: _sec(v, ("YAN_DISTA", "ON_DISTA"), a),
                "kasa_baglanti": lambda v, a: v if v == "" else _baglanti_kodu(v),
                "kanalli_taban": _bool,
                "taban_malzeme": lambda v, a: "" if v == "" else _malzeme(v),
                "taban_kalinlik": _SAYI(0, 30), "taban_kanal_derinlik": _SAYI(0, 20),
                "taban_alttan": _SAYI(0, 100),
                "kasa_malzeme": lambda v, a: _malzeme(v), "kasa_kalinlik": _SAYI(3, 30),
                "kasa_yukseklik": _SAYI(0, 1000), "kasa_derinlik": _SAYI(0, 1000),
                "kasa_bosluk_sol": _SAYI(-50, 50), "kasa_bosluk_sag": _SAYI(-50, 50),
                "kasa_bosluk_ust": _SAYI(-50, 200), "kasa_bosluk_alt": _SAYI(-50, 200),
                "profil_kulp": lambda v, a: _sec(v, ("",) + tuple(E.KULP_PROFILLERI), a)},
    "usttabla": {"sol_tasma": _SAYI(0, 500), "sag_tasma": _SAYI(0, 500), "on_tasma": _SAYI(0, 500),
                 "arka_tasma": _SAYI(0, 500), "on_yaricap": _SAYI(0, 200), "arka_yaricap": _SAYI(0, 200)},
}
KULP_ALAN = {"konum": lambda v, a: _sec(v, ("yok", "solust", "ortaust", "sagust", "solorta", "orta",
                                             "sagorta", "solalt", "ortaalt", "sagalt"), a),
             "yon": lambda v, a: _enum(M.KulpYon, v, a), "delik_adet": lambda v, a: _tam(v, a, 1, 6),
             "delik_arasi": _SAYI(0, 1000), "yatay_mesafe": _SAYI(0, 1000),
             "dikey_mesafe": _SAYI(0, 1000), "cap": _SAYI(2, 12)}


def _sablon(k: Any, tur: str, ad: Any) -> Any:
    t = TUR_SABLON.get(tur)
    if tur == "usttabla":
        return M.UstTabla()
    if ad in (None, ""):
        ad = tn.alt_sablon_adi(k.sablon, t) if k.sablon else None
    elif not isinstance(ad, str) or ad not in tn.eleman_sablon_adlari(t):
        raise Hata("Tanımda olmayan şablon")
    elif t in ("arkalik", "kulp", "cekmece") and not _acik_mi(t, ad):
        raise Hata("Bu şablon seçilemez")
    return tn.eleman_sablonu(t, ad)


def _secenek_uygula(s: Any, tur: str, secenek: Any) -> None:
    if secenek in (None, {}):
        return
    if not isinstance(secenek, dict):
        raise Hata("Geçersiz seçenekler")
    izin = {**SECENEK["ortak"], **SECENEK.get(tur, {})}
    for alan, v in secenek.items():
        if alan == "bant":
            _bant(s, v)
            continue
        if alan not in izin or not hasattr(s, alan):
            raise Hata(f"Bu alan değiştirilemez: {alan}")
        val = izin[alan](v, alan)
        setattr(s, alan, val)
        if alan == "malzeme" and "kalinlik" not in secenek and hasattr(s, "kalinlik"):
            s.kalinlik = float(tn.kalinlik(val, s.kalinlik))
        if alan == "kasa_malzeme" and "kasa_kalinlik" not in secenek:
            s.kasa_kalinlik = float(tn.kalinlik(val, s.kasa_kalinlik))


def _kulp(sec: Any) -> Any:
    """Kapak/çekmece kulp seçeneği -> Kulp şablonu ya da None (kulp yok)."""
    if sec is None:
        return tn.eleman_sablonu("kulp", None)
    if not isinstance(sec, dict):
        raise Hata("Kulp: geçersiz")
    ad = sec.get("sablon")
    if ad not in (None, "") and ad not in tn.eleman_sablon_adlari("kulp"):
        raise Hata("Kulp şablonu tanımda yok")
    if ad not in (None, "") and not _acik_mi("kulp", ad):
        raise Hata("Bu kulp seçilemez")
    ku = tn.eleman_sablonu("kulp", ad or None)
    for alan, v in sec.items():
        if alan == "sablon":
            continue
        if alan not in KULP_ALAN:
            raise Hata(f"Kulp alanı değiştirilemez: {alan}")
        setattr(ku, alan, KULP_ALAN[alan](v, alan))
    return None if str(ku.konum) == "yok" else ku


def _sabit_gozler(v: Any) -> list[float | None] | None:
    """'400,,300' ya da [400, null, 300]"""
    if v in (None, "", []):
        return None
    if isinstance(v, str):
        v = [x.strip() for x in v.split(",")]
    if not isinstance(v, list) or len(v) > 11:
        raise Hata("Sabit göz ölçüleri: en çok 11 değer")
    out: list[float | None] = []
    for x in v:
        out.append(None if x in (None, "") else _sayi(x, "Sabit göz", 1, OLCU_MAX))
    return out


# ══════════════════════════════════════════════════════════════════════════
# Bölge / göz bulma (tarayıcı yalnız tıklama noktasını yollar)
# ══════════════════════════════════════════════════════════════════════════
def _nokta(v: Any) -> tuple[float, float]:
    if not isinstance(v, (list, tuple)) or len(v) != 2:
        raise Hata("Geçersiz nokta")
    return _sayi(v[0], "x"), _sayi(v[1], "y")


def _bolge(k: Any, noktalar: Any, birlestir: bool) -> tuple[Any, str]:
    if not isinstance(noktalar, list) or not noktalar:
        raise Hata("Önce bir göz seçin")
    if len(noktalar) > 50:
        raise Hata("Çok fazla seçim")
    bolgeler = []
    for n in noktalar:
        x, y = _nokta(n)
        b = E.bolge_sec(k, x, y)
        if b is None:
            raise Hata("Seçilen yerde boş göz yok")
        bolgeler.append(b)
    if len(bolgeler) == 1:
        return bolgeler[0], ""
    if birlestir:
        b = E.bolge_birlestir(bolgeler)
        if b is None:
            raise Hata("Seçilen gözler birleştirilemiyor (yan yana değil)")
        return b, ""
    return bolgeler[-1], "Yalnız son seçilen göze uygulandı"


def _hucre(k: Any, nokta: Any) -> Any:
    x, y = _nokta(nokta)
    for h in E.hucreler(k):
        if h.xbas - 0.5 <= x <= h.xson + 0.5 and h.ybas - 0.5 <= y <= h.yson + 0.5:
            return h
    raise Hata("Bu noktada göz yok")


def _eleman(k: Any, eid: Any) -> Any:
    if not isinstance(eid, str):
        raise Hata("Parça seçin")
    e = k.bul(eid)
    if e is None:
        raise Hata("Parça bulunamadı")
    return e


# ══════════════════════════════════════════════════════════════════════════
# İşlemler
# ══════════════════════════════════════════════════════════════════════════
def _izinli(alan: str, izinler: set[str]) -> None:
    if alan not in izinler:
        raise Hata(f"Bu alanı değiştirme yetkiniz yok: {alan}")


def islem_uygula(k: Any, islem: dict, izinler: set[str]) -> dict:
    """k üzerinde tek işlem. Doner: işleme özel sonuç (mesaj, yeni id'ler...)."""
    tip = islem.get("tip")
    sonuc: dict[str, Any] = {}

    if tip in ("goster", None):
        return sonuc

    if tip == "olcu":
        olc = {}
        for f in OLCU_ALANLARI:
            if f in islem:
                _izinli(f, izinler)
                olc[f] = _sayi(islem[f], {"en": "En", "boy": "Boy", "derinlik": "Derinlik"}[f], OLCU_MIN, OLCU_MAX)
        if olc:
            E.boyutlandir(k, **olc)
        return sonuc

    if tip == "ozellik":
        alanlar = islem.get("alanlar")
        if not isinstance(alanlar, dict):
            raise Hata("Geçersiz alanlar")
        olc = {f: alanlar.pop(f) for f in OLCU_ALANLARI if f in alanlar}
        if alanlar:
            onceki = k.kopya()
            for alan, v in alanlar.items():
                if alan not in ALANLAR:
                    raise Hata(f"Bilinmeyen alan: {alan}")
                _izinli(alan, izinler)
                ALANLAR[alan](k, v)
            E.degisiklik_uygula(k, onceki)
        if olc:
            islem_uygula(k, {"tip": "olcu", **olc}, izinler)
        return sonuc

    if tip == "ekle":
        tur = islem.get("tur")
        if tur not in TUR_SABLON:
            raise Hata("Bilinmeyen eleman")
        _izinli(f"ekle.{tur}", izinler)
        once = {e.id for e in k.elemanlar()}
        s = _sablon(k, tur, islem.get("sablon"))
        _secenek_uygula(s, tur, islem.get("secenek"))
        if tur == "usttabla":
            if k.usttablalar:
                raise Hata("Dolapta tek üst tabla olur")
            E.ust_tabla_ekle(k, sablon=s)
        else:
            b, mesaj = _bolge(k, islem.get("noktalar"), tur in BIRLESEN)
            if mesaj:
                sonuc["mesaj"] = mesaj
            adet_ust = {"kapak": 4, "cekmece": 10, "yataykayit": 2}.get(tur, 10)
            adet = _tam(islem.get("adet", 1), "Adet", 1, adet_ust)
            sabit = _sabit_gozler(islem.get("sabit_gozler"))
            if tur == "dikeybolme":
                E.dikey_bolme_ekle(k, b, adet, sablon=s, sabit_gozler=sabit)
            elif tur in ("yataybolme", "raf", "aski"):
                tip_ = {"yataybolme": M.YatayTip.BOLME, "raf": M.YatayTip.RAF,
                        "aski": M.YatayTip.ASKI}[tur]
                E.yatay_ekle(k, b, 1 if tur == "aski" else adet, tip=tip_, sablon=s, sabit_gozler=sabit)
            elif tur == "yataykayit":
                E.yatay_ekle(k, b, adet, tip=M.YatayTip.KAYIT, sablon=s)
            elif tur == "dikeykayit":
                E.dikey_kayit_ekle(k, b, sablon=s)
            elif tur == "arkalik":
                E.arkalik_ekle(k, b, sablon=s)
            elif tur == "kapak":
                ms = islem.get("menteseler")
                menteseler = None
                if ms is not None:
                    if not isinstance(ms, list) or len(ms) > 6:
                        raise Hata("Açılma yönleri geçersiz")
                    menteseler = [_enum(M.MenteseYeri, m, "Açılma yönü") for m in ms]
                kp = E.kapak_ekle(k, b, adet, sablon=s, menteseler=menteseler)
                ku = _kulp(islem.get("kulp"))
                for x in kp:
                    x.kulplar = [E.kulp_yerlestir(ku, x.menteseyeri, True)] if ku else []
            elif tur == "cekmece":
                kil = _sabit_gozler(islem.get("kilitli"))
                cs = E.cekmece_ekle(k, b, adet, sablon=s, kilitli=kil)
                ku = _kulp(islem.get("kulp"))
                for x in cs:
                    x.kulplar = [E.kulp_yerlestir(ku, M.MenteseYeri.YOK, False)] if ku else []
        sonuc["yeni"] = [e.id for e in k.elemanlar() if e.id not in once]
        return sonuc

    if tip in ("raf_adet", "raf_esit"):
        _izinli("ekle.raf", izinler)
        b, _m = _bolge(k, [islem.get("nokta")], False)
        if tip == "raf_esit":
            sonuc["adet"] = E.raflari_esit_dagit(k, b)
        else:
            n = _tam(islem.get("n"), "Raf sayısı", 0, 20)
            E.raf_adedi_ayarla(k, b, n, _sablon(k, "raf", islem.get("sablon")))
        return sonuc

    if tip == "sil":
        _izinli("duzenle.sil", izinler)
        if islem.get("panel") is not None:
            ad = islem["panel"]
            if ad not in k.paneller:
                raise Hata("Panel bulunamadı")
            _izinli(f"paneller.{ad}", izinler)
            onceki = k.kopya()
            k.paneller[ad].mevcut = False
            E.degisiklik_uygula(k, onceki)
            return sonuc
        _eleman(k, islem.get("id"))
        if not E.sil(k, islem["id"]):
            raise Hata("Silinemedi")
        return sonuc

    if tip == "goz_olcu":
        h = _hucre(k, islem.get("nokta"))
        yon = _sec(islem.get("yon"), ("x", "y"), "Yön")
        deger = _sayi(islem.get("deger"), "Ölçü", 10, OLCU_MAX)
        r = E.hucre_olcu_uygula(k, h, yon, deger)
        if r is None:
            raise Hata("Bu gözün iki yanı da gövde: ölçü kabin ölçüsünden değişir")
        if abs(r - deger) > 0.5:
            sonuc["mesaj"] = f"Yer yetmedi: göz {r:g} mm oldu"
        return sonuc

    if tip == "kilit_ac":
        h = _hucre(k, islem.get("nokta"))
        yon = _sec(islem.get("yon"), ("x", "y"), "Yön")
        if not E.hucre_kilitle(k, h, yon, False):
            raise Hata("Bu gözde kilit yok")
        return sonuc

    if tip == "tasi":
        e = _eleman(k, islem.get("id"))
        dx = _sayi(islem.get("dx", 0), "dx", -OLCU_MAX, OLCU_MAX)
        dy = _sayi(islem.get("dy", 0), "dy", -OLCU_MAX, OLCU_MAX)
        sonuc["kayma"] = E.eleman_tasi(k, e, dx=dx, dy=dy)
        return sonuc

    if tip == "parca":                    # toplu malzeme / bant / damar (+ yönetici: kısaltma)
        idler = islem.get("idler")
        alanlar = islem.get("alanlar")
        if not isinstance(idler, list) or not idler or not isinstance(alanlar, dict):
            raise Hata("Parça seçin")
        onceki = k.kopya()
        if "tum:on" in idler:             # bütün kapak ve çekmece önleri (renk seçimi)
            idler = [e.id for e in k.elemanlar() if isinstance(e, (M.Kapak, M.Cekmece))]
            if not idler:
                return sonuc
        for eid in idler:
            if isinstance(eid, str) and eid.startswith("govde."):
                hedef = k.paneller.get(eid[6:])
                if hedef is None:
                    raise Hata("Parça bulunamadı")
            else:
                hedef = _eleman(k, eid)
            for alan, v in alanlar.items():
                _izinli(f"parca.{alan}", izinler)
                if alan == "malzeme":
                    hedef.malzeme = _malzeme(v)
                    hedef.kalinlik = float(tn.kalinlik(hedef.malzeme, hedef.kalinlik))
                elif alan == "bant":
                    _bant(hedef, v)
                elif alan == "damar":
                    hedef.damar = _sec(v, tuple(M.DAMAR_SECENEK), "Damar")
                elif alan in ("onek", "arkaek"):
                    setattr(hedef, alan, _sayi(v, alan, -500, 500))
                else:
                    raise Hata(f"Bu alan değiştirilemez: {alan}")
        E.degisiklik_uygula(k, onceki)
        return sonuc

    if tip == "grup_duzenle":             # kapak / çekmece grubunu yeni ayarlarla yeniden kur
        e = _eleman(k, islem.get("id"))
        if not isinstance(e, (M.Kapak, M.Cekmece)):
            raise Hata("Yalnız kapak ve çekmece")
        tur = "kapak" if isinstance(e, M.Kapak) else "cekmece"
        _izinli(f"ekle.{tur}", izinler)
        grup = E.grup_uyeleri(k, e)
        b = E.bolge_referanslardan(k, e)
        lst = k.kapaklar if tur == "kapak" else k.cekmeceler
        for x in grup:
            lst.remove(x)
        once = {x.id for x in k.elemanlar()}
        s = _sablon(k, tur, islem.get("sablon"))
        _secenek_uygula(s, tur, islem.get("secenek"))
        adet = _tam(islem.get("adet", len(grup)), "Adet", 1, 4 if tur == "kapak" else 10)
        ku = _kulp(islem.get("kulp"))
        if tur == "kapak":
            ms = islem.get("menteseler")
            menteseler = [_enum(M.MenteseYeri, m, "Açılma yönü") for m in ms] if isinstance(ms, list) else None
            for x in E.kapak_ekle(k, b, adet, sablon=s, menteseler=menteseler):
                x.kulplar = [E.kulp_yerlestir(ku, x.menteseyeri, True)] if ku else []
        else:
            for x in E.cekmece_ekle(k, b, adet, sablon=s, kilitli=_sabit_gozler(islem.get("kilitli"))):
                x.kulplar = [E.kulp_yerlestir(ku, M.MenteseYeri.YOK, False)] if ku else []
        sonuc["yeni"] = [x.id for x in k.elemanlar() if x.id not in once]
        return sonuc

    if tip == "ray_kontrol":              # çekmece penceresi: hangi ray sığar
        s = _sablon(k, "cekmece", islem.get("sablon"))
        _secenek_uygula(s, "cekmece", islem.get("secenek"))
        sonuc["raylar"] = {str(r["kod"]): bool(E.ray_sigar_mi(k, s, str(r["kod"]))) for r in tn.raylar()}
        return sonuc

    if tip == "yeni_uid":                 # kopya dolap
        k.uid = M.yeni_uid()
        return sonuc

    raise Hata("Bilinmeyen işlem")


# ══════════════════════════════════════════════════════════════════════════
# Görünüm (tarayıcının çizdiği veri)
# ══════════════════════════════════════════════════════════════════════════
def derle(k: Any) -> Any:
    E.yeniden_hesapla(k)
    return regenerate(new_cabinet(k.kabinno or "kabin", k.aciklama or "kabin", "interaktif",
                                  C.kabin_parametreleri(k, TANIM_KLASORU)), rules=rs)


def _r(v: float) -> float:
    return round(float(v), 1) + 0.0


def _kutu(b: Any) -> dict[str, float]:
    return {"x1": _r(b.xbas), "x2": _r(b.xson), "y1": _r(b.ybas), "y2": _r(b.yson),
            "z1": _r(b.zbas), "z2": _r(b.zson)}


def _tur(e: Any) -> str:
    if isinstance(e, M.YatayEleman):
        return {M.YatayTip.RAF: "raf", M.YatayTip.BOLME: "yataybolme",
                M.YatayTip.KAYIT: "yataykayit", M.YatayTip.ASKI: "aski"}[e.yataytip]
    return str(e.tur)


_MALIYET_ON = (("Malzeme ", "malzeme:"), ("Kenar bandi ", "bant:"), ("Baglanti ", "baglanti:"),
               ("Kanal", "donati:kanal"), ("Mentese", "donati:mentese"), ("Kulp", "donati:kulp"),
               ("Cekmece rayi", "donati:ray"), ("Ayak", "donati:ayak"))


def _maliyet_anahtari(ad: str) -> str:
    """R.maliyet kalem adından kararlı anahtar (fiyat eşleştirmesi için)."""
    for on, anahtar in _MALIYET_ON:
        if ad.startswith(on):
            return anahtar + ad[len(on):].strip() if anahtar.endswith(":") else anahtar
    return "diger:" + ad


def gorunum(k: Any, cab: Any, rol: str) -> dict[str, Any]:
    adlar = {p.id: p.name for p in cab.panels}
    el_tur = {e.id: _tur(e) for e in k.elemanlar()}

    gozler = []
    for h in E.hucreler(k):
        gozler.append({"x1": _r(h.xbas), "y1": _r(h.ybas), "x2": _r(h.xson), "y2": _r(h.yson),
                       "kilit": {"x": bool(E.hucre_kilitli(k, h, "x")),
                                 "y": bool(E.hucre_kilitli(k, h, "y"))}})

    p2: list[dict[str, Any]] = []
    for ad, p in k.paneller.items():
        if p.mevcut:
            p2.append({"id": f"govde.{ad}", "tur": "govde", "ad": adlar.get(C.GOVDE_ID.get(ad, ""), ad),
                       "katman": "govde", "kutu": _kutu(p.kutu), "malzeme": p.malzeme})
    for e in k.elemanlar():
        if getattr(e, "gizle", False):
            continue
        tur = _tur(e)
        pid = C.panel_id(e)
        d = {"id": e.id, "tur": tur, "ad": adlar.get(pid, tur), "malzeme": e.malzeme,
             "kalinlik": e.kalinlik, "katman": "on" if tur in ("kapak", "cekmece") else "ic",
             "kutu": _kutu(e.kutu), "grup": getattr(e, "grup", "")}
        if tur == "kapak":
            n = e.mentese_adet or E.mentese_otomatik_adet(e)
            d.update(menteseyeri=e.menteseyeri.name, mentese=int(n),
                     menteseler=[_r(x) for x in E.mentese_konumlari(e)] if e.menteseyeri.name != "YOK" else [],
                     kulp=[ku.konum for ku in e.kulplar], tip=e.tip.name)
        elif tur == "cekmece":
            d.update(raytipi=e.raytipi, tip=e.tip.name, ic=bool(getattr(e, "ic", False)),
                     kulp=[ku.konum for ku in e.kulplar],
                     kasa={ad: {"en": _r(b.xson - b.xbas), "boy": _r(b.yson - b.ybas),
                                "derinlik": _r(b.zson - b.zbas)} for ad, b in (e.kasa or {}).items()})
        p2.append(d)

    p3 = []
    for p in gorunum_panelleri(cab):
        pid = p.id
        bas = pid.split(".")[0]
        eid = pid.split(".")[1] if "." in pid else ""
        if bas in ("side", "panel"):
            tur = "govde"
        elif bas == "cekmece":
            tur = "cekmece" if pid.count(".") == 1 else "cekmece_kasa"
        elif eid in el_tur:
            tur = el_tur[eid]
        else:
            tur = bas
        p3.append({"id": pid, "eleman": eid if eid in el_tur else "", "ad": p.name, "rol": p.role.name,
                   "tur": tur, "malzeme": p.material.id,
                   "koseler": [[_r(c.x), _r(c.y), _r(c.z)] for c in W.panel_corners(p)]})

    satirlar = R.parca_listesi(cab, k)
    pl = []
    for s in satirlar:
        row = {"cins": s.cins, "ad": s.aciklama, "boy": s.boy, "en": s.en, "kalinlik": s.kalinlik,
               "adet": s.adet, "malzeme": s.malzeme, "bant": [s.kbboy1, s.kbboy2, s.kben1, s.kben2],
               "panel_id": s.panel_id}
        if rol == "yonetici":               # müşteri delik sayısını görmez
            row.update(delik_ust=s.delik_ust, delik_alt=s.delik_alt, delik_yan=s.delik_yan,
                       kanal=s.kanal, freze=s.freze)
        pl.append(row)

    donati = [{"ad": r.label, "adet": r.qty} for r in H.hardware_rows(cab, rules=rs, qty=max(1, int(k.adet)))]
    maliyet = [{"anahtar": _maliyet_anahtari(m.ad), "ad": m.ad, "miktar": m.miktar, "birim": m.birim}
               for m in R.maliyet(satirlar, cab, tn, k)]
    doktor = [{"seviye": b.seviye, "kod": b.kod, "mesaj": b.mesaj, "parcalar": list(b.parcalar)}
              for b in D.teshis(cab, k, tn)]
    uyarilar = list(dict.fromkeys(list(k.uyarilar or []) + list(E.cakisma_kontrol(k) or [])))
    return {
        "govde": {"en": k.en, "boy": k.boy, "derinlik": k.derinlik, "govde_boyu": k.govde_boyu,
                  "ayaktipi": k.ayaktipi.name, "ayakyuksekligi": k.ayakyuksekligi},
        "ozellik": ozellikler(k),
        "gozler": gozler, "parcalar2b": p2, "parcalar3b": p3,
        "parca_listesi": pl, "donati": donati, "maliyet": maliyet,
        "ozet": {"parca": len(satirlar), "m2": round(sum(s.alan_m2 for s in satirlar), 3),
                 "bant_m": round(sum(s.bant_m for s in satirlar), 2)},
        "uyarilar": uyarilar, "doktor": doktor,
        "hata_var": any(b["seviye"] == "hata" for b in doktor),
    }


# ══════════════════════════════════════════════════════════════════════════
# HTTP
# ══════════════════════════════════════════════════════════════════════════
def _hata(mesaj: str, kod: int = 422) -> JSONResponse:
    return JSONResponse({"error": mesaj}, status_code=kod)


@app.get("/bilgi")
def bilgi() -> dict:
    sab = []
    for ad in tn.kabin_sablon_adlari():
        k = tn.kabin_sablonu(ad)
        sab.append({"ad": ad, "en": k.en, "boy": k.boy, "derinlik": k.derinlik,
                    "ayaktipi": k.ayaktipi.name, "ayakyuksekligi": k.ayakyuksekligi,
                    "ust": k.paneller["ust"].mevcut,
                    "koseler": [k.solust.name, k.sagust.name, k.solalt.name, k.sagalt.name],
                    "alt_sablonlar": {t: tn.alt_sablon_adi(ad, t) for t in
                                      ("arkalik", "kapak", "kulp", "cekmece", "raf", "dikeybolme")}})
    return {
        "motor_surumu": MOTOR_SURUMU, "tanim_surumu": TANIM_SURUMU,
        "kabin_sablonlari": sab,
        "eleman_sablonlari": {t: tn.eleman_sablon_adlari(t) for t in
                              ("dikeybolme", "yataybolme", "raf", "yataykayit", "dikeykayit",
                               "arkalik", "kapak", "kulp", "cekmece")},
        "arkaliklar": [{"ad": a, "tip": tn.eleman_sablonu("arkalik", a).tip.name,
                        "kalinlik": tn.eleman_sablonu("arkalik", a).kalinlik}
                       for a in tn.eleman_sablon_adlari("arkalik")],
        "kulplar": {a: {f: (getattr(tn.eleman_sablonu("kulp", a), f).name
                            if isinstance(getattr(tn.eleman_sablonu("kulp", a), f), Enum)
                            else getattr(tn.eleman_sablonu("kulp", a), f))
                        for f in KULP_ALAN} for a in tn.eleman_sablon_adlari("kulp")},
        "malzemeler": [{"kod": m["kod"], "ad": m.get("ad", m["kod"]), "kalinlik": m.get("kalinlik")}
                       for m in tn.malzemeler()],
        "bantlar": [{"kod": b["kod"], "ad": b.get("ad", b["kod"])} for b in tn.bantlar()],
        "raylar": [{"kod": r["kod"], "ad": r.get("ad", r["kod"]),
                    "kasa_derinlik": r.get("kasa_derinlik")} for r in tn.raylar()],
        "menteseler": [{"kod": m["kod"], "ad": m.get("ad", m["kod"])} for m in tn.menteseler()],
        "baglantilar": [{"kod": b["kod"], "ad": b.get("ad", b["kod"])} for b in tn.baglantilar()],
        "baglanti_sablonlari": tn.baglanti_sablon_adlari(),
        "profil_kulplar": {k: v["ad"] for k, v in E.KULP_PROFILLERI.items()},
        "damar": list(M.DAMAR_SECENEK),
        "alanlar": sorted(ALANLAR) + list(OLCU_ALANLARI),
    }


@app.post("/islem")
async def islem(request: Request) -> JSONResponse:
    ham = await request.body()
    if len(ham) > MAX_ISTEK:
        return _hata("İstek çok büyük", 413)
    try:
        govde = json.loads(ham, parse_constant=lambda c: (_ for _ in ()).throw(Hata("Geçersiz sayı")))
    except Hata as e:
        return _hata(str(e))
    except Exception:
        return _hata("Geçersiz istek", 400)
    if not isinstance(govde, dict):
        return _hata("Geçersiz istek", 400)
    rol = govde.get("rol")
    if rol not in ("musteri", "yonetici"):
        return _hata("Geçersiz rol", 400)
    izinler = set(govde.get("izinler") or [])
    kodlar = govde.get("kapali") if rol != "yonetici" else None
    if kodlar is not None and (not isinstance(kodlar, dict) or
                               not all(isinstance(v, list) for v in kodlar.values())):
        return _hata("Geçersiz kodlar", 400)
    islemler = govde.get("islem")
    if isinstance(islemler, dict):
        islemler = [islemler]
    if not isinstance(islemler, list) or len(islemler) > 20:
        return _hata("Geçersiz işlem", 400)
    global KAPALI
    with _KILIT:
        KAPALI = {t: {str(x) for x in v} for t, v in kodlar.items()} if kodlar else {}
        try:
            _sonlu(islemler, "islem")
            ilk = islemler[0] if islemler else {}
            if isinstance(ilk, dict) and ilk.get("tip") == "yeni":
                ad = ilk.get("sablon")
                if ad not in tn.kabin_sablon_adlari() or not _acik_mi("sablon", ad):
                    raise Hata("Tanımda olmayan şablon")
                k = tn.kabin_sablonu(ad)
                olc = {f: _sayi(ilk[f], f, OLCU_MIN, OLCU_MAX) for f in OLCU_ALANLARI if ilk.get(f) is not None}
                if olc:
                    E.boyutlandir(k, **olc)
                islemler = islemler[1:]
            else:
                k = kabin_yukle(govde.get("kabin"), rol)
            sonuclar = []
            for isl in islemler:
                if not isinstance(isl, dict):
                    raise Hata("Geçersiz işlem")
                E.yeniden_hesapla(k)
                sonuclar.append(islem_uygula(k, dict(isl), izinler))
            E.yeniden_hesapla(k)
            if sum(1 for _ in k.elemanlar()) > MAX_ELEMAN:
                raise Hata("Çok fazla parça")
            cab = derle(k)
            out = gorunum(k, cab, rol)
            kd = k.to_dict()
            if rol != "yonetici":
                kd = _yasaklari_bosalt(kd)
            out["kabin"] = kd
            out["sonuc"] = sonuclar[-1] if sonuclar else {}
            out["motor_surumu"], out["tanim_surumu"] = MOTOR_SURUMU, TANIM_SURUMU
            return JSONResponse(out)
        except (Hata, E.EklemeHatasi) as e:
            return _hata(str(e))
        except Exception:                 # iz ve sürüm bilgisi dışarı sızmaz
            return _hata("Motor bu işlemi yapamadı", 500)
