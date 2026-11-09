// Mobil menü aç/kapat
(function () {
  var btn = document.querySelector('.nav-toggle');
  var nav = document.getElementById('ana-menu');
  if (!btn || !nav) return;
  btn.addEventListener('click', function () {
    var open = nav.classList.toggle('open');
    btn.setAttribute('aria-expanded', open ? 'true' : 'false');
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && nav.classList.contains('open')) {
      nav.classList.remove('open');
      btn.setAttribute('aria-expanded', 'false');
      btn.focus();
    }
  });
})();

// Formda ilk hatalı alana odaklan
(function () {
  var invalid = document.querySelector('[aria-invalid="true"]');
  if (invalid) invalid.focus();
})();
