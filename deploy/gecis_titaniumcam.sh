#!/usr/bin/env bash
#
# Bir kerelik geçiş: membran → titaniumcam (veritabanı, .env, sertifika korunur)
#   /opt/membran/Teklif/membran-app   → /opt/titaniumcam/Teklif/titaniumcam
#   servisler membran, membran-kabin   → titaniumcam, titaniumcam-kabin
#   (varsa) deneme ortamı              → /opt/titaniumcam-deneme, titaniumcam-deneme
#   /etc/membran*                      → /etc/titaniumcam*
#
# Sunucuda root olarak:
#   curl -fsSL https://raw.githubusercontent.com/technoral3458/Teklif/claude/hexagonal-drill-glass-software-0a4fo5/deploy/gecis_titaniumcam.sh -o /root/gecis.sh
#   bash /root/gecis.sh
#
set -euo pipefail
BRANCH="claude/hexagonal-drill-glass-software-0a4fo5"
U=/etc/systemd/system

tasi() {   # $1 eski kök  $2 yeni kök  $3 eski servis  $4 yeni servis  $5 eski etc  $6 yeni etc
    local ESKI="$1/Teklif" YENI="$2/Teklif" ES="$3" YS="$4"
    if [ -d "$YENI/.git" ]; then echo "    $YENI zaten var, kod adımı atlandı"; else
        [ -d "$ESKI/.git" ] || { echo "    $ESKI yok, atlandı"; return 0; }
        echo "    kod güncelleniyor ve taşınıyor: $ESKI → $YENI"
        git config --global --add safe.directory "$ESKI" 2>/dev/null || true
        git -C "$ESKI" pull --ff-only origin "$BRANCH"
        mkdir -p "$2"; mv "$ESKI" "$YENI"; rmdir "$1" 2>/dev/null || true
        git config --global --add safe.directory "$YENI" 2>/dev/null || true
    fi
    cd "$YENI"
    for x in .env data; do                      # git dışı dosyalar: ayarlar ve veritabanı
        if [ -e "membran-app/$x" ] && [ ! -e "titaniumcam/$x" ]; then mv "membran-app/$x" "titaniumcam/$x"; fi
    done
    rm -rf membran-app                           # eski venv ve önbellek
    if [ -d "$5" ] && [ ! -e "$6" ]; then mv "$5" "$6"; fi       # acil lisans anahtarı (varsa)
    [ -f titaniumcam/.env ] && sed -i "s#$5#$6#g" titaniumcam/.env
    echo "    Python ortamı (birkaç dakika)"
    cd titaniumcam
    [ -d venv ] || python3 -m venv venv
    ./venv/bin/pip install -q --upgrade pip
    ./venv/bin/pip install -q -r requirements.txt
    echo "    servis: $ES → $YS"
    for s in "$ES:$YS" "$ES-kabin:$YS-kabin"; do
        e="${s%%:*}"; y="${s##*:}"
        if [ -f "$U/$e.service" ] && [ ! -f "$U/$y.service" ]; then
            sed -e "s#$1/Teklif/membran-app#$YENI/titaniumcam#g" -e "s#Membran Kapak Yonetim Sistemi#TitaniumCam#g" \
                -e "s#Membran DENEME#TitaniumCam DENEME#g" "$U/$e.service" > "$U/$y.service"
        fi
        if [ -f "$U/$e.service" ]; then systemctl disable --now "$e" 2>/dev/null || true; rm -f "$U/$e.service"; fi
    done
    systemctl daemon-reload
    for y in "$YS" "$YS-kabin"; do [ -f "$U/$y.service" ] && systemctl enable --now "$y" && systemctl restart "$y"; done
    for e in "${ES##*/}"; do                     # nginx site dosyasının adı
        if [ -f "/etc/nginx/sites-available/$e" ] && [ ! -f "/etc/nginx/sites-available/$YS" ]; then
            mv "/etc/nginx/sites-available/$e" "/etc/nginx/sites-available/$YS"
            rm -f "/etc/nginx/sites-enabled/$e"
            ln -sf "/etc/nginx/sites-available/$YS" "/etc/nginx/sites-enabled/$YS"
        fi
    done
}

echo ">>> [1/3] Canlı site"
systemctl stop membran membran-kabin 2>/dev/null || true
tasi /opt/membran /opt/titaniumcam membran titaniumcam /etc/membran /etc/titaniumcam

echo ">>> [2/3] Deneme ortamı (kuruluysa)"
if [ -d /opt/membran-deneme ] || [ -d /opt/titaniumcam-deneme ]; then
    systemctl stop membran-deneme 2>/dev/null || true
    tasi /opt/membran-deneme /opt/titaniumcam-deneme membran-deneme titaniumcam-deneme /etc/membran-deneme /etc/titaniumcam-deneme
    [ -f /etc/cron.d/geoip-guncelle ] && sed -i 's/membran-deneme/titaniumcam-deneme/g' /etc/cron.d/geoip-guncelle
else
    echo "    kurulu değil, atlandı"
fi

echo ">>> [3/3] nginx ve kontrol"
nginx -t && systemctl reload nginx
sleep 3
echo -n "    titaniumcam:       "; systemctl is-active titaniumcam || true
echo -n "    titaniumcam-kabin: "; systemctl is-active titaniumcam-kabin || true
curl -s -o /dev/null -w "    site (8100):  %{http_code}\n" http://127.0.0.1:8100/login || true
curl -s -o /dev/null -w "    motor (8210): %{http_code}\n" http://127.0.0.1:8210/bilgi || true
echo ">>> Bitti. Güncellemek için artık:"
echo "    cd /opt/titaniumcam/Teklif && git pull origin $BRANCH && systemctl restart titaniumcam titaniumcam-kabin"
