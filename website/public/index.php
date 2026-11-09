<?php
declare(strict_types=1);

/*
 * Ön denetleyici (front controller). Tüm istekler buraya yönlendirilir.
 * Geliştirme: php -S localhost:8000 -t website/public website/public/index.php
 */

// Yerleşik PHP sunucusunda statik dosyaları doğrudan sun.
if (PHP_SAPI === 'cli-server') {
    $file = __DIR__ . parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
    if ($file !== __FILE__ && is_file($file)) {
        return false;
    }
}

// Uygulama klasörü: önerilen yerleşimde bir üst klasörde (../app), FTP girişi doğrudan
// public_html'e açılan hostinglerde ise bu klasörün içindedir (./app).
define('PUBLIC_DIR', __DIR__);
define('APP_DIR', is_dir(__DIR__ . '/app') ? __DIR__ . '/app' : dirname(__DIR__) . '/app');

require APP_DIR . '/bootstrap.php';

$routes = [
    ''            => ['home',     'Ana Sayfa'],
    'ozellikler'  => ['features', 'Özellikler'],
    'nasil-calisir' => ['how',    'Nasıl Çalışır'],
    'mobil'       => ['mobile',   'Mobil Uygulama'],
    'paketler'    => ['packages', 'Lisans Paketleri'],
    'sss'         => ['faq',      'Sık Sorulan Sorular'],
    'hakkimizda'  => ['about',    'Hakkımızda'],
    'iletisim'    => ['contact',  'İletişim'],
    'demo'        => ['demo',     'Demo Talebi'],
    'kvkk'        => ['kvkk',     'KVKK Aydınlatma Metni'],
];

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$base = parse_url(cfg('base_url'), PHP_URL_PATH) ?: '';
if ($base !== '' && str_starts_with($path, $base)) {
    $path = substr($path, strlen($base));
}
$slug = trim($path, '/');
if ($slug === 'index.php') {
    $slug = '';
}

$host = cfg('base_url') ?: ((!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off' ? 'https' : 'http') . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost'));

if ($slug === 'robots.txt') {
    header('Content-Type: text/plain; charset=UTF-8');
    echo "User-agent: *\nAllow: /\nSitemap: $host/sitemap.xml\n";
    exit;
}

if ($slug === 'sitemap.xml') {
    header('Content-Type: application/xml; charset=UTF-8');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' . "\n";
    foreach (array_keys($routes) as $r) {
        echo '  <url><loc>' . e($host . '/' . $r) . "</loc></url>\n";
    }
    echo "</urlset>\n";
    exit;
}

if (!isset($routes[$slug])) {
    http_response_code(404);
    [$page, $title] = ['404', 'Sayfa Bulunamadı'];
} else {
    [$page, $title] = $routes[$slug];
}

$current = $slug;
$pageTitle = $title;
$pageDescription = cfg('tagline');

// Sayfa içeriğini önce tamponla (formlar yönlendirme yapabilir).
ob_start();
require APP_DIR . '/pages/' . $page . '.php';
$content = ob_get_clean();

require APP_DIR . '/partials/layout.php';
