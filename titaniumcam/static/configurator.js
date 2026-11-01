// Ortak ürün konfigüratörü (3B + opsiyonlar + fiyat).
// Sayfa, window.CFG = { products, colors, priceUrl, saveUrl, mode } sağlar.
// mode: 'admin' (yönetici) | 'public' (müşteri talep)

const PRODUCTS = CFG.products, COLORS = CFG.colors;
const HEX = {}; COLORS.forEach(c => HEX[c.id] = c.hex);
const PT = 0.018;
let prod = null, params = {}, showDoors = true;
let scene, camera, renderer, controls, unit;
const c0 = COLORS[0] ? COLORS[0].id : null;

function initThree() {
  const el = document.getElementById('scene');
  scene = new THREE.Scene(); scene.background = new THREE.Color(0xeef2f7);
  camera = new THREE.PerspectiveCamera(42, el.clientWidth / el.clientHeight, 0.05, 100);
  renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setSize(el.clientWidth, el.clientHeight); renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  el.appendChild(renderer.domElement);
  controls = new THREE.OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true; controls.maxPolarAngle = Math.PI * 0.5;

  scene.add(new THREE.HemisphereLight(0xffffff, 0xb6bcc6, 0.85));
  scene.add(new THREE.AmbientLight(0xffffff, 0.18));
  const key = new THREE.DirectionalLight(0xffffff, 0.62); key.position.set(4.5, 8, 6); key.castShadow = true;
  key.shadow.mapSize.set(2048, 2048); key.shadow.bias = -0.0004;
  const sc = key.shadow.camera; sc.near = 0.5; sc.far = 40; sc.left = -4; sc.right = 4; sc.top = 4; sc.bottom = -4;
  scene.add(key);
  const fill = new THREE.DirectionalLight(0xffffff, 0.22); fill.position.set(-5, 3, -4); scene.add(fill);

  const ground = new THREE.Mesh(new THREE.PlaneGeometry(60, 60), new THREE.MeshStandardMaterial({ color: 0xe4e9ef, roughness: 0.97 }));
  ground.rotation.x = -Math.PI / 2; ground.position.y = -0.003; ground.receiveShadow = true; scene.add(ground);
  scene.add(new THREE.GridHelper(12, 24, 0xd2d9e2, 0xe2e8f0));

  window.addEventListener('resize', () => { camera.aspect = el.clientWidth / el.clientHeight; camera.updateProjectionMatrix(); renderer.setSize(el.clientWidth, el.clientHeight); });
  (function loop() { requestAnimationFrame(loop); controls.update(); renderer.render(scene, camera); })();
}
function hx(id) { return parseInt((HEX[id] || '#d8b88a').slice(1), 16); }
function darken(h, f) { return ((h >> 16 & 255) * f << 16) | ((h >> 8 & 255) * f << 8) | ((h & 255) * f); }
function mat(c, o) { return new THREE.MeshStandardMaterial(Object.assign({ color: c, roughness: 0.78, metalness: 0.04 }, o || {})); }
const EDGE_MAT = new THREE.LineBasicMaterial({ color: 0x1f2937, transparent: true, opacity: 0.14 });
function box(g, w, h, d, x, y, z, m) {
  w = Math.max(w, 0.004); h = Math.max(h, 0.004); d = Math.max(d, 0.004);
  const geo = new THREE.BoxGeometry(w, h, d);
  const me = new THREE.Mesh(geo, m); me.position.set(x, y, z); me.castShadow = true; me.receiveShadow = true; g.add(me);
  const big = [w, h, d].filter(v => v > 0.08).length;
  if (big >= 2 && !m.transparent) { const ed = new THREE.LineSegments(new THREE.EdgesGeometry(geo), EDGE_MAT); ed.position.set(x, y, z); g.add(ed); }
  return me;
}
function resetView() { const H = params.H / 1000, W = params.W / 1000; camera.position.set(W * 0.95, H * 0.6, Math.max(W, 1.2) * 1.6); controls.target.set(0, H / 2, 0); controls.update(); }

