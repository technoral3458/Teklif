// Online Kabin tarayıcı testi. Çalıştırma: node tests/ekran_test.js <klasör>
//   <klasör>/npm: three@0.128.0 + tw.js (CDN'e erişilemeyen ortam için yerel kopya), <klasör>/ekran: çıktı
//   Site 127.0.0.1:8100'de, motor servisi 127.0.0.1:8210'da çalışıyor olmalı.
const { chromium } = require('playwright');
const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', OUT = S + '/ekran/', B = 'http://127.0.0.1:8100';
async function yerel(ctx) {
  await ctx.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  await ctx.route('**/three.min.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/build/three.min.js'), contentType: 'application/javascript'}));
  await ctx.route('**/OrbitControls.js', r => r.fulfill({body: fs.readFileSync(N + 'node_modules/three/examples/js/controls/OrbitControls.js'), contentType: 'application/javascript'}));
}
const EP = 'musteri' + Date.now() + '@test.com';
const ok = (c, m) => { if (!c) throw new Error('BAŞARISIZ: ' + m); console.log('✓ ' + m); };
async function bekle(p) { await p.waitForResponse(r => r.url().includes('/islem') || r.url().includes('/panel/'), {timeout: 15000}); await p.waitForTimeout(250); }
async function goz(p, i, mod) { await p.click(`svg#s2 rect.goz[data-goz="${i}"]`, mod ? {modifiers: ['Control']} : {}); }
async function gozBul(p, f) {   // ölçüye göre göz indeksi (motorun döndürdüğü gözler)
  return await p.evaluate(f => S.v.gozler.findIndex(new Function('z', 'return ' + f)), f);
}
(async () => {
  const br = await chromium.launch();
  // ── masaüstü müşteri ─────────────────────────────────────────────
  const ctx = await br.newContext({viewport: {width: 1440, height: 900}, acceptDownloads: true});
  await yerel(ctx);
  const p = await ctx.newPage();
  const hatalar = []; p.on('pageerror', e => hatalar.push(e.message));
  await p.goto(B + '/magaza/kayit');
  await p.fill('[name=full_name]', 'Deneme Müşteri'); await p.fill('[name=username]', EP); await p.fill('[name=password]', '1234');
  await Promise.all([p.waitForNavigation(), p.click('button:has-text("Hesabı Oluştur")')]);
  await p.goto(B + '/kabin/yeni');
  await p.screenshot({path: OUT + '1_sablon_sec.png'});
  await Promise.all([p.waitForNavigation(), p.click('button[value="Alt Dolap"]')]);
  await p.waitForFunction(() => typeof S !== 'undefined' && S.v);
  // 1) kabin özellikleri: en 800 (canlı, Tamam)
  await p.click('[data-k=ozellik]');
  await p.fill('#panelAlan [name=en]', '800');
  await bekle(p);
  await p.screenshot({path: OUT + '2_kabin_ozellikleri.png'});
  await p.click('#panelTamam'); await bekle(p);
  ok(await p.evaluate(() => S.v.govde.en === 800 && S.v.govde.govde_boyu === 740), 'Adım 1: en 800, gövde boyu 740');
  // 2) dikey bölme, sabit göz 400
  await goz(p, 0);
  await p.click('[data-ekle=dikeybolme]'); await p.fill('#dlgSol [name=sabit]', '400');
  await p.click('#dlgTamam'); await bekle(p);
  ok(await p.evaluate(() => { const b = S.v.parcalar2b.find(x => x.tur === 'dikeybolme').kutu; return b.x1 === 418 && b.x2 === 436; }), 'Adım 2: bölme X 418–436');
  // 3) sol göze raf
  await goz(p, await gozBul(p, 'z.x1===18'));
  await p.click('[data-ekle=raf]'); await p.click('#dlgTamam'); await bekle(p);
  ok(await p.evaluate(() => { const r = S.v.parcalar2b.find(x => x.tur === 'raf').kutu; return r.x1 === 19 && r.x2 === 417 && r.y1 === 352; }), 'Adım 3: raf X 19–417, Y 352–370');
  // 4) sol iki göz + kapak
  await goz(p, await gozBul(p, 'z.x1===18 && z.y1===0'));
  await goz(p, await gozBul(p, 'z.x1===18 && z.y1===370'), true);
  await p.click('[data-ekle=kapak]');
  await p.waitForSelector('#onSvg', {timeout: 10000}); await p.waitForTimeout(400);
  await p.screenshot({path: OUT + '3_kapak_penceresi.png'});
  await p.click('#dlgTamam'); await bekle(p);
  ok(await p.evaluate(() => { const k = S.v.parcalar2b.find(x => x.tur === 'kapak'); return k.kutu.x2 === 425.5 && k.kutu.y2 === 740 && k.mentese === 3; }), 'Adım 4: kapak 735 × 423,5, 3 menteşe');
  // 5) sağ göz + 3 çekmece (kulpsuz, metindeki örnek gibi)
  await goz(p, await gozBul(p, 'z.x1===436'));
  await p.click('[data-ekle=cekmece]');
  await p.waitForSelector('#rayKartlar .kart'); await p.fill('#dlgSol [name=adet]', '3');
  await p.uncheck('#dlgSol [name=kulpVar]');
  await p.waitForSelector('#onSvg'); await p.waitForTimeout(700);
  await p.screenshot({path: OUT + '4_cekmece_penceresi.png'});
  await p.click('#dlgTamam'); await bekle(p);
  ok(await p.evaluate(() => S.v.parcalar2b.filter(x => x.tur === 'cekmece').length === 3), 'Adım 5: 3 çekmece');
  // 6) üç gözü seç + arkalık (çakma)
  const n = await p.evaluate(() => S.v.gozler.length);
  for (let i = 0; i < n; i++) await goz(p, i, i > 0);
  await p.click('[data-ekle=arkalik]'); await p.click('#dlgTamam'); await bekle(p);
  const ozet = await p.textContent('#ozet');
  ok(ozet.includes('25 parça') && ozet.includes('3,895 m²') && ozet.includes('9,36 m bant'), 'Sonuç: ' + ozet);
  ok((await p.textContent('#kontrolBtn')).includes('Kontrol'), 'Doktor: 0 hata');
  await p.waitForTimeout(500);
  await p.screenshot({path: OUT + '5_editor_masaustu.png'});
  // sürükle: bölme +50 (kilit oluşmaz) sonra geri al
  const box = await p.locator('svg#s2 g.parca[data-tur=dikeybolme] rect').boundingBox();
  const olcek = await p.evaluate(() => { const m = document.getElementById('s2').getScreenCTM(); return m.a; });
  await p.mouse.move(box.x + box.width / 2, box.y + box.height / 2); await p.mouse.down();
  await p.mouse.move(box.x + box.width / 2 + 50 * olcek, box.y + box.height / 2, {steps: 5}); await Promise.all([bekle(p), p.mouse.up()]);
  ok(await p.evaluate(() => { const b = S.v.parcalar2b.find(x => x.tur === 'dikeybolme').kutu; return Math.abs(b.x1 - 468) <= 1 && !S.v.gozler.some(z => z.kilit.x); }), 'Sürükleme: bölme ~X 468, kilit yok');
  await p.click('[data-k=geri]'); await p.waitForTimeout(600);
  ok(await p.evaluate(() => S.v.parcalar2b.find(x => x.tur === 'dikeybolme').kutu.x1 === 418), 'Geri al: bölme X 418');
  // sepete ekle → sipariş
  await p.click('#sepetBtn'); await p.click('#dlgTamam'); await p.waitForTimeout(800);
  await p.goto(B + '/kabin');
  await p.fill('[name=proje]', 'Mutfak deneme');
  await Promise.all([p.waitForNavigation(), p.click('button:has-text("Sipariş ver")')]);
  const oid = p.url().split('/').pop();
  ok(/\/kabin\/siparis\/\d+$/.test(p.url()), 'Sipariş verildi: ' + p.url());
  await p.screenshot({path: OUT + '6_musteri_siparis.png'});
  // ── yönetici ─────────────────────────────────────────────────────
  const a = await ctx.newPage();
  await a.goto(B + '/login'); await a.fill('[name=username]', 'admin'); await a.fill('[name=password]', 'admin123');
  await Promise.all([a.waitForNavigation(), a.click('button[type=submit], button')]);
  await a.goto(B + '/membrane/kabin/siparis/' + oid);
  await a.waitForSelector('table tbody tr'); await a.waitForTimeout(400);
  await a.screenshot({path: OUT + '7_yonetici_onay.png', fullPage: true});
  await Promise.all([a.waitForNavigation(), a.click('button:has-text("Onayla")')]);
  ok((await a.textContent('body')).includes('Onaylı'), 'Yönetici onayladı');
  const [dl] = await Promise.all([a.waitForEvent('download'), a.click('a:has-text("İndir (.tkabin)")')]);
  const yol = OUT + dl.suggestedFilename(); await dl.saveAs(yol);
  const tk = JSON.parse(fs.readFileSync(yol, 'utf8'));
  ok(tk.format === 'tkabin' && tk.schema === 1 && tk.kabin.en === 800 && tk.kabin.adet === 1, '.tkabin indirildi: ' + dl.suggestedFilename());
  // ── telefon 375 px ───────────────────────────────────────────────
  const m = await br.newContext({viewport: {width: 375, height: 812}, isMobile: true, hasTouch: true});
  await yerel(m);
  const t = await m.newPage(); t.on('pageerror', e => hatalar.push('mobil: ' + e.message));
  await t.goto(B + '/magaza/giris'); await t.fill('[name=username]', EP); await t.fill('[name=password]', '1234');
  await Promise.all([t.waitForNavigation(), t.click('button:has-text("Giriş")')]);
  await t.goto(B + '/kabin'); await t.screenshot({path: OUT + '8_mobil_dolaplarim.png', fullPage: true});
  ok(await t.evaluate(() => document.documentElement.scrollWidth <= 375), 'Mobil /kabin: yatay kaydırma yok');
  await Promise.all([t.waitForNavigation(), t.click('a:has-text("Aç")')]);
  await t.waitForFunction(() => typeof S !== 'undefined' && S.v);
  ok(await t.evaluate(() => document.documentElement.scrollWidth <= 375), 'Mobil editör: yatay kaydırma yok');
  await t.screenshot({path: OUT + '9_mobil_editor.png'});
  await t.click('[data-ac=sol]'); await t.waitForTimeout(300);
  await t.screenshot({path: OUT + '10_mobil_araclar.png'});
  await t.click('[data-kapat=sol]'); await t.click('[data-ac=sag]'); await t.waitForTimeout(300);
  await t.screenshot({path: OUT + '11_mobil_parcalar.png'});
  await t.click('[data-kapat=sag]');
  await t.click('#ust [data-gor="3b"] >> visible=true'); await t.waitForTimeout(800);
  await t.screenshot({path: OUT + '12_mobil_3b.png'});
  ok(hatalar.length === 0, 'Tarayıcı JS hatası yok ' + JSON.stringify(hatalar));
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
