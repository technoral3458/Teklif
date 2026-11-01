// Sade müşteri ekranı — telefon (390 px) uçtan uca + yönetici hazır model
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
  const br = await chromium.launch();
  const m = await br.newContext({viewport: {width: 390, height: 844}, isMobile: true, hasTouch: true});
  await yerel(m);
  const t = await m.newPage(); const hat = []; t.on('pageerror', e => hat.push(e.message));
  const yanit = () => t.waitForResponse(r => /\/(islem|panel\/)/.test(r.url()) && r.request().method() === 'POST');
  const gozSec = async kosul => { const i = await t.evaluate(f => S.v.gozler.findIndex(new Function('z', 'return ' + f)), kosul);
                                  await t.tap(`#s2 rect.goz[data-goz="${i}"]`); };
  await t.goto(B + '/');
  await Promise.all([t.waitForNavigation(), t.tap('button[value="Alt Dolap"]')]);
  await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
  ok(await t.evaluate(() => S.v.parcalar2b.some(p => p.tur === 'arkalik')), 'Arkalık otomatik geldi (müşteri seçmedi)');
  await t.waitForSelector('#sayfa.acik', {timeout: 5000});
  ok((await t.textContent('#sayfaBaslik')) === 'Ölçüler', 'Yeni dolapta ölçü penceresi kendiliğinden açıldı');
  await t.waitForTimeout(300);
  await t.screenshot({path: O + '30_basit_olcu.png'});
  await t.fill('#sayfaIc [name=en]', '800'); await yanit(); await t.waitForTimeout(150);
  await t.tap('#sayfaIc [data-adim=boy] [data-a="1"]'); await yanit();      // + düğmesi: 10 mm
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  ok(await t.evaluate(() => S.v.govde.en === 800 && S.v.govde.boy === 870), 'Ölçü: en 800 (yazarak), boy +10 (düğmeyle)');
  // bölme
  await gozSec('true');
  ok(await t.isVisible('#dockIc [data-k=kapak]'), 'Bölmeye dokununca Raf/Bölme/Kapak/Çekmece çıktı');
  await t.screenshot({path: O + '31_basit_goz.png'});
  await t.tap('#dockIc [data-k=bolme]'); await t.waitForTimeout(300);
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  ok(await t.evaluate(() => S.v.gozler.length === 2), 'Dikey bölme eklendi (2 bölüm)');
  // sol bölmeye raf
  await gozSec('z.x1 < 100'); await t.tap('#dockIc [data-k=raf]'); await t.waitForTimeout(300);
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  ok(await t.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'raf').length === 1), 'Raf eklendi');
  // sol sütuna kapak (bütün sütun, tek seçimle)
  await gozSec('z.x1 < 100 && z.y1 < 10'); await t.tap('#dockIc [data-k=kapak]');
  await t.waitForFunction(() => document.getElementById('onBilgi') && document.getElementById('onBilgi').textContent.includes('menteşe'), null, {timeout: 8000});
  await t.screenshot({path: O + '32_basit_kapak.png'});
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  ok(await t.evaluate(() => { const k = S.v.parcalar2b.filter(p => p.tur === 'kapak'); return k.length === 1 && k[0].kutu.y2 - k[0].kutu.y1 > 700; }), 'Kapak bütün sütunu kapattı');
  // sağ bölmeye 3 çekmece
  await gozSec('z.x1 > 300'); await t.tap('#dockIc [data-k=cekmece]');
  await t.waitForFunction(() => document.getElementById('onBilgi') && document.getElementById('onBilgi').textContent.includes('Çekmece önü'), null, {timeout: 8000});
  await t.screenshot({path: O + '33_basit_cekmece.png'});
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(400);
  ok(await t.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'cekmece').length === 3 && !S.v.hata_var), '3 çekmece, doktor hatası yok');
  await t.screenshot({path: O + '34_basit_bitti.png'});
  // kapağa dokun → düzenle → 2 kapak
  await t.tap('#s2 g.parca[data-tur=kapak] rect'); await t.waitForTimeout(200);
  ok(await t.isVisible('#dockIc [data-k=duzenle]'), 'Kapağa dokununca Düzenle/Sil');
  await t.tap('#dockIc [data-k=duzenle]'); await t.waitForTimeout(300);
  await t.tap('#sayfaIc [data-seg=n] button[data-d="2"]'); await t.waitForTimeout(500);
  await Promise.all([yanit(), t.tap('#uygula')]); await t.waitForTimeout(300);
  ok(await t.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'kapak').length === 2), 'Kapak 2 kapağa çevrildi');
  // 3B
  await t.tap('[data-gor="3b"]'); await t.waitForTimeout(800);
  await t.screenshot({path: O + '35_basit_3b.png'});
  await t.tap('[data-gor="2b"]');
  ok(await t.evaluate(() => document.documentElement.scrollWidth <= 390), 'Yatay taşma yok');
  // sepete ekle
  await t.tap('#sepetBtn'); await t.waitForTimeout(300); await t.tap('#uygula');
  await t.waitForSelector('#git', {timeout: 5000});
  ok(true, 'Sepete eklendi');
  await Promise.all([t.waitForNavigation(), t.tap('#git')]);
  ok(t.url().endsWith('/magaza/sepet'), 'Sepete gidildi');
  // ── yönetici: hazır model ──
  const d = await br.newContext({viewport: {width: 1280, height: 900}}); await yerel(d);
  const a = await d.newPage(); a.on('pageerror', e => hat.push('admin: ' + e.message));
  await a.goto(B + '/login'); await a.fill('[name=username]', 'admin'); await a.fill('[name=password]', 'admin123');
  await Promise.all([a.waitForNavigation(), a.click('button')]);
  for (const [ad, sab, isl] of [['Çekmeceli Alt Dolap', 'Alt Dolap', [{tip: 'olcu', en: 600}, {tip: 'ekle', tur: 'cekmece', noktalar: [[300, 300]], adet: 4, sablon: 'Mandalli 500'}]],
                                ['Raflı Boy Dolap', 'Boy Dolap', [{tip: 'ekle', tur: 'raf', noktalar: [[300, 900]], adet: 4}, {tip: 'ekle', tur: 'kapak', noktalar: [[300, 100]], adet: 1, menteseler: ['SAG'], kulp: {sablon: 'Ust orta 128'}}]],
                                ['İki Kapaklı Üst Dolap', 'Ust Dolap', [{tip: 'olcu', en: 800}, {tip: 'ekle', tur: 'raf', noktalar: [[400, 300]], adet: 1}, {tip: 'ekle', tur: 'kapak', noktalar: [[400, 100], [400, 500]], adet: 2, kulp: {sablon: 'Ust orta 128'}}]]]) {
    await a.goto(B + '/titaniumcam/kabinler');
    if (!(await a.isVisible('[name=ad]'))) await a.click('summary:has-text("Yeni kabin")');
    await a.fill('[name=ad]', ad); await a.selectOption('select[name=sablon]', sab);
    await Promise.all([a.waitForNavigation(), a.click('button:has-text("Oluştur ve tasarla")')]);
    const taban = new URL(a.url()).pathname;
    for (const x of isl) { const r = await a.request.post(B + taban + '/islem', {data: {islem: x}}); if (!r.ok()) throw new Error(ad + ': ' + await r.text()); }
    const r = await a.request.post(B + taban + '/modele-kaydet'); if (!r.ok()) throw new Error(await r.text());
  }
  await a.goto(B + '/titaniumcam/kabinler');
  const formlar = await a.$$eval('form[action$="/guncelle"]', fs => fs.map(f => f.action));
  for (const act of formlar) await a.request.post(act, {form: {acik: '1', aciklama: 'Ölçüye göre üretilir'}});
  await a.goto(B + '/titaniumcam/kabinler'); await a.waitForTimeout(300);
  await a.screenshot({path: O + '36_yonetici_modeller.png', fullPage: true});
  ok((await a.textContent('body')).includes('Satışta'), 'Yönetici: 3 kabin satışa açıldı');
  // vitrin
  const v = await m.newPage(); await v.goto(B + '/'); await v.waitForTimeout(400);
  await v.screenshot({path: O + '37_vitrin_modeller.png', fullPage: true});
  ok((await v.textContent('body')).includes('Raflı Boy Dolap'), 'Vitrinde hazır modeller');
  await Promise.all([v.waitForNavigation(), v.tap('button[name=model] >> nth=1')]);
  await v.waitForFunction(() => typeof S !== 'undefined' && S.v);
  ok(await v.evaluate(() => S.v.parcalar2b.filter(p => p.tur === 'raf').length === 4), 'Müşteri modelden başladı (4 raf, kapak hazır)');
  await v.screenshot({path: O + '38_modelden_basit.png'});
  ok(hat.length === 0, 'JS hatası yok ' + JSON.stringify(hat));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
