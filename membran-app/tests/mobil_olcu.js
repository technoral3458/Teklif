// Telefon: 3B görünümde 📐 Ölçü paneli. Çalıştırma: node tests/mobil_olcu.js <klasör> (bkz. ekran_test.js)
const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
(async () => {
  const br = await chromium.launch();
  const m = await br.newContext({viewport: {width: 412, height: 860}, isMobile: true, hasTouch: true});
  await m.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  await m.route('**/three.min.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/build/three.min.js'), contentType: 'application/javascript'}));
  await m.route('**/OrbitControls.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/controls/OrbitControls.js'), contentType: 'application/javascript'}));
  const t = await m.newPage(); const hat = []; t.on('pageerror', e => hat.push(e.message));
  await t.goto(B + '/');
  await Promise.all([t.waitForNavigation(), t.tap('button[value="Boy Dolap"]')]);
  await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
  await t.tap('#ust [data-gor="3b"] >> visible=true'); await t.waitForTimeout(500);
  await t.tap('#alt [data-k=ozellik]'); await t.waitForSelector('#panelAlan [name=en]');
  ok(await t.isVisible('#panelAlan [name=en]'), '3B görünümde 📐 Ölçü ile ölçü paneli açıldı');
  await t.waitForTimeout(500); await t.screenshot({path: O + '14_mobil_olcu.png'});
  await t.fill('#panelAlan [name=en]', '900');
  await t.waitForResponse(r => r.url().includes('/islem')); await t.waitForTimeout(300);
  await t.tap('[data-kapat=sag]'); await t.waitForTimeout(700);
  ok(await t.evaluate(() => !S.panel && S.v.durum.geri), '✕ ile kapatınca kaydedildi (geri alınabilir)');
  ok(await t.evaluate(() => S.v.govde.en === 900), 'Genişlik 900 oldu');
  await t.screenshot({path: O + '15_mobil_3b_900.png'});
  // 3B'de eleman eklemeye çalışınca 2B'ye geçsin
  await t.tap('[data-ac=sol]'); await t.waitForTimeout(300);
  await t.tap('[data-ekle=raf]'); await t.waitForTimeout(400);
  ok(!(await t.evaluate(() => document.getElementById('orta').classList.contains('tek3b'))), 'Göz gerekince 2B görünüme geçti');
  ok(await t.evaluate(() => document.documentElement.scrollWidth <= 412), 'Yatay taşma yok');
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
