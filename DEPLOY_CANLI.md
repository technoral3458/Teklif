# Canlı Sunucuya Kurulum (kapak.ersanmakina.net)

Bu rehber, yeni **Delme Panelleri (DWD)** özelliğini canlı siteye almak içindir.
Komutlar **sunucunun kendisinde** çalıştırılır (bu repoyu geliştiren ortamdan
uzaktan kurulamaz).

> Sunucuyu başka biri kurduysa bu dosyayı ona iletin; ihtiyacı olan tek bilgi:
> **dal adı `claude/hexagonal-drill-glass-software-0a4fo5`**.

## Değişen ne var? (kurulum kontrol listesi)

- [x] **Backend**: yeni `DrillPanel` tablosu → `migrate` **zorunlu**
- [x] **Frontend**: yeni "Delme Panelleri" ekranı → `npm run build` **gerekli**
- [x] **Yeni Python/Node paketi yok** (yine de install zararsız)

---

## A) VPS — nginx + gunicorn + systemd (en yaygın)

SSH ile sunucuya bağlanın ve proje klasörüne girin (yol sizde farklı olabilir):

```bash
cd /var/www/Teklif            # <-- projenin sunucudaki gerçek yolu

# 1) Güncel kodu al
git fetch origin
git checkout claude/hexagonal-drill-glass-software-0a4fo5
git pull origin claude/hexagonal-drill-glass-software-0a4fo5

# 2) Backend
cd backend
source venv/bin/activate       # sanal ortam yolu sizde farklı olabilir
pip install -r requirements.txt
python manage.py migrate                  # ← yeni tablo
python manage.py collectstatic --noinput  # statik dosya sunuluyorsa

# 3) Frontend (derlenmiş dosyalar nginx tarafından sunulur)
cd ../frontend
npm install
npm run build                  # çıktı: frontend/dist/

# 4) Servisleri yeniden başlat (servis adları sizde farklı olabilir)
sudo systemctl restart gunicorn
sudo systemctl reload nginx
```

**Servis adını bilmiyorsanız** şunlarla bulun:
```bash
systemctl list-units --type=service | grep -Ei 'gunicorn|uwsgi|teklif|django'
```

**Frontend nereye build edilmeli?** nginx hangi klasörü sunuyorsa oraya. Mevcut
ayarı görmek için:
```bash
grep -R "root " /etc/nginx/sites-enabled/
```
`root` satırındaki klasör `frontend/dist` ile eşleşmeli (veya dist içeriğini
oraya kopyalayın).

---

## B) cPanel / Plesk (Setup Python App)

1. **Dosyaları yükle**: Git varsa Terminal’den `git pull`; yoksa repo ZIP’ini
   File Manager ile yükleyip değiştirin.
2. **Python App** panelinde:
   - “Run Pip Install” → `requirements.txt`
   - Terminal/“Execute” alanından:
     ```
     python manage.py migrate
     python manage.py collectstatic --noinput
     ```
   - **Restart** butonuna basın.
3. **Frontend build**: cPanel’de Node yoksa, `frontend`’i kendi bilgisayarınızda
   `npm run build` ile derleyip oluşan `frontend/dist/` içeriğini sitenin
   public klasörüne (genelde `public_html/`) yükleyin.

---

## C) Sadece FTP / panel erişimi varsa

Komut çalıştıramıyorsanız `migrate` yapılamaz; bu özellik veritabanı tablosu
gerektirdiği için **mutlaka** birinin sunucuda şu komutu çalıştırması lazım:
```
python manage.py migrate
```
Bu yüzden bu durumda sunucuyu yöneten kişiden destek alın (bu dosyayı iletin).

---

## Kurulum sonrası kontrol

1. Siteye CNC/Yönetici kullanıcısıyla girin.
2. Sol menüde **“Delme Panelleri”** görünmeli.
3. **DWD Dosyası Yükle** ile bir `.xml` yükleyip önizlemeyi görün.
4. **DWD (.xml) İndir** ile dosyanın geri alındığını doğrulayın.

## Sorun olursa

- **Menüde görünmüyor**: frontend build edilmemiş ya da nginx eski `dist`’i
  sunuyor → `npm run build` + doğru klasör + tarayıcı önbelleğini temizleyin.
- **Yüklerken 500 hatası**: `migrate` çalışmamış → backend loglarında
  “no such table: cnc_drillpanel” görürsünüz; `migrate` çalıştırın.
- **Login sonrası boş sayfa**: backend ulaşılamıyor → gunicorn/servis durumunu
  ve nginx `/api` yönlendirmesini kontrol edin.
