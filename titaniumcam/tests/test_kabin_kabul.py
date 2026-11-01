"""
Online Kabin — kabul testleri (komut metni bölüm 14).

Motor kodu repoda değildir. Çalıştırmak için:
    KABIN_MOTOR_DIR=/opt/titaniumcam/kabin_motor python3 -m pytest tests/test_kabin_kabul.py -v

Altın örnek varsayılan tanimlar.json ile koşar (KABIN_TANIM_DIR verilmez).
docs/online_kabin_ornek_parcalar.json ve docs/online_kabin_ornek.tkabin.json
varsa birebir karşılaştırılır; yoksa metindeki ölçülerle denetlenir.
"""
import json
import math
import os
import sys
import tempfile

import pytest

MOTOR = os.environ.get("KABIN_MOTOR_DIR")
pytestmark = pytest.mark.skipif(not MOTOR, reason="KABIN_MOTOR_DIR verilmedi (motor kodu repoda değil)")

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(APP, "docs")
sys.path.insert(0, APP)


# ══════════════════════════════════════════════════════════════════════════
# Kurulum
# ══════════════════════════════════════════════════════════════════════════
@pytest.fixture(scope="module")
def ortam():
    os.environ.pop("KABIN_TANIM_DIR", None)          # altın örnek: varsayılan tanımlar
    import httpx
    from fastapi.testclient import TestClient
    import config
    import db
    tmp = tempfile.mkdtemp()
    config.DB_PATH = db.DB_PATH = os.path.join(tmp, "test.db")
    db.init()
    from kabin_servis import servis
    import kabin_istemci
    kabin_istemci.TRANSPORT = httpx.ASGITransport(app=servis.app)
    kabin_istemci._BILGI = None
    import main
    return {"servis": servis, "S": TestClient(servis.app), "app": main.app, "db": db,
            "TestClient": TestClient}


def izinler_hepsi(servis):
    from routers.kabin import VARSAYILAN_YETKI
    return sorted(VARSAYILAN_YETKI)


def svc(o, kabin, islem, rol="musteri", izin=None):
    from routers.kabin import izinler
    r = o["S"].post("/islem", json={"kabin": kabin, "islem": islem, "rol": rol,
                                    "izinler": sorted(izin if izin is not None else izinler(rol))})
    return r.status_code, r.json()


ADIMLAR = [
    {"tip": "ekle", "tur": "dikeybolme", "noktalar": [[400, 300]], "adet": 1, "sabit_gozler": "400"},
    {"tip": "ekle", "tur": "raf", "noktalar": [[200, 300]], "adet": 1},
    {"tip": "ekle", "tur": "kapak", "noktalar": [[200, 100], [200, 500]], "adet": 1, "menteseler": ["SOL"],
     "kulp": {"sablon": "Ust orta 128"}},
    {"tip": "ekle", "tur": "cekmece", "noktalar": [[600, 300]], "adet": 3, "sablon": "Mandalli 500"},
    {"tip": "ekle", "tur": "arkalik", "noktalar": [[200, 100], [200, 500], [600, 300]],
     "sablon": "Alt Dolap Cakma"},
]


def altin_kabin(o):
    """Metindeki 6 adım, motor düzeyinde (tüm eleman yetkileriyle; müşteri arkalık seçmez)."""
    from routers.kabin import VARSAYILAN_YETKI
    hepsi = sorted(VARSAYILAN_YETKI)
    s, v = svc(o, None, {"tip": "yeni", "sablon": "Alt Dolap", "en": 800, "boy": 860, "derinlik": 560}, izin=hepsi)
    assert s == 200, v
    adimlar = [v]
    for a in ADIMLAR:
        s, v = svc(o, v["kabin"], a, izin=hepsi)
        assert s == 200, (a, v)
        adimlar.append(v)
    return adimlar


def yakin(a, b, tol=0.1):
    return abs(float(a) - float(b)) <= tol


def satir(v, ad):
    return next(r for r in v["parca_listesi"] if r["ad"] == ad)


