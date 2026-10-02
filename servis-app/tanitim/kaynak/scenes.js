/* Deli Kadir App tanıtım videosu — kare bazlı deterministik animasyon.
   window.frame(t) çağrıldığında t saniyesindeki görüntü kurulur. */
(() => {
const $ = s => document.querySelector(s);
const $$ = s => Array.from(document.querySelectorAll(s));
const cl = x => x < 0 ? 0 : x > 1 ? 1 : x;
const seg = (t, a, b) => cl((t - a) / (b - a));
const eo = p => 1 - Math.pow(1 - p, 3);
const eio = p => p < .5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
const eb = p => { const c1 = 1.9, c3 = c1 + 1; return 1 + c3 * Math.pow(p - 1, 3) + c1 * Math.pow(p - 1, 2); };
const lerp = (a, b, p) => a + (b - a) * p;
const PH_Y = 150, PH_S = 1.2;   // telefonun sahnedeki yeri ve ölçeği

function money(v, sym = '₺', dec = 2) {
  const neg = v < 0; v = Math.abs(v);
  let [i, f] = v.toFixed(dec).split('.');
  i = i.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return (neg ? '-' : '') + (dec ? `${i},${f}` : i) + (sym ? ' ' + sym : '');
}
function rise(el, p, dy = 44, extra = '') {
  el.style.opacity = p;
  el.style.transform = `translateY(${(1 - eo(cl(p))) * dy}px) ${extra}`;
}
function pop(el, p) {
  el.style.opacity = cl(p * 1.6);
  el.style.transform = `scale(${lerp(.62, 1, eb(cl(p)))})`;
}
function type(el, text, p, caret) {
  const n = Math.round(cl(p) * text.length);
  el.textContent = text.slice(0, n) + (caret && n < text.length ? '▌' : '');
}
function stagger(list, t, start, step, dur, dy) {
  list.forEach((el, i) => rise(el, seg(t, start + i * step, start + i * step + dur), dy));
}
function hideAll(list) { list.forEach(el => { el.style.opacity = 0; }); }

// ---------------------------------------------------------------- elemanlar
const stage = $('#stage'), bg = $('#bg'), head = $('#head'), brand = $('#brand'), prog = $('#prog');
const scSplash = $('#sc-splash'), scPdf = $('#sc-pdf'), scOutro = $('#sc-outro');
const phoneWrap = $('#phoneWrap'), phone = $('#phone');
const APPS = $$('.app');
const mailDlg = $('#mailDlg'), toast = $('#toast');
const SPLASH_T = 'BEN DELİ KADİR ULANNNN';
const SPLASH_S = 'Yer Yüzünün En Sexsü Teknik Servisi';
const FAULT = 'Kesim sırasında X ekseninde titreşim ve ölçü kaçıklığı. Operatör 2 mm\'ye varan sapma bildirdi.';
const MAILTO = 'bakim@mermercimakine.com';

// ---------------------------------------------------------------- zaman çizelgesi
const TL = [
  { id: 'splash', dur: 6.4 },
  { id: 'dash',   dur: 6.6 },
  { id: 'form',   dur: 8.6 },
  { id: 'money',  dur: 7.6 },
  { id: 'exp',    dur: 7.6 },
  { id: 'pdf',    dur: 8.4 },
  { id: 'fin',    dur: 7.4 },
  { id: 'mail',   dur: 5.2 },
  { id: 'outro',  dur: 8.2 },
];
let acc = 0;
TL.forEach(s => { s.t0 = acc; acc += s.dur; s.t1 = acc; });
const TOTAL = acc;
window.TOTAL = TOTAL;

const HEADS = {
  dash:  ['ÖZET', 'Sahanın <em>tek</em> ekranı', 'Bu ay kaç servis, ne kadar alacak, kim gecikti — açar açmaz görürsün.'],
  form:  ['RAPOR', 'Raporu <em>sahada</em> yaz', 'Bölümler, arıza, çözüm, fotoğraf, yedek parça ve imza. Hepsi makinenin başında.'],
  money: ['KUR', 'Euro mu? <em>Kuru kendi alır</em>', 'TCMB kurunu otomatik çeker, rapora işler. Masraf TL olsa da kâr doğru çıkar.'],
  exp:   ['MASRAF', 'Masraf <em>müşterinin</em>', 'Otel, yakıt, otoyol, yemek — fişiyle birlikte servis bedelinin üstüne eklenir.'],
  pdf:   ['PDF', 'Tek tuşla <em>PDF</em>', 'Servis raporu ve masraf dökümü; fişlerin fotoğrafları da aynı dosyada.'],
  fin:   ['CARİ', 'Vadesi geçeni <em>unutmaz</em>', 'Kim ne kadar borçlu, kim söz verip ödemedi — uyarıyı o verir.'],
  mail:  ['MAİL', 'Müşteriye <em>tek tuşla</em> gider', 'Kendi SMTP hesabınla, telefondan. Rapor, fişler ve fotoğraflar ekte.'],
};

// ---------------------------------------------------------------- yardımcılar
function showPhone(p, dy, scale) {
  phoneWrap.style.opacity = p;
  phoneWrap.style.transform = `translate(-50%,-50%) translateY(${dy}px) scale(${scale})`;
}
function appSwap(curId, p) {
  hideAll(APPS);
  const c = $('#' + curId);
  c.style.opacity = cl(p * 1.4);
  c.style.transform = `translateX(${(1 - eo(cl(p))) * 30}px)`;
}
function setHead(key, pIn, pOut) {
  const h = HEADS[key];
  if (!h) { head.style.opacity = 0; return; }
  $('#eyebrowT').textContent = h[0];
  $('#headline').innerHTML = h[1];
  $('#subline').innerHTML = h[2];
  const o = Math.min(pIn, 1 - pOut);
  head.style.opacity = o;
  head.style.transform = `translateY(${(1 - eo(pIn)) * 40 - eo(pOut) * 28}px)`;
}

// ---------------------------------------------------------------- sahneler
function sSplash(t, d) {
  scSplash.style.opacity = cl(seg(t, 0, .35) - seg(t, d - .9, d));
  const icon = $('#spIcon'), halo = $('#spHalo'), wrap = $('#spWrap');
  const sp = seg(t, .25, 1.25);
  icon.style.opacity = cl(sp * 1.8);
  icon.style.transform = `scale(${lerp(.55, 1, eb(sp))})`;
  const hp = .85 + .27 * (.5 - .5 * Math.cos(t * Math.PI / .8));   // 1,6 sn nabız
  halo.style.transform = `scale(${hp})`;
  halo.style.opacity = cl(sp);
  const blink = Math.floor(t / .48) % 2 === 0;
  const tp = seg(t, .95, 2.35), sbp = seg(t, 2.55, 3.95);
  type($('#spTitle'), SPLASH_T, tp, blink);
  type($('#spSub'), SPLASH_S, sbp, blink && tp >= 1);
  const done = sbp >= 1;
  $$('#spDots i').forEach((el, i) => {
    const w = .25 + .75 * (.5 - .5 * Math.cos((t - i * .17) * Math.PI / .52));
    el.style.opacity = cl(sbp) * (done ? 1 : w);
  });
  $('#spHint').style.opacity = cl(seg(t, 1.6, 2.4)) * .9 - seg(t, d - 1.4, d - .6);
  const ex = seg(t, d - 1.1, d);
  wrap.style.transform = `translate(-50%,-50%) scale(${lerp(1, .78, eio(ex))})`;
  // arka plan ve telefonun ilk belirişi
  const pin = seg(t, d - .75, d);
  showPhone(pin * .9, lerp(PH_Y + 110, PH_Y + 12, eo(pin)), lerp(PH_S * .88, PH_S * .97, eo(pin)));
  appSwap('app-dash', seg(t, d - .7, d));
}

function sDash(t, d) {
  const ip = eo(seg(t, 0, .9));
  showPhone(1, lerp(PH_Y + 12, PH_Y, ip), lerp(PH_S * .97, PH_S, ip));
  appSwap('app-dash', 1);
  stagger($$('#dashScroll .an'), t, .35, .10, .55, 40);
  const sc = -270 * eio(seg(t, 3.3, 5.5));
  $('#dashScroll').style.transform = `translateY(${sc}px)`;
  rise($('#dashFab'), seg(t, 1.5, 2.1), 30);
  const pz = seg(t, 2.2, 3.0);
  const pulse = pz > 0 && pz < 1 ? 1 + .035 * Math.sin(pz * Math.PI * 2) : 1;
  const w = $('#dashWarn');
  w.style.transform = `translateY(${(1 - eo(seg(t, .75, 1.3))) * 40}px) scale(${pulse})`;
}

function sForm(t, d) {
  showPhone(1, PH_Y, PH_S);
  appSwap('app-form', seg(t, 0, .5));
  stagger($$('#formScroll .an'), t, .3, .085, .5, 40);
  $$('#depChips .chip').forEach(c => c.classList.remove('on'));
  const order = [[1, 1.35], [2, 1.65], [3, 1.95]];
  order.forEach(([n, at]) => { if (t >= at) $(`#depChips .chip[data-dep="${n}"]`).classList.add('on'); });
  const tp = seg(t, 2.25, 5.0);
  type($('#faultText'), FAULT, tp, Math.floor(t / .48) % 2 === 0);
  let sc = 0;
  sc -= 150 * eio(seg(t, 5.1, 5.9));
  sc -= 265 * eio(seg(t, 6.9, 7.9));
  $('#formScroll').style.transform = `translateY(${sc}px)`;
  $$('#photoRow .photo').forEach((el, i) => pop(el, seg(t, 2.35 + i * .22, 2.9 + i * .22)));
}

function sMoney(t, d) {
  showPhone(1, PH_Y, PH_S);
  appSwap('app-money', seg(t, 0, .5));
  stagger($$('#app-money .an'), t, .3, .1, .5, 40);
  $$('#curSeg div').forEach(el => el.classList.remove('on'));
  if (t >= 1.0) $('#curSeg div[data-c="EUR"]').classList.add('on');
  else $('#curSeg div[data-c="TRY"]').classList.add('on');
  $('#amtCur').textContent = t >= 1.0 ? '€' : '₺';
  const digits = '500';
  type($('#amtVal'), digits, seg(t, 1.25, 1.95), Math.floor(t / .48) % 2 === 0);
  if (t < 1.25) $('#amtVal').textContent = '0';
  const fetched = t >= 3.5;
  $('#rateSpin').style.display = fetched ? 'none' : 'block';
  $('#rateIcon').style.display = fetched ? 'inline-block' : 'none';
  $('#rateSpin').style.transform = `rotate(${t * 520}deg)`;
  $('#rateTxt').textContent = fetched ? 'TCMB · 16.09.2026 · 1 € = 47,8231 ₺'
    : (t > 2.2 ? 'Kur alınıyor…' : 'Kur bekleniyor');
  $('#rateRow').style.background = fetched ? 'rgba(27,127,75,.09)' : 'rgba(15,76,117,.07)';
  const cp = eio(seg(t, 3.6, 5.1));
  $('#tlVal').textContent = money(23911.55 * cp);
  $('#kvRate').textContent = fetched ? '47,8231' : '—';
  const hl = seg(t, 5.1, 5.9);
  const sc2 = hl > 0 && hl < 1 ? 1 + .022 * Math.sin(hl * Math.PI * 2) : 1;
  const card = $('#tlCard');
  card.style.transform = `translateY(${(1 - eo(seg(t, .5, 1.0))) * 40}px) scale(${sc2})`;
  card.style.boxShadow = hl > 0 && hl < 1 ? `0 0 0 ${3 * Math.sin(hl * Math.PI)}px rgba(15,76,117,.25)` : 'none';
}

function sExp(t, d) {
  showPhone(1, PH_Y, PH_S);
  appSwap('app-exp', seg(t, 0, .5));
  stagger($$('#app-exp .an'), t, .3, .1, .5, 40);
  $$('#expList .ex').forEach((el, i) => {
    const p = seg(t, .85 + i * .3, 1.45 + i * .3);
    el.style.opacity = p;
    el.style.transform = `translateX(${(1 - eo(p)) * 60}px)`;
  });
  const p1 = eio(seg(t, 2.5, 3.4));
  $('#expSum').textContent = money(4017.76 * p1);
  const p2 = eio(seg(t, 3.5, 4.9));
  $('#expTotal').textContent = money(lerp(23911.55, 27929.31, p2));
  const tap = seg(t, 5.6, 6.0);
  const s = tap > 0 && tap < 1 ? 1 - .04 * Math.sin(tap * Math.PI) : 1;
  $('#expBtn').style.transform = `translateY(${(1 - eo(seg(t, 1.1, 1.6))) * 40}px) scale(${s})`;
  $('#expScroll').style.transform = `translateY(${-72 * eio(seg(t, 5.0, 5.8))}px)`;
}

function sPdf(t, d) {
  const out = seg(t, 0, .7);
  showPhone(1 - out, lerp(PH_Y, PH_Y + 180, eio(out)), lerp(PH_S, PH_S * .84, eio(out)));
  appSwap('app-exp', 1 - out);
  scPdf.style.opacity = cl(seg(t, .35, .8) - seg(t, d - .6, d));
  const pages = $$('.pdfpage');
  pages.forEach((el, i) => {
    const p = eo(seg(t, .5 + i * .3, 1.5 + i * .3));
    const X = -232 + i * 116, Y = 140 - i * 56, R = -7.5 + i * 3.6;
    const drift = Math.sin((t * .5 + i * .7) * Math.PI) * 9;
    el.style.zIndex = i + 1;
    el.style.opacity = cl(p * 1.5);
    el.style.transform = `translate(-50%,-50%) translate(${lerp(X + 240, X, p)}px,${lerp(Y + 620, Y + drift, p)}px) rotate(${lerp(R + 14, R, p)}deg) scale(${lerp(.86, 1, p)})`;
  });
  const sp = eio(seg(t, 4.6, 7.6));
  $('#pdfStack').style.transform = `translate(-50%,-50%) scale(${lerp(1, 1.1, sp)}) translateY(${lerp(0, -40, sp)}px)`;
}

function sFin(t, d) {
  const inp = seg(t, 0, .85);
  showPhone(cl(inp * 1.5), lerp(PH_Y + 180, PH_Y, eo(inp)), lerp(PH_S * .84, PH_S, eo(inp)));
  appSwap('app-fin', inp);
  stagger($$('#app-fin .an'), t, .5, .11, .55, 40);
  $$('#finBars u').forEach((el, i) => {
    const p = eo(seg(t, 1.9 + i * .1, 2.6 + i * .1));
    el.style.height = (+el.dataset.h * p) + '%';
    if (i === 5) el.style.background = 'linear-gradient(180deg,#FFC24B,#B26A00)';
  });
  $('#app-fin .scroll').style.transform = `translateY(${-205 * eio(seg(t, 4.3, 6.4))}px)`;
}

function sMail(t, d) {
  showPhone(1, PH_Y, PH_S);
  appSwap('app-exp', 1);
  $('#expScroll').style.transform = 'translateY(-72px)';
  const dp = seg(t, .25, .85);
  mailDlg.style.opacity = cl(dp * 1.4) - seg(t, 3.1, 3.5);
  mailDlg.querySelector('.dialog').style.transform = `scale(${lerp(.88, 1, eb(dp))})`;
  type($('#mailTo'), MAILTO, seg(t, 1.0, 2.1), Math.floor(t / .48) % 2 === 0);
  const tap = seg(t, 2.6, 3.0);
  $('#mailSend').style.transform = tap > 0 && tap < 1 ? `scale(${1 - .06 * Math.sin(tap * Math.PI)})` : 'scale(1)';
  const tp = seg(t, 3.3, 3.8);
  toast.style.opacity = cl(tp * 1.4) - seg(t, d - .5, d);
  toast.style.transform = `translateY(${(1 - eo(tp)) * 36}px)`;
}

function sOutro(t, d) {
  scOutro.style.opacity = cl(seg(t, .15, .7));
  const ip = seg(t, .1, 1.1);
  const fl = Math.sin(t * Math.PI / 2.2) * 8;
  $('#outIcon').style.opacity = cl(ip * 1.5);
  $('#outIcon').style.transform = `scale(${lerp(.7, 1, eb(ip))}) translateY(${fl}px)`;
  rise($('#outName'), seg(t, .65, 1.3), 34);
  rise($('#outSlog'), seg(t, .95, 1.6), 28);
  stagger($$('#outChips span'), t, 1.35, .065, .5, 24);
  rise($('#outQr'), seg(t, 2.5, 3.3), 36);
}

// ---------------------------------------------------------------- ana döngü
const RUN = { splash: sSplash, dash: sDash, form: sForm, money: sMoney, exp: sExp, pdf: sPdf, fin: sFin, mail: sMail, outro: sOutro };

window.frame = function (t) {
  t = Math.max(0, Math.min(TOTAL - .001, t));
  // sıfırla
  scSplash.style.opacity = 0; scPdf.style.opacity = 0; scOutro.style.opacity = 0;
  phoneWrap.style.opacity = 0; mailDlg.style.opacity = 0; toast.style.opacity = 0;
  hideAll(APPS);

  const s = TL.find(x => t >= x.t0 && t < x.t1) || TL[TL.length - 1];
  const lt = t - s.t0;

  // arka plan: açılışta mor degrade, sonra ana zemin
  bg.style.opacity = cl(seg(t, TL[0].dur - 1.0, TL[0].dur - .1));
  const drift = t * 7;
  $('#bgGrid').style.transform = `translate(${-drift % 60}px,${-drift * .6 % 60}px)`;
  $('#blobA').style.transform = `translate(${Math.sin(t * .17) * 60}px,${Math.cos(t * .13) * 50}px)`;
  $('#blobB').style.transform = `translate(${Math.cos(t * .11) * 70}px,${Math.sin(t * .15) * 60}px)`;

  setHead(s.id, seg(lt, .25, 1.0), seg(lt, s.dur - .6, s.dur));
  brand.style.opacity = cl(seg(t, TL[1].t0 + .4, TL[1].t0 + 1.2)) - seg(t, TL[8].t0 - .4, TL[8].t0 + .2);
  prog.style.width = (t / TOTAL * 1080) + 'px';

  RUN[s.id](lt, s.dur);
};

window.READY = true;
})();
