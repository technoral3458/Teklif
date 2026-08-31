# Petkit Türkçe (PetkitTR)

**PETKIT YUMSHARE DUAL-HOPPER 2** ve diğer Petkit besleyicileri için Türkçe,
resmi olmayan Android uygulaması. Doğrudan Petkit bulut sunucusuna bağlanır;
arada başka bir sunucu yoktur.

## APK nasıl indirilir?

1. GitHub'da bu deponun **Actions** sekmesine girin.
2. **"Android APK derle"** iş akışının en son başarılı çalışmasını açın.
3. Sayfanın altındaki **Artifacts → PetkitTR-APK** dosyasını indirin (zip içinde APK vardır),
   veya deponun **Releases** bölümünden `PetkitTR-1.0.0-buildXX.apk` dosyasını doğrudan indirin.
4. Telefonda APK'ya dokunun, "bilinmeyen kaynaklardan kuruluma izin ver" uyarısını onaylayın.

### Kalıcı imza (güncellemelerin üst üste kurulması için)

Özel imza anahtarı bilinçli olarak depoya konmadı. Varsayılan durumda GitHub
Actions her derlemede yeni bir anahtar üretir; bu APK kurulur ama **imzası her
derlemede değiştiği için yeni sürümü kurmadan önce eskisini kaldırmanız
gerekir**.

Güncellemelerin üst üste kurulmasını istiyorsanız bir kez şunu yapın:

```bash
keytool -genkeypair -keystore petkit-tr.jks -storetype PKCS12 \
  -alias petkittr -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass SIZIN_PAROLANIZ -keypass SIZIN_PAROLANIZ \
  -dname "CN=PetkitTR, O=Kisisel, C=TR"
base64 -w0 petkit-tr.jks    # çıktıyı kopyalayın
```

Sonra GitHub'da **Settings → Secrets and variables → Actions** bölümünde iki sır
oluşturun:

- `ANDROID_KEYSTORE_BASE64` → yukarıdaki base64 çıktısı
- `ANDROID_KEYSTORE_PASSWORD` → `SIZIN_PAROLANIZ`

Bundan sonraki tüm derlemeler aynı anahtarla imzalanır. `petkit-tr.jks`
dosyasını yedekleyin, depoya eklemeyin (`.gitignore` içinde).

## Neler yapılabiliyor?

| Ekran | İşlevler |
|---|---|
| **Giriş** | Petkit e-posta/telefon + şifre, bölge (sunucu) seçimi, oturum hafızası |
| **Cihazlarım** | Hesaba bağlı tüm cihazları listeler, model adlarını Türkçe gösterir |
| **Durum** | Çevrimiçi/çevrimdışı, priz/pil, pil yüzdesi, Wi-Fi sinyali, hata kodu, her iki haznenin mama durumu, bugün planlanan/verilen porsiyon ve gram, yeme sayısı, nem alıcı kalan gün, seri no ve yazılım sürümü |
| **Besleme** | Hazne 1 ve Hazne 2 için ayrı porsiyon seçip anında mama verme, hızlı besleme düğmeleri (1/2/3/5), gönderilen beslemeyi iptal etme |
| **Plan** | Cihazdaki öğünleri listeleme, saat + iki hazne miktarı + haftanın günleri ile yeni öğün ekleme, öğün silme |
| **Kayıtlar** | Gün gün geçmiş: besleme, yeme, mama uyarısı, pil olayları; geriye 30 güne kadar gezinme |
| **Ayarlar** | Gösterge ışığı, çocuk kilidi, besleme sesi, sistem sesleri, mama azaldı uyarısı, nem alıcı uyarısı, düşük pil uyarısı, artan mama kontrolü, yeme algılama, kamera/gece görüşü/mikrofon/video yükleme, ses düzeyi, hareket duyarlılığı, cihaz adı değiştirme, nem alıcı sıfırlama, mama sayacı sıfırlama, ham JSON ayar gönderme |
| **API Konsolu** | Her isteğin/yanıtın günlüğü, hazır şablonlar, elle POST gönderme, sonucu panoya kopyalama |

Durum verileri cihaz ekranındayken 30 saniyede bir kendiliğinden yenilenir.

