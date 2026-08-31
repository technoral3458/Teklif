package com.technoral.petkit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.technoral.petkit.data.BesleyiciDurum
import com.technoral.petkit.data.Cihaz
import com.technoral.petkit.data.KayitSatiri
import com.technoral.petkit.data.PlanOgun
import com.technoral.petkit.data.mantik
import com.technoral.petkit.data.tamsayi
import com.technoral.petkit.ui.AyarAnahtari
import com.technoral.petkit.ui.BilgiSatiri
import com.technoral.petkit.ui.BolumKarti
import com.technoral.petkit.ui.BosDurum
import com.technoral.petkit.ui.HamJsonKarti
import com.technoral.petkit.ui.NotSeridi
import com.technoral.petkit.ui.SayiSecici
import com.technoral.petkit.ui.UiDurum
import kotlinx.serialization.json.JsonObject

enum class CihazSekmesi(val etiket: String) {
    DURUM("Durum"), BESLEME("Besleme"), PLAN("Plan"), KAYITLAR("Kayıtlar"), AYARLAR("Ayarlar")
}

// ------------------------------------------------------------------ DURUM

@Composable
fun DurumSekmesi(cihaz: Cihaz, ui: UiDurum, hamJson: () -> String, nemAliciSifirla: () -> Unit) {
    val d = ui.durum ?: BesleyiciDurum.bos(cihaz.ad)
    val g = ui.porsiyonGram

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BolumKarti("Cihaz durumu", Icons.Filled.Info) {
                BilgiSatiri(
                    "Bağlantı",
                    if (d.cevrimici) "Çevrimiçi" else "Çevrimdışı görünüyor",
                    if (d.cevrimici) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    vurgu = true
                )
                BilgiSatiri(
                    "Güç kaynağı",
                    when (d.prizeTakili) {
                        true -> "Prize takılı"
                        false -> "Pille çalışıyor"
                        null -> null
                    },
                    Icons.Filled.Power
                )
                BilgiSatiri(
                    "Pil",
                    d.pilYuzde?.let { "%$it" } ?: pilDurumMetni(d.pilDurumu),
                    Icons.Filled.Battery4Bar
                )
                BilgiSatiri(
                    "Wi-Fi sinyali",
                    d.wifiGuc?.let { "$it dBm (${wifiKalite(it)})" },
                    Icons.Filled.Wifi
                )
                if ((d.hataKodu ?: 0) != 0) {
                    BilgiSatiri("Hata kodu", d.hataKodu.toString(), Icons.Filled.Warning)
                }
            }
        }
        item {
            BolumKarti(
                if (cihaz.ciftHazne) "Hazneler" else "Mama haznesi",
                Icons.Filled.Restaurant
            ) {
                BilgiSatiri(
                    if (cihaz.ciftHazne) "Hazne 1 (sol)" else "Hazne",
                    mamaDurumMetni(d.hazne1Dolu),
                    vurgu = d.hazne1Dolu == false
                )
                if (cihaz.ciftHazne) {
                    BilgiSatiri("Hazne 2 (sağ)", mamaDurumMetni(d.hazne2Dolu), vurgu = d.hazne2Dolu == false)
                }
            }
        }
        item {
            BolumKarti("Bugün", Icons.Filled.Restaurant, "1 porsiyon ≈ $g gram olarak hesaplandı") {
                BilgiSatiri(
                    if (cihaz.ciftHazne) "Hazne 1 - planlanan" else "Planlanan",
                    porsiyonMetni(d.bugunPlanlanan1, g)
                )
                BilgiSatiri(
                    if (cihaz.ciftHazne) "Hazne 1 - verilen" else "Verilen",
                    porsiyonMetni(d.bugunVerilen1, g),
                    vurgu = true
                )
                if (cihaz.ciftHazne) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    BilgiSatiri("Hazne 2 - planlanan", porsiyonMetni(d.bugunPlanlanan2, g))
                    BilgiSatiri("Hazne 2 - verilen", porsiyonMetni(d.bugunVerilen2, g), vurgu = true)
                }
                if (d.yemeSayisi != null) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    BilgiSatiri("Yeme sayısı", "${d.yemeSayisi} kez")
                }
            }
        }
        item {
            BolumKarti("Nem alıcı (kurutucu)", Icons.Filled.Air) {
                BilgiSatiri(
                    "Kalan ömür",
                    d.nemAliciGun?.let { "$it gün" },
                    vurgu = (d.nemAliciGun ?: 99) < 5
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = nemAliciSifirla) {
                    Text("Yeni nem alıcı taktım - sayacı sıfırla")
                }
            }
        }
        item {
            BolumKarti("Cihaz bilgileri", Icons.Filled.Memory) {
                BilgiSatiri("Model", cihaz.tipEtiketi)
                BilgiSatiri("Cihaz kimliği", cihaz.id)
                BilgiSatiri("Seri numarası", d.seriNo ?: cihaz.seriNo)
                BilgiSatiri("Yazılım sürümü", d.yazilim)
                BilgiSatiri("API yolu", "/${cihaz.tip}/...")
            }
        }
        item { HamJsonKarti("Cihazın gönderdiği tüm veriler (JSON)", hamJson()) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun pilDurumMetni(kod: Int?): String? = when (kod) {
    0 -> "Normal"
    1 -> "Şarj oluyor"
    2 -> "Şarj doldu"
    else -> null
}

private fun wifiKalite(dbm: Int): String = when {
    dbm > -55 -> "çok iyi"
    dbm > -67 -> "iyi"
    dbm > -78 -> "orta"
    else -> "zayıf"
}

private fun mamaDurumMetni(dolu: Boolean?): String? = when (dolu) {
    true -> "Mama var"
    false -> "MAMA BİTTİ"
    null -> null
}

private fun porsiyonMetni(porsiyon: Int?, gram: Int): String? =
    porsiyon?.let { "$it porsiyon (~${it * gram} g)" }

// ------------------------------------------------------------------ BESLEME

@Composable
fun BeslemeSekmesi(
    cihaz: Cihaz,
    ui: UiDurum,
    besle: (Int, Int) -> Unit,
    iptal: () -> Unit
) {
    var p1 by remember { mutableStateOf(1) }
    var p2 by remember { mutableStateOf(0) }
    val g = ui.porsiyonGram

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BolumKarti(
                "Şimdi mama ver",
                Icons.Filled.Restaurant,
                "1 porsiyon ≈ $g gram. Değeri Ayarlar sekmesinden değiştirebilirsiniz."
            ) {
                SayiSecici(
                    etiket = if (cihaz.ciftHazne) "Hazne 1 (sol)" else "Porsiyon",
                    deger = p1,
                    enAz = 0,
                    enCok = 20,
                    altYazi = "~${p1 * g} gram"
                ) { p1 = it }

                if (cihaz.ciftHazne) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SayiSecici(
                        etiket = "Hazne 2 (sağ)",
                        deger = p2,
                        enAz = 0,
                        enCok = 20,
                        altYazi = "~${p2 * g} gram"
                    ) { p2 = it }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { besle(p1, p2) },
                    enabled = !ui.yukleniyor && (p1 > 0 || p2 > 0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Filled.Restaurant, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Mama ver" + if (cihaz.ciftHazne) " ($p1 + $p2 porsiyon)" else " ($p1 porsiyon)"
                    )
                }
            }
        }
        item {
            BolumKarti("Hızlı besleme", Icons.Filled.Restaurant) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { miktar ->
                        OutlinedButton(
                            onClick = { besle(miktar, 0) },
                            enabled = !ui.yukleniyor,
                            modifier = Modifier.weight(1f)
                        ) { Text("$miktar") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (cihaz.ciftHazne) "Yukarıdaki düğmeler yalnızca Hazne 1'den verir."
                    else "Seçilen porsiyon kadar anında mama verir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (ui.sonBeslemeId != null) {
            item {
                BolumKarti("Son besleme", Icons.Filled.Cancel) {
                    BilgiSatiri("Besleme kimliği", ui.sonBeslemeId)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = iptal, enabled = !ui.yukleniyor) {
                        Icon(Icons.Filled.Cancel, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bu beslemeyi iptal et")
                    }
                    Text(
                        "İptal yalnızca mama henüz dökülmediyse işe yarar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            NotSeridi(
                "Mama verildikten sonra cihazdan onay gelmesi birkaç saniye sürebilir. " +
                        "Durum sekmesi kendiliğinden 30 saniyede bir yenilenir."
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ------------------------------------------------------------------ PLAN

@Composable
fun PlanSekmesi(
    cihaz: Cihaz,
    ui: UiDurum,
    planKaydet: (Int, Int, Int, Int, Set<Int>, String?) -> Unit,
    planSil: (PlanOgun) -> Unit
) {
    var saat by remember { mutableStateOf(8) }
    var dakika by remember { mutableStateOf(0) }
    var p1 by remember { mutableStateOf(1) }
    var p2 by remember { mutableStateOf(0) }
    var ad by remember { mutableStateOf("") }
    var gunler by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }
    var saatMenu by remember { mutableStateOf(false) }
    var dakikaMenu by remember { mutableStateOf(false) }
    val g = ui.porsiyonGram

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BolumKarti(
                "Beslenme planı",
                Icons.Filled.Schedule,
                "${ui.planlar.size} öğün tanımlı"
            ) {
                if (ui.planlar.isEmpty()) {
                    Text(
                        "Cihazda tanımlı öğün okunamadı. Aşağıdan yeni öğün ekleyebilirsiniz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(ui.planlar) { ogun ->
            PlanSatiri(ogun, cihaz.ciftHazne, g) { planSil(ogun) }
        }
        item {
            BolumKarti("Yeni öğün ekle", Icons.Filled.Schedule) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saat", modifier = Modifier.weight(1f))
                    Box2 {
                        OutlinedButton(onClick = { saatMenu = true }) {
                            Text(String.format("%02d", saat))
                        }
                        DropdownMenu(expanded = saatMenu, onDismissRequest = { saatMenu = false }) {
                            (0..23).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(String.format("%02d", s)) },
                                    onClick = { saat = s; saatMenu = false }
                                )
                            }
                        }
                    }
                    Text(" : ", style = MaterialTheme.typography.titleLarge)
                    Box2 {
                        OutlinedButton(onClick = { dakikaMenu = true }) {
                            Text(String.format("%02d", dakika))
                        }
                        DropdownMenu(expanded = dakikaMenu, onDismissRequest = { dakikaMenu = false }) {
                            (0..55 step 5).forEach { dk ->
                                DropdownMenuItem(
                                    text = { Text(String.format("%02d", dk)) },
                                    onClick = { dakika = dk; dakikaMenu = false }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SayiSecici(
                    if (cihaz.ciftHazne) "Hazne 1 porsiyon" else "Porsiyon",
                    p1, 0, 20, "~${p1 * g} gram"
                ) { p1 = it }
                if (cihaz.ciftHazne) {
                    SayiSecici("Hazne 2 porsiyon", p2, 0, 20, "~${p2 * g} gram") { p2 = it }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Tekrar günleri", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val adlar = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
                    adlar.forEachIndexed { i, isim ->
                        val gun = i + 1
                        FilterChip(
                            selected = gunler.contains(gun),
                            onClick = {
                                gunler = if (gunler.contains(gun)) gunler - gun else gunler + gun
                            },
                            label = { Text(isim, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    if (gunler.isEmpty()) "Hiç gün seçilmedi - öğün yalnızca bugün için eklenir."
                    else "Seçilen günlerde her hafta tekrarlanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ad,
                    onValueChange = { ad = it },
                    label = { Text("Öğün adı (isteğe bağlı)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { planKaydet(saat, dakika, p1, p2, gunler, ad.ifBlank { null }) },
                    enabled = !ui.yukleniyor && (p1 > 0 || p2 > 0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Öğünü kaydet (" + String.format("%02d:%02d", saat, dakika) + ")"
                    )
                }
            }
        }
        item {
            NotSeridi(
                "Plan kaydetme/silme uçları Petkit'in resmi olmayan API'sini kullanır. " +
                        "Bir işlem hata verirse API Konsolu ekranındaki günlükten hangi alanın " +
                        "reddedildiğini görebilirsiniz."
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** DropdownMenu'yü konumlandırmak için küçük sarmalayıcı. */
@Composable
private fun Box2(icerik: @Composable () -> Unit) {
    Column { icerik() }
}

@Composable
private fun PlanSatiri(ogun: PlanOgun, ciftHazne: Boolean, gram: Int, sil: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                ogun.saatMetni,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val m1 = ogun.miktar1 ?: 0
                val m2 = ogun.miktar2 ?: 0
                Text(
                    if (ciftHazne) "Hazne 1: $m1 · Hazne 2: $m2 porsiyon"
                    else "$m1 porsiyon",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "~${(m1 + m2) * gram} gram" + (ogun.ad?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ogun.gunKodu != null) {
                    Text(
                        "Gün/tekrar: ${ogun.gunKodu}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = sil) {
                Icon(Icons.Filled.Delete, "öğünü sil", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ------------------------------------------------------------------ KAYITLAR

@Composable
fun KayitlarSekmesi(
    ui: UiDurum,
    gunEtiketi: String,
    gunDegistir: (Int) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { gunDegistir(ui.kayitGunFarki - 1) }) { Text("< Önceki") }
                Text(
                    gunEtiketi,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = { gunDegistir(ui.kayitGunFarki + 1) },
                    enabled = ui.kayitGunFarki < 0
                ) { Text("Sonraki >") }
            }
            HorizontalDivider()
        }
        if (ui.kayitlar.isEmpty()) {
            item { BosDurum("Bu güne ait kayıt bulunamadı.", Icons.Filled.History) }
        } else {
            items(ui.kayitlar) { k -> KayitKarti(k) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun KayitKarti(k: KayitSatiri) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (k.tur) {
                    "Besleme" -> Icons.Filled.Restaurant
                    "Yeme" -> Icons.Filled.Restaurant
                    "Mama uyarısı" -> Icons.Filled.Warning
                    "Pil" -> Icons.Filled.Battery4Bar
                    else -> Icons.Filled.Info
                },
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(k.tur, style = MaterialTheme.typography.titleSmall)
                Text(
                    k.aciklama,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                k.zamanMetni.substringAfter(" ").ifBlank { k.zamanMetni },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ------------------------------------------------------------------ AYARLAR

@Composable
fun AyarlarSekmesi(
    cihaz: Cihaz,
    ui: UiDurum,
    ayarDegistir: (String, Any, String) -> Unit,
    hamAyarGonder: (String) -> Unit,
    porsiyonGramAyarla: (Int) -> Unit,
    adDegistir: (String) -> Unit,
    komutGonder: (String, String, String) -> Unit,
    cikisYap: () -> Unit
) {
    val a: JsonObject? = ui.ayarlar
    var hamKv by remember { mutableStateOf("{\"lightMode\":1}") }
    var yeniAd by remember { mutableStateOf(cihaz.ad) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BolumKarti("Cihaz ayarları", Icons.Filled.Settings, "Değişiklikler anında cihaza gönderilir") {
                AyarAnahtari(
                    "Gösterge ışığı",
                    "Cihazın üzerindeki ışık",
                    a.mantik("lightMode")
                ) { ayarDegistir("lightMode", if (it) 1 else 0, "Gösterge ışığı") }

                AyarAnahtari(
                    "Çocuk kilidi / tuş kilidi",
                    "Cihaz üzerindeki düğmeleri kilitler",
                    a.mantik("manualLock")
                ) { ayarDegistir("manualLock", if (it) 1 else 0, "Çocuk kilidi") }

                AyarAnahtari(
                    "Besleme sesi (çağrı sesi)",
                    "Mama verilirken kedinizi çağıran ses",
                    a.mantik("feedSound")
                ) { ayarDegistir("feedSound", if (it) 1 else 0, "Besleme sesi") }

                AyarAnahtari(
                    "Sistem sesleri",
                    "Uyarı ve bildirim sesleri",
                    a.mantik("systemSoundEnable")
                ) { ayarDegistir("systemSoundEnable", if (it) 1 else 0, "Sistem sesleri") }

                AyarAnahtari(
                    "Mama azaldı uyarısı",
                    "Hazne boşalınca bildirim gönderir",
                    a.mantik("foodWarn")
                ) { ayarDegistir("foodWarn", if (it) 1 else 0, "Mama uyarısı") }

                AyarAnahtari(
                    "Nem alıcı uyarısı",
                    "Kurutucunun ömrü dolunca uyarır",
                    a.mantik("desiccantNotify")
                ) { ayarDegistir("desiccantNotify", if (it) 1 else 0, "Nem alıcı uyarısı") }

                AyarAnahtari(
                    "Düşük pil uyarısı",
                    null,
                    a.mantik("lowBatteryNotify")
                ) { ayarDegistir("lowBatteryNotify", if (it) 1 else 0, "Düşük pil uyarısı") }

                AyarAnahtari(
                    "Artan mama kontrolü",
                    "Kapta mama kaldıysa yeni öğünü azaltır",
                    a.mantik("surplusControl")
                ) { ayarDegistir("surplusControl", if (it) 1 else 0, "Artan mama kontrolü") }

                AyarAnahtari(
                    "Yeme algılama",
                    "Kedinizin yediğini algılar ve kaydeder",
                    a.mantik("eatDetection")
                ) { ayarDegistir("eatDetection", if (it) 1 else 0, "Yeme algılama") }
            }
        }

        if (cihaz.kameraVar) {
            item {
                BolumKarti("Kamera", Icons.Filled.Tune, "YumShare modellerindeki kamera ayarları") {
                    AyarAnahtari("Kamera açık", null, a.mantik("camera")) {
                        ayarDegistir("camera", if (it) 1 else 0, "Kamera")
                    }
                    AyarAnahtari("Gece görüşü", null, a.mantik("night")) {
                        ayarDegistir("night", if (it) 1 else 0, "Gece görüşü")
                    }
                    AyarAnahtari("Mikrofon", null, a.mantik("microphone")) {
                        ayarDegistir("microphone", if (it) 1 else 0, "Mikrofon")
                    }
                    AyarAnahtari("Video buluta yüklensin", null, a.mantik("upload")) {
                        ayarDegistir("upload", if (it) 1 else 0, "Video yükleme")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Canlı görüntü izleme bu uygulamada yoktur: Petkit canlı yayını " +
                                "kapalı (şifreli) bir video protokolüyle taşır. Video kayıtları " +
                                "resmi Petkit uygulamasından izlenebilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            BolumKarti("Ses düzeyi ve duyarlılık", Icons.Filled.Tune) {
                val ses = a.tamsayi("volume") ?: 5
                SayiSecici("Ses düzeyi", ses, 1, 9, "Cihazın hoparlör düzeyi") {
                    ayarDegistir("volume", it, "Ses düzeyi")
                }
                val duyarlilik = a.tamsayi("petSensitivity") ?: 2
                SayiSecici("Hareket duyarlılığı", duyarlilik, 1, 3, "1 düşük · 3 yüksek") {
                    ayarDegistir("petSensitivity", it, "Duyarlılık")
                }
            }
        }

        item {
            BolumKarti("Uygulama ayarı", Icons.Filled.Tune) {
                SayiSecici(
                    "1 porsiyon kaç gram?",
                    ui.porsiyonGram, 1, 50,
                    "Yalnızca uygulamadaki gram hesabını etkiler. YumShare için varsayılan 10 g."
                ) { porsiyonGramAyarla(it) }
            }
        }

        item {
            BolumKarti("Cihaz adı", Icons.Filled.Settings) {
                OutlinedTextField(
                    value = yeniAd,
                    onValueChange = { yeniAd = it },
                    label = { Text("Cihaz adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { adDegistir(yeniAd) },
                    enabled = yeniAd.isNotBlank() && yeniAd != cihaz.ad
                ) { Text("Adı kaydet") }
            }
        }

        item {
            BolumKarti(
                "İleri düzey ayar gönder",
                Icons.Filled.Tune,
                "Petkit'in beklediği JSON biçiminde doğrudan ayar gönderir"
            ) {
                OutlinedTextField(
                    value = hamKv,
                    onValueChange = { hamKv = it },
                    label = { Text("kv (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { hamAyarGonder(hamKv) }) { Text("Ayarı gönder") }
                    OutlinedButton(
                        onClick = { komutGonder("9", "{\"food\":0}", "Mama sayacı sıfırlama") }
                    ) { Text("Mama sayacını sıfırla") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Örnekler: {\"lightMode\":0} · {\"volume\":3} · {\"surplus\":30} · " +
                            "{\"lightRange\":[[0,480],[1200,1440]]}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            HamJsonKarti(
                "Cihazın mevcut ayarları (JSON)",
                ui.ayarlar?.toString() ?: "Ayarlar okunamadı"
            )
        }

        item {
            BolumKarti("Hesap", Icons.Filled.Settings) {
                BilgiSatiri("Giriş yapan", ui.kullaniciAdi)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = cikisYap) { Text("Çıkış yap") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
