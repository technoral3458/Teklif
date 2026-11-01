"""
TitaniumCam API, kabin tanım/fiyat ekranı ve Google girişi testleri.

    KABIN_MOTOR_DIR=/opt/kabin_motor python3 -m pytest tests/test_kabin_api.py -v
"""
import json
import os
import re
import sys
import tempfile

import pytest

MOTOR = os.environ.get("KABIN_MOTOR_DIR")
pytestmark = pytest.mark.skipif(not MOTOR, reason="KABIN_MOTOR_DIR verilmedi (motor kodu repoda değil)")
APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, APP)

ADIMLAR = [
    {"tip": "ekle", "tur": "dikeybolme", "noktalar": [[400, 300]], "adet": 1, "sabit_gozler": "400"},
    {"tip": "ekle", "tur": "raf", "noktalar": [[200, 300]], "adet": 1},
    {"tip": "ekle", "tur": "cekmece", "noktalar": [[600, 300]], "adet": 3, "sablon": "Mandalli 500"},
]
HTTPS = {"X-Forwarded-Proto": "https"}


@pytest.fixture(scope="module")
def o():
    os.environ.pop("KABIN_TANIM_DIR", None)
    import httpx
    from fastapi.testclient import TestClient
    import config
    import db
    config.DB_PATH = db.DB_PATH = os.path.join(tempfile.mkdtemp(), "api.db")
    db.init()
    from kabin_servis import servis
    import kabin_istemci
    kabin_istemci.TRANSPORT = httpx.ASGITransport(app=servis.app)
    kabin_istemci._BILGI = None
    import main
    a = TestClient(main.app)
    assert a.post("/login", data={"username": "admin", "password": "admin123"},
                  follow_redirects=False).status_code == 303
    return {"db": db, "app": main.app, "TC": TestClient, "admin": a}


def _musteri(o, eposta):
    c = o["TC"](o["app"])
    c.post("/magaza/kayit", data={"full_name": "T " + eposta, "username": eposta, "password": "1234"})
    return c


def _onayli_siparis(o, eposta, adet=2):
    c = _musteri(o, eposta)
    url = c.post("/kabin/yeni", data={"sablon": "Alt Dolap"}, follow_redirects=False).headers["location"]
    c.post(url + "/islem", json={"islem": {"tip": "olcu", "en": 800}})
    for a in ADIMLAR:
        assert c.post(url + "/islem", json={"islem": a}).status_code == 200
    assert c.post(url + "/sepet", json={"adet": adet}).status_code == 200
    r = c.post("/kabin/siparis", data={"proje": "API"}, follow_redirects=False)
    oid = int(r.headers["location"].rsplit("/", 1)[1])
    o["admin"].post(f"/membrane/kabin/siparis/{oid}/onayla")
    return oid


def _anahtar(o, kabin=True, kapak=False):
    r = o["admin"].post("/membrane/api/yeni", data={"ad": "test", **({"kabin": "1"} if kabin else {}),
                                                   **({"kapak": "1"} if kapak else {})})
    return re.search(r'id="anahtar"[^>]*>(tc_[^<]+)<', r.text).group(1)


def _h(k, ist="CNC-1"):
    return {"Authorization": f"Bearer {k}", "X-Titanium-Station": ist, **HTTPS}


# ══════════════════════════════════════════════════════════════════════════
# TitaniumCam API
# ══════════════════════════════════════════════════════════════════════════
def test_api_yetki_ve_https(o):
    c = o["TC"](o["app"])
    L = "/api/titanium/v1/cabinet-orders"
    assert c.get(L, headers=HTTPS).status_code == 401
    assert c.get(L, headers={"Authorization": "Bearer yanlis", **HTTPS}).status_code == 401
    k = _anahtar(o)
    assert c.get(L, headers={"Authorization": f"Bearer {k}"}).status_code == 403        # HTTP reddedilir
    assert c.get(L, headers=_h(k)).status_code == 200
    kapak = _anahtar(o, kabin=False, kapak=True)
    assert c.get(L, headers=_h(kapak)).status_code == 403                               # kabin izni yok
    row = o["db"].one("SELECT ozet, onek FROM api_keys ORDER BY id LIMIT 1")
    assert k not in json.dumps(row) and len(row["ozet"]) == 64                         # yalnız özet saklanır
    assert k not in o["admin"].get("/membrane/api").text                               # tam anahtar bir daha gösterilmez


