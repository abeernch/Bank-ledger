package com.abeernch.ledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.abeernch.ledger.R

/*
 * Design direction "A + B":
 *  Light = Calm Minimal: white cards on cool grey, one teal accent.
 *  Dark  = Dark Pro: near-black, lime accent.
 * Rules: one accent colour for anything tappable or active; green only for money in;
 * money out is plain text; orange only for warnings.
 */

/** Colours Material does not have a slot for. */
data class LedgerColors(
    val credit: Color,      // money in
    val warning: Color,     // over budget, needs attention
    val card: Color,        // card background
    val cardBorder: Color,
    val subtle: Color,      // inner tiles, empty progress track
    val accentSoft: Color   // tinted background behind accent elements
)

private val LightLedger = LedgerColors(
    credit = Color(0xFF15803D),
    warning = Color(0xFFC2410C),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFECEEF1),
    subtle = Color(0xFFF1F3F5),
    accentSoft = Color(0xFFDDEFED)
)

private val DarkLedger = LedgerColors(
    credit = Color(0xFF4ADE80),
    warning = Color(0xFFFB923C),
    card = Color(0xFF15181D),
    cardBorder = Color(0xFF1F232A),
    subtle = Color(0xFF1D2127),
    accentSoft = Color(0xFF2A3315)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEFED),
    onPrimaryContainer = Color(0xFF0F4C47),
    secondaryContainer = Color(0xFFDDEFED),
    onSecondaryContainer = Color(0xFF0F766E),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF1F3F5),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFB91C1C)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC8F25A),
    onPrimary = Color(0xFF0B0D10),
    primaryContainer = Color(0xFF2A3315),
    onPrimaryContainer = Color(0xFFE4F9A8),
    secondaryContainer = Color(0xFF2A3315),
    onSecondaryContainer = Color(0xFFC8F25A),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF0B0D10),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1D2127),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF15181D),
    surfaceContainer = Color(0xFF0F1114),
    surfaceContainerHigh = Color(0xFF1D2127),
    surfaceContainerHighest = Color(0xFF262A31),
    outline = Color(0xFF3A3F48),
    outlineVariant = Color(0xFF262A31),
    error = Color(0xFFF87171)
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Inter everywhere, with tabular digits so amounts line up in columns. */
private fun TextStyle.ledger() = copy(fontFamily = Inter, fontFeatureSettings = "tnum")

private val base = Typography()

private val LedgerTypography = Typography(
    displayLarge = base.displayLarge.ledger(),
    displayMedium = base.displayMedium.ledger(),
    displaySmall = base.displaySmall.ledger().copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.ledger().copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.ledger().copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.ledger().copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.ledger().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.ledger().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.ledger().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.ledger(),
    bodyMedium = base.bodyMedium.ledger(),
    bodySmall = base.bodySmall.ledger(),
    labelLarge = base.labelLarge.ledger(),
    labelMedium = base.labelMedium.ledger(),
    labelSmall = base.labelSmall.ledger()
)

val LocalLedgerColors = staticCompositionLocalOf { LightLedger }

/** Short access: LedgerTheme.colors.credit */
object LedgerTheme {
    val colors: LedgerColors
        @Composable get() = LocalLedgerColors.current
}

/** [themePref] is SYSTEM, LIGHT or DARK. */
@Composable
fun isDark(themePref: String): Boolean = when (themePref) {
    "LIGHT" -> false
    "DARK" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun LedgerTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLedgerColors provides if (dark) DarkLedger else LightLedger) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = LedgerTypography,
            content = content
        )
    }
}
