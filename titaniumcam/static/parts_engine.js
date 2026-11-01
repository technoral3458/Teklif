// No-code şablon motoru: parça tanımlarından (formüllü) 3B grup üretir.
// Bir parça: { mat, repeat:[[var,countExpr],...], when, x,y,z,w,h,d }  (mm formülleri)
// Değişkenler: PT (18), şablon parametreleri (W,H,D, columns...), repeat indexleri, math fonksiyonları.
window.PartsEngine = (function () {
  const MATH = { floor: Math.floor, ceil: Math.ceil, round: Math.round, min: Math.min, max: Math.max, sqrt: Math.sqrt, abs: Math.abs, pi: Math.PI };
  const EDGE_MAT = new THREE.LineBasicMaterial({ color: 0x1f2937, transparent: true, opacity: 0.14 });

  function evalExpr(expr, vars) {
    if (expr === undefined || expr === null || expr === '') return 0;
    if (typeof expr === 'number') return expr;
    const keys = Object.keys(vars);
    try { const v = Function(...keys, 'return (' + expr + ')')(...keys.map(k => vars[k])); return isFinite(v) ? v : 0; }
    catch (e) { return 0; }
  }
  function expand(repeat, base) {
    let combos = [{}];
    (repeat || []).forEach(([name, countExpr]) => {
      const n = Math.max(0, Math.floor(evalExpr(countExpr, base)));
      const next = [];
      combos.forEach(c => { for (let i = 0; i < n; i++) next.push(Object.assign({}, c, { [name]: i })); });
      combos = next;
    });
    return combos;
  }
  function darken(h, f) { return ((h >> 16 & 255) * f << 16) | ((h >> 8 & 255) * f << 8) | ((h & 255) * f); }
  function matFor(mat, o) {
    const bodyC = o.bodyHex, doorC = o.doorHex;
    if (mat === 'glass') return new THREE.MeshStandardMaterial({ color: 0xbcd6e6, transparent: true, opacity: 0.32, roughness: 0.08 });
    if (mat === 'metal') return new THREE.MeshStandardMaterial({ color: 0x565b62, metalness: 0.35, roughness: 0.42 });
    if (mat === 'back') return new THREE.MeshStandardMaterial({ color: darken(bodyC, 0.85), roughness: 0.8 });
    if (mat === 'door' || mat === 'drawer') return new THREE.MeshStandardMaterial({ color: doorC, roughness: 0.74 });
    return new THREE.MeshStandardMaterial({ color: bodyC, roughness: 0.78 }); // panel/shelf
  }
  function addBox(g, w, h, d, x, y, z, m) {
    w = Math.max(w, 0.004); h = Math.max(h, 0.004); d = Math.max(d, 0.004);
    const geo = new THREE.BoxGeometry(w, h, d);
    const me = new THREE.Mesh(geo, m); me.position.set(x, y, z); me.castShadow = true; me.receiveShadow = true; g.add(me);
    const big = [w, h, d].filter(v => v > 0.08).length;
    if (big >= 2 && !m.transparent) { const ed = new THREE.LineSegments(new THREE.EdgesGeometry(geo), EDGE_MAT); ed.position.set(x, y, z); g.add(ed); }
  }

  function buildGroup(def, params, opts) {
    opts = opts || {};
    const g = new THREE.Group();
    const base = Object.assign({ PT: 18 }, MATH, params);
    (def.parts || []).forEach(part => {
      if (part.mat === 'door' && opts.showDoors === false) return;
      expand(part.repeat, base).forEach(idx => {
        const v = Object.assign({}, base, idx);
        if (part.when && !evalExpr(part.when, v)) return;
        const w = evalExpr(part.w, v) / 1000, h = evalExpr(part.h, v) / 1000, d = evalExpr(part.d, v) / 1000;
        const x = evalExpr(part.x, v) / 1000, y = evalExpr(part.y, v) / 1000, z = evalExpr(part.z || '0', v) / 1000;
        addBox(g, w, h, d, x, y, z, matFor(part.mat, opts));
      });
    });
    return g;
  }

  function price(def, params, premium) {
    const vars = Object.assign({ PT: 18 }, MATH, params);
    const material = evalExpr(def.price_expr, vars);
    return material * (1 + (premium || 0));
  }

  return { evalExpr, buildGroup, price };
})();
