const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
(async () => {
  const br = await chromium.launch();
  const m = await br.newContext({viewport: {width: 390, height: 844}, isMobile: true, hasTouch: true, deviceScaleFactor: 2});
  await m.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  for (const f of ['three.min.js', 'OrbitControls.js', 'RoomEnvironment.js']) await m.route('**/' + f, r => r.fulfill({body: fs.readFileSync(N + {"three.min.js": 'node_modules/three/build/three.min.js', "OrbitControls.js": 'node_modules/three/examples/js/controls/OrbitControls.js', "RoomEnvironment.js": 'node_modules/three/examples/js/environments/RoomEnvironment.js'}[f]), contentType: 'application/javascript'}));
  const t = await m.newPage(); const hat = []; t.on('pageerror', e => hat.push(e.message));
  const yanit = () => t.waitForResponse(r => /\/(islem|panel\/)/.test(r.url()) && r.request().method() === 'POST');
  await t.goto(B + '/');
  await t.goto(B + (await t.request.post(B + '/kabin/yeni', {form: {sablon: 'Boy Dolap'}, maxRedirects: 0})).headers()['location']);
  await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
  await t.waitForSelector('#sayfa.acik'); await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  await t.tap('#s2 rect.goz[data-goz="0"]'); await t.tap('#dockIc [data-k=kapak]');
  await t.waitForFunction(() => document.getElementById('onBilgi') && document.getElementById('onBilgi').textContent.includes('menteşe'), null, {timeout: 8000});
  ok(await t.isVisible('.kulp-kapak'), 'Kulp yeri seçici görünüyor');
  // tek kapak olsun
  const tek = await t.$('#sayfaIc [data-seg=n] button[data-d="1"]'); if (tek) { await tek.tap(); await t.waitForTimeout(400); }
  await t.tap('#sayfaIc [data-seg=kulpyon] button[data-d=DIKEY]'); await t.waitForTimeout(300);
  await t.tap('#sayfaIc [data-kulpyer=sagorta]'); await t.waitForTimeout(500);
  await t.screenshot({path: O + '80_kulp_secici.png'});
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(400);
  let k = await t.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'kapak').map(p => [p.menteseyeri, p.kulp, p.kulp_yon]));
  console.log(JSON.stringify(k));
  ok(k.length && k.every(x => x[2][0] === 'DIKEY'), 'Dikey kulp kaydedildi');
  await t.screenshot({path: O + '81_sade_2b.png'});
  ok(await t.evaluate(() => !S.v.hata_var), 'Doktor hatası yok');
  // düzenle: kulpsuz
  const kid = await t.evaluate(() => S.v.parcalar2b.find(p => p.tur === 'kapak').id);
  const kb = await t.$eval(`#s2 g.parca[data-id="${kid}"] rect`, r => { const b = r.getBoundingClientRect(); return {x: b.x + b.width / 2, y: b.y + b.height / 2}; });
  await t.touchscreen.tap(kb.x, kb.y); await t.waitForTimeout(300);
  const dz = await t.$('#dockIc [data-k=duzenle]'); 
  if (dz) { await dz.tap(); await t.waitForTimeout(500);
    ok(await t.evaluate(() => document.querySelector('#sayfaIc [name=kulpyon]').value === 'DIKEY'), 'Düzenlemede mevcut kulp geldi');
    await t.tap('#sayfaIc [data-seg=kulpyon] button[data-d=yok]'); await t.waitForTimeout(500);
    await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(400);
    ok(await t.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'kapak').every(p => !p.kulp.length)), 'Kulpsuz yapıldı');
  } else console.log('düzenle düğmesi yok:', await t.$$eval('#dockIc [data-k]', b => b.map(x => x.dataset.k)));
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
