"""Fuar standı tasarımı testleri. Motor kodu repoda olmadığından motor burada sahte bir servisle
taklit edilir (işlemleri kaydeder, ölçüyü uygular); yapay zekâ çağrısı da taklit edilir.

    python3 -m pytest tests/test_stand.py -v
"""
import asyncio
import json
import math
import os
import re
import sys
import tempfile

import pytest

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, APP)

import stand as S  # noqa: E402
import stand_donati as SD  # noqa: E402


# ── sahte motor ─────────────────────────────────────────────────────────────
def _view(k):
    g = {"en": k["en"], "boy": k["boy"], "derinlik": k["derinlik"]}
    return {"kabin": k, "govde": g, "gozler": [{"x1": 18, "y1": 18, "x2": k["en"] - 18, "y2": k["boy"] - 18}],
            "hata_var": False, "doktor": [], "ozet": {"parca": 5 + len(k["ops"])}, "maliyet": [],
            "parcalar2b": [], "parcalar3b": [], "ozellik": {"adet": k["adet"]},
            "motor_surumu": "test", "tanim_surumu": "test", "sonuc": {"raylar": {"R1": True}}}


async def sahte_islem(kabin, islemler, rol, izinler):
    k = json.loads(json.dumps(kabin)) if kabin else None
    for i in islemler:
        if i["tip"] == "yeni":
            k = {"sablon": i["sablon"], "en": 600, "boy": 720, "derinlik": 560, "adet": 1, "uid": 1, "ops": []}
        elif i["tip"] == "olcu":
            k.update({f: i[f] for f in ("en", "boy", "derinlik") if f in i})
        elif i["tip"] == "ozellik" and "adet" in i.get("alanlar", {}):
            k["adet"] = i["alanlar"]["adet"]
        elif i["tip"] == "yeni_uid":
            k["uid"] += 1
        elif i["tip"] == "ozellik" or i["tip"] in ("ekle", "goz_olcu", "parca"):
            k["ops"].append(i["tip"] + ":" + str(i.get("tur", "")))
    return _view(k)


async def sahte_bilgi(yenile=False):
    return {"kabin_sablonlari": [{"ad": "Alt Dolap"}, {"ad": "Boy Dolap"}], "malzemeler": []}


@pytest.fixture(scope="module")
def o():
    from fastapi.testclient import TestClient
    import config
    import db
    config.DB_PATH = db.DB_PATH = os.path.join(tempfile.mkdtemp(), "stand.db")
    db.init()
    import kabin_istemci
    kabin_istemci.islem, kabin_istemci.bilgi = sahte_islem, sahte_bilgi
    os.environ.pop("ANTHROPIC_API_KEY", None)
    import main
    admin = TestClient(main.app)
    assert admin.post("/login", data={"username": "admin", "password": "admin123"},
                      follow_redirects=False).status_code == 303
    return {"db": db, "app": main.app, "TC": TestClient, "admin": admin}


FORM = {"en": "6", "derinlik": "4", "yukseklik": "3", "acik": ["on", "sol"], "sektor": "Kozmetik"}
DONATI = {"ekran": "tv_55", "ekran_adet": "1", "toplanti": "4", "bar": "1", "oturma": "berjer", "depo": "1",
          "brosur": "1", "bitki": "2", "sandalye": "sandalye_ahsap", "tabure": "tabure_beyaz", "koltuk": "berjer_deri"}


# ── birim: yerleşim ─────────────────────────────────────────────────────────
def test_kurali_alternatifler_cakismasiz():
    for acik in (["on"], ["sol"], ["on", "sol"], ["on", "sol", "sag"], ["sol", "sag"], list(S.CEPHELER)):
        for W, D in ((2000, 2000), (3000, 3000), (6000, 4000), (10000, 6000), (20000, 20000)):
            g = S.girdi_dogrula(W, D, 3000, acik)
            alt, kaynak, _ = asyncio.run(S.alternatifler_uret(g))
            assert kaynak == "kural" and len(alt) == 3
            for a in alt:
                assert not a["notlar"], (acik, W, D, a["notlar"])
                izler = [S.ayak_izi(m) for m in a["moduller"]]
                for i, x in enumerate(izler):
                    assert 0 <= x[0] and x[0] + x[2] <= W and 0 <= x[1] and x[1] + x[3] <= D
                    assert not any(S._cakisir(x, y) for y in izler[i + 1:])
            if W >= 3000:
                assert all(any(m["tip"] == "banko" for m in a["moduller"]) for a in alt)


