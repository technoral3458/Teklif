<?php
/**
 * Titanium Cam tanıtım içeriği (kaynak: TitaniumCam_Web_Paketi/metinler/site_metinleri_TR.md).
 * Metinleri buradan güncelleyin; sayfalar bu diziyi kullanır.
 */
return [
    'seo' => [
        'title'          => 'Titanium Cam | Mobilya üretimi için CNC yazılımı',
        'description'    => "Titanium Cam; çizim, kapak, kabin, mutfak ve nesting modülleriyle mobilya atölyelerini tasarımdan CNC makinesine tek programda götüren Türkçe CAD/CAM yazılımıdır.",
        'keywords'       => 'cnc yazılımı, mobilya cam programı, nesting programı, kabin tasarım programı, mdf kapak programı, cnc router g-kod, mutfak tasarım programı, ebatlama, parça etiketi',
        'og_title'       => 'Titanium Cam: tasarımdan makineye tek program',
        'og_description' => 'Kapak, kabin, mutfak ve nesting tek programda. Türkçe, hızlı, atölyeye göre.',
        'og_image'       => 'img/logo/og_image_tr.jpg',
    ],

    'hero' => [
        'eyebrow' => 'Mobilya üretimi için CNC yazılımı',
        'title'   => 'Tasarımdan makineye, tek program',
        'text'    => "Kapak modelinden mutfak planına, nesting'den etiket basımına kadar bütün atölye işi Titanium Cam'de. Çizin, yerleştirin, kesin.",
        'image'   => 'img/vitrin/hero.webp',
        'alt'     => 'Titanium Cam mutfak planı 3B görünüm',
    ],

    'counters' => [
        [32, 'hazır parametrik kapak modeli'],
        [9,  'hazır makine profili'],
        [3,  'yerleşim yöntemi'],
        [5,  'dil desteği'],
    ],

    'intro' => [
        'title' => 'Atölyenin bütün işi tek ekranda',
        'text'  => "Ayrı ayrı programlar arasında dosya taşımaya son. Titanium Cam'de çizdiğiniz kapak, tasarladığınız kabin ya da kurduğunuz mutfak aynı programda parçalara ayrılır, levhaya yerleşir ve makinenize hazır NC koduyla gider. Ölçü değişince her şey kendiliğinden yeniden hesaplanır.",
        'image' => 'img/render/mutfak_render.webp',
        'alt'   => 'Titanium Cam ile hazırlanmış gerçekçi mutfak görüntüsü',
    ],

    'modules' => [
        [
            'id' => 'cizim', 'title' => 'Çizim ve CAM',
            'lead' => 'Alışık olduğunuz kısayollarla çizin, işlemi seçin, 3B simülasyonda kesimi görün.',
            'items' => [
                'AutoCAD tarzı komut kısayolları, doğrudan uzunluk girişi, ORTHO ve yakalama',
                'Çizgi, yay, çember, ofset, kırp / uzat, ölçü ve DXF içe / dışa aktarma',
                'Freze, cep, delik, kanal, V oyma, yüzey tarama ve 3B rölyef',
                'Parametrik çizim: ölçüyü değiştirin, çizim kendini uyarlasın',
                'Malzeme kaldırmalı 3B kesim simülasyonu',
            ],
            'image' => 'img/vitrin/01_cizim.webp', 'alt' => 'Çizim ekranında kemerli kapak ve işlemler',
            'anim' => 'cizim_simulasyon', 'anim_alt' => '3B kesim simülasyonu',
        ],
        [
            'id' => 'kapak', 'title' => 'Kapak üretimi',
            'lead' => "32 hazır parametrik model; her ölçüde yeniden çizilir, siparişten nesting'e tek tuş.",
            'items' => [
                'Kemerli, çerçeveli, dalga, diyagonal ve daha birçok hazır kapak modeli',
                'Kendi modelinizi çizin, parametrik yapın, kütüphanenize ekleyin',
                'Model ayarları resim üzerinde harfli ölçülerle düzenlenir',
                'Sipariş listesi: elle, Excel / CSV ya da yapıştırarak',
                'Web sitenizden gelen onaylı siparişleri doğrudan programa alın',
                'Vakum pres için renge göre yükleme planı',
            ],
            'image' => 'img/vitrin/02_kapak.webp', 'alt' => 'Kapak üretimi sipariş listesi ve önizleme',
            'anim' => 'kapak_parametrik', 'anim_alt' => 'Parametrik kapak: model ve ölçü değişimi',
        ],
        [
            'id' => 'kabin', 'title' => 'Kabin tasarımı',
            'lead' => 'Dolabı 3B ekranda kurun; parçalar, delikler ve bağlantılar kendiliğinden çıksın.',
            'items' => [
                'Bölme, raf, kayıt, kapak, çekmece ve arkalık ekleme; sürükle-bırak düzenleme',
                'Minifix, dübel, konfirmat ve gizli bağlantı delikleri otomatik',
                'Çekmece yapılandırması, menteşe kenarı, kulp ve PVC bant seçimi',
                'Köşe modülleri: L, 45° çapraz ve kör köşe',
                'Patlatılmış görünüm, kapakları açık montaj görünümü ve gerçekçi render',
            ],
            'image' => 'img/vitrin/03_kabin.webp', 'alt' => 'Kabin tasarımı 3B görünüm ve parça listesi',
            'anim' => 'kabin_3b', 'anim_alt' => 'Kabin 3B: kapaklar açılır, patlatılmış görünüm',
        ],
        [
            'id' => 'mutfak', 'title' => 'Mutfak planı',
            'lead' => 'Odayı çizin, modülleri dizin; plan, cephe ve 3B görünüm aynı anda hazır.',
            'items' => [
                'Düz, L ve U oda; kapı, pencere, kolon ve kiriş',
                'Kütüphaneden sürükle-bırak modül yerleştirme, komşular kendiliğinden kayar',
                'Ocak, evye, davlumbaz, buzdolabı, bulaşık makinesi ve fırın',
                'Tezgah, baza ve dolgu; çakışma uyarıları',
                "Gerçekçi render ve bütün mutfağı tek tuşla nesting'e gönderme",
            ],
            'image' => 'img/vitrin/04_mutfak.webp', 'alt' => 'Mutfak planı 3B görünüm',
            'anim' => 'mutfak_3b', 'anim_alt' => 'Mutfak planı 3B görünüm',
        ],
        [
            'id' => 'nesting', 'title' => 'Nesting',
            'lead' => 'Parçalar en az levhaya yerleşir; artıklar depoda, etiketler hazır.',
            'items' => [
                'Serbest, düz kesim (giyotin) ve gerçek şekil yerleşimi',
                'Malzeme ve kalınlığa göre gruplama, damar yönü koruma',
                'Artık deposu: önce artıkları kullan, kalanı depoya ekle',
                'Elle düzenleme: sürükle, döndür, başka levhaya taşı',
                'Küçük parçalar için güvenli kesim ve ayarlanabilir işlem sırası',
                'Barkodlu parça etiketi, ebatlama listesi, maliyet ve teklif',
            ],
            'image' => 'img/vitrin/05_nesting.webp', 'alt' => 'Nesting sonucu: parçalar levhaya yerleşmiş',
            'anim' => 'nesting_yerlesim', 'anim_alt' => 'Nesting: parçalar levhaya yerleşir',
        ],
        [
            'id' => 'makine', 'title' => 'Makine ve üretim',
            'lead' => 'Takımlarınızı ve makinenizi bir kez tanıtın, plakaları çalışma listesinden gönderin.',
            'items' => [
                'Resimli takım kütüphanesi ve 21 hazır kesici profili',
                'Takım kurulumu: magazin ve delik ünitesi',
                'Makine profili: canlı G-kod önizlemeli düzenleyici',
                'ISO / Fanuc, Mach3 / Mach4, GRBL ve LinuxCNC için hazır profiller',
                'Çalışma listesi: plakaları sırayla makineye gönderin, durumu izleyin',
            ],
            'image' => 'img/vitrin/06_makine.webp', 'alt' => 'Makine profili ve G-kod önizleme',
            'anim' => null, 'anim_alt' => '',
        ],
    ],

    'steps' => [
        ['Tasarla', 'Kapağı seçin, kabini kurun ya da mutfağı planlayın.'],
        ['Parçala', 'Parça listesi, delikler ve bantlar kendiliğinden çıkar.'],
        ['Yerleştir', 'Nesting parçaları en az levhaya dizer, etiketleri hazırlar.'],
        ['Kes', 'NC kodu makinenize gider; kesimi önce simülasyonda görün.'],
    ],

    'outputs' => [
        ['Parça etiketi', 'Barkodlu etiket; kendi tasarımınız, A4 tabaka ya da rulo.'],
        ['Ebatlama listesi', 'Parça listesini ebatlama makinesine CSV olarak verin.'],
        ['Maliyet ve teklif', 'Levha, kesim ve delik sayısından maliyet; PDF teklif.'],
        ['Markalama', 'Bant kenarı, askı borusu ve arkalık hattını parçaya işaretleyin.'],
    ],

    'render' => [
        'title' => 'Müşteriye gerçekçi görüntü',
        'text'  => 'Tasarladığınız kabini ya da mutfağı fotoğrafa yakın görüntüyle sunun; teklifle birlikte gönderin.',
        'images' => [
            ['img/render/kabin_render_koyu.webp', 'Kabinin gerçekçi görüntüsü, koyu renk'],
            ['img/render/kabin_render_beyaz.webp', 'Kabinin gerçekçi görüntüsü, beyaz renk'],
        ],
    ],

    'languages' => [
        'title' => 'Atölyenizin dilinde',
        'text'  => 'Türkçe, İngilizce, Çince, Romence ve Portekizce arayüz.',
    ],

    'closing' => [
        'title' => 'Atölyenizi bir adım öne taşıyın',
        'text'  => 'Tasarımdan makineye tek program: Titanium Cam.',
    ],

    // Galeri: [dosya adı, açıklama, klasör]
    'gallery' => [
        'Çizim ve CAM' => [
            ['cizim_2b', 'Çizim: kemerli kapak ve işlemler'],
            ['cizim_3b', 'Çizim: 3B görünüm'],
            ['kesim_simulasyonu', 'Malzeme kaldırmalı 3B simülasyon'],
            ['parametrik_cizim', 'Parametrik çizim: kendi kapak modeliniz'],
        ],
        'Kapak üretimi' => [
            ['kapak_siparis', 'Kapak üretimi: sipariş listesi'],
            ['kapak_model_duzenle', 'Kapak modelini resim üzerinde düzenleme'],
            ['online_siparis', 'Online sipariş havuzu'],
        ],
        'Kabin ve mutfak' => [
            ['kabin_tasarim', 'Kabin tasarımı'],
            ['kabin_patlatma', 'Kabin: patlatılmış görünüm'],
            ['kabin_ozellikleri', 'Kabin özellikleri paneli'],
            ['mutfak_plan', 'Mutfak planı'],
            ['mutfak_3b', 'Mutfak 3B görünüm'],
        ],
        'Nesting ve makine' => [
            ['nesting_sonuc', 'Nesting sonucu'],
            ['nesting_islem_sirasi', 'NC işlem sırası'],
            ['calisma_listesi', 'Çalışma listesi'],
            ['takim_kurulumu', 'Takım kurulumu'],
            ['makine_profili', 'Makine profili ve G-kod önizleme'],
        ],
    ],
    'gallery_renders' => [
        ['mutfak_render', 'Mutfak: gerçekçi görüntü'],
        ['mutfak_render_2', 'Mutfak: gerçekçi görüntü 2'],
        ['kabin_render_koyu', 'Kabin: gerçekçi görüntü, koyu'],
        ['kabin_render_beyaz', 'Kabin: gerçekçi görüntü, beyaz'],
    ],

    'faq' => [
        ['Titanium Cam hangi CNC makineleriyle çalışır?', 'G-kod kullanan CNC router makineleriyle çalışır. ISO / Fanuc, Mach3 / Mach4, GRBL ve LinuxCNC için hazır profiller vardır; makine profili düzenleyicisiyle başka kontrol ünitelerine de uyarlanır.'],
        ['Hangi dosya biçimlerini kullanabilirim?', 'DXF içe ve dışa aktarma, NC (G-kod) ve XML çıktısı, 3B yüzey için STL ve resim, sipariş için Excel / CSV, ebatlama için CSV, teklif ve etiket için PDF.'],
        ['Kendi kapak modelimi ekleyebilir miyim?', 'Evet. Modeli Çizim ekranında çizin, ölçülerini parametrik yapın ve kütüphaneye ekleyin. Model her sipariş ölçüsünde kendiliğinden yeniden çizilir.'],
        ['Program internet bağlantısı istiyor mu?', 'Hayır. Program bilgisayarınızda çalışır. İnternet yalnız online sipariş havuzu gibi isteğe bağlı özellikler için gerekir.'],
        ['Lisans nasıl çalışır?', 'Lisans bilgisayara bağlıdır ve modül bazlıdır: Kabin, Kapak, Nesting gibi ihtiyacınız olan modülleri seçersiniz.'],
        ['Bilgisayar gereksinimi nedir?', 'Windows 10 ya da 11 (64 bit) ve OpenGL 3.3 destekli bir ekran kartı.'],
    ],
];
