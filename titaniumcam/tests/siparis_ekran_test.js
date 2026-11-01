const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], B = 'http://127.0.0.1:8100', O = S + '/ekran/';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
(async () => {
  const br = await chromium.launch(); const hat = [];
  const tw = async c => c.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(S + '/npm/tw.js'), contentType: 'application/javascript'}));
  // iki müşteri siparişi
  for (const [ad, ep, proje, n] of [['Zeynep Çelikkol', 'z@test.com', 'Kalyon', 1], ['Sefa Erşan', 's@test.com', 'Mutfak yenileme', 3]]) {
    const c = await br.newContext(); const p = await c.newPage();
    await p.request.post(B + '/magaza/kayit', {form: {full_name: ad, username: ep, password: '12345678', phone: ''}});
    const r = await p.request.post(B + '/kabin/yeni', {form: {sablon: 'Alt Dolap'}, maxRedirects: 0});
    const url = r.headers()['location'];
    await p.request.post(B + url + '/islem', {data: {islem: {tip: 'ekle', tur: 'kapak', noktalar: [[245, 300]], adet: 1}}});
    await p.request.post(B + url + '/sepet', {data: {adet: n}});
    await p.request.post(B + '/kabin/siparis', {form: {proje, note: ''}});
    await c.close();
  }
  const m = await br.newContext({viewport: {width: 412, height: 860}, isMobile: true, hasTouch: true, deviceScaleFactor: 2}); await tw(m);
  const a = await m.newPage(); a.on('pageerror', e => hat.push(e.message));
  await a.goto(B + '/login'); await a.fill('[name=username]', 'admin'); await a.fill('[name=password]', 'admin123');
  await Promise.all([a.waitForNavigation(), a.click('button')]);
  await a.waitForURL('**/titaniumcam/kabin', {timeout: 15000});
  await a.screenshot({path: O + 'B1_siparisler.png', fullPage: true});
  ok((await a.$$('form[action$="/sil"]')).length === 2 && !(await a.textContent('main')).includes('TitaniumCam API'), 'Liste: 2 sipariş, Sil düğmesi, kısayollar yok');
  ok(await a.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Taşma yok');
  await a.tap('text=Aç / düzenle >> nth=0'); await a.waitForLoadState();
  await a.tap('summary:has-text("Sipariş bilgilerini düzenle")'); await a.waitForTimeout(200);
  await a.screenshot({path: O + 'B2_siparis.png', fullPage: true});
  a.once('dialog', d => d.accept());
  await Promise.all([a.waitForNavigation(), a.tap('button:has-text("Siparişi sil")')]);
  ok((await a.textContent('body')).includes('silindi') && (await a.$$('form[action$="/sil"]')).length === 1, 'Sipariş silindi');
  await a.goto(B + '/titaniumcam/ayarlar'); await a.screenshot({path: O + 'B3_ayarlar.png'});
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
