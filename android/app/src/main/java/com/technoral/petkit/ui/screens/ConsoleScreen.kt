package com.technoral.petkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoral.petkit.data.ApiLog
import com.technoral.petkit.ui.BolumKarti
import com.technoral.petkit.ui.NotSeridi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KonsolEkrani(
    cihazYolu: String,
    cihazId: String,
    bugun: String,
    sonuc: String,
    yukleniyor: Boolean,
    gonder: (String, String) -> Unit
) {
    var yol by remember { mutableStateOf("$cihazYolu/device_detail") }
    var parametreler by remember { mutableStateOf("id=$cihazId") }
    val kayitlar by ApiLog.kayitlar.collectAsState()
    val pano = LocalClipboardManager.current

    val sablonlar = listOf(
        "Cihaz ayrıntısı" to ("$cihazYolu/device_detail" to "id=$cihazId"),
        "Cihaz listesi" to ("discovery/device_roster" to "day=$bugun"),
        "Aile listesi" to ("group/family/list" to ""),
        "Bugünün kayıtları" to ("$cihazYolu/getDeviceRecord" to "deviceId=$cihazId\ndays=$bugun"),
        "1 porsiyon ver" to ("$cihazYolu/saveDailyFeed" to "deviceId=$cihazId\nday=$bugun\ntime=-1\namount1=1\namount2=0"),
        "Nem alıcı sıfırla" to ("$cihazYolu/desiccantReset" to "deviceId=$cihazId"),
        "Ayar gönder" to ("$cihazYolu/updateSettings" to "id=$cihazId\nkv={\"lightMode\":1}"),
        "Kullanıcı bilgisi" to ("user/details" to ""),
        "Bölge sunucuları" to ("v1/regionservers" to "")
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            NotSeridi(
                "Bu ekran ileri düzey kullanım içindir. Petkit API'si resmi olarak " +
                        "belgelenmediği için bir işlev çalışmazsa buradan hangi alanın " +
                        "reddedildiğini görebilir, doğru parametrelerle elle deneyebilirsiniz."
            )
        }
        item {
            BolumKarti("İstek gönder", Icons.Filled.Terminal, "POST · form-urlencoded") {
                OutlinedTextField(
                    value = yol,
                    onValueChange = { yol = it },
                    label = { Text("Uç nokta (sunucu adresinden sonrası)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = parametreler,
                    onValueChange = { parametreler = it },
                    label = { Text("Parametreler (her satıra anahtar=değer)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { gonder(yol, parametreler) },
                    enabled = !yukleniyor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Gönder")
                }
                Spacer(Modifier.height(10.dp))
                Text("Hazır şablonlar", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sablonlar.forEach { (etiket, cift) ->
                        AssistChip(
                            onClick = {
                                yol = cift.first
                                parametreler = cift.second
                            },
                            label = { Text(etiket, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
        item {
            BolumKarti("Yanıt", Icons.Filled.Terminal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pano.setText(AnnotatedString(sonuc)) }) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Yanıtı kopyala")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(
                        sonuc.ifBlank { "Henüz istek gönderilmedi." },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "API günlüğü (${kayitlar.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { pano.setText(AnnotatedString(ApiLog.metinOlarak())) }) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tümünü kopyala")
                }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { ApiLog.temizle() }) {
                    Icon(Icons.Filled.Delete, "temizle", modifier = Modifier.size(16.dp))
                }
            }
            HorizontalDivider()
        }
        items(kayitlar) { kayit ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row {
                        Text(
                            "${kayit.zaman}  ${kayit.yol}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (kayit.basarili) "${kayit.sureMs} ms" else "HATA",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (kayit.basarili) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "→ ${kayit.istek}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "← ${kayit.yanit.take(1500)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
