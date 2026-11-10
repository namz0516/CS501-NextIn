package com.example.nextin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF6652A3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF302052),

    secondary = Color(0xFF536B91),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EDFA),
    onSecondaryContainer = Color(0xFF203653),

    tertiary = Color(0xFF287755),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF3E6),
    onTertiaryContainer = Color(0xFF174E35),

    background = Color(0xFFFCFAFF),
    onBackground = Color(0xFF25212C),
    surface = Color.White,
    onSurface = Color(0xFF25212C),
    surfaceVariant = Color(0xFFF1EDF8),
    onSurfaceVariant = Color(0xFF655F70),
    outline = Color(0xFF797181),
    outlineVariant = Color(0xFFE1DBE9),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF8E1DE),
    onErrorContainer = Color(0xFF7A231D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF35235D),
    primaryContainer = Color(0xFF4D3B73),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondaryContainer = Color(0xFF293C58),
    onSecondaryContainer = Color(0xFFD9E7FF),
    tertiaryContainer = Color(0xFF174E35),
    onTertiaryContainer = Color(0xFFDDF3E6),
    background = Color(0xFF15121B),
    onBackground = Color(0xFFEAE2F0),
    surface = Color(0xFF1D1924),
    onSurface = Color(0xFFEAE2F0),
    surfaceVariant = Color(0xFF302A3A),
    onSurfaceVariant = Color(0xFFCEC4D8)
)

private val NextInShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NextInTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            DarkColors
        } else {
            LightColors
        },
        typography = Typography(),
        shapes = NextInShapes,
        content = content
    )
}