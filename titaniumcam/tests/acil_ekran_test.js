const { chromium } = require('playwright'); const fs = require('fs'); const crypto = require('crypto');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100', O = S + '/ekran/';
const oz = k => crypto.createHash('sha256').update(k).digest('hex');
(async () => {
  const br = await chromium.launch();
  const c = await br.newContext({viewport: {width: 390, height: 844}, isMobile: true, hasTouch: true, deviceScaleFactor: 2});
  await c.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  const p = await c.newPage(); const hat = []; p.on('pageerror', e => hat.push(e.message));
  await p.goto(B + '/login'); await p.fill('[name=username]', 'admin'); await p.fill('[name=password]', 'admin123');
  await Promise.all([p.waitForNavigation(), p.click('button')]);
  await p.goto(B + '/titaniumcam/acil-lisans?sekme=anahtar');
  await p.screenshot({path: O + '80_anahtar_yok.png'});
  await p.fill('[name=onay]', 'EVET'); p.once('dialog', d => d.accept());
  await Promise.all([p.waitForNavigation(), p.click('button:has-text("Anahtar üret")')]);
  const acik = await p.inputValue('#acik'); console.log('açık anahtar', acik.length, 'hex');
  await p.screenshot({path: O + '81_anahtar.png'});
  const csv = 'lisans_no,musteri,moduller,bitis,kod_ozet\n' +
    `TC-20261101-39C235,Kontak Mobilya,"kabin,nesting",,${oz('KONTAKKODKONTAK1')}\n` +
    `TC-20261015-A1B2C3,Yıldız Ahşap,"kabin",2027-06-30,${oz('YILDIZKODYILDIZ1')}\n` +
    `TC-20260901-FF0012,Ege Kapı,"",2026-10-01,${oz('EGEKAPIKODEGEKA1')}\n`;
  await p.goto(B + '/titaniumcam/acil-lisans?sekme=yukle');
  await p.setInputFiles('input[type=file]', {name: 'liste.csv', mimeType: 'text/csv', buffer: Buffer.from(csv)});
  await Promise.all([p.waitForNavigation(), p.click('button:has-text("Yükle")')]);
  console.log((await p.textContent('.bg-emerald-50')).trim());
  // program gibi istek (yerel nginx gibi: 127.0.0.1 + X-Forwarded-Proto)
  const iste = (kod, mak, ik, ip) => p.request.post(B + '/api/titanium/v1/license/emergency', {data: {acil_kod: kod, makine: mak, istek_kodu: ik, surum: '2.0'}, headers: {'X-Forwarded-Proto': 'https', 'X-Real-IP': ip}});
  const M = {u: '1111111111111111', a: '2222222222222222', g: '3333333333333333', d: '4444444444444444'};
  for (const [k, ik, ip] of [['KONTAKKODKONTAK1', 'TCAM1-AAAA', '88.230.10.5'], ['KONTAKKODKONTAK1', 'TCAM1-BBBB', '88.230.10.5'], ['EGEKAPIKODEGEKA1', 'TCAM1-CCCC', '78.180.2.9'], ['YANLISYANLISYAN1', 'TCAM1-DDDD', '31.223.1.1']]) {
    const r = await iste(k, M, ik, ip); console.log(ik, r.status(), (await r.text()).slice(0, 80));
  }
  await p.request.post(B + '/api/titanium/v1/license/event', {data: {lisans_no: 'TC-20261101-39C235', olay: 'acilis', istek_kodu: 'TCAM1-AAAA', surum: '2.0'}, headers: {'X-Forwarded-Proto': 'https', 'X-Real-IP': '88.230.10.5'}});
  await p.goto(B + '/titaniumcam/acil-lisans'); await p.screenshot({path: O + '82_liste.png', fullPage: true});
  await p.goto(B + '/titaniumcam/acil-lisans?sekme=rapor'); await p.screenshot({path: O + '83_rapor.png', fullPage: true});
  await p.goto(B + '/titaniumcam/acil-lisans?sekme=ayar'); await p.screenshot({path: O + '84_ayar.png'});
  console.log('taşma yok:', await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'JS hata:', hat);
  await br.close();
})().catch(e => { console.error(e.message); process.exit(1); });
