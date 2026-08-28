package com.alhaq.amnishield.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.R
import com.alhaq.amnishield.permissions.PermissionsManager
import com.alhaq.amnishield.utils.AccessibilityDisclosureDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
    onRestoreBackup: () -> Unit = {}
) {
    val context = LocalContext.current
    val permManager = remember { PermissionsManager(context) }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }

    var isAccessibilityGranted by remember { mutableStateOf(permManager.isAccessibilityServiceEnabled()) }
    var isOverlayGranted by remember { mutableStateOf(permManager.isDrawOverOtherAppsEnabled()) }
    var isUsageGranted by remember { mutableStateOf(permManager.isUsageStatsPermissionGranted()) }
    var isBatteryGranted by remember { mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) }
    var isNotifGranted by remember { mutableStateOf(permManager.areNotificationsEnabled()) }

    // Dialog States
    var showOverlayDisclosure by remember { mutableStateOf(false) }
    var showUsageDisclosure by remember { mutableStateOf(false) }
    var showBatteryDisclosure by remember { mutableStateOf(false) }

    // Activity Launchers
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isOverlayGranted = permManager.isDrawOverOtherAppsEnabled()
    }
    val usageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isUsageGranted = permManager.isUsageStatsPermissionGranted()
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isBatteryGranted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isNotifGranted = granted || permManager.areNotificationsEnabled()
    }

    // Refresh permissions on resume
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityGranted = permManager.isAccessibilityServiceEnabled()
                isOverlayGranted = permManager.isDrawOverOtherAppsEnabled()
                isUsageGranted = permManager.isUsageStatsPermissionGranted()
                isBatteryGranted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                isNotifGranted = permManager.areNotificationsEnabled()
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Permission Setup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Enable protections for seamless distraction blocking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                val canProceed = true
                var grantedCount = 0
                if (isAccessibilityGranted) grantedCount++
                if (isOverlayGranted) grantedCount++
                if (isUsageGranted) grantedCount++
                if (isBatteryGranted) grantedCount++

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onContinue,
                        enabled = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (grantedCount >= 3) "Continue to Quick Guide" else "Continue ($grantedCount/4 Ready)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRestoreBackup) {
                        Text("Restore from Backup Archive")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // 1. Accessibility Service (Primary)
            item {
                PermissionCard(
                    title = "Accessibility Service",
                    description = "Required to detect active apps, inspect search keywords, and redirect away from short video reels.",
                    icon = Icons.Outlined.Shield,
                    isGranted = isAccessibilityGranted,
                    badgeLabel = "Required",
                    onClick = {
                        if (!isAccessibilityGranted) {
                            AccessibilityDisclosureDialog.show(
                                context = context,
                                onAgree = {
                                    try {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback
                                    }
                                }
                            )
                        }
                    }
                )
            }

            // 2. Display Over Other Apps (Overlay)
            item {
                PermissionCard(
                    title = "Display Over Other Apps",
                    description = "Enables floating focus timers, mindful break overlays, and instant blocking shields over restricted apps.",
                    icon = Icons.Outlined.Layers,
                    isGranted = isOverlayGranted,
                    badgeLabel = "Required",
                    onClick = {
                        if (!isOverlayGranted) {
                            showOverlayDisclosure = true
                        }
                    }
                )
            }

            // 3. App Usage Access (Stats)
            item {
                PermissionCard(
                    title = "App Usage Access",
                    description = "Provides precise screen-time tracking, daily launch counts, and habit analytics for detailed insights.",
                    icon = Icons.Outlined.BarChart,
                    isGranted = isUsageGranted,
                    badgeLabel = "Recommended",
                    onClick = {
                        if (!isUsageGranted) {
                            showUsageDisclosure = true
                        }
                    }
                )
            }

            // 4. Unrestricted Background / Battery Optimization
            item {
                PermissionCard(
                    title = "Unrestricted Background",
                    description = "Prevents system battery optimizations from killing scheduled focus sessions and timers.",
                    icon = Icons.Outlined.BatteryChargingFull,
                    isGranted = isBatteryGranted,
                    badgeLabel = "Recommended",
                    onClick = {
                        if (!isBatteryGranted) {
                            showBatteryDisclosure = true
                        }
                    }
                )
            }

            // 5. Notifications
            item {
                PermissionCard(
                    title = "Notification Alerts",
                    description = "Delivers mindful reminders, daily usage recaps, and focus session completion alerts.",
                    icon = Icons.Outlined.Notifications,
                    isGranted = isNotifGranted,
                    badgeLabel = "Optional",
                    onClick = {
                        if (!isNotifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }

    // Overlay Disclosure Dialog
    if (showOverlayDisclosure) {
        AlertDialog(
            onDismissRequest = { showOverlayDisclosure = false },
            icon = { Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Display Over Other Apps") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AmniShield requires overlay permission to render blocking screens and mindful pause timers when a restricted app is launched.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Displays instant full-screen distraction shields\n• Shows mindful breathing and pause countdowns\n• Operates 100% on-device without data collection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayDisclosure = false
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayLauncher.launch(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                overlayLauncher.launch(intent)
                            } catch (e2: Exception) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                            }
                        }
                    }
                ) {
                    Text("Agree & Configure")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOverlayDisclosure = false }) {
                    Text("Deny")
                }
            }
        )
    }

    // Usage Stats Disclosure Dialog
    if (showUsageDisclosure) {
        AlertDialog(
            onDismissRequest = { showUsageDisclosure = false },
            icon = { Icon(Icons.Outlined.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("App Usage Access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AmniShield requires Usage Access to calculate screen time, count daily app opens, and enforce daily launch limits.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Computes daily screen time and reels scrolled\n• Enforces scheduled usage thresholds\n• Data is processed 100% locally and never uploaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUsageDisclosure = false
                        try {
                            val intent = Intent(
                                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                            usageLauncher.launch(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                usageLauncher.launch(intent)
                            } catch (e2: Exception) {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        }
                    }
                ) {
                    Text("Agree & Configure")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUsageDisclosure = false }) {
                    Text("Deny")
                }
            }
        )
    }

    // Battery Optimization Disclosure Dialog
    if (showBatteryDisclosure) {
        AlertDialog(
            onDismissRequest = { showBatteryDisclosure = false },
            icon = { Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unrestricted Background") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AmniShield needs unrestricted background execution to ensure focus mode timers and scheduled rules are not killed during battery saving.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Keeps schedules and timers active reliably\n• Prevents Android OS from pausing protection services\n• Zero background tracking or server telemetry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatteryDisclosure = false
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            batteryLauncher.launch(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (e2: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }
                    }
                ) {
                    Text("Agree & Configure")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatteryDisclosure = false }) {
                    Text("Deny")
                }
            }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    badgeLabel: String,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isGranted) 1.5.dp else 1.dp,
            color = if (isGranted) primaryColor else outlineColor
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) primaryColor.copy(alpha = 0.15f) else onSurfaceVariant.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) primaryColor else onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isGranted) primaryColor.copy(alpha = 0.15f) else if (badgeLabel == "Required") errorColor.copy(alpha = 0.15f) else onSurfaceVariant.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = if (isGranted) "Active" else badgeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGranted) primaryColor else if (badgeLabel == "Required") errorColor else onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (isGranted) primaryColor else onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
