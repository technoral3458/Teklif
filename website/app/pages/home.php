<?php
$pageDescription = 'Titanium Cam; membran kapak siparişlerini bayiden CNC tezgâhına kadar tek sistemde yönetir: otomatik nesting, Fanuc/ISO G-code, DXF çıktısı ve mobil uygulama.';

// Hero illüstrasyonu için örnek plaka yerleşimi (mm ölçeğinde, 2800x2070 plaka).
$pieces = [
    [20, 20, 596, 716], [626, 20, 596, 716], [1232, 20, 446, 716], [1688, 20, 446, 716], [2144, 20, 396, 716], [2550, 20, 230, 1046],
    [20, 746, 796, 396], [826, 746, 796, 396], [1632, 746, 446, 396], [2088, 746, 452, 396],
    [20, 1152, 396, 896], [426, 1152, 396, 896], [832, 1152, 596, 446], [832, 1608, 596, 440], [1438, 1152, 696, 296],
    [1438, 1458, 696, 296], [1438, 1764, 696, 284], [2144, 1152, 636, 446], [2144, 1608, 636, 440],
];
?>
<section class="hero">
  <div class="container hero-grid">
    <div class="hero-copy">
      <span class="eyebrow">Membran kapak üreticileri için CAM yazılımı</span>
      <h1>Siparişten <span class="grad">CNC tezgâhına</span> tek tıkla.</h1>
      <p class="lead">Titanium Cam; bayi siparişlerini toplar, kapakları plakalara en verimli şekilde yerleştirir ve tezgâhınıza hazır G-code üretir. Fire azalır, hata kalmaz, üretim hızlanır.</p>
      <div class="hero-actions">
        <a class="btn btn-primary btn-lg" href="<?= e(url('demo')) ?>">Ücretsiz Demo İste</a>
        <a class="btn btn-ghost btn-lg" href="<?= e(url('nasil-calisir')) ?>">Nasıl çalışır?</a>
      </div>
      <ul class="hero-points">
        <li>Otomatik nesting</li>
        <li>Fanuc / ISO G-code</li>
        <li>Bayi portalı</li>
        <li>Android uygulama</li>
      </ul>
    </div>
    <figure class="hero-visual" aria-label="Titanium Cam nesting ekranı örneği">
      <div class="window">
        <div class="window-bar"><i></i><i></i><i></i><span>Nesting İşi #128 — 2800 × 2070 mm · MDF 18 mm</span></div>
        <svg class="nest" viewBox="0 0 2800 2070" role="img" aria-label="Plaka üzerinde yerleştirilmiş kapak parçaları">
          <rect class="plate" x="0" y="0" width="2800" height="2070" rx="12"/>
          <?php foreach ($pieces as $i => [$x, $y, $w, $h]): ?>
            <g class="piece" style="--d:<?= $i * 60 ?>ms">
              <rect x="<?= $x ?>" y="<?= $y ?>" width="<?= $w ?>" height="<?= $h ?>" rx="10"/>
              <rect class="groove" x="<?= $x + 60 ?>" y="<?= $y + 60 ?>" width="<?= max($w - 120, 10) ?>" height="<?= max($h - 120, 10) ?>" rx="6"/>
            </g>
          <?php endforeach; ?>
          <path class="toolpath" d="M20 20 H616 V736 H20 Z M626 20 H1222 V736 H626 Z M1232 20 H1678 V736 H1232 Z"/>
        </svg>
        <div class="window-stats">
          <div><b>%91,4</b><span>Plaka verimi</span></div>
          <div><b>19</b><span>Parça</span></div>
          <div><b>6 mm</b><span>Takım çapı</span></div>
          <div><b>G-code</b><span>Hazır ✓</span></div>
        </div>
      </div>
    </figure>
  </div>
</section>

