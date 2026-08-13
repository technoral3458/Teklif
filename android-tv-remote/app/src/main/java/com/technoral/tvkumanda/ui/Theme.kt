package com.technoral.tvkumanda.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Kumanda karanlik odada kullanildigi icin tema sabit koyu; ekran isigi
 * mumkun oldugunca az.
 */
private val ColorScheme = darkColorScheme(
    primary = Color(0xFF7FC4FF),
    onPrimary = Color(0xFF06263F),
    primaryContainer = Color(0xFF1E4C72),
    onPrimaryContainer = Color(0xFFD3E9FF),
    secondary = Color(0xFF9FCBA8),
    onSecondary = Color(0xFF0B2913),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE3E6EB),
    surface = Color(0xFF161A21),
    onSurface = Color(0xFFE3E6EB),
    surfaceVariant = Color(0xFF232932),
    onSurfaceVariant = Color(0xFFC2C7D0),
    outline = Color(0xFF3A424E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun TvKumandaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography(),
        content = content,
    )
}
