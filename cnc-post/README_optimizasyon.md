# AlphaCAM Post Optimizasyonu — Alpha Standard 3 ax Mill/Router

## Amaç
Aynı takımla yapılan operasyonlarda takım bloğunun (T, devir, ofsetler)
gereksiz tekrarını önlemek; takım değişiminde ise spindle'ı güvenli şekilde
durdurup (M05) yeniden başlatmak.

## Davranış (`$80` – yeni takım seçme)

1. **Aynı takım devam ediyorsa** (T = önceki takım) — derinlik veya devir
   farklı olsa bile — takım bloğu **hiç yazılmaz**. Operasyon sadece güvenli
   mesafe hareketiyle devam eder.
   ```
   ... T5 operasyonu biter ...
   G0 Z20.000
   G0 X.. Y..        <- sadece güvenli mesafe, T5/devir tekrar yok
   G0 Z10.000
   G1 Z.. F..
   ```

2. **Başka bir takıma geçişte** önce `M05` (spindle dur), sonra yeni takım
   yazılır:
   ```
   G0 Z20.000
   M05               <- takım değişimi: spindle durur
   T4
   G43 H4
   M03 S18000        <- yeni takım devri
   G54
   G52 Y-2100
   G0 X.. Y.. Z..
   ```

3. **İlk takımda M05 yazılmaz** (spindle program başında M405 ile yeni
   başlamıştır).

4. Program **sonundaki** tek `M05` (`$15`, M30'dan önce) güvenli duruş için
   korunmuştur.

## Nasıl çalışıyor?
`$1000` bölümüne `LAST_TOOL` değişkeni eklendi (`FIRST_RAPID` gibi tanımlı).
`$80` her çağrıldığında yeni takım **`NT`** ile `LAST_TOOL` karşılaştırılır:
- Eşitse (`$IF NT = LAST_TOOL`) → blok atlanır (`$ELSE` boş gövde deseni,
  postun `$25` bölümünde kullanılan kanıtlı yöntem).
- Farklıysa → (ilk takım değilse, `LAST_TOOL > 0`) M05 + takım bloğu yazılır
  ve `LAST_TOOL = NT` güncellenir.

### Önemli düzeltme (v3)
Önceki sürümde karşılaştırma `T` ile yapılıyordu. AlphaCAM APS'de takım
numarası **ifade (expression) içinde `T` ile değil `NT` ile** okunuyor; `[T]`
sadece çıktı token'ıdır. Bu yüzden aynı takımda blok yine tekrarlanıyordu.
`T` → `NT` olarak düzeltildi. Ayrıca `<>` yerine `= / $ELSE` ve `> 0`
kullanıldı (orijinal post da yalnızca bu operatörleri kullanıyor).

## Önemli not — aynı takımda farklı devir
Aynı takım numarasıyla iki operasyon **farklı devirlerde** programlanmışsa
(örn. T1 önce S18000, sonra S20000), ikinci operasyon **ilk operasyonun
deviriyle** (S18000) çalışır; çünkü aynı takımda blok yeniden yazılmaz.
İstediğin davranış bu. Eğer aynı takımda devir değişince **sadece devri**
(motoru durdurmadan, M05 olmadan) güncellemek istersen, bunu ekleyebilirim —
söylemen yeterli.

## Kullanımı
`Alpha_Standard_3ax_Mill_Router_optimized.txt` mevcut post kaynağının tam
yerine geçer. AlphaCAM'de kendi post dosyanın adı/uzantısıyla değiştirip
**APS ile yeniden derle (compile)**.
