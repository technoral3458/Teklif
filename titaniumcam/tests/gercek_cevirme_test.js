// Kullanıcının durumu: Boy Dolap + raf, Gerçek görünüm, parmakla çevirme
const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
(async () => {
  const br = await chromium.launch({args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']});
  const m = await br.newContext({viewport: {width: 412, height: 860}, isMobile: true, hasTouch: true, deviceScaleFactor: 2});
  await m.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  await m.route('**/three.min.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/build/three.min.js'), contentType: 'application/javascript'}));
  await m.route('**/OrbitControls.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/controls/OrbitControls.js'), contentType: 'application/javascript'}));
  await m.route('**/RoomEnvironment.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/environments/RoomEnvironment.js'), contentType: 'application/javascript'}));
  const t = await m.newPage(); t.on('pageerror', e => console.log('HATA', e.message)); t.on('console', c => c.type() !== 'log' && console.log(c.type(), c.text()));
  const yanit = () => t.waitForResponse(r => /\/(islem|panel\/)/.test(r.url()) && r.request().method() === 'POST');
  await t.goto(B + '/');
  await t.goto(B + (await t.request.post(B + '/kabin/yeni', {form: {sablon: 'Boy Dolap'}, maxRedirects: 0})).headers()['location']);
  await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
  await t.waitForSelector('#sayfa.acik'); await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  await t.tap('#s2 rect.goz[data-goz="0"]'); await t.tap('#dockIc [data-k=raf]'); await t.waitForTimeout(250);
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  await t.tap('#s2 rect.goz[data-goz="0"]');
  await t.tap('[data-gor="3b"]'); await t.waitForTimeout(2000);
  await t.screenshot({path: O + '70_a.png'});
  console.log((await t.evaluate(() => JSON.stringify(S.v.parcalar3b.map(p => [p.tur, p.ad, p.koseler.map(k => k.map(Math.round))])))).slice(0, 3000));
  // sürükle (fare ile çevirme)
  const c = await t.$('#v3 canvas'); const b = await c.boundingBox();
  await t.mouse.move(b.x + b.width / 2, b.y + b.height / 2); await t.mouse.down();
  await t.mouse.move(b.x + b.width / 2 + 150, b.y + b.height / 2 + 120, {steps: 10}); await t.mouse.up(); await t.waitForTimeout(1200);
  await t.screenshot({path: O + '70_b.png'});
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
