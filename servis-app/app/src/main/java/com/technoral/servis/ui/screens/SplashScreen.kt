package com.technoral.servis.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoral.servis.R
import kotlinx.coroutines.delay

private val Ink = Color(0xFF0A0A12)
private val Purple = Color(0xFF1E1233)
private val Ember = Color(0xFFE23B4E)
private val Gold = Color(0xFFFFC24B)

/** Yazı animasyonunun hızları (ms). */
private const val ICON_DELAY = 420L
private const val CHAR_DELAY_TITLE = 48L
private const val CHAR_DELAY_SUBTITLE = 30L
private const val LINE_GAP = 220L
private const val HOLD_AFTER = 800L

/**
 * Açılış ekranı: ikon yaylanarak gelir, ardından yazı daktilo efektiyle
 * harf harf yazılır. Bitince uygulama açılır; ekrana dokunarak geçilebilir.
 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val title = stringResource(R.string.splash_line_one)
    val subtitle = stringResource(R.string.splash_line_two)

    var typedTitle by remember { mutableIntStateOf(0) }
    var typedSubtitle by remember { mutableIntStateOf(0) }
    val finish by rememberUpdatedState(onFinish)

    val iconScale = remember { Animatable(0.55f) }
    val iconAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        iconAlpha.animateTo(1f, tween(320))
    }
    LaunchedEffect(Unit) {
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    LaunchedEffect(Unit) {
        delay(ICON_DELAY)
        while (typedTitle < title.length) {
            delay(CHAR_DELAY_TITLE)
            typedTitle++
        }
        delay(LINE_GAP)
        while (typedSubtitle < subtitle.length) {
            delay(CHAR_DELAY_SUBTITLE)
            typedSubtitle++
        }
        delay(HOLD_AFTER)
        finish()
    }

    // Yanıp sönen imleç ve ikon halesinin nabzı
    val transition = rememberInfiniteTransition(label = "splash")
    val caret by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "caret",
    )
    val halo by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo",
    )

    val done = typedSubtitle >= subtitle.length

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Purple, Ink)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { finish() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Kızıl hale
                Box(
                    Modifier
                        .size(260.dp)
                        .scale(halo)
                        .background(
                            Brush.radialGradient(
                                listOf(Ember.copy(alpha = 0.45f), Color.Transparent),
                            ),
                            CircleShape,
                        ),
                )
                Image(
                    painter = painterResource(R.drawable.deli_kadir),
                    contentDescription = "Deli Kadir",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(180.dp)
                        .scale(iconScale.value)
                        .alpha(iconAlpha.value)
                        .clip(CircleShape),
                )
            }

            Spacer(Modifier.height(34.dp))

            Text(
                text = title.take(typedTitle) + caretFor(typedTitle < title.length, caret),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = subtitle.take(typedSubtitle) +
                    caretFor(typedTitle >= title.length && typedSubtitle < subtitle.length, caret),
                color = Gold,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(40.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    val dot by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(520, delayMillis = index * 170),
                            RepeatMode.Reverse,
                        ),
                        label = "dot$index",
                    )
                    Box(
                        Modifier
                            .size(7.dp)
                            .alpha(if (done) 1f else dot)
                            .background(Ember, CircleShape),
                    )
                }
            }
        }

        Text(
            text = "geçmek için dokun",
            color = Color.White.copy(alpha = 0.32f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        )
    }
}

private fun caretFor(active: Boolean, blink: Float): String =
    if (active && blink > 0.5f) "▌" else ""
