# TeknoServis — Makine Servis Raporlama Uygulaması

Teknisyenlerin sahada, internet olmadan servis raporu oluşturabilmesi için yazılmış
Android uygulaması. Tüm kayıtlar telefonda tutulur; internet yalnızca mail gönderirken
gerekir.

## APK nereden indirilir?

Her derlemede imzalı bir APK otomatik yayınlanır:
**https://github.com/technoral3458/Teklif/releases** → `servis-apk-*` etiketli sürüm.

Telefondan bu sayfayı açıp APK dosyasına dokunun. Android "bilinmeyen kaynak" uyarısı
verirse *Ayarlar > Güvenlik > Bu kaynaktan kuruluma izin ver* seçeneğini açın.
Tüm sürümler aynı anahtarla imzalandığı için yeni sürüm eskisinin üzerine kurulur;
kayıtlarınız silinmez.

## Neler yapabilir?

**Servis raporu**
- Müşteri ve makine seçimi (kayıtlı değilse form içinden anında eklenir)
- Servis tipi: arıza, periyodik bakım, kurulum, devreye alma, revizyon, keşif, eğitim, garanti
- Öncelik (düşük → kritik/duruş) ve sonuç durumu (çözüldü, geçici çözüm, parça bekliyor,
  tekrar ziyaret gerekli)
- Tarih, başlangıç/bitiş saati (çalışma süresi otomatik hesaplanır), yol km
- **Bölüm bazlı çalışma kaydı:** mekanik, elektrik, elektronik, pnömatik, hidrolik,
  yazılım/PLC, kalibrasyon, otomasyon, temizlik/yağlama — her bölüm için ayrı işlem notu
- Arıza tanımı, **arıza nedeni (kök neden)**, yapılan işlem/çözüm, öneriler
- Kameradan çekerek veya galeriden **fotoğraf** ekleme; her fotoğrafa etiket
  (arıza / işlem öncesi / işlem sonrası / parça / makine etiketi) ve açıklama
- **Yedek parça listesi:** ad, kod, miktar, birim ve durum (takıldı, gerekli,
  sipariş edilecek, teklif verilecek)
- Müşteri yetkilisinden **parmakla imza** alma
- Sonraki bakım tarihi

**Çıktı ve gönderim**
- Firma başlıklı, fotoğraflı ve imzalı **A4 PDF**
- PDF'i WhatsApp/Drive vb. ile paylaşma
- **Mail gönder:** rapor özeti mail gövdesinde, PDF (ve istenirse fotoğraflar) ek olarak

**Takip**
- Özet paneli: bu ayki servis sayısı, açık kayıtlar, parça bekleyenler, makine sayısı
- Yaklaşan bakımlar ve takip gerektiren işler
- Rapor arama/filtreleme (rapor no, müşteri, seri no, arıza metni…)
- Makine kartında o makinenin **tüm servis geçmişi**
- JSON olarak yedek alma / geri yükleme

## İlk kurulumdan sonra

1. Açılışta firma bilgilerinizi ve teknisyen adını girin (rapor başlığında görünür).
2. **Ayarlar > Mail Ayarları**'ndan SMTP bilgilerinizi girin:
   - Gmail / Outlook / Yandex için hazır ayar düğmeleri var.
   - Gmail ve Outlook, normal hesap parolasını kabul etmez; hesabınızda 2 adımlı
     doğrulamayı açıp **uygulama parolası** üretmeniz ve onu girmeniz gerekir.
   - Kendi sunucunuz varsa: 465 → SSL, 587 → STARTTLS.
3. **Test maili gönder** ile ayarları doğrulayın.
4. Müşteri ve makinelerinizi bir kez kaydedin; sonrasında rapor açmak birkaç dokunuş.

Parolanız yalnızca telefonda saklanır ve mail gönderirken doğrudan kendi sunucunuza
iletilir; araya başka bir servis girmez.

## Web tarafı ile ilişkisi

Aynı raporlar web panelinde de yönetilebilir (*Servis Raporları*, *Makineler &
Müşteriler*, *Mail Ayarları* menüleri). Telefondaki kayıtlar **Ayarlar > Yedeği dışa
aktar** ile alınıp `POST /api/service/sync/` ucuna gönderilerek sunucuya aktarılabilir;
kayıtlar kimlik üzerinden eşlendiği için aynı yedek tekrar gönderildiğinde kopya oluşmaz.

## Geliştirme

```bash
cd servis-app
./gradlew assembleDebug      # geliştirme derlemesi
./gradlew assembleRelease    # imzalı APK (keystore/README.md)
```

- Kotlin + Jetpack Compose (Material 3), minSdk 26 / targetSdk 35
- Veri: uygulama klasöründe JSON dosyaları (`Repository.kt`) — harici veritabanı yok
- PDF: `android.graphics.pdf` üzerine yazılmış yerleşim motoru (`pdf/PdfBuilder.kt`)
- Mail: JavaMail (`com.sun.mail:android-mail`) ile doğrudan SMTP
