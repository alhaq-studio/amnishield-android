package com.alhaq.amnishield.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.permissions.PermissionsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityGuideScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER.lowercase()
    val isSamsung = manufacturer.contains("samsung")
    val isXiaomi = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
    val isOppoVivo = manufacturer.contains("oppo") || manufacturer.contains("vivo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility Setup Guide", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Accessibility Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("I've Enabled It — Continue")
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    text = "Follow these simple steps for your device (${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }}):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isXiaomi) {
                item {
                    StepCard(
                        step = 1,
                        title = "Locate Downloaded Apps",
                        description = "In Accessibility settings, tap 'Downloaded Apps' or 'Installed Services'."
                    )
                }
                item {
                    StepCard(
                        step = 2,
                        title = "Select AmniShield",
                        description = "Find AmniShield in the list and switch the toggle to ON."
                    )
                }
                item {
                    StepCard(
                        step = 3,
                        title = "Allow Restricted Settings (MIUI/HyperOS)",
                        description = "If grayed out: Go to App Info > AmniShield > tap the 3 dots (top right) > 'Allow restricted settings'."
                    )
                }
            } else if (isSamsung) {
                item {
                    StepCard(
                        step = 1,
                        title = "Open Installed Apps",
                        description = "In Accessibility settings, scroll down and select 'Installed Apps'."
                    )
                }
                item {
                    StepCard(
                        step = 2,
                        title = "Turn On AmniShield",
                        description = "Tap on 'AmniShield' and switch the main toggle to 'On'."
                    )
                }
                item {
                    StepCard(
                        step = 3,
                        title = "Confirm System Prompt",
                        description = "Tap 'Allow' on the confirmation dialog to grant full distraction shielding."
                    )
                }
            } else {
                item {
                    StepCard(
                        step = 1,
                        title = "Find Downloaded Services",
                        description = "In Accessibility settings, scroll down to 'Downloaded Services' or 'Installed Apps'."
                    )
                }
                item {
                    StepCard(
                        step = 2,
                        title = "Select AmniShield",
                        description = "Tap on 'AmniShield' and enable the switch."
                    )
                }
                item {
                    StepCard(
                        step = 3,
                        title = "Confirm Activation",
                        description = "Tap 'Allow' on the Android system dialog to start blocking distractions."
                    )
                }
            }

            item {
                OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Data Privacy: Zero personal or sensitive data is collected, stored, or transmitted using this service. All processing runs 100% locally on-device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    step: Int,
    title: String,
    description: String
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step.toString(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
