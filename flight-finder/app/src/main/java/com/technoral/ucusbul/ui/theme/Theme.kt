package com.technoral.ucusbul.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5C8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE7FA),
    onPrimaryContainer = Color(0xFF00293F),
    secondary = Color(0xFF1F6F54),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F0DE),
    onSecondaryContainer = Color(0xFF00281B),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4EAF1)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ECBF2),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004A6F),
    onPrimaryContainer = Color(0xFFCDE7FA),
    secondary = Color(0xFF8FD5BA),
    onSecondary = Color(0xFF00382A),
    background = Color(0xFF101418),
    surface = Color(0xFF171C21)
)

@Composable
fun UcusBulTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
