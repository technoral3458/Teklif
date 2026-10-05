package com.technoral.servis.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoral.servis.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Tahsilat kaydedildiğinde gösterilen kutlama bilgisi. */
data class Celebration(
    val amountText: String,
    val tryText: String? = null,
    val customer: String = "",
)

private const val DURATION = 4.2f          // saniye
private const val FADE_OUT = 0.55f         // son yarım saniyede söner

private val CONFETTI = listOf(
    Color(0xFFFFC24B), Color(0xFFE23B4E), Color(0xFF3282B8), Color(0xFF4ADE80),
    Color(0xFFFF7AC8), Color(0xFF7C5CFF), Color(0xFFFF8A3D), Color(0xFF2DD4BF),
    Color(0xFFFFFFFF),
)

private val CHEERS = listOf(
    "Kasaya girdi!",
    "Helal olsun, para yerinde!",
    "Kadir patron gülümsedi.",
    "Bu akşam çay senden!",
    "Alacak defteri bir nebze rahatladı.",
    "Emeğin karşılığı cepte.",
)

/**
 * Konfeti parçacığı. Konum/hız değerleri ekran yüksekliğinin oranı olarak
 * tutulur; böylece her ekran boyutunda aynı görünür.
 */
private data class Confetti(
    val t0: Float,
    val ox: Float, val oy: Float,
    val vx: Float, val vy: Float,
    val gravity: Float,
    val size: Float,
    val ratio: Float,
    val color: Color,
    val round: Boolean,
    val spin: Float,
    val rot0: Float,
    val swayAmp: Float,
    val swayHz: Float,
)

private fun buildConfetti(rnd: Random): List<Confetti> {
    val list = ArrayList<Confetti>(200)

    fun shoot(count: Int, t0: Float, ox: Float, oy: Float, aimDeg: Float, spreadDeg: Float,
              speedMin: Float, speedMax: Float) {
        repeat(count) {
            val ang = (aimDeg + (rnd.nextFloat() - 0.5f) * spreadDeg) * PI.toFloat() / 180f
            val sp = speedMin + rnd.nextFloat() * (speedMax - speedMin)
            list += Confetti(
                t0 = t0 + rnd.nextFloat() * 0.12f,
                ox = ox, oy = oy,
                vx = cos(ang) * sp, vy = sin(ang) * sp,
                gravity = 1.5f + rnd.nextFloat() * 0.6f,
                size = 0.013f + rnd.nextFloat() * 0.015f,
                ratio = 0.45f + rnd.nextFloat() * 1.1f,
                color = CONFETTI[rnd.nextInt(CONFETTI.size)],
                round = rnd.nextInt(5) == 0,
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                rot0 = rnd.nextFloat() * 360f,
                swayAmp = 0.004f + rnd.nextFloat() * 0.012f,
                swayHz = 0.8f + rnd.nextFloat() * 1.6f,
            )
        }
    }

    // İki yandan patlayan konfeti topları
    shoot(26, 0.00f, -0.02f, 1.02f, -62f, 46f, 1.45f, 2.25f)
    shoot(26, 0.06f, 1.02f, 1.02f, -118f, 46f, 1.45f, 2.25f)
    // Logonun arkasından her yöne saçılan patlama
    shoot(34, 0.22f, 0.5f, 0.40f, -90f, 360f, 0.55f, 1.35f)
    // İkinci salvo: animasyonun ortasında tekrar patlasın
    shoot(20, 1.25f, -0.02f, 1.02f, -58f, 40f, 1.35f, 2.05f)
    shoot(20, 1.35f, 1.02f, 1.02f, -122f, 40f, 1.35f, 2.05f)
    shoot(16, 1.70f, 0.5f, 0.42f, -90f, 360f, 0.5f, 1.1f)
    // Sürekli dalga: yukarıdan süzülerek inen konfeti
    repeat(54) {
        list += Confetti(
            t0 = 0.6f + rnd.nextFloat() * 2.2f,
            ox = rnd.nextFloat(), oy = -0.06f,
            vx = (rnd.nextFloat() - 0.5f) * 0.12f, vy = 0.18f + rnd.nextFloat() * 0.22f,
            gravity = 0.16f + rnd.nextFloat() * 0.14f,
            size = 0.012f + rnd.nextFloat() * 0.013f,
            ratio = 0.4f + rnd.nextFloat() * 1.2f,
            color = CONFETTI[rnd.nextInt(CONFETTI.size)],
            round = rnd.nextInt(6) == 0,
            spin = (rnd.nextFloat() - 0.5f) * 520f,
            rot0 = rnd.nextFloat() * 360f,
            swayAmp = 0.012f + rnd.nextFloat() * 0.022f,
            swayHz = 0.5f + rnd.nextFloat() * 1.1f,
        )
    }
    return list
}

