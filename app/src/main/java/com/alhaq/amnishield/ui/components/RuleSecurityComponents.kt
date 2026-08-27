package com.alhaq.amnishield.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.data.blockers.BaseRule
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthType

/**
 * Reusable PRO badge for premium-gated security features.
 */
@Composable
fun RuleProBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFF59E0B).copy(alpha = 0.18f),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(9.dp)
            )
            Text(
                text = "PRO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                fontSize = 9.sp,
                color = Color(0xFFF59E0B)
            )
        }
    }
}

/**
 * Reusable inline Security Level card for rule creation and edit screens.
 */
@Composable
fun RuleSecurityLevelSection(
    authType: AuthType,
    onAuthTypeChange: (AuthType) -> Unit,
    customPin: String,
    onCustomPinChange: (String) -> Unit,
    customPinConfirm: String,
    onCustomPinConfirmChange: (String) -> Unit,
    isPremiumUser: Boolean = false,
    onNavigateToPremium: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authResolver = remember { AuthResolver(context) }
    val hasGlobalPin = remember { authResolver.hasGlobalPin() }
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (authType != AuthType.NONE) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (authType != AuthType.NONE) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (authType != AuthType.NONE) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Security Level & Protection Lock",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isPremiumUser) {
                            RuleProBadge()
                        }
                    }
                    Text(
                        text = when (authType) {
                            AuthType.NONE -> "Standard: No PIN required to edit or delete"
                            AuthType.GLOBAL_PIN -> "Master PIN required to modify or remove"
                            AuthType.RULE_PIN -> "Custom Rule PIN required to modify or remove"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Options Row / Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option: None (Free)
                FilterChip(
                    selected = authType == AuthType.NONE,
                    onClick = { onAuthTypeChange(AuthType.NONE) },
                    label = { Text("No PIN") },
                    leadingIcon = {
                        if (authType == AuthType.NONE) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                // Option: Global PIN (Pro)
                if (hasGlobalPin) {
                    FilterChip(
                        selected = authType == AuthType.GLOBAL_PIN,
                        onClick = {
                            if (!isPremiumUser) {
                                onNavigateToPremium()
                            } else {
                                onAuthTypeChange(AuthType.GLOBAL_PIN)
                            }
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Master PIN")
                                if (!isPremiumUser) {
                                    RuleProBadge()
                                }
                            }
                        },
                        leadingIcon = {
                            if (authType == AuthType.GLOBAL_PIN) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        modifier = Modifier.weight(1.2f)
                    )
                }

                // Option: Custom Rule PIN (Pro)
                FilterChip(
                    selected = authType == AuthType.RULE_PIN,
                    onClick = {
                        if (!isPremiumUser) {
                            onNavigateToPremium()
                        } else {
                            onAuthTypeChange(AuthType.RULE_PIN)
                        }
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Rule PIN")
                            if (!isPremiumUser) {
                                RuleProBadge()
                            }
                        }
                    },
                    leadingIcon = {
                        if (authType == AuthType.RULE_PIN) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.weight(1.1f)
                )
            }

            // Expanded Custom Rule PIN Inputs (Visible when Pro user chooses RULE_PIN)
            AnimatedVisibility(visible = authType == AuthType.RULE_PIN) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customPin,
                        onValueChange = {
                            if (it.length <= 12 && it.all { c -> c.isDigit() }) onCustomPinChange(it)
                        },
                        label = { Text("Set Custom Rule PIN (4-6 digits)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_rule_pin_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = customPinConfirm,
                        onValueChange = {
                            if (it.length <= 12 && it.all { c -> c.isDigit() }) onCustomPinConfirmChange(it)
                        },
                        label = { Text("Confirm Custom Rule PIN") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = customPinConfirm.isNotEmpty() && customPin != customPinConfirm,
                        supportingText = {
                            if (customPinConfirm.isNotEmpty() && customPin != customPinConfirm) {
                                Text("PINs do not match", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_rule_pin_confirm_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Direct lock/unlock action button for rule cards and preview rows.
 */
@Composable
fun RuleLockIconButton(
    rule: BaseRule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = rule.authType != AuthType.NONE

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(36.dp)
            .testTag("rule_lock_btn_${rule.id}")
    ) {
        Icon(
            imageVector = if (isLocked) Icons.Default.Lock else Icons.Outlined.LockOpen,
            contentDescription = if (isLocked) "Rule is PIN-locked. Tap to unlock." else "Rule is unlocked. Tap to set PIN.",
            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

