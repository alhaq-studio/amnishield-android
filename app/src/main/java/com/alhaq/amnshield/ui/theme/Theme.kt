package com.alhaq.amnshield.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.alhaq.amnshield.ui.state.AppTheme

// 1. EMERALD CALM (Pearl Teal & Sage Option - Global Default)
private val EmeraldColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = EmeraldSecondary,
    onSecondary = EmeraldOnSecondary,
    secondaryContainer = EmeraldSecondaryContainer,
    onSecondaryContainer = EmeraldOnSecondaryContainer,
    tertiary = EmeraldTertiary,
    onTertiary = EmeraldOnTertiary,
    tertiaryContainer = EmeraldTertiaryContainer,
    onTertiaryContainer = EmeraldOnTertiaryContainer,
    background = EmeraldBg,
    onBackground = EmeraldOnSurface,
    surface = EmeraldSurface,
    onSurface = EmeraldOnSurface,
    surfaceVariant = EmeraldSurfaceVariant,
    onSurfaceVariant = EmeraldTextMuted,
    surfaceContainerLowest = EmeraldSurfaceContainerLowest,
    surfaceContainerLow = EmeraldSurfaceContainerLow,
    surfaceContainer = EmeraldSurfaceContainer,
    surfaceContainerHigh = EmeraldSurfaceContainerHigh,
    surfaceContainerHighest = EmeraldSurfaceContainerHighest,
    outline = EmeraldOutline,
    outlineVariant = EmeraldOutlineVariant
)

// 2. SUNSET GLOW (Warm Sand & Terracotta Option)
private val SunsetColorScheme = lightColorScheme(
    primary = SunsetPrimary,
    onPrimary = SunsetOnPrimary,
    primaryContainer = SunsetPrimaryContainer,
    onPrimaryContainer = SunsetOnPrimaryContainer,
    secondary = SunsetSecondary,
    onSecondary = SunsetOnSecondary,
    secondaryContainer = SunsetSecondaryContainer,
    onSecondaryContainer = SunsetOnSecondaryContainer,
    tertiary = SunsetTertiary,
    onTertiary = SunsetOnTertiary,
    tertiaryContainer = SunsetTertiaryContainer,
    onTertiaryContainer = SunsetOnTertiaryContainer,
    background = SunsetBg,
    onBackground = SunsetOnSurface,
    surface = SunsetSurface,
    onSurface = SunsetOnSurface,
    surfaceVariant = SunsetSurfaceVariant,
    onSurfaceVariant = SunsetTextMuted,
    surfaceContainerLowest = SunsetSurfaceContainerLowest,
    surfaceContainerLow = SunsetSurfaceContainerLow,
    surfaceContainer = SunsetSurfaceContainer,
    surfaceContainerHigh = SunsetSurfaceContainerHigh,
    surfaceContainerHighest = SunsetSurfaceContainerHighest,
    outline = SunsetOutline,
    outlineVariant = SunsetOutlineVariant
)

// 3. COSMIC NIGHT (Deep Violet Tech Option - High Contrast Dark Mode)
private val CosmicColorScheme = darkColorScheme(
    primary = CosmicPrimary,
    onPrimary = CosmicOnPrimary,
    primaryContainer = CosmicPrimaryContainer,
    onPrimaryContainer = CosmicOnPrimaryContainer,
    secondary = CosmicSecondary,
    onSecondary = CosmicOnSecondary,
    secondaryContainer = CosmicSecondaryContainer,
    onSecondaryContainer = CosmicOnSecondaryContainer,
    tertiary = CosmicTertiary,
    onTertiary = CosmicOnTertiary,
    tertiaryContainer = CosmicTertiaryContainer,
    onTertiaryContainer = CosmicOnTertiaryContainer,
    error = CosmicError,
    onError = CosmicOnError,
    errorContainer = CosmicErrorContainer,
    onErrorContainer = CosmicOnErrorContainer,
    background = CosmicBg,
    onBackground = CosmicOnSurface,
    surface = CosmicSurface,
    onSurface = CosmicOnSurface,
    surfaceVariant = CosmicSurfaceVariant,
    onSurfaceVariant = CosmicTextMuted,
    surfaceContainerLowest = CosmicSurfaceContainerLowest,
    surfaceContainerLow = CosmicSurfaceContainerLow,
    surfaceContainer = CosmicSurfaceContainer,
    surfaceContainerHigh = CosmicSurfaceContainerHigh,
    surfaceContainerHighest = CosmicSurfaceContainerHighest,
    outline = CosmicOutline,
    outlineVariant = CosmicOutlineVariant
)

@Composable
fun AmnShieldTheme(
    appTheme: AppTheme = AppTheme.SYSTEM_DEFAULT,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val effectiveTheme = when (appTheme) {
        AppTheme.SYSTEM_DEFAULT -> if (isSystemDark) AppTheme.COSMIC_NIGHT else AppTheme.EMERALD_CALM
        else -> appTheme
    }

    val colorScheme = when (effectiveTheme) {
        AppTheme.EMERALD_CALM -> EmeraldColorScheme
        AppTheme.SUNSET_GLOW -> SunsetColorScheme
        AppTheme.COSMIC_NIGHT -> CosmicColorScheme
        AppTheme.SYSTEM_DEFAULT -> if (isSystemDark) CosmicColorScheme else EmeraldColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                val isDarkTheme = effectiveTheme == AppTheme.COSMIC_NIGHT
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

