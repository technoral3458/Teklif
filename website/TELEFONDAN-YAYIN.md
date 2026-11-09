# Telefondan Geliştirme ve cPanel'e Yayın

Bu sistemde bilgisayar gerekmez. Akış şöyledir:

Canlı site: **titaniumcam.com** · Yükleme kayıtları: GitHub → Actions → "Web sitesini cPanel'e yükle"

```
Telefon (Claude Code) ──değişiklik──▶ GitHub ──otomatik──▶ GitHub Actions ──FTP──▶ cPanel (public_html)
```

1. Telefondan Claude Code'a ne istediğinizi yazarsınız ("fiyat sayfasına yeni paket ekle" gibi).
2. Claude değişikliği yapar ve GitHub'a gönderir.
3. GitHub Actions PHP dosyalarını kontrol eder, **yalnızca değişen dosyaları** cPanel'e FTP ile yükler.
4. Site birkaç dakika içinde güncellenir.

---

## Tek seferlik kurulum

Sunucu adı (`ftp.titaniumcam.com`) ve kullanıcı adı (`skttitan`) yükleme sistemine yazılı.
GitHub'a yalnızca **FTP şifresini** girmeniz gerekir.

1. Telefonda **Chrome** → `github.com` → `technoral3458` hesabıyla giriş yapın.
2. Chrome **⋮** menüsü → **Masaüstü sitesi** kutusunu işaretleyin.
3. Şu adresi açın: `github.com/technoral3458/Teklif/settings/secrets/actions`
4. Yeşil **New repository secret** butonu → **Name**: `FTP_PASSWORD` → **Secret**: FTP şifresi → **Add secret**.
5. Hosting panelinde PHP sürümünün **8.1 veya üstü** olduğundan emin olun.

Sonraki her `website/` değişikliğinde site otomatik yüklenir. Sistem bağlantıyı her zaman
şifreli (FTPS) ve sertifika doğrulamalı kurar. FTP girişinin ev klasörüne mi yoksa doğrudan
`public_html`'e mi açıldığını kendisi algılar.

Farklı bir sunucu veya kullanıcı kullanmak isterseniz aynı sayfadaki **Variables** sekmesine
`FTP_SERVER`, `FTP_USERNAME`, `FTP_PORT` ya da `SITE_URL` ekleyebilirsiniz.

---

## Sunucudaki klasör yapısı

FTP girişi ev klasörüne açılıyorsa (önerilen):
```
/home/kullanici/
├── public_html/     ← herkese açık: index.php, .htaccess, assets/
├── app/             ← PHP kodu (web'den erişilemez)
└── storage/         ← iletişim/demo form mesajları (yüklemeyle silinmez)
```

FTP girişi doğrudan `public_html`'e açılıyorsa:
```
public_html/
├── index.php, .htaccess, assets/
├── app/             ← .htaccess ile web erişimine kapalı
└── storage/         ← .htaccess ile web erişimine kapalı
```

Form mesajlarını görmek için: cPanel → **Dosya Yöneticisi** → `storage/` → `demo-2026-11.jsonl` gibi dosyalar.

## Sık karşılaşılan sorunlar

| Belirti (Actions kaydında) | Çözüm |
|---|---|
| "FTP_PASSWORD secret'ı tanımlı değil" | Kurulumdaki 4. adımı yapın. |
| `certificate` / `TLS` hatası | Variables'a `FTP_SERVER` olarak hosting firmasının sunucu adını girin (panelde veya hosting destek ekibinden öğrenebilirsiniz). |
| `530 Login incorrect` | Kullanıcı adı/şifre hatalı. Ek FTP hesaplarında kullanıcı adı `ad@alanadiniz.com.tr` biçimindedir. |
| `ETIMEDOUT` / bağlantı kurulamıyor | Hosting firması yurt dışı IP'lerden FTP'yi engelliyor olabilir; destek ekibinden yurt dışı FTP erişimini açmalarını isteyin. |
| Site "500 Internal Server Error" | PHP sürümü 8.1'den eski olabilir (Kurulum 5. adım). |
| Site açılıyor ama sayfalar 404 | `public_html/.htaccess` yüklenmemiş olabilir; Dosya Yöneticisi'nde "gizli dosyaları göster"i açıp kontrol edin. |
| Site hiç açılmıyor | Alan adı yeni alındıysa DNS yayılması birkaç saat sürebilir. |
