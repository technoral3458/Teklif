// Online Kabin tarayıcı testi. Çalıştırma: node tests/ekran_ek.js <klasör>
//   <klasör>/npm: three@0.128.0 + tw.js (CDN'e erişilemeyen ortam için yerel kopya), <klasör>/ekran: çıktı
//   Site 127.0.0.1:8100'de, motor servisi 127.0.0.1:8210'da çalışıyor olmalı.
const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
(async () => {
  const br = await chromium.launch(); const ctx = await br.newContext({viewport: {width: 1440, height: 900}});
  await ctx.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  await ctx.route('**/three.min.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/build/three.min.js'), contentType: 'application/javascript'}));
  await ctx.route('**/OrbitControls.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/controls/OrbitControls.js'), contentType: 'application/javascript'}));
  const p = await ctx.newPage(); const hat = []; p.on('pageerror', e => hat.push(e.message));
  const ep = 'ek' + Date.now() + '@test.com';
  await p.goto(B + '/magaza/kayit'); await p.fill('[name=full_name]', 'Ek'); await p.fill('[name=username]', ep); await p.fill('[name=password]', '1234');
  await Promise.all([p.waitForNavigation(), p.click('button:has-text("Hesabı Oluştur")')]);
  await p.goto(B + '/kabin/yeni'); await Promise.all([p.waitForNavigation(), p.click('button[value="Boy Dolap"]')]);
  await p.waitForFunction(() => typeof S !== 'undefined' && S.v);
  const istek = () => p.waitForResponse(r => r.url().includes('/islem'));
  // sağ tık → menü → raf ekle
  await p.click('svg#s2 rect.goz[data-goz="0"]', {button: 'right'});
  ok(await p.isVisible('#menu button:has-text("Raf ekle")'), 'Sağ tık menüsü açıldı');
  await p.click('#menu button:has-text("Raf ekle")'); await Promise.all([istek(), p.click('#dlgTamam')]);
  const n1 = await p.evaluate(() => S.v.parcalar2b.filter(x => x.tur === 'raf').length);
  // tekrarla: alttaki göze aynı raf
  const i = await p.evaluate(() => S.v.gozler.length - 1);
  await p.click(`svg#s2 rect.goz[data-goz="${i}"]`); await Promise.all([istek(), p.click('[data-k=tekrarla]')]);
  ok(await p.evaluate(n => S.v.parcalar2b.filter(x => x.tur === 'raf').length === n + 1, n1), 'Tekrarla: aynı raf başka göze eklendi');
  // 2 kapak ekle, sonra çift tıkla düzenle → 3 kapak
  const n = await p.evaluate(() => S.v.gozler.length);
  for (let j = 0; j < n; j++) await p.click(`svg#s2 rect.goz[data-goz="${j}"]`, j ? {modifiers: ['Control']} : {});
  await p.click('[data-ekle=kapak]'); await p.click('.kart[data-deger="2"]'); await p.waitForTimeout(500);
  await Promise.all([istek(), p.click('#dlgTamam')]);
  ok(await p.evaluate(() => S.v.parcalar2b.filter(x => x.tur === 'kapak').length === 2), '2 kapak eklendi');
  await p.check('#kapakSec');
  await p.dblclick('svg#s2 g.parca[data-tur=kapak] rect >> nth=0');
  await p.waitForSelector('#dlgSol'); await p.click('.kart[data-deger="3"]'); await p.waitForTimeout(500);
  await Promise.all([istek(), p.click('#dlgTamam')]);
  ok(await p.evaluate(() => S.v.parcalar2b.filter(x => x.tur === 'kapak').length === 3), 'Çift tık → kapak grubu 3 kapak oldu');
  // sil (Delete) + geri al
  await p.click('svg#s2 g.parca[data-tur=kapak] rect >> nth=0');
  await Promise.all([istek(), p.keyboard.press('Delete')]);
  ok(await p.evaluate(() => S.v.parcalar2b.filter(x => x.tur === 'kapak').length === 2), 'Delete: kapak silindi, kalanlar bölgeyi paylaştı');
  // ölçü etiketine tıkla → değer gir → kilit
  await p.uncheck('#kapakSec');
  await p.click('svg#s2 text.olcu[data-yon="y"] >> nth=0');
  await p.fill('#olcuKutu input', '600'); await Promise.all([istek(), p.press('#olcuKutu input', 'Enter')]);
  ok(await p.evaluate(() => S.v.gozler.some(z => z.kilit.y && Math.abs(z.y2 - z.y1 - 600) < 0.6)), 'Göz ölçüsü 600 girildi ve kilitlendi');
  await p.screenshot({path: S + '/ekran/13_boy_dolap.png'});
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
