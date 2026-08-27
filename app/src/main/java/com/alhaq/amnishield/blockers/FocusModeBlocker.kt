/**
 * ============================================================================
 * AmniShield Blocker Pipeline - FocusModeBlocker
 * ============================================================================
 * Architecture: Interceptor Pattern (Chain of Responsibility)
 * Priority: Focus Space & Productivity Lockdown
 * 
 * Description:
 * Evaluates active quick focus sessions and scheduled focus mode rules.
 * Enforces either Blacklist (Block Distracting Apps) or Strict Whitelist
 * (Block All Except Selected Apps) while strictly exempting essential system tools
 * and user-defined Always-Whitelisted Emergency Apps.
 * 
 * Execution Context:
 * Synchronous accessibility node evaluation within AmniShieldAccessibilityService.
 * ============================================================================
 */
package com.alhaq.amnishield.blockers

import android.content.Context
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.utils.SavedPreferencesLoader

class FocusModeBlocker : BaseBlocker() {

    companion object {
        const val STRICTNESS_MINDFUL_PAUSE = 0
        const val STRICTNESS_HARD_LOCK = 1

        // Essential system apps that should NEVER be blocked to prevent system instability
        val ESSENTIAL_SYSTEM_APPS = setOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.launcher",
            "com.samsung.android.settings",
            "com.coloros.settings",
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.huawei.android.launcher",
            "com.miui.mihome2",
            "com.mi.android.globallauncher",
            "com.android.dialer",
            "com.android.phone",
            "com.android.contacts",
            "com.android.mms",
            "com.android.messaging",
            "com.google.android.dialer",
            "com.google.android.contacts",
            "com.samsung.android.dialer",
            "com.samsung.android.contacts",
            "com.alhaq.amnishield",
            
            // OEM Security/Phone Managers
            "com.huawei.systemmanager",
            "com.miui.securitycenter",
            "com.iqoo.secure",
            "com.oppo.safe",
            "com.oneplus.security",
            "com.vivo.permissionmanager",
            "com.samsung.android.lool",
            "com.samsung.android.sm",
            "com.samsung.android.sm_cn",
            "com.coloros.safecenter",
            "com.coloros.securityguard",

            // System Keyboards
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.android.inputmethod.latin",
            "com.huawei.android.inputmethod",
            "com.miui.miinput",

            // Clocks & Alarms
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.deskclock",
            "com.huawei.deskclock",
            "com.miui.clock",
            "com.coloros.alarm",
            "com.oppo.alarm",

            // Calendars
            "com.google.android.calendar",
            "com.android.calendar",
            "com.samsung.android.calendar",
            "com.miui.calendar",

            // Calculators
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.android.calculator2",
            "com.miui.calculator",
            "com.coloros.calculator",

            // File Managers / Documents Provider
            "com.google.android.apps.nbu.files",
            "com.sec.android.app.myfiles",
            "com.android.documentsui",
            "com.mi.android.globalFileexplorer",
            "com.coloros.filemanager",

            // System Package Installer & Google Play Services
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.vending",
            "com.google.android.gms"
        )
    }

    var focusModeData = FocusModeData()

    fun update(data: FocusModeData) {
        this.focusModeData = data
    }

    /**
     * Check if an app needs to be blocked for reasons related to focus mode.
     */
    fun doesAppNeedToBeBlocked(
        context: Context,
        packageName: String,
        savedPreferencesLoader: SavedPreferencesLoader,
        defaultLauncher: String? = null
    ): FocusModeResult {
        // 1. NEVER block essential system apps or AmniShield itself
        if (ESSENTIAL_SYSTEM_APPS.contains(packageName) ||
            packageName.equals("com.alhaq.amnishield", ignoreCase = true) ||
            packageName.equals("com.alhaq.deenshield", ignoreCase = true) ||
            packageName.startsWith("com.alhaq.deenshield.", ignoreCase = true) ||
            (defaultLauncher != null && packageName == defaultLauncher)
        ) {
            return FocusModeResult(isBlocked = false)
        }

        // 2. NEVER block user-configured Always-Whitelisted Emergency Apps
        val alwaysWhitelisted = savedPreferencesLoader.getAlwaysWhitelistedApps()
        if (alwaysWhitelisted.contains(packageName)) {
            return FocusModeResult(isBlocked = false)
        }

        val isStrict = savedPreferencesLoader.getFocusModeStrictness() == STRICTNESS_HARD_LOCK

        // 3. Check active Quick Focus session
        if (focusModeData.isTurnedOn) {
            if (focusModeData.endTime > 0 && System.currentTimeMillis() >= focusModeData.endTime) {
                focusModeData.isTurnedOn = false
                return FocusModeResult(isBlocked = false, isRequestingToUpdateSPData = true)
            }
            return evaluateBlocking(packageName, focusModeData.modeType, focusModeData.selectedApps, focusModeData.endTime, isStrict)
        }

        return FocusModeResult(isBlocked = false)
    }

    private fun evaluateBlocking(
        packageName: String,
        modeType: Int,
        selectedApps: Set<String>,
        endTime: Long,
        isStrict: Boolean
    ): FocusModeResult {
        when (modeType) {
            Constants.FOCUS_MODE_BLOCK_SELECTED -> {
                if (selectedApps.contains(packageName)) {
                    return FocusModeResult(
                        isBlocked = true,
                        focusModeEndTime = endTime,
                        isStrict = isStrict
                    )
                }
            }
            Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED -> {
                if (!selectedApps.contains(packageName)) {
                    return FocusModeResult(
                        isBlocked = true,
                        focusModeEndTime = endTime,
                        isStrict = isStrict
                    )
                }
            }
        }
        return FocusModeResult(isBlocked = false)
    }

    /**
     * Stores information related to quick focus mode sessions
     */
    data class FocusModeData(
        var isTurnedOn: Boolean = false,
        val endTime: Long = -1,
        val modeType: Int = Constants.FOCUS_MODE_BLOCK_SELECTED,
        var selectedApps: HashSet<String> = hashSetOf()
    )

    /**
     * Focus mode blocker check result
     */
    data class FocusModeResult(
        val isBlocked: Boolean,
        val focusModeEndTime: Long = -1,
        val isRequestingToUpdateSPData: Boolean = false,
        val isStrict: Boolean = false
    )
}