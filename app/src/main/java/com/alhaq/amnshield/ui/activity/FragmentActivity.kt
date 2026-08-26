package com.alhaq.amnshield.ui.activity

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.alhaq.amnshield.R
import com.alhaq.amnshield.ui.fragments.anti_uninstall.ChooseModeFragment
import com.alhaq.amnshield.ui.fragments.installation.AccessibilityGuide

import com.alhaq.amnshield.ui.fragments.installation.WelcomeFragment
import com.alhaq.amnshield.ui.fragments.installation.PermissionsFragment
import com.alhaq.amnshield.ui.fragments.features.PremiumFeaturesFragment
import com.alhaq.amnshield.ui.fragments.usage.AllAppsUsageFragment
import com.alhaq.amnshield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnshield.ui.fragments.ManageLaunchLimitsFragment
import com.alhaq.amnshield.ui.fragments.ProfileFragment

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.alhaq.amnshield.AmnShield
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import com.alhaq.amnshield.ui.theme.AmnShieldTheme
import com.alhaq.amnshield.ui.state.AppTheme
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.platform.ViewCompositionStrategy

class FragmentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            com.alhaq.amnshield.utils.ThemeUtils.applyTheme(this)
        } catch (e: Throwable) {
            android.util.Log.e("FragmentActivity", "ThemeUtils.applyTheme failed", e)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        var fragment: Fragment? = null
        
        // Check for feature_type first (new navigation)
        val featureType = intent.getStringExtra("feature_type")
        if (featureType != null) {
            fragment = when (featureType) {
                "focus_mode" -> com.alhaq.amnshield.ui.fragments.FocusFragment()
                "focus_mode_config" -> com.alhaq.amnshield.ui.fragments.features.FocusModeConfigFragment()
                "app_blocker" -> com.alhaq.amnshield.ui.fragments.features.AppBlockerConfigFragment()
                "app_blocker_schedules" -> com.alhaq.amnshield.ui.fragments.BlocksManagerFragment().apply {
                    arguments = Bundle().apply {
                        putString("filter_type", "App Blocker")
                    }
                }
                // "view_blocker" was consolidated into Reel Blocker; route removed to
                // avoid resurrecting the deprecated config screen via legacy intents.
                "reel_blocker" -> com.alhaq.amnshield.ui.fragments.features.ReelBlockerConfigFragment()
                "reels_metrics" -> com.alhaq.amnshield.ui.fragments.usage.ReelsMetricsFragment()
                "website_blocker", "social_media_blocker" -> com.alhaq.amnshield.ui.fragments.features.WebsiteBlockerConfigFragment()
                "usage_tracker" -> com.alhaq.amnshield.ui.fragments.features.UsageTrackerConfigFragment()
                "keyword_blocker" -> com.alhaq.amnshield.ui.fragments.features.KeywordBlockerConfigFragment()
                "anti_uninstall" -> ChooseModeFragment()
                "setup_password_mode" -> com.alhaq.amnshield.ui.fragments.anti_uninstall.SetupPasswordModeFragment()
                "setup_timed_mode" -> com.alhaq.amnshield.ui.fragments.anti_uninstall.SetupTimedModeFragment()
                "additional_features" -> PremiumFeaturesFragment()
                "premium_features" -> PremiumFeaturesFragment()
                "diagnostics", "system_logs", "crash_logs" -> com.alhaq.amnshield.ui.fragments.DiagnosticsFragment()
                else -> null
            }
        }
        
        // Fallback to old fragment navigation
        if (fragment == null && intent.getStringExtra("fragment") != null) {
            when (intent.getStringExtra("fragment")) {
                com.alhaq.amnshield.ui.fragments.features.FocusModeConfigFragment.FRAGMENT_ID -> {
                    fragment = com.alhaq.amnshield.ui.fragments.features.FocusModeConfigFragment()
                }
                ChooseModeFragment.FRAGMENT_ID -> {
                    fragment = ChooseModeFragment()
                }
                AllAppsUsageFragment.FRAGMENT_ID -> {
                    fragment = AllAppsUsageFragment()
                }
                com.alhaq.amnshield.ui.fragments.usage.ReelsMetricsFragment.FRAGMENT_ID -> {
                    fragment = com.alhaq.amnshield.ui.fragments.usage.ReelsMetricsFragment()
                }
                com.alhaq.amnshield.ui.fragments.features.AppBlockerConfigFragment.FRAGMENT_ID -> {
                    fragment = com.alhaq.amnshield.ui.fragments.features.AppBlockerConfigFragment()
                }
                BlocksManagerFragment.FRAGMENT_ID -> {
                    fragment = BlocksManagerFragment()
                }
                ManageLaunchLimitsFragment.FRAGMENT_ID -> {
                    fragment = ManageLaunchLimitsFragment()
                }
                WelcomeFragment.FRAGMENT_ID -> {
                    fragment = WelcomeFragment()
                }

                AccessibilityGuide.FRAGMENT_ID ->
                    fragment = AccessibilityGuide()
                ProfileFragment.FRAGMENT_ID ->
                    fragment = ProfileFragment()
            }
            
            fragment?.arguments = Bundle().apply {
                intent.extras?.let { putAll(it) }
            }
        }
        
        if (fragment != null) {
            // Check if Bypass PIN Lock is enabled and needs verification
            val isBlockerConfig = when (featureType) {
                "focus_mode", "app_blocker", "reel_blocker", "social_media_blocker",
                "usage_tracker", "keyword_blocker", "anti_uninstall", "setup_password_mode",
                "setup_timed_mode" -> true
                else -> {
                    val fragId = intent.getStringExtra("fragment")
                    fragId == ChooseModeFragment.FRAGMENT_ID ||
                    fragId == BlocksManagerFragment.FRAGMENT_ID ||
                    fragId == ManageLaunchLimitsFragment.FRAGMENT_ID
                }
            }

            val loader = SavedPreferencesLoader(this)
            val pinEnabled = loader.isPinSecurityEnabled()
            val pinCode = loader.getPinCode()

            val needsPin = isBlockerConfig && pinEnabled && pinCode.isNotEmpty() && !AmnShield.isBypassSessionActive()

            if (needsPin) {
                showBypassPinDialog(pinCode) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_holder, fragment)
                        .commit()
                }
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, fragment)
                    .commit()
            }
        }
    }

    private fun showBypassPinDialog(correctPinCode: String, onSuccess: () -> Unit) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        
        dialog.window?.let { window ->
            window.decorView.setViewTreeLifecycleOwner(this)
            window.decorView.setViewTreeViewModelStoreOwner(this)
            window.decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AmnShieldTheme(appTheme = com.alhaq.amnshield.utils.ThemeUtils.resolveAppTheme(this@FragmentActivity)) {
                    com.alhaq.amnshield.ui.components.PinPromptContent(
                        correctPin = correctPinCode,
                        title = "Settings Locked",
                        subtitle = "Enter your 4-digit PIN to modify blocker settings",
                        allowForgotPin = true,
                        onDismiss = {
                            dialog.dismiss()
                            finish()
                        },
                        onPinSuccess = {
                            AmnShield.unlockBypassSession()
                            dialog.dismiss()
                            onSuccess()
                        },
                        onPinResetCompleted = {
                            AmnShield.unlockBypassSession()
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
}