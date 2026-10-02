#!/usr/bin/env bash
# Kareleri ve müziği MP4'e birleştirir.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
FF="$(python3 -c 'import imageio_ffmpeg;print(imageio_ffmpeg.get_ffmpeg_exe())')"
CRF="${1:-20}"
OUT="$HERE/DeliKadirApp-tanitim.mp4"

"$FF" -y -hide_banner -loglevel error \
  -framerate 30 -i "$HERE/frames/f%05d.jpg" \
  -i "$HERE/muzik.wav" \
  -c:v libx264 -preset slow -crf "$CRF" -pix_fmt yuv420p \
  -profile:v high -level 4.0 -g 60 \
  -c:a aac -b:a 160k -ar 44100 \
  -movflags +faststart -shortest "$OUT"

# kapak karesi (kapanıştaki logo)
"$FF" -y -hide_banner -loglevel error -i "$OUT" -ss 61 -frames:v 1 -q:v 3 "$HERE/kapak.jpg"

ls -la "$OUT" "$HERE/kapak.jpg"
"$FF" -hide_banner -i "$OUT" 2>&1 | grep -E "Duration|Stream"
