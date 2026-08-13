package com.technoral.tvkumanda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
    )
}

/**
 * Basildiginda tek sefer tetiklenen, basili tutuldugunda tekrarlayan tus.
 * Ses ve yon tuslarinda kumandaya benzer davranis icin gerekli.
 */
@Composable
private fun Modifier.pressable(
    enabled: Boolean,
    repeatable: Boolean,
    onPress: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    if (repeatable) {
        LaunchedEffect(pressed) {
            if (!pressed) return@LaunchedEffect
            delay(450)
            while (true) {
                onPress()
                delay(120)
            }
        }
    }

    return this.pointerInput(enabled, repeatable) {
        if (!enabled) return@pointerInput
        detectTapGestures(
            onPress = {
                pressed = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onPress()
                tryAwaitRelease()
                pressed = false
            },
        )
    }
}

/** Yuvarlak ikon tusu. */
@Composable
fun RoundKey(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    enabled: Boolean = true,
    repeatable: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .pressable(enabled, repeatable, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

/** Yazili tus - HDMI, rakamlar, uygulamalar. */
@Composable
fun LabelKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    repeatable: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .pressable(enabled, repeatable, onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Dikey ikili tus grubu (ses +/-, kanal +/-). */
@Composable
fun RockerKey(
    topIcon: ImageVector,
    bottomIcon: ImageVector,
    label: String,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RoundKey(
            icon = topIcon,
            contentDescription = "$label artir",
            onClick = onTop,
            enabled = enabled,
            repeatable = true,
            container = Color.Transparent,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RoundKey(
            icon = bottomIcon,
            contentDescription = "$label azalt",
            onClick = onBottom,
            enabled = enabled,
            repeatable = true,
            container = Color.Transparent,
        )
    }
}

/**
 * Kaydirmali gezinme alani. YouTube gibi uygulamalarda uzun listelerde
 * gezinirken yon tuslarina tek tek basmaktan cok daha hizli.
 *
 * Parmak [stepPx] kadar hareket ettikce ilgili yon tusu gonderilir;
 * tek dokunus OK, cift parmak/uzun basma geri anlamina gelir.
 */
@Composable
fun TouchPad(
    enabled: Boolean,
    onDirection: (dx: Int, dy: Int) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    stepPx: Float = 90f,
) {
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTap()
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                )
            }
            .pointerInput(enabled, stepPx) {
                if (!enabled) return@pointerInput
                var accumulated = Offset.Zero
                detectDragGestures(
                    onDragStart = { accumulated = Offset.Zero },
                    onDragEnd = { accumulated = Offset.Zero },
                    onDragCancel = { accumulated = Offset.Zero },
                ) { change, dragAmount ->
                    change.consume()
                    accumulated += dragAmount

                    // Tek eksende hareket: capraz kaymalarda yanlis yon uretmemek
                    // icin baskin eksen secilir.
                    if (abs(accumulated.x) >= stepPx && abs(accumulated.x) >= abs(accumulated.y)) {
                        val steps = (accumulated.x / stepPx).toInt()
                        accumulated = Offset(accumulated.x - steps * stepPx, 0f)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        repeat(abs(steps)) { onDirection(if (steps > 0) 1 else -1, 0) }
                    } else if (abs(accumulated.y) >= stepPx) {
                        val steps = (accumulated.y / stepPx).toInt()
                        accumulated = Offset(0f, accumulated.y - steps * stepPx)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        repeat(abs(steps)) { onDirection(0, if (steps > 0) 1 else -1) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Kaydır",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "dokun = seç · basılı tut = geri",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}

/** Klasik yon tuslari + ortada OK. */
@Composable
fun DPad(
    enabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        DirectionZone(Alignment.TopCenter, "Yukarı", enabled, onUp, Modifier.align(Alignment.TopCenter))
        DirectionZone(Alignment.BottomCenter, "Aşağı", enabled, onDown, Modifier.align(Alignment.BottomCenter))
        DirectionZone(Alignment.CenterStart, "Sol", enabled, onLeft, Modifier.align(Alignment.CenterStart))
        DirectionZone(Alignment.CenterEnd, "Sağ", enabled, onRight, Modifier.align(Alignment.CenterEnd))

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCenter()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "OK",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun DirectionZone(
    alignment: Alignment,
    label: String,
    enabled: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Yon tuslarinda otomatik aynalama istemiyoruz: "sol" fiziksel olarak sol.
    val icon = when (alignment) {
        Alignment.TopCenter -> Icons.Filled.KeyboardArrowUp
        Alignment.BottomCenter -> Icons.Filled.KeyboardArrowDown
        Alignment.CenterStart -> Icons.Filled.KeyboardArrowLeft
        else -> Icons.Filled.KeyboardArrowRight
    }
    Box(
        modifier = modifier
            .size(88.dp)
            .pressable(enabled, repeatable = true, onPress = onPress),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            },
            modifier = Modifier.size(34.dp),
        )
    }
}

/** Ekranin tamamini kaplayan basit durum bilgisi. */
@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
