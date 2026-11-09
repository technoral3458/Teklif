# Titanium Cam — Tanıtım Web Sitesi

Titanium Cam yazılımının tanıtım sitesi. Framework kullanmayan, saf **PHP 8.1+**
ile yazılmış; veritabanı gerektirmez. Linux üzerinde Apache veya Nginx + PHP-FPM ile çalışır.

## Klasör yapısı

```
website/
├── public/              # Web sunucusunun belge kökü (DocumentRoot) — yalnızca burası dışarı açılır
│   ├── index.php        # Ön denetleyici: tüm istekleri karşılar, sayfaları yönlendirir
│   ├── .htaccess        # Apache için temiz URL (mod_rewrite)
│   └── assets/          # css/, js/, img/
├── app/
│   ├── config.php       # Şirket adı, e-posta, telefon, adres vb. ayarlar
│   ├── bootstrap.php    # Oturum, güvenlik başlıkları, yardımcı fonksiyonlar, form işleme
│   ├── partials/        # Ortak şablon (layout) ve form yardımcıları
│   └── pages/           # Her sayfa için bir dosya
├── storage/             # Form mesajları (JSON Lines) — git'e eklenmez, web'den erişilemez
├── apache.conf.example
└── nginx.conf.example
```

## Sayfalar

| URL | Dosya |
|---|---|
| `/` | `app/pages/home.php` |
| `/ozellikler` | `features.php` |
| `/nasil-calisir` | `how.php` |
| `/mobil` | `mobile.php` |
| `/paketler` | `packages.php` |
| `/sss` | `faq.php` |
| `/hakkimizda` | `about.php` |
| `/iletisim` | `contact.php` (iletişim formu) |
| `/demo` | `demo.php` (demo talep formu) |
| `/kvkk` | `kvkk.php` |
| `/sitemap.xml`, `/robots.txt` | otomatik üretilir |

Yeni sayfa eklemek için `app/pages/` altına dosya oluşturup `public/index.php` içindeki
`$routes` dizisine ekleyin.

## Yerelde çalıştırma

```bash
php -S localhost:8000 -t website/public website/public/index.php
```

Ardından http://localhost:8000 adresini açın.

## Linux sunucuya kurulum (Ubuntu/Debian)

```bash
sudo apt install apache2 php libapache2-mod-php php-mbstring
sudo a2enmod rewrite headers expires deflate
sudo mkdir -p /var/www/titaniumcam && sudo cp -r website /var/www/titaniumcam/
sudo chown -R www-data:www-data /var/www/titaniumcam/website/storage
sudo cp /var/www/titaniumcam/website/apache.conf.example /etc/apache2/sites-available/titaniumcam.conf
sudo a2ensite titaniumcam && sudo systemctl reload apache2
```

Nginx kullanıyorsanız `nginx.conf.example` dosyasını `/etc/nginx/sites-available/` altına
kopyalayıp `php8.3-fpm` soket yolunu kendi sürümünüze göre düzenleyin.
HTTPS için: `sudo apt install certbot python3-certbot-apache && sudo certbot --apache`.

## Ayarlar

Şirket bilgilerini `app/config.php` içinde doğrudan değiştirebilir ya da ortam
değişkenleriyle verebilirsiniz (Apache: `SetEnv TC_EMAIL info@...`, PHP-FPM: `env[TC_EMAIL] = ...`):

| Değişken | Açıklama |
|---|---|
| `TC_COMPANY` | Şirket adı |
| `TC_BASE_URL` | Site adresi, ör. `https://www.ornek.com.tr` (sitemap için) |
| `TC_EMAIL`, `TC_PHONE`, `TC_ADDRESS` | İletişim bilgileri |
| `TC_WHATSAPP` | Dolu ise sağ altta WhatsApp butonu çıkar (ör. `905xxxxxxxxx`) |
| `TC_MAIL_TO` | Dolu ise form mesajları ayrıca bu adrese e-postalanır (PHP `mail()`) |
| `TC_MAIL_FROM` | Gönderen adresi |

## Formlar

İletişim ve demo formları CSRF koruması, bot tuzağı (honeypot), sunucu tarafı doğrulama,
KVKK onayı ve 30 saniyelik tekrar gönderim sınırı içerir. Her mesaj
`storage/<tür>-<YYYY-AA>.jsonl` dosyasına satır olarak eklenir:

```bash
tail -n 5 /var/www/titaniumcam/website/storage/demo-*.jsonl
```

> `kvkk.php` bir şablondur; yayına almadan önce hukuk danışmanınızla gözden geçirin.
