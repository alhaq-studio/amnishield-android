package com.alhaq.amnishield.ui.screens.config

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alhaq.amnishield.data.blockers.AppInterceptionLevel
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.utils.SavedPreferencesLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelBlockerConfigScreen(
    isServiceEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefsLoader = remember { SavedPreferencesLoader(context) }
    val reelConfigSp = remember { context.getSharedPreferences("config_reels", Context.MODE_PRIVATE) }

    var allowDmReels by remember {
        mutableStateOf(reelConfigSp.getBoolean("is_reel_inbox", false))
    }
    var allowFirstFeedReel by remember {
        mutableStateOf(reelConfigSp.getBoolean("is_reel_first", false))
    }

    val rules = remember { prefsLoader.loadUnifiedBlockRules() }
    val alwaysOnReels = remember { prefsLoader.loadAlwaysOnShieldsConfig().reels }
    val activeRules = remember(rules) {
        rules.filter { rule ->
            rule.isActiveOrScheduledForContentNow() &&
                (rule.includeInBrowserShortform ||
                    rule.appTargets.values.any { it == AppInterceptionLevel.BLOCK_SHORTFORM_ONLY })
        }
    }
    val activeShortformCardCount = activeRules.size
    val isAlwaysOnReelsActive = alwaysOnReels.enabled
    val activeBrowserShortsCount = remember(activeRules, alwaysOnReels) {
        activeRules.count { it.includeInBrowserShortform } +
            if (alwaysOnReels.enabled && alwaysOnReels.blockInBrowserShorts) 1 else 0
    }
    val hasAnyActiveReelsProtection = activeShortformCardCount > 0 || isAlwaysOnReelsActive

    fun broadcastReelRefresh() {
        context.sendBroadcast(
            Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER).apply {
                setPackage(context.packageName)
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Short-Form Detection Behavior", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reel_config_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (!isServiceEnabled) {
                item {
                    ServiceRequiredCard(onEnableClick = onEnableServiceClick)
                }
            }

            // Engine Status Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (hasAnyActiveReelsProtection) Icons.Default.CheckCircle else Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Managed by Always-On Shields & Block Cards",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (hasAnyActiveReelsProtection) {
                                        val guardText = if (isAlwaysOnReelsActive) "Always-On Reels Guard + " else ""
                                        "${guardText}$activeShortformCardCount active Block Card(s) guarding short-form feeds" +
                                            if (activeBrowserShortsCount > 0) " (including In-Browser Shorts)." else "."
                                    } else {
                                        "No Always-On Reels Guard or Block Cards are currently targeting Reels or Shorts."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
                        )

                        Text(
                            text = "Choose which apps block Reels/Shorts (or toggle \"Block in-browser short videos\") and set the interception style inside each Block Card.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Feed Exception & Sensitivity Tuning
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Feed Detection Exceptions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Fine-tune how the short-form accessibility detector handles shared links and feed previews.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Allow Reels from Direct Messages",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Let you view a single Reel sent by a friend inside Instagram DM Inbox without triggering a block until you scroll to the next Reel.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = allowDmReels,
                                onCheckedChange = { enabled ->
                                    allowDmReels = enabled
                                    reelConfigSp.edit().putBoolean("is_reel_inbox", enabled).apply()
                                    broadcastReelRefresh()
                                },
                                modifier = Modifier.testTag("allow_dm_reels_switch")
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Allow First Reel in Main Feed",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Avoid false-positive triggers when scrolling past an embedded video in the main home timeline before entering the dedicated Reels player.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = allowFirstFeedReel,
                                onCheckedChange = { enabled ->
                                    allowFirstFeedReel = enabled
                                    reelConfigSp.edit().putBoolean("is_reel_first", enabled).apply()
                                    broadcastReelRefresh()
                                },
                                modifier = Modifier.testTag("allow_first_feed_reel_switch")
                            )
                        }
                    }
                }
            }

            // Supported Platforms Reference Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Supported Short-Form Feeds",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "AmniShield detects view IDs and feed hierarchies for Instagram Reels, YouTube Shorts, TikTok Feed, and In-Browser Short Videos (/shorts/, /reels/, /tiktok/).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
