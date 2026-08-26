/**
 * ============================================================================
 * AmniShield Blocker Pipeline - AntiUninstallDetector
 * ============================================================================
 * Architecture: Interceptor Pattern (Chain of Responsibility)
 * Priority: 0 (Highest - Fail-Safe System Defense)
 * 
 * Description:
 * Inspects active window nodes to prevent unauthorized removal, system admin
 * deactivation, or bypasses through OEM Security Centers, Settings screens,
 * package installers, and the Google Play Store.
 * 
 * Execution Context:
 * Runs synchronously within [AmnShieldAccessibilityService.onAccessibilityEvent].
 * Main-thread dispatching used for overlay and activity triggers.
 * 
 * Invariants & AI/Developer Guidance:
 * - Do NOT perform blocking network calls or disk I/O in inspect().
 * - Respect the Node Lifecycle Invariant: NEVER recycle rootNode. Only recycle
 *   child nodes created within internal traversal stacks.
 * ============================================================================
 */
package com.alhaq.amnshield.security

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.edit
import com.alhaq.amnshield.Constants
import com.alhaq.amnshield.R
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import com.alhaq.amnshield.ui.activity.AntiUninstallPasswordActivity
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Locale

class AntiUninstallDetector(private val context: Context) {

    companion object {
        private const val PASSWORD_VERIFICATION_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes in-memory grace period
        private const val BLOCK_COOLDOWN_MS = 1500L // 1.5 seconds debounce

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.samsung.android.settings",
            "com.samsung.android.lool",
            "com.samsung.android.sm",
            "com.samsung.android.sm_cn",
            "com.miui.securitycenter",
            "com.miui.cleanmaster",
            "com.miui.securityadd",
            "com.coloros.safecenter",
            "com.coloros.settings",
            "com.oppo.safe",
            "com.iqoo.secure",
            "com.oneplus.security",
            "com.vivo.permissionmanager",
            "com.huawei.systemmanager",
            "com.transsion.phonemanager"
        )

