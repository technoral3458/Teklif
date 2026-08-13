package com.technoral.tvkumanda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoral.tvkumanda.ConnectionState
import com.technoral.tvkumanda.data.SavedTv
import com.technoral.tvkumanda.net.DiscoveredTv

@Composable
fun ConnectScreen(
    state: ConnectionState,
    discovered: List<DiscoveredTv>,
    saved: List<SavedTv>,
    onConnect: (name: String, host: String, port: Int) -> Unit,
    onForget: (host: String) -> Unit,
    onRefresh: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onCancelPairing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var manualIp by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TV Kumanda",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Telefon ve TV aynı Wi-Fi ağında olmalı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Yeniden ara")
            }
        }

        if (state is ConnectionState.Failed) {
            ErrorBanner(state.message)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (saved.isNotEmpty()) {
                item { SectionTitle("Eşleşmiş TV'ler") }
                items(saved, key = { "saved-${it.host}" }) { device ->
                    DeviceRow(
                        title = device.name,
                        subtitle = device.host,
                        paired = true,
                        onClick = { onConnect(device.name, device.host, device.port) },
                        onForget = { onForget(device.host) },
                    )
                }
            }

            item { SectionTitle("Ağda bulunanlar") }

            val savedHosts = saved.map { it.host }.toSet()
            val fresh = discovered.filterNot { it.host in savedHosts }
            if (fresh.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Aranıyor… TV açık olmalı.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(fresh, key = { "found-${it.host}" }) { device ->
                    DeviceRow(
                        title = device.name,
                        subtitle = device.host,
                        paired = false,
                        onClick = { onConnect(device.name, device.host, device.port) },
                        onForget = null,
                    )
                }
            }

            item { SectionTitle("IP ile bağlan") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TV bulunamıyorsa: TV'de Ayarlar › Ağ ve İnternet bölümünden " +
                            "IP adresini okuyup buraya yazın.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it.filter { char -> char.isDigit() || char == '.' } },
                            singleLine = true,
                            label = { Text("192.168.1.x") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { onConnect(manualIp, manualIp, 6466) },
                            enabled = manualIp.count { it == '.' } == 3,
                        ) {
                            Text("Bağlan")
                        }
                    }
                }
            }

            item { Box(modifier = Modifier.size(24.dp)) }
        }
    }

    when (state) {
        is ConnectionState.Connecting -> BusyDialog(state.deviceName)
        is ConnectionState.WaitingForCode -> PairingDialog(
            deviceName = state.deviceName,
            error = state.error,
            onSubmit = onSubmitCode,
            onDismiss = onCancelPairing,
        )
        else -> Unit
    }
}

@Composable
private fun DeviceRow(
    title: String,
    subtitle: String,
    paired: Boolean,
    onClick: () -> Unit,
    onForget: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (paired) "$subtitle · eşleşmiş" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onForget != null) {
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Kaydı sil",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
            .padding(12.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BusyDialog(deviceName: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Bağlanıyor") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(deviceName)
            }
        },
    )
}

@Composable
private fun PairingDialog(
    deviceName: String,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eşleştirme kodu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$deviceName ekranında 6 karakterlik bir kod göründü. " +
                        "Kodu buraya girin.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        code = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                    },
                    singleLine = true,
                    isError = error != null,
                    label = { Text("Örn. 4A7B2C") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = "Kod görünmüyorsa TV'de Ayarlar › Uzaktan Kumandalar ve Aksesuarlar " +
                        "bölümünü kontrol edin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(code) }, enabled = code.length == 6) {
                Text("Eşleştir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}