def test_api_akis(o):
    c = o["TC"](o["app"])
    oid = _onayli_siparis(o, "api1@test.com", adet=2)
    bekleyen = _onayli_siparis(o, "api2@test.com")
    o["db"].execute("UPDATE cabinet_orders SET status='bekliyor', approved_at='' WHERE id=?", (bekleyen,))
    k = _anahtar(o)
    L = "/api/titanium/v1/cabinet-orders"
    r = c.get(L + "?status=approved&unclaimed=1&station=CNC-1", headers=_h(k)).json()
    ids = [x["id"] for x in r["orders"]]
    assert f"K-{oid}" in ids and f"K-{bekleyen}" not in ids                              # yalnız onaylı
    s = next(x for x in r["orders"] if x["id"] == f"K-{oid}")
    ln = s["lines"][0]
    assert ln["qty"] == 2 and ln["tkabin"]["kabin"]["adet"] == 2
    assert ln["tkabin"]["format"] == "tkabin" and ln["tkabin"]["schema"] == 1
    assert isinstance(ln["width_mm"], float) and ln["width_mm"] == 800.0
    assert s["approved_at"].endswith("+03:00") and ln["engine_version"] and ln["definitions_version"]
    # claim: ilk 200, aynı istasyon tekrar 200, başka istasyon 409
    C = f"{L}/K-{oid}/claim"
    assert c.post(C, headers=_h(k), json={"station": "CNC-1", "app": "TitaniumCam", "version": "2.0"}).status_code == 200
    assert c.post(C, headers=_h(k), json={"station": "CNC-1"}).status_code == 200
    r = c.post(C, headers=_h(k, "CNC-2"), json={"station": "CNC-2"})
    assert r.status_code == 409 and r.json() == {"ok": False, "error": "Başka istasyon aldı", "claimed_by": "CNC-1"}
    assert c.post(f"{L}/K-99999/claim", headers=_h(k), json={"station": "CNC-1"}).status_code == 404
    assert c.post(f"{L}/K-{bekleyen}/claim", headers=_h(k), json={"station": "CNC-1"}).status_code == 404
    ids = [x["id"] for x in c.get(L + "?unclaimed=1", headers=_h(k)).json()["orders"]]
    assert f"K-{oid}" not in ids                                                        # alınan listeden çıkar
    # durum
    S = f"{L}/K-{oid}/status"
    assert c.post(S, headers=_h(k, "CNC-2"), json={"station": "CNC-2", "status": "done"}).status_code == 409
    assert c.post(S, headers=_h(k), json={"station": "CNC-1", "status": "uydurma"}).status_code == 400
    assert c.post(S, headers=_h(k), json={"station": "CNC-1", "status": "in_production"}).status_code == 200
    assert o["db"].one("SELECT production_status FROM cabinet_orders WHERE id=?", (oid,))["production_status"] == "in_production"
    assert c.post(S, headers=_h(k), json={"station": "CNC-1", "status": "released"}).status_code == 200
    ids = [x["id"] for x in c.get(L + "?unclaimed=1", headers=_h(k)).json()["orders"]]
    assert f"K-{oid}" in ids                                                            # released: havuza döner
    assert c.post(C, headers=_h(k, "CNC-2"), json={"station": "CNC-2"}).status_code == 200
    # yönetici ekranında görünür; değişirse uyarı
    assert "CNC-2" in o["admin"].get(f"/membrane/kabin/siparis/{oid}").text
    # iptal edilen anahtar
    kid = o["db"].one("SELECT id FROM api_keys ORDER BY id DESC LIMIT 1")["id"]
    o["admin"].post(f"/membrane/api/{kid}/iptal")
    assert c.get(L, headers=_h(k)).status_code == 401


