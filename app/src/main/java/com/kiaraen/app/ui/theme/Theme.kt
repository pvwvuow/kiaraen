package com.kiaraen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Kiara gold identity — exact palette of the approved HTML prototype
// (scripts/kiara-app-template.html :root vars)
val Gold = Color(0xFFFEC60D)      // --gold
val GoldDark = Color(0xFFF0A900)  // --gold-d
val Cream = Color(0xFFFFFDF8)     // input/panel white-cream
val Paper = Color(0xFFF7F6F3)     // --bg
val Ink = Color(0xFF1C1A17)       // --ink
val Muted = Color(0xFF9C968B)     // --mut
val Line = Color(0xFFE3DFD2)      // input borders

private val LightColors = lightColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = Color(0xFFFFE68A),
    onPrimaryContainer = Ink,
    secondary = GoldDark,
    onSecondary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEAE0),
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3A301F),
    onPrimaryContainer = Cream,
    secondary = GoldDark,
    onSecondary = Ink,
    background = Color(0xFF171310),
    onBackground = Cream,
    surface = Color(0xFF201A15),
    onSurface = Cream,
    surfaceVariant = Color(0xFF2B2318),
    onSurfaceVariant = Color(0xFFB7B1A2),
    outline = Color(0xFF4A4436),
    outlineVariant = Color(0xFF4A4436),
)

/**
 * The approved design is LIGHT ONLY: several screens draw [Ink] text directly,
 * which was invisible on the dark background whenever the phone used a dark
 * system theme. [DarkColors] stays for a future, fully designed dark mode.
 */
@Composable
fun KiaraTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
