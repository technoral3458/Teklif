<?php
/**
 * Site yapılandırması.
 * Şirket bilgilerini buradan güncelleyin. Boş bırakılan iletişim bilgileri sitede gösterilmez.
 * Gizli bilgileri (şifre vb.) bu dosyaya değil, sunucudaki ortam değişkenlerine koyun.
 */
return [
    'site_name'    => 'Titanium Cam',
    'company'      => getenv('TC_COMPANY')  ?: 'ERSAN MAKİNA',
    'tagline'      => 'Mobilya üretimi için CNC yazılımı',
    'base_url'     => rtrim(getenv('TC_BASE_URL') ?: '', '/'),   // ör. https://titaniumcam.com (boşsa otomatik)
    'email'        => getenv('TC_EMAIL')    ?: 'admin@titaniumcam.com',
    'phone'        => getenv('TC_PHONE')    ?: '',               // ör. +90 5xx xxx xx xx
    'address'      => getenv('TC_ADDRESS')  ?: '',               // ör. Çorlu / Tekirdağ
    'whatsapp'     => getenv('TC_WHATSAPP') ?: '',               // ör. 905xxxxxxxxx
    'youtube_url'  => getenv('TC_YOUTUBE')  ?: '',               // tanıtım videosu, ör. https://www.youtube.com/watch?v=XXXX

    // İletişim / demo formları: mesajlar her zaman storage/ altına kaydedilir.
    // mail_to doluysa ayrıca PHP mail() ile e-posta gönderilir.
    'mail_to'      => getenv('TC_MAIL_TO')  ?: 'admin@titaniumcam.com',
    'mail_from'    => getenv('TC_MAIL_FROM') ?: 'admin@titaniumcam.com',

    'storage_dir'  => dirname(__DIR__) . '/storage',
    'form_cooldown_seconds' => 30,
];