<section class="section">
  <div class="container">
    <div class="section-head">
      <span class="eyebrow">Neden Titanium Cam?</span>
      <h2>Atölyenizin bütün akışı, tek bir sistemde</h2>
      <p>Excel tabloları, telefonla alınan ölçüler ve elle çizilen kesim planları yerine; uçtan uca dijital bir üretim hattı.</p>
    </div>
    <div class="cards cards-3">
      <article class="card">
        <div class="icon"><svg viewBox="0 0 24 24"><path d="M3 3h8v8H3zM13 3h8v5h-8zM13 10h8v11h-8zM3 13h8v8H3z"/></svg></div>
        <h3>Akıllı Nesting</h3>
        <p>Kapakları plakaya otomatik yerleştirir, gerektiğinde döndürür ve her plaka için verim yüzdesini hesaplar. Daha az fire, daha az plaka.</p>
      </article>
      <article class="card">
        <div class="icon"><svg viewBox="0 0 24 24"><path d="M4 17l6-6-6-6M12 19h8"/></svg></div>
        <h3>Tezgâha Hazır G-code</h3>
        <p>Takım çapı, ilerleme, devir ve kesim derinliğine göre Fanuc/ISO uyumlu kontur frezeleme programı üretir. DXF çıktısı da hazır.</p>
      </article>
      <article class="card">
        <div class="icon"><svg viewBox="0 0 24 24"><path d="M16 11a4 4 0 1 0-8 0M4 21a8 8 0 0 1 16 0M19 8v6M22 11h-6"/></svg></div>
        <h3>Bayi Sipariş Portalı</h3>
        <p>Bayileriniz marka, model, renk ve ölçüleri kendisi girer; satış ekibi onaylar, sipariş doğrudan CNC kuyruğuna düşer.</p>
      </article>
    </div>
  </div>
</section>

<section class="section section-alt">
  <div class="container">
    <div class="section-head">
      <span class="eyebrow">Üretim akışı</span>
      <h2>Siparişin yolculuğu</h2>
    </div>
    <ol class="flow">
      <li><b>Taslak</b><span>Bayi siparişi oluşturur</span></li>
      <li><b>Onay</b><span>Satış ekibi kontrol eder</span></li>
      <li><b>CNC Kuyruğu</b><span>Nesting işine eklenir</span></li>
      <li><b>Üretim</b><span>G-code tezgâha gönderilir</span></li>
      <li><b>Tamamlandı</b><span>Bayiye bildirilir</span></li>
    </ol>
  </div>
</section>

<section class="section">
  <div class="container split">
    <div>
      <span class="eyebrow">Herkes için doğru ekran</span>
      <h2>Rol tabanlı çalışma</h2>
      <p>Her kullanıcı yalnızca kendi işine odaklanır. Yetkiler role göre ayrılır; kim neyi ne zaman değiştirdi bellidir.</p>
      <ul class="checklist">
        <li><b>Yönetici</b> — katalog, kullanıcılar ve tüm raporlar</li>
        <li><b>Satış Ekibi</b> — sipariş onayı, fiyat ve bayi takibi</li>
        <li><b>Bayi</b> — sipariş girişi ve durum takibi</li>
        <li><b>CNC Operatörü</b> — nesting işleri, kuyruk ve G-code</li>
      </ul>
      <a class="btn btn-ghost" href="<?= e(url('ozellikler')) ?>">Tüm özellikler →</a>
    </div>
    <div class="stat-grid">
      <div class="stat"><b>4</b><span>Kullanıcı rolü</span></div>
      <div class="stat"><b>7</b><span>Adımlı sipariş takibi</span></div>
      <div class="stat"><b>DXF · G-code</b><span>Çıktı biçimleri</span></div>
      <div class="stat"><b>Web + Android</b><span>Platformlar</span></div>
    </div>
  </div>
</section>

<section class="section section-alt">
  <div class="container split split-rev">
    <div class="phone-mock" aria-hidden="true">
      <div class="phone">
        <div class="phone-screen">
          <div class="ph-bar">Parametrik Kesim</div>
          <svg viewBox="0 0 200 200" class="ph-cube">
            <polygon points="100,30 170,70 100,110 30,70" class="f1"/>
            <polygon points="30,70 100,110 100,180 30,140" class="f2"/>
            <polygon points="170,70 100,110 100,180 170,140" class="f3"/>
          </svg>
          <div class="ph-row"><span>Panel kalınlığı</span><b>18 mm</b></div>
          <div class="ph-row"><span>Panel sayısı</span><b>24</b></div>
          <div class="ph-btn">DXF / ZIP indir</div>
        </div>
      </div>
    </div>
    <div>
      <span class="eyebrow">Sahada da yanınızda</span>
      <h2>Titanium Cam Mobil</h2>
      <p>Android uygulamasıyla sahada fotoğraf çekin veya 3B model (STL/OBJ) yükleyin; panel parametrelerini girin, sunucu modeli panellere dilimlesin. Sonucu 3B olarak inceleyin, DXF/ZIP dosyasını WhatsApp veya e-postayla paylaşın.</p>
      <a class="btn btn-ghost" href="<?= e(url('mobil')) ?>">Mobil uygulamayı keşfedin →</a>
    </div>
  </div>
</section>
