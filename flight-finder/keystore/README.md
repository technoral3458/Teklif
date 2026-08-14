# İmzalama anahtarı

`ucusbul.jks` bu uygulamanın **kişisel kullanım (sideload)** imzalama anahtarıdır.
Parolası gizli değildir ve bilerek depoda tutulur: `ucusbul`

Amaç, her CI derlemesinin **aynı imzayla** çıkmasıdır. Böylece yeni sürüm çıktığında
telefonunuzdaki uygulamanın üzerine sorunsuz kurulur; her seferinde "önce kaldır"
gerekmez.

Bu anahtar Google Play'e yükleme için **kullanılmamalıdır**. Play Store'a çıkmak
isterseniz kendi gizli anahtarınızı üretip GitHub Actions'a şu ortam
değişkenleriyle verin (varsa depodaki anahtarın yerine o kullanılır):

| Değişken              | Anlamı                       |
|-----------------------|------------------------------|
| `FF_KEYSTORE_FILE`    | keystore dosyasının yolu     |
| `FF_KEYSTORE_PASSWORD`| keystore parolası            |
| `FF_KEY_ALIAS`        | anahtar takma adı            |
| `FF_KEY_PASSWORD`     | anahtar parolası             |

Yeni anahtar üretmek için:

```bash
keytool -genkeypair -v -keystore benim.jks -alias benim -keyalg RSA \
  -keysize 2048 -validity 10950
```
