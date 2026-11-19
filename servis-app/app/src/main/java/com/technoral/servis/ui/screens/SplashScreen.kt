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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.technoral.servis.R
import kotlinx.coroutines.delay

private val Ink = Color(0xFF0A0A12)
private val Purple = Color(0xFF1E1233)
private val Ember = Color(0xFFE23B4E)

/** Açılış ekranının toplam süresi (ms). */
private const val HOLD = 1900L

/**
 * Açılış ekranı: logo yaylanarak gelir, arkasındaki hale nabız atar ve
 * uygulama açılır. Ekrana dokunarak beklemeden geçilebilir.
 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val finish by rememberUpdatedState(onFinish)

    val iconScale = remember { Animatable(0.62f) }
    val iconAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        iconAlpha.animateTo(1f, tween(300))
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
        delay(HOLD)
        finish()
    }

    val transition = rememberInfiniteTransition(label = "splash")
    val halo by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo",
    )

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
                // Logonun arkasındaki kızıl hale
                Box(
                    Modifier
                        .size(260.dp)
                        .scale(halo)
                        .background(
                            Brush.radialGradient(
                                listOf(Ember.copy(alpha = 0.38f), Color.Transparent),
                            ),
                            CircleShape,
                        ),
                )
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(180.dp)
                        .scale(iconScale.value)
                        .alpha(iconAlpha.value)
                        .clip(CircleShape),
                )
            }

            Spacer(Modifier.height(44.dp))

            // Yükleniyor göstergesi
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
                            .alpha(dot)
                            .background(Ember, CircleShape),
                    )
                }
            }
        }
    }
}
