package com.alhaq.amnishield.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import com.alhaq.amnishield.ui.state.AppTheme
import com.alhaq.amnishield.ui.components.AmniShieldToggleButton
import com.alhaq.amnishield.ui.components.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AmniShieldState,
    viewModel: AmniShieldViewModel,
    onNavigateToProfile: () -> Unit,
    onBackupRestore: () -> Unit,
    onReminders: () -> Unit,
    onShareCrashLogs: () -> Unit,
    onDiagnostics: () -> Unit = {},
    onHelpFAQ: () -> Unit,
    onAbout: () -> Unit,
    onLanguage: () -> Unit,
    onSignOut: () -> Unit,
    onToggleWebFilter: (Boolean) -> Unit,
    onToggleUsageLimit: (Boolean) -> Unit,
    onToggleAppUsageTracking: (Boolean) -> Unit = {},
    onToggleWebsiteUsageTracking: (Boolean) -> Unit = {},
    onToggleReelsTracking: (Boolean) -> Unit = {},
    onUpdatePinResetCooldown: (Int) -> Unit = {},
    onUpdateEmergencyCooldown: (Int) -> Unit = {},
    showTopAppBar: Boolean = false,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var showPinCooldownDialog by remember { mutableStateOf(false) }
    var showEmergencyCooldownDialog by remember { mutableStateOf(false) }

    if (showPinCooldownDialog) {
        CooldownSelectionDialog(
            title = "PIN Reset Cooldown",
            subtitle = "Enforce a minimum waiting delay before a forgotten PIN can be reset. Hard floor: 5 minutes.",
            selectedMinutes = state.pinResetCooldownMinutes,
            onDismiss = { showPinCooldownDialog = false },
            onSelectMinutes = { mins ->
                showPinCooldownDialog = false
                onUpdatePinResetCooldown(mins)
            }
        )
    }

    if (showEmergencyCooldownDialog) {
        CooldownSelectionDialog(
            title = "Emergency Access Cooldown",
            subtitle = "Enforce a minimum emergency delay before protection can be overridden in Timed Mode. Hard floor: 5 minutes.",
            selectedMinutes = state.emergencyAccessCooldownMinutes,
            onDismiss = { showEmergencyCooldownDialog = false },
            onSelectMinutes = { mins ->
                showEmergencyCooldownDialog = false
                onUpdateEmergencyCooldown(mins)
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showTopAppBar) {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            // USER PROFILE & ACCOUNT
            item {
                Text(
                    text = "PROFILE & ACCOUNT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { onNavigateToProfile() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.userName.take(2).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.userName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = state.userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Edit Profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Integrated Sign Out Action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { onSignOut() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = "Sign Out",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(
                                        text = "Sign Out",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Sign out of your active account",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // APPEARANCE
            item {
                Text(
                    text = "APPEARANCE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        // Custom Theme Selection row
                        var expandedThemeMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { expandedThemeMenu = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(
                                        "App Theme",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val themePref = com.alhaq.amnishield.utils.ThemeUtils.getSelectedThemePref(context)
                                    val isSystemDark = com.alhaq.amnishield.utils.ThemeUtils.isSystemInDarkMode(context)
                                    Text(
                                        text = when (themePref) {
                                            com.alhaq.amnishield.utils.ThemeUtils.THEME_PURPLE -> "Cosmic Night (Deep Violet • Dark)"
                                            com.alhaq.amnishield.utils.ThemeUtils.THEME_EMERALD -> "Emerald Calm (Pearl Teal • Light)"
                                            com.alhaq.amnishield.utils.ThemeUtils.THEME_SUNSET -> "Sunset Glow (Warm Sand • Light)"
                                            else -> "System Default (Auto: ${if (isSystemDark) "Cosmic Night" else "Emerald Calm"})"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Selection popup Menu drop-down option
                            Box {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                DropdownMenu(
                                    expanded = expandedThemeMenu,
                                    onDismissRequest = { expandedThemeMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("System Default (Auto Detect • Default)") },
                                        onClick = {
                                            viewModel.updateTheme(AppTheme.SYSTEM_DEFAULT)
                                            val prefs = context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
                                            prefs.edit().putString("theme_style", com.alhaq.amnishield.utils.ThemeUtils.THEME_SYSTEM).apply()
                                            expandedThemeMenu = false
                                            (context as? android.app.Activity)?.recreate()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Cosmic Night (Deep Violet • Dark)") },
                                        onClick = {
                                            viewModel.updateTheme(AppTheme.COSMIC_NIGHT)
                                            val prefs = context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
                                            prefs.edit().putString("theme_style", com.alhaq.amnishield.utils.ThemeUtils.THEME_PURPLE).apply()
                                            expandedThemeMenu = false
                                            (context as? android.app.Activity)?.recreate()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Emerald Calm (Pearl Teal • Light)") },
                                        onClick = {
                                            viewModel.updateTheme(AppTheme.EMERALD_CALM)
                                            val prefs = context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
                                            prefs.edit().putString("theme_style", com.alhaq.amnishield.utils.ThemeUtils.THEME_EMERALD).apply()
                                            expandedThemeMenu = false
                                            (context as? android.app.Activity)?.recreate()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sunset Glow (Warm Sand • Light)") },
                                        onClick = {
                                            viewModel.updateTheme(AppTheme.SUNSET_GLOW)
                                            val prefs = context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
                                            prefs.edit().putString("theme_style", com.alhaq.amnishield.utils.ThemeUtils.THEME_SUNSET).apply()
                                            expandedThemeMenu = false
                                            (context as? android.app.Activity)?.recreate()
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { onLanguage() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    "Language",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Change",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // FEATURES
            item {
                Text(
                    text = "FEATURES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        // Feature Toggle: App Usage Tracker
                        SettingsToggleRow(
                            icon = Icons.Default.BarChart,
                            title = "App Usage Tracker",
                            checked = state.isAppUsageTrackingEnabled,
                            onCheckedChange = { onToggleAppUsageTracking(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Feature Toggle: Website Usage Tracker
                        SettingsToggleRow(
                            icon = Icons.Default.Language,
                            title = "Website Usage Tracker",
                            checked = state.isWebsiteUsageTrackingEnabled,
                            onCheckedChange = { onToggleWebsiteUsageTracking(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Feature Toggle: Reels Doom-Scroll Tracker
                        SettingsToggleRow(
                            icon = Icons.Default.PlayCircle,
                            title = "Reels & Shorts Tracker",
                            checked = state.isReelsTrackingEnabled,
                            onCheckedChange = { onToggleReelsTracking(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Feature Toggle: Web Filter Engine
                        SettingsToggleRow(
                            icon = Icons.Default.FilterAlt,
                            title = "Web Filter & Protection",
                            checked = state.isWebFilterEnabled,
                            onCheckedChange = { onToggleWebFilter(it) }
                        )
                    }
                }
            }

            // SECURITY & COOLDOWNS
            item {
                Text(
                    text = "SECURITY & COOLDOWNS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        // PIN Reset Cooldown
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPinCooldownDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PIN Reset Cooldown",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Minimum delay before forgotten PIN can be reset",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${state.pinResetCooldownMinutes} mins",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Emergency Access Cooldown
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEmergencyCooldownDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HourglassTop,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Emergency Access Cooldown",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Minimum delay before Timed Mode override unlocks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${state.emergencyAccessCooldownMinutes} mins",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // HOME SCREEN WIDGETS
            item {
                Text(
                    text = "HOME SCREEN WIDGETS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        WidgetPinRow(
                            icon = Icons.Outlined.BarChart,
                            title = "Screen Time & Usage Widget",
                            description = "Track total screen time and top 3 used apps in real-time.",
                            onPin = { pinWidgetToHomeScreen(context, "com.alhaq.amnishield.ui.widgets.ScreentimeWidgetProvider") }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        WidgetPinRow(
                            icon = Icons.Outlined.Movie,
                            title = "Reels & Shorts Metrics Widget",
                            description = "Monitor daily Reels scrolled count, usage limit progress, and blocker status.",
                            onPin = { pinWidgetToHomeScreen(context, "com.alhaq.amnishield.ui.widgets.ReelsMetricsWidgetProvider") }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        WidgetPinRow(
                            icon = Icons.Outlined.Timer,
                            title = "Quick Focus Space Widget",
                            description = "Start instant Quick Focus sessions with customized duration presets directly from your Home screen.",
                            onPin = { pinWidgetToHomeScreen(context, "com.alhaq.amnishield.ui.widgets.QuickFocusWidgetProvider") }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        WidgetPinRow(
                            icon = Icons.Outlined.SelfImprovement,
                            title = "Mindful Breathing Space Widget",
                            description = "Launches full-screen AmniSpace breathing directly from your home screen.",
                            onPin = { pinWidgetToHomeScreen(context, "com.alhaq.amnishield.ui.widgets.BreathingWidgetProvider") }
                        )

                        // Widget Breathing Duration Selector
                        val prefsLoader = remember { SavedPreferencesLoader(context) }
                        var widgetDurationMins by remember { mutableIntStateOf(prefsLoader.getBreathingWidgetDurationMinutes()) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = "Widget Session Duration: ${widgetDurationMins} min",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1, 2, 3, 5).forEach { mins ->
                                    val isSelected = widgetDurationMins == mins
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            widgetDurationMins = mins
                                            prefsLoader.setBreathingWidgetDurationMinutes(mins)
                                        },
                                        label = { Text("${mins}m", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // GENERAL
            item {
                Text(
                    text = "GENERAL",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        SettingsNavigationRow(icon = Icons.Outlined.CloudUpload, title = "Backup & Restore", onClick = onBackupRestore)
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        SettingsNavigationRow(icon = Icons.Outlined.Language, title = "Language", subtitle = "Choose your preferred language", onClick = onLanguage)
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        SettingsNavigationRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Notifications & Reminders",
                            subtitle = "Daily reports, doomscroll alerts, wellbeing tips",
                            onClick = onReminders
                        )
                    }
                }
            }

            // PRIVACY & DATA RIGHTS
            item {
                Text(
                    text = "PRIVACY & DATA RIGHTS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Outlined.Shield,
                            title = "Privacy Policy",
                            subtitle = "UK GDPR, zero-telemetry disclosure, and rights",
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.PRIVACY_POLICY_URL)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        SettingsNavigationRow(
                            icon = Icons.Outlined.DeleteSweep,
                            title = "Account & Data Deletion Portal",
                            subtitle = "How to request permanent cloud and account erasure",
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DATA_DELETION_URL)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // HELP & DIAGNOSTICS
            item {
                Text(
                    text = "HELP & DIAGNOSTICS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            title = "Help & FAQ",
                            subtitle = "Frequently asked questions & documentation",
                            onClick = onHelpFAQ
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        SettingsNavigationRow(
                            icon = Icons.Outlined.BugReport,
                            title = "Share Crash Logs",
                            subtitle = "Export and share system diagnostic logs",
                            onClick = onShareCrashLogs
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        SettingsNavigationRow(
                            icon = Icons.Outlined.Terminal,
                            title = "Diagnostics & Logs",
                            subtitle = "View and inspect real-time system logs",
                            onClick = onDiagnostics
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        SettingsNavigationRow(
                            icon = Icons.Outlined.Info,
                            title = "About AmniShield",
                            subtitle = "App version, mission & open source links",
                            onClick = onAbout
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        AmniShieldToggleButton(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WidgetPinRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onPin: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onPin) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Pin Widget",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun pinWidgetToHomeScreen(context: Context, providerClassName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, providerClassName)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(context, "Launcher does not support widget pinning", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Feature requires Android 8.0+", Toast.LENGTH_SHORT).show()
    }
}
