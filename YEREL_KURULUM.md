# Teklif — Yerel (Masaüstü) Kurulum

Bu sistem tek bir bilgisayarda, internete ihtiyaç duymadan (ilk kurulum hariç)
çalışacak şekilde ayarlanmıştır. İki parçadan oluşur:

- **Backend** (Django) — veritabanı ve iş mantığı, `http://localhost:8000`
- **Arayüz** (React) — ekran/menüler, `http://localhost:3000`

`baslat.bat` her ikisini birden açar ve tarayıcıyı otomatik yönlendirir.

---

## 1. Gereksinimler (her bilgisayara bir kez)

Aşağıdaki iki programı kurun:

| Program | İndirme | Not |
|---------|---------|-----|
| **Python 3.11+** | https://www.python.org/downloads/ | Kurulumda **“Add Python to PATH”** kutusunu mutlaka işaretleyin |
| **Node.js (LTS)** | https://nodejs.org/ | “LTS” yazan sürümü indirin |

> Bu ikisi kurulu değilse `kur.bat` sizi uyarır.

---

## 2. Projeyi bilgisayara indirme

İki yol var:

**A) Git ile (önerilen, güncellemeler kolay olur):**
```
git clone <depo-adresi>
cd Teklif
git checkout claude/hexagonal-drill-glass-software-0a4fo5
```

**B) ZIP olarak:** GitHub’dan “Code → Download ZIP” ile indirip bir klasöre açın.

---

## 3. İlk kurulum

Proje klasöründeki **`kur.bat`** dosyasına **çift tıklayın**.

Bu işlem otomatik olarak:
- Python sanal ortamını ve paketleri kurar,
- veritabanını oluşturur,
- demo kullanıcıları ekler,
- arayüz paketlerini indirir.

Birkaç dakika sürebilir (internet gerekir). **Sadece bir kez** yapılır.

---

## 4. Günlük kullanım

Her gün programı açmak için **`baslat.bat`** dosyasına **çift tıklayın**.

- İki siyah pencere açılır (Backend ve Arayüz) — **kapatmayın**, program bunlarla çalışır.
- Tarayıcıda otomatik olarak `http://localhost:3000` açılır.
- Programı kapatmak için bu iki pencereyi kapatın.

### Giriş bilgileri (demo)

| Rol | Kullanıcı | Şifre |
|-----|-----------|-------|
| Yönetici | `admin` | `admin123` |
| Satış | `satis` | `satis123` |
| CNC Operatörü | `cnc` | `cnc123` |
| Bayi | `bayi1` | `bayi123` |

> Gerçek kullanımda bu şifreleri değiştirin (Yönetici panelinden).

---

## 5. Yeni güncelleme geldiğinde

```
git pull
```
sonra tekrar `baslat.bat`. (Başlatıcı, her açılışta veritabanını otomatik
günceller — `migrate` çalıştırır. Yeni Python/Node paketi eklendiyse
`kur.bat`’ı tekrar çalıştırın.)

---

## Sık sorulanlar

**“python bulunamadı” / “node bulunamadı” hatası**
Gereksinimler kurulu değil veya PATH’e eklenmemiş. Python’u kaldırıp
“Add Python to PATH” işaretleyerek tekrar kurun.

**Port meşgul / sayfa açılmıyor**
8000 veya 3000 portunu başka bir program kullanıyor olabilir. Açık siyah
pencereleri kapatıp `baslat.bat`’ı tekrar çalıştırın.

**Verilerim nerede?**
`backend/db.sqlite3` dosyasında. Yedek almak için bu dosyayı kopyalamanız
yeterli.

**Mac veya Linux kullanıyorum**
`.bat` dosyaları yalnızca Windows içindir. Mac/Linux için bana söyleyin,
karşılığı olan başlatma betiklerini hazırlayayım.
