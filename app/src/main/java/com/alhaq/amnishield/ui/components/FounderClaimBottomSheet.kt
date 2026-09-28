package com.alhaq.amnishield.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FounderClaimBottomSheet(
    onConfirm: (handle: String, optInPublic: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var handleInput by remember { mutableStateOf("") }
    var optInPublic by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE5B842).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFE5B842).copy(alpha = 0.4f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.WorkspacePremium,
                            contentDescription = "Founder Pass",
                            tint = Color(0xFFE5B842),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "FOUNDER PASS INVITATION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5B842),
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Early Supporter Reward",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = "Thank you for supporting AmniShield from the beginning. You are entitled to a complimentary 3-month Full Premium pass and exclusive Founder privileges.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Perks List
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FounderPerkItem(
                        icon = Icons.Outlined.Shield,
                        title = "3 Months Full Security Suite",
                        description = "Triple-PIN barriers, anti-tamper detection, and anti-uninstall enforcement."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FounderPerkItem(
                        icon = Icons.Outlined.Palette,
                        title = "Exclusive Founder Obsidian Theme",
                        description = "High-contrast dark theme with rich champagne gold and bronze accents."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FounderPerkItem(
                        icon = Icons.Outlined.WorkspacePremium,
                        title = "Founder Supporter Badge",
                        description = "Permanent early-adopter emblem displayed on your profile and settings."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FounderPerkItem(
                        icon = Icons.Outlined.HistoryEdu,
                        title = "AmniShield Journey & Hall of Fame",
                        description = "Permanent listing of your handle in the app documentation and journey wall."
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Supporter Handle Input
            Text(
                text = "Your Supporter Handle / Nickname (Optional)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = handleInput,
                onValueChange = { handleInput = it },
                placeholder = { Text("e.g., Habib, FocusPioneer, or Anonymous") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE5B842),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Opt-in Checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = optInPublic,
                    onCheckedChange = { optInPublic = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE5B842))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add my handle to the public AmniShield Journey & Hall of Fame",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Zero-knowledge privacy: Real names are never required; pseudonyms are fully supported. No hardware IDs or personal telemetry are transmitted.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 18.dp)
            )

            // Action Buttons
            Button(
                onClick = {
                    onConfirm(handleInput.trim(), optInPublic)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE5B842),
                    contentColor = Color(0xFF281C00)
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirm & Claim Founder Pass",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Skip for Now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FounderPerkItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFE5B842),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