function addDoor(g, cx, cy, ww, dh, front, i) {
  const dt = params.door_type, doorC = hx(params.door_color), door2C = hx(params.door_color2);
  const z = (dt === 'surgu') ? (front - (i % 2) * 0.028) : front, w = (dt === 'surgu') ? ww * 1.06 : ww;
  if (dt === 'camli') {
    const fr = mat(doorC), t = 0.06;
    box(g, w, t, PT, cx, cy + dh / 2 - t / 2, z, fr); box(g, w, t, PT, cx, cy - dh / 2 + t / 2, z, fr);
    box(g, t, dh, PT, cx - w / 2 + t / 2, cy, z, fr); box(g, t, dh, PT, cx + w / 2 - t / 2, cy, z, fr);
    box(g, w - 2 * t, dh - 2 * t, 0.006, cx, cy, z, mat(0xbcd6e6, { transparent: true, opacity: 0.32, roughness: 0.08 }));
  } else if (dt === 'desenli') {
    box(g, w, dh, PT, cx, cy, z, mat(doorC));
    for (let l = -1; l <= 1; l++) box(g, 0.02, dh - 0.06, 0.006, cx + l * (w / 4), cy, z + PT / 2, mat(darken(doorC, 0.8)));
  } else {
    box(g, w, dh, PT, cx, cy, z, mat(doorC));
    if (params.two_tone) box(g, w * 0.7, dh * 0.82, 0.006, cx, cy, z + PT / 2, mat(door2C));
  }
  if (params.handles) { const hxp = cx + (i % 2 ? -1 : 1) * (ww / 2 - 0.04); box(g, 0.022, Math.min(0.14, dh * 0.5), 0.028, hxp, cy, z + PT / 2 + 0.012, mat(0x565b62, { metalness: 0.35, roughness: 0.42 })); }
}

function buildCabinet() {
  const g = new THREE.Group();
  const W = params.W / 1000, H = params.H / 1000, D = params.D / 1000;
  const bt = params.base_type, legH = bt === 'ayakli' ? 0.10 : (bt === 'bazali' ? 0.08 : 0);
  const cB = legH, cH = H - legH;
  const bodyC = hx(params.body_color);
  const bodyMat = mat(bodyC), backMat = mat(darken(bodyC, 0.85)), metalMat = mat(0x565b62, { metalness: 0.35, roughness: 0.42 });

  box(g, PT, cH, D, -W / 2 + PT / 2, cB + cH / 2, 0, bodyMat);
  box(g, PT, cH, D, W / 2 - PT / 2, cB + cH / 2, 0, bodyMat);
  box(g, W, PT, D, 0, cB + cH - PT / 2, 0, bodyMat);
  box(g, W, PT, D, 0, cB + PT / 2, 0, bodyMat);
  if (params.back) box(g, W, cH, PT, 0, cB + cH / 2, -D / 2 + PT / 2, backMat);
  if (bt === 'bazali') box(g, W - 0.04, 0.08, D - 0.06, 0, 0.04, 0, mat(darken(bodyC, 0.7)));
  if (bt === 'ayakli') { const lx = W / 2 - 0.06, lz = D / 2 - 0.06;[[-lx, -lz], [lx, -lz], [-lx, lz], [lx, lz]].forEach(([x, z]) => box(g, 0.045, legH, 0.045, x, legH / 2, z, metalMat)); }

  const front = D / 2 - PT / 2;
  if (prod.layout === 'wardrobe') {
    box(g, PT, cH, D, 0, cB + cH / 2, 0, bodyMat);
    for (let s = 0; s < params.shelves; s++) { const y = cB + PT + (s + 1) * (cH - 2 * PT) / (params.shelves + 1); box(g, W / 2 - PT, PT, D - PT, W / 4, y, 0, bodyMat); }
    if (params.hanging) box(g, W / 2 - PT, 0.03, 0.03, -W / 4, cB + cH - 0.22, 0, metalMat);
    for (let k = 0; k < params.drawers; k++) box(g, W / 2 - PT - 0.02, 0.16, PT, -W / 4, cB + 0.11 + k * 0.18, front, mat(hx(params.door_color)));
    if (showDoors && params.door_type !== 'kapaksiz') { const n = Math.max(params.doors, 1), bw = W / n; for (let i = 0; i < n; i++) addDoor(g, -W / 2 + i * bw + bw / 2, cB + cH / 2, bw - 0.012, cH - 0.012, front, i); }
  } else {
    const doorBays = params.doors, drawerBay = params.drawers > 0 ? 1 : 0;
    const sections = Math.max(doorBays + drawerBay, 1), bw = W / sections;
    for (let i = 1; i < sections; i++) box(g, PT, cH, D, -W / 2 + i * bw, cB + cH / 2, 0, bodyMat);
    for (let s = 0; s < params.shelves; s++) { const y = cB + PT + (s + 1) * (cH - 2 * PT) / (params.shelves + 1); box(g, W - 2 * PT, PT, D - PT, 0, y, 0, bodyMat); }
    if (drawerBay) {
      const dx = -W / 2 + (sections - 1) * bw + bw / 2;
      for (let k = 0; k < params.drawers; k++) { const dh = (cH - 0.02) / params.drawers; box(g, bw - 0.02, dh - 0.01, PT, dx, cB + 0.01 + dh * (k + 0.5), front, mat(hx(params.door_color))); if (params.handles) box(g, bw * 0.4, 0.02, 0.03, dx, cB + 0.01 + dh * (k + 0.85), front + PT / 2 + 0.01, metalMat); }
    }
    if (showDoors && params.door_type !== 'kapaksiz') for (let i = 0; i < doorBays; i++) addDoor(g, -W / 2 + i * bw + bw / 2, cB + cH / 2, bw - 0.012, cH - 0.012, front, i);
  }
  return g;
}

