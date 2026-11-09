<?php
$pageDescription = "Titanium Cam'in gerçek program ekranları: çizim, kapak, kabin, mutfak, nesting ve makine modülleri ile gerçekçi render görüntüleri.";
?>
<section class="section">
  <div class="container">
    <p class="lead narrow">Aşağıdaki görüntülerin hepsi programın kendisinden alındı. Büyütmek için bir görsele dokunun.</p>

    <?php foreach (content('gallery') as $group => $items): ?>
      <h2 class="group-title"><?= e($group) ?></h2>
      <div class="gallery">
        <?php foreach ($items as [$file, $caption]): ?>
          <button class="gallery-item" type="button" data-full="<?= e(asset("img/ekranlar/$file.webp")) ?>" data-caption="<?= e($caption) ?>">
            <img src="<?= e(asset("img/ekranlar/kucuk/$file.webp")) ?>" alt="<?= e($caption) ?>" loading="lazy" width="640" height="380">
            <span><?= e($caption) ?></span>
          </button>
        <?php endforeach; ?>
      </div>
    <?php endforeach; ?>

    <h2 class="group-title">Gerçekçi render</h2>
    <div class="gallery">
      <?php foreach (content('gallery_renders') as [$file, $caption]): ?>
        <button class="gallery-item" type="button" data-full="<?= e(asset("img/render/$file.webp")) ?>" data-caption="<?= e($caption) ?>">
          <img src="<?= e(asset("img/render/kucuk/$file.webp")) ?>" alt="<?= e($caption) ?>" loading="lazy" width="640" height="360">
          <span><?= e($caption) ?></span>
        </button>
      <?php endforeach; ?>
    </div>

    <p class="muted small center">Ekranlardaki müşteri, sipariş ve parça adları örnektir.</p>
  </div>
</section>
