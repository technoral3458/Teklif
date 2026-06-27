#!/usr/bin/env bash
#
# Membran Kapak Yönetim Sistemi (FastAPI) - temiz, sıfırdan sunucu kurulumu.
# kapak.ersanmakina.net'i bu uygulamaya bağlar.
#
# Boş/temiz bir Ubuntu sunucuda root olarak:
#   curl -fsSL https://raw.githubusercontent.com/technoral3458/Teklif/claude/hexagonal-drill-glass-software-0a4fo5/deploy/setup_membran.sh -o setup_membran.sh
#   bash setup_membran.sh
#
set -euo pipefail

DOMAIN="kapak.ersanmakina.net"
REPO_URL="https://github.com/technoral3458/Teklif.git"
BRANCH="claude/hexagonal-drill-glass-software-0a4fo5"
APP_DIR="/opt/membran/Teklif"
APP_SUB="$APP_DIR/membran-app"
PORT=8100

echo ">>> [1/8] Önceki kurulumların izlerini temizle..."
systemctl disable --now teklif membran nginx 2>/dev/null || true
rm -f /etc/systemd/system/teklif.service /etc/systemd/system/membran.service
rm -f /etc/nginx/sites-enabled/* /etc/nginx/sites-available/teklif \
      /etc/nginx/sites-available/membran 2>/dev/null || true
systemctl daemon-reload

echo ">>> [2/8] Sistem paketleri..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y python3 python3-venv python3-pip git nginx curl ca-certificates

echo ">>> [3/8] Depoyu indir ($BRANCH)..."
rm -rf /opt/membran
mkdir -p /opt/membran
git config --global --add safe.directory "$APP_DIR" 2>/dev/null || true
git clone --branch "$BRANCH" --single-branch "$REPO_URL" "$APP_DIR"

echo ">>> [4/8] Python sanal ortam + paketler..."
cd "$APP_SUB"
python3 -m venv venv
./venv/bin/pip install --upgrade pip
./venv/bin/pip install -r requirements.txt

echo ">>> [5/8] .env (ANTHROPIC anahtarını sonra ekleyebilirsin)..."
if [ ! -f .env ]; then
    SECRET=$(./venv/bin/python -c "import secrets; print(secrets.token_urlsafe(40))")
    cat > .env <<EOF
ANTHROPIC_API_KEY=
SECRET_KEY=$SECRET
ANTHROPIC_MODEL=claude-sonnet-4-6
EOF
fi

echo ">>> [6/8] uvicorn systemd servisi (port $PORT)..."
cat > /etc/systemd/system/membran.service <<EOF
[Unit]
Description=Membran Kapak Yonetim Sistemi (FastAPI)
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
systemctl enable --now membran
systemctl restart membran

echo ">>> [7/8] nginx (tüm domaini uygulamaya yönlendir)..."
cat > /etc/nginx/sites-available/membran <<EOF
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name $DOMAIN;
    client_max_body_size 25M;
    location / {
        proxy_pass http://127.0.0.1:$PORT;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF
ln -sf /etc/nginx/sites-available/membran /etc/nginx/sites-enabled/membran
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable nginx
systemctl restart nginx

echo ">>> [8/8] Test..."
sleep 2
echo -n "membran servisi: "; systemctl is-active membran
curl -s -o /dev/null -w "uygulama (8100): %{http_code}\n" "http://127.0.0.1:$PORT/membrane"
curl -s -o /dev/null -w "site (80):       %{http_code}\n" "http://127.0.0.1/membrane"

echo ""
echo "============================================================"
echo "  KURULUM TAMAMLANDI"
echo "  Tarayıcı:  http://$DOMAIN/   (DNS bu sunucuya bakıyorsa)"
echo "  ya da IP ile:  http://SUNUCU_IP/"
echo ""
echo "  Tarama (foto) modülü için:"
echo "    nano $APP_SUB/.env   -> ANTHROPIC_API_KEY=sk-ant-...  (kaydet)"
echo "    systemctl restart membran"
echo ""
echo "  HTTPS için (DNS yönlendikten sonra):"
echo "    apt-get install -y certbot python3-certbot-nginx"
echo "    certbot --nginx -d $DOMAIN"
echo "============================================================"