function rebuild() { const sd = document.getElementById('showDoors'); showDoors = sd ? sd.checked : true; if (unit) scene.remove(unit); unit = buildCabinet(); scene.add(unit); refreshPrice(); }
let _pt = null;
function refreshPrice() {
  clearTimeout(_pt); _pt = setTimeout(async () => {
    const r = await fetch(CFG.priceUrl, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(params) });
    const d = await r.json();
    document.getElementById('price').textContent = Math.round(d.total).toLocaleString('tr-TR');
    const bd = document.getElementById('breakdown');
    if (bd) bd.innerHTML = d.items.filter(x => x.value > 0).map(x => `<div class="flex justify-between"><span>${x.label}</span><b>${x.value.toLocaleString('tr-TR')} TL</b></div>`).join('');
  }, 150);
}

function setNum(id, arr, val) { const e = document.getElementById(id); if (arr) { e.min = arr[0]; e.max = arr[1]; } e.value = val; }
function applyProduct(p) {
  prod = p; params = {
    W: p.dims.W[2], H: p.dims.H[2], D: p.dims.D[2], doors: p.opt.doors[2], shelves: p.opt.shelves[2], drawers: p.opt.drawers[2],
    door_type: 'duz', base_type: 'bazali', back: 1, hanging: p.opt.hanging ? 1 : 0, handles: 1, two_tone: 0,
    body_color: c0, door_color: c0, door_color2: c0
  };
  document.getElementById('prodName').textContent = p.name;
  setNum('W', p.dims.W, params.W); setNum('H', p.dims.H, params.H); setNum('D', p.dims.D, params.D);
  setNum('doors', p.opt.doors, params.doors); setNum('shelves', p.opt.shelves, params.shelves); setNum('drawers', p.opt.drawers, params.drawers);
  document.getElementById('door_type').value = 'duz'; document.getElementById('base_type').value = 'bazali';
  document.getElementById('back').checked = true; document.getElementById('hanging').checked = !!params.hanging; document.getElementById('handles').checked = true; document.getElementById('two_tone').checked = false;
  document.getElementById('row_hanging').style.display = p.opt.hanging ? 'flex' : 'none';
  document.getElementById('row_twotone').style.display = p.opt.two_tone ? 'flex' : 'none';
  document.getElementById('door2Wrap').classList.add('hidden');
  document.querySelectorAll('#prodBar button').forEach(b => b.className = b.dataset.id === p.id ? 'px-3 py-2 rounded bg-blue-600 text-white text-sm' : 'px-3 py-2 rounded bg-white border text-sm');
  swatches('bodyColors', 'body_color'); swatches('doorColors', 'door_color'); swatches('door2Colors', 'door_color2');
  rebuild(); resetView();
}
function buildProdBar() { const bar = document.getElementById('prodBar'); PRODUCTS.forEach(p => { const b = document.createElement('button'); b.textContent = p.name; b.dataset.id = p.id; b.className = 'px-3 py-2 rounded bg-white border text-sm'; b.onclick = () => applyProduct(p); bar.appendChild(b); }); }
function swatches(cid, key) { const cw = document.getElementById(cid); cw.innerHTML = ''; COLORS.forEach(c => { const b = document.createElement('button'); b.style.background = c.hex; b.title = c.name + (c.premium_pct ? ` (+%${c.premium_pct})` : ''); b.className = 'w-8 h-8 rounded-full border-2'; b.style.borderColor = (c.id === params[key]) ? '#2563eb' : '#cbd5e1'; b.onclick = () => { params[key] = c.id;[...cw.children].forEach(x => x.style.borderColor = '#cbd5e1'); b.style.borderColor = '#2563eb'; rebuild(); }; cw.appendChild(b); }); }

