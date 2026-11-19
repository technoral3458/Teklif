/* Fuar standı — 3B görünüm. Her modül motorun kurduğu kabinin kendisidir (KabinGercek parçaları);
   burada yalnız standa yerleştirilir: zemin, duvarlı cepheler, modüllerin konumu ve yönü.
   Plan: x soldan (0..en), y arkadan öne (0..derinlik). Sahne: X sağa, Z öne (koridor), Y yukarı. */
window.Stand3B = (() => {
  const T = window.THREE;
  const lin = hex => new T.Color(hex).convertSRGBToLinear();
  let kurucu = null;                       // parçaları üreten tek (görünmez) KabinGercek

  function kabinGrubu(gorsel, gorunum) {
    if (!kurucu) {
      const d = document.createElement('div');
      d.style.cssText = 'position:fixed;left:-10000px;top:0;width:64px;height:64px;pointer-events:none';
      document.body.appendChild(d);
      kurucu = new KabinGercek.KabinGercek(d, null, {cevrimdisi: true});
    }
    kurucu.guncelle(gorsel, {gorunum: gorunum || {}, ust: false, gozler: false});
    const g = new T.Group();
    kurucu.grup.children.slice().forEach(c => g.add(c));   // taşı (kurucu sonraki çizimde bunları silmesin)
    return g;
  }

  const DONUS = {on: 0, arka: Math.PI, sag: Math.PI / 2, sol: -Math.PI / 2};

  // ── donatılar (TV, masa, sandalye, koltuk…): basit ama ölçüsü doğru modeller; önü +z, taban y=0 ──
  const _m = {};
  const mat = (anahtar, f) => _m[anahtar] || (_m[anahtar] = f());
  const kumas = renk => mat('k' + renk, () => new T.MeshStandardMaterial({color: lin(renk), roughness: 0.92}));
  const parlak = renk => mat('p' + renk, () => new T.MeshStandardMaterial({color: lin(renk), roughness: 0.35, envMapIntensity: 0.9}));
  const metal = () => mat('metal', () => new T.MeshStandardMaterial({color: lin('#c3c6cb'), metalness: 1, roughness: 0.28}));
  const siyahMetal = () => mat('smetal', () => new T.MeshStandardMaterial({color: lin('#1f2937'), metalness: 0.6, roughness: 0.45}));
  const ekranMat = () => mat('ekran', () => new T.MeshStandardMaterial({color: lin('#0b1530'), emissive: lin('#1d4ed8'),
                                                                        emissiveIntensity: 0.35, roughness: 0.15}));
  function kutu(g, w, h, d, x, y, z, m) {
    const k = new T.Mesh(new T.BoxGeometry(w, h, d), m); k.position.set(x, y, z);
    k.castShadow = k.receiveShadow = true; g.add(k); return k;
  }
  function silindir(g, r1, r2, h, x, y, z, m, n = 24) {
    const k = new T.Mesh(new T.CylinderGeometry(r1, r2, h, n), m); k.position.set(x, y, z);
    k.castShadow = k.receiveShadow = true; g.add(k); return k;
  }
  function donatiNesnesi(p, H) {
    const g = new T.Group(), w = p.en, d = p.derinlik, h = p.yukseklik, renk = p.renk;
    switch (p.model) {
      case 'sandalye': {
        const sm = renk.toLowerCase() === '#8b5e3c' ? parlak(renk) : kumas(renk);
        kutu(g, w, 45, d * 0.85, 0, 455, d * 0.05, sm);
        kutu(g, w * 0.96, h - 500, 35, 0, 480 + (h - 500) / 2, -d / 2 + 25, sm);
        for (const sx of [-1, 1]) for (const sz of [-1, 1]) silindir(g, 11, 11, 440, sx * (w / 2 - 30), 220, sz * (d / 2 - 40), siyahMetal(), 10);
        break;
      }
      case 'tabure': {
        silindir(g, w * 0.42, w * 0.42, 60, 0, h - 30, 0, kumas(renk));
        silindir(g, 22, 22, h - 60, 0, (h - 60) / 2, 0, metal(), 14);
        silindir(g, w * 0.4, w * 0.44, 18, 0, 9, 0, metal());
        const halka = new T.Mesh(new T.TorusGeometry(w * 0.3, 9, 8, 28), metal());
        halka.rotation.x = Math.PI / 2; halka.position.y = 300; g.add(halka);
        break;
      }
      case 'berjer': case 'kanepe': {
        const km = kumas(renk), kol = w * (p.model === 'berjer' ? 0.15 : 0.09);
        kutu(g, w, 300, d, 0, 80 + 150, 0, km);                                   // gövde
        kutu(g, w - 2 * kol, 120, d * 0.8, 0, 380 + 60, d * 0.08, km);            // minder
        kutu(g, w, h - 380, d * 0.22, 0, 380 + (h - 380) / 2, -d / 2 + d * 0.11, km);   // sırt
        for (const sx of [-1, 1]) kutu(g, kol, 580 - 80, d, sx * (w / 2 - kol / 2), 80 + 250, 0, km);   // kolçak
        for (const sx of [-1, 1]) for (const sz of [-1, 1]) silindir(g, 15, 11, 80, sx * (w / 2 - 50), 40, sz * (d / 2 - 50), parlak('#5b3a1e'), 10);
        break;
      }
      case 'masa_yuvarlak': {
        silindir(g, w / 2, w / 2, 28, 0, h - 14, 0, parlak(renk), 40);
        silindir(g, 35, 35, h - 28, 0, (h - 28) / 2, 0, siyahMetal(), 16);
        silindir(g, Math.min(w * 0.35, 320), Math.min(w * 0.35, 320), 18, 0, 9, 0, siyahMetal(), 32);
        break;
      }
      case 'masa_kare': {
        kutu(g, w, 28, d, 0, h - 14, 0, parlak(renk));
        for (const sx of [-1, 1]) for (const sz of [-1, 1]) kutu(g, 40, h - 28, 40, sx * (w / 2 - 60), (h - 28) / 2, sz * (d / 2 - 60), siyahMetal());
        break;
      }
      case 'tv': case 'led': {
        const merkez = p.model === 'led' ? 400 + h / 2 : (p.montaj === 'ayakli' ? 1350 : 1500);
        kutu(g, w, h, 45, 0, merkez, 0, parlak('#0b0f14'));
        kutu(g, w - 30, h - 30, 4, 0, merkez, 24, ekranMat());
        break;
      }
      case 'tv_standi': {
        kutu(g, w * 0.85, 25, d * 0.85, 0, 12, 0, siyahMetal());
        for (const sx of [-1, 1]) kutu(g, 50, 1650, 50, sx * w * 0.2, 825, -40, siyahMetal());
        break;
      }
      case 'depo': {
        const yk = Math.min(h, H || h), t = 50, kapi = Math.min(800, w - 150), dm = mat('duvar', () => new T.MeshStandardMaterial({color: lin('#f4f3f1'), roughness: 0.85}));
        kutu(g, w, yk, t, 0, yk / 2, -d / 2 + t / 2, dm);
        for (const sx of [-1, 1]) kutu(g, t, yk, d, sx * (w / 2 - t / 2), yk / 2, 0, dm);
        const yan = (w - kapi) / 2;                                               // ön: kapı boşluğu
        for (const sx of [-1, 1]) kutu(g, yan, yk, t, sx * (w / 2 - yan / 2), yk / 2, d / 2 - t / 2, dm);
        kutu(g, kapi, yk - 2100, t, 0, 2100 + (yk - 2100) / 2, d / 2 - t / 2, dm);
        kutu(g, kapi - 10, 2090, 36, 0, 1045, d / 2 - t / 2, parlak('#b48a5f'));  // kapı
        silindir(g, 10, 10, 140, kapi / 2 - 70, 1050, d / 2 - t / 2 + 30, metal(), 10).rotation.z = Math.PI / 2;
        break;
      }
      case 'brosurluk': {
        kutu(g, w, h, 18, 0, h / 2, -d / 2 + 30, parlak('#e2e8f0'));
        kutu(g, w * 0.9, 20, d * 0.8, 0, 10, 0, siyahMetal());
        for (let i = 0; i < 4; i++) kutu(g, w * 0.92, 220, 12, 0, 560 + i * 240, -d / 2 + 70, parlak(['#2563eb', '#059669', '#f59e0b', '#e11d48'][i]));
        break;
      }
      case 'bitki': {
        silindir(g, w * 0.32, w * 0.26, 420, 0, 210, 0, parlak('#e5e7eb'), 28);
        const yap = kumas('#15803d'), yap2 = kumas('#166534');
        for (let i = 0; i < 7; i++) {
          const a = i * 2.4, r = w * 0.18 * (i % 3), yy = 600 + (h - 650) * (i / 7);
          const k = new T.Mesh(new T.SphereGeometry(w * (0.28 - i * 0.015), 14, 10), i % 2 ? yap : yap2);
          k.position.set(Math.cos(a) * r, yy, Math.sin(a) * r); k.castShadow = true; g.add(k);
        }
        break;
      }
      default: kutu(g, w, h, d, 0, h / 2, 0, kumas(renk));
    }
    return g;
  }

  // Model kartı için küçük resim (tek görünmez çizici)
  let _kart = null;
  function resim(p, boyut = 220) {
    if (!T) return null;
    if (!_kart) {
      const r = new T.WebGLRenderer({antialias: true, alpha: true, preserveDrawingBuffer: true});
      r.setPixelRatio(1); r.outputEncoding = T.sRGBEncoding; r.toneMapping = T.ACESFilmicToneMapping;
      const s = new T.Scene();
      if (T.RoomEnvironment) s.environment = new T.PMREMGenerator(r).fromScene(new T.RoomEnvironment(), 0.04).texture;
      s.add(new T.HemisphereLight(0xffffff, 0xcfd3d8, 0.5));
      const gun = new T.DirectionalLight(0xffffff, 1.2); gun.position.set(-1, 2, 2); s.add(gun);
      _kart = {r, s, c: new T.PerspectiveCamera(30, 1, 10, 50000), g: null};
    }
    const k = _kart;
    k.r.setSize(boyut, boyut);
    if (k.g) k.s.remove(k.g);
    k.g = donatiNesnesi(p, 2500); k.s.add(k.g);
    const kutuB = new T.Box3().setFromObject(k.g), mrk = kutuB.getCenter(new T.Vector3()), b = kutuB.getSize(new T.Vector3());
    const R = Math.max(b.x, b.y, b.z) * 0.62;
    const uz = R / Math.tan(T.MathUtils.degToRad(15)) * 1.05;
    k.c.position.copy(mrk).add(new T.Vector3(0.62, 0.42, 1).normalize().multiplyScalar(uz)); k.c.lookAt(mrk);
    k.r.render(k.s, k.c);
    return k.r.domElement.toDataURL('image/png');
  }

  class Gorunum {
    constructor(kap) {
      this.kap = kap;
      if (!T || !window.KabinGercek) { kap.innerHTML = '<p class="p-4 text-slate-500">3B görünüm yüklenemedi.</p>'; return; }
      const r = this.r = new T.WebGLRenderer({antialias: true, preserveDrawingBuffer: true});
      r.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
      r.outputEncoding = T.sRGBEncoding; r.toneMapping = T.ACESFilmicToneMapping; r.toneMappingExposure = 0.95;
      r.shadowMap.enabled = true; r.shadowMap.type = T.PCFSoftShadowMap;
      r.domElement.style.cssText = 'display:block;width:100%;height:100%;touch-action:none';
      kap.appendChild(r.domElement);
      const s = this.s = new T.Scene();
      s.background = lin('#e7e9ec');
      if (T.RoomEnvironment) s.environment = new T.PMREMGenerator(r).fromScene(new T.RoomEnvironment(), 0.04).texture;
      s.add(new T.HemisphereLight(0xffffff, 0xc9ccd1, s.environment ? 0.35 : 0.9));
      const gun = this.gun = new T.DirectionalLight(0xfff8ef, s.environment ? 1.4 : 1.0);
      gun.castShadow = true; gun.shadow.mapSize.set(2048, 2048); gun.shadow.bias = -0.0004; gun.shadow.normalBias = 2.5; gun.shadow.radius = 6;
      s.add(gun, gun.target);
      const salon = new T.Mesh(new T.PlaneGeometry(80000, 80000), new T.MeshStandardMaterial({color: lin('#d5d8dc'), roughness: 0.9}));
      salon.rotation.x = -Math.PI / 2; salon.position.y = -1; salon.receiveShadow = true; s.add(salon);
      this.grup = new T.Group(); s.add(this.grup);
      this.c = new T.PerspectiveCamera(38, 1, 50, 200000);
      this.ctl = T.OrbitControls ? new T.OrbitControls(this.c, r.domElement) : null;
      if (this.ctl) Object.assign(this.ctl, {enableDamping: true, dampingFactor: 0.09, maxPolarAngle: Math.PI / 2 - 0.05, rotateSpeed: 0.7});
      new ResizeObserver(() => this.boyut()).observe(kap);
      const don = () => { requestAnimationFrame(don); if (!kap.offsetParent) return; this.ctl && this.ctl.update(); r.render(s, this.c); };
      don();
    }

    boyut() {
      const w = this.kap.clientWidth || 300, h = this.kap.clientHeight || 300;
      this.r.setSize(w, h, false); this.c.aspect = w / h; this.c.updateProjectionMatrix();
    }

    goster(girdi, sahne, gorunum) {
      if (!this.r) return;
      this.grup.clear();
      const W = girdi.en, D = girdi.derinlik, H = girdi.yukseklik, acik = new Set(girdi.acik);
      // zemin: hafif yükseltilmiş halı
      const zemin = new T.Mesh(new T.BoxGeometry(W, 40, D), new T.MeshStandardMaterial({color: lin('#6b7280'), roughness: 0.95}));
      zemin.position.set(0, 20, 0); zemin.receiveShadow = true; this.grup.add(zemin);
      // duvarlı cepheler
      const duvarMat = new T.MeshStandardMaterial({color: lin('#f4f3f1'), roughness: 0.85});
      const seritMat = new T.MeshStandardMaterial({color: lin('#059669'), roughness: 0.6});
      const k = 60, duvarlar = {arka: [W + 2 * k, H, k, 0, -D / 2 - k / 2], on: [W + 2 * k, H, k, 0, D / 2 + k / 2],
                                sol: [k, H, D, -W / 2 - k / 2, 0], sag: [k, H, D, W / 2 + k / 2, 0]};
      for (const [c, [w, h, d, x, z]] of Object.entries(duvarlar)) {
        if (acik.has(c)) continue;
        const m = new T.Mesh(new T.BoxGeometry(w, h, d), duvarMat);
        m.position.set(x, h / 2 + 40, z); m.castShadow = m.receiveShadow = true; this.grup.add(m);
        const s = new T.Mesh(new T.BoxGeometry(c === 'sol' || c === 'sag' ? k + 4 : w, 120, c === 'sol' || c === 'sag' ? d : k + 4), seritMat);
        s.position.set(x, H + 40 - 60, z); this.grup.add(s);          // üstte marka şeridi
      }
      // modüller: kabin grubunu bir kez kur, her yerleşime kopyala
      sahne.kabinler.forEach(kb => {
        const kaynak = kabinGrubu(kb.gorsel, gorunum);
        kb.moduller.forEach(i => {
          const m = sahne.moduller[i];
          const yatay = m.yon === 'on' || m.yon === 'arka';
          const w = yatay ? m.en : m.derinlik, h = yatay ? m.derinlik : m.en;
          const g = kaynak.clone();
          g.rotation.y = DONUS[m.yon] || 0;
          g.position.set(m.x + w / 2 - W / 2, 40, m.y + h / 2 - D / 2);
          this.grup.add(g);
        });
      });
      // donatılar (plan merkezinde, açı derece; 3B: Y ekseni etrafında -açı)
      (sahne.donatilar || []).forEach(p => {
        const n = donatiNesnesi(p, H);
        n.rotation.y = -p.aci * Math.PI / 180;
        n.position.set(p.x - W / 2, 40, p.y - D / 2);
        this.grup.add(n);
      });
      // ışık ve kamera: koridor tarafından (ön-sağ), yukarıdan
      const R = Math.max(W, D, H);
      this.gun.position.set(-0.6 * R, H * 2.4 + 2000, 0.9 * R);
      this.gun.target.position.set(0, 0, 0);
      const sc = this.gun.shadow.camera, e = R * 0.9 + 1000;
      Object.assign(sc, {left: -e, right: e, top: e, bottom: -e, near: 10, far: R * 8 + 8000}); sc.updateProjectionMatrix();
      this.boyut();
      // kamera açık (koridor) tarafından: önce ön, yan olarak açık olan yan
      const yanX = acik.has('sol') && !acik.has('sag') ? -0.55 : 0.55;
      const onZ = acik.has('on') || !acik.has('arka') ? 1 : -1;
      const hedef = new T.Vector3(0, H * 0.32, 0);
      const yon = new T.Vector3(yanX, 0.62, onZ).normalize();
      const uz = (Math.hypot(W, D) * 1.05 + H * 0.6) * Math.max(1, Math.min(1.6, 1.3 / this.c.aspect));   // dar ekranda geri çekil
      this.c.position.copy(hedef).add(yon.multiplyScalar(uz));
      if (this.ctl) { this.ctl.target.copy(hedef); this.ctl.minDistance = R * 0.5; this.ctl.maxDistance = uz * 2.2; this.ctl.update(); }
      else this.c.lookAt(hedef);
    }
  }
  return {Gorunum, resim};
})();
