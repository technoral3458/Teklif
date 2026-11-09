<?php
$pageDescription = 'Titanium Cam lisans paketleri: atölyenizin büyüklüğüne göre Başlangıç, Profesyonel ve Kurumsal seçenekler.';
$packages = [
    [
        'name' => 'Başlangıç',
        'for'  => 'Tek tezgâhlı atölyeler',
        'features' => ['Otomatik nesting', 'G-code ve DXF çıktısı', 'Ürün kataloğu', '3 kullanıcı', 'E-posta desteği'],
        'featured' => false,
    ],
    [
        'name' => 'Profesyonel',
        'for'  => 'Bayi ağı olan üreticiler',
        'features' => ['Başlangıç paketindeki her şey', 'Bayi sipariş portalı', 'Satış onay akışı', 'Android uygulaması', 'Sınırsız bayi hesabı', 'Telefon ve uzaktan destek'],
        'featured' => true,
    ],
    [
        'name' => 'Kurumsal',
        'for'  => 'Çok tezgâhlı fabrikalar',
        'features' => ['Profesyonel paketteki her şey', 'Kendi sunucunuza kurulum (Linux)', 'Tezgâha özel post-processor uyarlaması', 'ERP / muhasebe entegrasyonu', 'Yerinde eğitim', 'Öncelikli destek'],
        'featured' => false,
    ],
];
?>
<section class="section">
  <div class="container">
    <p class="lead center narrow">Her atölyenin tezgâh sayısı, bayi ağı ve iş hacmi farklıdır. Fiyatlandırma ihtiyacınıza göre belirlenir; size özel teklif için bizimle iletişime geçin.</p>
    <div class="cards cards-3 pricing">
      <?php foreach ($packages as $p): ?>
        <article class="card price-card<?= $p['featured'] ? ' featured' : '' ?>">
          <?php if ($p['featured']): ?><span class="badge">En çok tercih edilen</span><?php endif; ?>
          <h2><?= e($p['name']) ?></h2>
          <p class="muted"><?= e($p['for']) ?></p>
          <ul class="checklist">
            <?php foreach ($p['features'] as $f): ?><li><?= e($f) ?></li><?php endforeach; ?>
          </ul>
          <a class="btn <?= $p['featured'] ? 'btn-primary' : 'btn-ghost' ?>" href="<?= e(url('demo')) ?>?paket=<?= urlencode($p['name']) ?>">Teklif İste</a>
        </article>
      <?php endforeach; ?>
    </div>
  </div>
</section>