        private val PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.vivo.abe",
            "android"
        )

        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }

    var isAntiUninstallOn: Boolean = false
        private set
    private var antiUninstallMode: Int = -1
    private var savedPassword: String? = null
    private var savedDate: String? = null
    private var savedUnlockAtMillis: Long = 0L

    private var lastBlockTime: Long = 0L

    // In-memory grace period management (never written to persistent storage)
    private var lastPasswordVerificationTime: Long = 0L
    private var isPasswordVerified: Boolean = false

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val info = context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        isAntiUninstallOn = info.getBoolean("is_anti_uninstall_on", false)
        antiUninstallMode = info.getInt("mode", -1)
        savedPassword = info.getString("password", null)
        savedDate = info.getString("date", null)
        savedUnlockAtMillis = info.getLong("unlock_at_millis", 0L)
    }

    /**
     * Called when the user enters the correct password in AntiUninstallPasswordActivity.
     * Starts an in-memory 5-minute admin grace period.
     */
    fun onPasswordVerified() {
        isPasswordVerified = true
        lastPasswordVerificationTime = System.currentTimeMillis()
    }

    private fun isSettingsPackage(packageName: String?): Boolean {
        packageName ?: return false
        if (SETTINGS_PACKAGES.contains(packageName)) return true
        val lower = packageName.lowercase(Locale.ROOT)
        return lower.endsWith(".settings") ||
                lower.contains("securitycenter") ||
                lower.contains("safecenter") ||
                lower.contains("systemmanager") ||
                lower.contains("permissionmanager")
    }

    private fun isPackageInstallerPackage(packageName: String?): Boolean {
        packageName ?: return false
        if (PACKAGE_INSTALLER_PACKAGES.contains(packageName)) return true
        val lower = packageName.lowercase(Locale.ROOT)
        return lower.contains("packageinstaller") || lower.contains("permissioncontroller")
    }

    /**
     * Inspects active window events and nodes for dangerous settings screens or uninstall dialogs.
     * Note: Respects the Node Lifecycle Invariant - NEVER recycles [rootNode].
     *
     * @return true if an anti-uninstall security action was triggered and event should short-circuit.
     */
    fun inspect(event: AccessibilityEvent?, rootNode: AccessibilityNodeInfo?): Boolean {
        if (!isAntiUninstallOn || rootNode == null) return false

        val rootPkg = rootNode.packageName?.toString() ?: event?.packageName?.toString() ?: return false
        val myPackageName = context.packageName

        // Never block our own application or core SystemUI overlays
        if (rootPkg.equals(myPackageName, ignoreCase = true) ||
            rootPkg.equals("com.alhaq.amnshield", ignoreCase = true) ||
            rootPkg.equals("com.android.systemui", ignoreCase = true)
        ) {
            return false
        }

        // Single-pass node hierarchy scan
        val screenData = scanScreenNodes(rootNode)

        // Check if this screen refers to AmnShield / DeenShield
        val hasAmnShieldMention = matchesAppIdentity(screenData.allTextCombined)

        // 1. Google Play Store Details Page & Uninstall Flow
        if (rootPkg.equals(PLAY_STORE_PACKAGE, ignoreCase = true)) {
            if (hasAmnShieldMention && (screenData.hasUninstallAction || screenData.allTextCombined.contains("uninstall"))) {
                return executeAntiUninstallBlock()
            }
            return false
        }

        // 2. Package Installer / Permission Controller Uninstall Confirmation Dialogs
        if (isPackageInstallerPackage(rootPkg)) {
            if (hasAmnShieldMention && (screenData.hasUninstallAction || screenData.allTextCombined.contains("uninstall") || screenData.allTextCombined.contains("remove") || screenData.allTextCombined.contains("delete"))) {
                return executeAntiUninstallBlock()
            }
            return false
        }

        // 3. Dangerous Settings Screens (Device Admin, Accessibility Toggle, App Info Details)
        if (isSettingsPackage(rootPkg)) {
            if (hasAmnShieldMention) {
                // Vector A: Device Admin Deactivation Screen
                val isDeviceAdmin = screenData.allTextCombined.contains("device admin") ||
                        screenData.allTextCombined.contains("device administrators") ||
                        screenData.allTextCombined.contains("admin app") ||
                        screenData.allTextCombined.contains("admin apps") ||
                        screenData.allTextCombined.contains("device policy")

                if (isDeviceAdmin && (screenData.hasDeactivateAction || screenData.hasSwitchOrButton)) {
                    return executeAntiUninstallBlock()
                }

                // Vector B: Accessibility Service Screen for AmnShield
                val isAccessibility = screenData.allTextCombined.contains("accessibility") ||
                        screenData.allTextCombined.contains("use amnishield") ||
                        screenData.allTextCombined.contains("use amnshield") ||
                        screenData.allTextCombined.contains("use service") ||
                        screenData.allTextCombined.contains("installed services") ||
                        screenData.allTextCombined.contains("downloaded apps") ||
                        screenData.allTextCombined.contains("downloaded services")

                if (isAccessibility && (screenData.hasToggleContext || screenData.hasSwitchOrButton)) {
                    return executeAntiUninstallBlock()
                }

                // Vector C: App Info Details / Storage Clear / Force Stop
                val isAppInfo = screenData.hasUninstallAction ||
                        screenData.allTextCombined.contains("force stop") ||
                        screenData.allTextCombined.contains("clear storage") ||
                        screenData.allTextCombined.contains("clear data") ||
                        screenData.allTextCombined.contains("manage space") ||
                        screenData.allTextCombined.contains("storage & cache") ||
                        screenData.allTextCombined.contains("disable")

                if (isAppInfo) {
                    return executeAntiUninstallBlock()
                }
            }
        }

        return false
    }

    private fun matchesAppIdentity(text: String): Boolean {
        if (text.contains("amnishield") ||
            text.contains("amnshield") ||
            text.contains("deenshield") ||
            text.contains("deen shield") ||
            text.contains("amni shield") ||
            text.contains("amn shield") ||
            text.contains("com.alhaq.amnshield") ||
            text.contains("com.alhaq.deenshield") ||
            text.contains(context.packageName.lowercase(Locale.ROOT))
        ) {
            return true
        }

        val appLabel = getAppName(context.packageName).lowercase(Locale.ROOT)
        return appLabel.isNotBlank() && text.contains(appLabel)
    }

    private data class ScreenData(
        val allTextCombined: String,
        val hasUninstallAction: Boolean,
        val hasDeactivateAction: Boolean,
        val hasToggleContext: Boolean,
        val hasSwitchOrButton: Boolean
    )

    private fun scanScreenNodes(root: AccessibilityNodeInfo): ScreenData {
        val sb = StringBuilder()
        var hasUninstall = false
        var hasDeactivate = false
        var hasToggle = false
        var hasSwitchOrButton = false

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            val current = stack.pop()

            val nodeText = current.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
            val nodeDesc = current.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
            val className = current.className?.toString() ?: ""

            if (nodeText.isNotBlank()) {
                sb.append(nodeText).append(" ")
            }
            if (nodeDesc.isNotBlank() && nodeDesc != nodeText) {
                sb.append(nodeDesc).append(" ")
            }

            if (nodeText.contains("uninstall") || nodeDesc.contains("uninstall") ||
                nodeText.contains("remove") || nodeDesc.contains("remove") ||
                nodeText.contains("delete") || nodeDesc.contains("delete")
            ) {
                hasUninstall = true
            }

            if (nodeText.contains("deactivate") || nodeDesc.contains("deactivate") ||
                nodeText.contains("turn off") || nodeDesc.contains("turn off") ||
                nodeText.contains("disable") || nodeDesc.contains("disable")
            ) {
                hasDeactivate = true
            }

            if (nodeText.contains("use service") || nodeText.contains("use amnishield") ||
                nodeText.contains("use amnshield") || nodeText.contains("shortcut") ||
                nodeText.contains("stop") || nodeText.contains("turn on")
            ) {
                hasToggle = true
            }

            if (className.contains("Switch") || className.contains("ToggleButton") ||
                className.contains("CheckBox") || className.contains("Button") || current.isClickable
            ) {
                hasSwitchOrButton = true
            }

            for (i in 0 until current.childCount) {
                val child = current.getChild(i)
                if (child != null) {
                    stack.push(child)
                }
            }

            if (current != root) {
                try {
                    @Suppress("DEPRECATION")
                    current.recycle()
                } catch (_: Exception) {}
            }
        }

        return ScreenData(
            allTextCombined = sb.toString(),
            hasUninstallAction = hasUninstall,
            hasDeactivateAction = hasDeactivate,
            hasToggleContext = hasToggle,
            hasSwitchOrButton = hasSwitchOrButton
        )
    }

    private fun executeAntiUninstallBlock(): Boolean {
        if (!shouldBlockRemoval()) return false

        val currentTime = System.currentTimeMillis()
        // In-memory 5-minute admin grace period check
        if (isPasswordVerified && (currentTime - lastPasswordVerificationTime) < PASSWORD_VERIFICATION_COOLDOWN_MS) {
            return false
        }

        if (currentTime - lastBlockTime > BLOCK_COOLDOWN_MS) {
            lastBlockTime = currentTime
            handleAntiUninstallAttempt()
            return true
        }
        return false
    }

    private val loader by lazy { SavedPreferencesLoader(context) }

    private fun shouldBlockRemoval(): Boolean {
        if (!isAntiUninstallOn) {
            return false
        }

        // If emergency unlock window is active (10 minutes after emergency cooldown finishes), allow administrative access
        if (loader.isEmergencyWindowActive()) {
            return false
        }

        return when (antiUninstallMode) {
            Constants.ANTI_UNINSTALL_PASSWORD_MODE -> true
            Constants.ANTI_UNINSTALL_TIMED_MODE -> checkIfDateNotReached()
            else -> false
        }
    }

    private fun checkIfDateNotReached(): Boolean {
        val targetUnlockMillis = if (savedUnlockAtMillis > 0L) {
            savedUnlockAtMillis
        } else if (savedDate != null) {
            try {
                val parts = savedDate!!.split("/")
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

        if (targetUnlockMillis > 0L) {
            val now = System.currentTimeMillis()
            if (now >= targetUnlockMillis) {
                val info = context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
                info.edit {
                    putBoolean("is_anti_uninstall_on", false)
                    remove("unlock_at_millis")
                    remove("date")
                }
                isAntiUninstallOn = false

                Handler(Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.anti_uninstall_timed_mode_expired),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }

                val refreshIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                    .setPackage(context.packageName)
                context.sendBroadcast(refreshIntent)

                return false
            }
            return true
        }

        return false
    }

    private fun handleAntiUninstallAttempt() {
        // Immediate short-circuit: force dismiss dangerous system dialogue/page
        try {
            (context as? AccessibilityService)?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } catch (_: Exception) {}

        // Launch password / timed notice activity on top
        val intent = Intent(context, AntiUninstallPasswordActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            "AmniShield"
        }
    }
}
