#!/usr/bin/env bash
#
# Teklif - Hetzner / Ubuntu sunucu otomatik kurulum betiği.
# Yeni (boş) bir Ubuntu sunucuda root olarak çalıştırın:
#
#   bash setup_server.sh
#
# DNS'i (A kaydı) sunucu IP'sine yönlendirdikten sonra SSL için:
#   certbot --nginx -d kapak.ersanmakina.net
#
set -euo pipefail

# ===================== AYARLAR =====================
DOMAIN="kapak.ersanmakina.net"
REPO_URL="https://github.com/technoral3458/Teklif.git"
BRANCH="claude/hexagonal-drill-glass-software-0a4fo5"
APP_DIR="/opt/teklif/Teklif"
# ==================================================

echo ">>> [1/7] Sistem paketleri kuruluyor..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y python3 python3-venv python3-pip git nginx curl ca-certificates

echo ">>> [2/7] Node.js 20 kuruluyor..."
if ! command -v node >/dev/null 2>&1; then
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
    apt-get install -y nodejs
fi

echo ">>> [3/7] Depo indiriliyor ($BRANCH)..."
mkdir -p /opt/teklif
if [ ! -d "$APP_DIR/.git" ]; then
    git clone "$REPO_URL" "$APP_DIR"
fi
# Klasör sahipliği farklıysa git'in "dubious ownership" hatasını önle
git config --global --add safe.directory "$APP_DIR"
cd "$APP_DIR"
git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"

echo ">>> [4/7] Backend (sanal ortam + paketler + .env)..."
cd "$APP_DIR/backend"
python3 -m venv venv
./venv/bin/pip install --upgrade pip
./venv/bin/pip install -r requirements.txt

if [ ! -f .env ]; then
    SECRET=$(./venv/bin/python -c "import secrets; print(secrets.token_urlsafe(50))")
    cat > .env <<EOF
DJANGO_SECRET_KEY=$SECRET
DJANGO_DEBUG=False
DJANGO_ALLOWED_HOSTS=$DOMAIN
DJANGO_CSRF_TRUSTED_ORIGINS=https://$DOMAIN
EOF
    echo "    .env oluşturuldu."
fi

set -a; source .env; set +a
./venv/bin/python manage.py migrate
./venv/bin/python manage.py collectstatic --noinput
# Demo kullanıcılar (admin/admin123 ...). İsteğe bağlı; ilk kurulumda faydalı.
./venv/bin/python seed_data.py || true

echo ">>> [5/7] Frontend derleniyor..."
cd "$APP_DIR/frontend"
npm install
npm run build

echo ">>> [6/7] Dosya izinleri (www-data)..."
chown -R www-data:www-data /opt/teklif

echo ">>> [7/7] systemd + nginx servisleri..."
cp "$APP_DIR/deploy/gunicorn.service" /etc/systemd/system/teklif.service
systemctl daemon-reload
systemctl enable --now teklif
systemctl restart teklif

sed "s/__DOMAIN__/$DOMAIN/g" "$APP_DIR/deploy/nginx.conf" > /etc/nginx/sites-available/teklif
ln -sf /etc/nginx/sites-available/teklif /etc/nginx/sites-enabled/teklif
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo ""
echo "============================================================"
echo "  KURULUM TAMAMLANDI"
echo "  Test (DNS henüz yönlenmediyse IP ile):  http://SUNUCU_IP/"
echo "  DNS A kaydını bu sunucuya yönlendirin:   $DOMAIN"
echo "  Sonra HTTPS için:"
echo "     apt-get install -y certbot python3-certbot-nginx"
echo "     certbot --nginx -d $DOMAIN"
echo ""
echo "  Giriş: admin / admin123  (sonra mutlaka değiştirin)"
echo "============================================================"