def test_yerlesim_duzelt_sinirlar():
    g = S.girdi_dogrula(4000, 3000, 2500, ["on"])
    m, notlar = S.yerlesim_duzelt([
        {"tip": "raf_unitesi", "en": 5000, "boy": 9000, "derinlik": 10, "raf": 99, "x": 3900, "y": -50, "yon": "on"},
        {"tip": "raf_unitesi", "en": 1000, "boy": 2000, "derinlik": 400, "raf": 4, "x": 3500, "y": 0, "yon": "on"},
        {"tip": "uydurma", "en": 1000},
        {"tip": "banko", "en": 1600, "boy": 1050, "derinlik": 600, "x": 1000, "y": 2400, "yon": "arka"},
    ], g)
    assert [x["tip"] for x in m] == ["raf_unitesi", "banko"]
    r = m[0]
    assert (r["en"], r["derinlik"], r["raf"]) == (1200, 300, 6)
    assert r["boy"] == 2400                                  # duvar yüksekliği - 100
    assert r["x"] == 4000 - 1200 and r["y"] == 0             # içeri alındı
    assert any("çakıştığı" in n for n in notlar)


def test_girdi_hatalari():
    with pytest.raises(ValueError, match="cephe"):
        S.girdi_dogrula(6000, 4000, 3000, [])
    with pytest.raises(ValueError, match="Genişlik"):
        S.girdi_dogrula(500, 4000, 3000, ["on"])
    with pytest.raises(ValueError, match="yüksekliği"):
        S.girdi_dogrula(6000, 4000, 9000, ["on"])


def test_ekstralar_ve_tarif():
    g = S.girdi_dogrula(6000, 4000, 3000, ["on", "sol"])
    e = {k["kod"]: k for k in S.ekstralar(g, {"spot": 0, "grafik_m2": 0})}
    assert e["duvar"]["miktar"] == pytest.approx(6 * 3 + 4 * 3)      # arka + sağ duvar
    assert e["zemin"]["miktar"] == 24 and e["spot"]["miktar"] == 8
    t = S.tarif({"tip": "vitrin_dolabi", "ad": "V", "en": 1000, "boy": 2200, "derinlik": 400, "raf": 2, "kapak": 2})
    assert t["sablon"] == "Boy Dolap" and [a["tip"] for a in t["adimlar"]][:2] == ["olcu", "ekle"]


# ── yapay zekâ ──────────────────────────────────────────────────────────────
def test_ai_cevabi_dogrulanir(monkeypatch):
    async def ai(girdi, anahtar, setler=()):
        assert anahtar == "sk-ant-test"
        return [{"ad": "Ürün odaklı", "konsept": "x", "spot": 6, "grafik_m2": 4, "moduller": [
                    {"tip": "raf_unitesi", "ad": "Raf", "en": 1000, "boy": 2200, "derinlik": 400, "raf": 4, "kapak": 0,
                     "x": 0, "y": 0, "yon": "on"},
                    {"tip": "raf_unitesi", "ad": "Raf 2", "en": 1000, "boy": 2200, "derinlik": 400, "raf": 4, "kapak": 0,
                     "x": 500, "y": 0, "yon": "on"},                       # çakışır → çıkar
                    {"tip": "masa", "ad": "?", "en": 1, "boy": 1, "derinlik": 1, "raf": 0, "kapak": 0, "x": 0, "y": 0, "yon": "on"}]},
                {"ad": "Boş", "konsept": "", "spot": 0, "grafik_m2": 0, "moduller": []}]
    monkeypatch.setattr(S, "ai_alternatifler", ai)
    g = S.girdi_dogrula(6000, 4000, 3000, ["on"])
    alt, kaynak, neden = asyncio.run(S.alternatifler_uret(g, "sk-ant-test"))
    assert kaynak == "ai" and not neden and len(alt) == 1
    assert len(alt[0]["moduller"]) == 1 and alt[0]["notlar"]
    assert {k["kod"]: k["miktar"] for k in alt[0]["ekstralar"]}["spot"] == 6

    async def bozuk(girdi, anahtar, setler=()):
        raise RuntimeError("ağ yok")
    monkeypatch.setattr(S, "ai_alternatifler", bozuk)
    alt, kaynak, neden = asyncio.run(S.alternatifler_uret(g, "sk-ant-test"))
    assert kaynak == "kural" and len(alt) == 3 and "kullanılamadı" in neden


