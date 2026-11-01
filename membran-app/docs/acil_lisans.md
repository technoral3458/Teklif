# Acil lisans servisi

Bilgisayara bağlı TitaniumCam lisansı için acil durum yolu: müşteri acil kodu yeni bilgisayarda girer,
program kodu bu siteye gönderir, site o bilgisayara bağlı kısa süreli **Ed25519 imzalı** lisans döner.

## Uçlar (Bearer anahtarı YOK; yetki acil kodun kendisi; yalnız HTTPS, yönlendirme yok)

| Uç | Ne yapar |
|---|---|
| `POST /api/titanium/v1/license/emergency` | `{"acil_kod","makine":{u,a,g,d},"istek_kodu","surum"}` → `{"lisans","bitis"}` |
| `POST /api/titanium/v1/license/event` | `{"lisans_no","olay":"acilis","istek_kodu","surum"}` → `{"ok":true}` |

Karar sırası (`acil_lisans.etkinlestir`): hız (IP saatte 10 hatalı) → biçim (400) → kod (404) →
kapalı / ana lisans bitmiş (403) → aynı istek kodu süre içinde (aynı tarihlerle yeniden imza) →
sıklık (409, `sonraki`) → yeni süre. 3-7 tek işlemde (`BEGIN IMMEDIATE`): eşzamanlı iki istek iki süre alamaz.
Acil lisansın bitişi ana lisansın bitişini aşmaz.

Hata cevapları yalnız kısa kodlardır: `rate, bad_request, code, blocked, limit, license, https, unavailable, server`.

## Lisans metni

`lisans` = `{"veri": base64(ham), "imza": base64(Ed25519(ham))}` metni.
`ham` = `{"urun":"cam","lisans_no",…,"eposta":"","makine":{…},"verilis","bitis","moduller":[…],"acil":true}`
UTF-8 JSON (boşluksuz). `moduller` boşsa `[]`. Deneme vektörü `tests/test_acil_lisans.py` içinde birebir doğrulanır.

## Anahtar

- Özel anahtar: `ACIL_ANAHTAR_DIR/ozel_anahtar.pem` (varsayılan `/etc/membran/acil_lisans`, klasör 0700, dosya 0600).
  Uygulama klasörünün içine konamaz (kod reddeder); depoya, veritabanına, günlüğe, ekrana girmez.
- Yönetici → Acil Lisans → Anahtar: **bir kez** üretilir (`O_EXCL`; var olan dosyanın üzerine yazılmaz).
  Ekranda yalnız açık anahtar (64 hex) görünür.
- **Deneme ortamının anahtarı ayrıdır** (`/etc/membran-deneme/acil_lisans`). Programa yalnız CANLI anahtar gömülür.

### Yedek (canlı anahtar üretildikten hemen sonra, bir kez)

Bilgisayarınızda (PowerShell):

    scp root@167.233.104.21:/etc/membran/acil_lisans/ozel_anahtar.pem .

Dosyayı şifreli bir USB'ye ya da parola yöneticisine koyun, bilgisayardaki kopyayı silin.
Sunucu kaybolursa geri yükleme: dosyayı aynı yere koyun, `chmod 600`, servisi yeniden başlatın.
Yedek yoksa ve anahtar kaybolursa dağıtılmış programlar yeni acil lisansı kabul etmez.

## E-posta

`.env`: `SMTP_HOST, SMTP_PORT (465 SSL / 587 STARTTLS), SMTP_USER, SMTP_PASS, SMTP_FROM`.
Bildirim adresi: Acil Lisans → Ayarlar (orada "Deneme e-postası gönder" düğmesi de var).
Gönderim arka planda; hata olursa lisans yine verilir, yönetici ekranında uyarı çıkar.
Konu: `Acil lisans: <müşteri> (<olay>)` — `etkinlestirme` ve `reddedildi` olaylarında.

## Konum ve IP

Ülke / şehir yerel DB-IP Lite veritabanından (`GEOIP_DB`); IP üçüncü servise gönderilmez, yaklaşıktır.
`X-Real-IP` / `X-Forwarded-For` / `X-Forwarded-Proto` yalnız aynı sunucudaki nginx'ten (127.0.0.1) okunur.

## Deneme ortamı

DNS: `deneme.kapak` A kaydı → sunucu IP. Sonra sunucuda: `bash /opt/membran/Teklif/deploy/deneme_kur.sh`.
Ayrı klasör, veritabanı, port (8101), anahtar; canlıya dokunmaz.

## Canlıya alma (karar verildiğinde)

1. `cd /opt/membran/Teklif && git pull … && membran-app/venv/bin/pip install -r membran-app/requirements.txt`
2. `install -d -m 700 /etc/membran/acil_lisans`; `.env`'e SMTP satırları
3. nginx: `/api/titanium/v1/license/` için HTTP'de yönlendirme olmamalı (deneme_kur.sh'deki blok gibi)
4. `systemctl restart membran` → Acil Lisans → Anahtar üret → açık anahtarı programa → özel anahtarı yedekle
