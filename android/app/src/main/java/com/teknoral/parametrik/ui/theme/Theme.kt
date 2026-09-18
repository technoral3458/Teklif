package com.teknoral.parametrik.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.teknoral.parametrik.data.local.ThemeMode

/** Panel ahşabından alınan sıcak sarı-kahve palet. */
val Kavun = Color(0xFFD0AA70)
val Kayit = Color(0xFF967040)
val UyariTuruncu = Color(0xFFE8A33D)

private val DarkColors = darkColorScheme(
    primary = Kavun,
    onPrimary = Color(0xFF2B1D07),
    primaryContainer = Color(0xFF4A3717),
    onPrimaryContainer = Color(0xFFF3DDB8),
    secondary = Color(0xFFCFC3A8),
    onSecondary = Color(0xFF322B1B),
    background = Color(0xFF12100C),
    onBackground = Color(0xFFECE3D3),
    surface = Color(0xFF1B1813),
    onSurface = Color(0xFFECE3D3),
    surfaceVariant = Color(0xFF2A241B),
    onSurfaceVariant = Color(0xFFCFC3A8),
    outline = Color(0xFF6E6353),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF5F1509)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5A1E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5DFB4),
    onPrimaryContainer = Color(0xFF281900),
    secondary = Color(0xFF6B5D42),
    onSecondary = Color.White,
    background = Color(0xFFFAF6EE),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEDE1CC),
    onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7F7667),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun ParametrikTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
