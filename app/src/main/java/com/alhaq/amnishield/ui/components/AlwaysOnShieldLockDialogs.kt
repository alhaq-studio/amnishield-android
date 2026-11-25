package com.alhaq.amnishield.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alhaq.amnishield.data.blockers.*
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthResult
import com.alhaq.amnishield.security.ShieldAuthTarget
import com.alhaq.amnishield.utils.NotificationHelper
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Unlock Protection selector for an Always-On Shield.
 *
 * Free modes: Global PIN (inherit), Pause Delay, No Lock.
 * Pro modes (gated on selection/configuration only): Custom PIN, QR Code.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShieldLockSelectorDialog(
    shieldType: ShieldType,
    currentConfig: ShieldLockConfig,
    isPremiumUser: Boolean,
    hasGlobalPin: Boolean,
    onDismiss: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onSaveLockConfig: (ShieldLockConfig) -> Unit
) {
    val context = LocalContext.current
    val authResolver = remember { AuthResolver.getInstance(context) }

    var selectedMode by remember(currentConfig) { mutableStateOf(currentConfig.mode) }
    var delaySeconds by remember(currentConfig) { mutableIntStateOf(currentConfig.delaySeconds.coerceIn(5, 300)) }
    var localHasGlobalPin by remember(hasGlobalPin) { mutableStateOf(hasGlobalPin) }
    var globalPinInput by remember { mutableStateOf("") }
    var globalPinConfirm by remember { mutableStateOf("") }
    var globalPinVisible by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var registeredQr by remember(currentConfig) { mutableStateOf(currentConfig.registeredQrValue.orEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val qrRegisterLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents?.trim()
        if (!contents.isNullOrBlank()) {
            registeredQr = contents
            validationError = null
        }
    }

    fun handleSave() {
        // Safeguard #4: Only gate selecting/configuring CUSTOM_PIN and QR_CODE behind Pro.
        val isSelectingNewProMode = selectedMode.isProOnly &&
            (selectedMode != currentConfig.mode ||
                (selectedMode == ShieldLockMode.CUSTOM_PIN && pinInput.isNotEmpty()) ||
                (selectedMode == ShieldLockMode.QR_CODE && registeredQr != currentConfig.registeredQrValue.orEmpty()))

        if (isSelectingNewProMode && !isPremiumUser) {
            onDismiss()
            onNavigateToPremium()
            return
        }

        when (selectedMode) {
            ShieldLockMode.NONE -> {
                onSaveLockConfig(ShieldLockConfig(mode = ShieldLockMode.NONE))
            }
            ShieldLockMode.INHERIT_GLOBAL_PIN -> {
                if (!localHasGlobalPin) {
                    if (globalPinInput.length < 4) {
                        validationError = "Global Anti-Bypass PIN must be 4-8 digits."
                        return
                    }
                    if (globalPinInput != globalPinConfirm) {
                        validationError = "Global Anti-Bypass PINs do not match."
                        return
                    }
                    com.alhaq.amnishield.utils.SavedPreferencesLoader(context)
                        .saveGlobalAntiBypassPin(globalPinInput, linkAntiUninstall = true)
                    localHasGlobalPin = true
                }
                onSaveLockConfig(ShieldLockConfig(mode = ShieldLockMode.INHERIT_GLOBAL_PIN))
            }
            ShieldLockMode.PAUSE_DELAY -> {
                onSaveLockConfig(
                    ShieldLockConfig(
                        mode = ShieldLockMode.PAUSE_DELAY,
                        delaySeconds = delaySeconds.coerceIn(5, 300)
                    )
                )
            }
            ShieldLockMode.CUSTOM_PIN -> {
                val keepExisting = pinInput.isEmpty() &&
                    confirmPinInput.isEmpty() &&
                    !currentConfig.customPinHash.isNullOrBlank() &&
                    !currentConfig.customPinSalt.isNullOrBlank()
                if (keepExisting) {
                    onSaveLockConfig(
                        currentConfig.copy(mode = ShieldLockMode.CUSTOM_PIN)
                    )
                } else {
                    if (pinInput.length < 4) {
                        validationError = "PIN must be at least 4 digits."
                        return
                    }
                    if (pinInput != confirmPinInput) {
                        validationError = "PINs do not match."
                        return
                    }
                    val (hash, salt) = authResolver.createRulePin(pinInput)
                    onSaveLockConfig(
                        ShieldLockConfig(
                            mode = ShieldLockMode.CUSTOM_PIN,
                            customPinHash = hash,
                            customPinSalt = salt
                        )
                    )
                }
            }
            ShieldLockMode.QR_CODE -> {
                if (registeredQr.isBlank()) {
                    validationError = "Scan a QR or barcode to register your unlock key."
                    return
                }
                onSaveLockConfig(
                    ShieldLockConfig(
                        mode = ShieldLockMode.QR_CODE,
                        registeredQrValue = registeredQr
                    )
                )
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
                .testTag("shield_lock_selector_dialog"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unlock Protection",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${shieldType.pillLabel} - Required before turning off or weakening",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ShieldLockOptionRow(
                    title = "Global Anti-Bypass PIN",
                    subtitle = if (localHasGlobalPin) {
                        "Uses your Global Anti-Bypass PIN (Free)"
                    } else {
                        "Tap below to create a Global Anti-Bypass PIN right now (Free)"
                    },
                    icon = Icons.Outlined.Shield,
                    isSelected = selectedMode == ShieldLockMode.INHERIT_GLOBAL_PIN,
                    isProLocked = false,
                    onClick = {
                        selectedMode = ShieldLockMode.INHERIT_GLOBAL_PIN
                        validationError = null
                    }
                )

                AnimatedVisibility(visible = selectedMode == ShieldLockMode.INHERIT_GLOBAL_PIN && !localHasGlobalPin) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Create Global Anti-Bypass PIN",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Protects this shield, Settings, and Anti-Uninstall tamper guard.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = globalPinInput,
                                onValueChange = { next ->
                                    if (next.length <= 8 && next.all { it.isDigit() }) {
                                        globalPinInput = next
                                        validationError = null
                                    }
                                },
                                label = { Text("Enter Global PIN (4-8 digits)") },
                                singleLine = true,
                                visualTransformation = if (globalPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Next
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { globalPinVisible = !globalPinVisible }) {
                                        Icon(
                                            imageVector = if (globalPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Global PIN visibility"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_global_pin_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = globalPinConfirm,
                                onValueChange = { next ->
                                    if (next.length <= 8 && next.all { it.isDigit() }) {
                                        globalPinConfirm = next
                                        validationError = null
                                    }
                                },
                                label = { Text("Confirm Global PIN") },
                                singleLine = true,
                                visualTransformation = if (globalPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_global_pin_confirm_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                ShieldLockOptionRow(
                    title = "Pause Delay",
                    subtitle = "Enforce a ${delaySeconds}s mindful countdown before unlocking (Free)",
                    icon = Icons.Outlined.Timer,
                    isSelected = selectedMode == ShieldLockMode.PAUSE_DELAY,
                    isProLocked = false,
                    onClick = {
                        selectedMode = ShieldLockMode.PAUSE_DELAY
                        validationError = null
                    }
                )

                AnimatedVisibility(visible = selectedMode == ShieldLockMode.PAUSE_DELAY) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        listOf(15, 30, 60, 120).forEach { secs ->
                            FilterChip(
                                selected = delaySeconds == secs,
                                onClick = { delaySeconds = secs },
                                label = { Text("${secs}s") }
                            )
                        }
                    }
                }

                ShieldLockOptionRow(
                    title = "Custom Shield PIN",
                    subtitle = "Dedicated PIN exclusively for ${shieldType.pillLabel}",
                    icon = Icons.Outlined.Pin,
                    isSelected = selectedMode == ShieldLockMode.CUSTOM_PIN,
                    isProLocked = !isPremiumUser,
                    onClick = {
                        if (!isPremiumUser && currentConfig.mode != ShieldLockMode.CUSTOM_PIN) {
                            onDismiss()
                            onNavigateToPremium()
                        } else {
                            selectedMode = ShieldLockMode.CUSTOM_PIN
                            validationError = null
                        }
                    }
                )

                AnimatedVisibility(visible = selectedMode == ShieldLockMode.CUSTOM_PIN) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { next ->
                                if (next.length <= 8 && next.all { it.isDigit() }) {
                                    pinInput = next
                                    validationError = null
                                }
                            },
                            label = { Text("Enter Custom PIN (4-8 digits)") },
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
                                        contentDescription = "Toggle PIN visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = confirmPinInput,
                            onValueChange = { next ->
                                if (next.length <= 8 && next.all { it.isDigit() }) {
                                    confirmPinInput = next
                                    validationError = null
                                }
                            },
                            label = { Text("Confirm Custom PIN") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                ShieldLockOptionRow(
                    title = "QR / Barcode Key",
                    subtitle = if (registeredQr.isNotBlank()) "QR key registered" else "Scan a physical QR or barcode to unlock",
                    icon = Icons.Outlined.QrCodeScanner,
                    isSelected = selectedMode == ShieldLockMode.QR_CODE,
                    isProLocked = !isPremiumUser,
                    onClick = {
                        if (!isPremiumUser && currentConfig.mode != ShieldLockMode.QR_CODE) {
                            onDismiss()
                            onNavigateToPremium()
                        } else {
                            selectedMode = ShieldLockMode.QR_CODE
                            validationError = null
                        }
                    }
                )

                AnimatedVisibility(visible = selectedMode == ShieldLockMode.QR_CODE) {
                    OutlinedButton(
                        onClick = {
                            val opts = ScanOptions().apply {
                                setPrompt("Scan QR or Barcode to register as unlock key")
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                            qrRegisterLauncher.launch(opts)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (registeredQr.isBlank()) "Scan QR / Barcode" else "Re-scan QR / Barcode")
                    }
                }

                ShieldLockOptionRow(
                    title = "No Lock",
                    subtitle = "Allow changes immediately without unlock friction",
                    icon = Icons.Outlined.LockOpen,
                    isSelected = selectedMode == ShieldLockMode.NONE,
                    isProLocked = false,
                    onClick = {
                        selectedMode = ShieldLockMode.NONE
                        validationError = null
                    }
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            .testTag("save_shield_lock_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Lock", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShieldLockOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    isProLocked: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
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
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isProLocked) {
                        RuleProBadge()
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Unlock Challenge dialog for weakening or turning off a protected Always-On Shield.
 *
 * Safeguard #4: Always allows passing an existing CUSTOM_PIN or QR_CODE challenge even if Pro expired.
 */
@Composable
fun ShieldUnlockChallengeDialog(
    shieldType: ShieldType,
    lockConfig: ShieldLockConfig,
    actionDescription: String,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val authResolver = remember { AuthResolver.getInstance(context) }
    val target = remember(lockConfig) { authResolver.resolveShieldChallenge(lockConfig) }

    LaunchedEffect(target) {
        if (target is ShieldAuthTarget.PassThrough) {
            onUnlocked()
        }
    }
    if (target is ShieldAuthTarget.PassThrough) return

    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var remainingDelaySecs by remember(target) {
        mutableIntStateOf(if (target is ShieldAuthTarget.RequireDelay) target.delaySeconds else 0)
    }
    var lockoutRemainingMs by remember(target) {
        mutableLongStateOf(if (target is ShieldAuthTarget.LockedOut) target.remainingMillis else 0L)
    }

    LaunchedEffect(target, remainingDelaySecs) {
        if (target is ShieldAuthTarget.RequireDelay && remainingDelaySecs > 0) {
            delay(1_000L)
            remainingDelaySecs -= 1
        }
    }

    LaunchedEffect(lockoutRemainingMs) {
        if (lockoutRemainingMs > 0L) {
            delay(1_000L)
            lockoutRemainingMs = authResolver.getLockoutRemainingMillis()
        }
    }

    fun completeUnlock() {
        runCatching {
            NotificationHelper.getInstance(context).showShieldUnlockAlert(
                shieldName = shieldType.displayName,
                actionDetail = actionDescription
            )
        }
        onUnlocked()
    }

    val qrVerifyLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents?.trim()
        if (!contents.isNullOrBlank() && target is ShieldAuthTarget.RequireQr) {
            if (contents == target.expectedValue) {
                completeUnlock()
            } else {
                errorMessage = "Scanned QR/barcode does not match the registered unlock key."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Unlock ${shieldType.pillLabel}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = actionDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    lockoutRemainingMs > 0L -> {
                        val secs = ((lockoutRemainingMs + 999L) / 1000L).toInt()
                        Text(
                            text = "Too many failed attempts. Try again in ${secs}s.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    target is ShieldAuthTarget.RequireGlobalPin || target is ShieldAuthTarget.RequireCustomPin -> {
                        val label = if (target is ShieldAuthTarget.RequireGlobalPin) {
                            "Enter Global Master PIN"
                        } else {
                            "Enter Custom Shield PIN"
                        }
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { next ->
                                if (next.length <= 12) {
                                    pinInput = next
                                    errorMessage = null
                                }
                            },
                            label = { Text(label) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            isError = errorMessage != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("shield_unlock_pin_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    target is ShieldAuthTarget.RequireDelay -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (remainingDelaySecs > 0) "${remainingDelaySecs}s" else "Ready",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (remainingDelaySecs > 0) {
                                    "Pause and confirm you really want to weaken protection."
                                } else {
                                    "Countdown complete. Tap Confirm Unlock below."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    target is ShieldAuthTarget.RequireQr -> {
                        Button(
                            onClick = {
                                val opts = ScanOptions().apply {
                                    setPrompt("Scan your registered QR/Barcode to unlock")
                                    setBeepEnabled(false)
                                    setOrientationLocked(false)
                                }
                                qrVerifyLauncher.launch(opts)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Unlock QR / Barcode")
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            when {
                lockoutRemainingMs > 0L -> {}
                target is ShieldAuthTarget.RequireGlobalPin || target is ShieldAuthTarget.RequireCustomPin -> {
                    Button(
                        onClick = {
                            when (val result = authResolver.verifyShieldPin(target, pinInput)) {
                                is AuthResult.Success -> completeUnlock()
                                is AuthResult.InvalidPin -> {
                                    pinInput = ""
                                    if (result.isLockedOut) {
                                        lockoutRemainingMs = result.lockoutRemainingMillis
                                        errorMessage = "Too many failed attempts. Locked temporarily."
                                    } else {
                                        errorMessage = "Incorrect PIN (${result.attemptsRemaining} attempts left)."
                                    }
                                }
                                is AuthResult.LockedOut -> {
                                    lockoutRemainingMs = result.remainingMillis
                                    errorMessage = "Locked temporarily due to failed attempts."
                                }
                            }
                        },
                        enabled = pinInput.isNotEmpty(),
                        modifier = Modifier.testTag("shield_unlock_confirm_btn")
                    ) {
                        Text("Unlock")
                    }
                }
                target is ShieldAuthTarget.RequireDelay -> {
                    Button(
                        onClick = { completeUnlock() },
                        enabled = remainingDelaySecs <= 0,
                        modifier = Modifier.testTag("shield_unlock_delay_confirm_btn")
                    ) {
                        Text(if (remainingDelaySecs > 0) "Wait ${remainingDelaySecs}s" else "Confirm Unlock")
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * In-screen Compose ModalBottomSheet to manage Always-On Website Guard domain packs and custom domains.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageShieldDomainsSheet(
    config: WebsiteGuardConfig,
    onDismiss: () -> Unit,
    onAddDomain: (String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    onTogglePack: (String, Boolean) -> Unit
) {
    var domainInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val filteredCustomDomains = remember(config.customDomains, searchQuery) {
        if (searchQuery.isBlank()) config.customDomains
        else config.customDomains.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("manage_shield_domains_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Manage Blocked Domains",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${config.effectiveDomains().size} active always-blocked domains",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }

            // Custom domain add row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = {
                        domainInput = it
                        inputError = null
                    },
                    placeholder = { Text("e.g. reddit.com, youtube.com") },
                    singleLine = true,
                    isError = inputError != null,
                    supportingText = inputError?.let { err -> { Text(err) } },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shield_add_domain_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        val normalized = WebsiteGuardConfig.normalizeDomainInput(domainInput)
                        if (normalized == null) {
                            inputError = "Enter a valid domain (e.g. example.com)"
                        } else {
                            onAddDomain(normalized)
                            domainInput = ""
                            inputError = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("shield_add_domain_btn")
                ) {
                    Text("Add")
                }
            }

            // Built-in Domain Packs (accurate counts from PreloadedPacks.websitePacks)
            Text(
                text = "Built-in Domain Blocklists",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            PreloadedPacks.websitePacks.forEach { pack ->
                val isPackOn = pack.id in config.enabledPackIds
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isPackOn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (isPackOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = pack.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${pack.title} (${pack.items.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = pack.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = isPackOn,
                            onCheckedChange = { checked -> onTogglePack(pack.id, checked) },
                            modifier = Modifier.testTag("shield_web_pack_${pack.id}")
                        )
                    }
                }
            }

            // Custom domains list
            if (config.customDomains.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Custom Domains (${config.customDomains.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (config.customDomains.size > 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter custom domains...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredCustomDomains.forEach { domain ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveDomain(domain) },
                            label = { Text(domain) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove $domain",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * In-screen Compose ModalBottomSheet to manage Always-On Keyword Filter packs and custom terms.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageShieldKeywordsSheet(
    config: KeywordGuardConfig,
    onDismiss: () -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onTogglePack: (String, Boolean) -> Unit
) {
    var keywordInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val filteredCustomKeywords = remember(config.customKeywords, searchQuery) {
        if (searchQuery.isBlank()) config.customKeywords
        else config.customKeywords.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("manage_shield_keywords_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Manage Blocked Keywords",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${config.effectiveKeywords().size} active filtered search terms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = {
                        keywordInput = it
                        inputError = null
                    },
                    placeholder = { Text("Enter keyword or phrase...") },
                    singleLine = true,
                    isError = inputError != null,
                    supportingText = inputError?.let { err -> { Text(err) } },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shield_add_keyword_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        val cleaned = keywordInput.trim().lowercase(Locale.ROOT)
                        if (cleaned.length < 2) {
                            inputError = "Enter at least 2 characters"
                        } else {
                            onAddKeyword(cleaned)
                            keywordInput = ""
                            inputError = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("shield_add_keyword_btn")
                ) {
                    Text("Add")
                }
            }

            Text(
                text = "Curated Keyword Packs",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            PreloadedPacks.keywordPacks.forEach { pack ->
                val isPackOn = pack.id in config.enabledPackIds
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isPackOn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (isPackOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = pack.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${pack.title} (${pack.items.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = pack.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = isPackOn,
                            onCheckedChange = { checked -> onTogglePack(pack.id, checked) },
                            modifier = Modifier.testTag("shield_kw_pack_${pack.id}")
                        )
                    }
                }
            }

            if (config.customKeywords.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Custom Keywords (${config.customKeywords.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (config.customKeywords.size > 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter custom keywords...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredCustomKeywords.forEach { kw ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveKeyword(kw) },
                            label = { Text(kw) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove $kw",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * In-screen Compose ModalBottomSheet to manage whitelisted apps excluded from Keyword Guard scanning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldIgnoredAppsSheet(
    ignoredPackages: Set<String>,
    onDismiss: () -> Unit,
    onToggleIgnoredApp: (packageName: String, nowIgnored: Boolean) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = info.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
                    pkg to label
                }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase(Locale.ROOT) }
        }
    }

    val filtered = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter { (pkg, name) ->
            name.contains(searchQuery.trim(), ignoreCase = true) ||
                pkg.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("shield_ignored_apps_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ignored Apps Whitelist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${ignoredPackages.size} app(s) excluded from Keyword Filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.first }) { (pkg, appName) ->
                    val isIgnored = pkg in ignoredPackages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleIgnoredApp(pkg, !isIgnored) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = pkg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Checkbox(
                            checked = isIgnored,
                            onCheckedChange = { checked -> onToggleIgnoredApp(pkg, checked) }
                        )
                    }
                }
            }
        }
    }
}
