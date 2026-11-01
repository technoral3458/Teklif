// Raf / bölme konumu: dokun → Konum → alttaki boşluk mm (telefon)
const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
(async () => {
  const br = await chromium.launch();
  const m = await br.newContext({viewport: {width: 390, height: 844}, isMobile: true, hasTouch: true, deviceScaleFactor: 2});
  await m.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  await m.route('**/three.min.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/build/three.min.js'), contentType: 'application/javascript'}));
  await m.route('**/OrbitControls.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/controls/OrbitControls.js'), contentType: 'application/javascript'}));
  await m.route('**/RoomEnvironment.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/environments/RoomEnvironment.js'), contentType: 'application/javascript'}));
  const t = await m.newPage(); const hat = []; t.on('pageerror', e => hat.push(e.message));
  const yanit = () => t.waitForResponse(r => /\/(islem|panel\/)/.test(r.url()) && r.request().method() === 'POST');
  await t.goto(B + '/');
  await t.goto(B + (await t.request.post(B + '/kabin/yeni', {form: {sablon: 'Boy Dolap'}, maxRedirects: 0})).headers()['location']);
  await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
  await t.waitForSelector('#sayfa.acik'); await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  // dikey bölme + sol tarafa raf
  await t.tap('#s2 rect.goz[data-goz="0"]'); await t.tap('#dockIc [data-k=bolme]'); await t.waitForTimeout(250);
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  await t.tap('#s2 rect.goz[data-goz="0"]'); await t.tap('#dockIc [data-k=raf]'); await t.waitForTimeout(250);
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  // rafa (ince) yakın dokun: rafın 4 px üstüne
  const raf = await t.evaluate(() => S.v.parcalar2b.find(p => p.tur === 'raf').id);
  const kutu = await t.$eval(`#s2 g.parca[data-id="${raf}"] rect`, r => { const b = r.getBoundingClientRect(); return {x: b.x + b.width / 2, y: b.y - 5, h: b.height}; });
  console.log('raf ekranda', kutu.h.toFixed(1), 'px kalın');
  await t.touchscreen.tap(kutu.x, kutu.y); await t.waitForTimeout(200);
  ok(await t.evaluate(r => S.parca === r, raf), 'Rafa yakın dokununca raf seçildi');
  ok(await t.isVisible('#dockIc [data-k=konum]'), 'Konum düğmesi çıktı');
  await t.tap('#dockIc [data-k=konum]'); await t.waitForTimeout(400);
  await t.screenshot({path: O + '60_konum_raf.png'});
  await t.fill('#sayfaIc [name=a]', '600'); await yanit(); await t.waitForTimeout(300);
  const ust = await t.inputValue('#sayfaIc [name=b]');
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  const alt = await t.evaluate(r => { const p = S.v.parcalar2b.find(x => x.id === r); const z = S.v.gozler.find(z => Math.abs(z.y1 - p.kutu.y2) < 1.5 && z.x1 < p.kutu.x2 && z.x2 > p.kutu.x1); return z.y2 - z.y1; }, raf);
  ok(Math.abs(alt - 600) < 0.6, 'Rafın altındaki boşluk 600 mm oldu (üstü ' + ust + ' mm)');
  // +10 düğmesi
  await t.tap('#dockIc [data-k=konum]'); await t.waitForTimeout(300);
  await t.tap('#sayfaIc [data-adim=a] [data-a="1"]'); await yanit(); await t.waitForTimeout(200);
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  ok(await t.evaluate(r => { const p = S.v.parcalar2b.find(x => x.id === r); const z = S.v.gozler.find(z => Math.abs(z.y1 - p.kutu.y2) < 1.5 && z.x1 < p.kutu.x2 && z.x2 > p.kutu.x1); return Math.abs(z.y2 - z.y1 - 610) < 0.6; }, raf), '+ ile 610 mm');
  // dikey bölme: solundaki boşluk 300
  const bol = await t.evaluate(() => S.v.parcalar2b.find(p => p.tur === 'dikeybolme').id);
  await t.tap('#dockIc [data-k=bırak]');
  const kb = await t.$eval(`#s2 g.parca[data-id="${bol}"] rect`, r => { const b = r.getBoundingClientRect(); return {x: b.x - 4, y: b.y + b.height * 0.8}; });
  await t.touchscreen.tap(kb.x, kb.y); await t.waitForTimeout(200);
  ok(await t.evaluate(r => S.parca === r, bol), 'Dikey bölme seçildi');
  await t.tap('#dockIc [data-k=konum]'); await t.waitForTimeout(300);
  ok((await t.textContent('#sayfaIc')).includes('Solundaki'), 'Solundaki / sağındaki boşluk soruluyor');
  await t.fill('#sayfaIc [name=a]', '300'); await yanit(); await t.waitForTimeout(300);
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  ok(await t.evaluate(r => { const p = S.v.parcalar2b.find(x => x.id === r); return S.v.gozler.some(z => Math.abs(z.x2 - p.kutu.x1) < 1.5 && Math.abs(z.x2 - z.x1 - 300) < 0.6); }, bol), 'Bölmenin solu 300 mm');
  await t.screenshot({path: O + '61_konum_sonra.png'});
  // vazgeç geri alır
  await t.tap('#dockIc [data-k=konum]'); await t.waitForTimeout(300);
  await t.fill('#sayfaIc [name=a]', '200'); await yanit(); await t.waitForTimeout(300);
  await Promise.all([t.waitForResponse(r => r.url().includes('/panel/iptal')), t.tap('#vazgec')]); await t.waitForTimeout(300);
  ok(await t.evaluate(r => { const p = S.v.parcalar2b.find(x => x.id === r); return S.v.gozler.some(z => Math.abs(z.x2 - p.kutu.x1) < 1.5 && Math.abs(z.x2 - z.x1 - 300) < 0.6); }, bol), 'Vazgeç: 300 mm olarak kaldı');
  ok(await t.evaluate(() => !S.v.hata_var), 'Doktor hatası yok');
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
