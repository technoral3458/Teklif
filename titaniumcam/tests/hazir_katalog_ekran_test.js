const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
async function yerel(c) {
  await c.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  for (const [p, f] of [['**/three.min.js', 'build/three.min.js'], ['**/OrbitControls.js', 'examples/js/controls/OrbitControls.js'], ['**/RoomEnvironment.js', 'examples/js/environments/RoomEnvironment.js']])
    await c.route(p, r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/' + f), contentType: 'application/javascript'}));
}
(async () => {
  const br = await chromium.launch({args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']});
  const hat = [];
  const d = await br.newContext({viewport: {width: 1280, height: 900}}); await yerel(d);
  const a = await d.newPage(); a.on('pageerror', e => hat.push(e.message));
  await a.goto(B + '/login'); await a.fill('[name=username]', 'admin'); await a.fill('[name=password]', 'admin123');
  await Promise.all([a.waitForNavigation(), a.click('button')]);
  await a.goto(B + '/titaniumcam/kabinler'); await a.waitForTimeout(300);
  await a.screenshot({path: O + 'E1_hazir_katalog.png', fullPage: true});
  await Promise.all([a.waitForNavigation({timeout: 120000}), a.click('button:has-text("Seçilenleri ekle")')]);
  const msg = await a.textContent('.bg-emerald-50'); console.log(msg.trim());
  ok(msg.includes('17 kabin eklendi'), 'Hazır katalog yüklendi');
  await a.waitForFunction(() => document.querySelectorAll('.kabin-sahne img.hazir').length >= 17, null, {timeout: 120000}); await a.waitForTimeout(500);
  await a.screenshot({path: O + 'E2_yonetici_katalog.png', fullPage: true});
  for (const [ad, vp] of [['masa', {viewport: {width: 1280, height: 900}}], ['tel', {viewport: {width: 390, height: 844}, isMobile: true, deviceScaleFactor: 2}]]) {
    const c = await br.newContext(vp); await yerel(c);
    const t = await c.newPage(); t.on('pageerror', e => hat.push(e.message));
    await t.goto(B + '/'); await t.waitForFunction(() => document.querySelectorAll('.kabin-sahne img.hazir').length >= 17, null, {timeout: 120000}); await t.waitForTimeout(500);
    await t.screenshot({path: O + `E3_vitrin_${ad}.png`, fullPage: true});
    await c.close();
  }
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
