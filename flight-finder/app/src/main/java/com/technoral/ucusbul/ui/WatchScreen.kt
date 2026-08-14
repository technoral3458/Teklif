package com.technoral.ucusbul.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.technoral.ucusbul.data.formatMinutes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchScreen(vm: MainViewModel, state: UiState, modifier: Modifier = Modifier) {
    val stamp = SimpleDateFormat("d MMM HH:mm", Locale("tr", "TR"))

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nasıl çalışır", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Takibe aldığınız aramalar yaklaşık 6 saatte bir arka planda yeniden " +
                            "çalıştırılır. Hedef fiyatın altına inildiğinde veya fiyat %7'den fazla " +
                            "düştüğünde bildirim gelir; bildirimdeki düğme sizi doğrudan bilet " +
                            "sayfasına götürür.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { vm.checkWatchesNow() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Şimdi kontrol et")
                    }
                }
            }
        }

        if (state.watches.isEmpty()) {
            item {
                Text(
                    "Henüz takip edilen arama yok. Arama sonuçlarının üstündeki " +
                        "\"Bu aramayı takibe al\" düğmesini kullanın.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(state.watches) { watch ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                watch.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${watch.query.originIata} → ${watch.query.destinationCity} " +
                                    "(+${formatMinutes(watch.query.maxGroundMinutes)} çevre)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            watch.targetPrice?.let {
                                Text(
                                    "Hedef: ${formatPrice(it, "TRY")}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (watch.lastNote.isNotBlank()) {
                                Text(watch.lastNote, style = MaterialTheme.typography.bodySmall)
                            }
                            if (watch.lastCheckedAt > 0) {
                                Text(
                                    "Son kontrol: ${stamp.format(Date(watch.lastCheckedAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { vm.removeWatch(watch.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Sil")
                        }
                    }
                }
            }
        }
    }
}