### Bilinçli olarak yapılmayanlar

- **Canlı kamera görüntüsü / sesli konuşma yok.** Petkit canlı yayını kendi kapalı
  (şifreli, belgelenmemiş) gerçek zamanlı video protokolüyle taşıyor; bu
  uygulamada tersine mühendislikle çözülmüş bir uygulaması yok. Kamera
  *ayarları* (açık/kapalı, gece görüşü, mikrofon, yükleme) buradan
  değiştirilebilir, görüntü resmi Petkit uygulamasından izlenir.
- **Cihaz kurulumu (Wi-Fi eşleştirme) yok.** İlk kurulumu resmi uygulamayla yapın.

## Cihaz listesi boş geliyorsa

Giriş başarılı ama "Hesabınıza bağlı cihaz bulunamadı" yazıyorsa sırayla:

1. **Cihaz hesaba eklenmiş mi?** Hesap açmak tek başına yetmez. Besleyicinin
   resmi Petkit uygulamasıyla Wi-Fi'ye alınıp hesabınıza bağlanması gerekir.
   Resmi uygulamada cihazı görmüyorsanız burada da görünmez.
2. **Bölge doğru mu?** Çıkış yapıp diğer bölge sunucusuyla girin. Hesap hangi
   bölgede açıldıysa cihazlar yalnızca orada görünür.
3. **Ham yanıtı okuyun.** Sağ üstteki konsol simgesine dokunun, *Cihaz listesi*
   ve *Aile listesi* şablonlarını çalıştırın. Sunucu gerçekten boş liste mi
   döndürüyor, yoksa isteği mi reddediyor - orada görünür.

## Gizlilik seçeneği: şifreyi saklamamak

Giriş ekranındaki **"Şifremi bu telefonda sakla"** anahtarını kapatırsanız
hiçbir şifre bilgisi kaydedilmez. Karşılığı: oturum düştüğünde uygulama
sessizce yeniden giriş yapamaz, giriş ekranını açar. Açık bırakırsanız
şifrenin MD5 özeti uygulamanın özel alanında tutulur; **Ayarlar → Hesap ve
gizlilik** bölümünden sonradan da silebilirsiniz.

## Önemli: API hakkında dürüst not

Petkit'in sunucu API'si resmi olarak belgelenmemiştir. Uç noktalar ve alan
adları topluluk çalışmalarından derlenmiştir ve **canlı bir hesapla test
edilmemiştir**. Bu yüzden uygulama şöyle tasarlandı:

- Bilinmeyen/eksik alanlar uygulamayı çökertmez, "bilinmiyor" olarak görünür.
- Her ekranda cihazın gönderdiği **ham JSON** görülebilir.
- **API Konsolu** ekranından her isteğin gövdesi ve yanıtı okunabilir, elle
  istek gönderilebilir.

Bir işlev çalışmazsa: API Konsolu → *Tümünü kopyala* ile günlüğü alın; hangi
alanın reddedildiği oradan anlaşılır ve eşleme kolayca düzeltilir.

## Kaynak koddan derleme

```bash
cd android
./gradlew assembleRelease      # veya assembleDebug
# APK: app/build/outputs/apk/release/
```

`keystore/petkit-tr.jks` dosyası varsa onunla, yoksa Android'in hata ayıklama
anahtarıyla imzalanır; her iki durumda da telefona kurulabilir bir APK çıkar.

Gerekenler: JDK 17, Android SDK (compileSdk 35). Uygulama Kotlin + Jetpack
Compose (Material 3) ile yazılmıştır; ağ için OkHttp, JSON için
kotlinx.serialization kullanılır. Ek bir arka uç sunucusu yoktur.

## Gizlilik

- E-posta ve şifrenin MD5 özeti yalnızca telefonun uygulamaya özel alanında
  saklanır (oturum düşerse sessizce yeniden giriş yapabilmek için).
- Veriler yalnızca Petkit sunucularına gider; başka hiçbir yere veri
  gönderilmez, analitik/izleme yoktur.
- Uygulama izinleri: yalnızca internet.

## Sürüm

1.0.0 — ilk sürüm.
