package com.trilink.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Pinterest Aesthetic Color Palette
val PinterestRed = Color(0xFFE60023)
val WarmSand = Color(0xFFFBF9F5)
val WarmSurface = Color(0xFFFFFFFF)
val WarmContainer = Color(0xFFF3EFEA)
val WarmContainerHigh = Color(0xFFECE7E0)
val SageGreen = Color(0xFF4C6B56)
val SoftDarkBg = Color(0xFF131315)
val SoftDarkSurface = Color(0xFF1C1C1E)
val SoftDarkContainer = Color(0xFF28282C)

private val DarkColorScheme = darkColorScheme(
    primary = PinterestRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF441217),
    onPrimaryContainer = Color(0xFFFFDADA),
    secondary = SageGreen,
    onSecondary = Color.White,
    background = SoftDarkBg,
    surface = SoftDarkSurface,
    surfaceVariant = SoftDarkContainer,
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFFAAAAAE),
    outline = Color(0xFF88888E)
)

private val LightColorScheme = lightColorScheme(
    primary = PinterestRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEAEA),
    onPrimaryContainer = Color(0xFF5D0B12),
    secondary = SageGreen,
    onSecondary = Color.White,
    background = WarmSand,
    surface = WarmSurface,
    surfaceVariant = WarmContainer,
    onBackground = Color(0xFF1B1B1D),
    onSurface = Color(0xFF1B1B1D),
    onSurfaceVariant = Color(0xFF6B6A70),
    outline = Color(0xFF9E9DA4)
)

@Composable
fun TriLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
