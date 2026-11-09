<?php
$pageDescription = cfg('company') . ' KVKK aydınlatma metni.';
?>
<section class="section">
  <div class="container narrow prose">
    <p class="alert alert-info">Bu metin bir şablondur. Yayına almadan önce hukuk danışmanınızla gözden geçirip şirket bilgilerinizle güncelleyin.</p>
    <h2>Veri Sorumlusu</h2>
    <p>6698 sayılı Kişisel Verilerin Korunması Kanunu ("KVKK") uyarınca kişisel verileriniz, veri sorumlusu sıfatıyla <?= e(cfg('company')) ?> ("Şirket") tarafından aşağıda açıklanan kapsamda işlenmektedir.</p>
    <h2>İşlenen Kişisel Veriler</h2>
    <p>İletişim ve demo talep formları aracılığıyla paylaştığınız ad soyad, firma adı, e-posta adresi, telefon numarası, şehir ve mesaj içeriği ile teknik olarak IP adresiniz.</p>
    <h2>İşleme Amaçları</h2>
    <p>Talebinize yanıt verilmesi, demo görüşmesinin planlanması, teklif hazırlanması ve sizinle iletişim kurulması.</p>
    <h2>Hukuki Sebep ve Toplama Yöntemi</h2>
    <p>Verileriniz web sitemizdeki formlar aracılığıyla elektronik ortamda; KVKK m.5/2 (c) bir sözleşmenin kurulmasıyla doğrudan ilgili olması ve (f) meşru menfaat hukuki sebeplerine dayanılarak toplanmaktadır.</p>
    <h2>Aktarım</h2>
    <p>Kişisel verileriniz, yasal yükümlülükler dışında üçüncü kişilerle paylaşılmaz.</p>
    <h2>Haklarınız</h2>
    <p>KVKK m.11 kapsamındaki haklarınıza ilişkin taleplerinizi <a href="mailto:<?= e(cfg('email')) ?>"><?= e(cfg('email')) ?></a> adresine iletebilirsiniz.</p>
  </div>
</section>
