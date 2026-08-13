# TV Kumanda — Arçelik / Android TV uzaktan kumanda uygulaması

Android telefondan Arçelik (ve diğer Android TV / Google TV) televizyonları kumanda etmek
için yazılmış, tek dosyalık kuruluma sahip yerli bir uygulama. Wi‑Fi üzerinden çalışır,
telefonda kızılötesi (IR) donanımı gerektirmez.

Televizyonun kendi kumandası kaybolduğunda ya da YouTube gibi uygulamalarda uzun listelerde
gezinmek zor geldiğinde işe yarar: parmakla kaydırarak gezinme, HDMI girişleri arasında tek
dokunuşla geçiş ve doğrudan YouTube araması yapabilirsiniz.

## Neler var

| Bölüm | Ne yapar |
| --- | --- |
| **Dokunmatik gezinme** | Parmağı kaydırdıkça yön tuşu gönderir; dokunmak “OK”, basılı tutmak “Geri”. Uzun listelerde tek tek tuşa basmaktan çok hızlı. |
| **Yön tuşları** | Klasik D‑pad görünümü isteyenler için ikinci mod. Basılı tutunca tekrar eder. |
| **Girişler** | Kaynak listesi, TV, HDMI 1‑4, AV ve Rehber tuşları. |
| **Uygulamalar** | YouTube, Netflix, Prime Video, Disney+, Spotify, Exxen, BluTV, Play Store — TV'de doğrudan açar. |
| **Oynatma** | Oynat/duraklat, ileri‑geri sarma, önceki/sonraki. |
| **Ses ve kanal** | Ekrandaki tuşlar **ve** telefonun fiziksel ses tuşları TV'nin sesini ayarlar. |
| **Yazı** | TV'deki arama kutusuna metin yazar; ayrıca YouTube'da doğrudan arama sonucunu açar. |
| **Rakamlar** | Kanal numarası girmek için tuş takımı. |

## Kurulum

Gereken: **Android Studio** (Ladybug veya üstü) ve **JDK 17**.

```bash
cd android-tv-remote
./gradlew assembleDebug
```

APK şurada oluşur:

```
app/build/outputs/apk/debug/app-debug.apk
```

Telefona kurmak için:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb` yoksa APK dosyasını telefona kopyalayıp dosya yöneticisinden açmanız da yeterli
(“bilinmeyen kaynaklardan kuruluma izin ver” demeniz istenir).

Android Studio ile: `android-tv-remote` klasörünü açın, telefonu USB ile bağlayıp **Run**
deyin. İlk açılışta Gradle bağımlılıkları indireceği için internet gerekir.

## İlk kullanım

1. Telefon ve TV **aynı Wi‑Fi ağında** olmalı.
2. TV'yi açın, uygulamayı başlatın. TV birkaç saniye içinde listede görünür.
3. TV'ye dokunun. Televizyon ekranında **6 karakterlik bir kod** çıkar (örn. `4A7B2C`).
4. Kodu uygulamaya girin. Bu bir kereliktir — sonraki açılışlarda doğrudan bağlanır.

TV listede görünmüyorsa: bazı modemler mDNS yayınını engeller. Bu durumda TV'de
**Ayarlar › Ağ ve İnternet** bölümünden IP adresini okuyup uygulamadaki “IP ile bağlan”
alanına yazın.

## Nasıl çalışıyor

Uygulama Google'ın Android TV cihazlarında çalışan **Android TV Remote Service v2**
protokolünü konuşur — Google TV uygulamasının kullandığı protokolün aynısı. Arçelik,
Beko ve Grundig'in Android TV modelleri bu servisi standart olarak içerir.

İki TCP/TLS kanalı var:

- **6467 — eşleşme.** Telefon kendi ürettiği sertifikayı sunar, TV ekranda bir kod
  gösterir. Kod, iki tarafın açık anahtarları ve koddaki nonce'tan türetilen bir
  SHA‑256 özetiyle doğrulanır; böylece kod ağda hiç dolaşmaz.
- **6466 — kumanda.** Eşleşmiş sertifikayla açılır. Tuşlar Android `KeyEvent` kodları
  olarak protobuf mesajlarıyla gönderilir. TV düzenli ping atar, yanıt verilmezse
  bağlantıyı düşürür.

Kod tarafında dikkat edilenler:

- **Protobuf elle yazıldı.** Protokol yalnızca birkaç alan tipi kullandığı için
  `protoc`/codegen bağımlılığı yerine ~150 satırlık bir kodlayıcı/çözücü var
  (`protocol/Proto.kt`). Bilinmeyen alanlar sessizce atlanır, yani TV yazılımı yeni
  alanlar eklerse uygulama kırılmaz.
- **Sertifika elle üretiliyor.** Android'de sertifika *üretme* API'si yok; genelde
  BouncyCastle (~5 MB) eklenir. Tek bir self‑signed RSA sertifikası gerektiği için
  bunun yerine gereken DER yapısı `protocol/ClientCertificate.kt` içinde yazıldı.
  Sertifika `filesDir/client.p12` içinde saklanır — **silinirse TV ile yeniden
  eşleşmek gerekir**.
- **Sertifika sabitleme.** TV self‑signed sertifika sunduğu için zincir doğrulaması
  yapılamıyor. Bunun yerine eşleşme anındaki parmak izi kaydediliyor ve sonraki
  bağlantılarda aynısı bekleniyor; aynı ağdaki başka bir cihaz TV taklidi yapamıyor.

## Testler

Protokol katmanı saf JVM olduğu için emülatör veya TV olmadan test edilebiliyor.
Testler, aynı protokolü konuşan sahte bir TV sunucusuna karşı **tam el sıkışmayı**
çalıştırır: eşleşme akışı, kod doğrulaması, ping yanıtı, tuş ve uygulama komutları.

```bash
./gradlew :app:testDebugUnitTest
```

## Bilinen sınırlar

- **Türkçe karakterler.** Donanım klavyesi tuş kodlarında `ı ş ğ ü ö ç` karşılığı yok;
  metin gönderilirken en yakın ASCII harfe indirgenir. YouTube araması bundan
  etkilenmez (adres satırından gider, Türkçe karakterleri doğru taşır).
- **HDMI tuşları.** Bazı modeller `HDMI 1‑4` tuş kodlarına yanıt vermez. O durumda
  “Kaynak” tuşuyla listeyi açıp yön tuşlarıyla seçmek gerekir.
- **Kapalı TV'yi açmak.** Güç tuşunun bekleme modundan açması için TV'de “ağ üzerinden
  uyandırma / hızlı açılış” ayarının açık olması gerekir; kapalıyken TV ağ servisini
  de kapattığı için uygulama ulaşamaz.
- **Sesli asistan.** Mikrofon tuşu TV'de asistanı açar, ancak telefonun mikrofonundan
  ses akışı gönderilmez.

Yeni bir uygulama kısayolu eklemek için `protocol/TvKeys.kt` içindeki `TvApps.defaults`
listesine paket adıyla bir satır eklemek yeterli.
