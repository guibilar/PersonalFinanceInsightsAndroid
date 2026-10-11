package com.guibilar.financeinsights.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt

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

val LightChartColors = ChartColors(
    text = Color(0xFF4B5563),
    muted = Color(0xFF6B7280),
    grid = Color(0xFFE9ECF2),
    axis = Color(0xFFC9CFDB),
    blue = Color(0xFF2A78D6),
    orange = Color(0xFFEB6834),
    aqua = Color(0xFF1BAF7A),
    magenta = Color(0xFFE87BA4),
    positive = Color(0xFF14804F),
    negative = Color(0xFFC62E2E),
)

val DarkChartColors = ChartColors(
    text = Color(0xFFCBD5E1),
    muted = Color(0xFF94A3B8),
    grid = Color(0xFF262D3B),
    axis = Color(0xFF3B4558),
    blue = Color(0xFF3987E5),
    orange = Color(0xFFD95926),
    aqua = Color(0xFF199E70),
    magenta = Color(0xFFD55181),
    positive = Color(0xFF3DC48A),
    negative = Color(0xFFF27272),
)

val LocalChartColors = staticCompositionLocalOf { LightChartColors }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2A78D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E7FA),
    onPrimaryContainer = Color(0xFF0B2E57),
    // The page behind everything (Scaffold) is the same as surfaces: left out, it was Material's pinkish default.
    background = Color(0xFFF6F7FA),
    surface = Color(0xFFF6F7FA),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F6),
    error = Color(0xFFC62E2E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7AB0F0),
    onPrimary = Color(0xFF062A52),
    primaryContainer = Color(0xFF1D4F8C),
    onPrimaryContainer = Color(0xFFD9E7FA),
    background = Color(0xFF10131A),
    surface = Color(0xFF10131A),
    surfaceContainerLow = Color(0xFF171B24),
    surfaceContainer = Color(0xFF1C212C),
    error = Color(0xFFF27272),
)

/** Material 3, with the wallpaper's colours on Android 12 and later; the charts keep their own palette. */
@Composable
fun FinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalChartColors provides if (darkTheme) DarkChartColors else LightChartColors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** A "#RRGGBB" colour from the API; grey when it isn't one. */
fun parseHexColor(hex: String): Color = runCatching {
    Color(hex.toColorInt())
}.getOrDefault(Color(0xFF9CA3AF))
