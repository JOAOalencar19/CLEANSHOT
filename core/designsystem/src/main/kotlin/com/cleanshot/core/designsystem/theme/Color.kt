package com.cleanshot.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Raw palette of the CleanShot design system. */
object Palette {
    val Aurora = Color(0xFF5B7CFF)
    val AuroraDeep = Color(0xFF3D55E0)
    val Lumen = Color(0xFF2FD3B5)
    val Iris = Color(0xFF9A7BFF)
    val Coral = Color(0xFFFF5D73)
    val Amber = Color(0xFFFFB443)
    val Sky = Color(0xFF5AC8FA)
    val Emerald = Color(0xFF34C77B)
    val Slate = Color(0xFF8A93A6)

    val Ink = Color(0xFF07090E)
    val Night = Color(0xFF0C0F16)
    val NightRaised = Color(0xFF141824)
    val Mist = Color(0xFFF3F5FA)
    val Paper = Color(0xFFFFFFFF)
}

/** Semantic colours that Material 3 does not model (glass, privacy levels, verdicts). */
@Immutable
data class CleanShotColors(
    val isDark: Boolean,
    val background: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentAlt: Color,
    val accentSoft: Color,
    val privacyHigh: Color,
    val privacyMedium: Color,
    val privacyLow: Color,
    val privacyNone: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val orbA: Color,
    val orbB: Color,
    val orbC: Color,
)

val DarkCleanShotColors = CleanShotColors(
    isDark = true,
    background = Palette.Night,
    glassTop = Color.White.copy(alpha = 0.085f),
    glassBottom = Color.White.copy(alpha = 0.035f),
    glassBorder = Color.White.copy(alpha = 0.10f),
    glassHighlight = Color.White.copy(alpha = 0.28f),
    textPrimary = Color(0xFFF4F6FB),
    textSecondary = Color(0xFFB4BBCB),
    textTertiary = Color(0xFF7D8598),
    accent = Palette.Aurora,
    accentAlt = Palette.Lumen,
    accentSoft = Palette.Aurora.copy(alpha = 0.18f),
    privacyHigh = Palette.Coral,
    privacyMedium = Palette.Amber,
    privacyLow = Palette.Sky,
    privacyNone = Palette.Slate,
    success = Palette.Emerald,
    warning = Palette.Amber,
    danger = Palette.Coral,
    info = Palette.Sky,
    orbA = Palette.Aurora.copy(alpha = 0.42f),
    orbB = Palette.Iris.copy(alpha = 0.30f),
    orbC = Palette.Lumen.copy(alpha = 0.22f),
)

val LightCleanShotColors = CleanShotColors(
    isDark = false,
    background = Palette.Mist,
    glassTop = Color.White.copy(alpha = 0.86f),
    glassBottom = Color.White.copy(alpha = 0.62f),
    glassBorder = Color(0xFF0B1020).copy(alpha = 0.07f),
    glassHighlight = Color.White,
    textPrimary = Color(0xFF0E1220),
    textSecondary = Color(0xFF4B5366),
    textTertiary = Color(0xFF808A9E),
    accent = Palette.AuroraDeep,
    accentAlt = Color(0xFF12A88E),
    accentSoft = Palette.Aurora.copy(alpha = 0.12f),
    privacyHigh = Color(0xFFE5394F),
    privacyMedium = Color(0xFFD98500),
    privacyLow = Color(0xFF1C8FD1),
    privacyNone = Color(0xFF6E778B),
    success = Color(0xFF1E9E5B),
    warning = Color(0xFFD98500),
    danger = Color(0xFFE5394F),
    info = Color(0xFF1C8FD1),
    orbA = Palette.Aurora.copy(alpha = 0.22f),
    orbB = Palette.Iris.copy(alpha = 0.16f),
    orbC = Palette.Lumen.copy(alpha = 0.14f),
)

val LocalCleanShotColors = staticCompositionLocalOf { DarkCleanShotColors }
