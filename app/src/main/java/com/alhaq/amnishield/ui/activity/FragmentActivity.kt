package com.alhaq.amnishield.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.navigation.FragmentDestination
import com.alhaq.amnishield.AmniShield
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.platform.ViewCompositionStrategy

class FragmentActivity : AppCompatActivity() {

    companion object {
        fun createIntent(
            context: Context,
            destination: FragmentDestination,
            extras: Bundle? = null
        ): Intent {
            return Intent(context, FragmentActivity::class.java).apply {
                putExtra(FragmentDestination.EXTRA_DESTINATION_KEY, destination.key)
                putExtra(FragmentDestination.EXTRA_FEATURE_TYPE, destination.key)
                putExtra(FragmentDestination.EXTRA_FRAGMENT, destination.key)
                when (destination) {
                    is FragmentDestination.AppBlockerSchedules -> {
                        putExtra("filter_type", destination.filterType)
                    }
                    is FragmentDestination.BlocksManager -> {
                        destination.filterType?.let { putExtra("filter_type", it) }
                    }
                    else -> Unit
                }
                if (extras != null) {
                    putExtras(extras)
                }
            }
        }

        fun launch(
            context: Context,
            destination: FragmentDestination,
            extras: Bundle? = null
        ) {
            context.startActivity(createIntent(context, destination, extras))
        }
    }

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
        val fragment: Fragment? = destination?.createFragment()?.also { createdFragment ->
            val routeDefaults = createdFragment.arguments ?: Bundle()
            val mergedArgs = Bundle()
            intent.extras?.let { mergedArgs.putAll(it) }
            mergedArgs.putAll(routeDefaults)
            if (!mergedArgs.isEmpty) {
                createdFragment.arguments = mergedArgs
            }
        }

        if (fragment != null) {
            val isBlockerConfig = destination.requiresBypassPin

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