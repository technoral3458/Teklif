<?php
/** Form alanı yardımcıları. $errors ve $old dizileri sayfa kapsamından gelir. */

function field_error(array $errors, string $name): string
{
    return isset($errors[$name])
        ? '<p class="field-error" id="err-' . e($name) . '">' . e($errors[$name]) . '</p>'
        : '';
}

function field_attrs(array $errors, string $name): string
{
    return isset($errors[$name]) ? ' aria-invalid="true" aria-describedby="err-' . e($name) . '"' : '';
}

function form_alerts(array $errors): string
{
    $out = '';
    if ($msg = flash('success')) {
        $out .= '<div class="alert alert-success" role="status">' . e($msg) . '</div>';
    }
    if (!empty($errors['_form'])) {
        $out .= '<div class="alert alert-error" role="alert">' . e($errors['_form']) . '</div>';
    } elseif ($errors) {
        $out .= '<div class="alert alert-error" role="alert">Lütfen işaretli alanları kontrol edin.</div>';
    }
    return $out;
}

function form_footer(array $errors, string $button): string
{
    ob_start(); ?>
    <div class="hp" aria-hidden="true"><label>Web sitesi <input type="text" name="website" tabindex="-1" autocomplete="off"></label></div>
    <label class="check">
      <input type="checkbox" name="kvkk" value="1"<?= field_attrs($errors, 'kvkk') ?>>
      <span><a href="<?= e(url('kvkk')) ?>" target="_blank">KVKK aydınlatma metnini</a> okudum, bilgilerimin bu talep için işlenmesini onaylıyorum.</span>
    </label>
    <?= field_error($errors, 'kvkk') ?>
    <button class="btn btn-primary btn-lg" type="submit"><?= e($button) ?></button>
    <?php return ob_get_clean();
}
