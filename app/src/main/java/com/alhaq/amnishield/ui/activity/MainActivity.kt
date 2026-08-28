package com.alhaq.amnishield.ui.activity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.alhaq.amnishield.AmniShield
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.R
import com.alhaq.amnishield.utils.BillingClientWrapper
import com.alhaq.amnishield.data.AmniShieldAccount
import com.alhaq.amnishield.premium.LicenseValidator
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.AmniShieldAdaptiveApp
import com.alhaq.amnishield.ui.components.PinPromptContent
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import com.alhaq.amnishield.utils.ErrorReportManager
import com.alhaq.amnishield.utils.GoogleSignInHelper
import com.alhaq.amnishield.utils.NotificationTimerManager
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.utils.ThemeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    data class WarningData(
        val message: String = "You can setup a custom message to appear here!",
        val timeInterval: Int = 120000, // default cooldown period
        val isDynamicIntervalSettingAllowed: Boolean = false,
        val isProceedDisabled: Boolean = false,
        val isWarningDialogHidden: Boolean = false, // perform back/home action directly without showing warning screen
        val proceedDelayInSecs: Int = 15
    )

    // Legacy compatibility methods for XML fragments (no-op in Compose)
    fun setBottomNavVisible(visible: Boolean) {}
    fun setToolbarVisible(visible: Boolean) {}
    fun selectTab(tabId: Int) {}
    fun getSelectedTabId(): Int = 0

    private var googleSignInHelper: GoogleSignInHelper? = null
    private val savedPreferencesLoader by lazy { SavedPreferencesLoader(this) }
    private val premiumManager by lazy { PremiumManager.getInstance(this) }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val account = googleSignInHelper?.handleSignInResult(result.data)
            if (account != null) {
                Toast.makeText(this, getString(R.string.signed_in_as, account.email), Toast.LENGTH_SHORT).show()
                syncAccountWithBackend(account)
            } else {
                Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncAccountWithBackend(account: AmniShieldAccount) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rest = com.alhaq.amnishield.data.sync.SupabaseRest()
                var profile: com.alhaq.amnishield.data.sync.SupabaseRest.UserProfile? = null

                if (!account.idToken.isNullOrBlank()) {
                    try {
                        val session = rest.signInWithGoogleIdToken(account.idToken)
                        if (session != null) {
                            profile = rest.fetchProfile(session)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Supabase id_token exchange failed, falling back to email query", e)
                    }
                }

                if (profile == null && !account.email.isNullOrBlank()) {
                    try {
                        profile = rest.fetchProfileByEmail(account.email)
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Fetch profile by email failed", e)
                    }
                }

                if (profile != null) {
                    val key = profile.licenseKey
                    if (!key.isNullOrBlank()) {
                        val activated = premiumManager.redeemLicenseKey(key)
                        if (activated) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Pro License Synced and Activated from Account!", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else if (profile.isPremium) {
                        premiumManager.updatePremiumStatus(true)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "AmniShield Pro Status Synced!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Background account sync error", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            ThemeUtils.applyTheme(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "ThemeUtils.applyTheme failed", e)
        }
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[AmniShieldViewModel::class.java]

        // Initialize Google Sign In helper safely
        try {
            googleSignInHelper = GoogleSignInHelper(this)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Failed to initialize GoogleSignInHelper", e)
        }

        // Initialize notification channels
        try {
            com.alhaq.amnishield.utils.NotificationHelper.getInstance(this)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Failed to initialize notification channels", e)
        }
        
        // Restore premium purchases automatically on app start
        try {
            restorePremiumPurchases()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Failed to restore premium purchases", e)
        }

        // Schedule background policy & rules sync engine
        try {
            com.alhaq.amnishield.data.sync.SyncWorker.schedule(this)
            lifecycleScope.launch(Dispatchers.IO) {
                com.alhaq.amnishield.data.sync.PolicySyncManager.syncNow(this@MainActivity)
            }
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Failed to initialize SyncWorker", e)
        }

        if (isFirstLaunchComplete()) {
            viewModel.completeSetup()
        }

        setContent {
            val state by viewModel.state.collectAsState()
            val activeTheme = ThemeUtils.resolveAppTheme(this)
            AmniShieldTheme(appTheme = activeTheme) {
                AmniShieldAdaptiveApp(
                    viewModel = viewModel,
                    state = state,
                    isGoogleSignedIn = googleSignInHelper?.getLastSignedInAccount() != null,
                    onGoogleSignIn = {
                        try {
                            googleSignInHelper?.getSignInIntent()?.let { googleSignInLauncher.launch(it) }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Sign-in error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onGoogleSignOut = {
                        googleSignInHelper?.signOut {
                            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenAccessibilitySettings = {
                        try {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (e: Exception) {
                            // ignore
                        }
                    },
                    onStartFocusMode = { mode, minutes, apps ->
                        startFocusSessionFromAdaptive(minutes, mode, apps)
                    },
                    onStopFocusMode = {
                        stopFocusSessionFromAdaptive()
                    },
                    onBackupRestore = {
                        try {
                            val intent = Intent(this, FragmentActivity::class.java).apply {
                                putExtra("fragment", "settings")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            // ignore
                        }
                    },
                    onShareCrashLogs = {
                        try {
                            val reportFile = ErrorReportManager.getInstance(this).createBundledReportFile()
                            if (reportFile != null) {
                                val uri = FileProvider.getUriForFile(this, "$packageName.provider", reportFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "Share Diagnostics"))
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Failed to share diagnostics", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onHelpFAQ = {
                        showFAQDialog()
                    },
                    onAbout = {
                        showAboutDialog()
                    },
                    onLanguage = {}
                )
            }
        }

        showDonationDialog()
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        checkAppLock()
        maybeShowPremiumReminder()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun startFocusSessionFromAdaptive(durationMinutes: Int, mode: Int, selectedApps: Set<String>) {
        val durationMillis = durationMinutes * 60_000L
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMillis
        
        savedPreferencesLoader.saveFocusModeSelectedApps(selectedApps.toList())
        savedPreferencesLoader.saveFocusModeData(
            com.alhaq.amnishield.blockers.FocusModeBlocker.FocusModeData(
                isTurnedOn = true,
                endTime = endTime,
                modeType = mode,
                selectedApps = HashSet(selectedApps)
            )
        )
        savedPreferencesLoader.saveFocusSessionStartTime(startTime, endTime)
        
        val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        
        val timer = NotificationTimerManager(this)
        timer.startTimer(
            totalMillis = durationMillis,
            onFinishCallback = {
                stopFocusSessionFromAdaptive()
            }
        )
    }

    private fun stopFocusSessionFromAdaptive() {
        savedPreferencesLoader.saveFocusModeData(
            com.alhaq.amnishield.blockers.FocusModeBlocker.FocusModeData(
                isTurnedOn = false,
                endTime = 0,
                modeType = 0,
                selectedApps = HashSet()
            )
        )
        val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        val timer = NotificationTimerManager(this)
        timer.stopTimer()
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase().orEmpty()
        val host = data.host?.lowercase().orEmpty()
        val path = data.path?.lowercase().orEmpty()

        val isAmnScheme = scheme == "amnishield"
        val isWebActivation = (scheme == "https" || scheme == "http") &&
                (host.contains("amnishield.com") || host.contains("amnishield.org")) &&
                (path.contains("activate") || path.contains("license") || path.contains("auth") || path.contains("verify") || path.contains("pair") || host == "pair" || data.getQueryParameter("key") != null || data.getQueryParameter("token") != null || data.getQueryParameter("code") != null || data.getQueryParameter("pin") != null)

        if (isAmnScheme || isWebActivation) {
            val isPairRequest = host == "pair" || path.contains("pair") || (data.getQueryParameter("owner") != null && data.getQueryParameter("token") != null)
            val pairPin = data.getQueryParameter("token") ?: data.getQueryParameter("pin") ?: data.getQueryParameter("code")

            // 0. Ephemeral Device Pairing via Web Console QR Code or PIN Link
            if (isPairRequest && !pairPin.isNullOrBlank()) {
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                Toast.makeText(this, "Linking device with PIN: $pairPin...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val rest = com.alhaq.amnishield.data.sync.SupabaseRest()
                        val result = rest.claimPairingToken(pairPin, deviceName, "android")
                        withContext(Dispatchers.Main) {
                            if (result.success) {
                                val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
                                prefs.edit()
                                    .putString("paired_device_id", result.deviceId)
                                    .putString("paired_owner_id", result.ownerId)
                                    .putBoolean("is_paired_with_console", true)
                                    .apply()
                                Toast.makeText(this@MainActivity, "Device Linked to Cloud Sync Hub!", Toast.LENGTH_LONG).show()
                                showDevicePairingSuccessDialog(pairPin, deviceName, result.isManaged)
                            } else {
                                Toast.makeText(this@MainActivity, "Pairing Failed: ${result.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Pairing error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                return
            }

            val key = data.getQueryParameter("key")
                ?: data.getQueryParameter("license")
                ?: data.getQueryParameter("license_key")

            val token = data.getQueryParameter("token")
                ?: data.getQueryParameter("code")

            val email = data.getQueryParameter("email")
            val accessToken = data.getQueryParameter("access_token")

            // 1. Direct ECDSA Signed License Key Redemption
            if (!key.isNullOrBlank()) {
                val payload = LicenseValidator.verifyLicense(key)
                if (payload != null) {
                    val activated = premiumManager.redeemLicenseKey(key)
                    if (activated) {
                        Toast.makeText(this, "AmniShield Pro License Activated!", Toast.LENGTH_LONG).show()
                        showProActivationSuccessDialog(payload)
                    }
                } else {
                    Toast.makeText(this, "Invalid or expired license key.", Toast.LENGTH_LONG).show()
                }
            }
            // 2. OTP 6-Digit Code / Token + Email Verification via Supabase
            else if (!token.isNullOrBlank() && !email.isNullOrBlank()) {
                Toast.makeText(this, "Verifying code for $email...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val rest = com.alhaq.amnishield.data.sync.SupabaseRest()
                        val session = rest.verifyOtp(email, token, "email")
                        val profile = rest.fetchProfile(session)
                        withContext(Dispatchers.Main) {
                            if (profile != null && !profile.licenseKey.isNullOrBlank()) {
                                if (premiumManager.redeemLicenseKey(profile.licenseKey)) {
                                    val verifiedPayload = LicenseValidator.verifyLicense(profile.licenseKey)
                                    if (verifiedPayload != null) {
                                        showProActivationSuccessDialog(verifiedPayload)
                                    } else {
                                        Toast.makeText(this@MainActivity, "AmniShield Pro Activated!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else if (profile?.isPremium == true) {
                                premiumManager.updatePremiumStatus(true)
                                Toast.makeText(this@MainActivity, "AmniShield Pro Activated!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Account verified! No active Pro license found for $email.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            // 3. Direct Access Token / Session
            else if (!accessToken.isNullOrBlank()) {
                val refreshToken = data.getQueryParameter("refresh_token") ?: ""
                val userId = data.getQueryParameter("user_id") ?: ""
                val userEmail = data.getQueryParameter("email")
                val session = com.alhaq.amnishield.data.sync.SupabaseRest.Session(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userId = userId,
                    email = userEmail,
                    expiresAt = System.currentTimeMillis() + 3600_000L
                )
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val rest = com.alhaq.amnishield.data.sync.SupabaseRest()
                        val profile = rest.fetchProfile(session)
                        withContext(Dispatchers.Main) {
                            if (profile != null && !profile.licenseKey.isNullOrBlank()) {
                                premiumManager.redeemLicenseKey(profile.licenseKey)
                                Toast.makeText(this@MainActivity, "AmniShield Pro Activated!", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        // ignore or log
                    }
                }
            }
        }
    }

    private fun showProActivationSuccessDialog(payload: com.alhaq.amnishield.premium.LicensePayload) {
        val expiryDate = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date(payload.expires))
        MaterialAlertDialogBuilder(this)
            .setTitle("Pro License Activated!")
            .setMessage("Welcome to AmniShield Pro!\n\n• Account: ${payload.email}\n• Plan: ${payload.type.replaceFirstChar { it.uppercase() }}\n• Valid Until: $expiryDate\n\nAll premium features, advanced rules, and cross-device sync are now fully unlocked on this device.")
            .setPositiveButton("Continue", null)
            .show()
    }

    private fun showDevicePairingSuccessDialog(pin: String, deviceName: String, isManaged: Boolean) {
        val modeText = if (isManaged) "Protected Sync Mode" else "Personal Focus Mode"
        MaterialAlertDialogBuilder(this)
            .setTitle("Device Linked Successfully!")
            .setMessage("Your device is now securely connected to the AmniShield Cloud Sync Hub.\n\n• Device: $deviceName\n• Pairing Token: $pin\n• Mode: $modeText\n\nRules, blocklists, and schedules configured in your account will now automatically sync to this device.")
            .setPositiveButton("Awesome", null)
            .show()
    }

    private fun restorePremiumPurchases() {
        if (premiumManager.isPremium()) return
        
        val billingWrapper = BillingClientWrapper(this)
        billingWrapper.startConnection {
            billingWrapper.queryPurchases { purchases: List<String> ->
                if (purchases.isNotEmpty()) {
                    premiumManager.updatePremiumStatus(true)
                    android.util.Log.d("MainActivity", "Premium status restored from purchases")
                }
            }
        }
    }

    private fun maybeShowPremiumReminder() {
        if (!premiumManager.isPremium() && premiumManager.shouldShowReminder()) {
            premiumManager.markReminderShown()
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.premium_reminder_title))
                .setMessage(getString(R.string.premium_reminder_message))
                .setPositiveButton(R.string.premium_view_plans) { _, _ ->
                    val intent = Intent(this, FragmentActivity::class.java).apply {
                        putExtra("feature_type", "premium_features")
                    }
                    startActivity(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showFAQDialog() {
        val faqItems = arrayOf(
            "How do I enable accessibility services?" to "Go to Settings -> Accessibility -> AmniShield, then enable the required services. This is needed for all blocking features to work.",
            "What is the Notifications bell icon?" to "The bell icon shows your notification inbox with blocking alerts, daily reports, reminders, and achievements. Tap it to view your notification history.",
            "How does Reel Blocker work?" to "Reel Blocker detects and blocks endless scrolling on Instagram Reels, YouTube Shorts, and TikTok videos, helping you maintain focus.",
            "Why do my blocked apps/keywords disappear?" to "Make sure accessibility services stay enabled. Some system optimizations may disable them. You can check status in Settings.",
            "Can I export my settings?" to "Yes! Go to Settings -> Backup & Restore to export/import your configuration.",
            "What is Focus Mode?" to "Focus Mode lets you time-box app restrictions (e.g., block gaming apps for 2 hours). It tracks your focus sessions and shows productivity insights.",
            "How do I disable Anti-Uninstall protection?" to "Go to Settings -> Anti-Uninstall, enter your password, and tap Disable. You can then uninstall AmniShield normally.",
            "Is AmniShield really privacy-focused?" to "Yes! All text analysis, keyword detection, and content blocking happens locally on your device. We never send your data to servers.",
            "Is AmniShield open source?" to "Yes! AmniShield is 100% open-source. You can view the full source code, report issues, and contribute on our GitHub repository:<br/><br/><a href=\"${Constants.GITHUB_REPO_URL}\"><b>github.com/alhaq-studio/amnishield-android</b></a>",
            "Where can I find the source code?" to "AmniShield's source code is publicly available on GitHub:<br/><br/><a href=\"${Constants.GITHUB_REPO_URL}\"><b>github.com/alhaq-studio/amnishield-android</b></a><br/><br/>You can also explore our other open-source projects at <a href=\"${Constants.ALHAQ_STUDIO_URL}\"><b>alhaq.uk</b></a>",
            "How can I support AmniShield?" to "There are many ways to support AmniShield development:<br/><br/>• <a href=\"${Constants.ALHAQ_INITIATIVE_DONATE_URL}\"><b>Al-Haq Central Funding Hub</b></a><br/>• <a href=\"${Constants.GITHUB_SPONSORS_INITIATIVE_URL}\"><b>GitHub Sponsors (Initiative)</b></a><br/>• <a href=\"${Constants.GITHUB_SPONSORS_PERSONAL_URL}\"><b>GitHub Sponsors (Developer)</b></a><br/>• <a href=\"${Constants.KOFI_URL}\"><b>Ko-fi</b></a><br/>• <a href=\"${Constants.BUY_ME_A_COFFEE_URL}\"><b>Buy Me a Coffee</b></a><br/>• <a href=\"${Constants.PATREON_URL}\"><b>Patreon</b></a>"
        )
        
        val questions = faqItems.map { it.first }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.faq))
            .setItems(questions) { _, which ->
                val question = faqItems[which].first
                val answer = faqItems[which].second
                
                val messageView = android.widget.TextView(this).apply {
                    text = androidx.core.text.HtmlCompat.fromHtml(answer, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                    setPadding(64, 32, 64, 24)
                    textSize = 15f
                    setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE))
                    setLinkTextColor(com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, android.graphics.Color.parseColor("#7C4DFF")))
                }
                
                val builder = MaterialAlertDialogBuilder(this)
                    .setTitle(question)
                    .setView(messageView)
                    .setNegativeButton(getString(R.string.ok), null)
                
                if (question.contains("support", ignoreCase = true)) {
                    builder.setPositiveButton("Support Options") { _, _ ->
                        showSupportOptionsDialog()
                    }
                } else if (question.contains("source code", ignoreCase = true) || question.contains("open source", ignoreCase = true)) {
                    builder.setPositiveButton("Open GitHub") { _, _ ->
                        openUrl(Constants.GITHUB_REPO_URL)
                    }
                }
                
                builder.show()
            }
            .setNeutralButton("GitHub") { _, _ ->
                openUrl(Constants.GITHUB_REPO_URL)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDonationDialog() {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val firstDate = sharedPreferences.getString("first_date", null)
        if (firstDate == null) {
            val currentDateString = LocalDate.now().toString()
            sharedPreferences.edit().putString("first_date", currentDateString).apply()
        }

        if (!(sharedPreferences.getBoolean("is_donation_alerted", false))) {
            val storedFirstDate = firstDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
            val daysPassed = ChronoUnit.DAYS.between(storedFirstDate, LocalDate.now())

            if (daysPassed > 5L) {
                sharedPreferences.edit().putBoolean("is_donation_alerted", true).apply()
                val donationHtml = """
                    Thank you for using AmniShield!<br/><br/>
                    My name is Habibur Rahman, founder of <a href="${Constants.ALHAQ_STUDIO_URL}"><b>Al-Haq Studio</b></a>. I'm a student dedicated to building open-source digital wellbeing tools to help maintain a healthy, balanced digital lifestyle.<br/><br/>
                    AmniShield is <b>100% open-source, free, and ad-free</b>. If you find it beneficial, please consider supporting ongoing development:<br/>
                    • <a href="${Constants.ALHAQ_INITIATIVE_DONATE_URL}"><b>Al-Haq Central Funding Hub</b></a><br/>
                    • <a href="${Constants.GITHUB_SPONSORS_INITIATIVE_URL}"><b>GitHub Sponsors (Initiative)</b></a><br/>
                    • <a href="${Constants.GITHUB_SPONSORS_PERSONAL_URL}"><b>GitHub Sponsors (Developer)</b></a><br/>
                    • <a href="${Constants.KOFI_URL}"><b>Ko-fi</b></a> • <a href="${Constants.BUY_ME_A_COFFEE_URL}"><b>Buy Me a Coffee</b></a> • <a href="${Constants.PATREON_URL}"><b>Patreon</b></a><br/>
                    • <a href="${Constants.GITHUB_REPO_URL}"><b>Star us on GitHub</b></a><br/><br/>
                    Website: <a href="${Constants.AMNISHIELD_WEBSITE_URL}"><b>amnishield.com</b></a><br/>
                    GitHub: <a href="${Constants.GITHUB_REPO_URL}"><b>github.com/alhaq-studio/amnishield-android</b></a><br/><br/>
                    Your support helps keep AmniShield free and accessible for everyone worldwide. JazakAllahu Khairan!
                """.trimIndent()

                val donationMsgView = android.widget.TextView(this).apply {
                    text = androidx.core.text.HtmlCompat.fromHtml(donationHtml, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                    setPadding(64, 32, 64, 24)
                    textSize = 14f
                    setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE))
                    setLinkTextColor(com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, android.graphics.Color.parseColor("#7C4DFF")))
                }

                val donationContainer = androidx.core.widget.NestedScrollView(this).apply {
                    addView(donationMsgView)
                }

                MaterialAlertDialogBuilder(this)
                    .setTitle("Support AmniShield Development")
                    .setView(donationContainer)
                    .setNegativeButton(R.string.close) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("Support Options") { dialog, _ ->
                        showSupportOptionsDialog()
                        dialog.dismiss()
                    }
                    .setNeutralButton("Visit Website") { _, _ ->
                        openUrl(Constants.AMNISHIELD_WEBSITE_URL)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }
    
    fun showSupportOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_support_hub, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.support_dialog_title)
            .setView(dialogView)
            .setNegativeButton(R.string.close, null)
            .create()

        dialogView.findViewById<android.view.View>(R.id.card_initiative_hub)?.setOnClickListener {
            openUrl(Constants.ALHAQ_INITIATIVE_DONATE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<android.view.View>(R.id.card_sponsors_initiative)?.setOnClickListener {
            openUrl(Constants.GITHUB_SPONSORS_INITIATIVE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<android.view.View>(R.id.card_sponsors_developer)?.setOnClickListener {
            openUrl(Constants.GITHUB_SPONSORS_PERSONAL_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<android.view.View>(R.id.card_kofi)?.setOnClickListener {
            openUrl(Constants.KOFI_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<android.view.View>(R.id.card_buymeacoffee)?.setOnClickListener {
            openUrl(Constants.BUY_ME_A_COFFEE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<android.view.View>(R.id.card_patreon)?.setOnClickListener {
            openUrl(Constants.PATREON_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<android.view.View>(R.id.card_studio_site)?.setOnClickListener {
            openUrl(Constants.ALHAQ_STUDIO_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<android.view.View>(R.id.card_amnishield_website)?.setOnClickListener {
            openUrl(Constants.AMNISHIELD_WEBSITE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<android.view.View>(R.id.card_pro_pass)?.setOnClickListener {
            val intent = Intent(this, FragmentActivity::class.java).apply {
                putExtra("feature_type", "premium_features")
            }
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAboutDialog() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        val aboutHtml = """
            <b>AmniShield v$versionName</b><br/><br/>
            A comprehensive digital wellbeing app designed to help you maintain focus, develop healthy digital habits, and protect yourself from distracting content.<br/><br/>
            <b>Free Core Features:</b><br/>
            • App Blocker - Block apps with schedules &amp; custom controls<br/>
            • Reel Blocker - Limit endless scrolling on Reels, Shorts, and TikTok<br/>
            • Keyword &amp; Website Blocker - Block inappropriate content &amp; sites<br/>
            • Focus Mode - Time-boxed app restrictions with timer<br/>
            • Launch Limits - Restrict daily app launch frequencies<br/>
            • Notifications &amp; Statistics - Activity reports &amp; productivity trends<br/><br/>
            <b>Premium Security:</b><br/>
            • Anti-Uninstall Protection - Device Admin protection<br/>
            • 4-Digit Security PIN &amp; App Lock - Master PIN lock for settings<br/>
            • Bypass PIN Lock - Require PIN to edit active blocks<br/><br/>
            <b>Privacy First:</b> 100% local processing, zero tracking<br/><br/>
            <b>Website:</b> <a href="${Constants.AMNISHIELD_WEBSITE_URL}">amnishield.com</a><br/>
            <b>Source Code:</b> <a href="${Constants.GITHUB_REPO_URL}">github.com/alhaq-studio/amnishield-android</a><br/>
            Built under: <a href="${Constants.ALHAQ_STUDIO_URL}">Al-Haq Studio</a><br/>
            <b>100% Open Source • No Ads • No Tracking • Privacy First</b>
        """.trimIndent()

        val messageView = android.widget.TextView(this).apply {
            text = androidx.core.text.HtmlCompat.fromHtml(aboutHtml, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            setPadding(64, 32, 64, 24)
            textSize = 14f
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE))
            setLinkTextColor(com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, android.graphics.Color.parseColor("#7C4DFF")))
        }

        val scrollContainer = androidx.core.widget.NestedScrollView(this).apply {
            addView(messageView)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.about))
            .setView(scrollContainer)
            .setPositiveButton("Support Hub") { _, _ ->
                showSupportOptionsDialog()
            }
            .setNeutralButton("GitHub") { _, _ ->
                openUrl(Constants.GITHUB_REPO_URL)
            }
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No application found to open the link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isFirstLaunchComplete(): Boolean {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("isFirstLaunchComplete", false)
    }

    private fun checkAppLock() {
        val pinEnabled = savedPreferencesLoader.isPinSecurityEnabled()
        val appLockEnabled = savedPreferencesLoader.isAppLockEnabled()
        val pinCode = savedPreferencesLoader.getPinCode()

        if (pinEnabled && appLockEnabled && pinCode.isNotEmpty() && !AmniShield.isAppUnlocked) {
            showPinLockFullscreenDialog(pinCode)
        }
    }

    private fun showPinLockFullscreenDialog(correctPinCode: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        
        dialog.window?.let { window ->
            window.decorView.setViewTreeLifecycleOwner(this)
            window.decorView.setViewTreeViewModelStoreOwner(this)
            window.decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AmniShieldTheme(appTheme = ThemeUtils.resolveAppTheme(this@MainActivity)) {
                    PinPromptContent(
                        correctPin = correctPinCode,
                        title = "AmniShield Locked",
                        subtitle = "Enter your 4-digit PIN to access the app",
                        allowForgotPin = true,
                        onDismiss = {
                            dialog.dismiss()
                            finish()
                        },
                        onPinSuccess = {
                            AmniShield.isAppUnlocked = true
                            dialog.dismiss()
                        },
                        onPinResetCompleted = {
                            AmniShield.isAppUnlocked = true
                            dialog.dismiss()
                        }
                    )
                }
            }
        }
        
        dialog.setContentView(composeView)
        dialog.setCancelable(false)
        dialog.show()
    }
}
