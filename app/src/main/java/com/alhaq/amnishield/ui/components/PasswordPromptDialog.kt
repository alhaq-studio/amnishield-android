package com.alhaq.amnishield.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alhaq.amnishield.data.blockers.BaseRule
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthResult
import com.alhaq.amnishield.security.AuthTarget
import kotlinx.coroutines.delay

/**
 * Enterprise security challenge dialog for rule-level and system authentication.
 * Uses strict non-dismissible dialog properties to prevent bypasses via system back or outside taps.
 */
@Composable
fun PasswordPromptDialog(
    rule: BaseRule? = null,
    target: AuthTarget? = null,
    title: String = "Authentication Required",
    subtitle: String = "Enter your PIN to proceed with this protected action",
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authResolver = remember { AuthResolver(context) }

    val resolvedTarget = remember(rule, target) {
        target ?: authResolver.resolveChallenge(rule)
    }

    // Pass-through immediately if rule is not protected
    LaunchedEffect(resolvedTarget) {
        if (resolvedTarget is AuthTarget.PassThrough) {
            onSuccess()
        }
    }

    if (resolvedTarget is AuthTarget.PassThrough) {
        return
    }

    var pinInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutRemainingMillis by remember {
        mutableStateOf(
            if (resolvedTarget is AuthTarget.LockedOut) resolvedTarget.remainingMillis
            else authResolver.getLockoutRemainingMillis()
        )
    }
    val isLockedOut = lockoutRemainingMillis > 0L

    // Lockout countdown timer
    LaunchedEffect(isLockedOut, lockoutRemainingMillis) {
        if (isLockedOut) {
            while (lockoutRemainingMillis > 0L) {
                delay(1000L)
                lockoutRemainingMillis = authResolver.getLockoutRemainingMillis()
            }
            errorMessage = null
        }
    }

    fun submitPin() {
        if (isLockedOut || pinInput.isBlank()) return

        when (val result = authResolver.verifyChallenge(resolvedTarget, pinInput)) {
            is AuthResult.Success -> {
                errorMessage = null
                onSuccess()
            }
            is AuthResult.InvalidPin -> {
                pinInput = ""
                if (result.isLockedOut) {
                    lockoutRemainingMillis = result.lockoutRemainingMillis
                    errorMessage = "Too many failed attempts. Locked out for 2 minutes."
                } else {
                    errorMessage = "Incorrect PIN. ${result.attemptsRemaining} attempts remaining."
                }
            }
            is AuthResult.LockedOut -> {
                pinInput = ""
                lockoutRemainingMillis = result.remainingMillis
                errorMessage = "Authentication locked out. Please wait for cooldown."
            }
        }
    }

    Dialog(
        onDismissRequest = { /* Strict interceptor: Do not dismiss on outside click */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .testTag("password_prompt_dialog"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLockedOut) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLockedOut) Icons.Default.HourglassTop else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isLockedOut) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Title and Subtitle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isLockedOut) "Security Lockout Active" else title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isLockedOut) {
                            val seconds = (lockoutRemainingMillis / 1000L).coerceAtLeast(1)
                            val mins = seconds / 60
                            val secs = seconds % 60
                            "Too many failed attempts. Try again in %d:%02d".format(mins, secs)
                        } else subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isLockedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Error Message if present and not locked out
                AnimatedVisibility(visible = errorMessage != null && !isLockedOut) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // PIN Input Field
                if (!isLockedOut) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { newValue ->
                            if (newValue.length <= 12 && newValue.all { it.isDigit() }) {
                                pinInput = newValue
                                errorMessage = null
                            }
                        },
                        label = { Text("Enter PIN") },
                        placeholder = { Text("4-6 digits") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitPin() }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide PIN" else "Show PIN"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_prompt_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { submitPin() },
                        enabled = !isLockedOut && pinInput.length >= 4,
                        modifier = Modifier
                            .weight(1.2f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("password_prompt_verify_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Verify PIN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
