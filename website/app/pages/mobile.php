<?php
$pageDescription = 'Titanium Cam Android uygulaması: fotoğraf veya STL/OBJ 3B model yükleyin, panellere dilimleyin, DXF/ZIP olarak paylaşın.';
?>
<section class="section">
  <div class="container split">
    <div>
      <p class="lead">Ölçü almak, müşteriye göstermek ve kesim dosyasını göndermek artık tek bir telefonda.</p>
      <ul class="checklist">
        <li><b>Fotoğraf çek veya galeriden seç</b> — kamera entegrasyonuyla sahada anında.</li>
        <li><b>3B model yükle</b> — STL ve OBJ dosyalarını destekler.</li>
        <li><b>Parametreleri gir</b> — panel kalınlığı ve ölçü bilgileri; uygulama girdileri doğrular.</li>
        <li><b>Sunucu dilimlesin</b> — model panellere bölünür, sonuç özetiyle ve uyarılarıyla gelir.</li>
        <li><b>3B incele</b> — parmakla döndürülebilen görünümde kontrol edin.</li>
        <li><b>Paylaş</b> — DXF/ZIP dosyasını WhatsApp veya e-postayla gönderin.</li>
      </ul>
      <p class="muted small">Android 8.0 (API 26) ve üzeri cihazlarda çalışır. Sunucu adresi uygulamanın Ayarlar ekranından girilir; oturumlar güvenli çerezle 30 gün açık kalır.</p>
      <a class="btn btn-primary" href="<?= e(url('demo')) ?>">Mobil demo isteyin</a>
    </div>
    <div class="phone-mock" aria-hidden="true">
      <div class="phone">
        <div class="phone-screen">
          <div class="ph-bar">İş listesi</div>
          <div class="ph-row"><span>Mutfak dolabı — Ankara</span><b class="ok">Hazır</b></div>
          <div class="ph-row"><span>Banyo modülü — İzmir</span><b class="ok">Hazır</b></div>
          <div class="ph-row"><span>Gardırop — Bursa</span><b class="wait">İşleniyor</b></div>
          <svg viewBox="0 0 200 200" class="ph-cube">
            <polygon points="100,30 170,70 100,110 30,70" class="f1"/>
            <polygon points="30,70 100,110 100,180 30,140" class="f2"/>
            <polygon points="170,70 100,110 100,180 170,140" class="f3"/>
          </svg>
          <div class="ph-btn">+ Yeni iş</div>
        </div>
      </div>
    </div>
  </div>
</section>
