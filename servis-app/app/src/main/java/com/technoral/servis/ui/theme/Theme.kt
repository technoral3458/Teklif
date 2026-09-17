package com.technoral.servis.ui.theme

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

private val Petrol = Color(0xFF0F4C75)
private val PetrolLight = Color(0xFF3282B8)
private val Amber = Color(0xFFB26A00)

private val LightColors = lightColorScheme(
    primary = Petrol,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E6F5),
    onPrimaryContainer = Color(0xFF04263C),
    secondary = Color(0xFF41637B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8F2),
    onSecondaryContainer = Color(0xFF17242E),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2B8),
    onTertiaryContainer = Color(0xFF3A2200),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF121A21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121A21),
    surfaceVariant = Color(0xFFE6ECF2),
    onSurfaceVariant = Color(0xFF47535E),
    outline = Color(0xFF9FAEBB),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = PetrolLight,
    onPrimary = Color(0xFF00304D),
    primaryContainer = Color(0xFF0B3D5C),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFFA9C8DE),
    onSecondary = Color(0xFF12323F),
    secondaryContainer = Color(0xFF2A4857),
    onSecondaryContainer = Color(0xFFDCE8F2),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF442B00),
    tertiaryContainer = Color(0xFF5F3F00),
    onTertiaryContainer = Color(0xFFFFE2B8),
    background = Color(0xFF0E1419),
    onBackground = Color(0xFFDFE3E8),
    surface = Color(0xFF151C23),
    onSurface = Color(0xFFDFE3E8),
    surfaceVariant = Color(0xFF3A464F),
    onSurfaceVariant = Color(0xFFBFCAD4),
    outline = Color(0xFF6D7B87),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun TeknoServisTheme(darkTheme: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkTheme ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** Durum rozetleri için tema dışı sabit renkler. */
object StatusColors {
    val success = Color(0xFF1B7F4B)
    val successBg = Color(0x221B7F4B)
    val warning = Color(0xFFB26A00)
    val warningBg = Color(0x22B26A00)
    val danger = Color(0xFFB3261E)
    val dangerBg = Color(0x22B3261E)
    val info = Color(0xFF0F4C75)
    val infoBg = Color(0x220F4C75)
    val neutral = Color(0xFF64748B)
    val neutralBg = Color(0x2264748B)
}
