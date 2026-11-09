<?php
/** @var string $content @var string $pageTitle @var string $current @var string $pageDescription */
$nav = [
    'ozellikler'    => 'Özellikler',
    'nasil-calisir' => 'Nasıl Çalışır',
    'mobil'         => 'Mobil',
    'paketler'      => 'Paketler',
    'sss'           => 'SSS',
    'hakkimizda'    => 'Hakkımızda',
    'iletisim'      => 'İletişim',
];
$fullTitle = $current === '' ? cfg('site_name') . ' — ' . cfg('tagline') : $pageTitle . ' | ' . cfg('site_name');
?><!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><?= e($fullTitle) ?></title>
<meta name="description" content="<?= e($pageDescription) ?>">
<meta property="og:title" content="<?= e($fullTitle) ?>">
<meta property="og:description" content="<?= e($pageDescription) ?>">
<meta property="og:type" content="website">
<meta name="theme-color" content="#0d1117">
<link rel="icon" href="<?= e(asset('img/favicon.svg')) ?>" type="image/svg+xml">
<link rel="stylesheet" href="<?= e(asset('css/style.css')) ?>">
</head>
<body>
<a class="skip" href="#icerik">İçeriğe geç</a>
<header class="site-header">
  <div class="container nav-wrap">
    <a class="brand" href="<?= e(url()) ?>" aria-label="<?= e(cfg('site_name')) ?> ana sayfa">
      <img src="<?= e(asset('img/logo.svg')) ?>" alt="" width="36" height="36">
      <span>Titanium<b>Cam</b></span>
    </a>
    <button class="nav-toggle" aria-expanded="false" aria-controls="ana-menu" aria-label="Menüyü aç/kapat">
      <span></span><span></span><span></span>
    </button>
    <nav id="ana-menu" class="main-nav" aria-label="Ana menü">
      <?php foreach ($nav as $slug => $label): ?>
        <a href="<?= e(url($slug)) ?>"<?= $current === $slug ? ' aria-current="page"' : '' ?>><?= e($label) ?></a>
      <?php endforeach; ?>
      <a class="btn btn-primary btn-sm" href="<?= e(url('demo')) ?>">Demo İste</a>
    </nav>
  </div>
</header>

<main id="icerik">
<?php if ($current !== ''): ?>
  <section class="page-hero">
    <div class="container">
      <nav class="crumbs" aria-label="Sayfa yolu"><a href="<?= e(url()) ?>">Ana Sayfa</a> <span>/</span> <?= e($pageTitle) ?></nav>
      <h1><?= e($pageTitle) ?></h1>
    </div>
  </section>
<?php endif; ?>
<?= $content ?>
</main>

<section class="cta-band">
  <div class="container cta-inner">
    <div>
      <h2>Atölyenizde Titanium Cam'i görün</h2>
      <p>Kendi kapak ölçülerinizle canlı demo yapalım; nesting verimini ve G-code çıktısını birlikte inceleyelim.</p>
    </div>
    <a class="btn btn-primary btn-lg" href="<?= e(url('demo')) ?>">Ücretsiz Demo Talep Et</a>
  </div>
</section>

<footer class="site-footer">
  <div class="container footer-grid">
    <div>
      <a class="brand" href="<?= e(url()) ?>"><img src="<?= e(asset('img/logo.svg')) ?>" alt="" width="32" height="32"><span>Titanium<b>Cam</b></span></a>
      <p class="muted"><?= e(cfg('tagline')) ?>.</p>
    </div>
    <div>
      <h3>Ürün</h3>
      <a href="<?= e(url('ozellikler')) ?>">Özellikler</a>
      <a href="<?= e(url('nasil-calisir')) ?>">Nasıl Çalışır</a>
      <a href="<?= e(url('mobil')) ?>">Mobil Uygulama</a>
      <a href="<?= e(url('paketler')) ?>">Lisans Paketleri</a>
    </div>
    <div>
      <h3>Şirket</h3>
      <a href="<?= e(url('hakkimizda')) ?>">Hakkımızda</a>
      <a href="<?= e(url('sss')) ?>">SSS</a>
      <a href="<?= e(url('iletisim')) ?>">İletişim</a>
      <a href="<?= e(url('kvkk')) ?>">KVKK</a>
    </div>
    <div>
      <h3>İletişim</h3>
      <a href="mailto:<?= e(cfg('email')) ?>"><?= e(cfg('email')) ?></a>
      <a href="tel:<?= e(preg_replace('/[^0-9+]/', '', cfg('phone'))) ?>"><?= e(cfg('phone')) ?></a>
      <span class="muted"><?= e(cfg('address')) ?></span>
    </div>
  </div>
  <div class="container footer-bottom">
    <span>© <?= date('Y') ?> <?= e(cfg('company')) ?>. Tüm hakları saklıdır.</span>
    <span class="muted">Titanium Cam, <?= e(cfg('company')) ?> ürünüdür.</span>
  </div>
</footer>

<?php if (cfg('whatsapp')): ?>
<a class="wa-float" href="https://wa.me/<?= e(cfg('whatsapp')) ?>" target="_blank" rel="noopener" aria-label="WhatsApp ile yazın">
  <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true"><path fill="currentColor" d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2Zm0 18.2c-1.5 0-3-.4-4.3-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2Zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8-.2-.1-.4-.1-.6.1l-.8 1c-.1.2-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.2-.4.2-.4.7-1.3.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.7 11.8 11.8 0 0 0 4.5 4c1.7.7 2.3.8 3.2.6.5-.1 1.5-.6 1.7-1.2.2-.6.2-1.1.2-1.2-.1-.1-.3-.2-.5-.3Z"/></svg>
</a>
<?php endif; ?>
<script src="<?= e(asset('js/main.js')) ?>" defer></script>
</body>
</html>