# ══════════════════════════════════════════════════════════════════════════
# 1. Altın örnek
# ══════════════════════════════════════════════════════════════════════════
def test_altin_ornek(ortam):
    a = altin_kabin(ortam)
    v1 = a[0]
    assert v1["govde"]["govde_boyu"] == 740
    g = v1["gozler"][0]
    assert (g["x1"], g["x2"], g["y1"], g["y2"]) == (18, 782, 0, 722)            # iç kutu
    b = next(p for p in a[1]["parcalar2b"] if p["tur"] == "dikeybolme")["kutu"]
    assert (b["x1"], b["x2"]) == (418, 436)
    assert [(z["x1"], z["x2"]) for z in a[1]["gozler"]] == [(18, 418), (436, 782)]  # 400 ve 346
    r = next(p for p in a[2]["parcalar2b"] if p["tur"] == "raf")["kutu"]
    assert (r["x1"], r["x2"], r["y1"], r["y2"]) == (19, 417, 352, 370)
    k = next(p for p in a[3]["parcalar2b"] if p["tur"] == "kapak")["kutu"]
    assert (k["x1"], k["x2"], k["y1"], k["y2"]) == (2, 425.5, 5, 740)
    v = a[-1]
    assert yakin(satir(v, "Raf 1")["boy"], 398) and yakin(satir(v, "Raf 1")["en"], 550)
    assert (satir(v, "Kapak 1")["boy"], satir(v, "Kapak 1")["en"]) == (423.5, 735)
    assert {(satir(v, f"Cekmece 1 {x}")["boy"], satir(v, f"Cekmece 1 {x}")["en"]) for x in ("Baslik",)} == {(369.5, 243)}
    assert (satir(v, "Cekmece 1 Yan Sol")["boy"], satir(v, "Cekmece 1 Yan Sol")["en"]) == (490, 94)
    assert (satir(v, "Cekmece 1 On")["boy"], satir(v, "Cekmece 1 On")["en"]) == (258, 94)
    assert (satir(v, "Cekmece 1 Taban")["boy"], satir(v, "Cekmece 1 Taban")["en"]) == (290, 490)
    ar = satir(v, "Arkalik 1")
    assert (ar["boy"], ar["en"], ar["kalinlik"]) == (796, 736, 5)
    assert v["ozet"] == {"parca": 25, "m2": 3.895, "bant_m": 9.36}
    assert [b for b in v["doktor"] if b["seviye"] == "hata"] == []
    yol = os.path.join(DOCS, "online_kabin_ornek_parcalar.json")
    if os.path.exists(yol):                           # verilen beklenen liste: ad, sıra, 0,1 mm
        bek = json.load(open(yol, encoding="utf-8"))
        bek = bek.get("parcalar", bek) if isinstance(bek, dict) else bek
        assert len(bek) == len(v["parca_listesi"])
        for e, g in zip(bek, v["parca_listesi"]):
            assert (e.get("ad") or e.get("aciklama")) == g["ad"]
            assert yakin(e["boy"], g["boy"]) and yakin(e["en"], g["en"])


# ══════════════════════════════════════════════════════════════════════════
# 2. Dosya: .tkabin motordan geçince aynı liste, tekrar yüklenince değişmez
# ══════════════════════════════════════════════════════════════════════════
def test_dosya_gidis_donus(ortam):
    v = altin_kabin(ortam)[-1]
    yol = os.path.join(DOCS, "online_kabin_ornek.tkabin.json")
    kabin = json.load(open(yol, encoding="utf-8"))["kabin"] if os.path.exists(yol) else v["kabin"]
    s, v2 = svc(ortam, kabin, {"tip": "goster"}, rol="yonetici")
    assert s == 200, v2
    assert [(r["ad"], r["boy"], r["en"]) for r in v2["parca_listesi"]] == \
           [(r["ad"], r["boy"], r["en"]) for r in v["parca_listesi"]]
    s, v3 = svc(ortam, v2["kabin"], {"tip": "goster"}, rol="yonetici")
    assert v3["kabin"] == v2["kabin"]


# ══════════════════════════════════════════════════════════════════════════
# 3. Kilit · 4. Sürükleme
# ══════════════════════════════════════════════════════════════════════════
def test_kilit(ortam):
    v = altin_kabin(ortam)[-1]
    s, v = svc(ortam, v["kabin"], {"tip": "goz_olcu", "nokta": [200, 100], "yon": "x", "deger": 300})
    assert s == 200, v
    b = next(p for p in v["parcalar2b"] if p["tur"] == "dikeybolme")["kutu"]
    assert b["x1"] == 318
    assert next(z for z in v["gozler"] if z["x1"] == 18)["kilit"]["x"] is True
    s, v = svc(ortam, v["kabin"], {"tip": "olcu", "en": 900})
    assert s == 200, v
    sol = [z for z in v["gozler"] if z["x1"] == 18]
    assert sol and all(yakin(z["x2"] - z["x1"], 300) for z in sol)
    s, v = svc(ortam, v["kabin"], {"tip": "kilit_ac", "nokta": [150, 100], "yon": "x"})
    assert s == 200, v
    assert not any(z["kilit"]["x"] for z in v["gozler"])


