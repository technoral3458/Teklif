# Tercüman TR ⇄ 中文 (Android)

Türkçe ile Çince arasında, yapay zekâ (Claude) destekli, sesli çeviri uygulaması.

## Nasıl çalışır?

1. **Mikrofon** → Telefonun konuşma tanıma servisi konuşmayı yazıya çevirir.
   *Otomatik* modda Türkçe mi Çince mi konuşulduğu kendiliğinden algılanır.
2. **Anlamlı çeviri (İngilizce köprü)** → Claude metni üç adımda işler:
   - Konuşma tanıma hatalarını bağlama göre düzeltir,
   - Anlamı açık İngilizceye aktarır (deyimler, ima edilen özne, teknik terimler çözülür),
   - Bu anlamdan hedef dile, o dili ana dili olarak konuşan birinin söyleyeceği şekilde çevirir.

   Ekranda İngilizce köprü metni de görünür, böylece çevirinin doğru anlaşılıp anlaşılmadığını kontrol edebilirsiniz.
   Çince çevirilerin altında pinyin okunuşu da yazılır.
3. **Sesli okuma** → Çeviri, karşı tarafın dilinde otomatik seslendirilir.

## Ekranlar

- **Sohbet**: Üstte yön seçimi: `Otomatik`, `TR → 中文`, `中文 → TR`. Alttaki büyük mikrofon tuşu.
- **Yüz yüze mod** (üstteki iki kişi simgesi): Telefonu masaya koyun. Üst yarı Çinli misafire dönük
  (ters çevrili), alt yarı size. Herkesin kendi mikrofon tuşu var; ortadaki küçük tuş otomatik algılar.
  Karşı tarafın söylediği, her yarıda o kişinin dilinde büyük harflerle görünür.
- **Ayarlar**:
  - *Claude API anahtarı* (zorunlu)
  - *Görüşmenin konusu*: örn. "CNC membran kapak üretimi, fiyat teklifi". Terimler buna göre seçilir.
  - *Terim sözlüğü*: Her satıra `Türkçe = English = 中文`. Bu terimler her zaman aynen kullanılır.
  - *Çeviri kalitesi*: Hızlı / Dengeli / En iyi
  - *Sesli oku* aç/kapa

## Kurulum

1. GitHub'da depo sayfasında **Releases** bölümüne gidin, en son `Tercüman v1.0.x` sürümündeki
   `Tercuman-v1.0.x.apk` dosyasını telefona indirin (veya **Actions** sekmesinde son derlemeden `Tercuman-apk`).
2. Dosyayı açın; "Bilinmeyen kaynaklardan yükleme" izni isterse verin.
3. Uygulamayı açın → **Ayarlar** → Claude API anahtarınızı yapıştırın
   (https://console.anthropic.com → *API Keys* → *Create Key*).
4. İlk mikrofon kullanımında mikrofon iznini verin.

### Telefonda olması gerekenler

- İnternet bağlantısı (çeviri ve konuşma tanıma için).
- **Google** uygulaması (konuşma tanıma servisi). Çince tanımanın daha iyi çalışması için:
  *Ayarlar → Google → Ses → Çevrimdışı konuşma tanıma* bölümünden **中文 (普通话)** ve **Türkçe** paketlerini indirebilirsiniz.
- Çince sesli okuma için: *Ayarlar → Erişilebilirlik / Sistem → Metin okuma çıkışı → Google* altında **Çince** ses verisini yükleyin.

## İpuçları

- Otomatik algılama, konuşma sırası değiştikçe bir sonraki konuşmacının diğer dilde konuşacağını varsayar
  ve iki dili birden dinler. Yanlış algılarsa yön seçiminden dili sabitleyin veya yüz yüze modda kişinin kendi tuşunu kullanın
  (en güvenilir yöntem budur).
- Kısa, tam cümleler en iyi sonucu verir. Konu ve terim sözlüğünü doldurmak teknik görüşmelerde çeviriyi belirgin şekilde iyileştirir.

## Geliştirme

Android Studio ile `tercuman-android` klasörünü açın veya:

```bash
cd tercuman-android
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Depoya her push'ta GitHub Actions APK'yı derleyip Release olarak yayınlar.

Kod düzeni:

| Dosya | Görev |
|---|---|
| `translate/TranslationEngine.kt` | Claude ile TR→EN→ZH / ZH→EN→TR köprü çeviri |
| `translate/Lang.kt` | Diller ve yazı sistemine göre dil tahmini |
| `speech/SpeechInput.kt` | Mikrofon ve otomatik dil algılamalı konuşma tanıma |
| `speech/Speaker.kt` | Sesli okuma |
| `ui/` | Sohbet, yüz yüze ve ayarlar ekranları |
