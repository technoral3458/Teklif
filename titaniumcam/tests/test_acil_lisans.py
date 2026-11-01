"""
Acil lisans servisi kabul testleri (docs/acil_lisans.md, bölüm 9).

    python3 -m pytest tests/test_acil_lisans.py -v
"""
import asyncio
import base64
import datetime as dt
import json
import os
import sqlite3
import sys
import tempfile
import threading

import pytest

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, APP)

# Bölüm 8: deneme vektörü (YALNIZ DENEME; canlıda kullanılmaz)
TEST_TOHUM = "905ced0005eb1d84f01dbcd13ce9d7ef8e225b1d2b8ab0a7899003e75ca71155"
TEST_ACIK = "45b5cc3cf97380304f94f2c3a8957b0a8b9d081f33bd5061575c626885fbb3e7"
TEST_HAM = ('{"urun":"cam","lisans_no":"TC-20261101-DENEME","musteri":"Örnek Mobilya","eposta":"",'
            '"makine":{"u":"1111111111111111","a":"2222222222222222","g":"3333333333333333","d":"4444444444444444"},'
            '"verilis":"2026-11-01","bitis":"2026-11-08","moduller":["kabin","nesting"],"acil":true}')
TEST_VERI = ("eyJ1cnVuIjoiY2FtIiwibGlzYW5zX25vIjoiVEMtMjAyNjExMDEtREVORU1FIiwibXVzdGVyaSI6IsOWcm5layBNb2JpbHlhIiwiZXBvc3Rh"
             "IjoiIiwibWFraW5lIjp7InUiOiIxMTExMTExMTExMTExMTExIiwiYSI6IjIyMjIyMjIyMjIyMjIyMjIiLCJnIjoiMzMzMzMzMzMzMzMzMzMz"
             "MyIsImQiOiI0NDQ0NDQ0NDQ0NDQ0NDQ0In0sInZlcmlsaXMiOiIyMDI2LTExLTAxIiwiYml0aXMiOiIyMDI2LTExLTA4IiwibW9kdWxsZXIi"
             "Olsia2FiaW4iLCJuZXN0aW5nIl0sImFjaWwiOnRydWV9")
TEST_IMZA = "aJmQuFn8XukECpRVqjy6tPDuY40KmFiOZxNlSi+qPe4tDLmiopAZzSPwRlbF5l5tLECKeHQhNVWNly2z6TD1Dw=="
TEST_KOD = "ABCDEFGHJKMNPQRS"
TEST_KOD_OZET = "f598056127fcd4387651a49b3a4235d8ce50d71f4091068df51511cfc10f388f"

M1 = {"u": "1111111111111111", "a": "2222222222222222", "g": "3333333333333333", "d": "4444444444444444"}
M2 = {"u": "aaaaaaaaaaaaaaaa", "a": "bbbbbbbbbbbbbbbb", "g": "cccccccccccccccc", "d": "dddddddddddddddd"}
M3 = {"u": "0123456789abcdef", "a": "0123456789abcdef", "g": "0123456789abcdef", "d": "0123456789abcdef"}
BUGUN = dt.date(2026, 11, 1)


def _ozet(kod):
    import hashlib
    return hashlib.sha256(kod.encode()).hexdigest()


@pytest.fixture(scope="module")
def o():
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    from fastapi.testclient import TestClient
    import config
    import db
    tmp = tempfile.mkdtemp()
    config.DB_PATH = db.DB_PATH = os.path.join(tmp, "acil.db")
    db.init()
    import acil_lisans as AL
    import eposta
    AL.ANAHTAR_DIR = os.path.join(tmp, "anahtar")          # testte web kökü dışı geçici klasör
    os.makedirs(AL.ANAHTAR_DIR, mode=0o700)
    ozel = Ed25519PrivateKey.from_private_bytes(bytes.fromhex(TEST_TOHUM))
    with open(os.path.join(AL.ANAHTAR_DIR, AL.ANAHTAR_DOSYA), "wb") as f:
        f.write(ozel.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                   serialization.NoEncryption()))
    AL.bugun = lambda: o_gun["d"]
    os.environ["SMTP_HOST"] = ""                            # varsayılan: e-posta yok
    import main
    api = TestClient(main.app, base_url="https://testserver", client=("127.0.0.1", 5000))
    admin = TestClient(main.app, base_url="https://testserver")
    assert admin.post("/login", data={"username": "admin", "password": "admin123"}, follow_redirects=False).status_code == 303
    return {"AL": AL, "db": db, "api": api, "admin": admin, "app": main.app, "TC": TestClient, "eposta": eposta, "tmp": tmp}


