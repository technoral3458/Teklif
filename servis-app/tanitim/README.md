# Deli Kadir App — tanıtım videosu

`DeliKadirApp-tanitim.mp4` — 1080x1920 (dikey), 30 fps, ~66 saniye, müzikli.
WhatsApp / Instagram / web sitesi için paylaşıma hazır.

## İçindekiler

| Saniye | Sahne |
|---|---|
| 0:00 | Açılış animasyonu (uygulamanın gerçek splash ekranı) |
| 0:06 | Özet paneli — bu ay servis, alacak, geciken ödeme |
| 0:13 | Servis raporu — bölümler, arıza tanımı, fotoğraf, yedek parça, imza |
| 0:22 | Ücretlendirme — Euro girilince TCMB kurunun otomatik gelmesi |
| 0:29 | Masraflar — fişleriyle birlikte müşteriye yansıtma |
| 0:37 | PDF — servis raporu ve masraf dökümü sayfaları |
| 0:45 | Cari takip — vadesi geçen ve söz tutmayan müşteriler |
| 0:53 | Mail gönderimi |
| 0:58 | Kapanış — logo, özellikler ve APK bağlantısı |

## Nasıl üretiliyor

Video, uygulamanın ekranlarının **HTML/CSS ile yeniden çizilmiş** hâlidir; renkler
(`ui/theme/Theme.kt`), yazılar ve yerleşim Compose kaynaklarından alınmıştır. Bu
ortamda Android cihaz/emülatör olmadığı için ekran kaydı alınamaz.

PDF sahnesindeki sayfalar ise **gerçektir**: `backend/service/pdf.py` içindeki
üreticiyle örnek bir rapordan üretilip görüntüye çevrilmiştir.

Müzik `music.py` ile numpy kullanılarak sentezlenmiştir (özgün, telif yok).

### Yeniden üretmek

```bash
pip install numpy pillow playwright imageio-ffmpeg pymupdf qrcode
cd kaynak && ./fontlari_indir.sh      # Google Fonts (Inter, Montserrat, JetBrains Mono, Material Symbols)
python3 gen_assets.py    # fiş görselleri, teknik çizimler, imza
python3 gen_pdfs.py      # gerçek PDF çıktıları (geçici veritabanı kullanır)
python3 music.py         # fon müziği
python3 shoot.py --all   # 1980 kare (Chromium)
./encode.sh              # MP4
```

Metinleri değiştirmek için `scenes.js` içindeki `HEADS` tablosuna,
ekran içeriklerini değiştirmek için `promo.html` dosyasına bakın.
Sahne süreleri `scenes.js` içindeki `TL` dizisindedir.
