# Teklif — web arayüzü

React + Vite ile yazılmış arayüz. Aynı zamanda **kurulabilir bir uygulamadır (PWA)**:
telefonda tarayıcıdan açıp "Uygulamayı yükle" dendiğinde ana ekrana kendi simgesiyle
eklenir, adres çubuğu olmadan tam ekran açılır.

## Çalıştırma

```bash
npm install
npm run dev      # geliştirme
npm run build    # yayın çıktısı -> dist/
```

## API adresi (önemli)

`src/api/client.js` API adresini `VITE_API_BASE_URL` ortam değişkeninden okur.
Değişken verilmezse `http://localhost:8000/api` kullanılır — bu **yalnızca kendi
bilgisayarınızda** doğrudur. Telefondan açıldığında `localhost` telefonun kendisi
anlamına geldiği için uygulama sunucuya ulaşamaz ve giriş yapılamaz.

Yayına alırken `.env.example` dosyasını `.env` olarak kopyalayıp adresi yazın:

```
VITE_API_BASE_URL=https://teklif.sirketiniz.com/api
```

Bu değer derleme sırasında okunur, dolayısıyla değiştirdikten sonra `npm run build`
komutunu yeniden çalıştırmak gerekir.

## Telefona uygulama olarak kurmak

Kullanıcı tarafında: siteyi Chrome ile açın → sağ üstteki ⋮ menüsü →
**Uygulamayı yükle**. Uygulama zaten kurulabilir durumdaysa ekranın altında
otomatik olarak bir "Yükle" teklifi de belirir (`components/InstallPrompt.jsx`).

iPhone'da: Safari → paylaş düğmesi → **Ana Ekrana Ekle**.

### Sunucu tarafında gereken üç şey

1. **HTTPS zorunlu.** Servis çalışanı yalnızca `https://` (veya `localhost`)
   üzerinde çalışır. Düz HTTP'de site açılır ama uygulama olarak kurulamaz.
2. **SPA yönlendirmesi.** `/dealer/orders` gibi adresler doğrudan istendiğinde
   sunucu `index.html` döndürmelidir; aksi halde uygulama yenilendiğinde 404 alır.
   - nginx: `try_files $uri $uri/ /index.html;`
   - Apache: `FallbackResource /index.html`
3. **`manifest.webmanifest` erişilebilir olmalı** ve `application/manifest+json`
   içerik türüyle sunulmalıdır.

## PWA dosyaları

| Dosya | Görevi |
| --- | --- |
| `public/manifest.webmanifest` | Uygulama adı, simgeler, tam ekran ayarı |
| `public/sw.js` | Servis çalışanı — arayüzü önbelleğe alır |
| `public/icons/` | Ana ekran simgeleri (192/512 px + maskable) |
| `src/pwa.js` | Servis çalışanını kaydeder |
| `src/components/InstallPrompt.jsx` | "Yükle" teklifi |

**Teklif ve sipariş verileri önbelleğe alınmaz.** `sw.js` içinde `/api/` altındaki
tüm istekler dışarıda bırakılmıştır; ekranda hiçbir zaman eski veri görünmez.
Önbelleğe alınan tek şey arayüzün kendisidir, bu sayede bağlantı koptuğunda
beyaz ekran yerine uygulama açılır.

Arayüzde değişiklik yapıp yayınladığınızda kullanıcılar uygulamayı bir sonraki
açışlarında yeni sürümü alır; yeniden kurmaları gerekmez.
