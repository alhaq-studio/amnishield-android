package com.alhaq.amnishield.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Primary top-level navigation destinations for AmniShield Adaptive Navigation.
 */
enum class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
) {
    BLOCKS(
        route = "blocks",
        selectedIcon = Icons.Filled.Shield,
        unselectedIcon = Icons.Outlined.Shield,
        label = "Shield"
    ),
    FOCUS(
        route = "focus",
        selectedIcon = Icons.Filled.Timer,
        unselectedIcon = Icons.Outlined.Timer,
        label = "Focus"
    ),
    STATS(
        route = "stats",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        label = "Insights"
    ),
    SETTINGS(
        route = "settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        label = "Settings"
    ),
    PROFILE(
        route = "profile",
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle,
        label = "Profile"
    )
}

/**
 * Deep routes and sub-screens throughout the application.
 */
object AppRoutes {
    const val BLOCKS = "blocks"
    const val FOCUS = "focus"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    
    // Sub-screens & Managers
    const val CREATE_APP_RULE = "create_app_rule"
    const val CREATE_KEYWORD_RULE = "create_keyword_rule"
    const val CREATE_WEB_RULE = "create_web_rule"
    const val CREATE_REELS_RULE = "create_reels_rule"
    const val CREATE_FOCUS_RULE = "create_focus_rule"
    const val ADVANCED_SETTINGS = "advanced_settings"
    const val REELS_METRICS = "reels_metrics"
    const val ONBOARDING_PERMISSIONS = "onboarding_permissions"
    const val ACCESSIBILITY_GUIDE = "accessibility_guide"
    const val REMINDERS = "reminders"
}
