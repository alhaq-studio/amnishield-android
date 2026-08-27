package com.alhaq.amnishield.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import kotlinx.coroutines.delay

enum class PinDialogMode {
    VERIFY,
    SETUP,
    RESET_COUNTDOWN,
    NEW_PIN_SETUP
}

@Composable
fun PinPromptDialog(
    correctPin: String = "",
    title: String = "PIN Security",
    subtitle: String = "Enter your 4-digit PIN to proceed",
    isSettingUp: Boolean = false,
    allowForgotPin: Boolean = true,
    onDismiss: () -> Unit,
    onPinSuccess: (String) -> Unit,
    onPinResetRequested: (() -> Unit)? = null,
    onPinResetCompleted: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }
    val isNonDismissible = loader.isPinResetCooldownActive()

    Dialog(
        onDismissRequest = {
            if (!isNonDismissible) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isNonDismissible,
            dismissOnClickOutside = !isNonDismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        PinPromptContent(
            correctPin = correctPin,
            title = title,
            subtitle = subtitle,
            isSettingUp = isSettingUp,
            allowForgotPin = allowForgotPin,
            onDismiss = onDismiss,
            onPinSuccess = onPinSuccess,
            onPinResetRequested = onPinResetRequested,
            onPinResetCompleted = onPinResetCompleted
        )
    }
}

