package com.teklif.tercuman.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teklif.tercuman.R
import com.teklif.tercuman.translate.Lang

/** Türkçe tuşu mavi, Çince tuşu sarı. */
val TurkeyBlue = Color(0xFF1565C0)
val ChinaGold = Color(0xFFF9A825)

fun Lang.color(): Color = if (this == Lang.TR) TurkeyBlue else ChinaGold

/** Sarı zemin üzerinde beyaz okunmaz; Çince tuşunda koyu simge/yazı kullanılır. */
fun Lang.onColor(): Color = if (this == Lang.TR) Color.White else Color(0xFF212121)

fun Lang.flag(): String = if (this == Lang.TR) "🇹🇷" else "🇨🇳"

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
    onSpeak: () -> Unit,
    onRetry: () -> Unit,
) {
    val source = utterance.source
    val result = utterance.result
    val alignEnd = source == Lang.ZH
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f),
            colors = CardDefaults.cardColors(
                containerColor = if (alignEnd) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = source?.let { "${it.flag()} ${it.displayName}" } ?: "…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = result?.cleanedSource ?: utterance.rawText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                when {
                    result != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${result.target.flag()} ${result.translation}",
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
