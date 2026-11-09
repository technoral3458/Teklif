<?php
$pageDescription = 'Titanium Cam özellikleri: otomatik nesting, G-code üretimi, DXF çıktısı, ürün kataloğu, bayi portalı, rol tabanlı kullanıcı yönetimi ve Android uygulaması.';
$groups = [
    'CAM ve CNC' => [
        ['Otomatik nesting', 'Siparişlerdeki tüm parçaları seçtiğiniz plaka ölçüsüne göre yerleştirir; parçaları gerekirse 90° döndürür ve birden fazla plakaya dağıtır.'],
        ['Verim hesabı', 'Her nesting işi için plaka kullanım yüzdesi hesaplanır; fireyi sayılarla görürsünüz.'],
        ['Takım boşluğu (kerf)', 'Freze çapına göre parçalar arası boşluk otomatik bırakılır; parçalar birbirine zarar vermez.'],
        ['G-code üreteci', 'Fanuc/ISO uyumlu kontur frezeleme programı: devir (RPM), ilerleme (mm/dak), kesim derinliği ve takım çapı parametreleriyle.'],
        ['Çoklu plaka programı', 'Tek bir iş içinde her plaka için ayrı program numarası üretilir; operatör sırayla keser.'],
        ['DXF çıktısı', 'Kesim planını DXF olarak dışa aktarın; farklı CAM/CAD yazılımlarında da kullanın.'],
    ],
    'Sipariş ve Satış' => [
        ['Bayi portalı', 'Bayiler web üzerinden sipariş girer, ölçüleri ve adetleri kendisi tanımlar, sipariş durumunu anlık izler.'],
        ['Onay akışı', 'Taslak → Onay Bekliyor → Onaylandı / Reddedildi → CNC Kuyruğu → Üretimde → Tamamlandı.'],
        ['Otomatik sipariş numarası', 'Her siparişe benzersiz ve sıralı bir numara verilir; arşivde aramak kolaylaşır.'],
        ['Satış ekranı', 'Satış ekibi tüm bayilerin siparişlerini tek listede görür, filtreler ve onaylar.'],
    ],
    'Katalog ve Yönetim' => [
        ['Marka / model / renk kataloğu', 'Membran kapak modellerinizi, markalarınızı ve renk seçeneklerinizi merkezi olarak yönetin.'],
        ['Rol tabanlı yetki', 'Yönetici, Satış Ekibi, Bayi ve CNC Operatörü rolleri; herkes yalnızca yetkili olduğu ekranları görür.'],
        ['Kullanıcı yönetimi', 'Yeni bayi ve personel hesaplarını dakikalar içinde açın, yetkilerini düzenleyin.'],
        ['Web tabanlı', 'Kurulum gerektirmez; ofisten, atölyeden ya da evden tarayıcıyla erişin.'],
    ],
    'Mobil' => [
        ['Android uygulaması', 'Sahada fotoğraf veya STL/OBJ 3B model yükleyin, panel parametrelerini girin.'],
        ['3B görüntüleyici', 'Dilimlenmiş modeli parmakla döndürerek inceleyin.'],
        ['Kolay paylaşım', 'DXF/ZIP çıktısını WhatsApp ya da e-postayla doğrudan paylaşın.'],
    ],
];
?>
<section class="section">
  <div class="container">
    <p class="lead center narrow">Titanium Cam; membran kapak üretiminin her adımını kapsayacak şekilde, sahadaki gerçek ihtiyaçlardan yola çıkarak geliştirildi.</p>
    <?php foreach ($groups as $group => $items): ?>
      <h2 class="group-title"><?= e($group) ?></h2>
      <div class="cards cards-3">
        <?php foreach ($items as [$title, $desc]): ?>
          <article class="card card-sm">
            <h3><?= e($title) ?></h3>
            <p><?= e($desc) ?></p>
          </article>
        <?php endforeach; ?>
      </div>
    <?php endforeach; ?>
  </div>
</section>
