package com.kiaraen.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Kiara gold identity (matches the HTML prototype)
val Gold = Color(0xFFC9A227)
val GoldDark = Color(0xFF8C6D1A)
val Cream = Color(0xFFFFF8E7)
val Ink = Color(0xFF2B2118)

private val LightColors = lightColorScheme(
    primary = Gold,
    onPrimary = Color.White,
    primaryContainer = Cream,
    onPrimaryContainer = Ink,
    secondary = GoldDark,
    background = Color(0xFFFFFCF5),
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3A301F),
    onPrimaryContainer = Cream,
    background = Color(0xFF171310),
    surface = Color(0xFF201A15),
    onBackground = Cream,
    onSurface = Cream,
)

@Composable
fun KiaraTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