def test_ai_istegi_bicimi(monkeypatch):
    """SDK'ya giden istek: model, yapılandırılmış çıktı şeması, yedek model; cevap JSON'dan okunur."""
    import types
    gelen = {}

    class Mesajlar:
        async def create(self, **kw):
            gelen.update(kw)
            metin = json.dumps({"alternatifler": [{"ad": "A", "konsept": "", "spot": 1, "grafik_m2": 1, "moduller": []}]})
            return types.SimpleNamespace(stop_reason="end_turn", content=[types.SimpleNamespace(type="text", text=metin)])

    class Istemci:
        def __init__(self, **kw):
            self.beta = types.SimpleNamespace(messages=Mesajlar())

    import anthropic
    monkeypatch.setattr(anthropic, "AsyncAnthropic", Istemci)
    g = S.girdi_dogrula(6000, 4000, 3000, ["on"], sektor="Mobilya")
    r = asyncio.run(S.ai_alternatifler(g, "sk-ant-x"))
    assert r[0]["ad"] == "A"
    assert gelen["model"] == S.MODEL and gelen["fallbacks"] == "default"
    assert gelen["output_config"]["format"]["schema"] == S._SEMA
    assert "Mobilya" in gelen["messages"][0]["content"] and "Sıra stand" in gelen["messages"][0]["content"]