def test_api_hiz_siniri(o):
    c = o["TC"](o["app"])
    k = _anahtar(o)
    kodlar = [c.get("/api/titanium/v1/cabinet-orders", headers=_h(k)).status_code for _ in range(121)]
    assert kodlar[:120] == [200] * 120 and kodlar[120] == 429


# ══════════════════════════════════════════════════════════════════════════
# Tanımlar: müşteriye açık + birim fiyat
# ══════════════════════════════════════════════════════════════════════════
def test_tanimlar_ve_fiyat(o):
    a = o["admin"]
    assert a.get("/membrane/kabin/tanimlar").status_code == 200
    form = {"kar": "10", "goster": "1",
            "f:malzeme:MDF18": "500", "a:malzeme:MDF18": "1", "f:malzeme:MDF16": "400", "a:malzeme:MDF16": "1",
            "f:malzeme:SUNTA18": "300",                                    # SUNTA18 müşteriye kapalı
            "f:malzeme:SUNTA5": "100", "a:malzeme:SUNTA5": "1", "f:malzeme:SUNTA8": "120", "a:malzeme:SUNTA8": "1",
            "f:bant:PVC1": "5", "a:bant:PVC1": "1", "f:bant:PVC04": "3", "a:bant:PVC04": "1",
            "f:ray:STD500": "0", "a:ray:STD500": "1", "f:ray:STD450": "0",   # STD450 kapalı
            "f:donati:kanal": "2", "f:donati:mentese": "40", "f:donati:kulp": "30", "f:donati:ray": "150",
            "f:donati:ayak": "10", "f:sablon:Alt Dolap": "0", "a:sablon:Alt Dolap": "1",
            "f:sablon:Ust Dolap": "0", "f:sablon:Boy Dolap": "0", "a:sablon:Boy Dolap": "1",
            **{f"e:{t}": "1" for t in ("dikeybolme", "yataybolme", "raf", "arkalik", "kapak", "cekmece")}}
    for b in ("V", "D", "K", "R", "S", "C", "L1", "L2", "L3", "L4"):
        form[f"f:baglanti:{b}"] = "1"
    assert a.post("/membrane/kabin/tanimlar", data=form, follow_redirects=False).status_code == 303
    # vitrin: kapalı şablon görünmez
    g = o["TC"](o["app"])
    t = g.get("/").text
    assert 'value="Alt Dolap"' in t and 'value="Ust Dolap"' not in t
    assert g.post("/kabin/yeni", data={"sablon": "Ust Dolap"}, follow_redirects=False).headers["location"].startswith("/kabin?hata")
    url = g.post("/kabin/yeni", data={"sablon": "Alt Dolap"}, follow_redirects=False).headers["location"]
    e = g.get(url).text
    assert '"SUNTA18"' not in e and '"STD450"' not in e                                  # editörde listelenmez
    r = g.post(url + "/islem", json={"islem": {"tip": "ekle", "tur": "dikeybolme", "noktalar": [[200, 300]],
                                               "secenek": {"malzeme": "SUNTA18"}}})
    assert r.status_code == 422 and "seçilemez" in r.json()["error"]                     # sunucu da reddeder
    r = g.post(url + "/islem", json={"islem": {"tip": "ekle", "tur": "usttabla"}})
    assert r.status_code == 422                                                         # üst tabla müşteriye kapalı
    for x in [{"tip": "olcu", "en": 800}] + ADIMLAR:
        r = g.post(url + "/islem", json={"islem": x})
        assert r.status_code == 200, r.text
    v = r.json()
    assert v["fiyat"]["toplam"] and v["fiyat"]["toplam"] > 0 and "kalemler" not in v["fiyat"]   # müşteri: yalnız toplam
    # yönetici: kalem kalem; toplam = ara toplam × 1,10
    yid = int(url.rsplit("/", 1)[1])
    k = json.loads(o["db"].one("SELECT kabin_json FROM kabin_taslak WHERE id=?", (yid,))["kabin_json"])
    from kabin_servis import servis
    from fastapi.testclient import TestClient
    sv = TestClient(servis.app).post("/islem", json={"kabin": k, "islem": {"tip": "goster"}, "rol": "yonetici",
                                                    "izinler": []}).json()
    from routers.kabin import fiyat_hesapla
    f = fiyat_hesapla(sv["maliyet"])
    assert f["eksik"] == [] and abs(f["toplam"] - f["ara_toplam"] * 1.1) < 0.02
    mdf = next(x for x in f["kalemler"] if x["anahtar"] == "malzeme:MDF18")
    assert mdf["birim_fiyat"] == 500 and abs(mdf["tutar"] - mdf["miktar"] * 500) < 0.01
    assert abs(v["fiyat"]["toplam"] - f["toplam"]) < 0.02
    # fiyatı girilmemiş kalem varsa müşteriye toplam gösterilmez
    o["db"].execute("UPDATE kabin_tanim SET fiyat=0 WHERE tur='donati' AND kod='ray'")
    v = g.get(url + "/veri").json()
    assert v["fiyat"]["toplam"] is None


