package com.cleanshot.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private fun scheme(c: CleanShotColors) = if (c.isDark) darkColorScheme(
    primary = c.accent, onPrimary = Palette.Paper, primaryContainer = c.accentSoft, onPrimaryContainer = c.textPrimary,
    secondary = c.accentAlt, onSecondary = Palette.Ink, tertiary = Palette.Iris,
    background = c.background, onBackground = c.textPrimary,
    surface = Palette.NightRaised, onSurface = c.textPrimary, surfaceVariant = Palette.NightRaised, onSurfaceVariant = c.textSecondary,
    surfaceContainerHigh = Palette.NightRaised, surfaceContainer = Palette.NightRaised,
    outline = c.glassBorder, outlineVariant = c.glassBorder, error = c.danger,
) else lightColorScheme(
    primary = c.accent, onPrimary = Palette.Paper, primaryContainer = c.accentSoft, onPrimaryContainer = c.textPrimary,
    secondary = c.accentAlt, onSecondary = Palette.Paper, tertiary = Palette.Iris,
    background = c.background, onBackground = c.textPrimary,
    surface = Palette.Paper, onSurface = c.textPrimary, surfaceVariant = Palette.Mist, onSurfaceVariant = c.textSecondary,
    surfaceContainerHigh = Palette.Paper, surfaceContainer = Palette.Paper,
    outline = c.glassBorder, outlineVariant = c.glassBorder, error = c.danger,
)

@Composable
fun CleanShotTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkCleanShotColors else LightCleanShotColors
    CompositionLocalProvider(LocalCleanShotColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors), typography = CleanShotTypography, content = content)
    }
}

/** Accessors for design-system tokens inside composables. */
object CleanShotTheme {
    val colors: CleanShotColors
        @Composable @ReadOnlyComposable get() = LocalCleanShotColors.current
}
