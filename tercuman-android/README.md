# Tercüman TR ⇄ 中文 (Android)

Türkçe ile Çince arasında, yapay zekâ (Claude) destekli, sesli çeviri uygulaması.

## Nasıl çalışır?

1. **Mikrofon** → Mavi tuş Türkçe, sarı tuş Çince. **Basılı tutup** konuşursunuz, bırakınca
   çeviri başlar. Kısa dokunuşta mikrofon açık kalır, ikinci dokunuş bitirir; kelime aralarındaki
   duraklamalar konuşmayı kesmez (Ayarlar'dan 1–5 sn sessizlikte kendiliğinden kapanma da seçilebilir). Ortadaki küçük
   *Otomatik* tuş dili kendisi algılamaya çalışır. Ses tanıma için iki yol vardır:
   - **OpenAI Whisper** (Ayarlar'a OpenAI anahtarı girilirse, önerilir): Dil sesten algılanır, her telefonda güvenilir.
   - **Telefonun ses tanıması** (anahtar yoksa): Türkçe + Çince birlikte dinlenir; Android 14+ telefonlarda
     dil algılama ve dil değiştirme açılır. Eski telefonlarda yanlış algılarsa dili sabitleyin.
2. **Anlamlı çeviri** → Varsayılan **Ekonomik mod**da Claude yalnızca dili ve çeviriyi üretir
   (anlamı yine kurar ama ara metin yazmaz; token kullanımı yarıya iner). Ekonomik mod kapatılırsa
   İngilizce köprü ile üç adımda işler:
   - Konuşma tanıma hatalarını bağlama göre düzeltir,
   - Anlamı açık İngilizceye aktarır (deyimler, ima edilen özne, teknik terimler çözülür),
   - Bu anlamdan hedef dile, o dili ana dili olarak konuşan birinin söyleyeceği şekilde çevirir.

   Ekranda İngilizce köprü metni de görünür, böylece çevirinin doğru anlaşılıp anlaşılmadığını kontrol edebilirsiniz.
   Çince çevirilerin altında pinyin okunuşu da yazılır.
3. **Sesli okuma** → Çeviri, karşı tarafın dilinde, o dilin sesiyle otomatik okunur
   (Çince için Çin anakarası sesi seçilir).

## Ekranlar

- **Sohbet**: WhatsApp düzeni: Türkçe konuşan solda mavi balon, Çince konuşan sağda sarı balon. Altta iki büyük tuş: 🇹🇷 **Türkçe** (mavi) ve 🇨🇳 **中文** (sarı); aralarında küçük *Otomatik* tuş.
  Basılı tut-konuş-bırak, ya da dokun-konuş-tekrar dokun.
- **Yüz yüze mod** (üstteki iki kişi simgesi): Telefonu masaya koyun. Üst yarı Çinli misafire dönük
  (ters çevrili), alt yarı size. Herkesin kendi mikrofon tuşu var; ortadaki küçük tuş otomatik algılar.
  Karşı tarafın söylediği, her yarıda o kişinin dilinde büyük harflerle görünür.
- **Ayarlar**:
  - *Claude API anahtarı* (zorunlu)
  - *OpenAI API anahtarı* (isteğe bağlı): https://platform.openai.com → *API keys*. Ses tanıma
    dakikası yaklaşık 0,6 sent.
  - *Mikrofon ne zaman kapansın*: Dokununca / 1, 2, 3, 4, 5 sn sessizlik
  - *Görüşmenin konusu*: örn. "CNC membran kapak üretimi, fiyat teklifi". Terimler buna göre seçilir.
  - *Terim sözlüğü*: Her satıra `Türkçe = English = 中文`. Bu terimler her zaman aynen kullanılır.
  - *Ekonomik mod* (varsayılan açık): yalnızca çeviri, az geçmiş; maliyet ve süre yarıya iner
  - *Yapay zekâ modeli*: Haiku (varsayılan, en ucuz) / Sonnet / Opus
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

- En güvenilir kullanım dil tuşlarıdır. *Otomatik* tuş Whisper ile iyi çalışır; telefonun kendi tanımasında
  yanılabilir.
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
| `translate/TranslationEngine.kt` | Claude ile çeviri (ekonomik mod / İngilizce köprülü tam mod) |
| `translate/Lang.kt` | Diller ve yazı sistemine göre dil tahmini |
| `speech/WhisperSpeechInput.kt` | Ses kaydı, sessizlik algılama, Whisper ile dil algılamalı tanıma |
| `speech/PhoneSpeechInput.kt` | Telefonun ses tanıması (Türkçe + Çince, Android 14+ dil değiştirme) |
| `speech/Speaker.kt` | Sesli okuma |
| `ui/` | Sohbet, yüz yüze ve ayarlar ekranları |