def test_surukleme(ortam):
    v = altin_kabin(ortam)[-1]
    eid = next(p for p in v["parcalar2b"] if p["tur"] == "dikeybolme")["id"]
    s, v = svc(ortam, v["kabin"], {"tip": "tasi", "id": eid, "dx": 50})
    assert s == 200, v
    assert next(p for p in v["parcalar2b"] if p["id"] == eid)["kutu"]["x1"] == 468
    assert not any(z["kilit"]["x"] for z in v["gozler"])


# ══════════════════════════════════════════════════════════════════════════
# 5. Reddedilenler
# ══════════════════════════════════════════════════════════════════════════
def test_reddedilenler(ortam):
    s, v = svc(ortam, None, {"tip": "yeni", "sablon": "Alt Dolap", "en": 800})
    s, v = svc(ortam, v["kabin"], {"tip": "ekle", "tur": "dikeybolme", "noktalar": [[400, 300]],
                                   "sabit_gozler": "20"})
    assert s == 200, v
    once = v["kabin"]
    s, r = svc(ortam, once, {"tip": "ekle", "tur": "dikeybolme", "noktalar": [[28, 300]]})
    assert s == 422 and "sığmıyor" in r["error"], r
    # ray: 560 derinlikte kasa derinliği 700
    s, r = svc(ortam, once, {"tip": "ray_kontrol", "secenek": {"kasa_derinlik": 700}})
    assert s == 200 and r["sonuc"]["raylar"] and not any(r["sonuc"]["raylar"].values())
    # üst tabla + üst dolgu
    v = altin_kabin(ortam)[-1]
    from routers.kabin import VARSAYILAN_YETKI
    s, v = svc(ortam, v["kabin"], [{"tip": "ekle", "tur": "usttabla"},
                                   {"tip": "ozellik", "alanlar": {"dolgu.ust": True, "dolgu.ust_konum": "UST"}}],
               izin=sorted(VARSAYILAN_YETKI))
    assert s == 200, v
    hatalar = [b for b in v["doktor"] if b["seviye"] == "hata"]
    assert len(hatalar) == 2 and v["hata_var"], hatalar


# ══════════════════════════════════════════════════════════════════════════
# 6. Güvenlik
# ══════════════════════════════════════════════════════════════════════════
def test_guvenlik_servis(ortam):
    v = altin_kabin(ortam)[-1]
    S = ortam["S"]
    r = S.post("/islem", content='{"kabin": null, "islem": {"tip":"yeni","sablon":"Alt Dolap","en": NaN}, '
                                 '"rol":"musteri","izinler":["en"]}', headers={"Content-Type": "application/json"})
    assert r.status_code == 422
    s, r = svc(ortam, v["kabin"], {"tip": "ozellik", "alanlar": {"ayaktipi": "X"}})
    assert s == 422
    k = json.loads(json.dumps(v["kabin"]))
    k["ayaktipi"] = "UYDURMA"
    s, r = svc(ortam, k, {"tip": "goster"})
    assert s == 422
    k = json.loads(json.dumps(v["kabin"]))
    k["bilinmeyen_alan"] = 1
    s, r = svc(ortam, k, {"tip": "goster"})
    assert s == 422
    s, r = svc(ortam, v["kabin"], {"tip": "ozellik", "alanlar": {"parca_cizimleri": {"x": {}}}})
    assert s == 422
    k = json.loads(json.dumps(v["kabin"]))       # müşteri taslağında makine alanları boşaltılır
    k["parca_cizimleri"] = {"side.left": {"islemler": [1]}}
    k["arkaliklar"][0]["kanal_takimi"] = "T99"
    s, r = svc(ortam, k, {"tip": "goster"})
    assert s == 200 and r["kabin"]["parca_cizimleri"] == {} and r["kabin"]["arkaliklar"][0]["kanal_takimi"] == ""
    assert all("delik_ust" not in x for x in r["parca_listesi"])           # müşteri delik görmez
    s, r = svc(ortam, v["kabin"], {"tip": "ozellik", "alanlar": {"k_soldanbosluk": 5}})  # Y alanı
    assert s == 422 and "yetki" in r["error"]
    s, r = svc(ortam, v["kabin"], {"tip": "ozellik", "alanlar": {"en": float("inf")}} if False else
               {"tip": "olcu", "en": 20})
    assert s == 422