# ── akış: tasarım → seçim → sepet → sipariş → API ──────────────────────────
def test_stand_akisi(o):
    db = o["db"]
    c = o["TC"](o["app"])                                     # misafir
    assert "Stand tasarla" in c.get("/").text
    r = c.get("/stand")
    assert r.status_code == 200 and "Hangi cepheler açık" in r.text
    r = c.post("/stand/uret", data={**FORM, "acik": []})
    assert "En az bir cephe" in r.text
    r = c.post("/stand/uret", data={**FORM, "en": "abc"})
    assert "metre" in r.text
    r = c.post("/stand/uret", data=FORM, follow_redirects=False)
    assert r.status_code == 303
    url = r.headers["location"]
    sid = int(url.rsplit("/", 1)[1])
    s = c.get(url).text
    assert s.count("Bu tasarımı seç") == 3 and "<svg" in s and "Köşe stand" in s
    assert "Hazır yerleşim kurallarıyla" in s                   # anahtar yok → kurallı
    assert o["TC"](o["app"]).get(url, follow_redirects=False).status_code == 303   # başkası göremez

    # seç → sepet (her farklı modül bir dolap, adetli)
    assert c.post(f"/stand/{sid}/sec/0", follow_redirects=False).headers["location"] == "/magaza/sepet"
    sahip = db.one("SELECT sahip FROM stand_proje WHERE id=?", (sid,))["sahip"]
    sepet = db.query("SELECT * FROM kabin_taslak WHERE musteri=? AND sepette=1", (sahip,))
    p = json.loads(db.one("SELECT alternatifler_json FROM stand_proje WHERE id=?", (sid,))["alternatifler_json"])[0]
    assert len(sepet) == len(p["kabinler"]) and all(t["stand_id"] == sid for t in sepet)
    assert sum(json.loads(t["kabin_json"])["adet"] for t in sepet) == len(p["moduller"])
    s = c.get("/magaza/sepet").text
    assert f"Fuar standı #{sid}" in s and "Duvar paneli" in s and "Stand ve dolap" not in s   # misafir: giriş iste
    # başka alternatif seçilince önceki stand dolapları sepetten çıkar
    c.post(f"/stand/{sid}/sec/2")
    p2 = json.loads(db.one("SELECT alternatifler_json FROM stand_proje WHERE id=?", (sid,))["alternatifler_json"])[2]
    assert len(db.query("SELECT id FROM kabin_taslak WHERE musteri=? AND sepette=1", (sahip,))) == len(p2["kabinler"])

    # üye ol → misafir standı ve dolapları hesaba geçer → sipariş
    c.post("/magaza/kayit", data={"full_name": "Fuar Firma", "username": "fuar@x.com", "password": "1234"})
    assert db.one("SELECT sahip FROM stand_proje WHERE id=?", (sid,))["sahip"] == "fuar@x.com"
    assert "Stand ve dolap siparişini ver" in c.get("/magaza/sepet").text
    r = c.post("/kabin/siparis", data={"proje": "Fuar 2027", "note": "Kapıya teslim"}, follow_redirects=False)
    oid = int(r.headers["location"].rsplit("/", 1)[1])
    od = db.one("SELECT * FROM cabinet_orders WHERE id=?", (oid,))
    st = json.loads(od["stand_json"])
    assert st[0]["id"] == sid and st[0]["alternatif"]["ad"] == p2["ad"]
    assert od["note"].startswith("Kapıya teslim") and f"FUAR STANDI #{sid}" in od["note"] and "Duvar paneli" in od["note"]
    lines = db.query("SELECT * FROM cabinet_order_lines WHERE order_id=?", (oid,))
    assert lines and all(l["note"] == f"Fuar standı #{sid} modülü" for l in lines)
    ms = c.get(f"/kabin/siparis/{oid}").text
    assert f"Fuar standı #{sid}" in ms and "Not: Kapıya teslim" in ms and "FUAR STANDI #" not in ms

    # TitaniumCam API: stand bilgisi siparişle birlikte
    from routers.titanium_api import _siparis_json
    j = _siparis_json(od)
    assert j["stands"][0]["id"] == sid and j["stands"][0]["alternatif"]["ekstralar"]
    assert all(l["note"].startswith("Fuar standı") for l in j["lines"])
    # yönetici sipariş ekranında stand kartı
    a = o["admin"].get(f"/titaniumcam/kabin/siparis/{oid}").text
    assert f"Fuar standı #{sid}" in a and "Duvar paneli" in a


def test_yonetici_ayar_ve_ai(o, monkeypatch):
    a = o["admin"]
    s = a.get("/titaniumcam/stand").text
    assert "Anahtar tanımlı değil" in s and "Müşteri stand tasarımları" in s
    assert "/titaniumcam/stand" in a.get("/titaniumcam/admin").text
    r = a.post("/titaniumcam/stand/ayar", data={"anahtar": "yanlis", "ai": "1", "acik": "1"}, follow_redirects=True)
    assert "geçersiz" in r.text
    a.post("/titaniumcam/stand/ayar", data={"anahtar": "sk-ant-gizli-1234", "ai": "1", "acik": "1", "gunluk": "50",
                                            "b:duvar": "450", "b:zemin": "300", "b:spot": "750", "b:grafik": "900"})
    s = a.get("/titaniumcam/stand").text
    assert "Anahtar tanımlı (…1234)" in s and "sk-ant-gizli" not in s      # anahtar ekranda görünmez
    from routers.stand import stand_ayar, ai_anahtari
    assert stand_ayar()["birim"]["duvar"] == 450 and stand_ayar()["gunluk"] == 50 and ai_anahtari() == "sk-ant-gizli-1234"

    # müşteri üretimi yapay zekâ ile
    async def ai(girdi, anahtar, setler=()):
        return [{"ad": "Yapay", "konsept": "k", "spot": 4, "grafik_m2": 2, "moduller": [
            {"tip": "banko", "ad": "Banko", "en": 1600, "boy": 1050, "derinlik": 600, "raf": 1, "kapak": 0,
             "x": 2000, "y": 3000, "yon": "arka"}]}]
    monkeypatch.setattr(S, "ai_alternatifler", ai)
    c = o["TC"](o["app"])
    url = c.post("/stand/uret", data=FORM, follow_redirects=False).headers["location"]
    s = c.get(url).text
    assert "Yapay zekâ ile tasarlandı" in s and "Yapay" in s
    assert "/titaniumcam/stand" not in s and "sk-ant" not in s

    # kapalıyken müşteri üretemez
    a.post("/titaniumcam/stand/ayar", data={"ai": "1"})
    assert "şu an kapalı" in o["TC"](o["app"]).post("/stand/uret", data=FORM).text
    a.post("/titaniumcam/stand/ayar", data={"ai": "1", "acik": "1", "anahtar_sil": "1"})
    assert ai_anahtari() == ""


