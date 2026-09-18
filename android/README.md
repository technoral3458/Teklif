# Parametrik Kesim — Android İstemcisi

FastAPI tabanlı "Parametrik Kesim" sunucusunun mobil istemcisi. Kullanıcı sahada
bir **3B katı model** (STL/OBJ) ya da **fotoğraf** yükler, panel parametrelerini
girer, sunucu modeli panellere dilimler; sonuç parmakla döndürülebilen 3B
görünümde incelenir ve CNC için **DXF/ZIP** indirilip WhatsApp veya e-posta ile
paylaşılır.

## Derleme

- Android Studio (Ladybug veya üstü), **JDK 17**, Android SDK **35**
- `minSdk 26`, `targetSdk 35`
- Kotlin 2.0.21 · Jetpack Compose (Material 3) · Hilt · Retrofit/OkHttp · CameraX

```bash
cd android
./gradlew :app:assembleDebug     # APK
./gradlew :app:testDebugUnitTest # birim testleri
```

Sunucu adresi uygulama içinden (Ayarlar ekranı) girilir; derleme zamanında
gömülü değildir. Sunucunuz HTTPS değilse `app/src/main/res/xml/network_security_config.xml`
içindeki `domain-config` listesine **yalnızca o host'u** ekleyin.

## Mimari

```
core/      biçimlendirme (mm, ondalık virgül), hata türleri, Result yardımcıları
domain/    model + saf iş mantığı (panel tahmini, istemci doğrulaması)
data/
  local/   DataStore ayarları, kalıcı çerez kavanozu (session, 30 gün)
  remote/  Retrofit sözleşmesi, base URL interceptor, 303/Location çözümleme
  media/   resim küçültme (2000 px / JPEG %85), MediaStore indirme, bildirim
  repository/  AuthRepository, JobRepository
ui/        Compose ekranları + ViewModel'ler, özel 3B görüntüleyici
```

Ekranlar: Sunucu/Ayarlar → Giriş → İş listesi → (Fotoğraf çek / Galeri / 3B model)
→ Parametreler → Sonuç (3B görünüm, özet, uyarılar, DXF/ZIP).

### Sunucunun HTML tabanlı olmasıyla başa çıkma

- OkHttp'de **yönlendirme takibi kapalı** (`followRedirects(false)`); başarı/hata
  `Location` başlığından okunur.
- `Location` içindeki `err=` / `msg=` değerleri URL-encoded Türkçe metindir;
  `URLDecoder.decode(..., "UTF-8")` ile çözülüp kullanıcıya **aynen** gösterilir.
- Girişte **200 = hatalı giriş**, 303 + `Set-Cookie: session=...` = başarılı.
- Oturum kalıcı çerez kavanozunda saklanır, uygulama yeniden açıldığında giriş
  istenmez. İsteğe bağlı biyometrik kilit vardır.

### 3B görüntüleyici

Kütüphane yok: ortografik izdüşüm + ressam algoritması, klasik `View` + `Canvas`
(Compose Canvas binlerce path'te yavaş kalıyor).

- Geometri **bir kez** yüzeylere açılır (`SurfaceSet`): köşeler global `FloatArray`
  dizilerinde, yüzeyler indeks dizileriyle gösterilir.
- Her karede köşe başına tek döndürme; arka yüz ayıklama ekran uzayındaki işaretli
  alanla; sıralama nesne yerine `LongArray` içinde paketlenmiş derinlik anahtarıyla.
- `Path` nesneleri `rewind()` ile yeniden kullanılır; sürükleme sırasında kenar
  çizgileri atlanır ve yüzey sayısı yüksekse seyreltilmiş (her 2. nokta) kontur
  kümesi çizilir.
- Tek parmak döndürür, iki parmak yakınlaştırır/kaydırır, çift dokunma sıfırlar;
  Perspektif / Ön / Yan / Üst hazır açıları vardır.

## ⚠️ Sunucuda eksik olan iki uç nokta

Uygulama iş listesi ve iş özeti için **JSON** bekler; HTML kazımaz. Sunucuya
aşağıdaki iki uç nokta eklenmelidir (oturum çerezi ile korunmalı):

```
GET /parametric/api/jobs
→ [ { "id":1, "name":"Dalga Panel", "src_kind":"image"|"mesh",
      "orig_name":"foto.jpg", "tri_count":0, "status":"Hazır"|"Yeni",
      "panel_count":50, "sheet_count":2, "thickness":18.0, "gap":6.0,
      "created_at":"2026-09-18 10:22" }, ... ]

GET /parametric/api/jobs/{jobId}
→ { ...yukarıdaki alanlar...,
    "axis":"z", "scale":1.0, "target_len":0.0, "simplify_tol":0.3,
    "hole_count":2, "hole_dia":10.0,
    "frame_count":2, "frame_t":18.0, "frame_h":120.0, "frame_fit":0.2,
    "sheet_w":2100.0, "sheet_h":2800.0, "part_gap":15.0,
    "img_w":1200.0, "img_h":800.0, "img_depth":180.0, "min_depth":40.0,
    "orient":"v", "shape_mode":"single", "invert":0, "smooth":1.0, "normalize":1,
    "summary": { "panel_count":50, "thickness":18.0, "gap":6.0, "pitch":24.0,
                 "stack_len":1194.0, "profile_w":180.0, "profile_h":800.0,
                 "cut_len_m":122.5, "sheet_count":2,
                 "hole_count":0, "hole_dia":10.0,
                 "frame_count":2, "frame_t":18.0, "frame_h":120.0,
                 "frame_len":1194.0, "warnings":["..."] } }
```

Bu uç noktalar yoksa iş listesi ekranı "Sunucuda /parametric/api/jobs uç noktası
bulunamadı. Sunucu sürümünü güncelleyin." hatasını gösterir. Diğer tüm uç noktalar
(`/auth/login`, `/parametric/upload`, `/parametric/upload-image`,
`/parametric/{id}/slice`, `/parametric/{id}/slice-image`, `/parametric/{id}/3d`,
`/parametric/{id}/dxf`, `/parametric/{id}/zip`, `/parametric/{id}/image`,
`/parametric/{id}/delete`) mevcut sözleşmeyle kullanılır.

`target_len` alanı sözleşmede yoktu; ölçek yerine hedef boy kullanılabildiği için
istemci bu alanı **isteğe bağlı** okur (yoksa 0 varsayılır).

## Testler

`./gradlew :app:testDebugUnitTest` — saf mantık birim testleri:

- `PanelEstimatorTest` — `pitch = kalınlık + boşluk`, panel sayısı formülü
- `NumbersTest` — virgüllü giriş çözümü, sunucuya nokta ile gönderim
- `HttpTest` — `Location` çözümleme, URL-encoded Türkçe hata mesajları
- `SurfaceSetTest` — ekstrüzyon köşeleri, yan yüzey/kapak sayısı, seyreltme
- `ParamValidationTest` — istemci doğrulaması, checkbox alanlarının gönderilmemesi
