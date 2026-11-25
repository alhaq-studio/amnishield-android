package com.alhaq.amnishield.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.alhaq.amnishield.ui.navigation.AppRoutes
import com.alhaq.amnishield.ui.navigation.TopLevelDestination
import com.alhaq.amnishield.ui.screens.*
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import com.alhaq.amnishield.utils.ReelsStatsManager

@Composable
fun AmniShieldAdaptiveApp(
    viewModel: AmniShieldViewModel,
    state: AmniShieldState,
    navController: NavHostController = rememberNavController(),
    isGoogleSignedIn: Boolean = false,
    onGoogleSignIn: () -> Unit = {},
    onGoogleSignOut: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onStartFocusMode: (Int, Int, Set<String>) -> Unit = { _, _, _ -> },
    onStopFocusMode: () -> Unit = {},
    onBackupRestore: () -> Unit = {},
    onShareCrashLogs: () -> Unit = {},
    onHelpFAQ: () -> Unit = {},
    onAbout: () -> Unit = {},
    onLanguage: () -> Unit = {}
) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Top-level adaptive scaffold
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = currentRoute == destination.route
                item(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.BLOCKS.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
            exitTransition = { fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
            popEnterTransition = { fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
            popExitTransition = { fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
        ) {
            // 1. Shield (Blocks) Main
            composable(TopLevelDestination.BLOCKS.route) {
                BlocksManagerAdaptiveScreen(
                    state = state,
                    viewModel = viewModel
                )
            }

            // 2. Focus Mode
            composable(TopLevelDestination.FOCUS.route) {
                FocusScreen(
                    isServiceEnabled = state.isMainServiceEnabled,
                    isFocusModeActive = state.isFocusModeActive,
                    focusModeEndTime = 0L,
                    installedApps = emptyList(),
                    preSelectedApps = emptySet(),
                    defaultMode = 0,
                    onStartFocusSession = onStartFocusMode,
                    onStopFocusSession = onStopFocusMode,
                    onOpenFocusConfig = { navController.navigate(AppRoutes.CREATE_FOCUS_RULE) },
                    onConfigureSchedules = { navController.navigate(AppRoutes.CREATE_FOCUS_RULE) },
                    onEnableService = onOpenAccessibilitySettings,
                    focusSchedules = state.scheduleRules.filter { it.targetBlockerType == "Focus Mode" },
                    onCreateFocusSchedule = { navController.navigate(AppRoutes.CREATE_FOCUS_RULE) }
                )
            }

            // 3. Stats & Insights (Adaptive Multi-Pane)
            composable(TopLevelDestination.STATS.route) {
                StatsAdaptiveScreen(
                    state = state,
                    viewModel = viewModel
                )
            }

            // 4. Settings
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onNavigateToProfile = { navController.navigate(TopLevelDestination.PROFILE.route) },
                    onBackupRestore = onBackupRestore,
                    onReminders = { navController.navigate(AppRoutes.REMINDERS) },
                    onShareCrashLogs = onShareCrashLogs,
                    onDiagnostics = { navController.navigate(AppRoutes.ADVANCED_SETTINGS) },
                    onHelpFAQ = onHelpFAQ,
                    onAbout = onAbout,
                    onLanguage = onLanguage,
                    onSignOut = onGoogleSignOut,
                    onToggleWebFilter = { viewModel.toggleWebFilter() },
                    onToggleUsageLimit = { viewModel.toggleSchedule() },
                    showTopAppBar = false
                )
            }

            // 5. Profile & Cloud Sync
            composable(TopLevelDestination.PROFILE.route) {
                ProfileScreen(
                    state = state,
                    viewModel = viewModel,
                    isGoogleSignedIn = isGoogleSignedIn,
                    onGoogleSignIn = onGoogleSignIn,
                    onGoogleSignOut = onGoogleSignOut,
                    onBack = { navController.popBackStack() }
                )
            }

            // Sub-destinations
            composable(AppRoutes.CREATE_FOCUS_RULE) {
                CreateFocusModeRuleScreen(
                    state = state,
                    onSaveRule = { rule ->
                        viewModel.addScheduleRule(rule)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AppRoutes.ADVANCED_SETTINGS) {
                AdvancedScreen(
                    state = state,
                    onNavigateToAppBlocker = { navController.navigate(TopLevelDestination.BLOCKS.route) },
                    onNavigateToKeywordBlocker = { navController.navigate(TopLevelDestination.BLOCKS.route) },
                    onNavigateToWebBlocker = { navController.navigate(TopLevelDestination.BLOCKS.route) },
                    onNavigateToReelsBlocker = { navController.navigate(TopLevelDestination.BLOCKS.route) },
                    onNavigateToAntiUninstall = {},
                    onNavigateToUsageTracker = { navController.navigate(TopLevelDestination.STATS.route) },
                    onNavigateToPremium = { navController.navigate(TopLevelDestination.PROFILE.route) },
                    onTogglePinSecurity = { enabled, pin -> viewModel.updatePinSettings(enabled, false) },
                    onToggleAppLock = { enabled -> viewModel.updatePinSettings(enabled, state.isBypassPinLockEnabled) }
                )
            }

            composable(AppRoutes.REELS_METRICS) {
                val reelsStatsManager = remember { ReelsStatsManager.getInstance(context) }
                val summary = remember { reelsStatsManager.getFullMetricsSummary() }
                ReelsMetricsScreen(
                    summary = summary,
                    dailyLimit = 50,
                    isBlockerEnabled = state.isReelsBlockerEnabled,
                    onBack = { navController.popBackStack() },
                    onRefresh = {},
                    onConfigureRules = { navController.navigate(TopLevelDestination.BLOCKS.route) }
                )
            }

            composable(AppRoutes.ONBOARDING_PERMISSIONS) {
                PermissionsScreen(
                    onContinue = { navController.navigate(AppRoutes.ACCESSIBILITY_GUIDE) },
                    onRestoreBackup = onBackupRestore
                )
            }

            composable(AppRoutes.ACCESSIBILITY_GUIDE) {
                AccessibilityGuideScreen(
                    onBack = { navController.popBackStack() },
                    onFinish = { navController.popBackStack(TopLevelDestination.BLOCKS.route, inclusive = false) }
                )
            }

            composable(AppRoutes.REMINDERS) {
                RemindersScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
