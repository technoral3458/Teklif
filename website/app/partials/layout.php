<?php
/** @var string $pageContent @var string $pageTitle @var string $current @var string $pageDescription */
$seo = content('seo');
$nav = [
    '#moduller' => 'Modüller',
    '#nasil'    => 'Nasıl çalışır',
    'galeri'    => 'Ekranlar',
    'sss'       => 'SSS',
    'iletisim'  => 'İletişim',
];
$fullTitle = $current === '' ? $seo['title'] : $pageTitle . ' | ' . cfg('site_name');
$origin = cfg('base_url') ?: ((!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off' ? 'https' : 'http') . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . base_path());
$ogImage = $origin . '/assets/' . $seo['og_image'];
$canonical = $origin . '/' . $current;
?><!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><?= e($fullTitle) ?></title>
<meta name="description" content="<?= e($pageDescription) ?>">
<?php if ($current === ''): ?><meta name="keywords" content="<?= e($seo['keywords']) ?>">
<?php endif; ?>
<link rel="canonical" href="<?= e($canonical) ?>">
<meta property="og:title" content="<?= e($current === '' ? $seo['og_title'] : $fullTitle) ?>">
<meta property="og:description" content="<?= e($current === '' ? $seo['og_description'] : $pageDescription) ?>">
<meta property="og:image" content="<?= e($ogImage) ?>">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:url" content="<?= e($canonical) ?>">
<meta property="og:type" content="website">
<meta property="og:locale" content="tr_TR">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#070c18">
<link rel="icon" href="<?= e(asset('img/logo/favicon.ico')) ?>" sizes="any">
<link rel="icon" href="<?= e(asset('img/logo/favicon-32.png')) ?>" type="image/png" sizes="32x32">
<link rel="apple-touch-icon" href="<?= e(asset('img/logo/apple-touch-icon.png')) ?>">
<link rel="stylesheet" href="<?= e(asset('css/style.css')) ?>">
<script>document.documentElement.className += ' js';</script>
</head>
<body>
<div class="bg-glow" aria-hidden="true"><i class="g1"></i><i class="g2"></i><i class="g3"></i></div>
<a class="skip" href="#icerik">İçeriğe geç</a>
<header class="site-header">
  <div class="container nav-wrap">
    <a class="brand" href="<?= e(url()) ?>" aria-label="Titanium Cam ana sayfa">
      <img src="<?= e(asset('img/logo/logo_128.png')) ?>" alt="" width="36" height="36">
      <span>Titanium Cam</span>
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
<?= $pageContent ?>
</main>

<?php $closing = content('closing'); ?>
<section class="cta-band">
  <div class="container cta-inner reveal">
    <div>
      <h2><?= e($closing['title']) ?></h2>
      <p><?= e($closing['text']) ?> Kendi parçalarınızla canlı demo yapalım.</p>
    </div>
    <a class="btn btn-primary btn-lg" href="<?= e(url('demo')) ?>">Ücretsiz Demo İste</a>
  </div>
</section>

<footer class="site-footer">
  <div class="container footer-grid">
    <div>
      <a class="brand" href="<?= e(url()) ?>"><img src="<?= e(asset('img/logo/logo_128.png')) ?>" alt="" width="32" height="32" loading="lazy"><span>Titanium Cam</span></a>
      <p class="muted">Tasarımdan makineye tek program. Mobilya atölyeleri için Türkçe CAD/CAM yazılımı.</p>
    </div>
    <div>
      <h3>Modüller</h3>
      <?php foreach (content('modules') as $m): ?>
        <a href="<?= e(url('#' . $m['id'])) ?>"><?= e($m['title']) ?></a>
      <?php endforeach; ?>
    </div>
    <div>
      <h3>Site</h3>
      <a href="<?= e(url('galeri')) ?>">Ekranlar ve görseller</a>
      <a href="<?= e(url('sss')) ?>">Sık sorulan sorular</a>
      <a href="<?= e(url('hakkimizda')) ?>">Hakkımızda</a>
      <a href="<?= e(url('kvkk')) ?>">KVKK</a>
    </div>
    <div>
      <h3>İletişim</h3>
      <?php if (cfg('email')): ?><a href="mailto:<?= e(cfg('email')) ?>"><?= e(cfg('email')) ?></a><?php endif; ?>
      <?php if (cfg('phone')): ?><a href="tel:<?= e(preg_replace('/[^0-9+]/', '', cfg('phone'))) ?>"><?= e(cfg('phone')) ?></a><?php endif; ?>
      <?php if (cfg('address')): ?><span class="muted"><?= e(cfg('address')) ?></span><?php endif; ?>
      <a href="<?= e(url('demo')) ?>">Demo talebi</a>
    </div>
  </div>
  <div class="container footer-bottom">
    <span>© <?= date('Y') ?> <?= e(cfg('company')) ?> · Titanium Cam</span>
  </div>
</footer>

<?php if (cfg('whatsapp')): ?>
<a class="wa-float" href="https://wa.me/<?= e(cfg('whatsapp')) ?>" target="_blank" rel="noopener" aria-label="WhatsApp ile yazın">
  <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true"><path fill="currentColor" d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2Zm0 18.2c-1.5 0-3-.4-4.3-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2Zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8-.2-.1-.4-.1-.6.1l-.8 1c-.1.2-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.2-.4.2-.4.7-1.3.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.7 11.8 11.8 0 0 0 4.5 4c1.7.7 2.3.8 3.2.6.5-.1 1.5-.6 1.7-1.2.2-.6.2-1.1.2-1.2-.1-.1-.3-.2-.5-.3Z"/></svg>
</a>
<?php endif; ?>

<div class="lightbox" hidden role="dialog" aria-modal="true" aria-label="Büyük görsel">
  <button class="lb-close" aria-label="Kapat">×</button>
  <button class="lb-prev" aria-label="Önceki">‹</button>
  <figure><img alt=""><figcaption></figcaption></figure>
  <button class="lb-next" aria-label="Sonraki">›</button>
</div>
<script src="<?= e(asset('js/main.js')) ?>" defer></script>
</body>
</html>
