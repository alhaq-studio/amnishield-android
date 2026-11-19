package com.alhaq.amnishield.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.ui.components.AmniShieldButton
import com.alhaq.amnishield.ui.components.AmniShieldButtonStyle

enum class SetupModeChoice {
    OFFLINE_STANDALONE,
    CLOUD_SYNC
}

/**
 * SetupModeScreen allows the user during onboarding (or setup reconfiguration)
 * to choose between 100% offline standalone operation (zero accounts, zero network calls)
 * or multi-device cloud synchronization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupModeScreen(
    modifier: Modifier = Modifier,
    initialChoice: SetupModeChoice = SetupModeChoice.OFFLINE_STANDALONE,
    onContinueOffline: () -> Unit,
    onContinueCloudSync: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var selectedChoice by remember { mutableStateOf(initialChoice) }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Account & Setup Mode") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            if (selectedChoice == SetupModeChoice.OFFLINE_STANDALONE) {
                                onContinueOffline()
                            } else {
                                onContinueCloudSync()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = if (selectedChoice == SetupModeChoice.OFFLINE_STANDALONE) {
                                "Continue in Offline Mode"
                            } else {
                                "Continue with Cloud Sync"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You can change or switch modes anytime in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Text(
                text = "Choose Your Setup Mode",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select how you would like to run AmniShield on this device. You retain full control over your privacy and rule enforcement.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Option 1: Offline Standalone (Default)
            SetupModeOptionCard(
                title = "Use Offline (No Account Required)",
                badgeText = "[RECOMMENDED - COMPLETE OFFLINE]",
                description = "100% on-device operation. Zero accounts, zero background connections, and full privacy. All blocking rules, schedules, and analytics stay strictly on this device. Offline ECDSA Supporter Pass keys remain fully supported.",
                icon = Icons.Outlined.CloudOff,
                badgeColor = MaterialTheme.colorScheme.primary,
                isSelected = selectedChoice == SetupModeChoice.OFFLINE_STANDALONE,
                bulletPoints = listOf(
                    "Zero accounts, zero registration, and zero telemetry",
                    "No background network sync or server communication",
                    "All app, web, keyword, and reels blockers run 100% locally",
                    "Offline ECDSA license key validation supported"
                ),
                onClick = { selectedChoice = SetupModeChoice.OFFLINE_STANDALONE }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 2: Cloud Sync & Pairing
            SetupModeOptionCard(
                title = "Connect Amni Ecosystem (Cloud Sync & Pairing)",
                badgeText = "[OPTIONAL MULTI-DEVICE]",
                description = "Synchronize rules, schedules, and website filters across Android, Windows PC, and Browser Extensions via your account or a 6-digit Console PIN. You can also enable or disable this anytime in Settings.",
                icon = Icons.Outlined.Devices,
                badgeColor = MaterialTheme.colorScheme.tertiary,
                isSelected = selectedChoice == SetupModeChoice.CLOUD_SYNC,
                bulletPoints = listOf(
                    "Cross-device rule sync with Windows PC and Browser Extension",
                    "Manage devices remotely via Web Console or 6-digit PIN",
                    "Encrypted cloud policy backup and force-apply remote updates",
                    "Switch back to offline mode anytime from Settings"
                ),
                onClick = { selectedChoice = SetupModeChoice.CLOUD_SYNC }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SetupModeOptionCard(
    title: String,
    badgeText: String,
    description: String,
    icon: ImageVector,
    badgeColor: Color,
    isSelected: Boolean,
    bulletPoints: List<String>,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) badgeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        label = "borderColor"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) badgeColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        label = "containerColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) badgeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) badgeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = badgeColor,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            bulletPoints.forEach { point ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isSelected) badgeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
