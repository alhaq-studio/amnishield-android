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

private const val TAG = "FocusModeConfigScreen"

/**
 * Dedicated Jetpack Compose configuration screen for Focus Mode.
 * 
 * Manages behavioral settings and preferences:
 * - Protection Mode (Block Selected vs Allow Only Selected)
 * - Focus app list selection
 * - Direct shortcut to Auto-Focus schedules in Blocks Manager
 * - Direct shortcut to start an instant Quick Focus session
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeConfigScreen(
    isServiceEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onBack: () -> Unit,
    onSelectAppsClick: () -> Unit,
    onStartFocusModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }

    var selectedAppsCount by remember { mutableStateOf(loader.getFocusModeSelectedApps().size) }
    var focusModeType by remember {
        val initialMode = loader.getFocusModeData().modeType
        mutableStateOf(if (initialMode == com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED) com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED else com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_SELECTED)
    }

    val autoFocusRulesCount = remember {
        loader.loadAppBlockerScheduleRules()
            .filter {
                it.packageName.equals("FOCUS_MODE", ignoreCase = true) ||
                it.packageName.equals("focus_mode", ignoreCase = true)
            }
            .map { it.groupId ?: it.id }
            .distinct()
            .size
    }

    Log.d(TAG, "Rendering FocusModeConfigScreen: selectedApps=$selectedAppsCount, autoFocusRules=$autoFocusRulesCount")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Focus Mode Settings",
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

            // 1. Quick Focus On-Demand Session Launcher
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelfImprovement,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick Focus Session",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Start an instant timer session right now",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onStartFocusModeClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Quick Focus Now", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 2. Focus Protection Mode Strategy
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Protection Strategy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Option A: Block Selected Apps
                    val isBlockSelected = focusModeType == com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_SELECTED
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBlockSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isBlockSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Log.i(TAG, "Selected focus mode type: Block Selected")
                                focusModeType = com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_SELECTED
                                val existingData = loader.getFocusModeData()
                                loader.saveFocusModeData(existingData.copy(modeType = com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_SELECTED))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isBlockSelected,
                                onClick = {
                                    focusModeType = com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_SELECTED
                                    val existingData = loader.getFocusModeData()
                                    loader.saveFocusModeData(existingData.copy(modeType = com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_SELECTED))
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Block Selected Apps (Default)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Only chosen distracting apps are blocked; all other apps remain accessible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option B: Block All Except Selected
                    val isBlockAllExcept = focusModeType == com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBlockAllExcept) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isBlockAllExcept) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Log.i(TAG, "Selected focus mode type: Whitelist Mode")
                                focusModeType = com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED
                                val existingData = loader.getFocusModeData()
                                loader.saveFocusModeData(existingData.copy(modeType = com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isBlockAllExcept,
                                onClick = {
                                    focusModeType = com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED
                                    val existingData = loader.getFocusModeData()
                                    loader.saveFocusModeData(existingData.copy(modeType = com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED))
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Strict Whitelist (Block All Except)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "All apps are blocked during focus mode EXCEPT the selected essential apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Selected Apps Config Card
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
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Focus Apps Selection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$selectedAppsCount apps selected for focus protection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onSelectAppsClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Target Apps ($selectedAppsCount)")
                    }
                }
            }

            // 4. Auto-Focus Schedules Link
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
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Focus Schedules",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$autoFocusRulesCount recurring focus schedule" + if (autoFocusRulesCount != 1) "s" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            Log.d(TAG, "Navigating to Blocks Manager for Auto-Focus Schedules")
                            val intent = Intent(context, FragmentActivity::class.java).apply {
                                putExtra("fragment", BlocksManagerFragment.FRAGMENT_ID)
                                putExtra("filter_type", "Focus Mode")
                                putExtra("prefill_target", "FOCUS_MODE")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Configure Auto-Focus Schedules")
                    }
                }
            }
        }
    }
}
