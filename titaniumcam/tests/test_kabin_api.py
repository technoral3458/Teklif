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
    o["admin"].post(f"/titaniumcam/kabin/siparis/{oid}/onayla")
    return oid


def _anahtar(o, kabin=True, kapak=False):
    r = o["admin"].post("/titaniumcam/api/yeni", data={"ad": "test", **({"kabin": "1"} if kabin else {}),
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
    assert k not in o["admin"].get("/titaniumcam/api").text                               # tam anahtar bir daha gösterilmez


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
    assert "CNC-2" in o["admin"].get(f"/titaniumcam/kabin/siparis/{oid}").text
    # iptal edilen anahtar
    kid = o["db"].one("SELECT id FROM api_keys ORDER BY id DESC LIMIT 1")["id"]
    o["admin"].post(f"/titaniumcam/api/{kid}/iptal")
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
    assert a.get("/titaniumcam/kabin/tanimlar").status_code == 200
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
    assert a.post("/titaniumcam/kabin/tanimlar", data=form, follow_redirects=False).status_code == 303
    # vitrin: kapalı şablon görünmez
    g = o["TC"](o["app"])
    t = g.get("/").text
    assert 'name="sablon"' not in t                                                    # vitrinde boş şablon yok
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


# ══════════════════════════════════════════════════════════════════════════
# Ayarlar: onay şekli (genel + müşteri istisnası), mağaza aşaması, hesap
# ══════════════════════════════════════════════════════════════════════════
def _siparis_ver(o, eposta):
    c = _musteri(o, eposta)
    url = c.post("/kabin/yeni", data={"sablon": "Alt Dolap"}, follow_redirects=False).headers["location"]
    c.post(url + "/islem", json={"islem": {"tip": "olcu", "en": 800}})
    c.post(url + "/islem", json={"islem": ADIMLAR[0]})
    assert c.post(url + "/sepet", json={"adet": 1}).status_code == 200
    r = c.post("/kabin/siparis", data={"proje": "akış"}, follow_redirects=False)
    return c, int(r.headers["location"].rsplit("/", 1)[1])


def test_onay_sekli(o):
    a, db = o["admin"], o["db"]
    assert a.get("/titaniumcam/ayarlar").status_code == 200
    a.post("/titaniumcam/ayarlar/akis", data={"kabin_onay": "otomatik", "magaza_asama": "planlama"})
    _c, oid = _siparis_ver(o, "oto@test.com")
    s = db.one("SELECT status, approved_by FROM cabinet_orders WHERE id=?", (oid,))
    assert s["status"] == "onayli" and s["approved_by"] == "otomatik"
    k = _anahtar(o)
    ids = [x["id"] for x in o["TC"](o["app"]).get("/api/titanium/v1/cabinet-orders", headers=_h(k)).json()["orders"]]
    assert f"K-{oid}" in ids                                                            # doğrudan API havuzunda
    # müşteri istisnası: genel otomatik ama bu müşteri yönetici onaylı
    uid = db.one("SELECT id FROM membrane_users WHERE username='oto@test.com'")["id"]
    a.post(f"/titaniumcam/ayarlar/musteri/{uid}", data={"onay_sekli": "yonetici"})
    c = o["TC"](o["app"])
    c.post("/magaza/giris", data={"username": "oto@test.com", "password": "1234"})
    url = c.post("/kabin/yeni", data={"sablon": "Alt Dolap"}, follow_redirects=False).headers["location"]
    c.post(url + "/sepet", json={"adet": 1})
    oid2 = int(c.post("/kabin/siparis", data={}, follow_redirects=False).headers["location"].rsplit("/", 1)[1])
    assert db.one("SELECT status FROM cabinet_orders WHERE id=?", (oid2,))["status"] == "bekliyor"
    # eski ürün grupları kaldırıldı
    m = _musteri(o, "magaza@test.com")
    assert m.post("/magaza/sepet/ekle", json={"product": "gardirop"}).status_code in (404, 405)
    assert m.get("/magaza/urun/gardirop", follow_redirects=False).headers["location"] == "/"
    assert "Ürün Grupları" not in m.get("/").text
    a.post("/titaniumcam/ayarlar/akis", data={"kabin_onay": "yonetici", "magaza_asama": "havuz"})


def test_hesap_degistir(o):
    a = o["admin"]
    assert "Mevcut şifre yanlış" in a.post("/titaniumcam/ayarlar/hesap", data={"mevcut": "yanlis", "yeni_sifre": "Yeni-Sifre-1",
                                                                           "yeni_sifre2": "Yeni-Sifre-1"}).text
    assert "en az 8" in a.post("/titaniumcam/ayarlar/hesap", data={"mevcut": "admin123", "yeni_sifre": "kisa",
                                                                 "yeni_sifre2": "kisa"}).text
    r = a.post("/titaniumcam/ayarlar/hesap", data={"mevcut": "admin123", "yeni_ad": "patron",
                                                "yeni_sifre": "Yeni-Sifre-1", "yeni_sifre2": "Yeni-Sifre-1"},
               follow_redirects=False)
    assert r.status_code == 303 and a.get("/titaniumcam/ayarlar").status_code == 200       # oturum yeni adla sürer
    y = o["TC"](o["app"])
    assert y.post("/login", data={"username": "admin", "password": "admin123"}, follow_redirects=False).status_code != 303
    assert y.post("/login", data={"username": "patron", "password": "Yeni-Sifre-1"},
                  follow_redirects=False).status_code == 303
    assert y.get("/titaniumcam/ayarlar").status_code == 200
    m = _musteri(o, "hacker@test.com")                                                  # müşteri ayarlara giremez
    assert m.get("/titaniumcam/ayarlar", follow_redirects=False).status_code == 303


# ══════════════════════════════════════════════════════════════════════════
# Hazır modeller · otomatik arkalık · sade müşteri ekranı
# ══════════════════════════════════════════════════════════════════════════
def test_oto_arkalik(o):
    a, db = o["admin"], o["db"]
    def yeni_taslak():
        g = o["TC"](o["app"])
        url = g.post("/kabin/yeni", data={"sablon": "Alt Dolap"}, follow_redirects=False).headers["location"]
        return g, url, g.get(url + "/veri").json()
    g, url, v = yeni_taslak()
    ar = [p for p in v["parcalar2b"] if p["tur"] == "arkalik"]
    assert len(ar) == 1                                                                 # şablonun arkalığı otomatik
    assert "kabin_basit" not in g.get(url).text and "Sepete ekle" in g.get(url).text    # sade ekran
    assert 'id="dockIc"' in g.get(url).text
    a.post("/titaniumcam/kabin/modeller/arkalik", data={"ark:Alt Dolap": "Kanalli 8", "ark:Ust Dolap": "", "ark:Boy Dolap": ""})
    _g, _u, v = yeni_taslak()
    ar = [r for r in v["parca_listesi"] if r["ad"].startswith("Arkalik")]
    assert len(ar) == 1 and ar[0]["kalinlik"] == 8
    a.post("/titaniumcam/kabin/modeller/arkalik", data={"ark:Alt Dolap": "", "ark:Ust Dolap": "", "ark:Boy Dolap": ""})
    _g, _u, v = yeni_taslak()
    assert not [p for p in v["parcalar2b"] if p["tur"] == "arkalik"]                   # yönetici "arkalık yok" dedi
    db.execute("DELETE FROM kabin_ayar WHERE anahtar='musteri_kabin'")


def test_hazir_model(o):
    a, db = o["admin"], o["db"]
    assert a.get("/titaniumcam/kabin/modeller").status_code == 200
    r = a.post("/titaniumcam/kabin/modeller/yeni", data={"ad": "3 Çekmeceli Alt", "sablon": "Alt Dolap"},
               follow_redirects=False)
    yurl = r.headers["location"]
    assert "Modeli kaydet" in a.get(yurl).text
    for x in [{"tip": "olcu", "en": 600}, {"tip": "ekle", "tur": "cekmece", "noktalar": [[300, 300]], "adet": 3,
                                           "sablon": "Mandalli 500"}]:
        assert a.post(yurl + "/islem", json={"islem": x}).status_code == 200
    r = a.post(yurl + "/modele-kaydet")
    assert r.status_code == 200, r.text
    mid = db.one("SELECT id FROM kabin_model WHERE ad='3 Çekmeceli Alt'")["id"]
    g = o["TC"](o["app"])
    assert "3 Çekmeceli Alt" not in g.get("/").text                                      # satışta değil
    assert g.post("/kabin/yeni", data={"model": mid}, follow_redirects=False).headers["location"].startswith("/kabin?hata")
    a.post(f"/titaniumcam/kabin/modeller/{mid}/guncelle", data={"ad": "3 Çekmeceli Alt", "aciklama": "Mutfak için",
                                                             "sira": "1", "acik": "1"})
    t = g.get("/").text
    assert "3 Çekmeceli Alt" in t and f'name="model" value="{mid}"' in t and "Mutfak için" in t
    url = g.post("/kabin/yeni", data={"model": mid}, follow_redirects=False).headers["location"]
    v = g.get(url + "/veri").json()
    assert len([p for p in v["parcalar2b"] if p["tur"] == "cekmece"]) == 3 and v["govde"]["en"] == 600
    mk = json.loads(db.one("SELECT kabin_json FROM kabin_model WHERE id=?", (mid,))["kabin_json"])
    tk = json.loads(db.one("SELECT kabin_json FROM kabin_taslak WHERE id=?", (int(url.rsplit("/", 1)[1]),))["kabin_json"])
    assert mk["uid"] != tk["uid"]                                                       # müşteri kopyası yeni uid
    r = g.post(url + "/islem", json={"islem": {"tip": "olcu", "en": 700}})                # müşteri ölçüyü değiştirir
    assert r.status_code == 200 and len([p for p in r.json()["parcalar2b"] if p["tur"] == "cekmece"]) == 3
    a.post(f"/titaniumcam/kabin/modeller/{mid}/sil")
    assert db.one("SELECT 1 FROM kabin_model WHERE id=?", (mid,)) is None


# ══════════════════════════════════════════════════════════════════════════
# Kabinler (katalog) ve Maliyetler
# ══════════════════════════════════════════════════════════════════════════
def test_kabinler_ve_maliyet(o):
    a, db = o["admin"], o["db"]
    from routers.kabin import fiyat_hesapla, tanim_tablosu
    assert a.get("/titaniumcam/kabinler").status_code == 200
    r = a.post("/titaniumcam/kabin/modeller/yeni", data={"ad": "Mutfak Alt 2 Kapak", "sablon": "Alt Dolap",
                                                        "kategori": "Mutfak"}, follow_redirects=False)
    yurl = r.headers["location"]
    for x in [{"tip": "olcu", "en": 800},
              {"tip": "ekle", "tur": "kapak", "noktalar": [[400, 300]], "adet": 2, "kulp": {"sablon": "Ust orta 128"}}]:
        assert a.post(yurl + "/islem", json={"islem": x}).status_code == 200
    assert a.post(yurl + "/modele-kaydet").json()["url"].startswith("/titaniumcam/kabinler")
    m = db.one("SELECT * FROM kabin_model WHERE ad='Mutfak Alt 2 Kapak'")
    g = json.loads(m["gorsel_json"])
    assert g["govde"]["en"] == 800 and any(p["tur"] == "kapak" for p in g["parcalar3b"]) and g["ust"] is False
    assert len([p for p in g["parcalar2b"] if p["tur"] == "kapak"]) == 2 and json.loads(m["maliyet_json"])
    # Maliyetler: birim fiyat + kâr; açık / kapalı ayarına dokunmaz
    kapali_once = {(r["tur"], r["kod"]) for r in db.query("SELECT tur, kod FROM kabin_tanim WHERE acik=0")}
    form = {"kar": "25", "goster": "1", "f:malzeme:MDF18": "650", "f:donati:mentese": "55"}
    assert "Fiyatlar kaydedildi" in a.post("/titaniumcam/kabinler/fiyatlar", data=form).text
    T = tanim_tablosu()
    assert T[("malzeme", "MDF18")]["fiyat"] == 650 and T[("donati", "mentese")]["fiyat"] == 55
    assert {(r["tur"], r["kod"]) for r in db.query("SELECT tur, kod FROM kabin_tanim WHERE acik=0")} == kapali_once
    t = a.get("/titaniumcam/kabinler?sekme=maliyet").text
    assert "Mutfak Alt 2 Kapak" in t and "Kabin maliyetleri" in t and 'name="f:malzeme:MDF18"' in t
    f = fiyat_hesapla(json.loads(m["maliyet_json"]))
    assert f["kar_orani"] == 25 and abs(f["toplam"] - f["ara_toplam"] * 1.25) < 0.02
    # Tanımlar kaydı fiyatları SİLMEZ (fiyat alanı artık orada yok)
    a.post("/titaniumcam/kabin/tanimlar", data={"f:malzeme:MDF18": "", "a:malzeme:MDF18": "1",
                                                **{f"e:{x}": "1" for x in ("dikeybolme", "yataybolme", "raf", "kapak", "cekmece")}})
    assert tanim_tablosu()[("malzeme", "MDF18")]["fiyat"] == 650
    # satışa aç / kopyala / grup
    a.post(f"/titaniumcam/kabin/modeller/{m['id']}/satis")
    assert db.one("SELECT acik FROM kabin_model WHERE id=?", (m["id"],))["acik"] == 1
    a.post(f"/titaniumcam/kabin/modeller/{m['id']}/kopyala")
    k = db.one("SELECT * FROM kabin_model WHERE ad='Mutfak Alt 2 Kapak (kopya)'")
    assert k and k["acik"] == 0 and k["kategori"] == "Mutfak" and k["gorsel_json"] == m["gorsel_json"]
    # vitrin: grup, gerçekçi görsel verisi, kopya görünmez
    v = o["TC"](o["app"]).get("/").text
    assert "Mutfak Alt 2 Kapak" in v and "(kopya)" not in v and 'data-grup="Mutfak"' in v and "kartlariDoldur" in v
    # eski görseli olmayan model bir kez motordan hesaplanır
    db.execute("UPDATE kabin_model SET gorsel_json='', maliyet_json='' WHERE id=?", (k["id"],))
    assert a.get("/titaniumcam/kabinler").status_code == 200
    assert db.one("SELECT gorsel_json FROM kabin_model WHERE id=?", (k["id"],))["gorsel_json"]
    a.post(f"/titaniumcam/kabin/modeller/{m['id']}/sil"); a.post(f"/titaniumcam/kabin/modeller/{k['id']}/sil")


# ══════════════════════════════════════════════════════════════════════════
# Sipariş düzenleme / silme (yönetici)
# ══════════════════════════════════════════════════════════════════════════
def test_siparis_duzenle_sil(o):
    a, db = o["admin"], o["db"]
    oid = _onayli_siparis(o, "duzen@test.com", adet=2)
    t = a.get("/titaniumcam/kabin").text
    assert f"/titaniumcam/kabin/siparis/{oid}/sil" in t and "TitaniumCam API" not in t and "Maliyetler" not in t.split("<main")[1]
    # bilgiler
    a.post(f"/titaniumcam/kabin/siparis/{oid}/bilgi", data={"proje": "Yeni Proje", "note": "Kapıya teslim", "musteri_ad": "Ali Veli"})
    s = db.one("SELECT * FROM cabinet_orders WHERE id=?", (oid,))
    assert (s["proje"], s["note"], s["musteri_ad"]) == ("Yeni Proje", "Kapıya teslim", "Ali Veli")
    # onayı geri al → bekliyor; TitaniumCam aldıysa önce havuza geri
    db.execute("UPDATE cabinet_orders SET claimed_by='CNC-1' WHERE id=?", (oid,))
    assert "havuza geri" in a.post(f"/titaniumcam/kabin/siparis/{oid}/bekliyor").text.lower()
    db.execute("UPDATE cabinet_orders SET claimed_by='' WHERE id=?", (oid,))
    a.post(f"/titaniumcam/kabin/siparis/{oid}/bekliyor")
    assert db.one("SELECT status FROM cabinet_orders WHERE id=?", (oid,))["status"] == "bekliyor"
    # adet ve satır çıkarma (üretime alınmışsa 'değişti')
    lid = db.one("SELECT id FROM cabinet_order_lines WHERE order_id=?", (oid,))["id"]
    db.execute("UPDATE cabinet_orders SET claimed_by='CNC-1' WHERE id=?", (oid,))
    a.post(f"/titaniumcam/kabin/satir/{lid}/adet", data={"adet": "5"})
    assert db.one("SELECT qty FROM cabinet_order_lines WHERE id=?", (lid,))["qty"] == 5
    assert db.one("SELECT degisti FROM cabinet_orders WHERE id=?", (oid,))["degisti"] == 1
    # son satır çıkarılınca sipariş tümden silinir; taslak / kayıtlar da gider
    a.post(f"/titaniumcam/kabin/satir/{lid}/duzenle")
    r = a.post(f"/titaniumcam/kabin/satir/{lid}/sil")
    assert "silindi" in r.text
    assert db.one("SELECT 1 FROM cabinet_orders WHERE id=?", (oid,)) is None
    assert db.one("SELECT 1 FROM cabinet_order_lines WHERE order_id=?", (oid,)) is None
    assert db.one("SELECT 1 FROM kabin_taslak WHERE siparis_satir_id=?", (lid,)) is None
    # liste üzerinden doğrudan silme; API'de de görünmez
    oid2 = _onayli_siparis(o, "duzen2@test.com", adet=1)
    k = _anahtar(o)
    assert any(x["id"] == f"K-{oid2}" for x in o["TC"](o["app"]).get("/api/titanium/v1/cabinet-orders", headers=_h(k)).json()["orders"])
    a.post(f"/titaniumcam/kabin/siparis/{oid2}/sil")
    assert db.one("SELECT 1 FROM cabinet_orders WHERE id=?", (oid2,)) is None
    assert not any(x["id"] == f"K-{oid2}" for x in o["TC"](o["app"]).get("/api/titanium/v1/cabinet-orders", headers=_h(k)).json()["orders"])
    assert o["TC"](o["app"]).get(f"/api/titanium/v1/cabinet-orders/K-{oid2}", headers=_h(k)).status_code == 404
    # API bölümü Ayarlar'da
    assert 'href="/titaniumcam/api"' in a.get("/titaniumcam/ayarlar").text


# ══════════════════════════════════════════════════════════════════════════
# Hazır katalog: en çok satılan kabin tipleri motorla kurulur
# ══════════════════════════════════════════════════════════════════════════
def test_hazir_katalog(o):
    import hazir_katalog as HK
    a, db = o["admin"], o["db"]
    adlar = [m["ad"] for m in HK.MODELLER]
    assert len(adlar) == len(set(adlar)) >= 15
    t = a.get("/titaniumcam/kabinler").text
    assert "Hazır katalog" in t and "4 Çekmeceli Şifonyer" in t
    r = a.post("/titaniumcam/kabinler/hazir-katalog", data={"ad": adlar, "satis": "1"})
    assert f"{len(adlar)} kabin eklendi ve satışa açıldı" in r.text and "kurulamayan" not in r.text, r.text[:400]
    for m in HK.MODELLER:
        k = db.one("SELECT * FROM kabin_model WHERE ad=?", (m["ad"],))
        g = json.loads(k["gorsel_json"])
        assert k["acik"] == 1 and k["kategori"] == m["kategori"] and g["parcalar3b"], m["ad"]
    sif = json.loads(db.one("SELECT gorsel_json FROM kabin_model WHERE ad='4 Çekmeceli Şifonyer'")["gorsel_json"])
    assert len([p for p in sif["parcalar2b"] if p["tur"] == "cekmece"]) == 4 and sif["govde"]["en"] == 800
    # ikinci kez: çoğalmaz
    r = a.post("/titaniumcam/kabinler/hazir-katalog", data={"ad": adlar, "satis": "1"})
    assert "0 kabin eklendi" in r.text and f"{len(adlar)} kabin zaten vardı" in r.text
    # vitrinde gruplar; müşteri komodinden başlayıp ölçüyü değiştirir
    v = o["TC"](o["app"]).get("/").text
    assert 'data-grup="Yatak odası"' in v and "TV Ünitesi 180 cm" in v
    g = o["TC"](o["app"])
    mid = db.one("SELECT id FROM kabin_model WHERE ad='2 Çekmeceli Komodin'")["id"]
    url = g.post("/kabin/yeni", data={"model": mid}, follow_redirects=False).headers["location"]
    r = g.post(url + "/islem", json={"islem": {"tip": "olcu", "en": 550}})
    assert r.status_code == 200 and r.json()["govde"]["en"] == 550
    assert len([p for p in r.json()["parcalar2b"] if p["tur"] == "cekmece"]) == 2 and not r.json()["hata_var"]
    for ad in adlar:
        db.execute("DELETE FROM kabin_model WHERE ad=?", (ad,))
