package com.alhaq.amnshield.ui.screens.config

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.ui.activity.FragmentActivity
import com.alhaq.amnshield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnshield.utils.SavedPreferencesLoader

private const val TAG = "WebsiteBlockerConfig"

/**
 * Dedicated Jetpack Compose configuration screen for Website Blocker.
 * 
 * Manages behavioral settings and preferences:
 * - Master Website Blocker enabled switch
 * - Intercept warning screen behavior trigger
 * - Direct shortcut to Active Blocks Manager for website domains and schedule rules
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteBlockerConfigScreen(
    isServiceEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onBack: () -> Unit,
    onConfigureWarning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }

    var isFeatureEnabled by remember { mutableStateOf(loader.isWebsiteBlockerEnabled()) }

    val blockedWebsitesCount = remember { loader.loadBlockedWebsites().size }
    val websiteRulesCount = remember {
        loader.loadAppBlockerScheduleRules()
            .filter { it.packageName == "website_blocker" }
            .map { it.groupId ?: it.id }
            .distinct()
            .size
    }

    Log.d(TAG, "Rendering WebsiteBlockerConfigScreen: enabled=$isFeatureEnabled, sitesCount=$blockedWebsitesCount, rulesCount=$websiteRulesCount")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Website Blocker Settings",
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

            // 1. Master Feature Switch Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Website Blocker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isFeatureEnabled) "Intercepting restricted web domains" else "Website blocker suspended",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isFeatureEnabled,
                        onCheckedChange = { checked ->
                            Log.i(TAG, "Website Blocker feature toggle changed: $checked")
                            isFeatureEnabled = checked
                            loader.setWebsiteBlockerEnabled(checked, updateManual = true)

                            val refreshIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER).apply {
                                setPackage(context.packageName)
                            }
                            context.sendBroadcast(refreshIntent)
                        }
                    )
                }
            }

            // 2. Active Website Rules Link
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Website Rules & URL Lists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$websiteRulesCount active rules • $blockedWebsitesCount domains configured",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            Log.d(TAG, "Navigating to Blocks Manager for Website Blocker")
                            val intent = Intent(context, FragmentActivity::class.java).apply {
                                putExtra("fragment", BlocksManagerFragment.FRAGMENT_ID)
                                putExtra("filter_type", "Website Blocker")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Websites & Rules")
                    }
                }
            }

            // 3. Intercept Warning Screen Settings
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfigureWarning() }
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
                            text = "Warning Screen Customization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
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