o_gun = {"d": BUGUN}


@pytest.fixture(autouse=True)
def _gun():
    o_gun["d"] = BUGUN
    yield
    o_gun["d"] = BUGUN


def _csrf(o):
    import re
    t = o["admin"].get("/titaniumcam/acil-lisans?sekme=ayar").text
    return re.search(r'name="csrf" value="([0-9a-f]+)"', t).group(1)


def _csv(o, satirlar):
    metin = "lisans_no,musteri,moduller,bitis,kod_ozet\n" + "\n".join(satirlar) + "\n"
    r = o["admin"].post("/titaniumcam/acil-lisans/yukle", data={"csrf": _csrf(o)},
                        files={"dosya": ("liste.csv", metin.encode("utf-8"), "text/csv")}, follow_redirects=False)
    assert r.status_code == 303
    return r.headers["location"]


def _lisans(o, ln, kod, musteri="Deneme Mobilya", moduller="kabin,nesting", bitis=""):
    _csv(o, [f'{ln},{musteri},"{moduller}",{bitis},{_ozet(kod)}'])


def _iste(o, kod, makine, istek, ip="203.0.113.10", surum="2.0"):
    return o["api"].post("/api/titanium/v1/license/emergency",
                         json={"acil_kod": kod, "makine": makine, "istek_kodu": istek, "surum": surum},
                         headers={"X-Forwarded-Proto": "https", "X-Real-IP": ip})


def _coz(o, cevap):
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    lis = json.loads(cevap["lisans"])
    ham = base64.b64decode(lis["veri"])
    Ed25519PublicKey.from_public_bytes(bytes.fromhex(o["AL"].acik_anahtar_hex())).verify(base64.b64decode(lis["imza"]), ham)
    return json.loads(ham), lis


# 1
def test_dogru_kod(o):
    _lisans(o, "TC-1", "KODBIRBIRBIRBIR1")
    r = _iste(o, "KODBIRBIRBIRBIR1", M1, "TCAM1-AAAAA-1")
    assert r.status_code == 200, r.text
    v, _ = _coz(o, r.json())
    assert v["bitis"] == r.json()["bitis"] == "2026-11-08" and v["verilis"] == "2026-11-01"
    assert v["urun"] == "cam" and v["eposta"] == "" and v["acil"] is True and v["makine"] == M1
    assert v["moduller"] == ["kabin", "nesting"]
    assert o["db"].one("SELECT olay FROM acil_olaylar WHERE lisans_no='TC-1' ORDER BY id DESC")["olay"] == "etkinlestirme"


# 2
def test_deneme_vektoru(o):
    AL = o["AL"]
    assert AL.acik_anahtar_hex() == TEST_ACIK
    assert AL.kod_ozeti(TEST_KOD) == TEST_KOD_OZET
    ham = AL.ham_bayt("TC-20261101-DENEME", "Örnek Mobilya", M1, "2026-11-01", "2026-11-08", ["kabin", "nesting"])
    assert ham == TEST_HAM.encode("utf-8")
    lis = json.loads(AL.imzala(ham))
    assert lis == {"veri": TEST_VERI, "imza": TEST_IMZA}
    # uçtan uca: aynı girdi → aynı veri ve imza
    _csv(o, [f'TC-20261101-DENEME,Örnek Mobilya,"kabin,nesting",,{TEST_KOD_OZET}'])
    r = _iste(o, "ABCD-EFGH-JKMN-PQRS".lower(), M1, "TCAM1-DENEME")     # tire / küçük harf de kabul
    assert r.status_code == 200
    assert json.loads(r.json()["lisans"]) == {"veri": TEST_VERI, "imza": TEST_IMZA}


