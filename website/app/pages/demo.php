<?php
require_once dirname(__DIR__) . '/partials/form_helpers.php';
$pageDescription = 'Titanium Cam için ücretsiz demo talep edin: kendi parçalarınızla kapak, kabin, mutfak, nesting ve makine modüllerini canlı görün.';

$moduleOptions = array_column(content('modules'), 'title');
$errors = [];
$old = [];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    ['errors' => $errors, 'old' => $old] = handle_form('demo', [
        'name'     => ['Ad Soyad', true, 120],
        'company'  => ['Firma', true, 160],
        'email'    => ['E-posta', true, 160],
        'phone'    => ['Telefon', true, 30],
        'city'     => ['Şehir', false, 60],
        'machines' => ['CNC makine sayısı', false, 10],
        'controller' => ['Makine kontrol ünitesi', false, 80],
        'modules'  => ['İlgilenilen modüller', false, 300],
        'message'  => ['Notlar', false, 4000],
    ], 'demo');
}
?>
<section class="section">
  <div class="container split split-top">
    <div>
      <p class="lead">Formu doldurun, ekibimiz sizi arayarak uygun bir zamanda canlı demo planlasın.</p>
      <ul class="checklist">
        <li>Kendi kapak modelleriniz ve ölçülerinizle canlı gösterim</li>
        <li>Kabin ve mutfak tasarımından parça listesine geçiş</li>
        <li>Nesting, artık deposu ve barkodlu etiketler</li>
        <li>Makinenize uygun profil ve NC kodu çıktısı</li>
        <li>İhtiyacınız olan modüllere göre lisans teklifi</li>
      </ul>
      <p class="muted small">Demo görüşmesi yaklaşık 30–45 dakika sürer; çevrim içi veya yerinde yapılabilir.</p>
    </div>
    <form class="form card" method="post" action="<?= e(url('demo')) ?>" novalidate>
      <?= form_alerts($errors) ?>
      <?= csrf_field() ?>
      <div class="row-2">
        <label>Ad Soyad *<input name="name" required maxlength="120" autocomplete="name" value="<?= e($old['name'] ?? '') ?>"<?= field_attrs($errors, 'name') ?>><?= field_error($errors, 'name') ?></label>
        <label>Firma *<input name="company" required maxlength="160" autocomplete="organization" value="<?= e($old['company'] ?? '') ?>"<?= field_attrs($errors, 'company') ?>><?= field_error($errors, 'company') ?></label>
      </div>
      <div class="row-2">
        <label>E-posta *<input type="email" name="email" required maxlength="160" autocomplete="email" value="<?= e($old['email'] ?? '') ?>"<?= field_attrs($errors, 'email') ?>><?= field_error($errors, 'email') ?></label>
        <label>Telefon *<input type="tel" name="phone" required maxlength="30" autocomplete="tel" value="<?= e($old['phone'] ?? '') ?>"<?= field_attrs($errors, 'phone') ?>><?= field_error($errors, 'phone') ?></label>
      </div>
      <div class="row-3">
        <label>Şehir<input name="city" maxlength="60" autocomplete="address-level1" value="<?= e($old['city'] ?? '') ?>"></label>
        <label>CNC makine sayısı<input type="number" name="machines" min="0" max="999" inputmode="numeric" value="<?= e($old['machines'] ?? '') ?>"></label>
        <label>Makine kontrol ünitesi<input name="controller" maxlength="80" placeholder="ör. Mach3, Syntec, NC Studio" value="<?= e($old['controller'] ?? '') ?>"></label>
      </div>
      <fieldset class="checks">
        <legend>İlgilendiğiniz modüller</legend>
        <?php $picked = array_map('trim', explode(',', $old['modules'] ?? '')); ?>
        <?php foreach ($moduleOptions as $opt): ?>
          <label class="check"><input type="checkbox" name="modules[]" value="<?= e($opt) ?>"<?= in_array($opt, $picked, true) ? ' checked' : '' ?>> <span><?= e($opt) ?></span></label>
        <?php endforeach; ?>
      </fieldset>
      <label>Notlar<textarea name="message" rows="4" maxlength="4000" placeholder="Ürettiğiniz ürünler, şu an kullandığınız program, aylık üretim adedi vb."><?= e($old['message'] ?? '') ?></textarea></label>
      <?= form_footer($errors, 'Demo Talebi Gönder') ?>
    </form>
  </div>
</section>
