/* Online Kabin — çizim. Yalnız motorun verdiği kutuları/köşeleri çizer; ölçü hesabı yapmaz. */
window.KabinCizim = (() => {
  const RENK = {
    dikeybolme: '#4cc9f0', yataybolme: '#4cc9f0', raf: '#2ed3a6', arkalik: '#d6a565',
    yataykayit: '#b28dff', dikeykayit: '#b28dff', usttabla: '#e0b070', serbest: '#ff9f6e',
    govde: '#cbd5e1', kapak: '#f8fafc', cekmece: '#f8fafc', cekmece_kasa: '#e2e8f0',
    aski: '#94a3b8', izgara: '#4cc9f0', diger: '#e2e8f0',
  };
  const AD = {
    dikeybolme: 'Dikey bölme', yataybolme: 'Yatay bölme', raf: 'Raf', arkalik: 'Arkalık',
    yataykayit: 'Yatay kayıt', dikeykayit: 'Dikey kayıt', usttabla: 'Üst tabla', kapak: 'Kapak',
    cekmece: 'Çekmece', govde: 'Gövde', serbest: 'Serbest parça',
  };
  const NS = 'http://www.w3.org/2000/svg';
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const sayi = n => (Math.round(n * 10) / 10).toLocaleString('tr-TR');

  function el(tag, a, parent) {
    const e = document.createElementNS(NS, tag);
    for (const k in a) if (a[k] !== undefined && a[k] !== null) e.setAttribute(k, a[k]);
    if (parent) parent.appendChild(e);
    return e;
  }

  function sinir(v) {
    const g = v.govde;
    let x0 = 0, x1 = g.en, y0 = 0, y1 = g.boy;
    for (const p of v.parcalar2b) {
      x0 = Math.min(x0, p.kutu.x1); x1 = Math.max(x1, p.kutu.x2);
      y0 = Math.min(y0, p.kutu.y1); y1 = Math.max(y1, p.kutu.y2);
    }
    return {x0, x1, y0, y1};
  }

  /* 2B ön görünüş. Eksen: X sağa, Y aşağı, 0 gövdenin üstü (motor ile aynı). */
  function ciz2B(svg, v, o = {}) {
    o = Object.assign({etiket: true, detay: true, seciliGoz: [], seciliParca: new Set(), hatali: new Set(),
                       kapakSec: true, gizli: new Set(), odak: null}, o);
    // detay: false → yalnız ana hatlar ve dış ölçüler (bölme / kapak ölçüsü, menteşe yok)
    const g = v.govde;
    const b = o.odak || sinir(v);
    const pad = Math.max(40, (b.x1 - b.x0) * 0.08);
    svg.setAttribute('viewBox', `${b.x0 - pad} ${b.y0 - pad} ${b.x1 - b.x0 + 2 * pad} ${b.y1 - b.y0 + 2 * pad}`);
    svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
    while (svg.firstChild) svg.removeChild(svg.firstChild);
    const fs = Math.max(12, Math.min(g.en, g.boy) / 22);
    svg.style.setProperty('--fs', fs);

    const kat = {};
    for (const ad of ['arka', 'ayak', 'govde', 'ic', 'goz', 'on', 'olcu']) kat[ad] = el('g', {'data-katman': ad}, svg);

    // ayak / baza (gövdenin altında: govde_boyu .. boy)
    const ay = g.boy - g.govde_boyu;
    if (ay > 0 && g.ayaktipi === 'AYAK') {
      const w = Math.min(50, g.en / 8);
      for (const x of [w * 0.6, g.en - w * 1.6])
        el('rect', {x, y: g.govde_boyu, width: w, height: ay, fill: '#64748b', rx: 3}, kat.ayak);
    } else if (ay > 0 && g.ayaktipi === 'BAZA') {
      el('rect', {x: 0, y: g.govde_boyu, width: g.en, height: ay, fill: '#94a3b8'}, kat.ayak);
    }

    for (const p of v.parcalar2b) {
      if (o.gizli.has(p.id)) continue;
      const k = p.kutu;
      const w = Math.max(0.5, k.x2 - k.x1), h = Math.max(0.5, k.y2 - k.y1);
      const secili = o.seciliParca.has(p.id), hatali = o.hatali.has(p.id);
      const parent = p.tur === 'arkalik' ? kat.arka : p.katman === 'on' ? kat.on : p.katman === 'govde' ? kat.govde : kat.ic;
      const grup = el('g', {'data-id': p.id, 'data-tur': p.tur, class: 'parca'}, parent);
      const on = p.katman === 'on';
      el('rect', {
        x: k.x1, y: k.y1, width: w, height: h, fill: RENK[p.tur] || RENK.diger,
        'fill-opacity': on ? (o.kapakSec ? 0.85 : 0.35) : p.tur === 'arkalik' ? 0.35 : 1,
        stroke: hatali ? '#dc2626' : secili ? '#059669' : '#334155',
        'stroke-width': hatali || secili ? 3 : 1, 'vector-effect': 'non-scaling-stroke',
        rx: on ? 2 : 0,
      }, grup);
      if (p.tur === 'kapak' && p.menteseyeri && p.menteseyeri !== 'YOK') {
        // açılış çizgisi (menteşe tarafından karşı köşelere kesik çizgi)
        const m = p.menteseyeri, cx = (k.x1 + k.x2) / 2, cy = (k.y1 + k.y2) / 2;
        const pts = m === 'SOL' ? [[k.x2, k.y1], [k.x1, cy], [k.x2, k.y2]] : m === 'SAG' ? [[k.x1, k.y1], [k.x2, cy], [k.x1, k.y2]]
          : m === 'UST' ? [[k.x1, k.y2], [cx, k.y1], [k.x2, k.y2]] : [[k.x1, k.y1], [cx, k.y2], [k.x2, k.y1]];
        el('polyline', {points: pts.map(q => q.join(',')).join(' '), fill: 'none', stroke: o.detay ? '#94a3b8' : '#cbd5e1',
                        'stroke-dasharray': '6 5', 'vector-effect': 'non-scaling-stroke'}, grup);
        if (o.detay) for (const z of (p.menteseler || [])) {          // menteşe konumları (kapak üstünden)
          const yy = k.y1 + z, xx = m === 'SAG' ? k.x2 - 22 : k.x1 + 22;
          if (m === 'SOL' || m === 'SAG') el('circle', {cx: xx, cy: yy, r: 17.5, fill: 'none', stroke: '#64748b', 'vector-effect': 'non-scaling-stroke'}, grup);
        }
      }
      if ((p.tur === 'kapak' || p.tur === 'cekmece') && (p.kulp || []).length) {
        p.kulp.forEach((kn, i) => {
          const dik = (p.kulp_yon || [])[i] === 'DIKEY';
          const cx = kn.includes('sol') ? k.x1 + (dik ? 40 : 100) : kn.includes('sag') ? k.x2 - (dik ? 40 : 100) : (k.x1 + k.x2) / 2;
          const cy = kn.includes('ust') ? k.y1 + (dik ? 110 : 40) : kn.includes('alt') ? k.y2 - (dik ? 110 : 40) : (k.y1 + k.y2) / 2;
          const [dx, dy] = dik ? [0, 64] : [64, 0];
          el('line', {x1: cx - dx, y1: cy - dy, x2: cx + dx, y2: cy + dy, stroke: '#475569', 'stroke-width': 4,
                      'stroke-linecap': 'round', 'vector-effect': 'non-scaling-stroke'}, grup);
        });
      }
      if (o.etiket && o.detay && on && o.kapakSec && w > fs * 4 && h > fs * 2) {
        el('text', {x: (k.x1 + k.x2) / 2, y: (k.y1 + k.y2) / 2, 'font-size': fs * 0.8, 'text-anchor': 'middle',
                    'dominant-baseline': 'middle', fill: '#334155', 'pointer-events': 'none'}, grup)
          .textContent = `${sayi(h)} × ${sayi(w)}`;
      }
    }

    // gözler (tıklanır) + ölçü etiketleri
    v.gozler.forEach((z, i) => {
      const sec = o.seciliGoz.some(s => s.i === i);
      el('rect', {x: z.x1, y: z.y1, width: z.x2 - z.x1, height: z.y2 - z.y1, class: 'goz', 'data-goz': i,
                  fill: sec ? 'rgba(16,185,129,.22)' : 'rgba(0,0,0,0)',
                  stroke: sec ? '#059669' : 'none', 'stroke-width': 2, 'vector-effect': 'non-scaling-stroke'}, kat.goz);
      if (!o.etiket || !o.detay) return;
      const w = z.x2 - z.x1, h = z.y2 - z.y1;
      if (w > fs * 2.5) {
        const t = el('text', {x: (z.x1 + z.x2) / 2, y: z.y1 + fs * 1.1, 'font-size': fs, 'text-anchor': 'middle',
                              class: 'olcu', 'data-goz': i, 'data-yon': 'x', fill: z.kilit.x ? '#b45309' : '#0f766e'}, kat.olcu);
        t.textContent = sayi(w) + (z.kilit.x ? ' 🔒' : '');
      }
      if (h > fs * 2.5) {
        // döndürülmüş yazının harfleri x'in soluna taşar: göz içinde kalsın, parçayı örtmesin
        const x = z.x1 + fs * 1.15, y = (z.y1 + z.y2) / 2;
        const t = el('text', {x, y, 'font-size': fs, 'text-anchor': 'middle', class: 'olcu', 'data-goz': i,
                              'data-yon': 'y', fill: z.kilit.y ? '#b45309' : '#0f766e',
                              transform: `rotate(-90 ${x} ${y})`}, kat.olcu);
        t.textContent = sayi(h) + (z.kilit.y ? ' 🔒' : '');
      }
    });
    kat.on.style.pointerEvents = o.kapakSec ? '' : 'none';

    if (o.etiket) {    // dış ölçüler
      el('text', {x: g.en / 2, y: -pad * 0.35, 'font-size': fs, 'text-anchor': 'middle', fill: '#475569'}, kat.olcu).textContent = sayi(g.en);
      const x = g.en + pad * 0.45, y = g.boy / 2;
      el('text', {x, y, 'font-size': fs, 'text-anchor': 'middle', fill: '#475569', transform: `rotate(-90 ${x} ${y})`}, kat.olcu).textContent = sayi(g.boy);
    }
  }

  /* 3B: motorun panel köşeleri (8 nokta; X sağa, Y derinliğe, Z yukarı). */
  class Kabin3B {
    constructor(kap, tikla) {
      this.kap = kap; this.tikla = tikla; this.kapakGizli = false;
      if (!window.THREE) { kap.innerHTML = '<p style="padding:1rem;color:#64748b">3B görünüm yüklenemedi.</p>'; return; }
      const T = THREE;
      this.r = new T.WebGLRenderer({antialias: true, preserveDrawingBuffer: true});
      this.r.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
      this.r.setClearColor(0xf1f5f9);
      kap.appendChild(this.r.domElement);
      this.s = new T.Scene();
      this.c = new T.PerspectiveCamera(35, 1, 10, 100000);
      this.s.add(new T.AmbientLight(0xffffff, 0.75));
      const d = new T.DirectionalLight(0xffffff, 0.55); d.position.set(1, 2, 3); this.s.add(d);
      this.grup = new T.Group(); this.s.add(this.grup);
      this.ctl = T.OrbitControls ? new T.OrbitControls(this.c, this.r.domElement) : null;
      this.elle = false;                     // kullanıcı döndürdüyse kamerayı yeniden sığdırma
      if (this.ctl) this.ctl.addEventListener('start', () => { this.elle = true; });
      this.ray = new T.Raycaster();
      this.r.domElement.addEventListener('click', e => this._tik(e));
      new ResizeObserver(() => this.boyut()).observe(kap);
      const don = () => { requestAnimationFrame(don); this.ctl && this.ctl.update(); this.r.render(this.s, this.c); };
      don();
    }
    boyut() {
      if (!this.r) return;
      const w = this.kap.clientWidth || 300, h = this.kap.clientHeight || 300;
      this.r.setSize(w, h); this.c.aspect = w / h; this.c.updateProjectionMatrix();
      if (this.olcu && !this.elle) this.sigdir();
    }
    sigdir() {                               // dolabı (çevreleyen küre) görüş alanına sığdır
      const [W, H, D] = this.olcu, R = 0.5 * Math.hypot(W, H, D);
      const v = THREE.MathUtils.degToRad(this.c.fov) / 2, h = Math.atan(Math.tan(v) * this.c.aspect);
      const uz = R / Math.sin(Math.min(v, h)) * 1.08;
      const yon = new THREE.Vector3(0.45, 0.32, 1).normalize();
      this.c.position.copy(yon.multiplyScalar(uz));
      if (this.ctl) { this.ctl.target.set(0, 0, 0); this.ctl.update(); } else this.c.lookAt(0, 0, 0);
    }
    guncelle(v, o = {}) {
      if (!this.r) return;
      const T = THREE, g = v.govde;
      this.grup.clear();
      const F = [[0, 1, 2, 3], [4, 5, 6, 7], [0, 1, 5, 4], [1, 2, 6, 5], [2, 3, 7, 6], [3, 0, 4, 7]];
      for (const p of v.parcalar3b) {
        const on = p.tur === 'kapak' || p.tur === 'cekmece';
        if ((on && this.kapakGizli) || (o.gizli && (o.gizli.has(p.eleman) || o.gizli.has(p.id)))) continue;
        const k = p.koseler.map(([x, y, z]) => [x - g.en / 2, z - g.boy / 2, -(y - g.derinlik / 2)]);
        const pos = [];
        for (const f of F) for (const i of [f[0], f[1], f[2], f[0], f[2], f[3]]) pos.push(...k[i]);
        const geo = new T.BufferGeometry();
        geo.setAttribute('position', new T.Float32BufferAttribute(pos, 3));
        geo.computeVertexNormals();
        const secili = o.seciliParca && (o.seciliParca.has(p.eleman) || o.seciliParca.has(p.id));
        const hatali = o.hatali && o.hatali.has(p.id);
        const mat = new T.MeshLambertMaterial({
          color: hatali ? 0xef4444 : secili ? 0x10b981 : new T.Color(RENK[p.tur] || RENK.diger),
          side: T.DoubleSide, transparent: on, opacity: on ? 0.8 : 1,
        });
        const m = new T.Mesh(geo, mat);
        m.userData = {id: p.eleman || p.id};
        this.grup.add(m);
        const kenar = new T.LineSegments(new T.EdgesGeometry(geo, 1), new T.LineBasicMaterial({color: 0x334155}));
        this.grup.add(kenar);
      }
      const yeniOlcu = !this.olcu || this.olcu[0] !== g.en || this.olcu[1] !== g.boy || this.olcu[2] !== g.derinlik;
      this.olcu = [g.en, g.boy, g.derinlik];
      if (o.kameraSifirla) this.elle = false;
      this.boyut();
      if (yeniOlcu && !this.elle) this.sigdir();
    }
    _tik(e) {
      if (!this.tikla) return;
      const b = this.r.domElement.getBoundingClientRect();
      const p = new THREE.Vector2(((e.clientX - b.left) / b.width) * 2 - 1, -((e.clientY - b.top) / b.height) * 2 + 1);
      this.ray.setFromCamera(p, this.c);
      const hit = this.ray.intersectObjects(this.grup.children.filter(x => x.isMesh))[0];
      this.tikla(hit ? hit.object.userData.id : null, e);
    }
  }

  return {ciz2B, Kabin3B, RENK, AD, esc, sayi, sinir};
})();
