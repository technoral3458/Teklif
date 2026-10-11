package com.teklif.tercuman.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teklif.tercuman.R
import com.teklif.tercuman.translate.Lang
import com.teklif.tercuman.translate.LangPair

/** Her dilin tuş/balon rengi: Türkçe mavi, Çince sarı, İngilizce yeşil, Rusça kırmızı. */
fun Lang.color(): Color = when (this) {
    Lang.TR -> Color(0xFF1565C0)
    Lang.ZH -> Color(0xFFF9A825)
    Lang.EN -> Color(0xFF2E7D32)
    Lang.RU -> Color(0xFFC62828)
}

/** Sarı zemin üzerinde beyaz okunmaz; Çince tuşunda koyu simge/yazı kullanılır. */
fun Lang.onColor(): Color = if (this == Lang.ZH) Color(0xFF212121) else Color.White

/** Açık zemin üzerinde okunur dil rengi (balon başlığı için). */
fun Lang.textColor(): Color = if (this == Lang.ZH) Color(0xFFB26A00) else color()

/**
 * Ses seviyesine göre büyüyüp küçülen yuvarlak mikrofon tuşu.
 *
 * Basılı tutulursa bırakılınca konuşma biter (bas-konuş). Kısa dokunuşta mikrofon açık kalır,
 * ikinci dokunuş bitirir. Dinlerken kare "bitir" simgesi görünür.
 */
@Composable
fun MicButton(
    listening: Boolean,
    level: Float,
    size: Dp,
    color: Color,
    onPress: () -> Unit,
    onRelease: (held: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    label: String? = null,
) {
    val pulse by animateFloatAsState(if (listening) 1f + level * 0.35f else 1f, label = "pulse")
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    Box(modifier = modifier.size(size * 1.4f), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(size)
                    .scale(pulse * 1.25f)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.25f))
            )
        }
        Box(
            Modifier
                .size(size)
                .scale(pressScale)
                .clip(CircleShape)
                .background(if (listening) color else color.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            val downAt = System.currentTimeMillis()
                            onPress()
                            tryAwaitRelease()
                            pressed = false
                            onRelease(System.currentTimeMillis() - downAt >= HOLD_MS)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(if (listening) R.drawable.ic_stop else R.drawable.ic_mic),
                    contentDescription = if (listening) "Bitir" else "Mikrofon",
                    tint = contentColor,
                    modifier = Modifier.size(if (label == null) size * 0.45f else size * 0.36f),
                )
                if (label != null) {
                    Text(
                        text = label,
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.16f).sp,
                        lineHeight = (size.value * 0.18f).sp,
                    )
                }
            }
        }
    }
}

/** Bu süreden uzun basış "bas-konuş" sayılır: bırakınca konuşma biter. */
private const val HOLD_MS = 350L

/** Sohbet listesindeki tek bir çeviri kartı. */
@Composable
fun UtteranceCard(
    utterance: Utterance,
    pair: LangPair,
    onSpeak: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val source = utterance.source
    val result = utterance.result
    // WhatsApp düzeni: soldaki dilin (a) konuşanı solda, sağdaki dilin (b) konuşanı sağda.
    val alignEnd = source == pair.b
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        EditDialog(
            initial = result?.cleanedSource ?: utterance.rawText,
            lang = source,
            onDismiss = { editing = false },
            onConfirm = { text ->
                editing = false
                onEdit(text)
            },
        )
    }
    val accent = source?.color() ?: MaterialTheme.colorScheme.outline
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (alignEnd) 18.dp else 4.dp,
        bottomEnd = if (alignEnd) 4.dp else 18.dp,
    )
    Row(
        Modifier.fillMaxWidth().padding(start = if (alignEnd) 36.dp else 0.dp, end = if (alignEnd) 0.dp else 36.dp),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(
                containerColor = accent.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface),
            ),
            border = BorderStroke(1.5.dp, accent.copy(alpha = 0.6f)),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = source?.let { "${it.flag} ${it.displayName}" } ?: "…",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = source?.textColor() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                )
                // Tanınan metin: dokununca klavyeyle düzeltilip yeniden çevrilir.
                Row(
                    Modifier.fillMaxWidth().clickable { editing = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = result?.cleanedSource ?: utterance.rawText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Düzelt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).size(18.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                when {
                    result != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${result.target.flag} ${result.translation}",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 30.sp,
                                )
                                if (result.pinyin.isNotBlank()) {
                                    Text(
                                        text = result.pinyin,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            IconButton(onClick = onSpeak) {
                                Icon(painterResource(R.drawable.ic_volume), contentDescription = "Seslendir")
                            }
                        }
                        if (result.english.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "EN köprü: ${result.english}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.note.isNotBlank()) {
                            Text(
                                text = "Not: ${result.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    utterance.error != null -> {
                        Text(utterance.error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Tekrar dene") }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        if (utterance.liveTranslation.isNotBlank()) {
                            Text(
                                text = utterance.liveTranslation,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 30.sp,
                            )
                        } else {
                            Text("Çevriliyor… / 翻译中…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Tanınan metni düzeltme penceresi. */
@Composable
private fun EditDialog(
    initial: String,
    lang: Lang?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lang?.let { "${it.flag} ${it.displayName}" } ?: "Düzelt") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Çevir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