# 3
def test_yanlis_kod(o):
    yanlis = "YANLISKODYANLIS9"
    r = _iste(o, yanlis, M1, "TCAM1-X", ip="203.0.113.30")
    assert r.status_code == 404 and r.json() == {"error": "code"}
    assert o["db"].one("SELECT COUNT(*) n FROM acil_olaylar WHERE olay='kod_hatali' AND ip='203.0.113.30'")["n"] == 1
    for yol in [o["db"].DB_PATH] + [os.path.join(d, f) for d, _, fs in os.walk(o["tmp"]) for f in fs]:
        with open(yol, "rb") as f:
            icerik = f.read()
        assert yanlis.encode() not in icerik and TEST_KOD.encode() not in icerik, yol
    assert yanlis not in o["admin"].get("/titaniumcam/acil-lisans?sekme=rapor").text


# 4
def test_ayni_bilgisayar(o):
    _lisans(o, "TC-4", "KODDORTDORTDORT4")
    b1 = _iste(o, "KODDORTDORTDORT4", M1, "TCAM1-DORT").json()["bitis"]
    o_gun["d"] = BUGUN + dt.timedelta(days=3)
    r = _iste(o, "KODDORTDORTDORT4", M1, "TCAM1-DORT")
    assert r.status_code == 200 and r.json()["bitis"] == b1
    v, _ = _coz(o, r.json())
    assert v["verilis"] == "2026-11-01" and v["bitis"] == b1
    assert o["db"].one("SELECT olay FROM acil_olaylar WHERE lisans_no='TC-4' ORDER BY id DESC")["olay"] == "yeniden"


# 5
def test_baska_bilgisayar_sinir(o):
    _lisans(o, "TC-5", "KODBESBESBESBES5")
    assert _iste(o, "KODBESBESBESBES5", M1, "TCAM1-BES-1").status_code == 200
    o_gun["d"] = BUGUN + dt.timedelta(days=30)
    r = _iste(o, "KODBESBESBESBES5", M2, "TCAM1-BES-2")
    assert r.status_code == 409 and r.json() == {"error": "limit", "sonraki": "2027-01-30"}
    o_gun["d"] = BUGUN + dt.timedelta(days=90)
    assert _iste(o, "KODBESBESBESBES5", M2, "TCAM1-BES-2").status_code == 200


# 6
def test_bir_kez_izin(o):
    _lisans(o, "TC-6", "KODALTIALTIALTI6")
    assert _iste(o, "KODALTIALTIALTI6", M1, "TCAM1-ALTI-1").status_code == 200
    o["admin"].post("/titaniumcam/acil-lisans/islem", data={"csrf": _csrf(o), "lisans_no": "TC-6", "ne": "izin"})
    assert o["db"].one("SELECT elle_izin FROM acil_lisanslar WHERE lisans_no='TC-6'")["elle_izin"] == 1
    assert _iste(o, "KODALTIALTIALTI6", M2, "TCAM1-ALTI-2").status_code == 200
    assert o["db"].one("SELECT elle_izin FROM acil_lisanslar WHERE lisans_no='TC-6'")["elle_izin"] == 0
    assert _iste(o, "KODALTIALTIALTI6", M3, "TCAM1-ALTI-3").status_code == 409


# 7
def test_kapali_ve_suresi_gecmis(o):
    _lisans(o, "TC-7", "KODYEDIYEDIYEDI7")
    o["admin"].post("/titaniumcam/acil-lisans/islem", data={"csrf": _csrf(o), "lisans_no": "TC-7", "ne": "kapali"})
    r = _iste(o, "KODYEDIYEDIYEDI7", M1, "TCAM1-YEDI")
    assert r.status_code == 403 and r.json() == {"error": "blocked"}
    _lisans(o, "TC-7B", "KODYEDIBYEDIBYE7", bitis="2026-10-31")
    assert _iste(o, "KODYEDIBYEDIBYE7", M1, "TCAM1-YEDIB").status_code == 403
    # bitiş günü hâlâ geçerli; acil lisans ana lisansı aşmaz
    _lisans(o, "TC-7C", "KODYEDICYEDICYE7", bitis="2026-11-03")
    r = _iste(o, "KODYEDICYEDICYE7", M1, "TCAM1-YEDIC")
    assert r.status_code == 200 and r.json()["bitis"] == "2026-11-03"


