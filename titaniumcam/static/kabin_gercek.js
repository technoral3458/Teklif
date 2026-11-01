/* Online Kabin — gerçekçi 3B görünüm (müşteri ekranı).
   Yalnız motorun verdiği parça köşelerini çizer; ölçü hesabı yapmaz.
   Malzeme görünümü (renk / doku) yöneticinin Tanımlar ekranındaki ayardan gelir. */
window.KabinGercek = (() => {
  const T = window.THREE;
  const lin = hex => new T.Color(hex).convertSRGBToLinear();

  // ── ahşap doku (tekrarlanır, 1 m × 1 m; damar X yönünde) ──
  function rastgele(tohum) { let s = tohum >>> 0 || 1; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }
  function gurultu1(L, tohum) {                       // periyodik 1B değer gürültüsü
    const r = rastgele(tohum), v = Array.from({length: L}, () => r());
    return x => { const i = Math.floor(x), f = x - i, a = v[((i % L) + L) % L], b = v[(((i + 1) % L) + L) % L];
                  const t = f * f * (3 - 2 * f); return a + (b - a) * t; };
  }
  const _dokular = {};
  function ahsapDoku(renk) {
    if (_dokular[renk]) return _dokular[renk];
    const N = 1024, c = document.createElement('canvas'); c.width = c.height = N;
    const x = c.getContext('2d'), img = x.createImageData(N, N), d = img.data;
    const taban = new T.Color(renk), R = taban.r * 255, G = taban.g * 255, B = taban.b * 255;
    const n1 = gurultu1(64, 7), n2 = gurultu1(256, 11), n3 = gurultu1(16, 3), gozenek = rastgele(5);
    const dalga = new Float32Array(N);
    for (let i = 0; i < N; i++) dalga[i] = 9 * Math.sin(2 * Math.PI * i / N * 2 + 1.3) + 4 * Math.sin(2 * Math.PI * i / N * 5 + 0.4);
    for (let py = 0; py < N; py++) {
      for (let px = 0; px < N; px++) {
        const yy = py + dalga[px] + 6 * n3(px / 64);
        const halka = n1(yy / 16);                                  // yıllık halka bantları
        const ince = n2(yy / 4);                                    // ince damar
        let s = 0.9 + 0.12 * (halka - 0.5) + 0.06 * (ince - 0.5);
        s -= 0.16 * Math.pow(Math.abs(Math.sin(halka * 9.0)), 14);  // koyu damar çizgileri
        if (gozenek() < 0.012) s -= 0.07;                           // gözenek
        const i = (py * N + px) * 4;
        d[i] = Math.min(255, R * s); d[i + 1] = Math.min(255, G * s); d[i + 2] = Math.min(255, B * s); d[i + 3] = 255;
      }
    }
    x.putImageData(img, 0, 0);
    const tex = new T.CanvasTexture(c);
    tex.wrapS = tex.wrapT = T.RepeatWrapping; tex.encoding = T.sRGBEncoding; tex.anisotropy = 8;
    return (_dokular[renk] = tex);
  }

  // ── kutu geometrisi: yüz başına normal + damar yönünde metre cinsinden UV ──
  function kutuGeo(a, b, damarEkseni, kayma) {
    const pos = [], nor = [], uv = [], idx = [];
    const yuz = (eksen, isaret) => {
      const [u, v] = [0, 1, 2].filter(e => e !== eksen);
      // damar: parçanın uzun ekseni bu yüzdeyse U onun boyunca
      const [ue, ve] = (v === damarEkseni) ? [v, u] : [u, v];
      const sabit = isaret > 0 ? b[eksen] : a[eksen], n = [0, 0, 0]; n[eksen] = isaret;
      const k = pos.length / 3;
      for (const [s, t] of [[0, 0], [1, 0], [1, 1], [0, 1]]) {
        const p = [0, 0, 0]; p[eksen] = sabit;
        p[ue] = s ? b[ue] : a[ue]; p[ve] = t ? b[ve] : a[ve];
        pos.push(...p); nor.push(...n); uv.push(p[ue] / 1000 + kayma, p[ve] / 1000 + kayma * 0.37);
      }
      // dışa bakan sarım
      const sag = ((ue - eksen + 3) % 3 === 1) === (isaret > 0);
      idx.push(...(sag ? [k, k + 1, k + 2, k, k + 2, k + 3] : [k, k + 2, k + 1, k, k + 3, k + 2]));
    };
    for (let e = 0; e < 3; e++) { yuz(e, 1); yuz(e, -1); }
    const g = new T.BufferGeometry();
    g.setAttribute('position', new T.Float32BufferAttribute(pos, 3));
    g.setAttribute('normal', new T.Float32BufferAttribute(nor, 3));
    g.setAttribute('uv', new T.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    return g;
  }
  const ozet = s => { let h = 0; for (const ch of String(s)) h = (h * 31 + ch.charCodeAt(0)) | 0; return (Math.abs(h) % 1000) / 1000; };

  class KabinGercek {
    constructor(kap, tikla) {
      this.kap = kap; this.tikla = tikla; this.gorunum = {};
      if (!T) { kap.innerHTML = '<p style="padding:1rem;color:#64748b">3B görünüm yüklenemedi.</p>'; return; }
      const r = this.r = new T.WebGLRenderer({antialias: true, alpha: true, preserveDrawingBuffer: true});
      r.setClearColor(0x000000, 0);
      kap.style.background = 'radial-gradient(120% 90% at 50% 38%, #fbfaf8 0%, #efebe5 55%, #ddd7ce 100%)';  // stüdyo fonu
      r.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
      r.outputEncoding = T.sRGBEncoding; r.toneMapping = T.ACESFilmicToneMapping; r.toneMappingExposure = 0.92;
      r.shadowMap.enabled = true; r.shadowMap.type = T.PCFSoftShadowMap;
      kap.appendChild(r.domElement);
      const s = this.s = new T.Scene();
      if (T.RoomEnvironment) {
        const pm = new T.PMREMGenerator(r);
        s.environment = pm.fromScene(new T.RoomEnvironment(), 0.04).texture;
      }
      s.add(new T.HemisphereLight(0xffffff, 0xcfc8bd, s.environment ? 0.35 : 0.85));
      const gun = this.gun = new T.DirectionalLight(0xfff6ea, s.environment ? 1.6 : 1.1);
      gun.castShadow = true; gun.shadow.mapSize.set(2048, 2048);
      gun.shadow.bias = -0.0005; gun.shadow.normalBias = 2.5; gun.shadow.radius = 8;
      s.add(gun, gun.target);
      // stüdyo: görünmez zemin yalnız gölgeyi alır + dolabın altında yumuşak temas gölgesi
      this.zemin = new T.Mesh(new T.PlaneGeometry(12000, 12000), new T.ShadowMaterial({opacity: 0.07}));
      this.zemin.rotation.x = -Math.PI / 2; this.zemin.receiveShadow = true;
      const cg = document.createElement('canvas'); cg.width = cg.height = 128;
      const cx = cg.getContext('2d'), gr = cx.createRadialGradient(64, 64, 4, 64, 64, 64);
      gr.addColorStop(0, 'rgba(0,0,0,0.55)'); gr.addColorStop(0.55, 'rgba(0,0,0,0.22)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
      cx.fillStyle = gr; cx.fillRect(0, 0, 128, 128);
      this.temas = new T.Mesh(new T.PlaneGeometry(1, 1), new T.MeshBasicMaterial({map: new T.CanvasTexture(cg), transparent: true, depthWrite: false}));
      this.temas.rotation.x = -Math.PI / 2; this.temas.renderOrder = -1;
      s.add(this.zemin, this.temas);
      this.grup = new T.Group(); s.add(this.grup);
      this.c = new T.PerspectiveCamera(32, 1, 20, 60000);
      this.ctl = T.OrbitControls ? new T.OrbitControls(this.c, r.domElement) : null;
      if (this.ctl) {
        Object.assign(this.ctl, {enableDamping: true, dampingFactor: 0.09, enablePan: false,
                                 minPolarAngle: 0.95, maxPolarAngle: Math.PI / 2 - 0.03,   // tepeden bakılmaz
                                 minAzimuthAngle: -1.05, maxAzimuthAngle: 1.05, rotateSpeed: 0.7});
        this.ctl.addEventListener('start', () => { this.elle = true; });
      }
      this.elle = false;
      this.ray = new T.Raycaster();
      let bas = null;
      r.domElement.addEventListener('pointerdown', e => { bas = [e.clientX, e.clientY]; });
      r.domElement.addEventListener('pointerup', e => {     // sürükleme değil, dokunma ise seç
        if (bas && Math.hypot(e.clientX - bas[0], e.clientY - bas[1]) < 6) this._tik(e);
        bas = null;
      });
      this._mats = {};
      new ResizeObserver(() => this.boyut()).observe(kap);
      const don = () => {
        requestAnimationFrame(don);
        if (!this.kap.offsetParent) return;               // gizliyken çizme (pil)
        this.ctl && this.ctl.update(); r.render(s, this.c);
      };
      don();
    }

    boyut() {
      if (!this.r) return;
      const w = this.kap.clientWidth || 300, h = this.kap.clientHeight || 300;
      this.r.setSize(w, h); this.c.aspect = w / h; this.c.updateProjectionMatrix();
      if (this.olcu && !this.elle) this.sigdir();
    }

    sigdir() {
      const [W, H, D] = this.olcu, R = 0.5 * Math.hypot(W, H, D);
      const v = T.MathUtils.degToRad(this.c.fov) / 2, h = Math.atan(Math.tan(v) * this.c.aspect);
      // kutuya göre sığdır (açılı bakışta görünen genişlik ≈ en + derinliğin bir kısmı)
      const gW = W * 0.93 + D * 0.42, gH = H * 1.0 + D * 0.2;
      const kart = this.kartModu;            // vitrin kartı: ortalı, sıkı kadraj
      const uz = (Math.max(gH / 2 / Math.tan(v), gW / 2 / Math.tan(h)) + D / 2) * (kart ? 1.06 : 1.32);
      const hedef = new T.Vector3(0, this.oy + H * (kart ? 0.47 : 0.56), 0);   // editör: üstteki düğmelere yer
      const yon = new T.Vector3(kart ? 0.36 : 0.42, kart ? 0.16 : 0.2, 1).normalize();
      this.c.position.copy(hedef).add(yon.multiplyScalar(uz));
      if (this.ctl) { this.ctl.target.copy(hedef); this.ctl.minDistance = R * 1.25; this.ctl.maxDistance = uz * 1.6; this.ctl.update(); }
      else this.c.lookAt(hedef);
    }

    _malzeme(kod, tur) {
      const g = this.gorunum[kod] || {renk: tur === 'arkalik' ? '#ece8e1' : '#f2f0eb', doku: 'mat'};
      const anahtar = `${g.renk}|${g.doku}`;
      if (this._mats[anahtar]) return this._mats[anahtar];
      let m;
      if (g.doku === 'ahsap') {
        const t = ahsapDoku(g.renk);
        m = new T.MeshStandardMaterial({map: t, roughness: 0.62, bumpMap: t, bumpScale: 0.35, envMapIntensity: 0.8});
      } else if (g.doku === 'parlak') {
        m = new T.MeshPhysicalMaterial({color: lin(g.renk), roughness: 0.16, clearcoat: 1, clearcoatRoughness: 0.06, envMapIntensity: 1.1});
      } else {
        m = new T.MeshStandardMaterial({color: lin(g.renk), roughness: 0.58, envMapIntensity: 0.85});
      }
      return (this._mats[anahtar] = m);
    }

    _kenar() {
      return this._mats._kenar || (this._mats._kenar = new T.LineBasicMaterial({color: 0x2a2f36, transparent: true, opacity: 0.28}));
    }
    _metal() {
      return this._mats._metal || (this._mats._metal = new T.MeshStandardMaterial({color: lin('#c3c6cb'), metalness: 1, roughness: 0.26}));
    }
    _plastik() {
      return this._mats._plastik || (this._mats._plastik = new T.MeshStandardMaterial({color: lin('#2b2d30'), roughness: 0.55}));
    }

    _ekle(mesh, id) {
      mesh.castShadow = true; mesh.receiveShadow = true;
      if (id) mesh.userData.id = id;
      this.grup.add(mesh); return mesh;
    }

    _kulp(cx, cy, zOn, uzun) {                           // çubuk kulp: 2 ayak + boru
      const L = uzun + 32, ay = 30;
      const boru = new T.Mesh(new T.CylinderGeometry(6, 6, L, 20), this._metal());
      boru.rotation.z = Math.PI / 2; boru.position.set(cx, cy, zOn + ay);
      this._ekle(boru);
      for (const dx of [-uzun / 2, uzun / 2]) {
        const a = new T.Mesh(new T.CylinderGeometry(4.5, 4.5, ay, 14), this._metal());
        a.rotation.x = Math.PI / 2; a.position.set(cx + dx, cy, zOn + ay / 2);
        this._ekle(a);
      }
    }

    guncelle(v, o = {}) {
      if (!this.r) return;
      if (o.gorunum) this.gorunum = o.gorunum;
      const g = v.govde, W = g.en, H = g.boy, Dp = g.derinlik;
      const oy = this.oy = o.ust ? 1450 : 0;                // üst dolap: duvarda, tezgâh üstü
      for (const m of this.grup.children) { m.geometry && m.geometry.dispose(); if (m.userData.ozelMat) m.material.dispose(); }
      this.grup.clear();
      const gizli = o.gizli || new Set(), secili = o.seciliParca || new Set(), hatali = o.hatali || new Set();
      const isaretle = (mat, renk) => { const m = mat.clone(); m.emissive = lin(renk); m.emissiveIntensity = 0.45; return m; };

      for (const p of v.parcalar3b) {
        if (gizli.has(p.eleman) || gizli.has(p.id)) continue;
        // motor: X sağa, Y derinliğe (0 = ön), Z yukarı (0 = zemin)
        const k = p.koseler.map(([x, y, z]) => [x - W / 2, z + oy, Dp / 2 - y]);
        const mn = [0, 1, 2].map(i => Math.min(...k.map(q => q[i]))), mx = [0, 1, 2].map(i => Math.max(...k.map(q => q[i])));
        const kutu = k.every(q => q.every((c, i) => Math.abs(c - mn[i]) < 0.01 || Math.abs(c - mx[i]) < 0.01));
        const boy = [0, 1, 2].map(i => mx[i] - mn[i]);
        const damar = boy.indexOf(Math.max(...boy));
        let geo;
        if (kutu) geo = kutuGeo(mn, mx, damar, ozet(p.id));
        else {                                            // eğik parça: köşelerden
          const F = [[0, 1, 2, 3], [4, 5, 6, 7], [0, 1, 5, 4], [1, 2, 6, 5], [2, 3, 7, 6], [3, 0, 4, 7]], pos = [];
          for (const f of F) for (const i of [f[0], f[1], f[2], f[0], f[2], f[3]]) pos.push(...k[i]);
          geo = new T.BufferGeometry(); geo.setAttribute('position', new T.Float32BufferAttribute(pos, 3)); geo.computeVertexNormals();
        }
        let mat = this._malzeme(p.malzeme, p.tur);
        const id = p.eleman || p.id;
        const sec = secili.has(p.eleman) || secili.has(p.id), hat = hatali.has(p.id) || hatali.has(p.eleman);
        if (hat || sec) mat = isaretle(mat, hat ? '#ef4444' : '#10b981');
        const m = this._ekle(new T.Mesh(geo, mat), id);
        m.userData.ozelMat = hat || sec;
        if (!kutu) m.material.side = T.DoubleSide;
        const ks = new T.LineSegments(new T.EdgesGeometry(geo, 30), this._kenar());   // ince kenar çizgisi: biçim belli olsun
        ks.raycast = () => {}; this.grup.add(ks);
      }

      // kulplar (2B kutusundan: x sağa, y üstten aşağı, z1 ön yüz)
      for (const p of v.parcalar2b) {
        if (!(p.tur === 'kapak' || p.tur === 'cekmece') || !(p.kulp || []).length || gizli.has(p.id)) continue;
        const b = p.kutu;
        for (const kn of p.kulp) {
          const cx = (kn.includes('sol') ? b.x1 + 50 : kn.includes('sag') ? b.x2 - 50 : (b.x1 + b.x2) / 2) - W / 2;
          const cyU = kn.includes('ust') ? b.y1 + 40 : kn.includes('alt') ? b.y2 - 40 : (b.y1 + b.y2) / 2;
          this._kulp(cx, H - cyU + oy, Dp / 2 - b.z1, 128);
        }
      }

      // ayak / baza
      const ay = H - (g.govde_boyu || H);
      if (ay > 1 && g.ayaktipi === 'AYAK') {
        for (const x of [-W / 2 + 55, W / 2 - 55]) for (const z of [Dp / 2 - 60, -Dp / 2 + 60]) {
          const a = new T.Mesh(new T.CylinderGeometry(16, 20, ay, 20), this._plastik());
          a.position.set(x, oy + ay / 2, z); this._ekle(a);
        }
      } else if (ay > 1 && g.ayaktipi === 'BAZA') {
        const geri = (v.ozellik && v.ozellik['baza.on_geriye']) || 50;
        const govdeMal = (v.ozellik && v.ozellik['govde.malzeme']) || '';
        const bz = new T.Mesh(kutuGeo([-W / 2 + 2, oy, Dp / 2 - geri - 18], [W / 2 - 2, oy + ay, Dp / 2 - geri], 0, 0.2),
                              this._malzeme(govdeMal, 'govde'));
        this._ekle(bz);
      }

      // zemin ve temas gölgesi (üst dolapta zemin yok)
      this.zemin.visible = this.temas.visible = !o.ust;
      this.temas.scale.set(W * 1.35 + 200, Dp * 1.6 + 200, 1); this.temas.position.set(0, 0.5, 0);
      // ana ışık: sol-üst-önden; gölge arkaya düşer
      const R = Math.max(W, H + oy, Dp);
      this.gun.position.set(-0.55 * R, oy + H * 2.2 + 800, Dp / 2 + 1.1 * R);
      this.gun.target.position.set(0, oy + H / 2, 0);
      const sc = this.gun.shadow.camera, e = Math.max(W, H) * 0.9 + 600;
      Object.assign(sc, {left: -e, right: e, top: e, bottom: -e, near: 10, far: R * 6 + 4000});
      sc.updateProjectionMatrix();

      const yeni = !this.olcu || this.olcu[0] !== W || this.olcu[1] !== H || this.olcu[2] !== Dp;
      this.olcu = [W, H, Dp];
      if (o.kameraSifirla) this.elle = false;
      this.boyut();
      if (yeni && !this.elle) this.sigdir();
    }

    _tik(e) {
      if (!this.tikla) return;
      const b = this.r.domElement.getBoundingClientRect();
      const p = new T.Vector2(((e.clientX - b.left) / b.width) * 2 - 1, -((e.clientY - b.top) / b.height) * 2 + 1);
      this.ray.setFromCamera(p, this.c);
      const hit = this.ray.intersectObjects(this.grup.children)[0];
      if (hit && !hit.object.userData.id) return;         // kulp / ayak: seçimi bozma
      this.tikla(hit ? hit.object.userData.id : null, e);
    }
  }
  // Vitrin / katalog kartı: tek görünmez çizici her kabin için bir resim (PNG, saydam) üretir;
  // sayfada onlarca WebGL penceresi açılmaz.
  let _kart = null;
  function resim(v, gorunum, w = 420, h = 520) {
    if (!T || !v || !v.govde) return null;
    if (!_kart) {
      const d = document.createElement('div');
      d.style.cssText = `position:fixed;left:-10000px;top:0;width:${w}px;height:${h}px;pointer-events:none`;
      document.body.appendChild(d);
      _kart = new KabinGercek(d, null);
      _kart.kartModu = true;
      d.style.background = 'none';
    }
    const k = _kart;
    if (!k.r) return null;
    k.kap.style.width = w + 'px'; k.kap.style.height = h + 'px';
    k.elle = false; k.olcu = null;
    k.guncelle(v, {gorunum: gorunum || {}, ust: !!v.ust});
    k.r.render(k.s, k.c);
    return k.r.domElement.toDataURL('image/png');
  }
  // Sırayla doldur: <img data-gorsel-i="i"> → gorseller[i] (sayfa donmasın diye aralıklı)
  function kartlariDoldur(secici, gorseller, gorunum) {
    const imgs = [...document.querySelectorAll(secici)];
    let i = 0;
    const adim = () => {
      if (i >= imgs.length) return;
      const img = imgs[i++], v = gorseller[+img.dataset.gorselI];
      try { const u = resim(v, gorunum); if (u) { img.src = u; img.classList.add('hazir'); } } catch (e) { console.warn(e); }
      (window.requestIdleCallback || (f => setTimeout(f, 16)))(adim);
    };
    adim();
  }
  return {KabinGercek, ahsapDoku, resim, kartlariDoldur};
})();
