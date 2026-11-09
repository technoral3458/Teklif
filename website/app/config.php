<?php
/**
 * Site yapılandırması.
 * Şirket bilgilerini buradan güncelleyin. Gizli bilgileri (şifre vb.)
 * bu dosyaya değil, sunucudaki ortam değişkenlerine koyun.
 */
return [
    'site_name'    => 'Titanium Cam',
    'company'      => getenv('TC_COMPANY')  ?: 'Şirket Adı',
    'tagline'      => 'Membran kapak üretimi için uçtan uca CAM ve üretim yönetimi',
    'base_url'     => rtrim(getenv('TC_BASE_URL') ?: '', '/'),   // ör. https://www.titaniumcam.com.tr
    'email'        => getenv('TC_EMAIL')    ?: 'info@ornek.com.tr',
    'phone'        => getenv('TC_PHONE')    ?: '+90 (000) 000 00 00',
    'address'      => getenv('TC_ADDRESS')  ?: 'Adres bilgisi, İl / Türkiye',
    'whatsapp'     => getenv('TC_WHATSAPP') ?: '',               // ör. 905xxxxxxxxx

    // İletişim / demo formları: mesajlar her zaman storage/ altına kaydedilir.
    // mail_to doluysa ayrıca PHP mail() ile e-posta gönderilir.
    'mail_to'      => getenv('TC_MAIL_TO')  ?: '',
    'mail_from'    => getenv('TC_MAIL_FROM') ?: 'no-reply@localhost',

    'storage_dir'  => dirname(__DIR__) . '/storage',
    'form_cooldown_seconds' => 30,
];