# 8
def test_hiz_siniri(o):
    _lisans(o, "TC-8", "KODSEKIZSEKIZSE8")
    for i in range(10):
        assert _iste(o, f"YANLIS{i:02d}YANLISKOD", M1, "TCAM1-S", ip="198.51.100.8").status_code == 404
    r = _iste(o, "KODSEKIZSEKIZSE8", M1, "TCAM1-S", ip="198.51.100.8")
    assert r.status_code == 429 and r.json() == {"error": "rate"}
    assert _iste(o, "KODSEKIZSEKIZSE8", M1, "TCAM1-S", ip="198.51.100.9").status_code == 200   # başka IP etkilenmez


# 9
@pytest.mark.parametrize("makine", [None, {}, {"u": "111111111111111"}, dict(M1, x="1111111111111111"),
                                    {"u": "111111111111111G"}, {"u": "AAAAAAAAAAAAAAAA"}, "1111", [M1]])
def test_bozuk_makine(o, makine):
    r = o["api"].post("/api/titanium/v1/license/emergency",
                      json={"acil_kod": TEST_KOD, "makine": makine, "istek_kodu": "TCAM1-B"},
                      headers={"X-Forwarded-Proto": "https", "X-Real-IP": "192.0.2.9"})
    assert r.status_code == 400 and r.json() == {"error": "bad_request"}


def test_bozuk_govde(o):
    h = {"X-Forwarded-Proto": "https", "X-Real-IP": "192.0.2.10", "Content-Type": "application/json"}
    api = o["api"]
    assert api.post("/api/titanium/v1/license/emergency", content=b"{bozuk", headers=h).status_code == 400
    buyuk = json.dumps({"acil_kod": TEST_KOD, "makine": M1, "istek_kodu": "X" * 5000})
    assert api.post("/api/titanium/v1/license/emergency", content=buyuk, headers=h).status_code == 400
    assert api.post("/api/titanium/v1/license/emergency", json={"acil_kod": TEST_KOD, "makine": M1, "istek_kodu": ""}, headers=h).status_code == 400
    assert api.post("/api/titanium/v1/license/emergency", json={"acil_kod": TEST_KOD, "makine": M1, "istek_kodu": "X" * 201}, headers=h).status_code == 400


# 10
def test_olay(o):
    _lisans(o, "TC-10", "KODONONONONONON1")
    h = {"X-Forwarded-Proto": "https", "X-Real-IP": "203.0.113.100"}
    r = o["api"].post("/api/titanium/v1/license/event", json={"lisans_no": "TC-10", "olay": "acilis", "istek_kodu": "TCAM1-O", "surum": "2.0"}, headers=h)
    assert r.status_code == 200 and r.json() == {"ok": True}
    k = o["db"].one("SELECT * FROM acil_olaylar WHERE lisans_no='TC-10' AND olay='acilis'")
    assert k and k["ip"] == "203.0.113.100" and k["surum"] == "2.0"
    assert o["api"].post("/api/titanium/v1/license/event", json={"lisans_no": "TC-YOK", "olay": "acilis"}, headers=h).json() == {"error": "license"}
    assert o["api"].post("/api/titanium/v1/license/event", json={"lisans_no": "TC-10", "olay": "baska"}, headers=h).status_code == 400
    for _ in range(57):
        o["api"].post("/api/titanium/v1/license/event", json={"lisans_no": "TC-10", "olay": "acilis"}, headers=h)
    assert o["api"].post("/api/titanium/v1/license/event", json={"lisans_no": "TC-10", "olay": "acilis"}, headers=h).status_code == 429


