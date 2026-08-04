# Teklif — Android uygulaması

Teklif sitesini kendi uygulaması içinde açan Android istemcisi. Ayrı bir arayüz
yazmaz; mevcut web arayüzünü gösterir, ama tarayıcı yerine gerçek bir uygulama
gibi davranır: kendi simgesi, açılış ekranı, geri tuşu, dosya yükleme ve PDF
indirme desteği vardır.

## Telefondan APK almak (bilgisayar gerekmez)

1. GitHub'da depoyu açın → **Actions** sekmesi → **Android APK** iş akışı.
2. **Run workflow** → çalıştırın (yaklaşık 3–5 dakika sürer).
3. Bitince **Releases** bölümünde en yeni sürümdeki `teklif.apk` dosyasına dokunun.
4. Android "bilinmeyen kaynaktan kurulum" uyarısı verirse tarayıcıya izin verin.
5. Uygulamayı ilk açtığınızda site adresini bir kez yazın.

`android/**` altında bir değişiklik push edildiğinde derleme kendiliğinden de çalışır.

## Site adresi

Uygulama hangi adresi açacağını üç yerden öğrenir, sırasıyla:

1. Kullanıcının uygulama içinde kaydettiği adres.
2. `app/src/main/res/values/strings.xml` içindeki `default_site_url`.
3. İkisi de boşsa ilk açılışta adres sorulur.

Adresi kalıcı olarak gömmek isterseniz `default_site_url` değerini doldurun;
o zaman uygulama kurulur kurulmaz doğrudan siteye bağlanır. Adresi sonradan
değiştirmek için uygulama simgesine basılı tutup **Site adresi** kısayolunu
kullanın.

## Neler destekleniyor

| Konu | Davranış |
| --- | --- |
| Oturum | Çerezler ve `localStorage` açık, JWT token uygulamada kalıcı — her açılışta yeniden giriş gerekmez |
| Geri tuşu | Sayfa geçmişinde geri gider, geçmiş bittiğinde uygulamadan çıkar |
| Yenileme | Sayfa en üstteyken aşağı çekince yenilenir |
| Dosya yükleme | Galeri/dosya seçimi ve doğrudan kamerayla fotoğraf çekme |
| İndirme | Sunucudan gelen dosyalar indirme yöneticisiyle, tarayıcıda üretilen PDF'ler (`blob:`) doğrudan İndirilenler klasörüne |
| Dış bağlantılar | `tel:`, `mailto:`, WhatsApp ve site dışı adresler sistem uygulamalarına devredilir |
| Bağlantı hatası | Türkçe hata ekranı, "Tekrar dene" ve "Site adresini değiştir" düğmeleriyle |
| Tema | Açık/koyu tema, web arayüzüyle aynı kurumsal renkler (`#1A237E` / `#F57C00`) |

Desteklenen sürüm: Android 8.0 (API 26) ve üzeri.

## İmzalama

Depoda `keystore/teklif-release.jks` adında hazır bir imzalama anahtarı bulunur;
parolaları `gradle.properties` içindedir. Bunun tek amacı, güncellemelerin eski
sürümün üzerine sorunsuz kurulabilmesi için her derlemede aynı imzanın
kullanılmasıdır.

Bu anahtar depoda açık durduğu için sizin adınıza uygulama imzalamak isteyen
birinin eline geçebilir. Uygulama yalnızca kendi ekibinize dağıtıldığı sürece
bu kabul edilebilir bir risktir; Play Store'a çıkacaksanız veya daha sıkı bir
kurulum istiyorsanız kendi anahtarınıza geçin:

```bash
keytool -genkeypair -v -keystore teklif-release.jks -alias teklif \
  -keyalg RSA -keysize 2048 -validity 10950
base64 -w0 teklif-release.jks   # çıktıyı kopyalayın
```

Deponun **Settings → Secrets and variables → Actions** bölümüne şunları ekleyin:

- `ANDROID_KEYSTORE_BASE64` — yukarıdaki base64 çıktısı
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Bu gizli değişkenler tanımlıysa iş akışı depodaki anahtar yerine sizinkini
kullanır. Anahtarı değiştirdiğinizde telefondaki eski uygulamayı bir kez
silmeniz gerekir.

## HTTPS

`res/xml/network_security_config.xml` şu an düz HTTP bağlantısına da izin
veriyor. Siteniz HTTPS ile yayındaysa oradaki `cleartextTrafficPermitted`
değerini `false` yapın.

## Yerelde derlemek (isteğe bağlı)

Android SDK kurulu bir bilgisayarda:

```bash
cd android
./gradlew assembleRelease
# çıktı: app/build/outputs/apk/release/app-release.apk
```
