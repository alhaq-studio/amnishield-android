package com.alhaq.amnishield.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import com.alhaq.amnishield.utils.ReelsStatsManager

enum class StatsDetailPaneType(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    OVERVIEW("Screen Time Trends", "Weekly screen time and focus sessions", Icons.Outlined.BarChart),
    REELS("Reels & Shorts Metrics", "Short video scroll counts & watch time", Icons.Outlined.SmartDisplay),
    APPS("App Launches & Limits", "Most used apps & launch frequency", Icons.Outlined.Apps)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StatsAdaptiveScreen(
    state: AmniShieldState,
    viewModel: AmniShieldViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val navigator = rememberListDetailPaneScaffoldNavigator<StatsDetailPaneType>()
    var selectedPane by remember { mutableStateOf<StatsDetailPaneType?>(StatsDetailPaneType.OVERVIEW) }
    val reelsStatsManager = remember { ReelsStatsManager.getInstance(context) }
    val reelsSummary = remember { reelsStatsManager.getFullMetricsSummary() }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Insights & Analytics", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        items(StatsDetailPaneType.entries) { paneType ->
                            val isSelected = selectedPane == paneType

                            OutlinedCard(
                                onClick = {
                                    selectedPane = paneType
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, paneType)
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            paneType.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            paneType.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            paneType.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val currentPane = selectedPane ?: StatsDetailPaneType.OVERVIEW
                when (currentPane) {
                    StatsDetailPaneType.OVERVIEW -> {
                        StatsScreen(
                            todayScreenTimeMs = (state.focusTimeMinutes * 60 * 1000L),
                            weeklyStats = state.weeklyScreenTime,
                            distractionsBlocked = state.distractionsBlocked,
                            focusTime = "${state.focusTimeMinutes / 60}h ${state.focusTimeMinutes % 60}m",
                            totalReelsWatched = state.totalReelsWatched,
                            averageWatchSeconds = state.averageWatchSeconds,
                            topApps = emptyList(),
                            isAppUsageTrackingEnabled = state.isAppUsageTrackingEnabled,
                            isWebsiteUsageTrackingEnabled = state.isWebsiteUsageTrackingEnabled,
                            onAppClick = {},
                            onRefresh = {},
                            onViewDetailedUsage = {},
                            onViewReelsMetrics = {
                                selectedPane = StatsDetailPaneType.REELS
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, StatsDetailPaneType.REELS)
                            }
                        )
                    }
                    StatsDetailPaneType.REELS -> {
                        ReelsMetricsScreen(
                            summary = reelsSummary,
                            dailyLimit = 50,
                            isBlockerEnabled = state.isReelsBlockerEnabled,
                            onBack = {
                                if (navigator.canNavigateBack()) navigator.navigateBack() else onBack()
                            },
                            onRefresh = {},
                            onConfigureRules = {}
                        )
                    }
                    StatsDetailPaneType.APPS -> {
                        StatsScreen(
                            todayScreenTimeMs = (state.focusTimeMinutes * 60 * 1000L),
                            weeklyStats = state.weeklyScreenTime,
                            distractionsBlocked = state.distractionsBlocked,
                            focusTime = "${state.focusTimeMinutes / 60}h ${state.focusTimeMinutes % 60}m",
                            totalReelsWatched = state.totalReelsWatched,
                            averageWatchSeconds = state.averageWatchSeconds,
                            topApps = emptyList(),
                            isAppUsageTrackingEnabled = state.isAppUsageTrackingEnabled,
                            isWebsiteUsageTrackingEnabled = state.isWebsiteUsageTrackingEnabled,
                            onAppClick = {},
                            onRefresh = {},
                            onViewDetailedUsage = {},
                            onViewReelsMetrics = {}
                        )
                    }
                }
            }
        }
    )
}
