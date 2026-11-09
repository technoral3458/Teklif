<?php
declare(strict_types=1);

$config = require __DIR__ . '/config.php';

ini_set('display_errors', '0');
error_reporting(E_ALL);

session_set_cookie_params([
    'lifetime' => 0,
    'path'     => '/',
    'secure'   => (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off'),
    'httponly' => true,
    'samesite' => 'Lax',
]);
session_name('tcsid');
session_start();

header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: SAMEORIGIN');
header('Referrer-Policy: strict-origin-when-cross-origin');

/** HTML çıktısı için kaçış. */
function e(?string $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function cfg(string $key, $default = null)
{
    global $config;
    return $config[$key] ?? $default;
}

/** Site içi bağlantı üretir. */
function url(string $path = ''): string
{
    return cfg('base_url') . '/' . ltrim($path, '/');
}

function asset(string $path): string
{
    $file = (defined('PUBLIC_DIR') ? PUBLIC_DIR : dirname(__DIR__) . '/public') . '/assets/' . ltrim($path, '/');
    $v = is_file($file) ? filemtime($file) : 1;
    return url('assets/' . ltrim($path, '/')) . '?v=' . $v;
}

function csrf_token(): string
{
    if (empty($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf'];
}

function csrf_field(): string
{
    return '<input type="hidden" name="csrf" value="' . e(csrf_token()) . '">';
}

function csrf_valid(): bool
{
    $sent = $_POST['csrf'] ?? '';
    return is_string($sent) && !empty($_SESSION['csrf']) && hash_equals($_SESSION['csrf'], $sent);
}

function flash(string $key, $value = null)
{
    if ($value !== null) {
        $_SESSION['flash'][$key] = $value;
        return null;
    }
    $v = $_SESSION['flash'][$key] ?? null;
    unset($_SESSION['flash'][$key]);
    return $v;
}

function redirect(string $path): void
{
    header('Location: ' . url($path), true, 303);
    exit;
}

function post_str(string $key, int $max = 2000): string
{
    $v = $_POST[$key] ?? '';
    if (!is_string($v)) {
        return '';
    }
    $v = trim(str_replace("\0", '', $v));
    return mb_substr($v, 0, $max);
}

/**
 * İletişim/demo formunu işler: doğrular, kaydeder, isteğe bağlı e-posta gönderir.
 * @param array $fields alan => [etiket, zorunlu mu, maks uzunluk]
 * @return array{errors: array, old: array}
 */
function handle_form(string $type, array $fields, string $redirectTo): array
{
    $errors = [];
    $data = [];

    if (!csrf_valid()) {
        $errors['_form'] = 'Oturum süreniz dolmuş olabilir. Lütfen formu tekrar gönderin.';
    }
    // Bot tuzağı: görünmeyen alan doluysa sessizce başarılı say.
    if (post_str('website') !== '') {
        flash('success', 'Mesajınız alındı. Teşekkür ederiz.');
        redirect($redirectTo);
    }

    foreach ($fields as $name => [$label, $required, $max]) {
        $data[$name] = post_str($name, $max);
        if ($required && $data[$name] === '') {
            $errors[$name] = "$label alanı zorunludur.";
        }
    }
    if (isset($data['email']) && $data['email'] !== '' && !filter_var($data['email'], FILTER_VALIDATE_EMAIL)) {
        $errors['email'] = 'Geçerli bir e-posta adresi girin.';
    }
    if (isset($data['phone']) && $data['phone'] !== '' && !preg_match('/^[0-9 +()\-]{7,20}$/', $data['phone'])) {
        $errors['phone'] = 'Geçerli bir telefon numarası girin.';
    }
    if (empty($_POST['kvkk'])) {
        $errors['kvkk'] = 'Devam etmek için aydınlatma metnini onaylayın.';
    }

    $last = $_SESSION['last_form_at'] ?? 0;
    if (!$errors && time() - $last < (int) cfg('form_cooldown_seconds', 30)) {
        $errors['_form'] = 'Çok kısa sürede tekrar gönderim yaptınız. Lütfen biraz bekleyin.';
    }

    if ($errors) {
        return ['errors' => $errors, 'old' => $data];
    }

    $record = [
        'type' => $type,
        'time' => date('c'),
        'ip'   => $_SERVER['REMOTE_ADDR'] ?? '',
        'data' => $data,
    ];
    $dir = cfg('storage_dir');
    if (!is_dir($dir)) {
        @mkdir($dir, 0750, true);
    }
    $ok = @file_put_contents(
        $dir . '/' . $type . '-' . date('Y-m') . '.jsonl',
        json_encode($record, JSON_UNESCAPED_UNICODE) . "\n",
        FILE_APPEND | LOCK_EX
    );
    if ($ok === false) {
        error_log("Titanium Cam: form kaydı yazılamadı ($dir)");
    }

    if (cfg('mail_to')) {
        $body = '';
        foreach ($fields as $name => [$label]) {
            $body .= "$label: " . ($data[$name] ?? '') . "\n";
        }
        $headers = [
            'From: ' . cfg('mail_from'),
            'Content-Type: text/plain; charset=UTF-8',
        ];
        if (!empty($data['email'])) {
            $headers[] = 'Reply-To: ' . str_replace(["\r", "\n"], '', $data['email']);
        }
        $subject = '=?UTF-8?B?' . base64_encode('Titanium Cam - ' . ($type === 'demo' ? 'Demo talebi' : 'İletişim formu')) . '?=';
        @mail(cfg('mail_to'), $subject, $body, implode("\r\n", $headers));
    }

    $_SESSION['last_form_at'] = time();
    flash('success', $type === 'demo'
        ? 'Demo talebiniz alındı. Ekibimiz en kısa sürede sizinle iletişime geçecek.'
        : 'Mesajınız alındı. En kısa sürede dönüş yapacağız.');
    redirect($redirectTo);
}
