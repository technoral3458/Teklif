#!/usr/bin/env bash
#
# Online Kabin — TitaniumCam motor servisini kurar / günceller.
#
# Motor kodu GİZLİDİR ve bu (herkese açık) repoda TUTULMAZ. Zip'i sunucuya
# kendin kopyalarsın, bu betik onu repo DIŞINDAKİ /opt/kabin_motor'a açar
# (setup_membran.sh /opt/membran'ı silse de motor kalır).
#
# Kullanım (sunucuda, root):
#   bash /opt/membran/Teklif/deploy/kabin_motor_kur.sh /root/aaa_2.zip
#
set -euo pipefail

ZIP="${1:-}"
MOTOR=/opt/kabin_motor
APP_SUB=/opt/membran/Teklif/membran-app
PORT=8210

[ -n "$ZIP" ] && [ -f "$ZIP" ] || { echo "Kullanım: bash $0 /yol/motor.zip"; exit 1; }
[ -x "$APP_SUB/venv/bin/uvicorn" ] || { echo "Önce siteyi kur (setup_membran.sh)"; exit 1; }
command -v unzip >/dev/null || apt-get install -y unzip

echo ">>> [1/4] Motor açılıyor: $MOTOR"
TMP=$(mktemp -d)
unzip -q "$ZIP" -d "$TMP"
KOK=$(dirname "$(find "$TMP" -type d -name cabinet_v2 | head -1)")
[ -d "$KOK/cabinet_v2/interactive" ] || { echo "Zip içinde cabinet_v2 bulunamadı"; exit 1; }
rm -rf "$MOTOR"; mkdir -p "$MOTOR"
cp -r "$KOK/cabinet_v2" "$MOTOR/"
[ -f "$KOK/i18n.py" ] && cp "$KOK/i18n.py" "$MOTOR/"
[ -f "$KOK/kabin_tanimlar.json" ] && cp "$KOK/kabin_tanimlar.json" "$MOTOR/"
# Web'de kullanılmayanlar: G-kod/CIX/DXF (üretim TitaniumCam'de kalır) ve Qt ekran dosyaları
rm -rf "$MOTOR/cabinet_v2/export" "$MOTOR/cabinet_v2/interactive/parca_cizim.py" \
       "$MOTOR/cabinet_v2/preview/"{widget,painter,render,secici}.py "$MOTOR/cabinet_v2/reports/label.py"
find "$MOTOR" -name __pycache__ -prune -exec rm -rf {} +
chmod -R go-rwx "$MOTOR"
rm -rf "$TMP"

echo ">>> [2/4] Motor servisi (yalnız 127.0.0.1:$PORT)"
TANIM=""
[ -f "$MOTOR/kabin_tanimlar.json" ] && TANIM="Environment=KABIN_TANIM_DIR=$MOTOR"
cat > /etc/systemd/system/membran-kabin.service <<EOF
[Unit]
Description=Online Kabin - TitaniumCam motor servisi (ic ag)
After=network.target

[Service]
WorkingDirectory=$APP_SUB
Environment=KABIN_MOTOR_DIR=$MOTOR
$TANIM
ExecStart=$APP_SUB/venv/bin/uvicorn kabin_servis.servis:app --host 127.0.0.1 --port $PORT --workers 1
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now membran-kabin
systemctl restart membran-kabin
systemctl restart membran

echo ">>> [3/4] Kontrol"
for i in $(seq 1 15); do curl -sf "http://127.0.0.1:$PORT/bilgi" >/dev/null && break; sleep 1; done
curl -s "http://127.0.0.1:$PORT/bilgi" | "$APP_SUB/venv/bin/python" -c \
  "import json,sys; d=json.load(sys.stdin); print('motor', d['motor_surumu'], '· tanım', d['tanim_surumu'], '· şablonlar:', ', '.join(s['ad'] for s in d['kabin_sablonlari']))"

echo ">>> [4/4] Altın örnek (800×860×560, 6 adım)"
"$APP_SUB/venv/bin/python" - <<'PY'
import json, urllib.request
def op(k, i):
    r = urllib.request.Request("http://127.0.0.1:8210/islem", method="POST", headers={"Content-Type": "application/json"},
        data=json.dumps({"kabin": k, "islem": i, "rol": "yonetici", "izinler": ["ekle." + t for t in
            ("dikeybolme", "raf", "kapak", "cekmece", "arkalik")]}).encode())
    return json.load(urllib.request.urlopen(r))
v = op(None, {"tip": "yeni", "sablon": "Alt Dolap", "en": 800, "boy": 860, "derinlik": 560})
for i in ({"tip": "ekle", "tur": "dikeybolme", "noktalar": [[400, 300]], "sabit_gozler": "400"},
          {"tip": "ekle", "tur": "raf", "noktalar": [[200, 300]]},
          {"tip": "ekle", "tur": "kapak", "noktalar": [[200, 100], [200, 500]], "menteseler": ["SOL"], "kulp": {"sablon": "Ust orta 128"}},
          {"tip": "ekle", "tur": "cekmece", "noktalar": [[600, 300]], "adet": 3, "sablon": "Mandalli 500"},
          {"tip": "ekle", "tur": "arkalik", "noktalar": [[200, 100], [200, 500], [600, 300]], "sablon": "Alt Dolap Cakma"}):
    v = op(v["kabin"], i)
h = [b["mesaj"] for b in v["doktor"] if b["seviye"] == "hata"]
print("sonuç:", v["ozet"], "· doktor hata:", len(h), *h, sep=" ")
print("(beklenen varsayılan tanımlarla: 25 parça, 3.895 m², 9.36 m, 0 hata)")
PY
echo ""
echo "Kurulum bitti. Müşteri ekranı: https://kapak.ersanmakina.net/kabin · Yönetici: /membrane/kabin"
