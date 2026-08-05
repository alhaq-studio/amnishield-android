package com.alhaq.amnshield.utils

import android.app.Activity
import android.content.Context
import com.alhaq.amnshield.R

object ThemeUtils {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_STYLE = "theme_style"

    fun applyTheme(activity: Activity) {
        val themeId = resolveTheme(activity)
        activity.setTheme(themeId)

        val isDark = themeId == R.style.Theme_AmnShield_Purple || themeId == R.style.Theme_AmnShield_Gradient
        try {
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        } catch (e: Exception) {
            // Ignore if decorView or window isn't ready
        }
    }

    fun resolveTheme(context: Context): Int {
        val themeStyle = context
            .getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            .getString(KEY_THEME_STYLE, "emerald")

        return when (themeStyle) {
            "gradient" -> R.style.Theme_AmnShield_Gradient
            "purple" -> R.style.Theme_AmnShield_Purple
            "emerald" -> R.style.Theme_AmnShield_Emerald
            "sunset" -> R.style.Theme_AmnShield_Sunset
            else -> R.style.Theme_AmnShield_Emerald
        }
    }

    fun resolveAppTheme(context: Context): com.alhaq.amnshield.ui.state.AppTheme {
        val themeStyle = context
            .getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            .getString(KEY_THEME_STYLE, "emerald")

        return when (themeStyle) {
            "sunset" -> com.alhaq.amnshield.ui.state.AppTheme.SUNSET_GLOW
            "emerald" -> com.alhaq.amnshield.ui.state.AppTheme.EMERALD_CALM
            "purple" -> com.alhaq.amnshield.ui.state.AppTheme.COSMIC_NIGHT
            else -> com.alhaq.amnshield.ui.state.AppTheme.EMERALD_CALM
        }
    }
}
