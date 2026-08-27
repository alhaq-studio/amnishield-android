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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alhaq.amnishield.blockers.ReelBlocker
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.activity.FragmentActivity
import com.alhaq.amnishield.ui.components.bounceClick
import com.alhaq.amnishield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnishield.utils.SavedPreferencesLoader

private const val TAG = "ReelBlockerConfigScreen"

/**
 * Dedicated Jetpack Compose configuration screen for Short Video / Reel Blocker.
 * 
 * Manages behavioral settings and preferences:
 * - Master Reel Blocker enabled switch
 * - Blocking response mode (Warning Screen, Redirect to Home Feed, Exit to Android Home)
 * - Individual platform detection switches (YouTube Shorts, Instagram Reels, TikTok, In-Browser)
 * - Warning dialog customization trigger
 * - Direct shortcut to Active Blocks Manager for reel rules
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelBlockerConfigScreen(
    isServiceEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onBack: () -> Unit,
    onConfigureWarning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }

    var isFeatureEnabled by remember { mutableStateOf(loader.isReelBlockerEnabled()) }
    var isYoutubeEnabled by remember { mutableStateOf(loader.isReelBlockerYoutubeEnabled()) }
    var isInstagramEnabled by remember { mutableStateOf(loader.isReelBlockerInstagramEnabled()) }
    var isTiktokEnabled by remember { mutableStateOf(loader.isReelBlockerTiktokEnabled()) }
    var isBrowserEnabled by remember { mutableStateOf(loader.isReelBlockerBrowserEnabled()) }
    var blockResponseMode by remember { mutableStateOf(loader.getReelBlockerBlockResponseMode()) }

    val reelsRulesCount = remember {
        loader.loadAppBlockerScheduleRules()
            .filter { it.packageName == "reel_blocker" }
            .map { it.groupId ?: it.id }
            .distinct()
            .size
    }

    Log.d(TAG, "Rendering ReelBlockerConfigScreen: enabled=$isFeatureEnabled, rulesCount=$reelsRulesCount")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = "Short Video & Reels Settings",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Block short-form scroll feeds & addiction",
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

            // 1. Master Feature Switch Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFeatureEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = if (isFeatureEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Reels Blocker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isFeatureEnabled) "Intercepting short-form videos" else "Reels blocker suspended",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isFeatureEnabled,
                        onCheckedChange = { checked ->
                            Log.i(TAG, "Reels Blocker feature toggle changed: $checked")
                            isFeatureEnabled = checked
                            loader.setReelBlockerEnabled(checked, updateManual = true)

                            val refreshIntent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER).apply {
                                setPackage(context.packageName)
                            }
                            context.sendBroadcast(refreshIntent)
                        }
                    )
                }
            }

            // 2. Active Reels Rules Link
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reels Rules & Daily Limits",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$reelsRulesCount active reels schedule rule" + if (reelsRulesCount != 1) "s" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            Log.d(TAG, "Navigating to Blocks Manager for Reels Blocker")
                            val intent = Intent(context, FragmentActivity::class.java).apply {
                                putExtra("fragment", BlocksManagerFragment.FRAGMENT_ID)
                                putExtra("filter_type", "Reels Blocker")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Reels Rules & Limits", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 3. Block Action / Response Mode
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Block Action",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Choose what happens when a reel or short video is intercepted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    ResponseModeOptionRow(
                        title = "Warning Screen (Hard Block)",
                        description = "Shows the warning screen with cooldown timer & motivation",
                        icon = Icons.Outlined.Shield,
                        isSelected = blockResponseMode == ReelBlocker.BlockResponseMode.HARD_BLOCK,
                        onClick = {
                            blockResponseMode = ReelBlocker.BlockResponseMode.HARD_BLOCK
                            loader.setReelBlockerBlockResponseMode(ReelBlocker.BlockResponseMode.HARD_BLOCK)
                            broadcastReelsRefresh(context)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    ResponseModeOptionRow(
                        title = "Redirect to Home Feed",
                        description = "Stays in app and redirects to the safe home / news feed",
                        icon = Icons.Outlined.DynamicFeed,
                        isSelected = blockResponseMode == ReelBlocker.BlockResponseMode.HOME_FEED_REDIRECT,
                        onClick = {
                            blockResponseMode = ReelBlocker.BlockResponseMode.HOME_FEED_REDIRECT
                            loader.setReelBlockerBlockResponseMode(ReelBlocker.BlockResponseMode.HOME_FEED_REDIRECT)
                            broadcastReelsRefresh(context)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    ResponseModeOptionRow(
                        title = "Exit to Android Home",
                        description = "Closes to your phone's home screen immediately",
                        icon = Icons.Outlined.Smartphone,
                        isSelected = blockResponseMode == ReelBlocker.BlockResponseMode.ANDROID_HOME,
                        onClick = {
                            blockResponseMode = ReelBlocker.BlockResponseMode.ANDROID_HOME
                            loader.setReelBlockerBlockResponseMode(ReelBlocker.BlockResponseMode.ANDROID_HOME)
                            broadcastReelsRefresh(context)
                        }
                    )
                }
            }

            // 4. Platform Detection Switches
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Supported Platforms",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    PlatformToggleRow(
                        title = "YouTube Shorts",
                        description = "Intercept Shorts feed and tab in YouTube app",
                        icon = Icons.Outlined.SmartDisplay,
                        checked = isYoutubeEnabled,
                        onCheckedChange = { checked ->
                            Log.i(TAG, "YouTube Shorts switch: $checked")
                            isYoutubeEnabled = checked
                            loader.setReelBlockerYoutubeEnabled(checked)
                            broadcastReelsRefresh(context)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    PlatformToggleRow(
                        title = "Instagram Reels",
                        description = "Intercept Reels tab and infinite scroll video viewer",
                        icon = Icons.Outlined.PhotoCamera,
                        checked = isInstagramEnabled,
                        onCheckedChange = { checked ->
                            Log.i(TAG, "Instagram Reels switch: $checked")
                            isInstagramEnabled = checked
                            loader.setReelBlockerInstagramEnabled(checked)
                            broadcastReelsRefresh(context)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    PlatformToggleRow(
                        title = "TikTok",
                        description = "Intercept TikTok video feeds and explore tabs",
                        icon = Icons.Outlined.Videocam,
                        checked = isTiktokEnabled,
                        onCheckedChange = { checked ->
                            Log.i(TAG, "TikTok switch: $checked")
                            isTiktokEnabled = checked
                            loader.setReelBlockerTiktokEnabled(checked)
                            broadcastReelsRefresh(context)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    PlatformToggleRow(
                        title = "In-Browser Short Videos",
                        description = "Intercept web shorts in Chrome, Firefox & mobile browsers",
                        icon = Icons.Outlined.Language,
                        checked = isBrowserEnabled,
                        onCheckedChange = { checked ->
                            Log.i(TAG, "Browser Reels switch: $checked")
                            isBrowserEnabled = checked
                            loader.setReelBlockerBrowserEnabled(checked)
                            broadcastReelsRefresh(context)
                        }
                    )
                }
            }

            // 5. Intercept Warning Screen Settings
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
                    .clickable { onConfigureWarning() }
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
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Warning Screen Behavior",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Customize message, cooldown delays, and motivation",
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
        }
    }
}

@Composable
private fun PlatformToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ResponseModeOptionRow(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}

private fun broadcastReelsRefresh(context: Context) {
    val refreshReelIntent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER).apply {
        setPackage(context.packageName)
    }
    context.sendBroadcast(refreshReelIntent)
    val refreshViewIntent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER).apply {
        setPackage(context.packageName)
    }
    context.sendBroadcast(refreshViewIntent)
}
