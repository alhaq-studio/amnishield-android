/**
 * ============================================================================
 * AmniShield UI - StatsScreen (Material 3)
 * ============================================================================
 * Architecture: Interactive Statistics Hub with Midnight-Aligned Screen Time,
 * Segmented Range Toggle (Today vs Last 7 Days), and Clickable Weekly Day Bars.
 * ============================================================================
 */
package com.alhaq.amnshield.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnshield.ui.components.bounceClick
import com.alhaq.amnshield.ui.state.ScreenTimeDay
import com.alhaq.amnshield.utils.TimeTools

data class AppUsageItem(
    val name: String,
    val packageName: String,
    val timeFormatted: String,
    val progress: Float,
    val icon: android.graphics.Bitmap? = null
)

enum class OverviewTimeRange { TODAY, LAST_7_DAYS }

@Composable
fun StatsScreen(
    todayScreenTimeMs: Long,
    weeklyStats: List<ScreenTimeDay>,
    distractionsBlocked: Int,
    focusTime: String,
    totalReelsWatched: Int,
    averageWatchSeconds: Int,
    totalReelsWatchTimeFormatted: String = "0m",
    topApps: List<AppUsageItem>,
    isAppUsageTrackingEnabled: Boolean = true,
    totalWebBrowsingTime: String = "0m",
    topWebDomain: String? = null,
    topWebDomainTime: String? = null,
    activeWebDomainsCount: Int = 0,
    isWebsiteUsageTrackingEnabled: Boolean = true,
    onEnableAppUsageTracking: () -> Unit = {},
    onEnableWebsiteUsageTracking: () -> Unit = {},
    onRefresh: () -> Unit,
    onViewDetailedUsage: () -> Unit,
    onViewWebUsageDetails: () -> Unit = {},
    onViewReelsMetrics: () -> Unit,
    onAppClick: (String) -> Unit
) {
    val dailyLimitMs = 2 * 3600 * 1000L // 2 hours daily limit

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
    ) {
        // Interactive Overview Card (Today vs 7 Days + Clickable Weekly Bars)
        item {
            InteractiveOverviewCard(
                todayScreenTimeMs = todayScreenTimeMs,
                weeklyStats = if (weeklyStats.isNotEmpty()) weeklyStats else listOf(
                    ScreenTimeDay("Mon", 45),
                    ScreenTimeDay("Tue", 90),
                    ScreenTimeDay("Wed", 60),
                    ScreenTimeDay("Thu", 130),
                    ScreenTimeDay("Fri", 85),
                    ScreenTimeDay("Sat", 40),
                    ScreenTimeDay("Sun", 15)
                ),
                dailyLimitMs = dailyLimitMs,
                isAppUsageTrackingEnabled = isAppUsageTrackingEnabled,
                onEnableAppUsageTracking = onEnableAppUsageTracking
            )
        }

        item {
            Button(
                onClick = onViewDetailedUsage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Detailed Usage Data",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Daily Activity Summary: Focus Time and Distractions (Side-by-side Cards)
        item {
            Column {
                Text(
                    text = "WELLBEING METRICS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Focus Time Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = focusTime,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Focus Session Time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Distractions Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = distractionsBlocked.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Interceptions Today",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Web Browsing Activity Card
        item {
            Column {
                Text(
                    text = "WEB BROWSING ACTIVITY",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewWebUsageDetails() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isWebsiteUsageTrackingEnabled) totalWebBrowsingTime else "–h –m",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isWebsiteUsageTrackingEnabled) "Web Browsing Today" else "Web Tracking Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isWebsiteUsageTrackingEnabled && !topWebDomain.isNullOrEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$topWebDomain • $topWebDomainTime",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            } else if (!isWebsiteUsageTrackingEnabled) {
                                TextButton(onClick = onEnableWebsiteUsageTracking) {
                                    Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (isWebsiteUsageTrackingEnabled && activeWebDomainsCount > 0) {
                            Spacer(modifier = Modifier.height(14.dp))
                            // Proportional segment progress bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            ) {
                                Box(modifier = Modifier.weight(0.55f).fillMaxHeight().background(Color(0xFF38BDF8)))
                                Spacer(modifier = Modifier.width(2.dp))
                                Box(modifier = Modifier.weight(0.30f).fillMaxHeight().background(Color(0xFFFF7675)))
                                Spacer(modifier = Modifier.width(2.dp))
                                Box(modifier = Modifier.weight(0.15f).fillMaxHeight().background(Color(0xFF34D399)))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$activeWebDomainsCount domains measured",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "View Breakdown →",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Short-form Video Tracker Card
        item {
            Column {
                Text(
                    text = "SHORT-FORM CONTENT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewReelsMetrics() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "$totalReelsWatched Reels Scrolled",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Total Watch Time: $totalReelsWatchTimeFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${averageWatchSeconds}s avg",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Hourly Reels Heatmap Distribution Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val hoursDistribution = listOf(
                                "Night" to 0.1f,
                                "Morning" to 0.45f,
                                "Afternoon" to 0.85f,
                                "Evening" to 0.3f
                            )
                            hoursDistribution.forEach { (period, weight) ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((20 * weight).dp.coerceAtLeast(4.dp))
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                if (weight > 0.6f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = period.take(3),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top Apps Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MOST USED TODAY",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Top 5",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (topApps.isEmpty() && isAppUsageTrackingEnabled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No app usage detected yet today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(topApps) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app.packageName) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.asImageBitmap(),
                                    contentDescription = app.name,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = getIconForApp(app.name),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = app.timeFormatted,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { app.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (app.progress >= 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Overview Card with Segmented Toggle (Today vs Last 7 Days)
 * and Clickable Weekly Day Bars.
 */
@Composable
fun InteractiveOverviewCard(
    todayScreenTimeMs: Long,
    weeklyStats: List<ScreenTimeDay>,
    dailyLimitMs: Long = 2 * 3600 * 1000L,
    isAppUsageTrackingEnabled: Boolean = true,
    onEnableAppUsageTracking: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedRange by remember { mutableStateOf(OverviewTimeRange.TODAY) }
    var selectedDayIndex by remember { mutableIntStateOf(weeklyStats.lastIndex.coerceAtLeast(0)) }

    // Keep selectedDayIndex in bounds if weeklyStats size changes
    LaunchedEffect(weeklyStats) {
        if (selectedDayIndex !in weeklyStats.indices) {
            selectedDayIndex = weeklyStats.lastIndex.coerceAtLeast(0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!isAppUsageTrackingEnabled) Modifier.blur(16.dp) else Modifier),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Title + Segmented Control Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedRange == OverviewTimeRange.TODAY) "TODAY'S OVERVIEW" else "WEEKLY OVERVIEW",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )

                    OverviewSegmentedToggle(
                        selectedRange = selectedRange,
                        onRangeSelected = { selectedRange = it }
                    )
                }

                // Main Metric Display
                val isInspectingHistoricalDay = selectedRange == OverviewTimeRange.TODAY &&
                        selectedDayIndex != weeklyStats.lastIndex &&
                        selectedDayIndex in weeklyStats.indices

                val displayTimeMs = when {
                    selectedRange == OverviewTimeRange.LAST_7_DAYS -> {
                        if (weeklyStats.isNotEmpty()) {
                            (weeklyStats.map { it.minutes * 60_000L }.average()).toLong()
                        } else 0L
                    }
                    isInspectingHistoricalDay -> {
                        weeklyStats[selectedDayIndex].minutes * 60_000L
                    }
                    else -> todayScreenTimeMs
                }

                val subtitleText = when {
                    selectedRange == OverviewTimeRange.LAST_7_DAYS -> "Daily Average (Last 7 Days)"
                    isInspectingHistoricalDay -> "Screen Time on ${weeklyStats[selectedDayIndex].dayOfWeek}"
                    else -> "Screen Time Today"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isAppUsageTrackingEnabled) TimeTools.formatHoursMinutes(displayTimeMs) else "–h –m",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Daily Limit Progress Bar
                val progress = if (dailyLimitMs > 0) (displayTimeMs.toFloat() / dailyLimitMs.toFloat()).coerceIn(0f, 1f) else 0f
                val progressPctText = "${(progress * 100).toInt()}%"

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedRange == OverviewTimeRange.TODAY) "Daily Limit Progress" else "Average Budget Progress",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isAppUsageTrackingEnabled) {
                                "${TimeTools.formatHoursMinutes(displayTimeMs)} / ${TimeTools.formatHoursMinutes(dailyLimitMs)} ($progressPctText)"
                            } else "Tracking Paused",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { if (isAppUsageTrackingEnabled) progress else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }

                // Clickable Weekly Bar Chart
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WEEKLY SCREEN TIME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        if (isInspectingHistoricalDay) {
                            Text(
                                text = "Tap today to reset",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { selectedDayIndex = weeklyStats.lastIndex }
                            )
                        }
                    }

                    InteractiveWeeklyBarChart(
                        weeklyStats = weeklyStats,
                        selectedIndex = selectedDayIndex,
                        onDayClick = { index ->
                            selectedDayIndex = index
                            if (selectedRange == OverviewTimeRange.LAST_7_DAYS) {
                                selectedRange = OverviewTimeRange.TODAY
                            }
                        }
                    )
                }
            }
        }

        // Privacy Overlay when App Usage Tracking is Disabled
        if (!isAppUsageTrackingEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
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
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "App Usage Tracking Paused",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Global app screen time logging is currently disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onEnableAppUsageTracking,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Enable Usage Tracking")
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewSegmentedToggle(
    selectedRange: OverviewTimeRange,
    onRangeSelected: (OverviewTimeRange) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OverviewSegmentButton(
                label = "Today",
                isSelected = selectedRange == OverviewTimeRange.TODAY,
                onClick = { onRangeSelected(OverviewTimeRange.TODAY) }
            )
            OverviewSegmentButton(
                label = "Last 7 Days",
                isSelected = selectedRange == OverviewTimeRange.LAST_7_DAYS,
                onClick = { onRangeSelected(OverviewTimeRange.LAST_7_DAYS) }
            )
        }
    }
}

@Composable
private fun OverviewSegmentButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "segment_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "segment_text"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun InteractiveWeeklyBarChart(
    weeklyStats: List<ScreenTimeDay>,
    selectedIndex: Int,
    onDayClick: (Int) -> Unit
) {
    val maxMins = weeklyStats.maxOfOrNull { it.minutes }?.coerceAtLeast(60) ?: 100

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        weeklyStats.forEachIndexed { index, day ->
            val isSelected = selectedIndex == index
            val barRatio = (day.minutes.toFloat() / maxMins.toFloat()).coerceIn(0.08f, 1f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .weight(1f)
                    .bounceClick { onDayClick(index) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .width(if (isSelected) 14.dp else 10.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(barRatio)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                    Text(
                        text = day.dayOfWeek.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun getIconForApp(appName: String): ImageVector {
    val nameLower = appName.lowercase()
    return when {
        nameLower.contains("game") || nameLower.contains("call of duty") || nameLower.contains("roblox") -> Icons.Default.Gamepad
        nameLower.contains("google") || nameLower.contains("chrome") || nameLower.contains("search") -> Icons.Default.Search
        nameLower.contains("youtube") || nameLower.contains("video") || nameLower.contains("netflix") -> Icons.Default.PlayArrow
        nameLower.contains("instagram") || nameLower.contains("camera") || nameLower.contains("tiktok") -> Icons.Default.CameraAlt
        else -> Icons.Default.Android
    }
}
