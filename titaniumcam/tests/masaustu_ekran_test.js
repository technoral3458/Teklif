// Masaüstü (1440×900): sağ panel, ölçü, 2B ve 3B'de tıklayarak düzenleme, render
const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
(async () => {
  const br = await chromium.launch({args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']});
  const c = await br.newContext({viewport: {width: 1440, height: 900}, acceptDownloads: true});
  await c.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  for (const [p, f] of [['**/three.min.js', 'build/three.min.js'], ['**/OrbitControls.js', 'examples/js/controls/OrbitControls.js'], ['**/RoomEnvironment.js', 'examples/js/environments/RoomEnvironment.js']])
    await c.route(p, r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/' + f), contentType: 'application/javascript'}));
  const t = await c.newPage(); const hat = []; t.on('pageerror', e => hat.push(e.message));
  const yanit = () => t.waitForResponse(r => /\/islem$/.test(r.url()) && r.request().method() === 'POST');
  await t.goto(B + '/');
  await Promise.all([t.waitForNavigation(), t.click('button[value="Boy Dolap"]')]);
  await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
  await t.waitForTimeout(500);
  ok(await t.isVisible('#panel') && !(await t.isVisible('#dock')) && !(await t.$eval('#sayfa', e => e.classList.contains('acik'))), 'Masaüstü: sağ panel açık, alt şerit ve ölçü penceresi yok');
  await t.screenshot({path: O + 'C1_masa_bos.png'});
  // panelden ölçü: en 900 (yazarak), derinlik +10
  await t.fill('#pOlcu [name=en]', '900'); await yanit(); await t.waitForTimeout(300);
  await t.click('#pOlcu [data-adim=derinlik] [data-a="1"]'); await yanit(); await t.waitForTimeout(300);
  ok(await t.evaluate(() => S.v.govde.en === 900 && S.v.govde.derinlik === 570), 'Panelden ölçü: en 900, derinlik 570');
  // 2B: bölmeye tıkla → panelde araçlar → dikey bölme
  await t.click('#s2 rect.goz[data-goz="0"]');
  ok(await t.isVisible('#pAraclar [data-k=kapak]'), '2B: bölmeye tıklayınca panelde Raf / Bölme / Kapak / Çekmece');
  await t.click('#pAraclar [data-k=bolme]'); await t.waitForTimeout(300);
  await t.screenshot({path: O + 'C2_masa_bolme.png'});
  await Promise.all([yanit(), t.click('#uygula')]); await t.waitForTimeout(400);
  ok(await t.evaluate(() => S.v.gozler.length === 2), 'Dikey bölme eklendi');
  // 3B (Gerçek): fareyle sağ bölmeye tıkla → kapak
  await t.click('[data-gor="3b"]'); await t.waitForTimeout(1500);
  const nokta = await t.evaluate(() => {
    const i = S.v.gozler.findIndex(z => z.x1 > 300), m = k3.gozKutular.find(x => x.userData.goz === i);
    m.geometry.computeBoundingBox(); const c = new THREE.Vector3(); m.geometry.boundingBox.getCenter(c);
    c.z = m.geometry.boundingBox.max.z; c.project(k3.c);
    const b = k3.r.domElement.getBoundingClientRect();
    return {x: b.left + (c.x + 1) / 2 * b.width, y: b.top + (1 - c.y) / 2 * b.height, i};
  });
  await t.mouse.move(nokta.x, nokta.y); await t.waitForTimeout(200); await t.mouse.click(nokta.x, nokta.y); await t.waitForTimeout(300);
  ok(await t.evaluate(i => S.goz && S.goz.i === i && k3.seciliGoz === i, nokta.i), "3B: fareyle bölmeye tıklayınca bölme seçildi ve vurgulandı");
  await t.screenshot({path: O + 'C3_masa_3b_secim.png'});
  await t.click('#pAraclar [data-k=kapak]');
  await t.waitForFunction(() => document.getElementById('onBilgi') && document.getElementById('onBilgi').textContent.includes('menteşe'), null, {timeout: 8000});
  await Promise.all([yanit(), t.click('#uygula')]); await t.waitForTimeout(400);
  ok(await t.evaluate(() => S.v.parcalar2b.some(p => p.tur === 'kapak')), '3B üzerinden kapak eklendi');
  // sol bölmeye 3B'den raf
  const n2 = await t.evaluate(() => {
    const i = S.v.gozler.findIndex(z => z.x1 < 100), m = k3.gozKutular.find(x => x.userData.goz === i);
    m.geometry.computeBoundingBox(); const c = new THREE.Vector3(); m.geometry.boundingBox.getCenter(c);
    c.z = m.geometry.boundingBox.max.z; c.project(k3.c); const b = k3.r.domElement.getBoundingClientRect();
    return {x: b.left + (c.x + 1) / 2 * b.width, y: b.top + (1 - c.y) / 2 * b.height};
  });
  await t.mouse.click(n2.x, n2.y); await t.waitForTimeout(200);
  await t.click('#pAraclar [data-k=raf]'); await t.waitForTimeout(250);
  await t.fill('#sayfaIc [name=n]', '3');
  await Promise.all([yanit(), t.click('#uygula')]); await t.waitForTimeout(800);
  ok(await t.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'raf').length === 3), '3B üzerinden 3 raf eklendi');
  await t.screenshot({path: O + 'C4_masa_3b.png'});
  // klavye: Esc bırak, Ctrl+Z geri
  await t.keyboard.press('Escape'); await t.keyboard.press('Control+z'); await t.waitForTimeout(600);
  ok(await t.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'raf').length === 0), 'Ctrl+Z geri aldı');
  await t.keyboard.press('Control+y'); await t.waitForTimeout(600);
  // render
  await t.click('#pRender');
  await t.waitForSelector('#rAlan img', {timeout: 60000}); await t.waitForTimeout(400);
  await t.screenshot({path: O + 'C5_masa_render_panel.png'});
  const oda = await t.$eval('#rAlan img', i => i.src); fs.writeFileSync(O + 'C6_render_oda.jpg', Buffer.from(oda.split(',')[1], 'base64'));
  await t.click('#sayfaIc [data-seg=ortam] button[data-d=studyo]');
  await t.waitForFunction(o => { const i = document.querySelector('#rAlan img'); return i && i.src !== o; }, oda, {timeout: 60000});
  const st = await t.$eval('#rAlan img', i => i.src); fs.writeFileSync(O + 'C7_render_studyo.jpg', Buffer.from(st.split(',')[1], 'base64'));
  const [dl] = await Promise.all([t.waitForEvent('download'), t.click('#uygula')]);
  ok(dl.suggestedFilename().endsWith('.jpg'), 'Render indirildi: ' + dl.suggestedFilename());
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
