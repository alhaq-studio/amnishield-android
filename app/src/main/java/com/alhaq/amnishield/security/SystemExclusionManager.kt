package com.alhaq.amnishield.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.alhaq.amnishield.utils.SavedPreferencesLoader

/**
 * Authoritative central safety manager governing system exclusions across the AmniShield ecosystem.
 *
 * Guarantees that emergency dialers (911, 999, 112), SOS services, active keyboards (IMEs),
 * home launchers, and essential system utilities are never blocked under any circumstances.
 *
 * Also implements the Settings App Safety Policy:
 * - In Focus Mode "Block All", the system settings app is unblocked by default (sub-screens
 *   like accessibility/device admin are protected by AntiUninstallDetector if enabled).
 * - Custom App Blocker checks this manager to enforce always-whitelisted and emergency apps.
 */
object SystemExclusionManager {

    // Core Android Framework & System UI
    private val OS_FRAMEWORK_PACKAGES = setOf(
        "android",
        "com.android.systemui"
    )

    // Emergency Services & SOS Hubs
    private val EMERGENCY_PACKAGES = setOf(
        "com.android.emergency",
        "com.sec.android.app.emergency",
        "com.sec.android.app.emergencyprovider",
        "com.samsung.android.emergency",
        "com.samsung.android.emergencyprovider",
        "com.samsung.android.incallui",
        "com.google.android.apps.safetyhub"
    )

    // System Telephony & Default OEM Dialers
    private val STATIC_DIALER_PACKAGES = setOf(
        "com.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.huawei.android.dialer",
        "com.oppo.dialer",
        "com.vivo.dialer",
        "com.oneplus.dialer"
    )