def test_hiz_siniri(o):
    c = o["TC"](o["app"])
    for _ in range(8):
        assert c.post("/stand/uret", data=FORM, follow_redirects=False).status_code == 303
    assert "Çok sık" in c.post("/stand/uret", data=FORM).text


def test_plan_svg():
    g = S.girdi_dogrula(6000, 4000, 3000, ["on"])
    alt = asyncio.run(S.alternatifler_uret(g))[0][0]
    svg = S.plan_svg(g, alt["moduller"])
    assert svg.startswith("<svg") and svg.count("<rect") >= len(alt["moduller"]) + 3 and "koridor" in svg
    assert re.search(r'<title>[^<]*Karşılama bankosu', svg)


# ── donatılar ───────────────────────────────────────────────────────────────
def _girdi_donatili(W=6000, D=4000, acik=("on",), **ek):
    K = SD.varsayilan_katalog()
    g = S.girdi_dogrula(W, D, 3000, list(acik))
    g["donati"] = SD.donati_dogrula({**DONATI, **ek}, K)
    return g, K


def test_donati_secimi_ve_setler():
    K = SD.varsayilan_katalog()
    s = SD.donati_dogrula({"ekran": "yok_boyle", "toplanti": "5", "sandalye": "uydurma", "bar": "9", "depo": "on"}, K)
    assert s["ekran"] == "tv_43" and s["toplanti"] in (4, 6) and s["sandalye"] == "sandalye_ofis"
    assert s["bar"] == 4 and s["depo"] is True
    g, K = _girdi_donatili()
    st = {x["id"]: x for x in SD.setler(g["donati"], K, g)}
    assert set(st) == {"depo", "ekran_1", "toplanti", "oturma", "bar_1", "brosur_1", "bitki_1", "bitki_2"}
    masa, *sandalyeler = st["toplanti"]["parcalar"]
    assert len(sandalyeler) == 4 and all(p["kod"] == "sandalye_ahsap" for p in sandalyeler)
    for p in sandalyeler:                 # her sandalye masanın merkezine bakar
        a = math.radians(p["aci"])
        bak = (-math.sin(a), math.cos(a))
        hedef = (masa["u"] - p["u"], masa["v"] - p["v"])
        n = math.hypot(*hedef)
        assert bak[0] * hedef[0] / n + bak[1] * hedef[1] / n > 0.99
    # dikdörtgen masa 6 kişi: 2 uç + 2×2 uzun kenar
    s6 = SD.donati_dogrula({"toplanti": 6, "toplanti_masasi": "toplanti_dikdortgen"}, K)
    t6 = next(x for x in SD.setler(s6, K, g) if x["id"] == "toplanti")
    assert len(t6["parcalar"]) == 7 and t6["en"] > 1600