# ══════════════════════════════════════════════════════════════════════════
# Google ile giriş · test girişi kapalı
# ══════════════════════════════════════════════════════════════════════════
def test_google_giris(o, monkeypatch):
    import routers.magaza as M
    c = o["TC"](o["app"])
    assert c.post("/magaza/google", json={"credential": "x"}).status_code == 401       # istemci kimliği yok
    monkeypatch.setattr(M, "GOOGLE_CLIENT_ID", "test.apps.googleusercontent.com")

    async def sahte(token):
        if token != "gecerli":
            raise ValueError("Google girişi doğrulanamadı")
        return {"email": "kisi@gmail.com", "name": "Kişi Google", "sub": "1"}
    monkeypatch.setattr(M, "google_dogrula", sahte)
    assert c.post("/magaza/google", json={"credential": "sahte"}).status_code == 401
    # misafirken tasarlanan dolap Google girişiyle hesaba geçer
    url = c.post("/kabin/yeni", data={"sablon": "Alt Dolap"}, follow_redirects=False).headers["location"]
    r = c.post("/magaza/google", json={"credential": "gecerli", "next": "/magaza/sepet"})
    assert r.status_code == 200 and r.json()["url"] == "/magaza/sepet" and c.cookies.get("csession")
    u = o["db"].one("SELECT * FROM membrane_users WHERE username='kisi@gmail.com'")
    assert u["role"] == "customer" and u["full_name"] == "Kişi Google"
    tid = int(url.rsplit("/", 1)[1])
    assert o["db"].one("SELECT musteri FROM kabin_taslak WHERE id=?", (tid,))["musteri"] == "kisi@gmail.com"
    assert c.post("/magaza/google", json={"credential": "gecerli", "next": "https://kotu.site"}).json()["url"] == "/"
    # Google düğmesi ve One Tap sayfada
    g = o["TC"](o["app"]).get("/magaza/giris").text
    assert "accounts.google.com/gsi/client" in g and "test.apps.googleusercontent.com" in g
    assert 'data-auto_prompt="false"' in g and 'data-auto_select="false"' in g          # otomatik giriş yok
    assert "g_id_signin" in g                                                          # yalnız düğme


def test_sosyal_test_girisi_kapali(o):
    c = o["TC"](o["app"])
    r = c.post("/magaza/sosyal", data={"provider": "google", "email": "kurban@test.com"}, follow_redirects=False)
    assert r.headers["location"] == "/magaza/giris" and not c.cookies.get("csession")
    assert o["db"].one("SELECT 1 FROM membrane_users WHERE username='kurban@test.com'") is None
