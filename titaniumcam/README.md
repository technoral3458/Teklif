# TitaniumCam — online dolap tasarım ve sipariş

FastAPI + SQLite + Jinja2. Müşteri tarayıcıda dolap tasarlar; hesap TitaniumCam motorunda yapılır
(ayrı, kapalı servis); sipariş yönetici onayıyla (ya da otomatik) TitaniumCam programına API ile gider.

| Bölüm | Kod |
|---|---|
| Mağaza (ana sayfa, sepet, giriş / kayıt / Google, siparişlerim) | `routers/magaza.py` |
| Dolap tasarımı, siparişler, hazır modeller, tanımlar ve fiyatlar | `routers/kabin.py`, `kabin_istemci.py` |
| Motor servisi (yalnız 127.0.0.1:8210; motor kodu depoda değil) | `kabin_servis/servis.py` |
| TitaniumCam API (`/api/titanium/v1`) | `routers/titanium_api.py`, `docs/titanium_api.md` |
| Acil lisans | `routers/acil_lisans.py`, `acil_lisans.py`, `docs/acil_lisans.md` |
| Yönetici merkezi ve ayarlar | `routers/ayarlar.py` |

Yönetim: `/login` → `/titaniumcam/kabin` · Yönetici merkezi: `/titaniumcam/admin`

## Yerel çalıştırma

```bash
cd titaniumcam
python3 -m venv venv && . venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
KABIN_MOTOR_DIR=/yol/kabin_motor uvicorn kabin_servis.servis:app --port 8210 &
uvicorn main:app --port 8100
```

Veritabanı ilk açılışta `data/titaniumcam.db` olarak oluşur (ilk yönetici: `admin` / `admin123`, hemen değiştirin).

## Testler

```bash
KABIN_MOTOR_DIR=/yol/kabin_motor python3 -m pytest -q tests
```

## Sunucu

`deploy/setup_titaniumcam.sh` (sıfırdan kurulum), `deploy/kabin_motor_kur.sh` (motor),
`deploy/deneme_kur.sh` (deneme ortamı), `deploy/gecis_titaniumcam.sh` (eski membran kurulumundan bir kerelik geçiş).
