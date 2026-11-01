// Canvas çizimleri: NC simülasyonu ve nesting levha görünümü.

// --- NC simülasyon: hareket yollarını çiz ---
function drawSim(canvasId, sim, W, H){
  const cv = document.getElementById(canvasId);
  if (!cv || !sim) return;
  const ctx = cv.getContext('2d');
  ctx.clearRect(0, 0, cv.width, cv.height);

  const pad = 20;
  const sx = (cv.width - 2*pad) / (W || 1);
  const sy = (cv.height - 2*pad) / (H || 1);
  const s = Math.min(sx, sy);
  // CNC Y yukarı (+) -> canvas Y aşağı: ters çevir
  const tx = x => pad + x * s;
  const ty = y => cv.height - pad - y * s;

  // parça sınırı
  ctx.strokeStyle = '#cbd5e1'; ctx.lineWidth = 1;
  ctx.strokeRect(tx(0), ty(H), W*s, H*s);

  (sim.paths || []).forEach(p => {
    ctx.beginPath();
    if (p.type === 'rapid'){
      ctx.strokeStyle = '#94a3b8'; ctx.setLineDash([4,3]); ctx.lineWidth = 1;
    } else if (p.type === 'arc'){
      ctx.strokeStyle = '#ef4444'; ctx.setLineDash([]); ctx.lineWidth = 1.5;
    } else {
      ctx.strokeStyle = '#2563eb'; ctx.setLineDash([]); ctx.lineWidth = 1.5;
    }
    ctx.moveTo(tx(p.x1), ty(p.y1));
    ctx.lineTo(tx(p.x2), ty(p.y2));
    ctx.stroke();
  });
  ctx.setLineDash([]);
}

// --- Nesting: her levha için ayrı canvas oluştur ---
function drawSheets(wrapId, placements, sheetW, sheetH, sheetCount){
  const wrap = document.getElementById(wrapId);
  wrap.innerHTML = '';
  const maxW = 520;
  const scale = maxW / sheetW;

  for (let s = 0; s < sheetCount; s++){
    const title = document.createElement('div');
    title.className = 'text-sm font-medium text-slate-500';
    title.textContent = `Levha ${s+1}`;
    wrap.appendChild(title);

    const cv = document.createElement('canvas');
    cv.width = sheetW * scale;
    cv.height = sheetH * scale;
    cv.className = 'border rounded bg-white';
    wrap.appendChild(cv);
    const ctx = cv.getContext('2d');

    ctx.strokeStyle = '#94a3b8';
    ctx.strokeRect(0, 0, cv.width, cv.height);

    placements.filter(p => p.sheet === s).forEach(p => {
      const x = p.x * scale, y = p.y * scale, w = p.w * scale, h = p.h * scale;
      ctx.fillStyle = p.color + 'cc';
      ctx.fillRect(x, y, w, h);
      ctx.strokeStyle = '#1e293b'; ctx.lineWidth = 1;
      ctx.strokeRect(x, y, w, h);
      // etiket
      ctx.fillStyle = '#0f172a';
      ctx.font = '10px sans-serif';
      const label = (p.label || '') + (p.rotated ? ' ↻' : '');
      ctx.save();
      ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip();
      ctx.fillText(label, x + 3, y + 12);
      ctx.fillText(`${Math.round(p.w)}×${Math.round(p.h)}`, x + 3, y + 24);
      ctx.restore();
    });
  }
}
