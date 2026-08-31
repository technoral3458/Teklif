package com.technoral.petkit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val AcikTema = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00251F),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2EBF2),
    onSecondaryContainer = Color(0xFF00272C),
    tertiary = Color(0xFFEF6C00),
    onTertiary = Color.White,
    background = Color(0xFFF6F8F8),
    onBackground = Color(0xFF1A1C1C),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1C),
    surfaceVariant = Color(0xFFE3EAE9),
    onSurfaceVariant = Color(0xFF3F4948),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val KoyuTema = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF00312B),
    primaryContainer = Color(0xFF004D42),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF00363D),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF452B00),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE1E3E3),
    surface = Color(0xFF181D1D),
    onSurface = Color(0xFFE1E3E3),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun PetkitTheme(
    koyu: Boolean = isSystemInDarkTheme(),
    dinamikRenk: Boolean = true,
    content: @Composable () -> Unit
) {
    val renkler = when {
        dinamikRenk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (koyu) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        koyu -> KoyuTema
        else -> AcikTema
    }
    MaterialTheme(colorScheme = renkler, content = content)
}
