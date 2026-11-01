#!/usr/bin/env bash
#
# DENEME ortamı: deneme.kapak.ersanmakina.net  (canlı siteye dokunmaz)
#  - ayrı klasör (/opt/membran-deneme), ayrı veritabanı, ayrı port (8101)
#  - ayrı acil lisans anahtarı (/etc/membran-deneme/acil_lisans)  ← programa GÖMÜLMEZ
#  - yerel GeoIP veritabanı (DB-IP Lite, ücretsiz; IP dışarı gönderilmez)
#
# Önce alan adına DNS kaydı: deneme.kapak  A  167.233.104.21
# Sonra sunucuda root olarak:
#   bash /opt/membran/Teklif/deploy/deneme_kur.sh
# Güncellemek için aynı komutu yeniden çalıştırın (veritabanı, .env ve anahtar korunur).
#
set -euo pipefail

DOMAIN="deneme.kapak.ersanmakina.net"
REPO_URL="https://github.com/technoral3458/Teklif.git"
BRANCH="claude/hexagonal-drill-glass-software-0a4fo5"
APP_DIR="/opt/membran-deneme/Teklif"
APP_SUB="$APP_DIR/membran-app"
ANAHTAR_DIR="/etc/membran-deneme/acil_lisans"
PORT=8101

echo ">>> [1/6] Kod ($BRANCH)"
if [ -d "$APP_DIR/.git" ]; then
    git -C "$APP_DIR" pull --ff-only origin "$BRANCH"
else
    mkdir -p /opt/membran-deneme
    git clone --branch "$BRANCH" --single-branch "$REPO_URL" "$APP_DIR"
fi

echo ">>> [2/6] Python paketleri"
cd "$APP_SUB"
[ -d venv ] || python3 -m venv venv
./venv/bin/pip install -q --upgrade pip
./venv/bin/pip install -q -r requirements.txt

echo ">>> [3/6] .env ve anahtar klasörü"
install -d -m 700 "$ANAHTAR_DIR"
if [ ! -f .env ]; then
    SECRET=$(./venv/bin/python -c "import secrets; print(secrets.token_urlsafe(40))")
    SIFRE=$(./venv/bin/python -c "import secrets; print(secrets.token_urlsafe(12))")
    cat > .env <<EOF
SECRET_KEY=$SECRET
ADMIN_USER=admin
ADMIN_PASSWORD=$SIFRE
ACIL_ANAHTAR_DIR=$ANAHTAR_DIR
GEOIP_DB=/var/lib/GeoIP/dbip-city-lite.mmdb
# Firma e-postası (SMTP). 465 = SSL, 587 = STARTTLS
SMTP_HOST=
SMTP_PORT=465
SMTP_USER=
SMTP_PASS=
SMTP_FROM=
EOF
    chmod 600 .env
    echo "    Deneme yönetici girişi: admin / $SIFRE   (.env içinde de yazılı)"
fi

echo ">>> [4/6] Yerel GeoIP veritabanı (DB-IP Lite, aylık)"
install -d /var/lib/GeoIP
cat > /usr/local/bin/geoip-guncelle <<'EOF'
#!/usr/bin/env bash
set -e
cd /var/lib/GeoIP
for AY in "$(date +%Y-%m)" "$(date -d '-1 month' +%Y-%m)"; do
  if curl -fsSL "https://download.db-ip.com/free/dbip-city-lite-$AY.mmdb.gz" -o dbip.mmdb.gz; then
    gunzip -f dbip.mmdb.gz && mv -f dbip.mmdb dbip-city-lite.mmdb && exit 0
  fi
done
echo "GeoIP indirilemedi (konum boş kalır, lisans verilmeye devam eder)" >&2
EOF
chmod +x /usr/local/bin/geoip-guncelle
/usr/local/bin/geoip-guncelle || true
echo "17 4 3 * * root /usr/local/bin/geoip-guncelle && systemctl restart membran-deneme" > /etc/cron.d/geoip-guncelle

echo ">>> [5/6] Servis (port $PORT)"
cat > /etc/systemd/system/membran-deneme.service <<EOF
[Unit]
Description=Membran DENEME (acil lisans denemesi)
After=network.target

[Service]
WorkingDirectory=$APP_SUB
ExecStart=$APP_SUB/venv/bin/uvicorn main:app --host 127.0.0.1 --port $PORT --workers 2
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now membran-deneme
systemctl restart membran-deneme

echo ">>> [6/6] nginx + HTTPS"
if [ ! -f /etc/nginx/sites-available/membran-deneme ]; then
cat > /etc/nginx/sites-available/membran-deneme <<EOF
server {
    listen 80;
    server_name $DOMAIN;
    client_max_body_size 5m;
    # Acil lisans uçları HTTP'de yönlendirilmez: uygulama doğrudan 403 {"error":"https"} döner
    location /api/titanium/v1/license/ {
        proxy_pass http://127.0.0.1:$PORT;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    location / {
        if (\$scheme = "http") { return 301 https://\$host\$request_uri; }
        proxy_pass http://127.0.0.1:$PORT;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF
ln -sf /etc/nginx/sites-available/membran-deneme /etc/nginx/sites-enabled/membran-deneme
fi
nginx -t && systemctl reload nginx
if command -v certbot >/dev/null && getent hosts "$DOMAIN" >/dev/null; then
    certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos --no-redirect -m "admin@$DOMAIN" || \
        echo "    certbot başarısız: 'certbot --nginx --no-redirect -d $DOMAIN' komutunu elle çalıştırın"
else
    echo "    DNS kaydı henüz görünmüyor. Kayıt yayılınca: certbot --nginx --no-redirect -d $DOMAIN"
fi

sleep 2
curl -fsS -o /dev/null -w "    Site: %{http_code}\n" "http://127.0.0.1:$PORT/login" || true
echo ">>> Bitti: https://$DOMAIN/membrane/acil-lisans"