# 11
def test_eposta_calismazken(o):
    AL, ep = o["AL"], o["eposta"]
    c = _csrf(o)
    o["admin"].post("/titaniumcam/acil-lisans/ayar", data={"csrf": c, "sure": "7", "aralik": "90", "eposta": "bildirim@ornek.com"})
    os.environ.update({"SMTP_HOST": "127.0.0.1", "SMTP_PORT": "1"})          # bağlantı reddedilir
    try:
        _lisans(o, "TC-11", "KODONBIRONBIRON1")
        r = _iste(o, "KODONBIRONBIRON1", M1, "TCAM1-E")
        assert r.status_code == 200
        ep._son["is"].join(10)
        assert ep.son_hata() is not None
        assert "Son bildirim e-postası gönderilemedi" in o["admin"].get("/titaniumcam/acil-lisans").text
    finally:
        os.environ["SMTP_HOST"] = ""
        ep.hata_temizle()


# 12
def test_sure_ayari(o):
    c = _csrf(o)
    r = o["admin"].post("/titaniumcam/acil-lisans/ayar", data={"csrf": c, "sure": "16", "aralik": "90", "eposta": ""}, follow_redirects=False)
    assert "hata=" in r.headers["location"]
    assert o["AL"].ayar()["sure"] != 16
    with pytest.raises(ValueError):
        o["AL"].ayar_yaz(0, 90, "")
    o["AL"].ayar_yaz(15, 90, "")
    assert o["AL"].ayar()["sure"] == 15
    o["AL"].ayar_yaz(7, 90, "")


# 13
def test_csv_ikinci_yukleme(o):
    _lisans(o, "TC-13", "KODONUCONUCONUC1")
    assert _iste(o, "KODONUCONUCONUC1", M1, "TCAM1-13").status_code == 200
    c = _csrf(o)
    o["admin"].post("/titaniumcam/acil-lisans/islem", data={"csrf": c, "lisans_no": "TC-13", "ne": "kapali"})
    o["admin"].post("/titaniumcam/acil-lisans/islem", data={"csrf": c, "lisans_no": "TC-13", "ne": "izin"})
    once = o["db"].one("SELECT * FROM acil_lisanslar WHERE lisans_no='TC-13'")
    konum = _csv(o, [f'TC-13,Yeni Ad Mobilya,"kabin",2030-01-01,{_ozet("KODONUCONUCONUC1")}', f'TC-13B,Başka,"",,{_ozet("KODONUCBONUCBON1")}'])
    assert "1+lisans+eklendi" in konum.replace("%20", "+") and "1+g%C3%BCncellendi" in konum.replace("%20", "+")
    sonra = o["db"].one("SELECT * FROM acil_lisanslar WHERE lisans_no='TC-13'")
    for alan in ("kapali", "elle_izin", "son_istek_kodu", "son_verilis", "son_bitis"):
        assert sonra[alan] == once[alan], alan
    assert sonra["musteri"] == "Yeni Ad Mobilya" and sonra["bitis"] == "2030-01-01"
    assert o["db"].one("SELECT 1 FROM acil_lisanslar WHERE lisans_no='TC-1'")      # dosyada yok, silinmedi


# 14
def test_bos_moduller(o):
    _lisans(o, "TC-14", "KODONDORTONDORT1", moduller="")
    r = _iste(o, "KODONDORTONDORT1", M1, "TCAM1-14")
    v, lis = _coz(o, r.json())
    assert v["moduller"] == [] and b'"moduller":[]' in base64.b64decode(lis["veri"])


