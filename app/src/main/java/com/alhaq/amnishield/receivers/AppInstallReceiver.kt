package com.alhaq.amnishield.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alhaq.amnishield.data.blockers.AppInterceptionLevel
import com.alhaq.amnishield.data.blockers.BlockRuleStatus
import com.alhaq.amnishield.data.blockers.PackageWand
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives broadcast when new apps are installed and automatically blocks them
 * if any UnifiedBlockRule has blockNewlyInstalledApps enabled or if they belong
 * to selected auto-block categories.
 */
class AppInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        // Only process package additions (ignore replacements/updates)
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        // Get the package name of the newly installed app
        val packageName = intent.data?.schemeSpecificPart ?: return

        // Don't auto-block AmniShield itself
        if (packageName == context.packageName || packageName == "com.alhaq.amnishield") return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefsLoader = SavedPreferencesLoader(appContext)

                // 1. Check UnifiedBlockRules where blockNewlyInstalledApps == true
                runCatching {
                    val unifiedRules = prefsLoader.loadUnifiedBlockRules()
                    var unifiedModified = false
                    val updatedRules = unifiedRules.map { rule ->
                        if (rule.status != BlockRuleStatus.ARCHIVED && rule.blockNewlyInstalledApps) {
                            if (!rule.appTargets.containsKey(packageName)) {
                                unifiedModified = true
                                val newTargets = rule.appTargets.toMutableMap().apply {
                                    put(packageName, AppInterceptionLevel.BLOCK_ENTIRE_APP)
                                }
                                rule.copy(appTargets = newTargets)
                            } else {
                                rule
                            }
                        } else {
                            rule
                        }
                    }
                    if (unifiedModified) {
                        prefsLoader.saveUnifiedBlockRules(updatedRules, syncToEngines = true)
                        Log.i("AppInstallReceiver", "Auto-added newly installed app $packageName to UnifiedBlockRules")
                    }
                }.onFailure {
                    Log.e("AppInstallReceiver", "Failed to update UnifiedBlockRules on package install", it)
                }

                // 2. Legacy category-based auto-block
                if (!prefsLoader.isAutoBlockEnabled()) return@launch

                val autoBlockCategories = prefsLoader.getAutoBlockCategories()
                if (autoBlockCategories.isEmpty()) return@launch

                val appInfo = try {
                    appContext.packageManager.getApplicationInfo(packageName, 0)
                } catch (e: Exception) {
                    null
                }

                val appCategory = PackageWand.getCategoryForPackage(packageName, appInfo)

                if (appCategory != null && autoBlockCategories.contains(appCategory)) {
                    val currentBlockedApps = prefsLoader.loadBlockedApps().toMutableSet()
                    currentBlockedApps.add(packageName)
                    prefsLoader.saveBlockedApps(currentBlockedApps)

                    val refreshIntent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
                        .setPackage(appContext.packageName)
                    appContext.sendBroadcast(refreshIntent)

                    Log.i("AppInstallReceiver", "Auto-blocked newly installed app: $packageName (category: $appCategory)")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
