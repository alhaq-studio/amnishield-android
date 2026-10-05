package com.alhaq.amnishield.ui.screens.config

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.ui.components.bounceClick
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.utils.ScreenTimeCalculator
import com.alhaq.amnishield.utils.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "UsageTrackerConfigScreen"

/**
 * Dedicated Jetpack Compose configuration screen for Usage Tracker.
 * 
 * Features two distinct, modular control systems:
 * 1. Reels & Shorts Doom-Scrolling Tracker & Floating Counter Overlay
 * 2. Global App Usage Data Tracking Switch with instant real-time UI blurring
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageTrackerConfigScreen(
    isServiceEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onBack: () -> Unit,
    onSelectOverlayAppsClick: () -> Unit,
    onConfigureTweaksClick: () -> Unit,
    onViewReelsMetricsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }

    var isReelsTrackingEnabled by remember { mutableStateOf(loader.isReelsTrackingEnabled()) }
    var isReelsOverlayEnabled by remember { mutableStateOf(loader.isReelsOverlayCounterEnabled()) }
    var overlayMode by remember { mutableStateOf(loader.getOverlayCounterDisplayMode()) }
    val targetAppsCount = remember { loader.getReelsOverlayApps().size }

    var isAppUsageTrackingEnabled by remember { mutableStateOf(loader.isAppUsageTrackingEnabled()) }
    var isWebsiteUsageTrackingEnabled by remember { mutableStateOf(loader.isWebsiteUsageTrackingEnabled()) }
    var isAmniSpaceFrictionEnabled by remember { mutableStateOf(loader.isAmniSpaceUsageLimitFrictionEnabled()) }

    var todayScreenTimeMillis by remember { mutableLongStateOf(0L) }
    var topAppsSummaryText by remember { mutableStateOf("Loading usage data...") }

    LaunchedEffect(isAppUsageTrackingEnabled) {
        if (!isAppUsageTrackingEnabled) {
            topAppsSummaryText = "App usage tracking paused"
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val screenTime = ScreenTimeCalculator.getTodayScreenTime(context)
                val startTime = ScreenTimeCalculator.getStartOfDayMillis(0)
                val endTime = System.currentTimeMillis()
                val helper = UsageStatsHelper(context)
                val statsList = helper.getForegroundStatsByTimestamps(startTime, endTime)
                val systemPackages = setOf(
                    "android", "com.android.systemui", "com.android.settings",
                    "com.google.android.gms", context.packageName
                )
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
                val launcherPackages = context.packageManager.queryIntentActivities(launcherIntent, 0)
                    .map { it.activityInfo.packageName }.toSet()

                val filtered = statsList.filter { stat ->
                    !systemPackages.contains(stat.packageName) &&
                    !launcherPackages.contains(stat.packageName) &&
                    stat.packageName.isNotBlank() &&
                    context.packageManager.getLaunchIntentForPackage(stat.packageName) != null
                }.sortedByDescending { it.totalTime }.take(3)

                val summary = if (filtered.isNotEmpty()) {
                    filtered.joinToString("\n") { stat ->
                        val label = try {
                            val info = context.packageManager.getApplicationInfo(stat.packageName, 0)
                            context.packageManager.getApplicationLabel(info).toString()
                        } catch (_: Exception) {
                            stat.packageName
                        }
                        val mins = stat.totalTime / (1000 * 60)
                        val timeStr = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
                        "• $label: $timeStr"
                    }
                } else {
                    "No app usage recorded today"
                }

                withContext(Dispatchers.Main) {
                    todayScreenTimeMillis = screenTime
                    topAppsSummaryText = summary
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    topAppsSummaryText = "No app usage recorded today"
                }
            }
        }
    }

    val domainStats = remember(isWebsiteUsageTrackingEnabled) { loader.loadWebsiteUsageStats() }
    val totalWebMillis = domainStats.values.sum()
    val totalWebHours = totalWebMillis / (1000 * 60 * 60)
    val totalWebMins = (totalWebMillis % (1000 * 60 * 60)) / (1000 * 60)
    val webTimeText = if (totalWebHours > 0) "${totalWebHours}h ${totalWebMins}m" else "${totalWebMins}m"
    val webProgress = if (totalWebMillis > 0) (totalWebMillis.toFloat() / (2 * 60 * 60 * 1000f)).coerceIn(0.05f, 1f) else 0f
    val topWebsitesText = remember(domainStats) {
        val topEntries = domainStats.entries.sortedByDescending { it.value }.take(3)
        if (topEntries.isNotEmpty()) {
            topEntries.joinToString("\n") { entry ->
                val mins = entry.value / (1000 * 60)
                val timeStr = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
                "• ${entry.key}: $timeStr"
            }
        } else {
            "No website activity logged today"
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = "Usage Tracker Settings",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Configure live overlay & usage tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Accessibility Service Banner if disabled
            if (!isServiceEnabled) {
                ServiceRequiredCard(onEnableClick = onEnableServiceClick)
            }

            // =========================================================================
            // 1. TOGGLE 1: REELS & SHORTS DOOM-SCROLLING TRACKING & OVERLAY
            // =========================================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reels / Shorts Doom-Scrolling Tracker",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isReelsTrackingEnabled) "Tracking scrolls & short-form watch time" else "Short-form video tracking disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isReelsTrackingEnabled,
                            onCheckedChange = { checked ->
                                Log.i(TAG, "Reels Tracking toggle changed: $checked")
                                isReelsTrackingEnabled = checked
                                loader.setReelsTrackingEnabled(checked)
                            }
                        )
                    }

                    // Expanded controls when Reels Tracking is enabled
                    AnimatedVisibility(
                        visible = isReelsTrackingEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            // Sub-toggle: Floating Counter Overlay
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPicture,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Floating Doom-Scrolling Overlay",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Displays live scroll count badge over selected apps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isReelsOverlayEnabled,
                                    onCheckedChange = { checked ->
                                        isReelsOverlayEnabled = checked
                                        loader.setReelsOverlayCounterEnabled(checked)
                                    }
                                )
                            }

                            if (isReelsOverlayEnabled) {
                                // Overlay Mode Selector Chips
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    Text(
                                        text = "COUNTER BADGE FORMAT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = overlayMode == SavedPreferencesLoader.OVERLAY_MODE_BOTH,
                                            onClick = {
                                                overlayMode = SavedPreferencesLoader.OVERLAY_MODE_BOTH
                                                loader.setOverlayCounterDisplayMode(overlayMode)
                                            },
                                            label = { Text("Count + Time", fontSize = 12.sp) }
                                        )
                                        FilterChip(
                                            selected = overlayMode == SavedPreferencesLoader.OVERLAY_MODE_COUNT,
                                            onClick = {
                                                overlayMode = SavedPreferencesLoader.OVERLAY_MODE_COUNT
                                                loader.setOverlayCounterDisplayMode(overlayMode)
                                            },
                                            label = { Text("Count Only", fontSize = 12.sp) }
                                        )
                                        FilterChip(
                                            selected = overlayMode == SavedPreferencesLoader.OVERLAY_MODE_TIME,
                                            onClick = {
                                                overlayMode = SavedPreferencesLoader.OVERLAY_MODE_TIME
                                                loader.setOverlayCounterDisplayMode(overlayMode)
                                            },
                                            label = { Text("Time Only", fontSize = 12.sp) }
                                        )
                                    }
                                }

                                // Target Apps Button
                                OutlinedButton(
                                    onClick = onSelectOverlayAppsClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bounceClick(),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Overlay Target Apps ($targetAppsCount selected)", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Link to Live Reels Metrics Screen
                            OutlinedCard(
                                onClick = onViewReelsMetricsClick,
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoGraph,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "View Live Reels Metrics",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "See velocity, historical logs, and doom-scroll trends",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 2. TOGGLE 2: GLOBAL APP USAGE DATA TRACKING & DYNAMIC BLURRING
            // =========================================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueryStats,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Global App Usage Data Tracking",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAppUsageTrackingEnabled) "Logging screen time and daily app launches" else "Usage tracking paused — stats blurred",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAppUsageTrackingEnabled,
                            onCheckedChange = { checked ->
                                Log.i(TAG, "Global App Usage Tracking toggle changed: $checked")
                                isAppUsageTrackingEnabled = checked
                                loader.setAppUsageTrackingEnabled(checked)
                                loader.setUsageTrackerFeatureEnabled(checked)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Interactive Preview of Stats with Real-Time Blur Effect
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .then(if (!isAppUsageTrackingEnabled) Modifier.blur(14.dp) else Modifier)
                        ) {
                            val screenTimeHours = todayScreenTimeMillis / (1000 * 60 * 60)
                            val screenTimeMins = (todayScreenTimeMillis % (1000 * 60 * 60)) / (1000 * 60)
                            val screenTimeText = if (screenTimeHours > 0) "${screenTimeHours}h ${screenTimeMins}m" else "${screenTimeMins}m"
                            val screenTimeProgress = if (todayScreenTimeMillis > 0) (todayScreenTimeMillis.toFloat() / (4 * 60 * 60 * 1000f)).coerceIn(0.05f, 1f) else 0f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TODAY'S SCREEN TIME",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = screenTimeText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { screenTimeProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = topAppsSummaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Frosted Glass Privacy Overlay when Usage Tracking is Disabled
                        if (!isAppUsageTrackingEnabled) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "App Usage Tracking Paused",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Usage stats and charts are blurred for privacy",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 3. TOGGLE 3: WEBSITE BROWSING USAGE TRACKING & PRIVACY
            // =========================================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Website Browsing Usage Tracking",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isWebsiteUsageTrackingEnabled) "Logging domain screen time 100% on-device" else "Website tracking paused — domain stats blurred",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isWebsiteUsageTrackingEnabled,
                            onCheckedChange = { checked ->
                                Log.i(TAG, "Website Usage Tracking toggle changed: $checked")
                                isWebsiteUsageTrackingEnabled = checked
                                loader.setWebsiteUsageTrackingEnabled(checked)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Interactive Preview of Domain Stats with Real-Time Blur Effect
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .then(if (!isWebsiteUsageTrackingEnabled) Modifier.blur(14.dp) else Modifier)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TODAY'S WEB BROWSING",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = webTimeText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { webProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = topWebsitesText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Frosted Glass Privacy Overlay when Website Tracking is Disabled
                        if (!isWebsiteUsageTrackingEnabled) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Website Usage Tracking Paused",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Web domain metrics are paused and blurred for privacy",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Privacy Guarantee Notice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "100% On-Device: Web domains are measured locally. Search queries, form data, and URLs are never saved or sent to any server.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // =========================================================================
            // 4. AMNISPACE MINDFUL LIMITS FRICTION
            // =========================================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AmniSpace Daily Limit Friction",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAmniSpaceFrictionEnabled) "Presents 3 mindful breaths & screen time reflection when quota is reached" else "Immediate hard lockout upon reaching limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAmniSpaceFrictionEnabled,
                            onCheckedChange = { checked ->
                                isAmniSpaceFrictionEnabled = checked
                                loader.setAmniSpaceUsageLimitFrictionEnabled(checked)
                            }
                        )
                    }
                }
            }

            // =========================================================================
            // 5. TRACKER DISPLAY & BEHAVIORAL TWEAKS
            // =========================================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick()
                    .clickable { onConfigureTweaksClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Badge Position & Display Tweaks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure overlay position, interval and timeout options",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Configure",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Open Source Credits Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open Source Attribution",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Doom-scrolling detection & overlay mechanics inspired by Curbox by Nethical (GPL-3.0-or-later).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