# 15
def test_es_zamanli(o):
    _lisans(o, "TC-15", "KODONBESONBESON1")
    sonuc = []

    async def iki():
        import httpx
        t = httpx.ASGITransport(app=o["app"], client=("127.0.0.1", 5001))
        async with httpx.AsyncClient(transport=t, base_url="https://testserver") as c:
            h = {"X-Forwarded-Proto": "https", "X-Real-IP": "203.0.113.15"}
            istek = lambda m, k: c.post("/api/titanium/v1/license/emergency", headers=h,  # noqa: E731
                                        json={"acil_kod": "KODONBESONBESON1", "makine": m, "istek_kodu": k})
            return await asyncio.gather(istek(M1, "TCAM1-P1"), istek(M2, "TCAM1-P2"))
    sonuc = asyncio.run(iki())
    assert sorted(r.status_code for r in sonuc) == [200, 409]
    # doğrudan iş parçacıklarıyla (kilit gerçekten çalışıyor mu)
    AL = o["AL"]
    _lisans(o, "TC-15B", "KODONBESBONBESB1")
    engel, cevap = threading.Barrier(4), []

    def is_(i):
        engel.wait()
        try:
            cevap.append(AL.etkinlestir(_ozet("KODONBESBONBESB1"), M1, f"TCAM1-T{i}", "2.0", "127.0.0.1", "", "")[0])
        except AL.Ret as r:
            cevap.append(r.durum)
    ts = [threading.Thread(target=is_, args=(i,)) for i in range(4)]
    [t.start() for t in ts]
    [t.join() for t in ts]
    assert sorted(cevap) == [200, 409, 409, 409]


# ── ek: güvenlik ──
def test_https_ve_proxy(o):
    r = o["TC"](o["app"], client=("127.0.0.1", 5000)).post(       # http (nginx https demiyor)
        "/api/titanium/v1/license/emergency", json={"acil_kod": TEST_KOD, "makine": M1, "istek_kodu": "X"})
    assert r.status_code == 403 and r.json() == {"error": "https"}
    assert r.headers.get("location") is None                         # yönlendirme yok
    # güvenilmeyen istemcinin X-Real-IP / X-Forwarded-Proto başlıkları okunmaz
    dis = o["TC"](o["app"], base_url="http://testserver", client=("198.51.100.77", 5000))
    r = dis.post("/api/titanium/v1/license/emergency", json={"acil_kod": TEST_KOD, "makine": M1, "istek_kodu": "X"},
                 headers={"X-Forwarded-Proto": "https", "X-Real-IP": "1.2.3.4"})
    assert r.status_code == 403
    # Bearer anahtarı istenmez (yetki acil kodun kendisi)
    assert _iste(o, "YOKYOKYOKYOKYOK1", M1, "X", ip="192.0.2.200").status_code == 404


def test_anahtar_uzerine_yazilmaz(o):
    AL = o["AL"]
    with pytest.raises(RuntimeError):
        AL.anahtar_uret()
    assert AL.acik_anahtar_hex() == TEST_ACIK
    t = o["admin"].get("/titaniumcam/acil-lisans?sekme=anahtar").text
    assert TEST_ACIK in t and TEST_TOHUM not in t and "PRIVATE" not in t
    # yeni klasörde bir kez üretilir, dosya 0600
    eski = AL.ANAHTAR_DIR
    AL.ANAHTAR_DIR = os.path.join(o["tmp"], "yeni")
    try:
        h = AL.anahtar_uret()
        assert len(h) == 64
        assert oct(os.stat(os.path.join(AL.ANAHTAR_DIR, AL.ANAHTAR_DOSYA)).st_mode & 0o777) == "0o600"
        with pytest.raises(RuntimeError):
            AL.anahtar_uret()
        assert AL.acik_anahtar_hex() == h
    finally:
        AL.ANAHTAR_DIR = eski
    # uygulama klasörünün içine anahtar konamaz
    AL.ANAHTAR_DIR = os.path.join(APP, "data", "anahtar")
    try:
        with pytest.raises(RuntimeError):
            AL.anahtar_uret()
    finally:
        AL.ANAHTAR_DIR = eski


def test_csrf(o):
    r = o["admin"].post("/titaniumcam/acil-lisans/islem", data={"csrf": "yanlis", "lisans_no": "TC-1", "ne": "kapali"})
    assert r.status_code == 403
    assert o["db"].one("SELECT kapali FROM acil_lisanslar WHERE lisans_no='TC-1'")["kapali"] == 0
    giris_yok = o["TC"](o["app"], base_url="https://testserver")
    assert giris_yok.get("/titaniumcam/acil-lisans", follow_redirects=False).status_code == 303
