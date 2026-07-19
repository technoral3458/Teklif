# AlphaCAM Post Optimizasyonu — Alpha Standard 3 ax Mill/Router

## Sorun
Takımlar farklı işlem derinliklerinde çalıştığında, post her operasyon
başında spindle'ı **M05** ile durdurup tekrar başlatıyordu. Bu, router'da
her operasyon arasında gereksiz durma/hızlanma bekleme süresi demekti.

## Neden oluyordu?
Postun `$80` (yeni takım seçme) bölümünde şu blok vardı:

```
$ELSE
$IF OPN > 1
M05          <-- ilk operasyondan sonraki HER operasyonda spindle durdu
$ENDIF
T[T]
...
```

`OPN` = operasyon numarası. Yani ilk operasyondan sonraki her operasyonda
`M05` yazılıyordu. Ardından `[RT] S[S]` (M03 + devir) spindle'ı tekrar
başlatıyordu → durdur/başlat döngüsü.

## Çözüm
Spindle zaten program başında **M405** (`$12` bölümü) ile bir kez çalışıyor
ve sürekli açık kalıyor. Bu yüzden `$80` içindeki `$IF OPN > 1 / M05 / $ENDIF`
bloğu tamamen kaldırıldı. Artık spindle operasyonlar arasında durmuyor.

Programın **sonundaki** tek `M05` (`$15` bölümü, M30'dan önce) korundu —
bu program bittiğinde spindle'ı güvenli şekilde durdurur ve doğrudur.

## Dosya
`Alpha_Standard_3ax_Mill_Router_optimized.txt` — mevcut post dosyanızın
yerine kullanılabilecek tam sürüm. AlphaCAM'de kendi post kaynağınızın
dosya adı/uzantısıyla değiştirip APS ile yeniden derleyin (compile).

## Not
Takım değişiminde hâlâ `M03 S....` (spindle yönü + devir) satırı çıkıyor.
Bu spindle'ı durdurmaz, sadece devri teyit eder — zararsızdır. Eğer
makinanızda takım değişiminde de M03/S satırının hiç çıkmasını istemezseniz,
söyleyin, `$80` içindeki `[RT] S[S]` satırını da kaldırırım.
