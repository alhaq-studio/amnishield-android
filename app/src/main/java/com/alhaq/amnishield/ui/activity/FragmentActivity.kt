package com.alhaq.amnishield.ui.activity

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.fragments.anti_uninstall.ChooseModeFragment
import com.alhaq.amnishield.ui.fragments.installation.AccessibilityGuide

import com.alhaq.amnishield.ui.fragments.installation.WelcomeFragment
import com.alhaq.amnishield.ui.fragments.installation.PermissionsFragment
import com.alhaq.amnishield.ui.fragments.features.PremiumFeaturesFragment
import com.alhaq.amnishield.ui.fragments.usage.AllAppsUsageFragment
import com.alhaq.amnishield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnishield.ui.fragments.ManageLaunchLimitsFragment
import com.alhaq.amnishield.ui.fragments.ProfileFragment
import com.alhaq.amnishield.ui.navigation.FragmentDestination

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
import com.alhaq.amnishield.AmniShield
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.ui.state.AppTheme
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.platform.ViewCompositionStrategy

class FragmentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            com.alhaq.amnishield.utils.ThemeUtils.applyTheme(this)
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

        val destination = FragmentDestination.fromIntent(intent)
        val fragment: Fragment? = destination?.createFragment() ?: when (intent.getStringExtra("fragment")) {
            com.alhaq.amnishield.ui.fragments.installation.PermissionsFragment.FRAGMENT_ID -> PermissionsFragment()
            else -> null
        }

        if (fragment != null) {
            fragment.arguments = Bundle().apply {
                fragment.arguments?.let { putAll(it) }
                intent.extras?.let { putAll(it) }
            }

            val isBlockerConfig = destination?.requiresBypassPin ?: run {
                val fragId = intent.getStringExtra("fragment")
                fragId == ChooseModeFragment.FRAGMENT_ID ||
                    fragId == BlocksManagerFragment.FRAGMENT_ID ||
                    fragId == ManageLaunchLimitsFragment.FRAGMENT_ID
            }

            val loader = SavedPreferencesLoader(this)
            val pinEnabled = loader.isPinSecurityEnabled()
            val pinCode = loader.getPinCode()

            val needsPin = isBlockerConfig && pinEnabled && pinCode.isNotEmpty() && !AmniShield.isBypassSessionActive()

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
                AmniShieldTheme(appTheme = com.alhaq.amnishield.utils.ThemeUtils.resolveAppTheme(this@FragmentActivity)) {
                    com.alhaq.amnishield.ui.components.PinPromptContent(
                        correctPin = correctPinCode,
                        title = "Settings Locked",
                        subtitle = "Enter your 4-digit PIN to modify blocker settings",
                        allowForgotPin = true,
                        onDismiss = {
                            dialog.dismiss()
                            finish()
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
}