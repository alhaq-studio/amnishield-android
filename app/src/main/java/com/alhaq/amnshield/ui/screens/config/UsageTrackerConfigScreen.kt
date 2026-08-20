package com.alhaq.amnshield.ui.screens.config

import android.content.Context
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
import com.alhaq.amnshield.utils.SavedPreferencesLoader

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
    onViewReelsMetricsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }

    // Dual core toggles
    var isReelsTrackingEnabled by remember { mutableStateOf(loader.isReelsTrackingEnabled(true)) }
    var isReelsOverlayEnabled by remember { mutableStateOf(loader.isReelsOverlayCounterEnabled(true)) }
    var isAppUsageTrackingEnabled by remember { mutableStateOf(loader.isAppUsageTrackingEnabled(true)) }
    var overlayMode by remember { mutableIntStateOf(loader.getOverlayCounterDisplayMode()) }
    var targetAppsCount by remember { mutableIntStateOf(loader.getReelsOverlayApps().size) }

    Log.d(TAG, "Rendering UsageTrackerConfigScreen: reelsTracking=$isReelsTrackingEnabled, appUsageTracking=$isAppUsageTrackingEnabled")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Usage Tracker Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
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
                    containerColor = MaterialTheme.colorScheme.surface
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
                                        fontWeight = FontWeight.SemiBold
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
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Overlay Target Apps ($targetAppsCount selected)")
                                }
                            }

                            // Link to Live Reels Metrics Screen
                            OutlinedCard(
                                onClick = onViewReelsMetricsClick,
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                                    text = "3h 42m",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { 0.65f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "• YouTube: 1h 15m\n• Instagram: 48m\n• Chrome: 35m",
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
            // 3. TRACKER DISPLAY & BEHAVIORAL TWEAKS
            // =========================================================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfigureTweaksClick() }
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
                            fontWeight = FontWeight.SemiBold
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
