<?php
$pageDescription = 'Titanium Cam ile bir membran kapak siparişinin bayiden CNC tezgâhına kadar izlediği yol, adım adım.';
$steps = [
    ['Katalogu tanımlayın', 'Markalarınızı, kapak modellerinizi ve renklerinizi bir kez sisteme girin. Bayileriniz bu listeden seçim yapar; yanlış model ya da renk siparişi ortadan kalkar.'],
    ['Bayi siparişi girer', 'Bayi portalda model, renk, en × boy ve adet bilgilerini girer. Sipariş otomatik numaralanır ve satış ekibinin onayına düşer.'],
    ['Satış onaylar', 'Satış ekibi siparişi kontrol eder; onaylanan sipariş CNC kuyruğuna aktarılır, reddedilen sipariş açıklamasıyla bayiye geri döner.'],
    ['Nesting işi oluşturulur', 'CNC operatörü kuyruktaki siparişleri seçer; plaka ölçüsü, takım çapı, kerf, devir, ilerleme ve kesim derinliğini girer. Titanium Cam parçaları plakalara yerleştirir ve verimi gösterir.'],
    ['G-code tezgâha gider', 'Her plaka için ayrı programlı G-code dosyası indirilir ve tezgâha yüklenir. İsterseniz DXF çıktısını da alabilirsiniz.'],
    ['Üretim tamamlanır', 'Operatör işi tamamlandı olarak işaretler; sipariş durumu güncellenir ve bayi siparişinin hazır olduğunu görür.'],
];
?>
<section class="section">
  <div class="container narrow">
    <ol class="steps">
      <?php foreach ($steps as $i => [$title, $desc]): ?>
        <li>
          <span class="step-no"><?= $i + 1 ?></span>
          <div>
            <h3><?= e($title) ?></h3>
            <p><?= e($desc) ?></p>
          </div>
        </li>
      <?php endforeach; ?>
    </ol>
  </div>
</section>

<section class="section section-alt">
  <div class="container split">
    <div>
      <span class="eyebrow">Örnek çıktı</span>
      <h2>Okunabilir, standart G-code</h2>
      <p>Titanium Cam'in ürettiği programlar ISO/Fanuc söz dizimini kullanır; operatörünüz ilk bakışta anlar, tezgâhınız sorunsuz çalıştırır.</p>
    </div>
<pre class="code" aria-label="Örnek G-code"><code>%
O0001 (MEMBRAN PLAKA-1)
(19 PARCA - 1. PLAKA)
G21 (METRIK)
G17 G40 G49 G80 G90 (BASLANGIC)
T1 M6 (FREZE D=6.0MM)
G43 H1 (TAKIM BOYU KOMPANZASYONU)
S18000 M3 (DEVIR VE YONDE)
G0 Z50.0 (GUVENLI YUK)

(1. PARCA: KAPAK-A 596.0x716.0mm)
G0 X23.000 Y17.000
G0 Z2.0
G1 Z-18.0 F2000 (DALMA)
G41 D1 (SOL TAKIM KOMPANZASYONU)
G1 Y20.000 F6000 (GIRIS)
G1 X20.000
G1 Y736.000
G1 X616.000
G1 Y20.000
G1 X23.000
G40 (KOMPANZASYON IPTAL)
G0 Z50.0
...
G0 X0 Y0 (PARK)
M5 (DEVIR DUR)
M30 (PROGRAM SONU)
%</code></pre>
  </div>
</section>
