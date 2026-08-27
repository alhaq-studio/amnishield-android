package com.alhaq.amnishield.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthType

/**
 * Dialog to configure or change rule-level PIN protection.
 */
@Composable
fun SetRulePinDialog(
    initialAuthType: AuthType = AuthType.NONE,
    isPremiumUser: Boolean = false,
    onDismiss: () -> Unit,
    onNavigateToPremium: () -> Unit = {},
    onSaveProtection: (AuthType, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val authResolver = remember { AuthResolver(context) }
    val hasGlobalPin = remember { authResolver.hasGlobalPin() }

    var selectedType by remember { mutableStateOf(initialAuthType) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun handleSave() {
        if (selectedType != AuthType.NONE && !isPremiumUser) {
            onDismiss()
            onNavigateToPremium()
            return
        }

        when (selectedType) {
            AuthType.NONE -> {
                onSaveProtection(AuthType.NONE, null, null)
            }
            AuthType.GLOBAL_PIN -> {
                onSaveProtection(AuthType.GLOBAL_PIN, null, null)
            }
            AuthType.RULE_PIN -> {
                if (pinInput.length < 4) {
                    validationError = "PIN must be at least 4 digits."
                    return
                }
                if (pinInput != confirmPinInput) {
                    validationError = "PINs do not match. Please re-enter."
                    return
                }
                val (hash, salt) = authResolver.createRulePin(pinInput)
                onSaveProtection(AuthType.RULE_PIN, hash, salt)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .testTag("set_rule_pin_dialog"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Rule Security Level",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Require authentication to edit, disable, or delete this rule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecurityOptionCard(
                        title = "No Lock (Standard)",
                        subtitle = "Rule can be edited or removed freely without a PIN",
                        icon = Icons.Outlined.LockOpen,
                        isSelected = selectedType == AuthType.NONE,
                        onClick = {
                            selectedType = AuthType.NONE
                            validationError = null
                        }
                    )

                    if (hasGlobalPin) {
                        SecurityOptionCard(
                            title = "Global Master PIN",
                            subtitle = "Protected by your system anti-uninstall password",
                            icon = Icons.Outlined.Shield,
                            isSelected = selectedType == AuthType.GLOBAL_PIN,
                            badge = if (!isPremiumUser) { { RuleProBadge() } } else null,
                            onClick = {
                                if (!isPremiumUser) {
                                    onDismiss()
                                    onNavigateToPremium()
                                } else {
                                    selectedType = AuthType.GLOBAL_PIN
                                    validationError = null
                                }
                            }
                        )
                    }

                    SecurityOptionCard(
                        title = "Custom Rule PIN",
                        subtitle = "Set a dedicated PIN specifically for this rule",
                        icon = Icons.Outlined.Lock,
                        isSelected = selectedType == AuthType.RULE_PIN,
                        badge = if (!isPremiumUser) { { RuleProBadge() } } else null,
                        onClick = {
                            if (!isPremiumUser) {
                                onDismiss()
                                onNavigateToPremium()
                            } else {
                                selectedType = AuthType.RULE_PIN
                                validationError = null
                            }
                        }
                    )
                }

                AnimatedVisibility(visible = selectedType == AuthType.RULE_PIN) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                if (it.length <= 12 && it.all { c -> c.isDigit() }) {
                                    pinInput = it
                                    validationError = null
                                }
                            },
                            label = { Text("Enter Rule PIN (4-6 digits)") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = confirmPinInput,
                            onValueChange = {
                                if (it.length <= 12 && it.all { c -> c.isDigit() }) {
                                    confirmPinInput = it
                                    validationError = null
                                }
                            },
                            label = { Text("Confirm Rule PIN") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            isError = confirmPinInput.isNotEmpty() && pinInput != confirmPinInput,
                            supportingText = {
                                if (confirmPinInput.isNotEmpty() && pinInput != confirmPinInput) {
                                    Text("PINs do not match", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { handleSave() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_rule_protection_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply Protection", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    badge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    badge?.invoke()
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
