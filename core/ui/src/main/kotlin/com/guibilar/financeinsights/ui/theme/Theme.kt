package com.guibilar.financeinsights.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.toColorInt
import com.guibilar.financeinsights.core.ui.R

/**
 * The chart colours of the web app, stepped for light and dark and checked against their surfaces. The user's
 * categories bring their own colours; these are for the series the charts pick themselves.
 */
@Immutable
data class ChartColors(
    val text: Color,
    val muted: Color,
    val grid: Color,
    val axis: Color,
    val blue: Color,
    val orange: Color,
    val aqua: Color,
    val magenta: Color,
    /** Money in. */
    val positive: Color,
    /** Over a limit, or more spent than before. */
    val negative: Color,
) {
    val income get() = aqua
    val spending get() = orange
    val net get() = blue
    val savings get() = magenta
}

// Hearth (the Hestia identity, as on the web): slate blue, terracotta, sage and plum on cream; each at least 3:1.
val LightChartColors = ChartColors(
    text = Color(0xFF5B524A),
    muted = Color(0xFF7A6F64),
    grid = Color(0xFFEFE7DC),
    axis = Color(0xFFD9CDBD),
    blue = Color(0xFF3F6F8F),
    orange = Color(0xFFC2643C),
    aqua = Color(0xFF5F8F5A),
    magenta = Color(0xFFA35D84),
    positive = Color(0xFF4F7A4A),
    negative = Color(0xFFB42318),
)

val DarkChartColors = ChartColors(
    text = Color(0xFFD8CFC4),
    muted = Color(0xFFA89C8F),
    grid = Color(0xFF352F29),
    axis = Color(0xFF4A4239),
    blue = Color(0xFF6F9CBC),
    orange = Color(0xFFE0805A),
    aqua = Color(0xFF86B37F),
    magenta = Color(0xFFC987AB),
    positive = Color(0xFF86B37F),
    negative = Color(0xFFEC8070),
)

val LocalChartColors = staticCompositionLocalOf { LightChartColors }

// Hearth: a cream room with warm white cards, charcoal ink, terracotta and sage.
private val LightScheme = lightColorScheme(
    primary = Color(0xFFB5562F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7E3D8),
    onPrimaryContainer = Color(0xFF5A2410),
    secondary = Color(0xFF4F7A4A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2ECDD),
    onSecondaryContainer = Color(0xFF1E3A1B),
    // Honey: "Needs attention" and other notes.
    tertiary = Color(0xFF99601A),
    tertiaryContainer = Color(0xFFF8EAD2),
    onTertiaryContainer = Color(0xFF3D2706),
    // The page behind everything (Scaffold) is the same as surfaces: left out, it was Material's pinkish default.
    background = Color(0xFFFAF6EF),
    onBackground = Color(0xFF2B2622),
    surface = Color(0xFFFAF6EF),
    onSurface = Color(0xFF2B2622),
    onSurfaceVariant = Color(0xFF6B635C),
    surfaceVariant = Color(0xFFF3EDE3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color(0xFFF3EDE3),
    surfaceContainerHigh = Color(0xFFEFE7DC),
    surfaceContainerHighest = Color(0xFFEAE1D4),
    outline = Color(0xFFB9AC9C),
    outlineVariant = Color(0xFFEBE2D5),
    error = Color(0xFFB42318),
)

// An evening room: brown-black, cards a little lighter, warm light ink.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFEBA27F),
    onPrimary = Color(0xFF4A1E0C),
    primaryContainer = Color(0xFF6E3219),
    onPrimaryContainer = Color(0xFFFBE2D6),
    secondary = Color(0xFF9CC493),
    onSecondary = Color(0xFF16290F),
    secondaryContainer = Color(0xFF2F472A),
    onSecondaryContainer = Color(0xFFDDEDD6),
    tertiary = Color(0xFFE2B46F),
    tertiaryContainer = Color(0xFF3A2C18),
    onTertiaryContainer = Color(0xFFF6E3C2),
    background = Color(0xFF1C1916),
    onBackground = Color(0xFFEDE6DD),
    surface = Color(0xFF1C1916),
    onSurface = Color(0xFFEDE6DD),
    onSurfaceVariant = Color(0xFFBDB2A6),
    surfaceVariant = Color(0xFF312B26),
    surfaceContainerLowest = Color(0xFF161411),
    surfaceContainerLow = Color(0xFF26211D),
    surfaceContainer = Color(0xFF2B2622),
    surfaceContainerHigh = Color(0xFF312B26),
    surfaceContainerHighest = Color(0xFF3A332D),
    outline = Color(0xFF7A6F64),
    outlineVariant = Color(0xFF3B342D),
    error = Color(0xFFEC8070),
)

