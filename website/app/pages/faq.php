<?php
$pageDescription = 'Titanium Cam hakkında sık sorulan sorular: uyumlu CNC makineleri, dosya biçimleri, kapak modelleri, lisans ve bilgisayar gereksinimi.';
$faqs = content('faq');
?>
<section class="section">
  <div class="container narrow">
    <div class="faq">
      <?php foreach ($faqs as $i => [$q, $a]): ?>
        <details<?= $i === 0 ? ' open' : '' ?>>
          <summary><?= e($q) ?></summary>
          <p><?= e($a) ?></p>
        </details>
      <?php endforeach; ?>
    </div>
    <p class="center muted">Sorunuzun cevabını bulamadınız mı? <a href="<?= e(url('iletisim')) ?>">Bize yazın</a>.</p>
  </div>
</section>
<script type="application/ld+json"><?= json_encode([
    '@context' => 'https://schema.org',
    '@type' => 'FAQPage',
    'mainEntity' => array_map(fn($f) => [
        '@type' => 'Question',
        'name' => $f[0],
        'acceptedAnswer' => ['@type' => 'Answer', 'text' => $f[1]],
    ], $faqs),
], JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?></script>
