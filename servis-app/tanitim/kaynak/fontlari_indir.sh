#!/usr/bin/env bash
# Videoda kullanılan Google Fonts dosyalarını indirir (OFL / Apache-2.0).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
mkdir -p "$HERE/fonts"
for q in "family=Inter:wght@400;500;600;700;800" \
         "family=Montserrat:wght@700;800;900" \
         "family=JetBrains+Mono:wght@500;700"; do
  curl -s -A "$UA" "https://fonts.googleapis.com/css2?$q&display=swap"
done > "$HERE/gf.css"
curl -s -A "$UA" "https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,1,0" -o "$HERE/ms.css"
curl -s "$(grep -oE 'https://[^)]+\.woff2' "$HERE/ms.css" | head -1)" -o "$HERE/fonts/MaterialSymbolsRounded.woff2"
python3 "$HERE/fontlari_yaz.py"
