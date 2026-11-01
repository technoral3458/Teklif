const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
(async () => {
  const br = await chromium.launch({args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']});
  const c = await br.newContext({viewport: {width: 390, height: 844}, isMobile: true, hasTouch: true, deviceScaleFactor: 2});
  await c.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  for (const [p, f] of [['**/three.min.js', 'build/three.min.js'], ['**/OrbitControls.js', 'examples/js/controls/OrbitControls.js'], ['**/RoomEnvironment.js', 'examples/js/environments/RoomEnvironment.js']])
    await c.route(p, r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/' + f), contentType: 'application/javascript'}));
  const t = await c.newPage(); const hat = []; t.on('pageerror', e => hat.push(e.message));
  await t.goto(B + '/'); await t.waitForTimeout(300);
  await Promise.all([t.waitForNavigation(), t.tap('.kabin-kart >> nth=0')]);
  await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
  await t.tap('#dockIc [data-k=render]');
  await t.waitForSelector('#rAlan img', {timeout: 60000}); await t.waitForTimeout(300);
  await t.screenshot({path: O + 'C8_tel_render.png'});
  console.log('telefon render tamam, hata:', JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
