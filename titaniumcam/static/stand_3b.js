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
  return {Gorunum};
})();
