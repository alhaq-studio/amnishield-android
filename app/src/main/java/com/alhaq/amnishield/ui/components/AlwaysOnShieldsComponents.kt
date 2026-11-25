package com.alhaq.amnishield.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alhaq.amnishield.data.blockers.*
import com.alhaq.amnishield.security.AuthResolver
import kotlin.math.roundToInt

/**
 * Pinned compact status banner shown at the top of Tab 1 (Custom Rules).
 * Strictly a visibility indicator + quick jump to Tab 2 (no duplicate switches).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlwaysOnShieldsStatusBanner(
    config: AlwaysOnShieldsConfig,
    onOpenAlwaysOnTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeShields = remember(config) { config.activeShieldTypes() }

    if (activeShields.isEmpty()) {
        Surface(
            onClick = onOpenAlwaysOnTab,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = modifier
                .fillMaxWidth()
                .testTag("always_on_status_banner_off")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Always-On Shields - Off (Tap to configure)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Always-On Shields",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    } else {
        Surface(
            onClick = onOpenAlwaysOnTab,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = modifier
                .fillMaxWidth()
                .testTag("always_on_status_banner_active")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Always-On Shields - ${activeShields.size} Active",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeShields.forEach { shield ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = BorderStroke(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = shield.pillLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Configure Always-On Shields",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private data class PendingShieldUnlock(
    val shieldType: ShieldType,
    val lockConfig: ShieldLockConfig,
    val actionDescription: String,
    val onApproved: () -> Unit
)

/**
 * Full Tab 2 (Always-On Shields) container rendering the 3 expandable accordion dropdown cards,
 * enforcing strict Asymmetric Friction on weakening actions, and hosting the in-screen Compose sheets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlwaysOnShieldsTabContent(
    config: AlwaysOnShieldsConfig,
    reelsScrolledToday: Int,
    isPremiumUser: Boolean,
    hasGlobalPinOverride: Boolean? = null,
    onUpdateConfig: (AlwaysOnShieldsConfig) -> Unit,
    onEnsureNotificationPermission: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authResolver = remember { AuthResolver.getInstance(context) }
    var liveHasGlobalPin by remember { mutableStateOf(authResolver.hasGlobalPin()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                liveHasGlobalPin = authResolver.hasGlobalPin()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasGlobalPin = hasGlobalPinOverride ?: liveHasGlobalPin

    var expandedShield by remember { mutableStateOf<ShieldType?>(null) }
    var pendingUnlock by remember { mutableStateOf<PendingShieldUnlock?>(null) }
    var lockSelectorForShield by remember { mutableStateOf<ShieldType?>(null) }

    var showManageDomainsSheet by remember { mutableStateOf(false) }
    var showManageKeywordsSheet by remember { mutableStateOf(false) }
    var showIgnoredAppsSheet by remember { mutableStateOf(false) }

    // Asymmetric Friction gate: strengthening executes immediately; weakening runs effective unlock challenge.
    fun gateWeakening(
        shieldType: ShieldType,
        isCurrentlyEnabled: Boolean,
        lockConfig: ShieldLockConfig,
        actionDescription: String,
        mutation: () -> Unit
    ) {
        if (!isCurrentlyEnabled || !lockConfig.isActivelyLocked(hasGlobalPin)) {
            mutation()
        } else {
            pendingUnlock = PendingShieldUnlock(
                shieldType = shieldType,
                lockConfig = lockConfig,
                actionDescription = actionDescription,
                onApproved = mutation
            )
        }
    }

    pendingUnlock?.let { req ->
        ShieldUnlockChallengeDialog(
            shieldType = req.shieldType,
            lockConfig = req.lockConfig,
            actionDescription = req.actionDescription,
            onDismiss = { pendingUnlock = null },
            onUnlocked = {
                val action = req.onApproved
                pendingUnlock = null
                action()
            }
        )
    }

    lockSelectorForShield?.let { targetShield ->
        val currentLock = config.lockConfigFor(targetShield)
        val isShieldOn = when (targetShield) {
            ShieldType.REELS -> config.reels.enabled
            ShieldType.WEBSITE -> config.website.enabled
            ShieldType.KEYWORD -> config.keyword.enabled
        }
        ShieldLockSelectorDialog(
            shieldType = targetShield,
            currentConfig = currentLock,
            isPremiumUser = isPremiumUser,
            hasGlobalPin = hasGlobalPin,
            onDismiss = { lockSelectorForShield = null },
            onNavigateToPremium = onNavigateToPremium,
            onSaveLockConfig = { newLock ->
                lockSelectorForShield = null
                liveHasGlobalPin = authResolver.hasGlobalPin()
                val isShorteningPauseDelay = currentLock.mode == ShieldLockMode.PAUSE_DELAY &&
                    newLock.mode == ShieldLockMode.PAUSE_DELAY &&
                    newLock.delaySeconds < currentLock.delaySeconds
                val isDowngradingOrEditingActiveLock = isShieldOn &&
                    currentLock.isActivelyLocked(hasGlobalPin) &&
                    (newLock.strengthRank(hasGlobalPin) < currentLock.strengthRank(hasGlobalPin) ||
                        newLock.mode != currentLock.mode ||
                        isShorteningPauseDelay)
                val applyLock = {
                    val updated = when (targetShield) {
                        ShieldType.REELS -> config.copy(reels = config.reels.copy(lockConfig = newLock))
                        ShieldType.WEBSITE -> config.copy(website = config.website.copy(lockConfig = newLock))
                        ShieldType.KEYWORD -> config.copy(keyword = config.keyword.copy(lockConfig = newLock))
                    }
                    onUpdateConfig(updated)
                }
                if (isDowngradingOrEditingActiveLock) {
                    gateWeakening(
                        shieldType = targetShield,
                        isCurrentlyEnabled = true,
                        lockConfig = currentLock,
                        actionDescription = "Verify to change unlock protection for ${targetShield.pillLabel}."
                    ) { applyLock() }
                } else {
                    applyLock()
                }
            }
        )
    }

    if (showManageDomainsSheet) {
        ManageShieldDomainsSheet(
            config = config.website,
            onDismiss = { showManageDomainsSheet = false },
            onAddDomain = { domain ->
                val updatedList = (config.website.customDomains + domain).distinct()
                onUpdateConfig(config.copy(website = config.website.copy(customDomains = updatedList)))
            },
            onRemoveDomain = { domain ->
                gateWeakening(
                    shieldType = ShieldType.WEBSITE,
                    isCurrentlyEnabled = config.website.enabled,
                    lockConfig = config.website.lockConfig,
                    actionDescription = "Verify to remove \"$domain\" from Permanent Website Guard."
                ) {
                    val updatedList = config.website.customDomains.filterNot { it == domain }
                    onUpdateConfig(config.copy(website = config.website.copy(customDomains = updatedList)))
                }
            },
            onTogglePack = { packId, checked ->
                if (checked) {
                    onUpdateConfig(
                        config.copy(
                            website = config.website.copy(enabledPackIds = config.website.enabledPackIds + packId)
                        )
                    )
                } else {
                    gateWeakening(
                        shieldType = ShieldType.WEBSITE,
                        isCurrentlyEnabled = config.website.enabled,
                        lockConfig = config.website.lockConfig,
                        actionDescription = "Verify to disable a website blocklist pack."
                    ) {
                        onUpdateConfig(
                            config.copy(
                                website = config.website.copy(enabledPackIds = config.website.enabledPackIds - packId)
                            )
                        )
                    }
                }
            }
        )
    }

    if (showManageKeywordsSheet) {
        ManageShieldKeywordsSheet(
            config = config.keyword,
            onDismiss = { showManageKeywordsSheet = false },
            onAddKeyword = { keyword ->
                val updatedList = (config.keyword.customKeywords + keyword).distinct()
                onUpdateConfig(config.copy(keyword = config.keyword.copy(customKeywords = updatedList)))
            },
            onRemoveKeyword = { keyword ->
                gateWeakening(
                    shieldType = ShieldType.KEYWORD,
                    isCurrentlyEnabled = config.keyword.enabled,
                    lockConfig = config.keyword.lockConfig,
                    actionDescription = "Verify to remove \"$keyword\" from Keyword & Search Filter."
                ) {
                    val updatedList = config.keyword.customKeywords.filterNot { it == keyword }
                    onUpdateConfig(config.copy(keyword = config.keyword.copy(customKeywords = updatedList)))
                }
            },
            onTogglePack = { packId, checked ->
                if (checked) {
                    onUpdateConfig(
                        config.copy(
                            keyword = config.keyword.copy(enabledPackIds = config.keyword.enabledPackIds + packId)
                        )
                    )
                } else {
                    gateWeakening(
                        shieldType = ShieldType.KEYWORD,
                        isCurrentlyEnabled = config.keyword.enabled,
                        lockConfig = config.keyword.lockConfig,
                        actionDescription = "Verify to disable a keyword filter pack."
                    ) {
                        onUpdateConfig(
                            config.copy(
                                keyword = config.keyword.copy(enabledPackIds = config.keyword.enabledPackIds - packId)
                            )
                        )
                    }
                }
            }
        )
    }

    if (showIgnoredAppsSheet) {
        ShieldIgnoredAppsSheet(
            ignoredPackages = config.keyword.ignoredPackages,
            onDismiss = { showIgnoredAppsSheet = false },
            onToggleIgnoredApp = { pkg, nowIgnored ->
                if (nowIgnored) {
                    // Adding an app to the ignore whitelist weakens protection
                    gateWeakening(
                        shieldType = ShieldType.KEYWORD,
                        isCurrentlyEnabled = config.keyword.enabled,
                        lockConfig = config.keyword.lockConfig,
                        actionDescription = "Verify to whitelist $pkg from Keyword Filter."
                    ) {
                        onUpdateConfig(
                            config.copy(
                                keyword = config.keyword.copy(ignoredPackages = config.keyword.ignoredPackages + pkg)
                            )
                        )
                    }
                } else {
                    onUpdateConfig(
                        config.copy(
                            keyword = config.keyword.copy(ignoredPackages = config.keyword.ignoredPackages - pkg)
                        )
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Background 24/7 protections that enforce independently of your Custom Rules.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ==================== CARD 1: Short Video & Reels Guard ====================
        AlwaysOnShieldAccordionCard(
            shieldType = ShieldType.REELS,
            icon = Icons.Outlined.SmartDisplay,
            title = ShieldType.REELS.displayName,
            summarySubtitle = config.reels.summarySubtitle(),
            lockBadgeLabel = if (config.reels.enabled) config.reels.lockConfig.activeBadgeLabel(hasGlobalPin) else null,
            enabled = config.reels.enabled,
            expanded = expandedShield == ShieldType.REELS,
            onToggleExpand = {
                expandedShield = if (expandedShield == ShieldType.REELS) null else ShieldType.REELS
            },
            onMasterToggle = { turnOn ->
                if (turnOn) {
                    onEnsureNotificationPermission()
                    onUpdateConfig(config.copy(reels = config.reels.copy(enabled = true)))
                } else {
                    gateWeakening(
                        shieldType = ShieldType.REELS,
                        isCurrentlyEnabled = true,
                        lockConfig = config.reels.lockConfig,
                        actionDescription = "Verify to disable Short Video & Reels Guard."
                    ) {
                        onUpdateConfig(config.copy(reels = config.reels.copy(enabled = false)))
                    }
                }
            }
        ) {
            val reels = config.reels
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Platforms",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                ShieldSwitchRow(
                    title = "YouTube Shorts",
                    checked = reels.blockYoutubeShorts,
                    testTag = "shield_reels_youtube_switch"
                ) { checked ->
                    if (checked) {
                        onUpdateConfig(config.copy(reels = reels.copy(blockYoutubeShorts = true)))
                    } else {
                        gateWeakening(ShieldType.REELS, reels.enabled, reels.lockConfig, "Verify to allow YouTube Shorts.") {
                            onUpdateConfig(config.copy(reels = reels.copy(blockYoutubeShorts = false)))
                        }
                    }
                }

                ShieldSwitchRow(
                    title = "Instagram Reels",
                    checked = reels.blockInstagramReels,
                    testTag = "shield_reels_instagram_switch"
                ) { checked ->
                    if (checked) {
                        onUpdateConfig(config.copy(reels = reels.copy(blockInstagramReels = true)))
                    } else {
                        gateWeakening(ShieldType.REELS, reels.enabled, reels.lockConfig, "Verify to allow Instagram Reels.") {
                            onUpdateConfig(config.copy(reels = reels.copy(blockInstagramReels = false)))
                        }
                    }
                }

                ShieldSwitchRow(
                    title = "TikTok Feed",
                    checked = reels.blockTikTok,
                    testTag = "shield_reels_tiktok_switch"
                ) { checked ->
                    if (checked) {
                        onUpdateConfig(config.copy(reels = reels.copy(blockTikTok = true)))
                    } else {
                        gateWeakening(ShieldType.REELS, reels.enabled, reels.lockConfig, "Verify to allow TikTok.") {
                            onUpdateConfig(config.copy(reels = reels.copy(blockTikTok = false)))
                        }
                    }
                }

                ShieldSwitchRow(
                    title = "In-Browser Short Videos",
                    subtitle = "Detects /shorts/, /reels/, and /tiktok/ URLs in mobile browsers",
                    checked = reels.blockInBrowserShorts,
                    testTag = "shield_reels_browser_switch"
                ) { checked ->
                    if (checked) {
                        onUpdateConfig(config.copy(reels = reels.copy(blockInBrowserShorts = true)))
                    } else {
                        gateWeakening(ShieldType.REELS, reels.enabled, reels.lockConfig, "Verify to disable in-browser short video blocking.") {
                            onUpdateConfig(config.copy(reels = reels.copy(blockInBrowserShorts = false)))
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Daily Limit Mode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = reels.limitMode == ReelsLimitMode.ALWAYS_BLOCK_24_7,
                        onClick = {
                            // Tightening to 24/7 is always immediate
                            onUpdateConfig(config.copy(reels = reels.copy(limitMode = ReelsLimitMode.ALWAYS_BLOCK_24_7)))
                        },
                        label = { Text("Always Block 24/7") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = reels.limitMode == ReelsLimitMode.LIMIT_BY_COUNT,
                        onClick = {
                            if (reels.limitMode == ReelsLimitMode.ALWAYS_BLOCK_24_7) {
                                gateWeakening(
                                    ShieldType.REELS,
                                    reels.enabled,
                                    reels.lockConfig,
                                    "Verify to switch from 24/7 block to daily reel count limit."
                                ) {
                                    onUpdateConfig(config.copy(reels = reels.copy(limitMode = ReelsLimitMode.LIMIT_BY_COUNT)))
                                }
                            }
                        },
                        label = { Text("Limit by Reels") },
                        modifier = Modifier.weight(1f)
                    )
                }

                AnimatedVisibility(visible = reels.limitMode == ReelsLimitMode.LIMIT_BY_COUNT) {
                    var draftSlider by remember(reels.dailyReelLimit) { mutableFloatStateOf(reels.dailyReelLimit.toFloat()) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Allowance: ${draftSlider.roundToInt()} reels",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Today: $reelsScrolledToday/${reels.dailyReelLimit}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = draftSlider,
                            onValueChange = { draftSlider = it },
                            onValueChangeFinished = {
                                val nextLimit = draftSlider.roundToInt().coerceIn(5, 100)
                                if (nextLimit <= reels.dailyReelLimit) {
                                    onUpdateConfig(config.copy(reels = reels.copy(dailyReelLimit = nextLimit)))
                                } else {
                                    gateWeakening(
                                        ShieldType.REELS,
                                        reels.enabled,
                                        reels.lockConfig,
                                        "Verify to increase daily reel limit to $nextLimit."
                                    ) {
                                        onUpdateConfig(config.copy(reels = reels.copy(dailyReelLimit = nextLimit)))
                                    }
                                    draftSlider = reels.dailyReelLimit.toFloat()
                                }
                            },
                            valueRange = 5f..100f
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 25, 50, 100).forEach { preset ->
                                FilterChip(
                                    selected = reels.dailyReelLimit == preset,
                                    onClick = {
                                        if (preset <= reels.dailyReelLimit) {
                                            onUpdateConfig(config.copy(reels = reels.copy(dailyReelLimit = preset)))
                                        } else {
                                            gateWeakening(
                                                ShieldType.REELS,
                                                reels.enabled,
                                                reels.lockConfig,
                                                "Verify to increase daily reel limit to $preset."
                                            ) {
                                                onUpdateConfig(config.copy(reels = reels.copy(dailyReelLimit = preset)))
                                            }
                                        }
                                    },
                                    label = { Text("$preset") }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Interception Style",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val styles = listOf(
                        ShortformInterceptionStyle.REDIRECT_TO_FEED to "Redirect to Home Feed",
                        ShortformInterceptionStyle.EXIT_TO_HOME to "Exit to Android Home",
                        ShortformInterceptionStyle.WARNING_SCREEN to "Warning Screen",
                        ShortformInterceptionStyle.MINDFUL_BREATHING_PAUSE to "AmniSpace Breathing"
                    )
                    styles.forEach { (style, label) ->
                        FilterChip(
                            selected = reels.interceptionStyle == style,
                            onClick = {
                                onUpdateConfig(config.copy(reels = reels.copy(interceptionStyle = style)))
                            },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider()

                ShieldUnlockProtectionRow(
                    lockConfig = reels.lockConfig,
                    hasGlobalPin = hasGlobalPin,
                    onClick = { lockSelectorForShield = ShieldType.REELS }
                )
            }
        }

        // ==================== CARD 2: Permanent Website Guard ====================
        AlwaysOnShieldAccordionCard(
            shieldType = ShieldType.WEBSITE,
            icon = Icons.Outlined.Language,
            title = ShieldType.WEBSITE.displayName,
            summarySubtitle = config.website.summarySubtitle(),
            lockBadgeLabel = if (config.website.enabled) config.website.lockConfig.activeBadgeLabel(hasGlobalPin) else null,
            enabled = config.website.enabled,
            expanded = expandedShield == ShieldType.WEBSITE,
            onToggleExpand = {
                expandedShield = if (expandedShield == ShieldType.WEBSITE) null else ShieldType.WEBSITE
            },
            onMasterToggle = { turnOn ->
                if (turnOn) {
                    onEnsureNotificationPermission()
                    onUpdateConfig(config.copy(website = config.website.copy(enabled = true)))
                } else {
                    gateWeakening(
                        shieldType = ShieldType.WEBSITE,
                        isCurrentlyEnabled = true,
                        lockConfig = config.website.lockConfig,
                        actionDescription = "Verify to disable Permanent Website Guard."
                    ) {
                        onUpdateConfig(config.copy(website = config.website.copy(enabled = false)))
                    }
                }
            }
        ) {
            val web = config.website
            val customDomainsList = remember(web.customDomains) {
                web.customDomains.mapNotNull { WebsiteGuardConfig.normalizeDomainInput(it) }.distinct()
            }
            val activePackCount = web.enabledPackIds.size
            val bundledActiveTotal = remember(web.enabledPackIds) {
                PreloadedPacks.websitePacks
                    .filter { it.id in web.enabledPackIds }
                    .sumOf { it.bundledDomainCount }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Domains summary + Manage button (shows custom domains + active pack counts only; never expands huge packs into UI)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Domains & Active Lists",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = buildString {
                                append("${customDomainsList.size} custom domain(s)")
                                if (activePackCount > 0) {
                                    append(" • $bundledActiveTotal bundled across $activePackCount pack(s)")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showManageDomainsSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("manage_shield_domains_btn")
                    ) {
                        Text("Manage")
                    }
                }

                if (customDomainsList.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        customDomainsList.take(5).forEach { domain ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = domain,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (customDomainsList.size > 5) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "+${customDomainsList.size - 5} more",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Quick Preset Toggles
                Text(
                    text = "Quick Blocklist Presets",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                val adultPack = PreloadedPacks.websitePacks.first { it.id == "web_adult_blocklist" }
                val gamblingPack = PreloadedPacks.websitePacks.first { it.id == "web_gambling_blocklist" }

                ShieldSwitchRow(
                    title = "${adultPack.title} (${String.format(java.util.Locale.US, "%,d", adultPack.bundledDomainCount)} domains)",
                    subtitle = "Compressed OISD NSFW offline blocklist",
                    checked = adultPack.id in web.enabledPackIds,
                    testTag = "shield_web_quick_adult_switch"
                ) { checked ->
                    if (checked) {
                        onUpdateConfig(config.copy(website = web.copy(enabledPackIds = web.enabledPackIds + adultPack.id)))
                    } else {
                        gateWeakening(ShieldType.WEBSITE, web.enabled, web.lockConfig, "Verify to disable Adult Domains Blocklist.") {
                            onUpdateConfig(config.copy(website = web.copy(enabledPackIds = web.enabledPackIds - adultPack.id)))
                        }
                    }
                }

                ShieldSwitchRow(
                    title = "${gamblingPack.title} (${String.format(java.util.Locale.US, "%,d", gamblingPack.bundledDomainCount)} domains)",
                    subtitle = "Compressed online casino & betting domain blocklist",
                    checked = gamblingPack.id in web.enabledPackIds,
                    testTag = "shield_web_quick_gambling_switch"
                ) { checked ->
                    if (checked) {
                        onUpdateConfig(config.copy(website = web.copy(enabledPackIds = web.enabledPackIds + gamblingPack.id)))
                    } else {
                        gateWeakening(ShieldType.WEBSITE, web.enabled, web.lockConfig, "Verify to disable Gambling Blocklist.") {
                            onUpdateConfig(config.copy(website = web.copy(enabledPackIds = web.enabledPackIds - gamblingPack.id)))
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Browser Bypass Protection",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                ShieldSwitchRow(
                    title = "Block Unsupported Browsers",
                    subtitle = "Blocks obscure browsers that hide their address bar",
                    checked = web.blockUnsupportedBrowsers,
                    testTag = "shield_web_unsupported_switch"
                ) { checked ->
                    if (checked) {
                        onUpdateConfig(config.copy(website = web.copy(blockUnsupportedBrowsers = true)))
                    } else {
                        gateWeakening(ShieldType.WEBSITE, web.enabled, web.lockConfig, "Verify to allow unsupported browsers.") {
                            onUpdateConfig(config.copy(website = web.copy(blockUnsupportedBrowsers = false)))
                        }
                    }
                }

                ShieldSwitchRow(
                    title = "Block Incognito / Private Tabs",
                    subtitle = "Intercepts private browsing sessions in supported browsers",
                    checked = web.blockIncognitoMode,
                    testTag = "shield_web_incognito_switch"
                ) { checked ->
                    if (checked) {
                        onUpdateConfig(config.copy(website = web.copy(blockIncognitoMode = true)))
                    } else {
                        gateWeakening(ShieldType.WEBSITE, web.enabled, web.lockConfig, "Verify to allow incognito browsing.") {
                            onUpdateConfig(config.copy(website = web.copy(blockIncognitoMode = false)))
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Interception Style",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val styles = listOf(
                        BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE to "AmniSpace Breathing",
                        BlockInterceptionStyle.STANDARD_WARNING_DIALOG to "Warning Screen",
                        BlockInterceptionStyle.SILENT_CLEAN to "Silent Clean",
                        BlockInterceptionStyle.INSTANT_HOME_OR_BACK to "Exit to Home"
                    )
                    styles.forEach { (style, label) ->
                        FilterChip(
                            selected = web.interceptionStyle == style,
                            onClick = {
                                onUpdateConfig(config.copy(website = web.copy(interceptionStyle = style)))
                            },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider()

                ShieldUnlockProtectionRow(
                    lockConfig = web.lockConfig,
                    hasGlobalPin = hasGlobalPin,
                    onClick = { lockSelectorForShield = ShieldType.WEBSITE }
                )
            }
        }

        // ==================== CARD 3: Keyword & Search Filter ====================
        AlwaysOnShieldAccordionCard(
            shieldType = ShieldType.KEYWORD,
            icon = Icons.Outlined.Spellcheck,
            title = ShieldType.KEYWORD.displayName,
            summarySubtitle = config.keyword.summarySubtitle(),
            lockBadgeLabel = if (config.keyword.enabled) config.keyword.lockConfig.activeBadgeLabel(hasGlobalPin) else null,
            enabled = config.keyword.enabled,
            expanded = expandedShield == ShieldType.KEYWORD,
            onToggleExpand = {
                expandedShield = if (expandedShield == ShieldType.KEYWORD) null else ShieldType.KEYWORD
            },
            onMasterToggle = { turnOn ->
                if (turnOn) {
                    onEnsureNotificationPermission()
                    onUpdateConfig(config.copy(keyword = config.keyword.copy(enabled = true)))
                } else {
                    gateWeakening(
                        shieldType = ShieldType.KEYWORD,
                        isCurrentlyEnabled = true,
                        lockConfig = config.keyword.lockConfig,
                        actionDescription = "Verify to disable Keyword & Search Filter."
                    ) {
                        onUpdateConfig(config.copy(keyword = config.keyword.copy(enabled = false)))
                    }
                }
            }
        ) {
            val kw = config.keyword
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keyword Packs & Custom Terms",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${kw.effectiveKeywords().size} filtered term(s) (${kw.enabledPackIds.size} packs, ${kw.customKeywords.size} custom)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showManageKeywordsSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("manage_shield_keywords_btn")
                    ) {
                        Text("Manage")
                    }
                }

                // Quick Keyword Pack chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreloadedPacks.keywordPacks.forEach { pack ->
                        val active = pack.id in kw.enabledPackIds
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (!active) {
                                    onUpdateConfig(config.copy(keyword = kw.copy(enabledPackIds = kw.enabledPackIds + pack.id)))
                                } else {
                                    gateWeakening(
                                        ShieldType.KEYWORD,
                                        kw.enabled,
                                        kw.lockConfig,
                                        "Verify to disable ${pack.title}."
                                    ) {
                                        onUpdateConfig(config.copy(keyword = kw.copy(enabledPackIds = kw.enabledPackIds - pack.id)))
                                    }
                                }
                            },
                            label = { Text("${pack.title} (${pack.items.size})") }
                        )
                    }
                }

                HorizontalDivider()

                // Ignored Apps whitelist link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showIgnoredAppsSheet = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ignored Apps Whitelist",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${kw.ignoredPackages.size} whitelisted app(s) excluded from keyword scanning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Manage Ignored Apps",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ShieldSwitchRow(
                    title = "Whole-Word Matching Only",
                    subtitle = "Prevents short keywords triggering inside unrelated words",
                    checked = kw.wholeWordOnly,
                    testTag = "shield_kw_whole_word_switch"
                ) { checked ->
                    onUpdateConfig(config.copy(keyword = kw.copy(wholeWordOnly = checked)))
                }

                ShieldSwitchRow(
                    title = "Scan All On-Screen Text",
                    subtitle = "Inspects all visible text fields beyond browser search bars",
                    checked = kw.scanAllTextFields,
                    testTag = "shield_kw_scan_all_switch"
                ) { checked ->
                    if (checked) {
                        onUpdateConfig(config.copy(keyword = kw.copy(scanAllTextFields = true)))
                    } else {
                        gateWeakening(ShieldType.KEYWORD, kw.enabled, kw.lockConfig, "Verify to reduce keyword scan scope.") {
                            onUpdateConfig(config.copy(keyword = kw.copy(scanAllTextFields = false)))
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Interception Style",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val styles = listOf(
                        BlockInterceptionStyle.STANDARD_WARNING_DIALOG to "Warning Screen",
                        BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE to "AmniSpace Breathing",
                        BlockInterceptionStyle.INSTANT_HOME_OR_BACK to "Silent Clear / Exit"
                    )
                    styles.forEach { (style, label) ->
                        FilterChip(
                            selected = kw.interceptionStyle == style,
                            onClick = {
                                onUpdateConfig(config.copy(keyword = kw.copy(interceptionStyle = style)))
                            },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider()

                ShieldUnlockProtectionRow(
                    lockConfig = kw.lockConfig,
                    hasGlobalPin = hasGlobalPin,
                    onClick = { lockSelectorForShield = ShieldType.KEYWORD }
                )
            }
        }
    }
}

@Composable
private fun AlwaysOnShieldAccordionCard(
    shieldType: ShieldType,
    icon: ImageVector,
    title: String,
    summarySubtitle: String,
    lockBadgeLabel: String?,
    enabled: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onMasterToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "accordion_chevron_${shieldType.name}"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (enabled) 1.5.dp else 1.dp,
            color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("always_on_card_${shieldType.name.lowercase()}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (lockBadgeLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = lockBadgeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = summarySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onMasterToggle,
                    modifier = Modifier.testTag("always_on_master_switch_${shieldType.name.lowercase()}")
                )

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                        modifier = Modifier.rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 14.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun ShieldSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ShieldUnlockProtectionRow(
    lockConfig: ShieldLockConfig,
    hasGlobalPin: Boolean,
    onClick: () -> Unit
) {
    val activeLabel = lockConfig.activeBadgeLabel(hasGlobalPin)
        ?: if (lockConfig.mode == ShieldLockMode.INHERIT_GLOBAL_PIN && !hasGlobalPin) {
            "Global PIN (Not set yet)"
        } else {
            "No Lock"
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unlock Protection",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = activeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Change unlock protection",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
