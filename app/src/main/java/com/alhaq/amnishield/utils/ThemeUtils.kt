package com.alhaq.amnishield.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.state.AppTheme

object ThemeUtils {
    const val PREFS_NAME = "theme_prefs"
    const val KEY_THEME_STYLE = "theme_style"
    const val THEME_SYSTEM = "system"
    const val THEME_EMERALD = "emerald"
    const val THEME_PURPLE = "purple"
    const val THEME_SUNSET = "sunset"

    fun isSystemInDarkMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    fun applyTheme(activity: Activity) {
        val themeId = resolveTheme(activity)
        activity.setTheme(themeId)

        val isDark = themeId == R.style.Theme_AmniShield_Purple || themeId == R.style.Theme_AmniShield_Gradient
        try {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        } catch (e: Exception) {
            // Ignore if decorView or window isn't ready
        }
    }

    fun resolveTheme(context: Context): Int {
        val themeStyle = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_STYLE, THEME_SYSTEM) ?: THEME_SYSTEM

        return when (themeStyle) {
            THEME_PURPLE, "cosmic" -> R.style.Theme_AmniShield_Purple
            THEME_EMERALD -> R.style.Theme_AmniShield_Emerald
            THEME_SUNSET -> R.style.Theme_AmniShield_Sunset
            "gradient" -> R.style.Theme_AmniShield_Gradient
            THEME_SYSTEM -> {
                if (isSystemInDarkMode(context)) {
                    R.style.Theme_AmniShield_Purple
                } else {
                    R.style.Theme_AmniShield_Emerald
                }
            }
            else -> {
                if (isSystemInDarkMode(context)) {
                    R.style.Theme_AmniShield_Purple
                } else {
                    R.style.Theme_AmniShield_Emerald
                }
            }
        }
    }

    fun resolveAppTheme(context: Context): AppTheme {
        val themeStyle = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_STYLE, THEME_SYSTEM) ?: THEME_SYSTEM

        return when (themeStyle) {
            THEME_SUNSET -> AppTheme.SUNSET_GLOW
            THEME_EMERALD -> AppTheme.EMERALD_CALM
            THEME_PURPLE, "cosmic" -> AppTheme.COSMIC_NIGHT
            THEME_SYSTEM -> {
                if (isSystemInDarkMode(context)) {
                    AppTheme.COSMIC_NIGHT
                } else {
                    AppTheme.EMERALD_CALM
                }
            }
            else -> {
                if (isSystemInDarkMode(context)) {
                    AppTheme.COSMIC_NIGHT
                } else {
                    AppTheme.EMERALD_CALM
                }
            }
        }
    }

    fun getSelectedThemePref(context: Context): String {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_STYLE, THEME_SYSTEM) ?: THEME_SYSTEM
    }
}

