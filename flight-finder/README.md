# UçuşBul — çevredeki havalimanlarını da tarayan uçak bileti arama uygulaması

Normal bilet siteleri "İstanbul → Jinan" dediğinizde yalnızca Jinan havalimanına
(TNA) bakar. Bu uygulama **hedef şehre X saat kara mesafesindeki tüm havalimanlarını
da** tarar ve hepsini tek listede fiyatına göre sıralar. Jinan örneğinde 2 saatlik
sınırla şunlar da taranır:

| Havalimanı | Şehir | Jinan'a uzaklık |
|---|---|---|
| TNA | Jinan | ana havalimanı |
| JNG | Jining | 141 km |
| DOY | Dongying | 157 km |
| WEF | Weifang | 171 km |
| HZA / LYI | Heze / Linyi | ~227 km |
| TSN | Tianjin | 252 km |
| TAO | Qingdao | 263 km |
| SJW | Shijiazhuang | 273 km |
| PKX | Pekin (Daxing) | 302 km |

Böylece "Pekin'e uçup hızlı trenle Jinan'a geçmek 8.000 ₺ daha ucuz" gibi seçenekleri
görebilirsiniz.

## Özellikler

- **Çevre havalimanı taraması** — hedefe 0–6 saat arası ayarlanabilir kara mesafesi.
  Süre; kuş uçuşu mesafenin 1,25 katı yol üzerinden, seçtiğiniz ulaşım tipine göre
  hesaplanır (karayolu 80 km/s, karışık 120 km/s, hızlı tren 200 km/s).
- **Tek havayoluna bağlı değil** — sağlayıcı mimarisi:
  - **Amadeus Self-Service** (GDS kaynaklı, yüzlerce havayolu) — ücretsiz anahtar
  - **Travelpayouts / Aviasales** (yüzlerce acente + doğrudan satın alma linki) — ücretsiz token
  - **Demo** — anahtar girilmeden tüm akışı denemek için (sonuçlar DEMO etiketli)

  İkisi de girilirse sonuçlar **birleştirilir** ve birlikte sıralanır. Yeni bir kaynak
  eklemek için `FlightProvider` arayüzünü uygulayıp `Providers`'a kaydetmek yeterli.
- **Sıralama** — en ucuz / en kısa toplam süre (uçuş + kara yolu) / akıllı (fiyat %65 + süre %35).
- **Satın alma yönlendirmesi** — sağlayıcının doğrudan linki, havayolunun kendi sitesi,
  Google Flights, Skyscanner ve Kayak bağlantıları.
- **Fiyat takibi** — aramayı takibe alırsınız, uygulama ~6 saatte bir arka planda tekrar
  arar; hedef fiyatın altına inince veya fiyat %7'den fazla düşünce **bildirim** gelir.
  Bildirimdeki "Bilete git" düğmesi doğrudan satın alma sayfasını açar.
- **Çevrimdışı havalimanı veritabanı** — 3.877 tarifeli havalimanı uygulamaya gömülü;
  Türkçe arama (Pekin, Şangay, Cinan, Kanton…) ve Türkçe karakter duyarsız eşleşme.

## APK'yı indirme

Her push'ta GitHub Actions APK üretir ve **Releases** sayfasına yükler:

1. Telefonun tarayıcısından deponun **Releases** sekmesini açın.
2. En üstteki `UcusBul-1.x.apk` dosyasına dokunun.
3. Android "bilinmeyen kaynak" izni isterse verin, kurun.

APK sabit bir anahtarla imzalandığı için yeni sürümler eskisinin üzerine kurulur
(bkz. `keystore/README.md`).

## İlk kullanım

Uygulama anahtarsız da çalışır ama **fiyatlar demo'dur**. Gerçek fiyatlar için:

1. **Ayarlar** sekmesi → *Amadeus* → "Ücretsiz anahtar al" ile
   [developers.amadeus.com](https://developers.amadeus.com) üzerinden kayıt olun,
   bir uygulama oluşturup **API Key** ve **API Secret** değerlerini girin.
   - Test ortamı ücretsizdir ama sonuç çeşitliliği sınırlıdır.
   - Canlı veri için Amadeus panelinden production anahtarı alıp
     "Canlı ortam" anahtarını açın.
2. İsterseniz ek olarak [travelpayouts.com](https://www.travelpayouts.com/) token'ı girin;
   iki kaynak birlikte taranır.
3. **Kaydet**'e basın.

Anahtarlar yalnızca telefonunuzda (`SharedPreferences`) tutulur, hiçbir sunucuya
gönderilmez.

## Kullanım

1. **Nereden** / **Nereye** alanlarına şehir yazıp listeden seçin.
2. Tarihi seçin.
3. **Seçenekler**'den "hedef şehre en fazla ... mesafe" kaydırıcısını ayarlayın
   (varsayılan 2 saat) ve ulaşım tipini seçin.
4. **Ara**'ya basın. Sonuçlarda her kartta uçuşun hangi havalimanına indiği ve
   hedef şehre kaç km / kaç saat kara yolu olduğu yazar.
5. Karta dokunun → satın alma seçenekleri açılır.

## Yerelde derleme

```bash
cd flight-finder
./gradlew assembleRelease      # APK: app/build/outputs/apk/release/
```

Android SDK 35 ve JDK 17 gerekir.

## Doğruluk notları

- Kara yolculuğu süresi bir **tahmindir**; gerçek süre trafik, tren seferi saatleri ve
  aktarmalara göre değişir. Uygulama mesafeyi (km) de gösterir ki kendiniz
  değerlendirebilesiniz.
- Uygulama bilet **satmaz**; bulduğu uçuşu satın alabileceğiniz yere yönlendirir.
  Nihai fiyat ve müsaitlik satıcının sayfasında doğrulanmalıdır.
- Hiçbir API "dünyadaki tüm havayollarını" %100 kapsamaz. Amadeus GDS taşıyıcılarının
  çok büyük bölümünü kapsar; bazı ucuz-maliyetli havayolları (ör. bazı Çin içi
  düşük maliyetliler) yalnızca kendi sitelerinde satış yapar. Bu yüzden her sonuçta
  Google Flights / Skyscanner bağlantıları da sunulur.
