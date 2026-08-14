package com.technoral.ucusbul.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.technoral.ucusbul.core.Prefs

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var amadeusKey by remember { mutableStateOf(prefs.amadeusKey) }
    var amadeusSecret by remember { mutableStateOf(prefs.amadeusSecret) }
    var amadeusProd by remember { mutableStateOf(prefs.amadeusProduction) }
    var tpToken by remember { mutableStateOf(prefs.travelpayoutsToken) }
    var tpMarker by remember { mutableStateOf(prefs.travelpayoutsMarker) }
    var currency by remember { mutableStateOf(prefs.currency) }
    var demoFallback by remember { mutableStateOf(prefs.demoFallback) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Uçuş kaynakları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Uygulama tek bir havayoluna veya acenteye bağlı değildir. Aşağıdaki " +
                        "kaynaklardan hangisinin anahtarını girerseniz o taranır; ikisi de " +
                        "girilirse sonuçlar birleştirilir. Anahtarlar yalnızca bu telefonda saklanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Amadeus", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "GDS kaynaklı gerçek uçuş fiyatları (yüzlerce havayolu). " +
                        "developers.amadeus.com adresinden ücretsiz anahtar alınır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amadeusKey,
                    onValueChange = { amadeusKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amadeusSecret,
                    onValueChange = { amadeusSecret = it },
                    label = { Text("API Secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SettingSwitch(
                    "Canlı ortam (production)",
                    "Kapalıysa test ortamı kullanılır; test ortamında sonuç sayısı sınırlıdır.",
                    amadeusProd
                ) { amadeusProd = it }
                OutlinedButton(onClick = {
                    openUrl(context, "https://developers.amadeus.com/register")
                }) { Text("Ücretsiz anahtar al") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Travelpayouts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Aviasales altyapısı: yüzlerce acente ve havayolunun fiyatını tarar, " +
                        "doğrudan satın alma linki verir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tpToken,
                    onValueChange = { tpToken = it },
                    label = { Text("API Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tpMarker,
                    onValueChange = { tpMarker = it },
                    label = { Text("Marker (opsiyonel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = {
                    openUrl(context, "https://www.travelpayouts.com/")
                }) { Text("Token al") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Genel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase().take(3) },
                    label = { Text("Para birimi (TRY, USD, EUR…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SettingSwitch(
                    "Anahtar yokken demo verisi göster",
                    "Kapatırsanız anahtar girilmeden sonuç gösterilmez.",
                    demoFallback
                ) { demoFallback = it }
            }
        }

        OutlinedButton(
            onClick = {
                prefs.amadeusKey = amadeusKey
                prefs.amadeusSecret = amadeusSecret
                prefs.amadeusProduction = amadeusProd
                prefs.travelpayoutsToken = tpToken
                prefs.travelpayoutsMarker = tpMarker
                prefs.currency = currency
                prefs.demoFallback = demoFallback
                vm.onSettingsChanged()
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Kaydet") }

        if (saved) {
            Text("Ayarlar kaydedildi.", style = MaterialTheme.typography.bodySmall)
        }

        Divider()
        Text(
            "Havalimanı verisi uygulamaya gömülüdür (3.877 havalimanı). Yakınlık hesabı " +
                "kuş uçuşu mesafenin 1,25 katı yol + seçtiğiniz ulaşım hızına göre yapılır; " +
                "gerçek yolculuk süresi trafik ve sefer saatlerine göre değişebilir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
