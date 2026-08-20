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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alhaq.amnshield.data.blockers.PackageWand
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.ui.activity.FragmentActivity
import com.alhaq.amnshield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnshield.utils.SavedPreferencesLoader

private const val TAG = "AppBlockerConfigScreen"

/**
 * Dedicated Jetpack Compose configuration screen for App Blocker.
 * 
 * Manages behavioral settings and preferences:
 * - Master App Blocker enabled switch
 * - Category-based auto-blocking preferences
 * - Intercept warning screen behavior
 * - Direct shortcut to Active Blocks Manager for scheduling/rules
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlockerConfigScreen(
    isServiceEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onBack: () -> Unit,
    onConfigureWarning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }

    var isFeatureEnabled by remember { mutableStateOf(loader.isAppBlockerFeatureEnabled()) }
    var isAutoBlockEnabled by remember { mutableStateOf(loader.isAutoBlockEnabled()) }
    var selectedCategories by remember { mutableStateOf(loader.getAutoBlockCategories()) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    // Count distinct app rules currently configured
    val appRulesCount = remember {
        loader.loadAppBlockerScheduleRules()
            .filter {
                it.packageName != "keyword_blocker" &&
                it.packageName != "website_blocker" &&
                it.packageName != "reel_blocker" &&
                !it.packageName.equals("FOCUS_MODE", ignoreCase = true) &&
                !it.packageName.equals("focus_mode", ignoreCase = true)
            }
            .map { it.groupId ?: it.id }
            .distinct()
            .size
    }

    Log.d(TAG, "Rendering AppBlockerConfigScreen: enabled=$isFeatureEnabled, rulesCount=$appRulesCount")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "App Blocker Settings",
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
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable App Blocker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isFeatureEnabled) "Blocking active rules" else "App blocker suspended",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isFeatureEnabled,
                        onCheckedChange = { checked ->
                            Log.i(TAG, "App Blocker feature toggle changed: $checked")
                            isFeatureEnabled = checked
                            loader.setAppBlockerFeatureEnabled(checked, updateManual = true)

                            // Broadcast refresh intent
                            val refreshIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER).apply {
                                setPackage(context.packageName)
                            }
                            context.sendBroadcast(refreshIntent)
                        }
                    )
                }
            }

            // 2. Active App Rules Link
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
                            imageVector = Icons.Default.ViewAgenda,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Blocking Rules",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$appRulesCount active app rule" + if (appRulesCount != 1) "s" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            Log.d(TAG, "Navigating to Blocks Manager for App Blocker")
                            val intent = Intent(context, FragmentActivity::class.java).apply {
                                putExtra("fragment", BlocksManagerFragment.FRAGMENT_ID)
                                putExtra("filter_type", "App Blocker")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage App Rules & Limits")
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
                            text = "Warning Screen Behavior",
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

            // 4. Auto-Block Categories Settings
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                imageVector = Icons.Outlined.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Block App Categories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (selectedCategories.isEmpty()) "No categories selected" else "${selectedCategories.size} categories auto-blocked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutoBlockEnabled,
                            onCheckedChange = { checked ->
                                Log.i(TAG, "Auto-block categories switch: $checked")
                                isAutoBlockEnabled = checked
                                loader.setAutoBlockEnabled(checked)
                            }
                        )
                    }

                    if (isAutoBlockEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showCategoryDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Categories (${selectedCategories.size})")
                        }
                    }
                }
            }
        }
    }

    // Category Multi-Choice Selection Dialog
    if (showCategoryDialog) {
        val allCategories = remember { PackageWand.getAllCategories() }
        val tempSelected = remember { mutableStateListOf<String>().apply { addAll(selectedCategories) } }

        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = {
                Text(
                    text = "Select Auto-Block Categories",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Newly installed apps matching these categories will be blocked automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    allCategories.forEach { (key, name) ->
                        val isChecked = tempSelected.contains(key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) tempSelected.remove(key) else tempSelected.add(key)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) tempSelected.add(key) else tempSelected.remove(key)
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newSet = tempSelected.toSet()
                        selectedCategories = newSet
                        loader.setAutoBlockCategories(newSet)
                        Log.i(TAG, "Saved auto-block categories: $newSet")
                        showCategoryDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Reusable Service Required Warning Card across all config screens
 */
@Composable
fun ServiceRequiredCard(
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Accessibility Service Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Enable the service in Android Settings for blocking to work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Service", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}