# ══════════════════════════════════════════════════════════════════════════
# Web akışı: müşteri → sepet → sipariş → yönetici onay → .tkabin
# ══════════════════════════════════════════════════════════════════════════
def _musteri(o, eposta):
    c = o["TestClient"](o["app"])
    r = c.post("/magaza/kayit", data={"full_name": "Test " + eposta, "phone": "", "username": eposta,
                                      "password": "1234"}, follow_redirects=False)
    assert r.status_code == 303
    return c


def _yeni_taslak(c):
    r = c.post("/kabin/yeni", data={"sablon": "Alt Dolap"}, follow_redirects=False)
    assert r.status_code == 303 and r.headers["location"].startswith("/kabin/t/"), r.text
    return r.headers["location"]


def test_web_akisi(ortam):
    db = ortam["db"]
    c = _musteri(ortam, "a@test.com")
    url = _yeni_taslak(c)
    r = c.post(url + "/islem", json={"islem": {"tip": "olcu", "en": 800}})
    assert r.status_code == 200, r.text
    for a in ADIMLAR[:-1]:                    # arkalık şablona göre otomatik geldi (müşteri seçmez)
        r = c.post(url + "/islem", json={"islem": a})
        assert r.status_code == 200, (a, r.text)
    v = r.json()
    assert v["ozet"] == {"parca": 25, "m2": 3.895, "bant_m": 9.36}
    r = c.post(url + "/islem", json={"islem": ADIMLAR[-1]})
    assert r.status_code == 422                                # müşteri arkalık ekleyemez
    assert "kabin" not in v                                   # müşteriye kabin JSON gitmez
    assert v["durum"]["geri"] and not v["durum"]["ileri"]
    # geri / ileri
    r = c.post(url + "/geri"); assert r.json()["ozet"]["parca"] == 7      # 3 çekmece (18 parça) geri alındı
    r = c.post(url + "/ileri"); assert r.json()["ozet"]["parca"] == 25
    # canlı panel: ara değerler birikmez, İptal açılış hali, Tamam tek adım
    tid = int(url.rsplit("/", 1)[1])
    imlec0 = db.one("SELECT imlec FROM kabin_taslak WHERE id=?", (tid,))["imlec"]
    c.post(url + "/panel/ac")
    for en in (810, 820, 830):
        r = c.post(url + "/islem", json={"islem": {"tip": "ozellik", "alanlar": {"en": en}}, "panel": True})
        assert r.status_code == 200 and r.json()["govde"]["en"] == en
    r = c.post(url + "/panel/iptal"); assert r.json()["govde"]["en"] == 800
    c.post(url + "/panel/ac")
    c.post(url + "/islem", json={"islem": {"tip": "ozellik", "alanlar": {"en": 830}}, "panel": True})
    r = c.post(url + "/panel/tamam"); assert r.json()["govde"]["en"] == 830
    assert db.one("SELECT imlec FROM kabin_taslak WHERE id=?", (tid,))["imlec"] == imlec0 + 1
    r = c.post(url + "/geri"); assert r.json()["govde"]["en"] == 800
    # hatalı işlem dolabı değiştirmez
    once = db.one("SELECT kabin_json FROM kabin_taslak WHERE id=?", (tid,))["kabin_json"]
    r = c.post(url + "/islem", json={"islem": {"tip": "ekle", "tur": "raf", "noktalar": [[-500, -500]]}})
    assert r.status_code == 422
    assert db.one("SELECT kabin_json FROM kabin_taslak WHERE id=?", (tid,))["kabin_json"] == once
    # müşteri ölçü sınırı ve yasak işlemler
    r = c.post(url + "/islem", json={"islem": {"tip": "yeni", "sablon": "Alt Dolap"}}); assert r.status_code == 400
    # başkasının taslağı
    c2 = _musteri(ortam, "b@test.com")
    assert c2.get(url + "/veri").status_code == 404
    assert c2.post(url + "/islem", json={"islem": {"tip": "goster"}}).status_code == 404
    # kopya: uid yenilenir
    r = c.post(url + "/kopyala", follow_redirects=False)
    kopya = r.headers["location"]
    u1 = json.loads(db.one("SELECT kabin_json FROM kabin_taslak WHERE id=?", (tid,))["kabin_json"])["uid"]
    u2 = json.loads(db.one("SELECT kabin_json FROM kabin_taslak WHERE id=?",
                           (int(kopya.rsplit("/", 1)[1]),))["kabin_json"])["uid"]
    assert u1 != u2
    # sepet + sipariş (2 dolap)
    assert c.post(url + "/sepet", json={"adet": 2}).status_code == 200
    assert c.post(kopya + "/sepet", json={"adet": 1}).status_code == 200
    r = c.post("/kabin/siparis", data={"proje": "Test proje", "note": ""}, follow_redirects=False)
    assert r.status_code == 303 and "/kabin/siparis/" in r.headers["location"]
    oid = int(r.headers["location"].rsplit("/", 1)[1])
    lines = db.query("SELECT * FROM cabinet_order_lines WHERE order_id=? ORDER BY line_no", (oid,))
    assert len(lines) == 2
    tk = [json.loads(l["tkabin"]) for l in lines]
    assert all(t["format"] == "tkabin" and t["schema"] == 1 for t in tk)
    assert tk[0]["kabin"]["adet"] == lines[0]["qty"] == 2
    assert isinstance(lines[0]["width_mm"], float) and lines[0]["width_mm"] == 800
    assert len({t["kabin"]["uid"] for t in tk}) == 2 and u1 not in {t["kabin"]["uid"] for t in tk}
    assert lines[0]["engine_version"] and lines[0]["definitions_version"]
    # yönetici
    a = ortam["TestClient"](ortam["app"])
    assert a.post("/login", data={"username": "admin", "password": "admin123"},
                  follow_redirects=False).status_code == 303
    assert c.get("/titaniumcam/kabin", follow_redirects=False).status_code == 303   # müşteri giremez
    assert a.get("/titaniumcam/kabin").status_code == 200
    assert a.get(f"/titaniumcam/kabin/siparis/{oid}").status_code == 200
    v = a.get(f"/titaniumcam/kabin/satir/{lines[0]['id']}/veri").json()
    assert v["ozet"]["parca"] == 25 and "delik_ust" in v["parca_listesi"][0]     # yönetici delik görür
    # yönetici onaydan önce düzeltir
    r = a.post(f"/titaniumcam/kabin/satir/{lines[1]['id']}/duzenle", follow_redirects=False)
    yurl = r.headers["location"]
    assert a.get(yurl).status_code == 200
    r = a.post(yurl + "/islem", json={"islem": {"tip": "ozellik", "alanlar": {"k_soldanbosluk": 4}}})
    assert r.status_code == 200 and "kabin" in r.json()
    r = a.post(yurl + "/satira-kaydet"); assert r.status_code == 200, r.text
    l2 = db.one("SELECT tkabin FROM cabinet_order_lines WHERE id=?", (lines[1]["id"],))
    assert json.loads(l2["tkabin"])["kabin"]["k_soldanbosluk"] == 4
    # onay + indir
    a.post(f"/titaniumcam/kabin/siparis/{oid}/onayla")
    assert db.one("SELECT status FROM cabinet_orders WHERE id=?", (oid,))["status"] == "onayli"
    r = a.get(f"/titaniumcam/kabin/satir/{lines[0]['id']}/tkabin")
    assert r.status_code == 200 and ".tkabin" in r.headers["content-disposition"]
    assert json.loads(r.content)["kabin"]["adet"] == 2
    # onaylı sipariş değiştirilemez
    r = a.post(f"/titaniumcam/kabin/satir/{lines[0]['id']}/duzenle", follow_redirects=False)
    assert "hata" in r.headers["location"]
    assert c.get(f"/kabin/siparis/{oid}").status_code == 200
    assert c2.get(f"/kabin/siparis/{oid}", follow_redirects=False).status_code == 303


