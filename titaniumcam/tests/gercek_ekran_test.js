// Gerçekçi 3B: yönetici renk/doku ayarı + müşteri boy dolap, renk seçimi, ekran görüntüsü
const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
async function yerel(ctx) {
  await ctx.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  await ctx.route('**/three.min.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/build/three.min.js'), contentType: 'application/javascript'}));
  await ctx.route('**/OrbitControls.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/controls/OrbitControls.js'), contentType: 'application/javascript'}));
  await ctx.route('**/RoomEnvironment.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/environments/RoomEnvironment.js'), contentType: 'application/javascript'}));
}
(async () => {
  const br = await chromium.launch({args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']});
  const hat = [];
  // yönetici: MDF16 → Antrasit parlak, SUNTA18 → Ceviz ahşap
  const d = await br.newContext({viewport: {width: 1280, height: 900}}); await yerel(d);
  const a = await d.newPage(); a.on('pageerror', e => hat.push('admin: ' + e.message));
  await a.goto(B + '/login'); await a.fill('[name=username]', 'admin'); await a.fill('[name=password]', 'admin123');
  await Promise.all([a.waitForNavigation(), a.click('button')]);
  await a.goto(B + '/membrane/kabin/tanimlar');
  await a.fill('[name="g:MDF16:ad"]', 'Antrasit'); await a.$eval('[name="g:MDF16:renk"]', i => i.value = '#3b3f45');
  await a.selectOption('[name="g:MDF16:doku"]', 'parlak');
  await a.fill('[name="g:SUNTA18:ad"]', 'Ceviz'); await a.$eval('[name="g:SUNTA18:renk"]', i => i.value = '#9a6a43');
  await a.screenshot({path: O + '50_tanim_gorunum.png', clip: {x: 0, y: 700, width: 1280, height: 600}});
  await Promise.all([a.waitForNavigation(), a.click('button:has-text("Kaydet") >> nth=-1')]);
  ok((await a.inputValue('[name="g:MDF16:ad"]')) === 'Antrasit', 'Yönetici malzeme görünümünü kaydetti');

  for (const [ad, vp] of [['tel', {width: 390, height: 844, isMobile: true, hasTouch: true}], ['tablet', {width: 1000, height: 800}]]) {
    const m = await br.newContext({viewport: {width: vp.width, height: vp.height}, isMobile: !!vp.isMobile, hasTouch: !!vp.hasTouch, deviceScaleFactor: 2});
    await yerel(m);
    const t = await m.newPage(); t.on('pageerror', e => hat.push(e.message));
    const yanit = () => t.waitForResponse(r => /\/(islem|panel\/)/.test(r.url()) && r.request().method() === 'POST');
    const tik = sel => vp.hasTouch ? t.tap(sel) : t.click(sel);
    const gozSec = async kosul => { const i = await t.evaluate(f => S.v.gozler.findIndex(new Function('z', 'return ' + f)), kosul); if (i < 0) console.log(kosul, JSON.stringify(await t.evaluate(() => S.v.gozler.map(z => [z.x1, z.y1, z.x2, z.y2])))); await tik(`#s2 rect.goz[data-goz="${i}"]`); };
    await t.goto(B + '/');
    await Promise.all([t.waitForNavigation(), tik('button[value="Boy Dolap"]')]);
    await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
    await t.waitForSelector('#sayfa.acik');
    await t.fill('#sayfaIc [name=en]', '900'); await yanit(); await t.waitForTimeout(150);
    await Promise.all([yanit(), tik('#uygula')]); await t.waitForTimeout(300);
    // dikey bölme
    await gozSec('true'); await tik('#dockIc [data-k=bolme]'); await t.waitForTimeout(250);
    await Promise.all([yanit(), tik('#uygula')]); await t.waitForTimeout(250);
    // sağ bölmenin altına yatay bölme → alt kısma 3 çekmece, üstüne kapak
    await gozSec('z.x1 > 300'); await tik('#dockIc [data-k=bolme]'); await t.waitForTimeout(250);
    await tik('#sayfaIc [data-seg=yon] button[data-d=yatay]');
    await Promise.all([yanit(), tik('#uygula')]); await t.waitForTimeout(250);
    await gozSec('z.x1 > 300 && z.y1 > 500'); await tik('#dockIc [data-k=cekmece]');
    await t.waitForFunction(() => document.getElementById('onBilgi') && document.getElementById('onBilgi').textContent.includes('Çekmece önü'), null, {timeout: 8000});
    await Promise.all([yanit(), tik('#uygula')]); await t.waitForTimeout(300);
    await gozSec('z.x1 > 300 && z.y1 < 100'); await tik('#dockIc [data-k=kapak]');
    await t.waitForFunction(() => document.getElementById('onBilgi') && document.getElementById('onBilgi').textContent.includes('menteşe'), null, {timeout: 8000});
    await tik('#sayfaIc [data-seg=acilis] button[data-d=SAG]');
    await t.waitForTimeout(400);
    await Promise.all([yanit(), tik('#uygula')]); await t.waitForTimeout(300);
    // sol sütuna raf + kapak
    await gozSec('z.x1 < 100'); await tik('#dockIc [data-k=raf]'); await t.waitForTimeout(250);
    await t.fill('#sayfaIc [name=n]', '4');
    await Promise.all([yanit(), tik('#uygula')]); await t.waitForTimeout(250);
    await gozSec('z.x1 < 100 && z.y1 < 30'); await tik('#dockIc [data-k=kapak]');
    await t.waitForFunction(() => document.getElementById('onBilgi') && document.getElementById('onBilgi').textContent.includes('menteşe'), null, {timeout: 8000});
    await Promise.all([yanit(), tik('#uygula')]); await t.waitForTimeout(300);
    console.log(JSON.stringify(await t.evaluate(() => S.v.doktor.filter(b => b.seviye === 'hata'))).slice(0, 600));
    ok(await t.evaluate(() => !S.v.hata_var), ad + ': dolap kuruldu, doktor hatası yok');
    // renk: gövde ceviz, önler antrasit parlak
    await tik('#dockIc [data-k=malzeme]'); await t.waitForTimeout(400);
    await t.screenshot({path: O + `51_${ad}_renk.png`});
    await Promise.all([yanit(), tik('#sayfaIc [data-hedef=govde][data-mal=SUNTA18]')]); await t.waitForTimeout(200);
    await Promise.all([yanit(), tik('#sayfaIc [data-hedef=on][data-mal=MDF16]')]); await t.waitForTimeout(200);
    await tik('#uygula'); await t.waitForTimeout(300);
    ok(await t.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'kapak' || p.tur === 'cekmece').every(p => p.malzeme === 'MDF16')), ad + ': bütün önler antrasit');
    // gerçek görünüm
    await tik('[data-gor="3b"]'); await t.waitForTimeout(2500);
    await t.screenshot({path: O + `52_${ad}_gercek.png`});
    // yeni çekmece eklenince ön rengi korunur: geri al + tekrar
    await tik('[data-gor="2b"]'); await t.waitForTimeout(200);
    const once = await t.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'cekmece').length);
    await t.evaluate(() => { S.onGizli = false; });
    // kapakları gizleyip içini göster
    await tik('[data-gor="3b"]'); await t.waitForTimeout(300);
    await tik('#icGoster'); await t.waitForTimeout(1200);
    await t.screenshot({path: O + `53_${ad}_ic.png`});
    await tik('#icGoster'); await t.waitForTimeout(200);
    ok(once === 3, ad + ': 3 çekmece');
    ok(await t.evaluate(() => document.documentElement.scrollWidth <= innerWidth), ad + ': yatay taşma yok');
    await m.close();
  }
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