@Composable
fun CelebrationOverlay(celebration: Celebration, onFinish: () -> Unit) {
    val finish by rememberUpdatedState(onFinish)
    val clock = remember { Animatable(0f) }
    val confetti = remember { buildConfetti(Random(System.nanoTime())) }
    val cheer = remember { CHEERS.random() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        clock.animateTo(DURATION, tween((DURATION * 1000).toInt(), easing = LinearEasing))
        finish()
    }

    BackHandler { finish() }

    // Not: clock.value yalnızca graphicsLayer ve Canvas bloklarının içinde okunuyor;
    // böylece her karede yeniden besteleme olmuyor, sadece çizim yenileniyor.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = clock.value
                alpha = if (t > DURATION - FADE_OUT) {
                    ((DURATION - t) / FADE_OUT).coerceIn(0f, 1f)
                } else 1f
            }
            .background(
                Brush.radialGradient(
                    listOf(Color(0xE60F0A1C), Color(0xF2070510)),
                    radius = 1400f,
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { finish() },
        contentAlignment = Alignment.Center,
    ) {
        // Arka plan: dönen ışık hüzmeleri, şok dalgaları ve konfeti
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            drawRays(t)
            drawShockwave(t, 0.10f, 0.95f, Color(0xFFFFC24B))
            drawShockwave(t, 0.34f, 1.25f, Color(0xFFE23B4E))
            drawConfetti(confetti, t)
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Nabız atan altın hale
                Canvas(Modifier.size(300.dp)) {
                    val u = (clock.value - 0.08f).coerceAtLeast(0f)
                    val glow = 0.88f + 0.12f * sin(u * 4.2f)
                    val r = size.minDimension / 2f * glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0x66FFC24B), Color(0x00FFC24B)),
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                    )
                }
                // Deli Kadir: çizgi film gibi zıplayarak gelir, sonra hafifçe sallanır
                Image(
                    painter = painterResource(R.drawable.deli_kadir),
                    contentDescription = "Deli Kadir",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(190.dp)
                        .graphicsLayer {
                            val u = (clock.value - 0.08f).coerceAtLeast(0f)
                            val grow = 1f - exp(-10f * u)
                            val bounce = 1f + 0.26f * exp(-3.0f * u) * sin(12.5f * u)
                            val breathe = 1f + 0.035f * sin(u * 3.1f)
                            val s = grow * bounce * breathe
                            scaleX = s
                            scaleY = s
                            rotationZ = 11f * exp(-2.0f * u) * sin(10.5f * u) + 2.5f * sin(u * 2.7f)
                            alpha = grow
                        }
                        .clip(CircleShape),
                )
            }

            Text(
                "PARA GELDİ!",
                color = Color(0xFFFFC24B),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .graphicsLayer {
                        val u = (clock.value - 0.30f).coerceAtLeast(0f)
                        val p = popScale(clock.value, 0.30f)
                        scaleX = p
                        scaleY = p
                        alpha = p.coerceAtMost(1f)
                        rotationZ = 4f * exp(-3f * u) * sin(13f * u)
                    },
            )

            Text(
                celebration.amountText,
                color = Color.White,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .graphicsLayer {
                        val p = popScale(clock.value, 0.46f)
                        scaleX = p
                        scaleY = p
                        alpha = p.coerceAtMost(1f)
                    },
            )

            celebration.tryText?.let { tl ->
                Text(
                    tl,
                    color = Color(0xFFB9C6D6),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .graphicsLayer { alpha = popScale(clock.value, 0.58f).coerceIn(0f, 1f) },
                )
            }

            if (celebration.customer.isNotBlank()) {
                Text(
                    celebration.customer,
                    color = Color(0xFF9FB0C6),
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .graphicsLayer { alpha = popScale(clock.value, 0.66f).coerceIn(0f, 1f) },
                )
            }

            Text(
                cheer,
                color = Color(0xFFFFC24B).copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .graphicsLayer { alpha = popScale(clock.value, 0.85f).coerceIn(0f, 1f) },
            )
        }

        Text(
            "kapatmak için dokun",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .graphicsLayer { alpha = popScale(clock.value, 1.4f).coerceIn(0f, 1f) },
        )
    }
}

