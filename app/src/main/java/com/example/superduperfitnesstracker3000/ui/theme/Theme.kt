package com.example.superduperfitnesstracker3000.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F5E2),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFFE85D25),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2D4),
    onSecondaryContainer = Color(0xFF3B1300),
    tertiary = Color(0xFF2563EB),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBE7FF),
    onTertiaryContainer = Color(0xFF00194D),
    background = Color(0xFFF2F7F4),
    onBackground = Color(0xFF14201B),
    surface = Color(0xFFF2F7F4),
    onSurface = Color(0xFF14201B),
    surfaceVariant = Color(0xFFE1ECE6),
    onSurfaceVariant = Color(0xFF4F5F57),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FBF9),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF5F9F7),
    surfaceContainerHighest = Color.White,
    outline = Color(0xFF8A9A92),
    outlineVariant = Color(0xFFD5E2DB),
    error = Color(0xFFD92D20),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE9B),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0B5A3C),
    onPrimaryContainer = Color(0xFFC8F5E2),
    secondary = Color(0xFFFF9F75),
    onSecondary = Color(0xFF4A1800),
    secondaryContainer = Color(0xFF6B2A0B),
    onSecondaryContainer = Color(0xFFFFE2D4),
    tertiary = Color(0xFF8AB4FF),
    onTertiary = Color(0xFF002A70),
    tertiaryContainer = Color(0xFF1D3F86),
    onTertiaryContainer = Color(0xFFDBE7FF),
    background = Color(0xFF0E1412),
    onBackground = Color(0xFFE2EBE6),
    surface = Color(0xFF0E1412),
    onSurface = Color(0xFFE2EBE6),
    surfaceVariant = Color(0xFF26312C),
    onSurfaceVariant = Color(0xFFB4C4BB),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF151D19),
    surfaceContainer = Color(0xFF18211D),
    surfaceContainerHigh = Color(0xFF1E2824),
    surfaceContainerHighest = Color(0xFF1B2420),
    outline = Color(0xFF7E8F87),
    outlineVariant = Color(0xFF34413B),
)

@Composable
fun SuperDuperFitnessTracker3000Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
