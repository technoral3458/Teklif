# Hetzner Sunucuya Kurulum (kapak.ersanmakina.net)

Sıfırdan, boş bir **Ubuntu** Hetzner sunucusuna kurulum. Sonunda
`kapak.ersanmakina.net` bu sunucudan, HTTPS ile yayında olur.

---

## 1. Sunucuyu oluştur (Hetzner Console)

- **Image**: Ubuntu (22.04/24.04/26.04 fark etmez)
- **Type**: en küçük paylaşımlı CPU (CX22 gibi) yeterli
- **SSH key**: Varsa ekleyin (önerilir). Yoksa root şifresi e-postaya gelir.
- **Create & Buy now** → oluşunca size bir **IP adresi** verilir (örn. `5.75.10.20`).

---

## 2. Sunucuya bağlan

Kendi bilgisayarınızın terminalinden (Windows’ta PowerShell):
```bash
ssh root@SUNUCU_IP
```
İlk bağlanışta “yes” deyin; şifre sorarsa e-postadaki root şifresini girin.

---

## 3. Tek komutla kurulum

Sunucuda şunu çalıştırın:
```bash
curl -fsSL https://raw.githubusercontent.com/technoral3458/Teklif/claude/hexagonal-drill-glass-software-0a4fo5/deploy/setup_server.sh -o setup_server.sh
bash setup_server.sh
```

Betik otomatik olarak:
- Python, Node.js, nginx, git kurar,
- depoyu indirir, backend’i hazırlar, **veritabanını oluşturur** (`migrate`),
- `.env` içinde **güvenli bir SECRET_KEY** üretir, `DEBUG=False` yapar,
- frontend’i derler,
- gunicorn (systemd servisi `teklif`) ve nginx’i ayarlar.

> Depo **özel (private)** ise `curl` ile inmez. O durumda: önce
> `apt install -y git`, sonra `git clone` için kullanıcı adı + **personal
> access token** ile klonlayıp `bash /opt/teklif/Teklif/deploy/setup_server.sh`
> çalıştırın.

Bittiğinde tarayıcıdan **`http://SUNUCU_IP/`** açıp test edin
(giriş: `admin` / `admin123`).

---

## 4. Alan adını (DNS) bu sunucuya yönlendir

`kapak.ersanmakina.net` şu an başka yerde. DNS yönetiminde (alan adının
kayıtlı olduğu yer ya da Hetzner DNS) bir **A kaydı**:

| Tip | İsim | Değer |
|-----|------|-------|
| A | kapak (veya `kapak.ersanmakina.net`) | **SUNUCU_IP** |

Yayılması birkaç dakika–saat sürebilir. `ping kapak.ersanmakina.net` yeni IP’yi
gösterince hazırdır.

---

## 5. HTTPS (SSL sertifikası) — DNS yönlendikten sonra

```bash
apt-get install -y certbot python3-certbot-nginx
certbot --nginx -d kapak.ersanmakina.net
```
Certbot sertifikayı kurar ve http→https yönlendirmesini ekler. Artık
`https://kapak.ersanmakina.net` yayında.

---

## 6. Güncelleme geldiğinde (sonraki sürümler)

```bash
cd /opt/teklif/Teklif
git pull
cd backend && source .env && ./venv/bin/pip install -r requirements.txt
./venv/bin/python manage.py migrate
./venv/bin/python manage.py collectstatic --noinput
cd ../frontend && npm install && npm run build
chown -R www-data:www-data /opt/teklif
systemctl restart teklif && systemctl reload nginx
```

---

## Faydalı komutlar / sorun çözme

```bash
systemctl status teklif         # backend servisi çalışıyor mu?
journalctl -u teklif -n 50      # backend hata logları
nginx -t                        # nginx ayar testi
tail -f /var/log/nginx/error.log
```

- **502 Bad Gateway**: gunicorn (`teklif`) çalışmıyor → `journalctl -u teklif`.
- **Menüde Delme Panelleri yok**: frontend build edilmemiş → `npm run build`
  + `systemctl reload nginx` + tarayıcı önbelleğini temizle.
- **Yüklerken hata / "no such table"**: `migrate` çalışmamış → tekrar çalıştırın.
- **DisallowedHost hatası**: `.env` içindeki `DJANGO_ALLOWED_HOSTS` alan adıyla
  eşleşmeli, sonra `systemctl restart teklif`.

## Güvenlik notları (ilk işler)

- `admin` ve diğer demo şifrelerini değiştirin (`/admin/` panelinden).
- Üretimde demo kullanıcıları silmek isterseniz `seed_data.py`’yi tekrar
  çalıştırmayın; bu kullanıcılar yalnızca ilk kurulumda örnek içindir.
- Hetzner **Firewall**’dan yalnızca 22 (SSH), 80, 443 portlarına izin verin.