    // System Settings Apps
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.settings",
        "com.samsung.android.settings",
        "com.coloros.settings",
        "com.oppo.settings",
        "com.vivo.settings",
        "com.oneplus.settings",
        "com.miui.securitycenter"
    )

    // Core Package Management & Permission Controllers
    private val PACKAGE_MANAGEMENT_PACKAGES = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.vending",
        "org.fdroid.fdroid"
    )

    // Static OEM Launchers (fallback alongside dynamic resolution)
    private val STATIC_LAUNCHER_PACKAGES = setOf(
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.huawei.android.launcher",
        "com.miui.mihome2",
        "com.mi.android.globallauncher",
        "com.coloros.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher",
        "com.oneplus.launcher"
    )

    // Static Popular Keyboards (fallback alongside dynamic resolution)
    private val STATIC_KEYBOARD_PACKAGES = setOf(
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.inputmethod.latin",
        "com.huawei.android.inputmethod",
        "com.miui.miinput",
        "com.swiftkey.swiftkeyapp",
        "com.touchtype.swiftkey"
    )

    // Clocks & Alarms (ensures alarms always fire during focus sessions)
    private val ALARM_PACKAGES = setOf(
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.deskclock",
        "com.huawei.deskclock",
        "com.miui.clock",
        "com.coloros.alarm",
        "com.oppo.alarm"
    )

    /**
     * Determines whether the given package is an Android Settings application.
     */
    fun isSettingsApp(packageName: String): Boolean {
        return SETTINGS_PACKAGES.contains(packageName)
    }

    /**
     * Checks whether an application is an Emergency service, SOS package, or Dialer.
     */
    fun isEmergencyOrDialer(packageName: String, context: Context): Boolean {
        if (EMERGENCY_PACKAGES.contains(packageName) || STATIC_DIALER_PACKAGES.contains(packageName)) {
            return true
        }

        // Dynamically resolve default dialer
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultDialer = telecomManager?.defaultDialerPackage
            if (!defaultDialer.isNullOrEmpty() && defaultDialer.equals(packageName, ignoreCase = true)) {
                return true
            }
        } catch (_: Throwable) {}

        return false
    }

    /**
     * Checks whether an application is an active or installed system keyboard (IME).
     */
    fun isSystemInputMethod(packageName: String, context: Context): Boolean {
        if (STATIC_KEYBOARD_PACKAGES.contains(packageName)) {
            return true
        }

        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val enabledMethods = imm?.enabledInputMethodList ?: emptyList()
            for (method in enabledMethods) {
                if (method.packageName.equals(packageName, ignoreCase = true)) {
                    return true
                }
            }
        } catch (_: Throwable) {}

        return false
    }

    /**
     * Checks whether an application is the default home launcher.
     */
    fun isLauncher(packageName: String, context: Context, cachedDefaultLauncher: String? = null): Boolean {
        if (!cachedDefaultLauncher.isNullOrEmpty() && cachedDefaultLauncher.equals(packageName, ignoreCase = true)) {
            return true
        }

        if (STATIC_LAUNCHER_PACKAGES.contains(packageName)) {
            return true
        }

        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            val resolvedPackage = resolveInfo?.activityInfo?.packageName
            if (!resolvedPackage.isNullOrEmpty() && resolvedPackage.equals(packageName, ignoreCase = true)) {
                return true
            }
        } catch (_: Throwable) {}

        return false
    }

    /**
     * Checks whether an application is a core system utility (framework, installer, or alarm).
     */
    fun isCoreSystemUtility(packageName: String): Boolean {
        return OS_FRAMEWORK_PACKAGES.contains(packageName) ||
               PACKAGE_MANAGEMENT_PACKAGES.contains(packageName) ||
               ALARM_PACKAGES.contains(packageName)
    }

    /**
     * Authoritative check for whether an application is exempt from blocking.
     *
     * @param packageName The application package name being evaluated.
     * @param context Application or Service context.
     * @param isFocusModeBlockAll Whether this evaluation is occurring in Focus Mode "Block All" mode.
     * @param savedPreferencesLoader Optional loader to verify user's Always-Whitelisted apps.
     * @param cachedDefaultLauncher Optional pre-cached default launcher package name.
     * @return true if the package is exempt and must NEVER be blocked.
     */
    fun isExempt(
        packageName: String,
        context: Context,
        isFocusModeBlockAll: Boolean = false,
        savedPreferencesLoader: SavedPreferencesLoader? = null,
        cachedDefaultLauncher: String? = null
    ): Boolean {
        if (packageName.isBlank()) return true

        // 1. AmniShield and sister apps must never block themselves
        val myPackage = context.packageName
        if (packageName.equals(myPackage, ignoreCase = true) ||
            packageName.equals("com.alhaq.amnishield", ignoreCase = true) ||
            packageName.equals("com.alhaq.deenshield", ignoreCase = true) ||
            packageName.startsWith("com.alhaq.deenshield.", ignoreCase = true)
        ) {
            return true
        }

        // 2. Android Framework & System UI
        if (OS_FRAMEWORK_PACKAGES.contains(packageName)) {
            return true
        }

        // 3. Emergency services, SOS hubs, and Telephony/Dialers
        if (isEmergencyOrDialer(packageName, context)) {
            return true
        }

        // 4. Active or static keyboards (IMEs)
        if (isSystemInputMethod(packageName, context)) {
            return true
        }

        // 5. Default home launcher
        if (isLauncher(packageName, context, cachedDefaultLauncher)) {
            return true
        }

        // 6. Package installer & alarm clocks
        if (isCoreSystemUtility(packageName)) {
            return true
        }

        // 7. Settings App handling: unblocked by default in Focus Mode "Block All"
        if (isFocusModeBlockAll && isSettingsApp(packageName)) {
            return true
        }

        // 8. User-configured Always-Whitelisted apps
        if (savedPreferencesLoader != null) {
            val alwaysWhitelisted = savedPreferencesLoader.getAlwaysWhitelistedApps()
            if (alwaysWhitelisted.contains(packageName)) {
                return true
            }
        }

        return false
    }
}
