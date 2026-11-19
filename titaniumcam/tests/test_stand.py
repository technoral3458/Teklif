"""Fuar standı tasarımı testleri. Motor kodu repoda olmadığından motor burada sahte bir servisle
taklit edilir (işlemleri kaydeder, ölçüyü uygular); yapay zekâ çağrısı da taklit edilir.

    python3 -m pytest tests/test_stand.py -v
"""
import asyncio
import json
import os
import re
import sys
import tempfile

import pytest

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, APP)

import stand as S  # noqa: E402


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
    async def ai(girdi, anahtar):
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

    async def bozuk(girdi, anahtar):
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
    async def ai(girdi, anahtar):
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
