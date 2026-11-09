<?php
$hero = content('hero');
$intro = content('intro');
$render = content('render');
$langs = content('languages');
$yt = youtube_embed((string) cfg('youtube_url'));
?>
<section class="hero">
  <div class="container hero-grid">
    <div class="hero-copy">
      <span class="pill"><?= e($hero['eyebrow']) ?></span>
      <h1><?= e($hero['title']) ?></h1>
      <p class="lead"><?= e($hero['text']) ?></p>
      <div class="hero-actions">
        <a class="btn btn-primary btn-lg" href="#moduller">Özellikleri incele</a>
        <?php if ($yt): ?>
          <a class="btn btn-ghost btn-lg" href="#video">▶ Tanıtım videosu</a>
        <?php else: ?>
          <a class="btn btn-ghost btn-lg" href="<?= e(url('demo')) ?>">Demo iste</a>
        <?php endif; ?>
      </div>
    </div>
    <figure class="hero-visual">
      <img src="<?= e(asset($hero['image'])) ?>" alt="<?= e($hero['alt']) ?>" width="1920" height="1080" fetchpriority="high">
    </figure>
  </div>
  <div class="container counters">
    <?php foreach (content('counters') as [$n, $label]): ?>
      <div class="counter"><b data-count="<?= (int) $n ?>"><?= (int) $n ?></b><span><?= e($label) ?></span></div>
    <?php endforeach; ?>
  </div>
</section>

<section class="section" id="ozellikler">
  <div class="container split">
    <div class="reveal">
      <span class="eyebrow">Tek program</span>
      <h2><?= e($intro['title']) ?></h2>
      <p class="lead"><?= e($intro['text']) ?></p>
    </div>
    <figure class="shot reveal">
      <img src="<?= e(asset($intro['image'])) ?>" alt="<?= e($intro['alt']) ?>" width="1920" height="1080" loading="lazy">
    </figure>
  </div>
</section>

<section class="section section-alt" id="nasil">
  <div class="container">
    <div class="section-head reveal">
      <span class="eyebrow">Nasıl çalışır</span>
      <h2>Dört adımda üretim</h2>
    </div>
    <ol class="flow">
      <?php foreach (content('steps') as [$t, $d]): ?>
        <li class="reveal"><b><?= e($t) ?></b><span><?= e($d) ?></span></li>
      <?php endforeach; ?>
    </ol>
  </div>
</section>

<section class="section" id="moduller">
  <div class="container">
    <div class="section-head reveal">
      <span class="eyebrow">Modüller</span>
      <h2>Atölyenin her işi için bir modül</h2>
      <p>İhtiyacınız olan modülleri seçin; hepsi aynı programda, aynı parça listesiyle çalışır.</p>
    </div>
    <nav class="module-tabs" aria-label="Modüller">
      <?php foreach (content('modules') as $m): ?>
        <a href="#<?= e($m['id']) ?>"><?= e($m['title']) ?></a>
      <?php endforeach; ?>
    </nav>
    <?php foreach (content('modules') as $i => $m): ?>
      <article class="module<?= $i % 2 ? ' module-rev' : '' ?>" id="<?= e($m['id']) ?>">
        <div class="module-copy reveal">
          <span class="module-no"><?= sprintf('%02d', $i + 1) ?></span>
          <h3><?= e($m['title']) ?></h3>
          <p class="lead"><?= e($m['lead']) ?></p>
          <ul class="checklist">
            <?php foreach ($m['items'] as $item): ?><li><?= e($item) ?></li><?php endforeach; ?>
          </ul>
        </div>
        <div class="module-media reveal">
          <figure class="shot">
            <?php if ($m['anim']): ?>
              <?= loop_video($m['anim'], $m['anim_alt']) ?>
            <?php else: ?>
              <img src="<?= e(asset($m['image'])) ?>" alt="<?= e($m['alt']) ?>" width="1920" height="1080" loading="lazy">
            <?php endif; ?>
          </figure>
          <?php if ($m['anim']): ?>
            <button class="shot-thumb" type="button" data-full="<?= e(asset($m['image'])) ?>" data-caption="<?= e($m['alt']) ?>">
              <img src="<?= e(asset($m['image'])) ?>" alt="<?= e($m['alt']) ?>" width="1920" height="1080" loading="lazy">
              <span>Ekranı büyüt</span>
            </button>
          <?php endif; ?>
        </div>
      </article>
    <?php endforeach; ?>
  </div>
