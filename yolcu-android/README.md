# Yolcu – Avrupa için Türkçe navigasyon (Android + Android Auto)

Ücretsiz harita verisiyle (OpenStreetMap) çalışan, Türkçe sesli yol tarifi veren navigasyon.
Avrupa'da araçla gezenler için: **ülke sınırlarını ve sınır kapılarını bilir**, bir sonraki sınıra
kaç km kaldığını söyler, gidilen yolu ülke ülke sayar ve **Android Auto** ekranında çalışır.

## Özellikler

- **Harita:** OpenStreetMap karoları. Gezilen bölgeler telefonda saklanır; aynı yoldan dönerken internetsiz de görünür.
- **Rota ve Türkçe sesli tarif:** Valhalla (OSM) rota sunucusu Türkçe anlatım üretir ("200 metre sonra sağa dönün",
  çıkış tabelaları dahil). Çok uzun rotalarda OSRM devreye girer; anlatımı uygulama Türkçeleştirir.
- **Sınırlar:** Rota hesaplanınca hangi kilometrede hangi ülkeye geçileceği ve bilinen sınır kapısının adı
  (Kapıkule, Kalotina, Horgoš, Nickelsdorf…) listelenir. Yolda "20 / 5 / 1 km sonra Bulgaristan sınırı, Kapıkule"
  diye okunur; yeni ülkeye girince "Bulgaristan'a hoş geldiniz".
- **Kilometre sayacı:** Toplam, ülke ülke ve son sınırdan beri gidilen yol. Sıfırlanabilir.
- **Rotadan çıkınca** otomatik yeniden hesaplar.
- **Android Auto:** Araç ekranında harita, sıradaki dönüş, kalan km/süre/varış ve sonraki sınır. Arama da araç ekranından yapılır.
- **Arama:** Şehir, adres, yer adı (Photon / OpenStreetMap).

## Kurulum

1. GitHub → **Releases** → en son `Yolcu v1.0.x` → `Yolcu-v1.0.x.apk` dosyasını telefona indirip kurun
   ("bilinmeyen kaynak" izni isterse verin).
2. Uygulamayı açın, **konum iznini** verin ("Uygulamayı kullanırken"). Android 13+ bildirim izni de ister.
3. Arama kutusuna hedefi yazın → sonuçtan seçin → rota, süre ve sınır geçişleri görünür → **Başlat**.

### Android Auto'da görünmesi için

Android Auto 17.7 ve sonrası, Play Store dışından (APK ile) kurulan Car App Library uygulamalarını
araç ekranında **göstermez**; geliştirici ayarlarındaki "Bilinmeyen kaynaklar" yalnızca müzik/mesaj
türü uygulamaları kapsar. Araçta çalışması için uygulamanın Google Play üzerinden kurulmuş olması gerekir:

1. Google Play Console'da geliştirici hesabı açın (tek seferlik ücret).
2. Yeni uygulama oluşturun (paket adı `com.teklif.yolcu`), **Test → Dahili test** kanalına Releases'teki
   `Yolcu-v1.0.x.aab` dosyasını yükleyin, kendi Gmail adresinizi test kullanıcısı yapın.
3. Test bağlantısını telefonda açıp uygulamayı Play Store'dan kurun. Android Auto artık kabul eder.

Alternatif (dongle): AAWireless gibi kablosuz adaptörlerin "geliştirici modu", Android Auto'nun kurulum
kaynağı kontrolünü atlatır; APK ile kurulu Yolcu o durumda araçta görünür.

### Gereksinimler

- Android 8.0+ ve Google Play Hizmetleri (konum için).
- İnternet (harita karoları, rota, arama). Avrupa'da dolaşım verisi yeterlidir; harita karoları çok küçüktür.
- Türkçe sesli okuma için telefonda Türkçe TTS sesi (Google Metin Okuma'da genelde hazır gelir).

## Sınırlar ve bilinmesi gerekenler

- Ülke sınırları Natural Earth 1:50m verisinden hesaplanır; sınır noktası birkaç yüz metre ile 2 km arasında
  sapabilir. Sınır kapısı adı, geçiş noktasına 25 km içindeki bilinen kapılar listesinden gelir; listede
  olmayan küçük kapılarda yalnızca "X sınırı" yazar.
- Rota sunucuları ücretsiz topluluk sunucularıdır (fair use). Yoğun zamanlarda yavaşlayabilir.
- Trafik bilgisi, hız kamerası ve şerit yardımı yoktur.

## Geliştirme

```bash
cd yolcu-android
./gradlew assembleDebug
```

| Dosya | Görev |
|---|---|
| `geo/Geo.kt` | Mesafe, yön, Web Mercator, çokgen içi testi, polyline çözme |
| `geo/Countries.kt` | Ülke sınırları (assets/countries.geojson) ve Türkçe adlar |
| `geo/BorderCrossings.kt` | Avrupa'nın başlıca sınır kapıları |
| `map/TileCache.kt`, `map/MapRenderer.kt` | Karo indirme/önbellek ve harita çizimi (telefon + araç ortak) |
| `routing/Router.kt` | Valhalla / OSRM rota, Türkçe talimat üretimi |
| `routing/Geocoder.kt` | Photon ile yer arama |
| `nav/Nav.kt` | Tek navigasyon durumu (ekranlar ve servis bunu paylaşır) |
| `nav/NavigationEngine.kt` | Rotaya oturtma, sıradaki dönüş/sınır, sesli uyarı zamanlaması, rotadan çıkma |
| `nav/RouteBorders.kt` | Rota üzerindeki ülke geçişleri |
| `nav/TripRecorder.kt` | Kilometre sayacı |
| `nav/LocationService.kt` | Ön plan konum servisi, TTS, bildirim |
| `car/` | Android Auto: harita yüzeyi, navigasyon şablonu, arama |
| `ui/` | Telefon ekranı (Compose) |
