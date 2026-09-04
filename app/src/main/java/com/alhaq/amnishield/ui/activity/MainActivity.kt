package com.alhaq.amnishield.ui.activity

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.alhaq.amnishield.AmniShield
import com.alhaq.amnishield.data.AmniShieldAccount
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.ActivityMainBinding
import com.alhaq.amnishield.databinding.DialogPermissionInfoBinding
import com.alhaq.amnishield.databinding.DialogRemoveAntiUninstallBinding
import com.alhaq.amnishield.receivers.AdminReceiver
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnishield.ui.fragments.StatsFragment
import com.alhaq.amnishield.ui.fragments.SettingsFragment
import com.alhaq.amnishield.ui.fragments.AdvancedFragment
import com.alhaq.amnishield.ui.fragments.FocusFragment
import com.alhaq.amnishield.ui.activity.FragmentActivity
import com.alhaq.amnishield.ui.fragments.installation.AccessibilityGuide
import com.alhaq.amnishield.ui.fragments.installation.WelcomeFragment
import com.alhaq.amnishield.utils.ErrorReportManager
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.utils.PermissionGuideHelper
import com.alhaq.amnishield.utils.GoogleSignInHelper
import com.alhaq.amnishield.utils.ThemeUtils
import com.alhaq.amnishield.utils.UserFeedback
import com.alhaq.amnishield.utils.ZipUtils
import com.alhaq.amnishield.utils.BillingClientWrapper
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.permissions.PermissionsManager
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.ui.state.AppTheme


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var brandLogoBitmap: Bitmap? = null
    private var drawerBannerDrawable: Drawable? = null
    
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val account = googleSignInHelper.handleSignInResult(result.data)
            if (account != null) {
                updateNavigationHeader(account)
                Toast.makeText(this, getString(R.string.signed_in_as, account.email), Toast.LENGTH_SHORT).show()
                syncAccountWithBackend(account)
            } else {
                Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncAccountWithBackend(account: com.alhaq.amnishield.data.AmniShieldAccount) {
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
    
    private lateinit var selectPinnedAppsLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectBlockedAppsLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectFocusModeUnblockedAppsLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectOverlayAppsLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectBlockedKeywords: ActivityResultLauncher<Intent>

    private lateinit var addCheatHoursActivity: ActivityResultLauncher<Intent>

    private lateinit var addAutoFocusHoursActivity: ActivityResultLauncher<Intent>

    private lateinit var directoryPicker: ActivityResultLauncher<Intent>


    private val savedPreferencesLoader = SavedPreferencesLoader(this)
    private val premiumManager by lazy { PremiumManager.getInstance(this) }
    private lateinit var options: ActivityOptionsCompat

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted, show notifications
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()

//                makeStartFocusModeDialog()
            } else {
                // Permission denied
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()

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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // Proper edge-to-edge inset handling for Android 15+
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            
            // Apply padding to prevent content from being hidden by system bars
            v.setPadding(
                systemBars.left,
                systemBars.top, // Add top padding to account for status bar
                systemBars.right,
                maxOf(systemBars.bottom, imeInsets.bottom) // Handle keyboard
            )
            
            // Consume insets to prevent further propagation
            WindowInsetsCompat.CONSUMED
        }

        // Initialize helpers
        try {
            googleSignInHelper = GoogleSignInHelper(this)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Failed to initialize GoogleSignInHelper", e)
        }
        drawerLayout = binding.drawerLayout

        // Modern back-press handling: prefer OnBackPressedDispatcher over the
        // deprecated Activity.onBackPressed override. Closes the navigation
        // drawer when open, pops fragment backstack, otherwise falls back to system default.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Initialize notification channels
        try {
            initializeNotificationChannels()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Failed to initialize notification channels", e)
        }
        
        // Restore premium purchases automatically on app start
        try {
            restorePremiumPurchases()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Failed to restore premium purchases", e)
        }
        
        // Schedule daily reports for premium users
        scheduleDailyReportsIfPremium()

        // Schedule background policy & rules sync engine
        try {
            com.alhaq.amnishield.data.sync.SyncWorker.schedule(this)
            lifecycleScope.launch(Dispatchers.IO) {
                com.alhaq.amnishield.data.sync.PolicySyncManager.syncNow(this@MainActivity)
            }
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Failed to initialize SyncWorker", e)
        }

        // Setup navigation drawer
        setupNavigationDrawer()

        options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
        setupActivityLaunchers()
        setupFragmentNavigation(savedInstanceState)

        if (!isFirstLaunchComplete()) {
            val intent = Intent(this, FragmentActivity::class.java)
            intent.putExtra("fragment", WelcomeFragment.FRAGMENT_ID)
            startActivity(intent, options.toBundle())
            finish()
            return
        }
        showDonationDialog()
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase().orEmpty()
        val host = data.host?.lowercase().orEmpty()
        val path = data.path?.lowercase().orEmpty()

        val isAmnScheme = scheme == "amnishield" || scheme == "amnishield"
        val isWebActivation = (scheme == "https" || scheme == "http") &&
                (host.contains("amnishield.com") || host.contains("amnishield.com") || host.contains("amnishield.org")) &&
                (path.contains("activate") || path.contains("license") || path.contains("auth") || path.contains("verify") || path.contains("pair") || host == "pair" || data.getQueryParameter("key") != null || data.getQueryParameter("token") != null || data.getQueryParameter("code") != null || data.getQueryParameter("pin") != null)

        if (isAmnScheme || isWebActivation) {
            val isPairRequest = host == "pair" || path.contains("pair") || (data.getQueryParameter("owner") != null && data.getQueryParameter("token") != null)
            val pairPin = data.getQueryParameter("token") ?: data.getQueryParameter("pin") ?: data.getQueryParameter("code")

            // 0. Ephemeral Device Pairing via Web Console QR Code or PIN Link
            if (isPairRequest && !pairPin.isNullOrBlank()) {
                val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
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
                                binding.bottomNavigation.selectedItemId = binding.bottomNavigation.selectedItemId
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
                val payload = com.alhaq.amnishield.premium.LicenseValidator.verifyLicense(key)
                if (payload != null) {
                    val activated = premiumManager.redeemLicenseKey(key)
                    if (activated) {
                        Toast.makeText(this, "AmniShield Pro License Activated!", Toast.LENGTH_LONG).show()
                        showProActivationSuccessDialog(payload)
                        binding.bottomNavigation.selectedItemId = binding.bottomNavigation.selectedItemId
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
                                    val verifiedPayload = com.alhaq.amnishield.premium.LicenseValidator.verifyLicense(profile.licenseKey)
                                    if (verifiedPayload != null) {
                                        showProActivationSuccessDialog(verifiedPayload)
                                    } else {
                                        Toast.makeText(this@MainActivity, "AmniShield Pro Activated!", Toast.LENGTH_LONG).show()
                                    }
                                    binding.bottomNavigation.selectedItemId = binding.bottomNavigation.selectedItemId
                                }
                            } else if (profile?.isPremium == true) {
                                premiumManager.updatePremiumStatus(true)
                                Toast.makeText(this@MainActivity, "AmniShield Pro Activated!", Toast.LENGTH_LONG).show()
                                binding.bottomNavigation.selectedItemId = binding.bottomNavigation.selectedItemId
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
                                binding.bottomNavigation.selectedItemId = binding.bottomNavigation.selectedItemId
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
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Pro License Activated!")
            .setMessage("Welcome to AmniShield Pro!\n\n• Account: ${payload.email}\n• Plan: ${payload.type.replaceFirstChar { it.uppercase() }}\n• Valid Until: $expiryDate\n\nAll premium features, advanced rules, and cross-device sync are now fully unlocked on this device.")
            .setPositiveButton("Continue", null)
            .show()
    }

    private fun showDevicePairingSuccessDialog(pin: String, deviceName: String, isManaged: Boolean) {
        val modeText = if (isManaged) "Protected Sync Mode" else "Personal Focus Mode"
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("📱 Device Linked Successfully!")
            .setMessage("Your device is now securely connected to the AmniShield Cloud Sync Hub.\n\n• Device: $deviceName\n• Pairing Token: $pin\n• Mode: $modeText\n\nRules, blocklists, and schedules configured in your account will now automatically sync to this device.")
            .setPositiveButton("Awesome", null)
            .show()
    }
    
    private fun updateToolbarTitleForTab(tabId: Int) {
        val title = when (tabId) {
            R.id.navigation_stats -> "Usage & Stats"
            R.id.navigation_blocks -> "Blocks & Rules"
            R.id.navigation_focus -> "Quick Focus"
            R.id.navigation_advanced -> "Advanced Protection"
            else -> getString(R.string.app_name)
        }
        binding.toolbar.title = title
        supportActionBar?.title = title
    }

    private fun setupFragmentNavigation(savedInstanceState: Bundle?) {
        // Load StatsFragment or deep-linked tab by default
        if (savedInstanceState == null) {
            val startTab = intent.getIntExtra("start_tab", R.id.navigation_stats)
            val initialFragment = when (startTab) {
                R.id.navigation_advanced -> AdvancedFragment()
                R.id.navigation_blocks -> BlocksManagerFragment()
                R.id.navigation_focus -> FocusFragment()
                else -> StatsFragment()
            }
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.nav_host_fragment, initialFragment)
                .commit()
            binding.bottomNavigation.selectedItemId = startTab
            updateToolbarTitleForTab(startTab)
        } else {
            updateToolbarTitleForTab(binding.bottomNavigation.selectedItemId)
        }
        
        supportFragmentManager.addOnBackStackChangedListener {
            val isBackStackEmpty = supportFragmentManager.backStackEntryCount == 0
            if (::drawerToggle.isInitialized) {
                drawerToggle.isDrawerIndicatorEnabled = isBackStackEmpty
                supportActionBar?.setDisplayHomeAsUpEnabled(!isBackStackEmpty)
                if (isBackStackEmpty) {
                    drawerToggle.syncState()
                    updateToolbarTitleForTab(binding.bottomNavigation.selectedItemId)
                } else {
                    val currentFrag = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    if (currentFrag is SettingsFragment) {
                        setToolbarTitle("Settings")
                    }
                }
            }
        }
        
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
            updateToolbarTitleForTab(item.itemId)
            when (item.itemId) {
                R.id.navigation_stats -> {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.nav_host_fragment, StatsFragment())
                        .commit()
                    true
                }
                R.id.navigation_blocks -> {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.nav_host_fragment, BlocksManagerFragment())
                        .commit()
                    true
                }
                R.id.navigation_focus -> {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.nav_host_fragment, FocusFragment())
                        .commit()
                    true
                }
                R.id.navigation_advanced -> {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.nav_host_fragment, AdvancedFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    fun selectTab(tabId: Int) {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        binding.bottomNavigation.selectedItemId = tabId
        updateToolbarTitleForTab(tabId)
    }

    fun getSelectedTabId(): Int = binding.bottomNavigation.selectedItemId

    fun setBottomNavVisible(visible: Boolean) {
        binding.bottomNavigation.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setToolbarVisible(visible: Boolean) {
        binding.toolbar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setToolbarTitle(title: CharSequence) {
        binding.toolbar.title = title
        supportActionBar?.title = title
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val startTab = intent.getIntExtra("start_tab", -1)
        if (startTab != -1) {
            selectTab(startTab)
        }
        handleDeepLink(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
    
    override fun onResume() {
        super.onResume()
        updateToolbarTitleForTab(binding.bottomNavigation.selectedItemId)
        checkAppLock()
        // Permissions will now be handled by PermissionsBottomSheet on first launch, 
        // or user can access them through settings if needed.
        // checkPermissions() // Removed old permission check
        maybeShowPremiumReminder()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (::drawerToggle.isInitialized && drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        return when (item.itemId) {
            R.id.action_notifications -> {
                // Bell icon opens the in-app notification inbox.
                // Reminder/notification preferences live under Settings → Reminders.
                val intent = Intent(this, NotificationsActivity::class.java)
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    R.anim.fade_in,
                    R.anim.fade_out
                )
                startActivity(intent, options.toBundle())
                true
            }
            R.id.action_settings -> {
                openSettingsScreen()
                true
            }
            R.id.action_faq -> {
                showFAQDialog()
                true
            }
            R.id.action_feedback -> {
                showFeedbackDialog()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupActivityLaunchers() {

        selectPinnedAppsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                selectedApps?.let {
                    savedPreferencesLoader.savePinned(it.toSet())
                }
            }
        }

        selectBlockedAppsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                    selectedApps?.let {
                        savedPreferencesLoader.saveBlockedApps(it.toSet())
                        sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
                    }
                }
            }


        selectFocusModeUnblockedAppsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                    selectedApps?.let {
                        savedPreferencesLoader.saveFocusModeSelectedApps(selectedApps)
                        sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE)
                    }
                }
            }

        selectOverlayAppsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                    selectedApps?.let {
                        savedPreferencesLoader.setOverlayApps(it.toSet())
                    }
                }
            }

        selectBlockedKeywords =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val blockedKeywords = result.data?.getStringArrayListExtra("SELECTED_KEYWORDS")
                    blockedKeywords?.let {
                        savedPreferencesLoader.saveBlockedKeywords(it.toSet())
                        sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST)
                    }
                }
            }

        addCheatHoursActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
            }

        addAutoFocusHoursActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            }
        // Register the directory picker
        directoryPicker = ZipUtils.registerDirectoryPicker(this) { directoryUri ->
            // Create the zip file in the selected directory
            val filename = ZipUtils.createZipFileName()
            val zipUri = createFileInDirectory(directoryUri, filename)
            zipUri?.let {
                ZipUtils.zipSharedPreferencesToUri(this, it)
            }
        }
    }

    // OLD: setupClickListeners removed - UI moved to fragments
    /*
    private fun setupClickListeners() {
        // click listeners for configuration options
        binding.selectPinnedApps.setOnClickListener {
            val intent = Intent(this, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                ArrayList(savedPreferencesLoader.loadPinnedApps())
            )

            selectPinnedAppsLauncher.launch(intent, options)

        }
        binding.selectBlockedApps.setOnClickListener {
            val intent = Intent(this, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                ArrayList(savedPreferencesLoader.loadBlockedApps())
            )
            selectBlockedAppsLauncher.launch(intent, options)
        }
        binding.selectBlockedKeywords.setOnClickListener {
            val intent = Intent(this, ManageKeywordsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SAVED_KEYWORDS",
                ArrayList(savedPreferencesLoader.loadBlockedKeywords())
            )
            selectBlockedKeywords.launch(intent, options)
        }


        binding.appBlockerSelectCheatHours.setOnClickListener {
            val intent = Intent(this, TimedActionActivity::class.java)
            intent.putExtra("selected_mode", TimedActionActivity.MODE_APP_BLOCKER_CHEAT_HOURS)
            addCheatHoursActivity.launch(intent, options)
        }
        binding.btnConfigAppblockerWarning.setOnClickListener {
            TweakAppBlockerWarning(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_app_blocker_warning"
            )
        }
        binding.btnConfigViewblockerWarning.setOnClickListener {
            TweakViewBlockerWarning(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_view_blocker_warning"
            )
        }
        binding.btnConfigViewblockerCheatHours.setOnClickListener {
            TweakViewBlockerCheatHours(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_view_blocker_cheat_hours"
            )
        }
        binding.btnConfigTracker.setOnClickListener{
            TweakUsageTracker(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_usage_tracker"
            )
        }
        binding.btnUnlockAntiUninstall.setOnClickListener {
            makeRemoveAntiUninstallDialog()
        }
        binding.btnManagePreinstalledKeywords.setOnClickListener {
            TweakKeywordPack().show(supportFragmentManager, "tweak_keyword_pack")
        }
        binding.btnManageKeywordBlocker.setOnClickListener {
            TweakKeywordBlocker(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_keyword_blocker"
            )
        }
        binding.selectAppUsageStats.setOnClickListener {
            val intent = Intent(this, FragmentActivity::class.java)
            intent.putExtra("fragment", AllAppsUsageFragment.FRAGMENT_ID)
            startActivity(intent, options.toBundle())
        }

        binding.selectReelUsageStats.setOnClickListener {
            val intent = Intent(this, UsageMetricsActivity::class.java)
            startActivity(intent, options.toBundle())
        }
        binding.btnSelectAppsToShowOverlay.setOnClickListener {
            val intent = Intent(this, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                ArrayList(savedPreferencesLoader.getOverlayApps())
            )
            selectOverlayAppsLauncher.launch(intent, options)
        }
        binding.selectFocusBlockedApps.setOnClickListener {
            val intent = Intent(this, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                ArrayList(savedPreferencesLoader.getFocusModeSelectedApps())
            )
            selectFocusModeUnblockedAppsLauncher.launch(intent, options)
        }
        binding.autoFocus.setOnClickListener {
            val intent = Intent(this, TimedActionActivity::class.java)
            intent.putExtra("selected_mode", TimedActionActivity.MODE_AUTO_FOCUS)
            addAutoFocusHoursActivity.launch(intent, options)
        }


        binding.startFocusMode.setOnClickListener {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS,options)
                    return@setOnClickListener
                }
            }


            createFocusModeShortcut()

            StartFocusMode(savedPreferencesLoader, onPositiveButtonPressed = {
                binding.selectFocusBlockedApps.isEnabled = false
                binding.startFocusMode.isEnabled = false

            }).show(
                supportFragmentManager,
                "start_focus_mode"
            )

        }

        // listeners for turn on/ off buttons
        binding.antiUninstallCardChip.setOnClickListener {
            if (!isDeviceAdminOn) {
                makeDeviceAdminPermissionDialog()
            } else {
                if (binding.antiUninstallWarning.visibility == View.GONE) {
                    val intent = Intent(this, FragmentActivity::class.java)
                    intent.putExtra("fragment", ChooseModeFragment.FRAGMENT_ID)
                    startActivity(intent, options.toBundle())
                } else {
                    makeAccessibilityInfoDialog(
                        "AmniShield",
                        AmniShieldAccessibilityService::class.java
                    )
                }
            }
        }

        // Monochrome feature removed - Shizuku dependency removed

        binding.keywordBlockerStatusChip.setOnClickListener {
            makeAccessibilityInfoDialog("Keyword Blocker", KeywordBlockerService::class.java)
        }
        binding.focusModeStatusChip.setOnClickListener {
            makeAccessibilityInfoDialog("App Blocker", AppBlockerService::class.java)
        }
        binding.appBlockerStatusChip.setOnClickListener {
            makeAccessibilityInfoDialog("App Blocker", AppBlockerService::class.java)
        }
        binding.viewBlockerStatusChip.setOnClickListener {
            makeAccessibilityInfoDialog("View Blocker", ViewBlockerService::class.java)
        }
        binding.usageTrackerStatusChip.setOnClickListener {
            if (!isDisplayOverOtherAppsOn) {
                makeDrawOverOtherAppsDialog()
            } else {
                makeAccessibilityInfoDialog("Usage Tracker", UsageTrackingService::class.java)
            }
        }

        // socials click listeners
        binding.btnDiscord.setOnClickListener {
            openUrl("https://discord.gg/zXz7pGVJY")
        }

        binding.btnTelegram.setOnClickListener {
            openUrl("https://t.me/amnishield")
        }
        binding.btnGithub.setOnClickListener {
            openUrl(Constants.AMNISHIELD_WEBSITE_URL)
        }
        binding.btnInstagram.setOnClickListener {
            openUrl("https://www.instagram.com/alhaqinitiative")
        }
        binding.btnDonate.setOnClickListener {
            openUrl("https://alhaq.uk/support.html")
        }

        binding.btnCredits.setOnClickListener {
            openUrl(Constants.PRIVACY_POLICY_URL)
        }
        binding.btnBackup.setOnClickListener {
            ZipUtils.showDirectoryPicker(directoryPicker)
        }
        binding.helpReelBlocker.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_view_blocker))
                .setMessage(getString(R.string.this_option_has_the_ability_to_block_youtube_shorts_and_instagram_reels_while_allowing_access_to_other_app_features))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }

        binding.btnBackup.setOnClickListener {
            ZipUtils.showDirectoryPicker(directoryPicker)
        }
        binding.btnShareErrors.setOnClickListener {
            shareCrashLog(this)
        }

        // Card click listeners - make feature cards expandable/collapsible
        binding.focusModeCard.setOnClickListener {
            // Toggle visibility of focus mode configuration buttons
            val buttonsVisible = binding.selectFocusBlockedApps.visibility == View.VISIBLE
            binding.selectFocusBlockedApps.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
            binding.autoFocus.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
            binding.startFocusMode.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
        }

        binding.appBlockerCard.setOnClickListener {
            // Toggle visibility of app blocker configuration buttons
            val buttonsVisible = binding.selectBlockedApps.visibility == View.VISIBLE
            binding.selectBlockedApps.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
            binding.appBlockerSelectCheatHours.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
            binding.btnConfigAppblockerWarning.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
        }

        binding.viewBlockerCard.setOnClickListener {
            // Toggle visibility of view blocker configuration buttons
            val buttonsVisible = binding.btnConfigViewblockerWarning.visibility == View.VISIBLE
            binding.btnConfigViewblockerWarning.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
            binding.btnConfigViewblockerCheatHours.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
        }

        binding.keywordBlockerCard.setOnClickListener {
            // Toggle visibility of keyword blocker configuration buttons
            val buttonsVisible = binding.selectBlockedKeywords.visibility == View.VISIBLE
            binding.selectBlockedKeywords.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
            binding.btnManagePreinstalledKeywords.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
            binding.btnManageKeywordBlocker.visibility = if (buttonsVisible) View.GONE else View.VISIBLE
        }

        binding.usageTrackerCard.setOnClickListener {
            // Navigate to AllAppsUsageFragment which shows usage stats with diagram
            val intent = Intent(this, FragmentActivity::class.java)
            intent.putExtra("fragment", "all_app_usage")
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
        }

        binding.antiUninstallCard.setOnClickListener {
            // Show/hide anti-uninstall unlock button
            val buttonVisible = binding.btnUnlockAntiUninstall.visibility == View.VISIBLE
            binding.btnUnlockAntiUninstall.visibility = if (buttonVisible) View.GONE else View.VISIBLE
        }
    }
    */ // END setupClickListeners - commented out

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent, options.toBundle())
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No application found to open the link", Toast.LENGTH_SHORT).show()
        }
    }

