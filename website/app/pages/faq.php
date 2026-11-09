<?php
$pageDescription = 'Titanium Cam hakkında sık sorulan sorular: kurulum, tezgâh uyumluluğu, mobil uygulama, veri güvenliği ve destek.';
$faqs = [
    ['Titanium Cam kurulum gerektiriyor mu?', 'Hayır. Titanium Cam web tabanlıdır; kullanıcılar tarayıcı üzerinden erişir. Kurumsal pakette yazılımı kendi Linux sunucunuza da kurabiliyoruz.'],
    ['Hangi CNC tezgâhlarıyla uyumlu?', 'Üretilen G-code, Fanuc/ISO standart söz dizimini kullanır ve bu standardı destekleyen tezgâhların büyük çoğunluğunda çalışır. Tezgâhınıza özel bir kontrol ünitesi varsa post-processor uyarlaması yapıyoruz.'],
    ['Plaka ölçülerini ve takım ayarlarını değiştirebilir miyim?', 'Evet. Her nesting işinde plaka eni/boyu, freze çapı, takım boşluğu (kerf), ilerleme hızı, devir ve kesim derinliği ayrı ayrı girilebilir.'],
    ['Bayilerim sisteme nasıl erişir?', 'Yönetici her bayi için bir kullanıcı hesabı açar. Bayi yalnızca kendi siparişlerini görür ve yeni sipariş girebilir.'],
    ['Mobil uygulama iOS için de var mı?', 'Şu an Android (8.0 ve üzeri) sürümü mevcuttur. Diğer platformlar için yol haritamızı demo görüşmesinde paylaşabiliriz.'],
    ['Verilerim güvende mi?', 'Tüm kullanıcılar rol tabanlı yetkilerle çalışır, oturumlar güvenli çerezlerle korunur. Kendi sunucunuza kurulumda veriler tamamen sizin altyapınızda kalır.'],
    ['Deneme sürümü var mı?', 'Demo talep formunu doldurduğunuzda, kendi kapak ölçülerinizle canlı bir gösterim yapıyor ve değerlendirme süreci için erişim sağlıyoruz.'],
    ['Eğitim ve destek veriyor musunuz?', 'Evet. Tüm paketlerde kullanım eğitimi ve destek sunuyoruz; kapsam pakete göre değişir.'],
];
?>
<section class="section">
  <div class="container narrow">
    <div class="faq">
      <?php foreach ($faqs as [$q, $a]): ?>
        <details>
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
