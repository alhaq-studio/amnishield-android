package com.alhaq.amnishield.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.R
import com.alhaq.amnishield.data.datastore.AmniShieldDataStore
import com.alhaq.amnishield.data.sync.SupabaseRest
import com.alhaq.amnishield.permissions.PermissionsManager
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.ui.theme.CosmicPrimary
import com.alhaq.amnishield.ui.theme.CosmicPrimaryContainer
import com.alhaq.amnishield.ui.theme.EmeraldPrimary
import com.alhaq.amnishield.ui.theme.EmeraldPrimaryContainer
import com.alhaq.amnishield.ui.theme.SunsetPrimary
import com.alhaq.amnishield.ui.theme.SunsetPrimaryContainer
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.utils.ThemeUtils
import com.alhaq.amnishield.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TOTAL_ONBOARDING_STEPS = 5

/**
 * Clean 5-Step Jetpack Compose Onboarding Experience:
 * Step 1: Welcome, Calming Hero, Theme Preview, Terms Consent & Backup Restore
 * Step 2: Focus Areas (Quick Always-On Shields Starter + Profile, Daily Goal & Trackers)
 * Step 3: Mindful Barriers (Optional 4-Digit Master PIN, Cooldown Delay & Settings Lock)
 * Step 4: Essential Device Permissions + Play-Compliant Disclosures & Android 13+ Guide
 * Step 5: Protection Summary, Community Access, Optional Support Tiers & Launch
 */