//    private fun checkPermissions() { // Removed old permission check
//        isDisplayOverOtherAppsOn = Settings.canDrawOverlays(this)
//        lifecycleScope.launch {
//            withContext(Dispatchers.IO) {
//                isGeneralSettingsOn = isAccessibilityServiceEnabled(AmniShieldAccessibilityService::class.java)
//            }
//
//            val devicePolicyManager =
//                getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
//            val componentName = ComponentName(applicationContext, AdminReceiver::class.java)
//            isDeviceAdminOn = devicePolicyManager.isAdminActive(componentName)
//
//            val antiUninstallInfo = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
//            isAntiUninstallOn = antiUninstallInfo.getBoolean("is_anti_uninstall_on", false)
//
//            withContext(Dispatchers.Main) {
//                notifyHomeFragment()
//            }
//        }
//    }




    // setupShizukuFeatures removed - Shizuku dependency removed

    private fun showDonationDialog() {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val firstDate = sharedPreferences.getString("first_date", null)
        if (firstDate == null) {
            // Store the current date as a string representation
            val currentDateString = LocalDate.now().toString()
            sharedPreferences.edit().putString("first_date", currentDateString).apply()
        }

        if (!(sharedPreferences.getBoolean("is_donation_alerted", false))) {
            // Parse the stored date string back to LocalDate
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
                    • ⭐ <a href="${Constants.GITHUB_REPO_URL}"><b>Star us on GitHub</b></a><br/><br/>
                    🌐 Website: <a href="${Constants.AMNISHIELD_WEBSITE_URL}"><b>amnishield.com</b></a><br/>
                    📂 GitHub: <a href="${Constants.GITHUB_REPO_URL}"><b>github.com/alhaq-studio/amnishield-android</b></a><br/><br/>
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

        dialogView.findViewById<View>(R.id.card_initiative_hub)?.setOnClickListener {
            openUrl(Constants.ALHAQ_INITIATIVE_DONATE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_sponsors_initiative)?.setOnClickListener {
            openUrl(Constants.GITHUB_SPONSORS_INITIATIVE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_sponsors_developer)?.setOnClickListener {
            openUrl(Constants.GITHUB_SPONSORS_PERSONAL_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_kofi)?.setOnClickListener {
            openUrl(Constants.KOFI_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_buymeacoffee)?.setOnClickListener {
            openUrl(Constants.BUY_ME_A_COFFEE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_patreon)?.setOnClickListener {
            openUrl(Constants.PATREON_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_studio_site)?.setOnClickListener {
            openUrl(Constants.ALHAQ_STUDIO_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_amnishield_website)?.setOnClickListener {
            openUrl(Constants.AMNISHIELD_WEBSITE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_pro_pass)?.setOnClickListener {
            val intent = Intent(this, FragmentActivity::class.java).apply {
                putExtra("feature_type", "premium_features")
            }
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }
    
    private fun isFirstLaunchComplete(): Boolean {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("isFirstLaunchComplete", false)
    }

    private fun setFirstLaunchComplete(complete: Boolean) {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isFirstLaunchComplete", complete).apply()
    }

    fun shareCrashLog(context: Context) {
        try {
            val crashLogger = com.alhaq.amnishield.CrashLogger.getInstance(context)
            val exportFile = crashLogger.getExportLogFile()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                exportFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AmniShield Diagnostic Logs")
                putExtra(Intent.EXTRA_TEXT, "Attached are the AmniShield diagnostic system & crash logs.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("Diagnostic Logs", uri)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Logs"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share logs: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun sendRefreshRequest(action: String) {
        val intent = Intent(action).setPackage(packageName)
        sendBroadcast(intent)
    }



    private fun createFocusModeShortcut() {

        val sp = getSharedPreferences("shortcuts",Context.MODE_PRIVATE)
        if(sp.getBoolean("focus_mode",false)){
            return
        }
        val intent = Intent(this, ShortcutActivity::class.java).apply {
            action = Intent.ACTION_CREATE_SHORTCUT
        }
        val shortcutInfo = ShortcutInfoCompat.Builder(this, "amnishield_focus_mode")
            .setShortLabel(getString(R.string.focus_mode))
            .setLongLabel(getString(R.string.focus_mode))
            .setIntent(intent)
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_focus_mode))
            .build()


        val supported = ShortcutManagerCompat.isRequestPinShortcutSupported(this)
        val dynamicShortcuts = ShortcutManagerCompat.getDynamicShortcuts(this)

        if(supported){
            if(dynamicShortcuts.contains(shortcutInfo)){
                return
            }
        }
        MaterialAlertDialogBuilder(this).apply {
            setTitle("Add Focus Mode to Home Screen")
            setMessage("Would you like to add Focus Mode to your home screen for quick access?")
            setPositiveButton("Ok") { dialog, _ ->
                sp.edit().putBoolean("focus_mode",true).apply()
                val pinnedShortcutCallbackIntent = Intent("example.intent.action.SHORTCUT_CREATED")

                val successCallback = PendingIntent.getBroadcast(
                    this@MainActivity,
                    1000,
                    pinnedShortcutCallbackIntent,
                    FLAG_IMMUTABLE
                )

                ShortcutManagerCompat.requestPinShortcut(
                    this@MainActivity,
                    shortcutInfo,
                    successCallback.intentSender
                )

            }
            setNegativeButton("Cancel", { _,_ ->
                sp.edit().putBoolean("focus_mode",false).apply()
            })
            show()
        }

    }

    @SuppressLint("ApplySharedPref")
    private fun makeRemoveAntiUninstallDialog() {
        val antiUninstallInfo = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        val mode = antiUninstallInfo.getInt("mode", -1)
        when (mode) {

            Constants.ANTI_UNINSTALL_TIMED_MODE -> {
                val unlockAtMillis = antiUninstallInfo.getLong("unlock_at_millis", 0L)
                val targetUnlockMillis = if (unlockAtMillis > 0L) {
                    unlockAtMillis
                } else {
                    val dateString = antiUninstallInfo.getString("date", null)
                    if (dateString != null) {
                        try {
                            val parts = dateString.split("/")
                            val cal = Calendar.getInstance().apply {
                                set(parts[2].toInt(), parts[0].toInt() - 1, parts[1].toInt(), 23, 59, 59)
                                set(Calendar.MILLISECOND, 999)
                            }
                            cal.timeInMillis
                        } catch (e: Exception) {
                            0L
                        }
                    } else {
                        0L
                    }
                }

                val remainingMillis = targetUnlockMillis - System.currentTimeMillis()
                if (targetUnlockMillis > 0L && remainingMillis <= 0L) {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.anti_uninstall_removed),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    antiUninstallInfo.edit()
                        .putBoolean("is_anti_uninstall_on", false)
                        .remove("unlock_at_millis")
                        .remove("date")
                        .commit()
                    sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                } else {
                    val daysDiff = if (remainingMillis > 0L) {
                        kotlin.math.ceil(remainingMillis / (1000.0 * 60.0 * 60.0 * 24.0)).toInt().coerceAtLeast(1)
                    } else {
                        1
                    }

                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.failed))
                        .setMessage(getString(R.string.remaining_time_anti_uninstall, daysDiff))
                        .setPositiveButton("Ok", null)
                        .show()
                }
            }

            Constants.ANTI_UNINSTALL_PASSWORD_MODE -> {
                val dialogRemoveAntiUninstall =
                    DialogRemoveAntiUninstallBinding.inflate(layoutInflater)
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.remove_anti_uninstall))
                    .setView(dialogRemoveAntiUninstall.root)
                    .setPositiveButton(R.string.remove) { _, _ ->
                        val entered = dialogRemoveAntiUninstall.password.text.toString()
                        val stored = antiUninstallInfo.getString("password", null)
                        if (com.alhaq.amnishield.utils.PasswordHasher.verify(entered, stored)) {
                            // Upgrade legacy plaintext on the way out (defense in depth: even
                            // though we are removing protection, leave no plaintext behind).
                            if (com.alhaq.amnishield.utils.PasswordHasher.isPlainText(stored)) {
                                antiUninstallInfo.edit()
                                    .putString(
                                        "password",
                                        com.alhaq.amnishield.utils.PasswordHasher.hash(entered)
                                    )
                                    .apply()
                            }
                            antiUninstallInfo.edit().putBoolean("is_anti_uninstall_on", false)
                                .commit()
                            sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)

                            Snackbar.make(
                                binding.root,
                                "Anti Uninstall removed",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        } else {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.incorrect_password_please_try_again),
                                Snackbar.LENGTH_SHORT
                            )
                                .setAction(getString(R.string.retry)) {
                                    makeRemoveAntiUninstallDialog()
                                }
                                .show()
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }

    }
    private fun createFileInDirectory(directoryUri: Uri, filename: String): Uri? {
        return try {
            val docTree = DocumentFile.fromTreeUri(this, directoryUri)
            docTree?.createFile("application/zip", filename)?.uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class WarningData(
        val message: String = "You can setup a custom message to appear here!",
        val timeInterval: Int = 120000, // default cooldown period
        val isDynamicIntervalSettingAllowed: Boolean = false,
        val isProceedDisabled: Boolean = false,
        val isWarningDialogHidden: Boolean = false, // perform back/home action directly without showing warning screen
        val proceedDelayInSecs: Int = 15
    )

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
            <b>⭐ Premium Security:</b><br/>
            • Anti-Uninstall Protection - Device Admin protection<br/>
            • 4-Digit Security PIN &amp; App Lock - Master PIN lock for settings<br/>
            • Bypass PIN Lock - Require PIN to edit active blocks<br/><br/>
            <b>Privacy First:</b> 100% local processing, zero tracking<br/><br/>
            🌐 <b>Website:</b> <a href="${Constants.AMNISHIELD_WEBSITE_URL}">amnishield.com</a><br/>
            📂 <b>Source Code:</b> <a href="${Constants.GITHUB_REPO_URL}">github.com/alhaq-studio/amnishield-android</a><br/>
            ⭐ <a href="${Constants.GITHUB_REPO_URL}">Star us on GitHub to show your support!</a><br/><br/>
            💬 <b>Community:</b><br/>
            • <a href="${Constants.TELEGRAM_URL}">Telegram: t.me/amnishield</a><br/>
            • <a href="${Constants.DISCORD_URL}">Discord: discord.gg/zXz7pGVJY</a><br/><br/>
            💖 <b>Support Development:</b><br/>
            • <a href="${Constants.ALHAQ_INITIATIVE_DONATE_URL}">Al-Haq Central Funding Hub</a><br/>
            • <a href="${Constants.GITHUB_SPONSORS_INITIATIVE_URL}">GitHub Sponsors (Initiative)</a><br/>
            • <a href="${Constants.GITHUB_SPONSORS_PERSONAL_URL}">GitHub Sponsors (Developer)</a><br/>
            • <a href="${Constants.KOFI_URL}">Ko-fi</a> • <a href="${Constants.BUY_ME_A_COFFEE_URL}">Buy Me a Coffee</a> • <a href="${Constants.PATREON_URL}">Patreon</a><br/><br/>
            Built under: <a href="${Constants.ALHAQ_STUDIO_URL}">Al-Haq Studio</a><br/>
            Free Access Program: <a href="${Constants.ALHAQ_INITIATIVE_URL}">Al-Haq Initiative</a><br/>
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

    private fun setupNavigationDrawer() {
        val navigationView = binding.navView
        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerToggle.setToolbarNavigationClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            }
        }
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        drawerToggle.drawerArrowDrawable.color =
            ContextCompat.getColor(this, R.color.md_theme_onSurface)
        
        // Update header with current sign-in status
        val account = googleSignInHelper.getLastSignedInAccount()
        updateNavigationHeader(account)
        
        // Set up navigation item selection
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> {
                    openProfileScreen()
                }
                R.id.nav_sign_in -> {
                    signInWithGoogle()
                }
                R.id.nav_sign_out -> {
                    signOut()
                }
                R.id.nav_premium -> {
                    openPremiumScreen()
                }
                R.id.nav_roadmap -> {
                    val intent = Intent(this, FragmentActivity::class.java).apply {
                        putExtra("feature_type", "roadmap")
                    }
                    startActivity(intent)
                }
                R.id.nav_donate -> {
                    showSupportOptionsDialog()
                }
                R.id.nav_advanced -> {
                    selectTab(R.id.navigation_advanced)
                }
                R.id.nav_settings -> {
                    openSettingsScreen()
                }
                // Community & Links
                R.id.nav_website -> {
                    openUrl(Constants.AMNISHIELD_WEBSITE_URL)
                }
                R.id.nav_github -> {
                    openUrl(Constants.GITHUB_REPO_URL)
                }
                R.id.nav_star_github -> {
                    openUrl(Constants.GITHUB_REPO_URL)
                }
                R.id.nav_other_projects -> {
                    openUrl(Constants.ALHAQ_STUDIO_URL)
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun openSettingsScreen() {
        val loader = savedPreferencesLoader
        val pinEnabled = loader.isPinSecurityEnabled()
        val pinCode = loader.getPinCode()

        val needsPin = pinEnabled && pinCode.isNotEmpty() && !AmniShield.isBypassSessionActive()

        if (needsPin) {
            showBypassPinDialog(pinCode, onCancel = {
                selectTab(R.id.navigation_stats)
            }) {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.nav_host_fragment, SettingsFragment())
                    .addToBackStack("settings")
                    .commit()
                setToolbarTitle("Settings")
            }
        } else {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.nav_host_fragment, SettingsFragment())
                .addToBackStack("settings")
                .commit()
            setToolbarTitle("Settings")
        }
    }

    private fun showBypassPinDialog(correctPinCode: String, onCancel: () -> Unit, onSuccess: () -> Unit) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        
        dialog.window?.let { window ->
            window.decorView.setViewTreeLifecycleOwner(this)
            window.decorView.setViewTreeViewModelStoreOwner(this)
            window.decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AmniShieldTheme(appTheme = ThemeUtils.resolveAppTheme(this@MainActivity)) {
                    com.alhaq.amnishield.ui.components.PinPromptContent(
                        correctPin = correctPinCode,
                        title = "Settings Locked",
                        subtitle = "Enter your 4-digit PIN to modify blocker settings",
                        allowForgotPin = true,
                        onDismiss = {
                            dialog.dismiss()
                            onCancel()
                        },
                        onPinSuccess = {
                            AmniShield.unlockBypassSession()
                            dialog.dismiss()
                            onSuccess()
                        },
                        onPinResetCompleted = {
                            AmniShield.unlockBypassSession()
                            dialog.dismiss()
                            onSuccess()
                        }
                    )
                }
            }
        }
        
        dialog.setContentView(composeView)
        dialog.setCancelable(false)
        dialog.show()
    }
    
    private fun updateNavigationHeader(account: AmniShieldAccount?) {
        val headerView = binding.navView.getHeaderView(0)
        val usernameView = headerView.findViewById<android.widget.TextView>(R.id.nav_header_username)
        val emailView = headerView.findViewById<android.widget.TextView>(R.id.nav_header_email)
        val profileImageView = headerView.findViewById<android.widget.ImageView>(R.id.nav_header_profile_image)

        ensureDrawerBranding(headerView, profileImageView, account)
        
        val menu = binding.navView.menu
        val signInItem = menu.findItem(R.id.nav_sign_in)
        val signOutItem = menu.findItem(R.id.nav_sign_out)
        val premiumItem = menu.findItem(R.id.nav_premium)
        
        if (account != null) {
            usernameView.text = account.displayName ?: account.email?.split("@")?.get(0) ?: getString(R.string.guest_user)
            emailView.text = account.email ?: getString(R.string.not_signed_in)
            // Could load profile photo here with Glide or similar
            signInItem.isVisible = false
            signOutItem.isVisible = true
            premiumItem.title = getString(if (premiumManager.isPremium()) R.string.premium_nav_manage else R.string.premium_nav_upgrade)
            if (profileImageView.drawable == null && brandLogoBitmap != null) {
                profileImageView.setImageBitmap(brandLogoBitmap)
            }
        } else {
            usernameView.text = getString(R.string.guest_user)
            emailView.text = getString(R.string.not_signed_in)
            if (brandLogoBitmap != null) {
                profileImageView.setImageBitmap(brandLogoBitmap)
            }
            signInItem.isVisible = true
            signOutItem.isVisible = false
            premiumItem.title = getString(R.string.premium_nav_upgrade)
        }
    }
    
    private fun ensureDrawerBranding(
        headerView: View,
        profileImageView: android.widget.ImageView,
        account: AmniShieldAccount?
    ) {
        drawerBannerDrawable?.let { ViewCompat.setBackground(headerView, it) }
        if (account == null && brandLogoBitmap != null) {
            profileImageView.setImageBitmap(brandLogoBitmap)
        }

        val needsBanner = drawerBannerDrawable == null
        val needsLogo = brandLogoBitmap == null

        if (!needsBanner && (!needsLogo || account != null)) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val banner = if (needsBanner) loadDrawerBannerDrawable() else null
            val logo = if (needsLogo) loadBrandLogoBitmap() else null
            withContext(Dispatchers.Main) {
                banner?.let {
                    drawerBannerDrawable = it
                    ViewCompat.setBackground(headerView, it)
                }
                if (logo != null && brandLogoBitmap == null) {
                    brandLogoBitmap = logo
                }
                if (account == null) {
                    brandLogoBitmap?.let { profileImageView.setImageBitmap(it) }
                }
            }
        }
    }

    private fun loadBrandLogoBitmap(): Bitmap? {
        return runCatching {
            assets.open(BRAND_LOGO_ASSET_PATH).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        }.getOrNull()
    }

    private fun loadDrawerBannerDrawable(): Drawable? {
        return runCatching {
            assets.open(BRAND_BANNER_ASSET_PATH).use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                BitmapDrawable(resources, bitmap)
            }
        }.getOrNull()
    }

    /**
     * Automatically restore premium purchases on app start.
     * This ensures premium status persists across:
     * - App restarts
     * - Device reboots  
     * - App reinstalls
     * - Device changes
     * Works for both real purchases and test purchases (License Test accounts)
     */
    private fun restorePremiumPurchases() {
        // Don't query if already premium
        if (premiumManager.isPremium()) {
            return
        }
        
        val billingWrapper = BillingClientWrapper(this)
        billingWrapper.startConnection {
            billingWrapper.queryPurchases { purchases: List<String> ->
                if (purchases.isNotEmpty()) {
                    // User has active purchases - restore premium status
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
                    openPremiumScreen()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun openPremiumScreen() {
        val intent = Intent(this, FragmentActivity::class.java).apply {
            putExtra("feature_type", "premium_features")
        }
        startActivity(intent)
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInHelper.getSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }
    
    private fun signOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sign_out))
            .setMessage(getString(R.string.sign_out_confirmation))
            .setPositiveButton(getString(R.string.sign_out)) { _, _ ->
                googleSignInHelper.signOut {
                    updateNavigationHeader(null)
                    Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun openProfileScreen() {
        val intent = Intent(this, FragmentActivity::class.java)
        intent.putExtra("fragment", "profile")
        startActivity(intent)
    }
    
    private fun showFAQDialog() {
        val faqItems = arrayOf(
            "How do I enable accessibility services?" to "Go to Settings → Accessibility → AmniShield, then enable the required services. This is needed for all blocking features to work.",
            "What is the Notifications bell icon?" to "The bell icon shows your notification inbox with blocking alerts, daily reports, reminders, and achievements. Tap it to view your notification history.",
            "How does Reel Blocker work?" to "Reel Blocker detects and blocks endless scrolling on Instagram Reels, YouTube Shorts, and TikTok videos, helping you maintain focus.",
            "Why do my blocked apps/keywords disappear?" to "Make sure accessibility services stay enabled. Some system optimizations may disable them. You can check status in Settings.",
            "Can I export my settings?" to "Yes! Go to Settings → Backup &amp; Restore to export/import your configuration.",
            "What is Focus Mode?" to "Focus Mode lets you time-box app restrictions (e.g., block gaming apps for 2 hours). It tracks your focus sessions and shows productivity insights.",
            "How do I disable Anti-Uninstall protection?" to "Go to Settings → Anti-Uninstall, enter your password, and tap Disable. You can then uninstall AmniShield normally.",
            "Is AmniShield really privacy-focused?" to "Yes! All text analysis, keyword detection, and content blocking happens locally on your device. We never send your data to servers.",
            "Is AmniShield open source?" to "Yes! AmniShield is 100% open-source. You can view the full source code, report issues, and contribute on our GitHub repository:<br/><br/><a href=\"${Constants.GITHUB_REPO_URL}\"><b>github.com/alhaq-studio/amnishield-android</b></a><br/><br/>⭐ Please consider starring the repository to support us!",
            "Where can I find the source code?" to "AmniShield's source code is publicly available on GitHub:<br/><br/><a href=\"${Constants.GITHUB_REPO_URL}\"><b>github.com/alhaq-studio/amnishield-android</b></a><br/><br/>You can also explore our other open-source projects at <a href=\"${Constants.ALHAQ_STUDIO_URL}\"><b>alhaq.uk</b></a>",
            "How can I support AmniShield?" to "There are many ways to support AmniShield development:<br/><br/>• <a href=\"${Constants.ALHAQ_INITIATIVE_DONATE_URL}\"><b>Al-Haq Central Funding Hub</b></a><br/>• <a href=\"${Constants.GITHUB_SPONSORS_INITIATIVE_URL}\"><b>GitHub Sponsors (Initiative)</b></a><br/>• <a href=\"${Constants.GITHUB_SPONSORS_PERSONAL_URL}\"><b>GitHub Sponsors (Developer)</b></a><br/>• <a href=\"${Constants.KOFI_URL}\"><b>Ko-fi</b></a><br/>• <a href=\"${Constants.BUY_ME_A_COFFEE_URL}\"><b>Buy Me a Coffee</b></a><br/>• <a href=\"${Constants.PATREON_URL}\"><b>Patreon</b></a><br/><br/>⭐ You can also <a href=\"${Constants.GITHUB_REPO_URL}\"><b>star us on GitHub</b></a> and share AmniShield with others!"
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
    
    private fun showFeedbackDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.feedback_hint)
            minLines = 4
            maxLines = 8
            setPadding(64, 32, 64, 32)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.send_feedback))
            .setView(input)
            .setPositiveButton(getString(R.string.send_feedback)) { _, _ ->
                val feedbackText = input.text.toString()
                if (feedbackText.isNotBlank()) {
                    sendFeedbackEmail(feedbackText)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun sendFeedbackEmail(feedback: String) {
        val account = googleSignInHelper.getLastSignedInAccount()
        val userEmail = account?.email
        val userName = account?.displayName ?: "Anonymous"
        val errorManager = ErrorReportManager.getInstance(this)

        errorManager.saveFeedback(
            UserFeedback(
                category = "General",
                message = feedback,
                rating = 3,
                email = userEmail,
                deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}"
            )
        )

        val emailBody = buildString {
            append("From: ")
            append(userName)
            append("\n")
            append("Device type or model: ")
            append("${Build.MANUFACTURER} ${Build.MODEL}")
            append("\n")
            append("Issue or Feedback:\n")
            append(feedback)
        }

        val attachmentFile = errorManager.createBundledReportFile(prefixText = emailBody)
        if (attachmentFile == null) {
            Toast.makeText(this, getString(R.string.feedback_error), Toast.LENGTH_SHORT).show()
            return
        }
        val attachmentUri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            attachmentFile
        )

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
            putExtra(Intent.EXTRA_TEXT, emailBody)
            putExtra(Intent.EXTRA_CC, SUPPORT_CC_ADDRESSES)
            putExtra(Intent.EXTRA_STREAM, attachmentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        }

        try {
            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_feedback)))
            Toast.makeText(this, getString(R.string.feedback_sent), Toast.LENGTH_SHORT).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.feedback_error), Toast.LENGTH_SHORT).show()
        }
    }

    // onBackPressed override removed; handled by OnBackPressedDispatcher in onCreate.

    private fun initializeNotificationChannels() {
        com.alhaq.amnishield.utils.NotificationHelper.getInstance(this)
    }
    
    private fun scheduleDailyReportsIfPremium() {
        // Daily report notifications will be implemented in future update with WorkManager
        // For now, users can manually access reports from the Reports tab
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

        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AmniShieldTheme(appTheme = ThemeUtils.resolveAppTheme(this@MainActivity)) {
                    com.alhaq.amnishield.ui.components.PinPromptContent(
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

    private companion object {

        private val SUPPORT_CC_ADDRESSES = arrayOf(
            "support@alhaq.uk",
            "alhaq.dst@gmail.com"
        )
        private const val BRAND_LOGO_ASSET_PATH = "icons/Amnishield_Transparent_bg.png"
        private const val BRAND_BANNER_ASSET_PATH = "icons/Blue and Pink Trendy Gradient Technology X-Frame Banner_20251028_204729_0000.png"
    }
}
