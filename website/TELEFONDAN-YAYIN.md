# Telefondan Geliştirme ve cPanel'e Yayın

Bu sistemde bilgisayar gerekmez. Akış şöyledir:

```
Telefon (Claude Code) ──değişiklik──▶ GitHub ──otomatik──▶ GitHub Actions ──FTP──▶ cPanel (public_html)
```

1. Telefondan Claude Code'a ne istediğinizi yazarsınız ("fiyat sayfasına yeni paket ekle" gibi).
2. Claude değişikliği yapar ve GitHub'a gönderir.
3. GitHub Actions PHP dosyalarını kontrol eder, **yalnızca değişen dosyaları** cPanel'e FTP ile yükler.
4. Site birkaç dakika içinde güncellenir.

---

## Tek seferlik kurulum (yaklaşık 10 dakika)

### Adım 1 — cPanel'de FTP hesabını öğrenin
cPanel → **FTP Hesapları** (FTP Accounts).

- En kolayı cPanel'in **ana kullanıcısıyla** bağlanmaktır. Bu hesabın kök klasörü
  `/home/kullanici/` olduğu için `public_html` ve onun yanındaki `app/` klasörü doğru yere gider.
- Ayrı bir FTP hesabı açacaksanız **Dizin** (Directory) alanını boş bırakın veya `/` yazın.
  `public_html` yazarsanız aşağıdaki "Özel durum" bölümüne bakın.
- Not edin: **FTP sunucusu** (ör. `ftp.alanadiniz.com.tr`), **kullanıcı adı**, **şifre**.

### Adım 2 — PHP sürümünü ayarlayın
cPanel → **MultiPHP Yöneticisi** (veya "PHP Sürümü Seç") → alan adınız için **PHP 8.1 veya üstü** seçin.

### Adım 3 — GitHub'a bilgileri girin
Telefon tarayıcısında **github.com**'u açın. Bu ayar GitHub mobil uygulamasında yok;
tarayıcı menüsünden "Masaüstü sitesi" seçeneğini açmanız gerekebilir.

Depo → **Settings** → **Secrets and variables** → **Actions**

**Secrets** sekmesi → *New repository secret*:

| Ad | Değer |
|---|---|
| `FTP_PASSWORD` | cPanel FTP şifreniz |

**Variables** sekmesi → *New repository variable*:

| Ad | Örnek değer | Zorunlu mu? |
|---|---|---|
| `FTP_SERVER` | `ftp.alanadiniz.com.tr` | Evet |
| `FTP_USERNAME` | `kullanici` veya `ftp@alanadiniz.com.tr` | Evet |
| `SITE_URL` | `https://www.alanadiniz.com.tr` | Önerilir (yükleme sonrası kontrol) |
| `FTP_PROTOCOL` | `ftps` (varsayılan) veya `ftp` | Hayır |
| `FTP_PORT` | `21` | Hayır |
| `FTP_ROOT_DIR` | `./` | Hayır |
| `FTP_PUBLIC_DIR` | `public_html` | Hayır |

> Şifreyi **asla** sohbete, koda veya "Variables" sekmesine yazmayın; yalnızca **Secrets** sekmesine.
> GitHub şifreyi gizli tutar, kayıtlarda bile `***` olarak görünür.

### Adım 4 — İlk yüklemeyi başlatın
**GitHub mobil uygulaması** → depo → **Actions** → *Web sitesini cPanel'e yükle* → **Run workflow**.

Yeşil tik ✅ çıkarsa siteniz yayında. Bundan sonra `website/` klasöründeki her değişiklik
otomatik yüklenir.

---

## Sunucudaki klasör yapısı

```
/home/kullanici/
├── public_html/     ← herkese açık: index.php, .htaccess, assets/
├── app/             ← PHP kodu (web'den erişilemez)
├── storage/         ← iletişim/demo form mesajları (yüklemeyle silinmez)
└── .titaniumcam-deploy-state.json   ← hangi dosyaların yüklendiğinin kaydı (silmeyin)
```

Form mesajlarını görmek için: cPanel → **Dosya Yöneticisi** → `storage/` → `demo-2026-11.jsonl` gibi dosyalar.

## Sık karşılaşılan sorunlar

| Belirti (Actions kaydında) | Çözüm |
|---|---|
| "FTP ayarları eksik, yükleme atlandı" | Adım 3'teki değerleri girin. |
| `certificate`, `TLS`, `self signed` hatası | `FTP_SERVER` olarak hosting firmasının sunucu adını kullanın (ör. `server123.hostingfirmasi.com`; cPanel sağ panelinde yazar) **veya** `FTP_PROTOCOL` değişkenini `ftp` yapın (şifre şifrelenmeden gider, önerilmez). |
| `530 Login incorrect` | Kullanıcı adı/şifre hatalı. Ek FTP hesaplarında kullanıcı adı `ad@alanadiniz.com.tr` biçimindedir. |
| `ETIMEDOUT` / bağlantı kurulamıyor | Hosting firması yabancı IP'lerden FTP'yi engelliyor olabilir; destek ekibinden GitHub Actions için FTP erişimi isteyin. |
| Site "500 Internal Server Error" | PHP sürümü 8.1'den eski olabilir (Adım 2). |
| Site açılıyor ama sayfalar 404 | `public_html/.htaccess` yüklenmemiş olabilir; Dosya Yöneticisi'nde "gizli dosyaları göster"i açıp kontrol edin. |

## Özel durum: FTP hesabı doğrudan `public_html`'e açılıyorsa
Bu tür hesaplar üst klasöre çıkamaz. En doğrusu **ana cPanel kullanıcısıyla** bağlanmaktır (Adım 1).
Mecbur kalırsanız Variables'a `FTP_PUBLIC_DIR` = `.` ekleyin. Bu durumda `app/` ve `storage/`
klasörleri `public_html` içine yüklenir. İçlerindeki `.htaccess` dosyaları web erişimini engeller,
ancak bu yerleşim önerilmez.