def test_misafir_tasarlar_sipariste_giris(ortam):
    """Üye olmayan Ürünler'den dolap tasarlar; giriş yalnız sipariş verirken istenir."""
    db = ortam["db"]
    g = ortam["TestClient"](ortam["app"])
    r = g.get("/magaza")
    assert r.status_code == 200 and "Dolap tasarla" in r.text
    assert 'name="sablon"' not in r.text                     # vitrinde boş şablon yok: yalnız yayınlanan kabinler
    assert g.get("/kabin/yeni", follow_redirects=False).headers["location"] == "/#kabinler"
    assert "Kabin Tasarla" not in r.text                     # ayrı menü yok, Ürünler'de
    r = g.post("/kabin/yeni", data={"sablon": "Alt Dolap"}, follow_redirects=False)
    assert r.status_code == 303 and r.headers["location"].startswith("/kabin/t/")
    url = r.headers["location"]
    assert g.get(url).status_code == 200                      # editör girişsiz açılır
    for a in ADIMLAR[:2]:
        assert g.post(url + "/islem", json={"islem": a}).status_code == 200
    assert g.post(url + "/sepet", json={"adet": 1}).status_code == 200
    r = g.get("/magaza/sepet")
    assert "Sipariş için giriş yapın" in r.text and "/magaza/kayit?next=/magaza/sepet" in r.text
    r = g.post("/kabin/siparis", data={"proje": ""}, follow_redirects=False)
    assert r.status_code == 303 and r.headers["location"] == "/magaza/giris?next=/magaza/sepet"
    baska = ortam["TestClient"](ortam["app"])                # başka misafir göremez
    baska.get("/magaza")
    assert baska.get(url + "/veri").status_code == 404
    # üye olunca dolap hesaba geçer, sepet korunur, sipariş verilir
    r = g.post("/magaza/kayit", data={"full_name": "Misafir Sonra Üye", "phone": "", "username": "m@test.com",
                                      "password": "1234", "next": "/magaza/sepet"}, follow_redirects=False)
    assert r.headers["location"] == "/magaza/sepet"
    tid = int(url.rsplit("/", 1)[1])
    t = db.one("SELECT musteri, sepette FROM kabin_taslak WHERE id=?", (tid,))
    assert t["musteri"] == "m@test.com" and t["sepette"] == 1
    assert "Dolap siparişini ver" in g.get("/magaza/sepet").text
    r = g.post("/kabin/siparis", data={"proje": "Misafir"}, follow_redirects=False)
    assert r.status_code == 303 and "/kabin/siparis/" in r.headers["location"]
    assert r.headers["location"].replace("/kabin/siparis/", "") and "/kabin/siparis/" in g.get("/magaza/siparislerim").text


