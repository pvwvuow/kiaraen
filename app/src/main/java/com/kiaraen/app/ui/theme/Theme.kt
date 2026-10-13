package com.kiaraen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R

// Kiara gold identity — exact palette of the approved HTML prototype
// (scripts/kiara-app-template.html :root vars)
val Gold = Color(0xFFFEC60D)      // --gold
val GoldDark = Color(0xFFF0A900)  // --gold-d
val Cream = Color(0xFFFFFDF8)     // input/panel white-cream
val Paper = Color(0xFFF7F6F3)     // --bg
val Ink = Color(0xFF1C1A17)       // --ink
val Muted = Color(0xFF9C968B)     // --mut
val Line = Color(0xFFE3DFD2)      // input borders

/** Hard shadow under clay buttons — darkened gold (کف دکمه پف‌دار). */
val GoldShade = Color(0xFFD89A00)
/** Inner highlight for clay surfaces. */
val GoldLight = Color(0xFFFFE68A)
/** Soft cream card on paper background. */
val CardCream = Color(0xFFFFFBF0)
/** Progress-bar track (dark) from the approved folder card. */
val TrackBrown = Color(0xFF3B2F1B)
/** Dark charcoal clay button («ادامه بده» روی پوشه + هیرو راهنما). */
val Charcoal = Color(0xFF3A362F)
/** Charcoal pressed base (کف دکمه زغالی). */
val CharcoalShade = Color(0xFF232019)
/** Frozen chip background (روزهای آینده). */
val IceCream = Color(0xFFF1EDE4)
/** Destructive row text. */
val Danger = Color(0xFFB3261E)

/** The capsule fill of the approved progress tube (sampled from pr-fill). */
val CapsuleGradient = Brush.verticalGradient(
    listOf(Color(0xFFFEF7CC), Color(0xFFFDCE23), Color(0xFFE7A902)),
)
/** Cream-outlined pill for secondary actions («بازگشت» outline buttons). */
val OutlineInk = Color(0xFF8D8779)

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

/** Estedad — the approved Persian UI face (static instances pinned from the VF). */
val Estedad = FontFamily(
    Font(R.font.estedad_regular, FontWeight.Normal),
    Font(R.font.estedad_medium, FontWeight.Medium),
    Font(R.font.estedad_semibold, FontWeight.SemiBold),
    Font(R.font.estedad_bold, FontWeight.Bold),
    Font(R.font.estedad_black, FontWeight.Black),
)

/** Baloo 2 ExtraBold — only for the Latin «kiara» logotype. */
val Baloo = FontFamily(Font(R.font.baloo2_800, FontWeight.Black))

/**
 * Lalezar — the approved display face for the big friendly headlines
 * (یه قدم جلوتر…، وقت مرور رسیده، تیترهای پیشرفت). Single weight (400).
 */
val Lalezar = FontFamily(Font(R.font.lalezar_regular, FontWeight.Normal))

private val KiaraTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Black, fontSize = 18.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Estedad, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp,
    ),
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
        typography = KiaraTypography,
        content = content,
    )
}