@Composable
fun PinPromptContent(
    correctPin: String = "",
    title: String = "PIN Security",
    subtitle: String = "Enter your 4-digit PIN to proceed",
    isSettingUp: Boolean = false,
    allowForgotPin: Boolean = true,
    onDismiss: () -> Unit,
    onPinSuccess: (String) -> Unit,
    onPinResetRequested: (() -> Unit)? = null,
    onPinResetCompleted: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }

    val initialMode = remember {
        when {
            loader.isPinResetReady() -> PinDialogMode.NEW_PIN_SETUP
            loader.isPinResetCooldownActive() -> PinDialogMode.RESET_COUNTDOWN
            isSettingUp -> PinDialogMode.SETUP
            else -> PinDialogMode.VERIFY
        }
    }

    var currentMode by remember { mutableStateOf(initialMode) }
    var pinText by remember { mutableStateOf("") }
    var firstEnteredPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var remainingMillis by remember { mutableStateOf(loader.getPinResetRemainingMillis()) }

    // Live countdown ticker if in countdown mode
    LaunchedEffect(currentMode, remainingMillis) {
        if (currentMode == PinDialogMode.RESET_COUNTDOWN) {
            while (remainingMillis > 0L) {
                delay(1000L)
                remainingMillis = loader.getPinResetRemainingMillis()
            }
            if (loader.isPinResetReady()) {
                currentMode = PinDialogMode.NEW_PIN_SETUP
                pinText = ""
                errorText = ""
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .wrapContentHeight()
            .clip(RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
            AnimatedContent(
                targetState = currentMode,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "PinDialogState"
            ) { mode ->
                when (mode) {
                    PinDialogMode.RESET_COUNTDOWN -> {
                        PinCountdownContent(
                            remainingMillis = remainingMillis,
                            cooldownMinutes = loader.getPinResetCooldownMinutes(),
                            onCancelReset = {
                                loader.clearPinResetRequest()
                                currentMode = if (isSettingUp) PinDialogMode.SETUP else PinDialogMode.VERIFY
                                pinText = ""
                                errorText = ""
                            }
                        )
                    }

                    PinDialogMode.NEW_PIN_SETUP, PinDialogMode.SETUP -> {
                        PinKeypadContent(
                            title = if (mode == PinDialogMode.NEW_PIN_SETUP) "Set New 4-Digit PIN" else if (firstEnteredPin.isEmpty()) "Setup 4-Digit PIN" else "Confirm New PIN",
                            subtitle = if (mode == PinDialogMode.NEW_PIN_SETUP) "Security reset complete. Create a new secure PIN." else if (firstEnteredPin.isEmpty()) "Create a 4-digit PIN to secure your settings." else "Re-enter the same 4-digit PIN to confirm.",
                            pinText = pinText,
                            errorText = errorText,
                            showForgotPin = false,
                            onCharClick = { char ->
                                if (pinText.length < 4) {
                                    pinText += char
                                    errorText = ""
                                    if (pinText.length == 4) {
                                        if (firstEnteredPin.isEmpty()) {
                                            firstEnteredPin = pinText
                                            pinText = ""
                                        } else {
                                            if (pinText == firstEnteredPin) {
                                                loader.setPinCode(pinText)
                                                loader.clearPinResetRequest()
                                                onPinResetCompleted?.invoke(pinText) ?: onPinSuccess(pinText)
                                            } else {
                                                pinText = ""
                                                errorText = "PINs do not match. Try again."
                                                firstEnteredPin = ""
                                            }
                                        }
                                    }
                                }
                            },
                            onDeleteClick = {
                                if (pinText.isNotEmpty()) pinText = pinText.dropLast(1)
                            },
                            onClearClick = {
                                pinText = ""
                                errorText = ""
                            },
                            onCancelClick = onDismiss,
                            onForgotPinClick = {}
                        )
                    }

                    PinDialogMode.VERIFY -> {
                        PinKeypadContent(
                            title = title,
                            subtitle = subtitle,
                            pinText = pinText,
                            errorText = errorText,
                            showForgotPin = allowForgotPin,
                            onCharClick = { char ->
                                if (pinText.length < 4) {
                                    pinText += char
                                    errorText = ""
                                    if (pinText.length == 4) {
                                        val expectedPin = correctPin.ifEmpty { loader.getPinCode() }
                                        if (pinText == expectedPin) {
                                            onPinSuccess(pinText)
                                        } else {
                                            pinText = ""
                                            errorText = "Incorrect PIN code"
                                        }
                                    }
                                }
                            },
                            onDeleteClick = {
                                if (pinText.isNotEmpty()) pinText = pinText.dropLast(1)
                            },
                            onClearClick = {
                                pinText = ""
                                errorText = ""
                            },
                            onCancelClick = onDismiss,
                            onForgotPinClick = {
                                loader.requestPinReset()
                                remainingMillis = loader.getPinResetRemainingMillis()
                                currentMode = PinDialogMode.RESET_COUNTDOWN
                                onPinResetRequested?.invoke()
                            }
                        )
                    }
                }
            }
        }
    }

@Composable
private fun PinCountdownContent(
    remainingMillis: Long,
    cooldownMinutes: Int,
    onCancelReset: () -> Unit
) {
    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
    val minutes = (totalSeconds / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    val progress = (remainingMillis.toFloat() / (cooldownMinutes * 60 * 1000f)).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Security Reset Pending",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Protection will unlock in %02d:%02d".format(minutes, seconds),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(14.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "All blockers & Anti-Uninstall protections remain 100% active during the cooldown window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(
            onClick = onCancelReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Cancel Reset Request",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PinKeypadContent(
    title: String,
    subtitle: String,
    pinText: String,
    errorText: String,
    showForgotPin: Boolean,
    onCharClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClearClick: () -> Unit,
    onCancelClick: () -> Unit,
    onForgotPinClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 4 Pin Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { index ->
                val hasChar = index < pinText.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasChar) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        if (errorText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val buttons = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("Clear", "0", "Delete")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            buttons.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    row.forEach { char ->
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(
                                    if (char == "Clear" || char == "Delete") Color.Transparent
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
                                .clickable {
                                    when (char) {
                                        "Clear" -> onClearClick()
                                        "Delete" -> onDeleteClick()
                                        else -> onCharClick(char)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                style = if (char == "Clear" || char == "Delete") MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (char == "Clear") MaterialTheme.colorScheme.error
                                else if (char == "Delete") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancelClick) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (showForgotPin) {
                TextButton(onClick = onForgotPinClick) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Forgot PIN?",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