def test_hatali_dolap_siparis_olmaz(ortam):
    from routers.kabin import _ayar_yaz
    _ayar_yaz("yetkiler", {"ekle.usttabla": "M"})             # bu test için üst tabla müşteriye açık
    c = _musteri(ortam, "c@test.com")
    url = _yeni_taslak(c)
    for a in ([{"tip": "ekle", "tur": "usttabla"}],
              [{"tip": "ozellik", "alanlar": {"dolgu.ust": True, "dolgu.ust_konum": "UST"}}]):
        r = c.post(url + "/islem", json={"islem": a})
        assert r.status_code == 200
    assert r.json()["hata_var"]
    r = c.post(url + "/sepet", json={"adet": 1})
    assert r.status_code == 422 and "hata" in r.json()["error"].lower()
    _ayar_yaz("yetkiler", {})


# ══════════════════════════════════════════════════════════════════════════
# 9. Yönetim sayfaları (eski sistem kaldırıldı)  (7. API: tests/test_kabin_api.py)
# ══════════════════════════════════════════════════════════════════════════
def test_yonetim_sayfalari(ortam):
    a = ortam["TestClient"](ortam["app"])
    a.post("/login", data={"username": "admin", "password": "admin123"})
    for yol in ("/titaniumcam/admin", "/titaniumcam/kabin", "/titaniumcam/kabin/modeller", "/titaniumcam/kabin/tanimlar",
                "/titaniumcam/kabin/ayar", "/titaniumcam/api", "/titaniumcam/acil-lisans", "/titaniumcam/ayarlar", "/", "/magaza/sepet"):
        assert a.get(yol).status_code == 200, yol
    for yol in ("/titaniumcam/kapak", "/titaniumcam/vardar/havuz/havuz", "/titaniumcam/urunler", "/titaniumcam/uretim",
                "/titaniumcam/jobs", "/titaniumcam/admin/prices", "/teklif/1"):
        assert a.get(yol, follow_redirects=False).status_code == 404, yol
