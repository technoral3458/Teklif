// Kabinler: yönetici katalog kurar (renkli, gruplu), mağaza + yönetici ekranları
const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
async function yerel(ctx) {
  await ctx.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  await ctx.route('**/three.min.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/build/three.min.js'), contentType: 'application/javascript'}));
  await ctx.route('**/RoomEnvironment.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/environments/RoomEnvironment.js'), contentType: 'application/javascript'}));
  await ctx.route('**/OrbitControls.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/controls/OrbitControls.js'), contentType: 'application/javascript'}));
}
(async () => {
  const br = await chromium.launch({args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']});
  const hat = [];
  const d = await br.newContext({viewport: {width: 1280, height: 900}}); await yerel(d);
  const a = await d.newPage(); a.on('pageerror', e => hat.push('admin: ' + e.message));
  await a.goto(B + '/login'); await a.fill('[name=username]', 'admin'); await a.fill('[name=password]', 'admin123');
  await Promise.all([a.waitForNavigation(), a.click('button')]);
  // renkler: MDF16 antrasit parlak, SUNTA18 ceviz
  await a.goto(B + '/titaniumcam/kabin/tanimlar');
  await a.fill('[name="g:MDF16:ad"]', 'Antrasit'); await a.$eval('[name="g:MDF16:renk"]', i => i.value = '#3b3f45'); await a.selectOption('[name="g:MDF16:doku"]', 'parlak');
  await a.fill('[name="g:SUNTA18:ad"]', 'Ceviz'); await a.$eval('[name="g:SUNTA18:renk"]', i => i.value = '#9a6a43');
  await Promise.all([a.waitForNavigation(), a.click('button:has-text("Kaydet") >> nth=-1')]);
  const K = [
    ['İki Kapaklı Alt Dolap', 'Alt Dolap', 'Mutfak alt', [{tip: 'olcu', en: 800}, {tip: 'ekle', tur: 'raf', noktalar: [[400, 300]], adet: 1}, {tip: 'ekle', tur: 'kapak', noktalar: [[400, 300]], adet: 2, kulp: {sablon: 'Ust orta 128'}}, {tip: 'parca', idler: ['tum:on'], alanlar: {malzeme: 'MDF16'}}]],
    ['Çekmeceli Alt Dolap', 'Alt Dolap', 'Mutfak alt', [{tip: 'olcu', en: 600}, {tip: 'ekle', tur: 'cekmece', noktalar: [[300, 300]], adet: 3, sablon: 'Mandalli 500'}, {tip: 'ozellik', alanlar: {'govde.malzeme': 'SUNTA18'}}]],
    ['Kiler Boy Dolap', 'Boy Dolap', 'Boy dolaplar', [{tip: 'olcu', en: 600}, {tip: 'ekle', tur: 'raf', noktalar: [[300, 900]], adet: 4}, {tip: 'ekle', tur: 'kapak', noktalar: [[300, 100]], adet: 1, menteseler: ['SAG'], kulp: {sablon: 'Ust orta 128'}}, {tip: 'parca', idler: ['tum:on'], alanlar: {malzeme: 'MDF16'}}, {tip: 'ozellik', alanlar: {'govde.malzeme': 'SUNTA18'}}]],
    ['Fırın Dolabı', 'Boy Dolap', 'Boy dolaplar', [{tip: 'olcu', en: 600}, {tip: 'ekle', tur: 'yataybolme', noktalar: [[300, 1300]], adet: 1}, {tip: 'ekle', tur: 'cekmece', noktalar: [[300, 1700]], adet: 2, sablon: 'Mandalli 500'}, {tip: 'ekle', tur: 'kapak', noktalar: [[300, 300]], adet: 1, menteseler: ['SOL'], kulp: {sablon: 'Ust orta 128'}}]],
    ['Üst Dolap 2 Kapak', 'Ust Dolap', 'Mutfak üst', [{tip: 'olcu', en: 800}, {tip: 'ekle', tur: 'raf', noktalar: [[400, 300]], adet: 1}, {tip: 'ekle', tur: 'kapak', noktalar: [[400, 300]], adet: 2, kulp: {sablon: 'Ust orta 128'}}, {tip: 'parca', idler: ['tum:on'], alanlar: {malzeme: 'MDF16'}}]],
  ];
  for (const [ad, sab, kat, isl] of K) {
    const r = await a.request.post(B + '/titaniumcam/kabin/modeller/yeni', {form: {ad, sablon: sab, kategori: kat}, maxRedirects: 0});
    const yurl = r.headers()['location'];
    for (const x of isl) { const q = await a.request.post(B + yurl + '/islem', {data: {islem: x}}); if (!q.ok()) throw new Error(ad + ' ' + JSON.stringify(x) + ' ' + await q.text()); }
    const s = await a.request.post(B + yurl + '/modele-kaydet'); if (!s.ok()) throw new Error(ad + ': ' + await s.text());
  }
  await a.goto(B + '/titaniumcam/kabinler');
  for (const f of await a.$$eval('form[action$="/satis"]', fs => fs.map(x => x.action))) await a.request.post(f);
  // fiyatlar
  await a.goto(B + '/titaniumcam/kabinler?sekme=maliyet');
  for (const [k, v] of Object.entries({'f:malzeme:MDF18': 520, 'f:malzeme:MDF16': 610, 'f:malzeme:SUNTA18': 380, 'f:malzeme:SUNTA5': 90, 'f:malzeme:SUNTA8': 120, 'f:bant:PVC1': 6, 'f:bant:PVC04': 3, 'f:bant:PVC2': 9,
       'f:donati:kanal': 2, 'f:donati:mentese': 45, 'f:donati:kulp': 35, 'f:donati:ray': 180, 'f:donati:ayak': 12})) await a.fill(`[name="${k}"]`, String(v));
  for (const i of await a.$$('input[name^="f:baglanti:"]')) await i.fill('1.5');
  await a.fill('[name=kar]', '35'); await a.check('[name=goster]');
  await Promise.all([a.waitForNavigation(), a.click('button:has-text("Fiyatları kaydet")')]);
  ok((await a.textContent('body')).includes('Fiyatlar kaydedildi'), 'Fiyatlar kaydedildi');
  await a.click('details summary >> nth=0'); await a.waitForTimeout(200);
  await a.screenshot({path: O + 'A2_maliyetler.png', fullPage: true});
  await a.goto(B + '/titaniumcam/kabinler'); await a.waitForFunction(() => document.querySelectorAll('.kabin-sahne img.hazir').length >= 5, null, {timeout: 60000});
  await a.screenshot({path: O + 'A1_katalog.png', fullPage: true});
  ok(true, 'Yönetici katalogu: 5 kabin gerçekçi görselle');
  // mağaza (telefon)
  const m = await br.newContext({viewport: {width: 390, height: 844}, isMobile: true, hasTouch: true, deviceScaleFactor: 2}); await yerel(m);
  const t = await m.newPage(); t.on('pageerror', e => hat.push(e.message));
  await t.goto(B + '/'); await t.waitForFunction(() => document.querySelectorAll('.kabin-sahne img.hazir').length >= 5, null, {timeout: 60000});
  await t.screenshot({path: O + 'A3_vitrin_tel.png', fullPage: true});
  ok((await t.$$('.kabin-kart')).length === 5 && await t.isVisible('#cipler'), 'Vitrin: 5 kabin, grup düğmeleri');
  await t.tap('#cipler [data-grup="Boy dolaplar"]'); await t.waitForTimeout(150);
  ok((await t.$$eval('.kabin-kart', ks => ks.filter(k => !k.classList.contains('hidden')).length)) === 2, 'Grup süzgeci: 2 boy dolap');
  ok(await t.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Taşma yok');
  await Promise.all([t.waitForNavigation(), t.tap('.kabin-kart:not(.hidden) >> nth=0')]);
  await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
  ok(t.url().includes('/kabin/t/'), 'Kabine dokununca tasarım ekranı açıldı');
  const mm = await br.newContext({viewport: {width: 1280, height: 860}}); await yerel(mm);
  const tm = await mm.newPage(); await tm.goto(B + '/'); await tm.waitForFunction(() => document.querySelectorAll('.kabin-sahne img.hazir').length >= 5, null, {timeout: 60000});
  await tm.screenshot({path: O + 'A4_vitrin_masa.png', fullPage: true});
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
