package com.teklif.tercuman.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teklif.tercuman.R
import com.teklif.tercuman.translate.Lang

val TurkishRed = Color(0xFFC62828)
val ChinaGold = Color(0xFFD18800)

fun Lang.color(): Color = if (this == Lang.TR) TurkishRed else ChinaGold

fun Lang.flag(): String = if (this == Lang.TR) "🇹🇷" else "🇨🇳"

/** Ses seviyesine göre büyüyüp küçülen yuvarlak mikrofon tuşu. */
@Composable
fun MicButton(
    listening: Boolean,
    level: Float,
    size: Dp,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse by animateFloatAsState(if (listening) 1f + level * 0.35f else 1f, label = "pulse")
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
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(size),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (listening) color else color.copy(alpha = 0.9f),
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Mikrofon",
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

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