function bindNum(id) { document.getElementById(id).addEventListener('input', e => { params[id] = +e.target.value || 0; rebuild(); }); }
function bindSel(id) { document.getElementById(id).addEventListener('change', e => { params[id] = e.target.value; rebuild(); }); }
function bindChk(id) { document.getElementById(id).addEventListener('change', e => { params[id] = e.target.checked ? 1 : 0; if (id === 'two_tone') document.getElementById('door2Wrap').classList.toggle('hidden', !e.target.checked); rebuild(); }); }

function loadQuote(pid, p) {
  const found = PRODUCTS.find(x => x.id === pid) || PRODUCTS[0]; applyProduct(found); params = Object.assign(params, p);
  ['W', 'H', 'D', 'doors', 'shelves', 'drawers'].forEach(k => { if (document.getElementById(k)) document.getElementById(k).value = params[k]; });
  document.getElementById('door_type').value = params.door_type; document.getElementById('base_type').value = params.base_type;
  ['back', 'hanging', 'handles', 'two_tone'].forEach(k => document.getElementById(k).checked = !!params[k]);
  document.getElementById('door2Wrap').classList.toggle('hidden', !params.two_tone);
  swatches('bodyColors', 'body_color'); swatches('doorColors', 'door_color'); swatches('door2Colors', 'door_color2');
  rebuild(); resetView(); window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function saveQuote() {
  let body;
  if (CFG.mode === 'public') {
    const name = (document.getElementById('c_name').value || '').trim();
    const phone = (document.getElementById('c_phone').value || '').trim();
    if (!name || !phone) { alert('Lütfen adınızı ve telefonunuzu girin.'); return; }
    body = { product: prod.id, params, contact: { name, phone } };
  } else {
    body = { product: prod.id, name: document.getElementById('q_name').value, customer: document.getElementById('q_customer').value, params };
  }
  const r = await fetch(CFG.saveUrl, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const d = await r.json();
  if (CFG.mode === 'public') {
    if (d.ok) { document.getElementById('publicForm').classList.add('hidden'); const t = document.getElementById('thanks'); t.classList.remove('hidden'); document.getElementById('thanksPrice').textContent = Math.round(d.price).toLocaleString('tr-TR'); window.scrollTo({ top: 0, behavior: 'smooth' }); }
    else alert('Gönderilemedi, tekrar deneyin.');
  } else {
    const m = document.getElementById('saveMsg');
    if (d.ok) { m.innerHTML = '<span class="text-emerald-700">Kaydedildi (' + Math.round(d.price).toLocaleString('tr-TR') + ' TL). Yenileniyor...</span>'; setTimeout(() => location.reload(), 900); }
    else m.innerHTML = '<span class="text-red-600">Kaydedilemedi</span>';
  }
}

// başlat
initThree();
['W', 'H', 'D', 'doors', 'shelves', 'drawers'].forEach(bindNum);
['door_type', 'base_type'].forEach(bindSel);
['back', 'hanging', 'handles', 'two_tone'].forEach(bindChk);
const _sd = document.getElementById('showDoors'); if (_sd) _sd.addEventListener('change', rebuild);
buildProdBar();
if (PRODUCTS.length) applyProduct(PRODUCTS[0]);
