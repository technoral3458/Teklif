<?php
require_once dirname(__DIR__) . '/partials/form_helpers.php';
$pageDescription = 'Titanium Cam için ücretsiz demo talep edin. Kendi kapak ölçülerinizle canlı nesting ve G-code gösterimi.';

$packageOptions = ['Henüz bilmiyorum', 'Başlangıç', 'Profesyonel', 'Kurumsal'];
$errors = [];
$old = ['package' => in_array($_GET['paket'] ?? '', $packageOptions, true) ? $_GET['paket'] : ''];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    ['errors' => $errors, 'old' => $old] = handle_form('demo', [
        'name'     => ['Ad Soyad', true, 120],
        'company'  => ['Firma', true, 160],
        'email'    => ['E-posta', true, 160],
        'phone'    => ['Telefon', true, 30],
        'city'     => ['Şehir', false, 60],
        'machines' => ['CNC tezgâh sayısı', false, 10],
        'package'  => ['İlgilenilen paket', false, 40],
        'message'  => ['Notlar', false, 4000],
    ], 'demo');
    if (!in_array($old['package'] ?? '', $packageOptions, true)) {
        $old['package'] = '';
    }
}
?>
<section class="section">
  <div class="container split split-top">
    <div>
      <p class="lead">Formu doldurun, ekibimiz sizi arayarak uygun bir zamanda canlı demo planlasın.</p>
      <ul class="checklist">
        <li>Kendi kapak ölçülerinizle canlı nesting gösterimi</li>
        <li>Tezgâhınıza uygun G-code çıktısının incelenmesi</li>
        <li>Bayi portalı ve onay akışının tanıtımı</li>
        <li>Android uygulamasının sahada kullanımı</li>
        <li>İhtiyacınıza özel lisans teklifi</li>
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
        <label>CNC tezgâh sayısı<input type="number" name="machines" min="0" max="999" inputmode="numeric" value="<?= e($old['machines'] ?? '') ?>"></label>
        <label>Paket
          <select name="package">
            <?php foreach ($packageOptions as $opt): ?>
              <option<?= ($old['package'] ?? '') === $opt ? ' selected' : '' ?>><?= e($opt) ?></option>
            <?php endforeach; ?>
          </select>
        </label>
      </div>
      <label>Notlar<textarea name="message" rows="4" maxlength="4000" placeholder="Kullandığınız tezgâh markası, aylık üretim adedi vb."><?= e($old['message'] ?? '') ?></textarea></label>
      <?= form_footer($errors, 'Demo Talebi Gönder') ?>
    </form>
  </div>
</section>