def test_donatili_kurali_yerlesim():
    for acik in (("on",), ("on", "sol"), ("on", "sol", "sag")):
        g, K = _girdi_donatili(8000, 5000, acik)
        alt, kaynak, _ = asyncio.run(S.alternatifler_uret(g, None, K))
        for a in alt:
            assert not a["notlar"], (acik, a["ad"], a["notlar"])
            assert len(a["donatilar"]) == 8 and any(m["tip"] == "banko" for m in a["moduller"])
            izler = [S.ayak_izi(x) for x in a["moduller"] + a["donatilar"]]
            for i, x in enumerate(izler):
                assert 0 <= x[0] and x[0] + x[2] <= 8000 and 0 <= x[1] and x[1] + x[3] <= 5000
                assert not any(S._cakisir(x, y) for y in izler[i + 1:])
            ekran = next(d for d in a["donatilar"] if d["tur"] == "ekran")
            assert ekran["montaj"] == "duvar" and S._duvara_sirt(ekran, g)
            depo = next(d for d in a["donatilar"] if d["tur"] == "depo")
            assert S._duvara_sirt(depo, g)
            say = {e["kod"]: e["miktar"] for e in a["ekstralar"]}
            assert say["don:sandalye_ahsap"] == 4 and say["don:tabure_beyaz"] == 2 and say["don:berjer_deri"] == 2
            assert say["don:tv_55"] == 1 and say["don:depo_odasi"] == 1 and say["don:bitki"] == 2
            svg = S.plan_svg(g, a["moduller"], donatilar=a["donatilar"])
            assert "DEPO" in svg and svg.count("<title>Ahşap sandalye") == 4
    # ada standı: duvar yok → ekran ayaklı standla
    g, K = _girdi_donatili(8000, 6000, S.CEPHELER)
    a = asyncio.run(S.alternatifler_uret(g, None, K))[0][0]
    ekran = next(d for d in a["donatilar"] if d["tur"] == "ekran")
    assert ekran["montaj"] == "ayakli" and any(p["kod"] == "tv_standi" for p in ekran["parcalar"])
    # küçük standa sığmayan set müşteriye yazılır
    g, K = _girdi_donatili(3000, 3000)
    assert any("sığmadı" in n for n in asyncio.run(S.alternatifler_uret(g, None, K))[0][0]["notlar"])


def test_ai_donati_yerlesimi(monkeypatch):
    g, K = _girdi_donatili(8000, 5000, ("on",))
    gelen = {}

    async def ai(girdi, anahtar, setler=()):
        gelen["setler"] = [x["id"] for x in setler]
        return [{"ad": "A", "konsept": "", "spot": 6, "grafik_m2": 3,
                 "moduller": [{"tip": "raf_unitesi", "ad": "Raf", "en": 1000, "boy": 2200, "derinlik": 400, "raf": 4,
                               "kapak": 0, "x": 3000, "y": 0, "yon": "on"}],
                 "donatilar": [{"set": "ekran_1", "x": 3500, "y": 2500, "yon": "on", "montaj": "duvar"},   # duvarda değil
                               {"set": "toplanti", "x": 2900, "y": 0, "yon": "on", "montaj": ""},          # rafa biner
                               {"set": "depo", "x": 0, "y": 0, "yon": "on", "montaj": ""},
                               {"set": "uydurma", "x": 0, "y": 0, "yon": "on", "montaj": ""}]}]
    monkeypatch.setattr(S, "ai_alternatifler", ai)
    alt, kaynak, _ = asyncio.run(S.alternatifler_uret(g, "sk-ant-x", K))
    a = alt[0]
    assert kaynak == "ai" and "toplanti" in gelen["setler"]
    d = {x["id"]: x for x in a["donatilar"]}
    assert d["ekran_1"]["montaj"] == "ayakli" and (d["ekran_1"]["x"], d["ekran_1"]["y"]) == (3500, 2500)
    assert (d["depo"]["x"], d["depo"]["y"]) == (0, 0)
    assert not S._cakisir(S.ayak_izi(d["toplanti"]), S.ayak_izi(a["moduller"][0]))     # kurallarla başka yere
    assert len(a["donatilar"]) == 8
    assert "toplanti: Toplantı alanı (4 kişi)" in S._kullanici_istemi(g, SD.setler(g["donati"], K, g))


