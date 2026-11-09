<?php
require_once dirname(__DIR__) . '/partials/form_helpers.php';
$pageDescription = 'Titanium Cam ve ' . cfg('company') . ' ile iletişime geçin: soru, lisans teklifi ve destek talepleri.';

$errors = [];
$old = [];
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    ['errors' => $errors, 'old' => $old] = handle_form('iletisim', [
        'name'    => ['Ad Soyad', true, 120],
        'email'   => ['E-posta', true, 160],
        'phone'   => ['Telefon', false, 30],
        'subject' => ['Konu', false, 160],
        'message' => ['Mesaj', true, 4000],
    ], 'iletisim');
}
?>
<section class="section">
  <div class="container split split-top">
    <div>
      <p class="lead">Titanium Cam hakkında sorularınız, lisans teklifi veya destek için bize yazın; en kısa sürede dönüş yapalım.</p>
      <ul class="contact-list">
        <li><span>Firma</span><?= e(cfg('company')) ?></li>
        <?php if (cfg('email')): ?><li><span>E-posta</span><a href="mailto:<?= e(cfg('email')) ?>"><?= e(cfg('email')) ?></a></li><?php endif; ?>
        <?php if (cfg('phone')): ?><li><span>Telefon</span><a href="tel:<?= e(preg_replace('/[^0-9+]/', '', cfg('phone'))) ?>"><?= e(cfg('phone')) ?></a></li><?php endif; ?>
        <?php if (cfg('address')): ?><li><span>Adres</span><?= e(cfg('address')) ?></li><?php endif; ?>
      </ul>
    </div>
    <form class="form card" method="post" action="<?= e(url('iletisim')) ?>" novalidate>
      <?= form_alerts($errors) ?>
      <?= csrf_field() ?>
      <div class="row-2">
        <label>Ad Soyad *<input name="name" required maxlength="120" autocomplete="name" value="<?= e($old['name'] ?? '') ?>"<?= field_attrs($errors, 'name') ?>><?= field_error($errors, 'name') ?></label>
        <label>E-posta *<input type="email" name="email" required maxlength="160" autocomplete="email" value="<?= e($old['email'] ?? '') ?>"<?= field_attrs($errors, 'email') ?>><?= field_error($errors, 'email') ?></label>
      </div>
      <div class="row-2">
        <label>Telefon<input type="tel" name="phone" maxlength="30" autocomplete="tel" value="<?= e($old['phone'] ?? '') ?>"<?= field_attrs($errors, 'phone') ?>><?= field_error($errors, 'phone') ?></label>
        <label>Konu<input name="subject" maxlength="160" value="<?= e($old['subject'] ?? '') ?>"></label>
      </div>
      <label>Mesaj *<textarea name="message" rows="6" required maxlength="4000"<?= field_attrs($errors, 'message') ?>><?= e($old['message'] ?? '') ?></textarea><?= field_error($errors, 'message') ?></label>
      <?= form_footer($errors, 'Mesajı Gönder') ?>
    </form>
  </div>
</section>