/** Fraunces with soft serifs, for titles and the big figures (as on the web). Bundled: `res/font/fraunces.ttf` (OFL). */
@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: Int, opticalSize: Float) = Font(
    R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("SOFT", 100f),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

/** Fraunces for display and headline sizes (large optical size). */
val HearthDisplay = FontFamily(fraunces(400, 72f), fraunces(600, 72f), fraunces(700, 72f))

/** Fraunces for titles and figures (smaller optical size: sturdier at 20 to 30 sp). */
val HearthTitle = FontFamily(fraunces(400, 24f), fraunces(600, 24f), fraunces(700, 24f))

private val HearthTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = HearthDisplay),
        displayMedium = base.displayMedium.copy(fontFamily = HearthDisplay),
        displaySmall = base.displaySmall.copy(fontFamily = HearthDisplay, fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontFamily = HearthDisplay, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = HearthDisplay, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = HearthTitle, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = HearthTitle, fontWeight = FontWeight.SemiBold),
    )
}

/** A module's own warm colour (Today's sections and their tiles), as on the web (`data-accent`). */
@Immutable
data class ModuleAccent(val color: Color, val container: Color)

private val LightAccents = mapOf(
    "money" to ModuleAccent(Color(0xFFA6492A), Color(0xFFF7E3D8)),
    "tasks" to ModuleAccent(Color(0xFF4A7344), Color(0xFFE2ECDD)),
    "journal" to ModuleAccent(Color(0xFF844A6D), Color(0xFFF1E1EA)),
    "inbox" to ModuleAccent(Color(0xFF99601A), Color(0xFFF8EAD2)),
    "documents" to ModuleAccent(Color(0xFF3C6886), Color(0xFFDFEAF2)),
    "home" to ModuleAccent(Color(0xFF8F5A3A), Color(0xFFF2E3D8)),
    "people" to ModuleAccent(Color(0xFFA44457), Color(0xFFF6DFE3)),
    "calendar" to ModuleAccent(Color(0xFF5B5A8C), Color(0xFFE6E5F1)),
)

private val DarkAccents = mapOf(
    "money" to ModuleAccent(Color(0xFFEBA27F), Color(0xFF3D2A20)),
    "tasks" to ModuleAccent(Color(0xFF9CC493), Color(0xFF26331F)),
    "journal" to ModuleAccent(Color(0xFFD5A3C2), Color(0xFF3A2431)),
    "inbox" to ModuleAccent(Color(0xFFE2B46F), Color(0xFF3A2C18)),
    "documents" to ModuleAccent(Color(0xFF93BCD8), Color(0xFF1F2D38)),
    "home" to ModuleAccent(Color(0xFFD7A888), Color(0xFF362619)),
    "people" to ModuleAccent(Color(0xFFE39AA8), Color(0xFF3B2027)),
    "calendar" to ModuleAccent(Color(0xFFAEACD9), Color(0xFF26253A)),
)

/** True in the dark theme (for colours the scheme does not have, such as [accentOf]). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The accent around a tile: a module's colour on Today, else the primary colour (see [accentOf]). */
val LocalModuleAccent = compositionLocalOf<ModuleAccent?> { null }

/** The warm colour of the module [id] ("money", "tasks", …); null for one without its own (it uses the primary). */
@Composable
fun accentOf(id: String?): ModuleAccent? = id?.let { (if (LocalDarkTheme.current) DarkAccents else LightAccents)[it] }

/**
 * Material 3 in Hestia's Hearth colours and type; with [dynamicColor], the wallpaper's colours on Android 12 and later
 * (off by default: the identity first). The charts keep their own palette.
 *
 * [ripples] false is for the screenshot tests (T28): a ripple is drawn by an Android view on the system clock, not on
 * the Compose test clock, so a capture soon after a tap showed it at a different point on each run.
 */
@Composable
fun FinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    ripples: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(
        LocalChartColors provides if (darkTheme) DarkChartColors else LightChartColors,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(colorScheme = scheme, typography = HearthTypography) {
            if (ripples) content() else CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
        }
    }
}

/** A "#RRGGBB" colour from the API; grey when it isn't one. */
fun parseHexColor(hex: String): Color = runCatching {
    Color(hex.toColorInt())
}.getOrDefault(Color(0xFF9CA3AF))