</section>

<section class="section section-alt" id="videolar">
  <div class="container">
    <div class="section-head reveal">
      <span class="eyebrow">Kullanım videoları</span>
      <h2>Adım adım, programın içinden</h2>
    </div>
    <?php foreach (content('tutorials') as $tv): ?>
      <article class="tutorial reveal">
        <div class="tutorial-media">
          <video controls playsinline preload="none" width="1280" height="720"
                 poster="<?= e(asset("anim/{$tv['file']}_poster.webp")) ?>" aria-label="<?= e($tv['title']) ?> videosu">
            <source src="<?= e(asset("anim/{$tv['file']}_720p.mp4")) ?>" type="video/mp4">
          </video>
        </div>
        <div class="tutorial-copy">
          <span class="pill"><?= e($tv['tag']) ?></span>
          <h3><?= e($tv['title']) ?></h3>
          <p class="lead"><?= e($tv['text']) ?></p>
          <ol class="mini-steps">
            <?php foreach ($tv['steps'] as $st): ?><li><?= e($st) ?></li><?php endforeach; ?>
          </ol>
          <p class="muted small">Sesli video · <?= e($tv['duration']) ?></p>
        </div>
      </article>
    <?php endforeach; ?>
  </div>
</section>

<section class="section">
  <div class="container">
    <div class="section-head reveal">
      <span class="eyebrow">Atölyeye hazır</span>
      <h2>Üretim çıktıları</h2>
    </div>
    <div class="cards cards-4">
      <?php foreach (content('outputs') as $i => [$t, $d]): ?>
        <article class="card reveal">
          <div class="icon" aria-hidden="true"><?= ['🏷️', '📋', '🧾', '✏️'][$i] ?? '•' ?></div>
          <h3><?= e($t) ?></h3>
          <p><?= e($d) ?></p>
        </article>
      <?php endforeach; ?>
    </div>
  </div>
</section>

<section class="section">
  <div class="container split split-rev">
    <div class="render-pair reveal">
      <?php foreach ($render['images'] as [$src, $alt]): ?>
        <button class="shot shot-btn" type="button" data-full="<?= e(asset($src)) ?>" data-caption="<?= e($alt) ?>">
          <img src="<?= e(asset($src)) ?>" alt="<?= e($alt) ?>" width="1920" height="1080" loading="lazy">
        </button>
      <?php endforeach; ?>
    </div>
    <div class="reveal">
      <span class="eyebrow">Gerçekçi render</span>
      <h2><?= e($render['title']) ?></h2>
      <p class="lead"><?= e($render['text']) ?></p>
      <a class="btn btn-ghost" href="<?= e(url('galeri')) ?>">Tüm ekranlar ve görseller →</a>
    </div>
  </div>
</section>

<?php if ($yt): ?>
<section class="section section-alt" id="video">
  <div class="container narrow center">
    <span class="eyebrow">Video</span>
    <h2>3 dakikada Titanium Cam</h2>
    <p>Çizimden makineye bütün akışı kısa videoda izleyin.</p>
    <div class="video-frame">
      <iframe src="<?= e($yt) ?>" title="Titanium Cam tanıtım videosu" loading="lazy" allow="accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen" allowfullscreen></iframe>
    </div>
  </div>
</section>
<?php endif; ?>

<section class="section">
  <div class="container narrow center reveal">
    <span class="eyebrow">Çok dilli</span>
    <h2><?= e($langs['title']) ?></h2>
    <p class="lead"><?= e($langs['text']) ?></p>
    <ul class="lang-list">
      <li>Türkçe</li><li>English</li><li>中文</li><li>Română</li><li>Português</li>
    </ul>
  </div>
</section>