/** Gecikmeli, hafif aşan "pat" büyümesi. */
private fun popScale(t: Float, delay: Float): Float {
    val u = t - delay
    if (u <= 0f) return 0f
    return (1f - exp(-13f * u)) * (1f + 0.22f * exp(-5.5f * u) * sin(15f * u))
}

private fun DrawScope.drawConfetti(items: List<Confetti>, t: Float) {
    val w = size.width
    val h = size.height
    items.forEach { c ->
        val lt = t - c.t0
        if (lt <= 0f) return@forEach
        val sway = sin((lt + c.rot0) * c.swayHz * 2f * PI.toFloat()) * c.swayAmp * h
        val x = c.ox * w + c.vx * h * lt + sway
        val y = c.oy * h + c.vy * h * lt + 0.5f * c.gravity * h * lt * lt
        if (y > h + 80f || x < -120f || x > w + 120f) return@forEach
        val life = (1f - (lt - 2.2f) / 1.3f).coerceIn(0f, 1f)
        if (life <= 0f) return@forEach
        val col = c.color.copy(alpha = c.color.alpha * life)
        val sw = c.size * h
        val sh = sw * c.ratio
        if (c.round) {
            drawCircle(color = col, radius = sw / 2f, center = Offset(x, y))
        } else {
            withTransform({ rotate(c.rot0 + c.spin * lt, Offset(x, y)) }) {
                drawRect(color = col, topLeft = Offset(x - sw / 2f, y - sh / 2f), size = Size(sw, sh))
            }
        }
    }
}

/** Logonun arkasında yavaşça dönen ışık hüzmeleri. */
private fun DrawScope.drawRays(t: Float) {
    val appear = (t / 0.5f).coerceIn(0f, 1f)
    if (appear <= 0f) return
    val cx = size.width / 2f
    val cy = size.height * 0.40f
    val len = size.maxDimension
    withTransform({ rotate(t * 11f, Offset(cx, cy)) }) {
        repeat(14) { i ->
            val a = (i * 360f / 14f) * PI.toFloat() / 180f
            val spread = 7f * PI.toFloat() / 180f
            val p1 = Offset(cx + cos(a - spread) * len, cy + sin(a - spread) * len)
            val p2 = Offset(cx + cos(a + spread) * len, cy + sin(a + spread) * len)
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close()
                },
                color = Color(0xFFFFC24B).copy(alpha = 0.038f * appear),
            )
        }
    }
}

/** Dışa doğru açılan halka. */
private fun DrawScope.drawShockwave(t: Float, start: Float, dur: Float, color: Color) {
    val u = (t - start) / dur
    if (u <= 0f || u >= 1f) return
    val cx = size.width / 2f
    val cy = size.height * 0.40f
    val r = size.minDimension * (0.12f + 0.75f * (1f - (1f - u) * (1f - u)))
    drawCircle(
        color = color.copy(alpha = 0.45f * (1f - u)),
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = 9f * (1f - u) + 2f),
    )
}