@Composable
fun OnboardingFlowHost(
    initialStep: Int = 1,
    onFinishOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = remember { SavedPreferencesLoader(context) }
    val permManager = remember { PermissionsManager(context) }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }
    val appPrefs = remember { context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE) }

    var themeMode by rememberSaveable { mutableStateOf(ThemeUtils.getSelectedThemePref(context)) }
    val resolvedAppTheme = remember(themeMode) { ThemeUtils.resolveAppTheme(context) }

    var currentStep by rememberSaveable { mutableIntStateOf(initialStep.coerceIn(1, TOTAL_ONBOARDING_STEPS)) }

    // Step 1 State
    var tosAccepted by rememberSaveable { mutableStateOf(appPrefs.getBoolean("onboarding_tos_accepted", false)) }

    // Step 2 State: Quick Shields + Identity, Goal & Trackers
    var alwaysOnConfig by remember { mutableStateOf(loader.loadAlwaysOnShieldsConfig()) }
    var quickShieldState by remember {
        mutableStateOf(OnboardingQuickShieldState.fromConfig(alwaysOnConfig))
    }

    fun updateQuickShieldState(transform: (OnboardingQuickShieldState) -> OnboardingQuickShieldState) {
        val nextQuick = transform(quickShieldState)
        quickShieldState = nextQuick
        val updatedConfig = nextQuick.applyToConfig(alwaysOnConfig)
        alwaysOnConfig = updatedConfig
        loader.saveAlwaysOnShieldsConfig(updatedConfig, syncToEngines = true)
    }

    var userName by rememberSaveable { mutableStateOf(loader.getProfileName()) }
    var dailyGoalMinutes by rememberSaveable { mutableIntStateOf(loader.getProfileGoalMinutes()) }
    var appTrackerEnabled by rememberSaveable { mutableStateOf(loader.isAppUsageTrackingEnabled()) }
    var webTrackerEnabled by rememberSaveable { mutableStateOf(loader.isWebsiteUsageTrackingEnabled()) }
    var reelsTrackerEnabled by rememberSaveable { mutableStateOf(loader.isReelsTrackingEnabled()) }

    val fullAppId = remember { loader.getDeviceId() }
    val shortAppId = remember { loader.getShortAppId() }
    var syncStatusText by remember { mutableStateOf("Synced with App ID") }
    var syncJob by remember { mutableStateOf<Job?>(null) }

    fun triggerImmediateIdentitySync(
        newName: String = userName,
        newGoal: Int = dailyGoalMinutes
    ) {
        val cleanName = newName.trim().ifEmpty { "Guardian" }
        loader.setProfileName(cleanName)
        loader.setProfileGoalMinutes(newGoal)
        loader.markIdentitySynced(cleanName, fullAppId)

        syncStatusText = "Syncing..."
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            delay(250L)
            try {
                SupabaseRest().syncUserIdentityWithAppId(
                    context = context,
                    userName = cleanName,
                    goalMinutes = newGoal
                )
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                syncStatusText = "Synced with App ID"
            }
        }
    }

    // Step 3 State: Mindful Barriers & Master PIN
    var pinDraft by remember {
        mutableStateOf(
            OnboardingPinDraft(
                cooldownMinutes = loader.getPinResetCooldownMinutes().coerceIn(2, 30),
                lockSettingsWithPin = true
            )
        )
    }
    var isPinConfigured by rememberSaveable { mutableStateOf(loader.isPinSecurityEnabled()) }

    // Step 4 State: Permissions
    var isAccessibilityGranted by remember { mutableStateOf(permManager.isAccessibilityServiceEnabled()) }
    var isOverlayGranted by remember { mutableStateOf(permManager.isDrawOverOtherAppsEnabled()) }
    var isUsageGranted by remember { mutableStateOf(permManager.isUsageStatsPermissionGranted()) }
    var isBatteryGranted by remember { mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) }
    var isNotifGranted by remember { mutableStateOf(permManager.areNotificationsEnabled()) }

    fun refreshPermissions() {
        isAccessibilityGranted = permManager.isAccessibilityServiceEnabled()
        isOverlayGranted = permManager.isDrawOverOtherAppsEnabled()
        isUsageGranted = permManager.isUsageStatsPermissionGranted()
        isBatteryGranted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        isNotifGranted = permManager.areNotificationsEnabled()
    }

    fun reloadStateAfterBackupRestore() {
        val restoredTheme = ThemeUtils.getSelectedThemePref(context)
        themeMode = restoredTheme
        ThemeUtils.setAppTheme(context, restoredTheme)
        alwaysOnConfig = loader.loadAlwaysOnShieldsConfig()
        quickShieldState = OnboardingQuickShieldState.fromConfig(alwaysOnConfig)
        userName = loader.getProfileName()
        dailyGoalMinutes = loader.getProfileGoalMinutes()
        appTrackerEnabled = loader.isAppUsageTrackingEnabled()
        webTrackerEnabled = loader.isWebsiteUsageTrackingEnabled()
        reelsTrackerEnabled = loader.isReelsTrackingEnabled()
        isPinConfigured = loader.isPinSecurityEnabled()
        tosAccepted = true
        appPrefs.edit().putBoolean("onboarding_tos_accepted", true).apply()
        refreshPermissions()
        triggerImmediateIdentitySync(newName = userName, newGoal = dailyGoalMinutes)
        currentStep = 4
    }

    fun completeOnboardingAndLaunch() {
        triggerImmediateIdentitySync()
        appPrefs.edit().putBoolean("isFirstLaunchComplete", true).apply()
        scope.launch(Dispatchers.IO) {
            runCatching { AmniShieldDataStore.getInstance(context).setFirstLaunchComplete(true) }
        }
        onFinishOnboarding()
    }

    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
        }
    }

    BackHandler(enabled = currentStep > 1) {
        currentStep = (currentStep - 1).coerceAtLeast(1)
    }

    AmniShieldTheme(appTheme = resolvedAppTheme) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                OnboardingTopHeader(
                    currentStep = currentStep,
                    totalSteps = TOTAL_ONBOARDING_STEPS,
                    onBack = { currentStep = (currentStep - 1).coerceAtLeast(1) },
                    onSelectCompletedStep = { target -> currentStep = target }
                )
            },
            bottomBar = {
                OnboardingBottomActionBar(
                    currentStep = currentStep,
                    tosAccepted = tosAccepted,
                    pinMatches = pinDraft.isMatching(),
                    onStep1Continue = {
                        appPrefs.edit().putBoolean("onboarding_tos_accepted", true).apply()
                        currentStep = 2
                    },
                    onStep2Continue = {
                        triggerImmediateIdentitySync()
                        currentStep = 3
                    },
                    onStep3SavePinAndContinue = {
                        if (pinDraft.isMatching()) {
                            loader.setPinCode(pinDraft.pin)
                            loader.setPinSecurityEnabled(true)
                            loader.setBypassPinLockEnabled(pinDraft.lockSettingsWithPin)
                            loader.setPinResetCooldownMinutes(pinDraft.cooldownMinutes)
                            loader.setEmergencyAccessCooldownMinutes(pinDraft.cooldownMinutes)
                            isPinConfigured = true
                            currentStep = 4
                        }
                    },
                    onStep3Skip = { currentStep = 4 },
                    onStep4Continue = { currentStep = 5 },
                    onStep5Launch = ::completeOnboardingAndLaunch
                )
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 3 } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally { width -> -width / 3 } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut(tween(180)))
                    }
                },
                label = "onboardingStepTransition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .widthIn(max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (step) {
                        1 -> OnboardingWelcomeStep(
                            themeMode = themeMode,
                            onThemeModeChanged = { newMode ->
                                themeMode = newMode
                                ThemeUtils.setAppTheme(context, newMode)
                            },
                            tosAccepted = tosAccepted,
                            onTosAcceptedChanged = { accepted ->
                                tosAccepted = accepted
                                appPrefs.edit().putBoolean("onboarding_tos_accepted", accepted).apply()
                            },
                            onBackupRestoredJumpToPermissions = ::reloadStateAfterBackupRestore
                        )
                        2 -> OnboardingFocusAndProfileStep(
                            quickShieldState = quickShieldState,
                            onUpdateQuickShieldState = ::updateQuickShieldState,
                            userName = userName,
                            onUserNameChanged = { newName ->
                                userName = newName
                                triggerImmediateIdentitySync(newName = newName)
                            },
                            dailyGoalMinutes = dailyGoalMinutes,
                            onDailyGoalChanged = { newGoal ->
                                dailyGoalMinutes = newGoal
                                triggerImmediateIdentitySync(newGoal = newGoal)
                            },
                            shortAppId = shortAppId,
                            fullAppId = fullAppId,
                            syncStatusText = syncStatusText,
                            appTrackerEnabled = appTrackerEnabled,
                            onAppTrackerChanged = { enabled ->
                                appTrackerEnabled = enabled
                                loader.setAppUsageTrackingEnabled(enabled)
                            },
                            webTrackerEnabled = webTrackerEnabled,
                            onWebTrackerChanged = { enabled ->
                                webTrackerEnabled = enabled
                                loader.setWebsiteUsageTrackingEnabled(enabled)
                            },
                            reelsTrackerEnabled = reelsTrackerEnabled,
                            onReelsTrackerChanged = { enabled ->
                                reelsTrackerEnabled = enabled
                                loader.setReelsTrackingEnabled(enabled)
                            }
                        )
                        3 -> OnboardingPinAndBarriersStep(
                            pinDraft = pinDraft,
                            alreadyHasPin = isPinConfigured,
                            onUpdateDraft = { updater -> pinDraft = updater(pinDraft) }
                        )
                        4 -> OnboardingPermissionsStep(
                            isAccessibilityGranted = isAccessibilityGranted,
                            isOverlayGranted = isOverlayGranted,
                            isUsageGranted = isUsageGranted,
                            isBatteryGranted = isBatteryGranted,
                            isNotifGranted = isNotifGranted,
                            onRefreshPermissions = ::refreshPermissions,
                            onBackupRestored = ::reloadStateAfterBackupRestore
                        )
                        5 -> OnboardingReadyAndSupportStep(
                            userName = userName,
                            dailyGoalMinutes = dailyGoalMinutes,
                            shortAppId = shortAppId,
                            fullAppId = fullAppId,
                            quickShieldSummary = quickShieldState.summaryText(),
                            isPinEnabled = isPinConfigured,
                            cooldownMinutes = pinDraft.cooldownMinutes,
                            appTrackerEnabled = appTrackerEnabled,
                            webTrackerEnabled = webTrackerEnabled,
                            reelsTrackerEnabled = reelsTrackerEnabled
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopHeader(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onSelectCompletedStep: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (currentStep == 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = "AmniShield",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = "AmniShield",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OnboardingStatusPill(
                    text = "Step 1 of $totalSteps",
                    showDot = true
                )
            }
        } else {
            val stepTitle = when (currentStep) {
                2 -> "Focus & Trackers"
                3 -> "Rules & PIN"
                4 -> "Device Access"
                else -> "Setup Complete"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("onboarding_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = stepTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "$currentStep of $totalSteps",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Segmented pill progress track; completed steps are clickable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (stepIdx in 1..totalSteps) {
                        val isCompleted = stepIdx < currentStep
                        val isCurrent = stepIdx == currentStep
                        val barColor = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(barColor)
                                .then(
                                    if (isCompleted) Modifier.clickable { onSelectCompletedStep(stepIdx) }
                                    else Modifier
                                )
                                .testTag("onboarding_progress_step_$stepIdx")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingBottomActionBar(
    currentStep: Int,
    tosAccepted: Boolean,
    pinMatches: Boolean,
    onStep1Continue: () -> Unit,
    onStep2Continue: () -> Unit,
    onStep3SavePinAndContinue: () -> Unit,
    onStep3Skip: () -> Unit,
    onStep4Continue: () -> Unit,
    onStep5Launch: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (currentStep) {
                1 -> {
                    AnimatedVisibility(visible = !tosAccepted) {
                        Text(
                            text = "Accept the Terms & Privacy Policy above to get started",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(
                        onClick = onStep1Continue,
                        enabled = tosAccepted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_step1_continue"),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Get started",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
                2 -> {
                    Button(
                        onClick = onStep2Continue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_step2_continue"),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Continue",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
                3 -> {
                    Button(
                        onClick = onStep3SavePinAndContinue,
                        enabled = pinMatches,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_step3_save_pin"),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Set PIN & Continue",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                    TextButton(
                        onClick = onStep3Skip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("onboarding_step3_skip"),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Skip for now",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                4 -> {
                    Button(
                        onClick = onStep4Continue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_step4_continue"),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Continue & Shield Focus",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
                else -> {
                    Button(
                        onClick = onStep5Launch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("onboarding_launch_button"),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Open AmniShield",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

private data class ThemeOptionItem(
    val mode: String,
    val label: String,
    val icon: ImageVector,
    val primarySwatch: Color,
    val secondarySwatch: Color
)

@Composable
private fun OnboardingWelcomeStep(
    themeMode: String,
    onThemeModeChanged: (String) -> Unit,
    tosAccepted: Boolean,
    onTosAcceptedChanged: (Boolean) -> Unit,
    onBackupRestoredJumpToPermissions: () -> Unit
) {
    val context = LocalContext.current
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            ZipUtils.unzipSharedPreferencesFromUri(context, uri)
            Toast.makeText(
                context,
                "Backup restored. Review permissions to complete setup.",
                Toast.LENGTH_SHORT
            ).show()
            onBackupRestoredJumpToPermissions()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Concentric calm emblem inspired by the serene sanctuary design
        Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            )
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "AmniShield Emblem",
                    modifier = Modifier.size(66.dp)
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        OnboardingStatusPill(
            text = "Designed for deep cognitive calm",
            icon = Icons.Outlined.VerifiedUser
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Text(
                text = "Reclaim your attention and master your focus.",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    lineHeight = 32.sp
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Shield distracting apps, endless reels, and harmful sites with intentional pauses and 100% on-device privacy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        // Privacy pill
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Your data stays completely private on your device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Clean 2-column tonal highlight cards (borderless Material You depth)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OnboardingHighlightTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Timer,
                title = "Intentional Pauses",
                subtitle = "Mindful breathing & calm shields before scrolling"
            )
            OnboardingHighlightTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Shield,
                title = "PIN & Rules Guard",
                subtitle = "Offline blocklists, schedules & cooldown locks"
            )
        }

        // Compact live Theme Switcher card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Appearance Theme",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val themeOptions = listOf(
                    ThemeOptionItem(
                        mode = ThemeUtils.THEME_SYSTEM,
                        label = "System",
                        icon = Icons.Outlined.BrightnessAuto,
                        primarySwatch = EmeraldPrimary,
                        secondarySwatch = CosmicPrimaryContainer
                    ),
                    ThemeOptionItem(
                        mode = ThemeUtils.THEME_EMERALD,
                        label = "Emerald",
                        icon = Icons.Outlined.LightMode,
                        primarySwatch = EmeraldPrimary,
                        secondarySwatch = EmeraldPrimaryContainer
                    ),
                    ThemeOptionItem(
                        mode = ThemeUtils.THEME_PURPLE,
                        label = "Cosmic",
                        icon = Icons.Outlined.DarkMode,
                        primarySwatch = CosmicPrimary,
                        secondarySwatch = CosmicPrimaryContainer
                    ),
                    ThemeOptionItem(
                        mode = ThemeUtils.THEME_SUNSET,
                        label = "Sunset",
                        icon = Icons.Outlined.WbTwilight,
                        primarySwatch = SunsetPrimary,
                        secondarySwatch = SunsetPrimaryContainer
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themeOptions.forEach { item ->
                        val isSelected = themeMode == item.mode
                        Surface(
                            onClick = { onThemeModeChanged(item.mode) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_theme_${item.label}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .background(item.primarySwatch)
                                            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .background(item.secondarySwatch)
                                            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    )
                                }
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Clean Terms & Privacy Agreement Card
        Surface(
            onClick = { onTosAcceptedChanged(!tosAccepted) },
            shape = RoundedCornerShape(18.dp),
            color = if (tosAccepted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = if (tosAccepted) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_tos_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = tosAccepted,
                        onCheckedChange = onTosAcceptedChanged,
                        modifier = Modifier.testTag("onboarding_tos_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I agree to the AmniShield Terms of Service and 100% On-Device Privacy Policy.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { openExternalUrl(context, Constants.AMNISHIELD_TERMS_URL) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Terms",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        onClick = { openExternalUrl(context, Constants.AMNISHIELD_MOBILE_PRIVACY_URL) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PrivacyTip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Privacy Policy",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Subtle secondary Backup Restore pill button
        Surface(
            onClick = { ZipUtils.showRestorePicker(restoreLauncher) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("onboarding_step1_restore_backup_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I have a backup (.zip)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private enum class OnboardingStep2Section {
    QUICK_SHIELDS,
    PROFILE_AND_GOALS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingFocusAndProfileStep(
    quickShieldState: OnboardingQuickShieldState,
    onUpdateQuickShieldState: ((OnboardingQuickShieldState) -> OnboardingQuickShieldState) -> Unit,
    userName: String,
    onUserNameChanged: (String) -> Unit,
    dailyGoalMinutes: Int,
    onDailyGoalChanged: (Int) -> Unit,
    shortAppId: String,
    fullAppId: String,
    syncStatusText: String,
    appTrackerEnabled: Boolean,
    onAppTrackerChanged: (Boolean) -> Unit,
    webTrackerEnabled: Boolean,
    onWebTrackerChanged: (Boolean) -> Unit,
    reelsTrackerEnabled: Boolean,
    onReelsTrackerChanged: (Boolean) -> Unit
) {
    var activeSection by rememberSaveable { mutableStateOf(OnboardingStep2Section.QUICK_SHIELDS) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Editorial section header
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "PROTECTION & FOCUS GOALS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                text = "Choose what to shield",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Start with quiet always-on guards for short video & harmful sites, or tune your daily goal and trackers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }

        // Clean M3 Segmented Pill Switcher matching app UI
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_step2_segment_row")
        ) {
            val shieldCount = quickShieldState.activeShieldCount()
            SegmentedButton(
                selected = activeSection == OnboardingStep2Section.QUICK_SHIELDS,
                onClick = { activeSection = OnboardingStep2Section.QUICK_SHIELDS },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("onboarding_tab_quick_shields")
            ) {
                Text(
                    text = if (shieldCount > 0) "Quick Shields ($shieldCount)" else "Quick Shields",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SegmentedButton(
                selected = activeSection == OnboardingStep2Section.PROFILE_AND_GOALS,
                onClick = { activeSection = OnboardingStep2Section.PROFILE_AND_GOALS },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("onboarding_tab_profile_goals")
            ) {
                Text(
                    text = "Goal & Trackers",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when (activeSection) {
            OnboardingStep2Section.QUICK_SHIELDS -> {
                Text(
                    text = "Allows normal messaging and browsing while quietly intercepting infinite video loops and blocked domains.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OnboardingCleanToggleRow(
                            icon = Icons.Outlined.SlowMotionVideo,
                            title = "YouTube Shorts",
                            subtitle = "Redirects and mutes endless Shorts feeds",
                            checked = quickShieldState.blockYoutubeShorts,
                            testTag = "onboarding_shield_youtube_shorts",
                            onCheckedChange = { checked ->
                                onUpdateQuickShieldState { it.copy(blockYoutubeShorts = checked) }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                        OnboardingCleanToggleRow(
                            icon = Icons.Outlined.PlayCircle,
                            title = "Instagram Reels",
                            subtitle = "Keeps feed and DMs usable without Reels tabs",
                            checked = quickShieldState.blockInstagramReels,
                            testTag = "onboarding_shield_instagram_reels",
                            onCheckedChange = { checked ->
                                onUpdateQuickShieldState { it.copy(blockInstagramReels = checked) }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                        OnboardingCleanToggleRow(
                            icon = Icons.Outlined.VideoLibrary,
                            title = "TikTok",
                            subtitle = "Interrupts vertical For You feed loops",
                            checked = quickShieldState.blockTikTok,
                            testTag = "onboarding_shield_tiktok",
                            onCheckedChange = { checked ->
                                onUpdateQuickShieldState { it.copy(blockTikTok = checked) }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                        OnboardingCleanToggleRow(
                            icon = Icons.Outlined.Public,
                            title = "In-browser shorts & reels",
                            subtitle = "Blocks web short-video feeds in mobile browsers",
                            checked = quickShieldState.blockBrowserReels,
                            testTag = "onboarding_shield_browser_reels",
                            onCheckedChange = { checked ->
                                onUpdateQuickShieldState { it.copy(blockBrowserReels = checked) }
                            }
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OnboardingCleanToggleRow(
                            icon = Icons.Outlined.Shield,
                            title = "Adult Domains Guard",
                            subtitle = "17,500+ explicit domains filtered offline",
                            badge = "OFFLINE",
                            checked = quickShieldState.blockAdultSites,
                            testTag = "onboarding_shield_adult_sites",
                            onCheckedChange = { checked ->
                                onUpdateQuickShieldState { it.copy(blockAdultSites = checked) }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                        OnboardingCleanToggleRow(
                            icon = Icons.Outlined.Casino,
                            title = "Gambling Domains Guard",
                            subtitle = "278,000+ betting & casino sites filtered offline",
                            badge = "OFFLINE",
                            checked = quickShieldState.blockGamblingSites,
                            testTag = "onboarding_shield_gambling_sites",
                            onCheckedChange = { checked ->
                                onUpdateQuickShieldState { it.copy(blockGamblingSites = checked) }
                            }
                        )
                    }
                }

                // Quiet footer note card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "You can create custom schedules, launch limits, and exceptions anytime in the Blocks tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            OnboardingStep2Section.PROFILE_AND_GOALS -> {
                OnboardingProfileGoalAndTrackersPanel(
                    userName = userName,
                    onUserNameChanged = onUserNameChanged,
                    dailyGoalMinutes = dailyGoalMinutes,
                    onDailyGoalChanged = onDailyGoalChanged,
                    shortAppId = shortAppId,
                    fullAppId = fullAppId,
                    syncStatusText = syncStatusText,
                    appTrackerEnabled = appTrackerEnabled,
                    onAppTrackerChanged = onAppTrackerChanged,
                    webTrackerEnabled = webTrackerEnabled,
                    onWebTrackerChanged = onWebTrackerChanged,
                    reelsTrackerEnabled = reelsTrackerEnabled,
                    onReelsTrackerChanged = onReelsTrackerChanged
                )
            }
        }
    }
}

@Composable
private fun OnboardingProfileGoalAndTrackersPanel(
    userName: String,
    onUserNameChanged: (String) -> Unit,
    dailyGoalMinutes: Int,
    onDailyGoalChanged: (Int) -> Unit,
    shortAppId: String,
    fullAppId: String,
    syncStatusText: String,
    appTrackerEnabled: Boolean,
    onAppTrackerChanged: (Boolean) -> Unit,
    webTrackerEnabled: Boolean,
    onWebTrackerChanged: (Boolean) -> Unit,
    reelsTrackerEnabled: Boolean,
    onReelsTrackerChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val effectiveDisplayName = userName.trim().ifEmpty { "Guardian" }
    var copiedJustNow by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Clean Identity & Bound App ID Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Your Display Name",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = syncStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = userName,
                    onValueChange = onUserNameChanged,
                    label = { Text("Username") },
                    placeholder = { Text("e.g. Guardian") },
                    supportingText = {
                        Text(
                            text = if (userName.isBlank()) {
                                "Defaults to \"Guardian\" if left blank"
                            } else {
                                "Active name: $effectiveDisplayName"
                            }
                        )
                    },
                    trailingIcon = {
                        if (userName.isNotEmpty()) {
                            IconButton(onClick = { onUserNameChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear username")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_username_input")
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_app_id_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "App ID: $shortAppId",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Unique device ID for backup & license passes",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("AmniShield App ID", fullAppId))
                                copiedJustNow = true
                                scope.launch {
                                    delay(2000L)
                                    copiedJustNow = false
                                }
                                Toast.makeText(context, "App ID copied: $shortAppId", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("onboarding_copy_app_id_btn")
                        ) {
                            Icon(
                                imageVector = if (copiedJustNow) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                                contentDescription = "Copy App ID",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Interactive Daily Screen Time Goal Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            val goalDisplay = formatDailyGoalDuration(dailyGoalMinutes)
            val goalMood = resolveDailyGoalMood(dailyGoalMinutes)

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Daily Screen Time Goal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = goalMood,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { onDailyGoalChanged((dailyGoalMinutes - 15).coerceIn(30, 480)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease 15m", modifier = Modifier.size(16.dp))
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = goalDisplay,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onDailyGoalChanged((dailyGoalMinutes + 15).coerceIn(30, 480)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase 15m", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Slider(
                    value = dailyGoalMinutes.toFloat(),
                    onValueChange = { raw ->
                        val snapped = ((raw / 15f).roundToInt() * 15).coerceIn(30, 480)
                        onDailyGoalChanged(snapped)
                    },
                    valueRange = 30f..480f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_goal_slider")
                )

                val presets = listOf(60 to "1h", 120 to "2h", 180 to "3h", 240 to "4h", 360 to "6h")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (minsPreset, label) ->
                        val isSelected = dailyGoalMinutes == minsPreset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onDailyGoalChanged(minsPreset) },
                            shape = CircleShape,
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. Usage & Activity Trackers Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Usage Trackers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Private on-device insights for Stats tab",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OnboardingCleanToggleRow(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "App Usage Tracker",
                    subtitle = "Daily screen time & app launch metrics",
                    checked = appTrackerEnabled,
                    testTag = "onboarding_toggle_app_tracker",
                    onCheckedChange = onAppTrackerChanged
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
                OnboardingCleanToggleRow(
                    icon = Icons.Outlined.Language,
                    title = "Website Usage Tracker",
                    subtitle = "Browser domain activity & trends",
                    checked = webTrackerEnabled,
                    testTag = "onboarding_toggle_web_tracker",
                    onCheckedChange = onWebTrackerChanged
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
                OnboardingCleanToggleRow(
                    icon = Icons.Outlined.Insights,
                    title = "Reels & Shorts Counter",
                    subtitle = "Tracks short videos scrolled across feeds",
                    checked = reelsTrackerEnabled,
                    testTag = "onboarding_toggle_reels_tracker",
                    onCheckedChange = onReelsTrackerChanged
                )
            }
        }
    }
}

@Composable
internal fun OnboardingStatusPill(
    text: String,
    icon: ImageVector? = null,
    showDot: Boolean = false
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun OnboardingHighlightTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun OnboardingCleanToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    badge: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (checked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (badge != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun openExternalUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser application found", Toast.LENGTH_SHORT).show()
    }
}
