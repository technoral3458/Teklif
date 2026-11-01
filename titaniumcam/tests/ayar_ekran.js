const { chromium } = require('playwright'); const fs = require('fs');
const S = process.argv[2], N = S + '/npm/', B = 'http://127.0.0.1:8100';
(async () => {
  const br = await chromium.launch(); const ctx = await br.newContext({viewport: {width: 1280, height: 900}});
  await ctx.route('https://cdn.tailwindcss.com/**', r => r.fulfill({body: fs.readFileSync(N + 'tw.js'), contentType: 'application/javascript'}));
  const a = await ctx.newPage(); const hat = []; a.on('pageerror', e => hat.push(e.message));
  await a.goto(B + '/magaza/kayit'); await a.fill('[name=full_name]', 'Usta Mobilya'); await a.fill('[name=username]', 'usta@test.com'); await a.fill('[name=password]', '1234');
  await Promise.all([a.waitForNavigation(), a.click('button:has-text("Hesabı Oluştur")')]);
  await a.goto(B + '/login'); await a.fill('[name=username]', 'admin'); await a.fill('[name=password]', 'admin123');
  await Promise.all([a.waitForNavigation(), a.click('button')]);
  await a.goto(B + '/titaniumcam/ayarlar');
  await a.check('input[name=kabin_onay][value=otomatik]');
  await Promise.all([a.waitForNavigation(), a.click('button:has-text("Kaydet")')]);
  console.log('kayit:', (await a.textContent('body')).includes('kaydedildi'));
  await a.screenshot({path: S + '/ekran/22_ayarlar.png', fullPage: true});
  console.log('JS hatası:', JSON.stringify(hat));
  await br.close();
})();
