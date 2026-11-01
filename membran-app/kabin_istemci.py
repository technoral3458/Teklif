"""Web sunucusundan kabin motor servisine istek (servis yalnız 127.0.0.1'de)."""
import json
import os

import httpx

SERVIS_URL = os.environ.get("KABIN_SERVIS_URL", "http://127.0.0.1:8210")
ZAMAN_ASIMI = float(os.environ.get("KABIN_SERVIS_ZAMAN_ASIMI", "15"))
TRANSPORT = None          # testlerde servis uygulaması doğrudan bağlanır (httpx.ASGITransport)


class ServisHatasi(Exception):
    def __init__(self, mesaj, kod=422):
        super().__init__(mesaj)
        self.mesaj, self.kod = mesaj, kod


def _client():
    if TRANSPORT is not None:
        return httpx.AsyncClient(transport=TRANSPORT, base_url="http://kabin", timeout=ZAMAN_ASIMI)
    return httpx.AsyncClient(base_url=SERVIS_URL, timeout=ZAMAN_ASIMI)


async def _istek(yontem, yol, govde=None):
    try:
        async with _client() as c:
            if yontem == "GET":
                r = await c.get(yol)
            else:
                r = await c.post(yol, content=json.dumps(govde, allow_nan=False),
                                 headers={"Content-Type": "application/json"})
    except httpx.TimeoutException:
        raise ServisHatasi("Kabin motoru zamanında cevap vermedi", 504)
    except (httpx.HTTPError, OSError):
        raise ServisHatasi("Kabin motoru şu an çalışmıyor", 503)
    except ValueError:
        raise ServisHatasi("Geçersiz sayı", 422)
    try:
        veri = r.json()
    except ValueError:
        raise ServisHatasi("Kabin motoru geçersiz cevap verdi", 502)
    if r.status_code != 200:
        raise ServisHatasi(veri.get("error", "Motor hatası"), r.status_code)
    return veri


_BILGI = None


async def bilgi(yenile=False):
    global _BILGI
    if _BILGI is None or yenile:
        _BILGI = await _istek("GET", "/bilgi")
    return _BILGI


async def islem(kabin, islemler, rol, izinler):
    return await _istek("POST", "/islem", {"kabin": kabin, "islem": islemler, "rol": rol,
                                           "izinler": sorted(izinler)})