def test_donatili_akis_ve_katalog(o):
    db = o["db"]
    a = o["admin"]
    s = a.get("/titaniumcam/stand").text
    assert "Donatı kataloğu" in s and "Ofis sandalyesi" in s
    a.post("/titaniumcam/stand/donati", data={"sandalye_ahsap:ad": "Ahşap sandalye (ceviz)", "sandalye_ahsap:en": "450",
                                              "sandalye_ahsap:derinlik": "500", "sandalye_ahsap:yukseklik": "820",
                                              "sandalye_ahsap:renk": "#8b5e3c", "sandalye_ahsap:fiyat": "1250",
                                              "sandalye_ahsap:aktif": "1",
                                              "sandalye_tasarim:ad": "Tasarım sandalye (beyaz)", "sandalye_tasarim:en": "450",
                                              "sandalye_tasarim:derinlik": "500", "sandalye_tasarim:yukseklik": "820",
                                              "sandalye_tasarim:renk": "#f1f5f9", "sandalye_tasarim:fiyat": "0"})
    r = db.one("SELECT * FROM stand_donati WHERE kod='sandalye_ahsap'")
    assert r["fiyat"] == 1250 and r["aktif"] == 1
    assert db.one("SELECT aktif FROM stand_donati WHERE kod='sandalye_tasarim'")["aktif"] == 0
    a.post("/titaniumcam/stand/donati/yeni", data={"ad": "Yeşil berjer", "grup": "koltuk"})
    assert db.one("SELECT grup, model FROM stand_donati WHERE kod='yesil_berjer'")["model"] == "berjer"
    a.post("/titaniumcam/stand/donati/depo_odasi/sil")
    assert db.one("SELECT 1 FROM stand_donati WHERE kod='depo_odasi'")

    c = o["TC"](o["app"])
    f = c.get("/stand").text
    assert "Standda neler olsun" in f and "Yeşil berjer" in f and "Tasarım sandalye" not in f   # pasif görünmez
    r = c.post("/stand/uret", data={**FORM, "en": "8", "derinlik": "5", **DONATI, "koltuk": "yesil_berjer"},
               follow_redirects=False)
    url = r.headers["location"]
    sid = int(url.rsplit("/", 1)[1])
    s = c.get(url).text
    assert "Donatılar" in s and "Yeşil berjer" in s and "Ahşap sandalye" in s
    assert 'name="koltuk" value="yesil_berjer"' in s                          # başka alternatif üret: seçim korunur
    c.post(f"/stand/{sid}/sec/0")
    c.post("/magaza/kayit", data={"full_name": "Donatı", "username": "donati@x.com", "password": "1234"})
    assert "Ahşap sandalye" in c.get("/magaza/sepet").text
    r = c.post("/kabin/siparis", data={"proje": "D"}, follow_redirects=False)
    od = db.one("SELECT * FROM cabinet_orders WHERE id=?", (int(r.headers["location"].rsplit("/", 1)[1]),))
    st = json.loads(od["stand_json"])[0]
    kal = {e["kod"]: e for e in st["alternatif"]["ekstralar"]}
    assert kal["don:sandalye_ahsap"]["miktar"] == 4 and kal["don:sandalye_ahsap"]["tutar"] == 5000
    assert st["alternatif"]["donatilar"] and "Ahşap sandalye (ceviz): 4 adet" in od["note"]


# ── ikram köşesi, cam vitrin, truss / banner, logo ─────────────────────────
PNG = (b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89"
       b"\x00\x00\x00\rIDATx\x9cc\xf8\xff\xff?\x00\x05\xfe\x02\xfe\xa7\x35\x81\x84\x00\x00\x00\x00IEND\xaeB`\x82")


def test_ikram_vitrin_truss_banner():
    g, K = _girdi_donatili(8000, 5000, ("on", "sol"), vitrin="2", vitrin_model="cam_vitrin")
    g["yapi"] = S.yapi_dogrula({"truss": "1", "banner": "on", "ikram": "1"})
    for a in asyncio.run(S.alternatifler_uret(g, None, K))[0]:
        assert not a["notlar"], a["notlar"]
        ik = [m for m in a["moduller"] if m["tip"] == "ikram_tezgahi"]
        assert len(ik) == 1 and S._duvara_sirt({**ik[0], "tur": "depo"}, g)
        assert sum(d["tur"] == "vitrin" for d in a["donatilar"]) == 2
        k = {e["kod"]: e for e in a["ekstralar"]}
        assert k["truss"]["miktar"] == 26 and k["banner"]["miktar"] == 1 and k["don:cam_vitrin"]["miktar"] == 2
        svg = S.plan_svg(g, a["moduller"], donatilar=a["donatilar"])
        assert "Truss tavan" in svg and "Askılı banner" in svg
    t = S.tarif({"tip": "ikram_tezgahi", "ad": "İ", "en": 1200, "boy": 900, "derinlik": 600, "raf": 1, "kapak": 2})
    assert t["sablon"] == "Alt Dolap" and any(x.get("tur") == "kapak" for x in t["adimlar"])
    # yapay zekâ ikram tezgâhını koymadıysa eklenir
    a = S.alternatif_duzelt({"ad": "A", "moduller": [{"tip": "banko", "en": 1600, "boy": 1050, "derinlik": 600, "raf": 1,
                                                       "kapak": 0, "x": 3000, "y": 4000, "yon": "arka"}]}, g, [], K)
    assert any(m["tip"] == "ikram_tezgahi" for m in a["moduller"])
    assert "ikram_tezgahi" in S._kullanici_istemi(g)


