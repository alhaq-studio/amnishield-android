package com.alhaq.amnishield

import android.content.Context
import android.content.SharedPreferences
import com.alhaq.amnishield.security.SystemExclusionManager
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemExclusionManagerTest {

    private lateinit var fakePrefs: InMemorySharedPreferences
    private lateinit var context: Context
    private lateinit var loader: SavedPreferencesLoader

    @Before
    fun setup() {
        fakePrefs = InMemorySharedPreferences()
        context = FakeContext(fakePrefs)
        loader = SavedPreferencesLoader(
            context = context,
            injectedCompassionatePrefs = fakePrefs,
            injectedPremiumPrefs = fakePrefs
        )
    }

    private class FakeContext(
        private val prefs: SharedPreferences
    ) : android.content.ContextWrapper(null) {
        override fun getPackageName(): String = "com.alhaq.amnishield"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getSystemService(name: String): Any? = null
    }

    @Test
    fun testSettingsAppRecognition() {
        assertTrue("com.android.settings must be recognized as settings", SystemExclusionManager.isSettingsApp("com.android.settings"))
        assertTrue("com.google.android.settings must be recognized as settings", SystemExclusionManager.isSettingsApp("com.google.android.settings"))
        assertTrue("com.samsung.android.settings must be recognized as settings", SystemExclusionManager.isSettingsApp("com.samsung.android.settings"))
        assertFalse("com.instagram.android must NOT be recognized as settings", SystemExclusionManager.isSettingsApp("com.instagram.android"))
    }

    @Test
    fun testCoreSystemUtilities() {
        assertTrue("android framework must be core utility", SystemExclusionManager.isCoreSystemUtility("android"))
        assertTrue("com.android.systemui must be core utility", SystemExclusionManager.isCoreSystemUtility("com.android.systemui"))
        assertTrue("com.android.packageinstaller must be core utility", SystemExclusionManager.isCoreSystemUtility("com.android.packageinstaller"))
        assertTrue("com.google.android.deskclock must be core utility", SystemExclusionManager.isCoreSystemUtility("com.google.android.deskclock"))
        assertFalse("com.zhiliaoapp.musically must NOT be core utility", SystemExclusionManager.isCoreSystemUtility("com.zhiliaoapp.musically"))
    }

    @Test
    fun testEmergencyAndDialerPackages() {
        assertTrue("com.android.emergency must be exempt", SystemExclusionManager.isEmergencyOrDialer("com.android.emergency", context))
        assertTrue("com.sec.android.app.emergency must be exempt", SystemExclusionManager.isEmergencyOrDialer("com.sec.android.app.emergency", context))
        assertTrue("com.google.android.apps.safetyhub must be exempt", SystemExclusionManager.isEmergencyOrDialer("com.google.android.apps.safetyhub", context))
        assertTrue("com.android.dialer must be exempt", SystemExclusionManager.isEmergencyOrDialer("com.android.dialer", context))
        assertTrue("com.google.android.dialer must be exempt", SystemExclusionManager.isEmergencyOrDialer("com.google.android.dialer", context))
        assertTrue("com.samsung.android.dialer must be exempt", SystemExclusionManager.isEmergencyOrDialer("com.samsung.android.dialer", context))
        assertFalse("com.twitter.android must NOT be emergency or dialer", SystemExclusionManager.isEmergencyOrDialer("com.twitter.android", context))
    }

    @Test
    fun testStaticKeyboardsAndLaunchers() {
        assertTrue("Gboard must be recognized as keyboard", SystemExclusionManager.isSystemInputMethod("com.google.android.inputmethod.latin", context))
        assertTrue("Samsung Honeyboard must be recognized as keyboard", SystemExclusionManager.isSystemInputMethod("com.samsung.android.honeyboard", context))
        assertTrue("Samsung launcher must be recognized as launcher", SystemExclusionManager.isLauncher("com.sec.android.app.launcher", context))
        assertTrue("Pixel launcher must be recognized as launcher", SystemExclusionManager.isLauncher("com.google.android.apps.nexuslauncher", context))
    }

    @Test
    fun testFocusModeBlockAllSettingsExemption() {
        // In Focus Mode "Block All", Settings is unblocked by default
        val isExemptInBlockAll = SystemExclusionManager.isExempt(
            packageName = "com.android.settings",
            context = context,
            isFocusModeBlockAll = true,
            savedPreferencesLoader = loader
        )
        assertTrue("com.android.settings must be exempt in Focus Mode Block All", isExemptInBlockAll)

        // In standard mode (not Block All), Settings is not automatically exempt
        val isExemptStandard = SystemExclusionManager.isExempt(
            packageName = "com.android.settings",
            context = context,
            isFocusModeBlockAll = false,
            savedPreferencesLoader = loader
        )
        assertFalse("com.android.settings is not automatically exempt in standard mode", isExemptStandard)
    }

    @Test
    fun testNormalUserAppsAreNotExempt() {
        val apps = listOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.zhiliaoapp.musically",
            "com.reddit.frontpage",
            "com.snapchat.android"
        )
        apps.forEach { app ->
            val isExempt = SystemExclusionManager.isExempt(
                packageName = app,
                context = context,
                isFocusModeBlockAll = true,
                savedPreferencesLoader = loader
            )
            assertFalse("App $app must NOT be exempt", isExempt)
        }
    }

    @Test
    fun testAlwaysWhitelistedEmergencyAppsAreExempt() {
        val userEmergencyApp = "com.whatsapp"
        loader.saveAlwaysWhitelistedApps(listOf(userEmergencyApp))

        val isExempt = SystemExclusionManager.isExempt(
            packageName = userEmergencyApp,
            context = context,
            isFocusModeBlockAll = true,
            savedPreferencesLoader = loader
        )
        assertTrue("User always-whitelisted app must be exempt", isExempt)
    }

    @Test
    fun testAmniShieldSelfExemption() {
        assertTrue("AmniShield must never block itself", SystemExclusionManager.isExempt("com.alhaq.amnishield", context))
        assertTrue("Sister package com.alhaq.deenshield must be exempt", SystemExclusionManager.isExempt("com.alhaq.deenshield", context))
    }
}
