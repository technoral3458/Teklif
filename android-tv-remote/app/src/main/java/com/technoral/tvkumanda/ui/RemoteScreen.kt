package com.technoral.tvkumanda.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoral.tvkumanda.VolumeState
import com.technoral.tvkumanda.protocol.TvApps
import com.technoral.tvkumanda.protocol.TvKeys

@Composable
fun RemoteScreen(
    deviceName: String,
    connected: Boolean,
    volume: VolumeState?,
    currentApp: String?,
    onKey: (Int) -> Unit,
    onApp: (String) -> Unit,
    onText: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var touchMode by remember { mutableStateOf(true) }
    var showNumbers by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Header(deviceName, connected, volume, currentApp, onDisconnect)

        // --- Ust sira: guc, sessiz, kaynak, ayarlar ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RoundKey(
                icon = Icons.Filled.PowerSettingsNew,
                contentDescription = "Aç / kapat",
                onClick = { onKey(TvKeys.POWER) },
                enabled = connected,
                container = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                tint = MaterialTheme.colorScheme.error,
            )
            RoundKey(
                icon = if (volume?.muted == true) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = "Sessiz",
                onClick = { onKey(TvKeys.VOLUME_MUTE) },
                enabled = connected,
            )
            RoundKey(
                icon = Icons.Filled.Search,
                contentDescription = "Ara",
                onClick = { onKey(TvKeys.SEARCH) },
                enabled = connected,
            )
            RoundKey(
                icon = Icons.Filled.Mic,
                contentDescription = "Sesli asistan",
                onClick = { onKey(TvKeys.ASSIST) },
                enabled = connected,
            )
            RoundKey(
                icon = Icons.Filled.Settings,
                contentDescription = "Ayarlar",
                onClick = { onKey(TvKeys.SETTINGS) },
                enabled = connected,
            )
        }

        // --- Gezinme modu ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = touchMode,
                onClick = { touchMode = true },
                label = { Text("Dokunmatik") },
            )
            FilterChip(
                selected = !touchMode,
                onClick = { touchMode = false },
                label = { Text("Yön tuşları") },
            )
        }

        // --- Yon alani + ses/kanal ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RockerKey(
                topIcon = Icons.Filled.Add,
                bottomIcon = Icons.Filled.Remove,
                label = "Ses",
                onTop = { onKey(TvKeys.VOLUME_UP) },
                onBottom = { onKey(TvKeys.VOLUME_DOWN) },
                enabled = connected,
            )

            Box(modifier = Modifier.weight(1f)) {
                if (touchMode) {
                    TouchPad(
                        enabled = connected,
                        onDirection = { dx, dy ->
                            when {
                                dx > 0 -> onKey(TvKeys.DPAD_RIGHT)
                                dx < 0 -> onKey(TvKeys.DPAD_LEFT)
                                dy > 0 -> onKey(TvKeys.DPAD_DOWN)
                                dy < 0 -> onKey(TvKeys.DPAD_UP)
                            }
                        },
                        onTap = { onKey(TvKeys.DPAD_CENTER) },
                        onLongPress = { onKey(TvKeys.BACK) },
                    )
                } else {
                    DPad(
                        enabled = connected,
                        onUp = { onKey(TvKeys.DPAD_UP) },
                        onDown = { onKey(TvKeys.DPAD_DOWN) },
                        onLeft = { onKey(TvKeys.DPAD_LEFT) },
                        onRight = { onKey(TvKeys.DPAD_RIGHT) },
                        onCenter = { onKey(TvKeys.DPAD_CENTER) },
                    )
                }
            }

            RockerKey(
                topIcon = Icons.Filled.Add,
                bottomIcon = Icons.Filled.Remove,
                label = "Kanal",
                onTop = { onKey(TvKeys.CHANNEL_UP) },
                onBottom = { onKey(TvKeys.CHANNEL_DOWN) },
                enabled = connected,
            )
        }

        // --- Geri / ana ekran / menu ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Alti tus var; dar ekranlarda tasmamasi icin biraz kucuk.
            val navSize = 48.dp
            RoundKey(Icons.AutoMirrored.Filled.ArrowBack, "Geri", { onKey(TvKeys.BACK) }, size = navSize, enabled = connected)
            RoundKey(Icons.Filled.Home, "Ana ekran", { onKey(TvKeys.HOME) }, size = navSize, enabled = connected)
            RoundKey(Icons.Filled.Menu, "Menü", { onKey(TvKeys.MENU) }, size = navSize, enabled = connected)
            RoundKey(Icons.AutoMirrored.Filled.List, "Rehber", { onKey(TvKeys.GUIDE) }, size = navSize, enabled = connected)
            RoundKey(Icons.Filled.Info, "Bilgi", { onKey(TvKeys.INFO) }, size = navSize, enabled = connected)
            RoundKey(Icons.Filled.Subtitles, "Altyazı", { onKey(TvKeys.CAPTIONS) }, size = navSize, enabled = connected)
        }

        // --- Oynatma ---
        SectionTitle("Oynatma")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RoundKey(Icons.Filled.SkipPrevious, "Önceki", { onKey(TvKeys.MEDIA_PREVIOUS) }, enabled = connected)
            RoundKey(Icons.Filled.FastRewind, "Geri sar", { onKey(TvKeys.MEDIA_REWIND) }, enabled = connected, repeatable = true)
            RoundKey(
                icon = Icons.Filled.PlayArrow,
                contentDescription = "Oynat / duraklat",
                onClick = { onKey(TvKeys.MEDIA_PLAY_PAUSE) },
                enabled = connected,
                size = 64.dp,
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            RoundKey(Icons.Filled.FastForward, "İleri sar", { onKey(TvKeys.MEDIA_FAST_FORWARD) }, enabled = connected, repeatable = true)
            RoundKey(Icons.Filled.SkipNext, "Sonraki", { onKey(TvKeys.MEDIA_NEXT) }, enabled = connected)
        }

        // --- Girisler ve uygulamalar (tek bolum) ---
        SectionTitle("Girişler ve uygulamalar")
        Text(
            text = "Kaydırırken yanlışlıkla açılmasın diye bu tuşlar 1 saniye " +
                "basılı tutunca çalışır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ShortcutGrid(
            shortcuts = rememberShortcuts(onKey = onKey, onApp = onApp),
            enabled = connected,
        )
        Text(
            text = "Bazı modellerde doğrudan HDMI tuşları çalışmaz; o durumda " +
                "\"Kaynak\" ile listeyi açıp yön tuşlarıyla seçin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        // --- Klavye ---
        SectionTitle("Yazı ve arama")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showKeyboard = !showKeyboard }, modifier = Modifier.weight(1f)) {
                Text(if (showKeyboard) "Gizle" else "Klavye")
            }
            OutlinedButton(onClick = { showNumbers = !showNumbers }, modifier = Modifier.weight(1f)) {
                Text(if (showNumbers) "Gizle" else "Rakamlar")
            }
        }

        AnimatedVisibility(visible = showKeyboard) {
            TextPanel(enabled = connected, onKey = onKey, onText = onText, onApp = onApp)
        }

        AnimatedVisibility(visible = showNumbers) {
            NumberPad(enabled = connected, onKey = onKey)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header(
    deviceName: String,
    connected: Boolean,
    volume: VolumeState?,
    currentApp: String?,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (connected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = deviceName, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                text = buildStatusLine(connected, volume, currentApp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDisconnect) { Text("Ayrıl") }
    }
}

/** Baslik altindaki tek satirlik durum metni. */
private fun buildStatusLine(
    connected: Boolean,
    volume: VolumeState?,
    currentApp: String?,
): String {
    if (!connected) return "Bağlantı bekleniyor…"

    val parts = mutableListOf<String>()
    if (volume != null && volume.max > 0) {
        parts += "Ses ${volume.level}/${volume.max}"
        if (volume.muted) parts += "sessiz"
    }
    // TV yalnizca paket adini bildiriyor; son parca okunabilir bir isim veriyor.
    currentApp?.substringAfterLast('.')?.takeIf { it.isNotBlank() }?.let { parts += it }

    return if (parts.isEmpty()) "Bağlı" else parts.joinToString(" · ")
}

/** Girisler ve uygulamalar ayni listede; [isInput] yalnizca rengi belirler. */
private data class Shortcut(
    val label: String,
    val isInput: Boolean,
    val onActivate: () -> Unit,
)

@Composable
private fun rememberShortcuts(
    onKey: (Int) -> Unit,
    onApp: (String) -> Unit,
): List<Shortcut> = remember(onKey, onApp) {
    buildList {
        add(Shortcut("Kaynak", true) { onKey(TvKeys.TV_INPUT) })
        add(Shortcut("TV", true) { onKey(TvKeys.TV) })
        add(Shortcut("HDMI 1", true) { onKey(TvKeys.TV_INPUT_HDMI_1) })
        add(Shortcut("HDMI 2", true) { onKey(TvKeys.TV_INPUT_HDMI_2) })
        add(Shortcut("HDMI 3", true) { onKey(TvKeys.TV_INPUT_HDMI_3) })
        add(Shortcut("HDMI 4", true) { onKey(TvKeys.TV_INPUT_HDMI_4) })
        add(Shortcut("AV", true) { onKey(TvKeys.TV_INPUT_COMPOSITE_1) })
        TvApps.defaults.forEach { app ->
            add(Shortcut(app.label, false) { onApp(app.link) })
        }
    }
}

@Composable
private fun ShortcutGrid(shortcuts: List<Shortcut>, enabled: Boolean) {
    // Sabit ve kisa bir liste oldugu icin LazyGrid yerine basit satirlar;
    // ic ice kaydirma sorunlarindan da kacinmis oluruz.
    shortcuts.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { shortcut ->
                HoldKey(
                    label = shortcut.label,
                    onActivate = shortcut.onActivate,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    container = if (shortcut.isInput) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (shortcut.isInput) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TextPanel(
    enabled: Boolean,
    onKey: (Int) -> Unit,
    onText: (String) -> Unit,
    onApp: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text("Metin") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "\"TV'ye yaz\" için TV'de bir yazı alanı açık olmalı. " +
                "YouTube araması ise doğrudan sonuç sayfasını açar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onText(text) },
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("TV'ye yaz")
            }
            Button(
                onClick = { onApp(TvApps.youtubeSearch(text)) },
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("YouTube'da ara")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelKey("Boşluk", { onKey(TvKeys.SPACE) }, Modifier.weight(1f), enabled)
            LabelKey("Sil", { onKey(TvKeys.DEL) }, Modifier.weight(1f), enabled, repeatable = true)
            LabelKey("Enter", { onKey(TvKeys.ENTER) }, Modifier.weight(1f), enabled)
        }
    }
}

@Composable
private fun NumberPad(enabled: Boolean, onKey: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(1..3, 4..6, 7..9).forEach { range ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                range.forEach { digit ->
                    LabelKey(
                        label = digit.toString(),
                        onClick = { onKey(TvKeys.digit(digit)) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelKey("Renk", { onKey(TvKeys.PROG_RED) }, Modifier.weight(1f), enabled,
                container = Color(0xFF7A2E2E))
            LabelKey("0", { onKey(TvKeys.digit(0)) }, Modifier.weight(1f), enabled)
            LabelKey("Sil", { onKey(TvKeys.DEL) }, Modifier.weight(1f), enabled)
        }
    }
}