def test_logo_yukleme(o):
    c = o["TC"](o["app"])
    veri = {k: v for k, v in {**FORM, **DONATI, "truss": "1", "banner": "1", "ikram": "1"}.items()}
    r = c.post("/stand/uret", data=veri, files={"logo": ("logo.svg", b"<svg onload=alert(1)>", "image/png")})
    assert "PNG ya da JPG" in r.text
    r = c.post("/stand/uret", data=veri, files={"logo": ("b.png", PNG + b"0" * (3 * 1024 * 1024), "image/png")})
    assert "3 MB" in r.text
    r = c.post("/stand/uret", data=veri, files={"logo": ("logo.png", PNG, "image/png")}, follow_redirects=False)
    url = r.headers["location"]
    sid = int(url.rsplit("/", 1)[1])
    g = json.loads(o["db"].one("SELECT girdi_json FROM stand_proje WHERE id=?", (sid,))["girdi_json"])
    ad = g["yapi"]["logo"]
    assert re.fullmatch(r"[0-9a-f]{32}\.png", ad) and g["yapi"]["truss"] and g["yapi"]["ikram"]
    l = c.get(f"/stand/logo/{ad}")
    assert l.status_code == 200 and l.content == PNG and l.headers["x-content-type-options"] == "nosniff"
    assert c.get("/stand/logo/..%2Fstand.db").status_code == 404 and c.get("/stand/logo/x.png").status_code == 404
    s = c.get(url).text
    assert f'name="logo_kod" value="{ad}"' in s and "/stand/logo/" + ad in s and "Truss tavan" in s
    # başka alternatif üret: logo yeniden yüklenmeden korunur
    r = c.post("/stand/uret", data={**veri, "logo_kod": ad}, follow_redirects=False)
    sid2 = int(r.headers["location"].rsplit("/", 1)[1])
    assert json.loads(o["db"].one("SELECT girdi_json FROM stand_proje WHERE id=?", (sid2,))["girdi_json"])["yapi"]["logo"] == ad
    r = c.post("/stand/uret", data={**veri, "logo_kod": "../../etc/passwd"}, follow_redirects=False)
    sid3 = int(r.headers["location"].rsplit("/", 1)[1])
    assert json.loads(o["db"].one("SELECT girdi_json FROM stand_proje WHERE id=?", (sid3,))["girdi_json"])["yapi"]["logo"] == ""
    # sipariş: logo ve stand yapısı
    c.post(f"/stand/{sid}/sec/0")
    c.post("/magaza/kayit", data={"full_name": "Logo", "username": "logo@x.com", "password": "1234"})
    assert "Logoyu indir" in c.get("/magaza/sepet").text
    r = c.post("/kabin/siparis", data={"proje": "L"}, follow_redirects=False)
    od = o["db"].one("SELECT * FROM cabinet_orders WHERE id=?", (int(r.headers["location"].rsplit("/", 1)[1]),))
    st = json.loads(od["stand_json"])[0]
    assert st["logo"] == f"/stand/logo/{ad}" and st["girdi"]["yapi"]["banner"]
    assert f"Logo dosyası: /stand/logo/{ad}" in od["note"] and "Truss tavan" in od["note"]
    assert "Logoyu indir" in o["admin"].get(f"/titaniumcam/kabin/siparis/{od['id']}").text
