# Membran Kapak Yönetim Sistemi

Bağımsız FastAPI + SQLite + Jinja2 uygulaması. 4 modül:

1. **Maliyet Hesaplayıcı** — malzeme fiyatları, döviz (TCMB), m²/kapı maliyeti
2. **Kapak Model Editörü** — parametrik NC (G-code) üretimi + simülasyon
3. **Nesting** — MaxRects BSSF levha yerleşim optimizasyonu + 2-geçişli NC
4. **El Yazısı Tarama** — Claude AI ile fotoğraftan ölçü çıkarma

## Kurulum

```bash
cd membran-app
python -m venv venv
source venv/bin/activate          # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env               # ANTHROPIC_API_KEY girin (tarama için)
uvicorn main:app --reload
```

Tarayıcı: http://127.0.0.1:8000/membrane

## Notlar

- Veritabanı ilk açılışta `data/membrane.db` olarak otomatik oluşur.
- Giriş/kullanıcı yok (tek kullanıcılı).
- `ANTHROPIC_API_KEY` yoksa diğer 3 modül çalışır; sadece tarama devre dışı kalır.
- DXF içe aktarma için `ezdxf` opsiyoneldir.

## NC formatı

Fanuc/Haas uyumlu (G21 metrik, G90 mutlak, G54 iş koordinatı). Koordinatlar mm.
İfadeler parametriktir: `LPX/2`, `LPY-10`, `sqrt(R*2)`, `-T` (W/H/T = LPX/LPY/LPZ).
