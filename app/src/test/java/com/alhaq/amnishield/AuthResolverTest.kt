package com.alhaq.amnishield

import android.content.Context
import android.content.SharedPreferences
import com.alhaq.amnishield.data.blockers.BaseRule
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthResult
import com.alhaq.amnishield.security.AuthTarget
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.alhaq.amnishield.utils.PasswordHasher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class AuthResolverTest {

    private lateinit var fakeSecurityPrefs: InMemorySharedPreferences
    private lateinit var fakeAntiUninstallPrefs: InMemorySharedPreferences
    private lateinit var authResolver: AuthResolver

    @Before
    fun setup() {
        fakeSecurityPrefs = InMemorySharedPreferences()
        fakeAntiUninstallPrefs = InMemorySharedPreferences()
        authResolver = AuthResolver(
            injectedSecurityPrefs = fakeSecurityPrefs,
            injectedAntiUninstallPrefs = fakeAntiUninstallPrefs
        )
    }

    private data class DummyRule(
        override val id: String = "rule_1",
        override val authType: AuthType = AuthType.NONE,
        override val rulePasswordHash: String? = null,
        override val rulePasswordSalt: String? = null
    ) : BaseRule

    @Test
    fun testResolveChallengeNoAuthReturnsPassThrough() {
        val rule = DummyRule(authType = AuthType.NONE)
        val target = authResolver.resolveChallenge(rule)
        assertTrue("Rule with AuthType.NONE must resolve to PassThrough", target is AuthTarget.PassThrough)
    }

    @Test
    fun testResolveChallengeRulePinReturnsRequireRulePin() {
        val pin = "1234"
        val (hash, salt) = authResolver.createRulePin(pin)
        val rule = DummyRule(id = "custom_rule", authType = AuthType.RULE_PIN, rulePasswordHash = hash, rulePasswordSalt = salt)

        val target = authResolver.resolveChallenge(rule)
        assertTrue("Rule with RULE_PIN must resolve to RequireRulePin", target is AuthTarget.RequireRulePin)
        val ruleTarget = target as AuthTarget.RequireRulePin
        assertEquals("custom_rule", ruleTarget.ruleId)
        assertEquals(hash, ruleTarget.hash)
        assertEquals(salt, ruleTarget.salt)

        // Verify correct PIN
        val successResult = authResolver.verifyChallenge(ruleTarget, pin)
        assertTrue("Correct PIN verification must return Success", successResult is AuthResult.Success)

        // Verify incorrect PIN
        val failedResult = authResolver.verifyChallenge(ruleTarget, "0000")
        assertTrue("Incorrect PIN verification must return InvalidPin", failedResult is AuthResult.InvalidPin)
    }

    @Test
    fun testResolveChallengeGlobalPin() {
        val masterPin = "9999"
        val masterHash = PasswordHasher.hash(masterPin)
        fakeAntiUninstallPrefs.edit().putString("password", masterHash).apply()

        val rule = DummyRule(authType = AuthType.GLOBAL_PIN)
        val target = authResolver.resolveChallenge(rule)

        assertTrue("Rule with GLOBAL_PIN must resolve to RequireGlobalPin when master PIN set", target is AuthTarget.RequireGlobalPin)
        val globalTarget = target as AuthTarget.RequireGlobalPin

        val successResult = authResolver.verifyChallenge(globalTarget, masterPin)
        assertTrue(successResult is AuthResult.Success)

        val failedResult = authResolver.verifyChallenge(globalTarget, "1111")
        assertTrue(failedResult is AuthResult.InvalidPin)
    }

    @Test
    fun testResolveChallengeSystemActionNullRule() {
        // When global PIN is not configured
        val targetWithoutGlobal = authResolver.resolveChallenge(null)
        assertTrue(targetWithoutGlobal is AuthTarget.PassThrough)

        // When global PIN is configured
        val masterPin = "8888"
        fakeAntiUninstallPrefs.edit().putString("password", PasswordHasher.hash(masterPin)).apply()
        val targetWithGlobal = authResolver.resolveChallenge(null)
        assertTrue(targetWithGlobal is AuthTarget.RequireGlobalPin)
    }

    @Test
    fun testBruteForceLockoutTriggeredAfterMaxAttempts() {
        val pin = "5678"
        val (hash, salt) = authResolver.createRulePin(pin)
        val target = AuthTarget.RequireRulePin("rule_test", hash, salt)

        // 4 failed attempts
        for (i in 1..4) {
            val result = authResolver.verifyChallenge(target, "wrong_$i")
            assertTrue("Attempt $i should be InvalidPin and not locked out", result is AuthResult.InvalidPin)
            val invalid = result as AuthResult.InvalidPin
            assertFalse(invalid.isLockedOut)
            assertEquals(5 - i, invalid.attemptsRemaining)
        }

        // 5th failed attempt triggers lockout
        val fifthResult = authResolver.verifyChallenge(target, "wrong_5")
        assertTrue("5th attempt must trigger lockout", fifthResult is AuthResult.InvalidPin)
        val lockoutResult = fifthResult as AuthResult.InvalidPin
        assertTrue(lockoutResult.isLockedOut)
        assertEquals(0, lockoutResult.attemptsRemaining)
        assertTrue(lockoutResult.lockoutRemainingMillis > 0)

        // Subsequent resolution is locked out
        val resolvedTarget = authResolver.resolveChallenge(DummyRule(authType = AuthType.RULE_PIN, rulePasswordHash = hash, rulePasswordSalt = salt))
        assertTrue("Subsequent challenge resolution must return LockedOut", resolvedTarget is AuthTarget.LockedOut)
    }

    @Test
    fun testRulePinHelperMethods() {
        val rule = ScheduleRule(
            id = "schedule_1",
            name = "Test Schedule",
            appOrCategory = "Apps",
            restrictionType = "Block Schedule",
            startTime = "09:00",
            endTime = "17:00",
            days = listOf("Mon", "Tue"),
            limitValue = 0,
            isActive = true
        )

        val lockedWithPin = authResolver.applyRulePin(rule, "1234")
        assertEquals(AuthType.RULE_PIN, lockedWithPin.authType)
        assertNotNull(lockedWithPin.rulePasswordHash)
        assertNotNull(lockedWithPin.rulePasswordSalt)

        val unlocked = authResolver.removeRulePin(lockedWithPin)
        assertEquals(AuthType.NONE, unlocked.authType)
        assertNull(unlocked.rulePasswordHash)
        assertNull(unlocked.rulePasswordSalt)

        val lockedGlobal = authResolver.applyGlobalPin(rule)
        assertEquals(AuthType.GLOBAL_PIN, lockedGlobal.authType)
        assertNull(lockedGlobal.rulePasswordHash)
    }
}

/**
 * In-memory test double for SharedPreferences.
 */
class InMemorySharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getAll(): Map<String, *> = data.toMap()
    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl(data)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class EditorImpl(private val sharedData: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { if (key != null) temp[key] = value }
        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor = apply { if (key != null) temp[key] = values }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) temp[key] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) temp[key] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) temp[key] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) temp[key] = value }
        override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) temp[key] = null }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) sharedData.clear()
            temp.forEach { (k, v) ->
                if (v == null) sharedData.remove(k)
                else sharedData[k] = v
            }
            temp.clear()
        }
    }
}
