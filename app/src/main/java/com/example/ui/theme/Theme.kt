package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = WarmAmber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = SoftIvoryText,
    onSecondary = Color.White,
    secondaryContainer = SoftIvorySurfaceVariant,
    onSecondaryContainer = SoftIvoryText,
    tertiary = OpacSbnGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF14532D),
    background = SoftIvoryBg,
    onBackground = SoftIvoryText,
    surface = SoftIvorySurface,
    onSurface = SoftIvoryText,
    surfaceVariant = SoftIvorySurfaceVariant,
    onSurfaceVariant = SoftIvoryTextMuted,
    outline = SoftIvoryBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = GoldenSun,
    onPrimary = Color(0xFF451A03),
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = WarmDarkTextMuted,
    onSecondary = WarmDarkBg,
    secondaryContainer = WarmDarkSurfaceVariant,
    onSecondaryContainer = WarmDarkText,
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFDCFCE7),
    background = WarmDarkBg,
    onBackground = WarmDarkText,
    surface = WarmDarkSurface,
    onSurface = WarmDarkText,
    surfaceVariant = WarmDarkSurfaceVariant,
    onSurfaceVariant = WarmDarkTextMuted,
    outline = WarmDarkBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
