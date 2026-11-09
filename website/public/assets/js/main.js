(function () {
  'use strict';
  document.documentElement.classList.add('js');
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var hasIO = 'IntersectionObserver' in window;

  // Mobil menü
  var btn = document.querySelector('.nav-toggle');
  var nav = document.getElementById('ana-menu');
  if (btn && nav) {
    btn.addEventListener('click', function () {
      var open = nav.classList.toggle('open');
      btn.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    nav.addEventListener('click', function (e) {
      if (e.target.closest('a')) { nav.classList.remove('open'); btn.setAttribute('aria-expanded', 'false'); }
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && nav.classList.contains('open')) {
        nav.classList.remove('open'); btn.setAttribute('aria-expanded', 'false'); btn.focus();
      }
    });
  }

  // Kaydırınca belirme
  var reveals = document.querySelectorAll('.reveal');
  if (hasIO && !reduce) {
    var ro = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) { en.target.classList.add('seen'); ro.unobserve(en.target); }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
    reveals.forEach(function (el) { ro.observe(el); });
  } else {
    reveals.forEach(function (el) { el.classList.add('seen'); });
  }

  // Sayaçlar
  var counters = document.querySelectorAll('[data-count]');
  function countUp(el) {
    var target = +el.getAttribute('data-count'), t0 = null, dur = 1400;
    function step(t) {
      if (!t0) t0 = t;
      var k = Math.min((t - t0) / dur, 1);
      el.textContent = Math.round(target * (1 - Math.pow(1 - k, 3)));
      if (k < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  }
  if (hasIO && !reduce) {
    var co = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) { if (en.isIntersecting) { countUp(en.target); co.unobserve(en.target); } });
    }, { threshold: 0.6 });
    counters.forEach(function (el) { el.textContent = '0'; co.observe(el); });
  }

  // Döngülü klipler yalnız görünürken oynar
  var videos = document.querySelectorAll('video.loop');
  if (reduce) {
    videos.forEach(function (v) { v.controls = true; v.preload = 'metadata'; });
  } else if (hasIO) {
    var vo = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        var v = en.target;
        if (en.isIntersecting) {
          if (v.preload === 'none') { v.preload = 'auto'; v.load(); }
          var p = v.play(); if (p && p.catch) p.catch(function () { v.controls = true; });
        } else { v.pause(); }
      });
    }, { threshold: 0.3 });
    videos.forEach(function (v) { vo.observe(v); });
  } else {
    videos.forEach(function (v) { v.controls = true; });
  }

  // Büyük görsel (lightbox)
  var box = document.querySelector('.lightbox');
  var items = Array.prototype.slice.call(document.querySelectorAll('[data-full]'));
  if (box && items.length) {
    var img = box.querySelector('img'), cap = box.querySelector('figcaption'), idx = 0, lastFocus = null;
    function show(i) {
      idx = (i + items.length) % items.length;
      var it = items[idx];
      img.src = it.getAttribute('data-full');
      img.alt = it.getAttribute('data-caption') || '';
      cap.textContent = it.getAttribute('data-caption') || '';
    }
    function open(i) {
      lastFocus = document.activeElement; show(i); box.hidden = false;
      document.body.style.overflow = 'hidden'; box.querySelector('.lb-close').focus();
    }
    function close() {
      box.hidden = true; img.removeAttribute('src'); document.body.style.overflow = '';
      if (lastFocus) lastFocus.focus();
    }
    items.forEach(function (it, i) { it.addEventListener('click', function () { open(i); }); });
    box.querySelector('.lb-close').addEventListener('click', close);
    box.querySelector('.lb-prev').addEventListener('click', function () { show(idx - 1); });
    box.querySelector('.lb-next').addEventListener('click', function () { show(idx + 1); });
    box.addEventListener('click', function (e) { if (e.target === box) close(); });
    document.addEventListener('keydown', function (e) {
      if (box.hidden) return;
      if (e.key === 'Escape') close();
      else if (e.key === 'ArrowLeft') show(idx - 1);
      else if (e.key === 'ArrowRight') show(idx + 1);
    });
  }

  // Formda ilk hatalı alana odaklan
  var invalid = document.querySelector('[aria-invalid="true"]');
  if (invalid) invalid.focus();
})();
