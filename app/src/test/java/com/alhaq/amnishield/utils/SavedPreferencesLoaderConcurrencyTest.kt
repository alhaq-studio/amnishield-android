package com.alhaq.amnishield.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.alhaq.amnishield.InMemorySharedPreferences
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SavedPreferencesLoaderConcurrencyTest {

    private lateinit var prefsMap: MutableMap<String, InMemorySharedPreferences>
    private lateinit var testContext: Context

    @Before
    fun setUp() {
        SavedPreferencesLoader.invalidateScheduleRulesCache()
        prefsMap = mutableMapOf()
        testContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            @Synchronized
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return prefsMap.getOrPut(name ?: "default") { InMemorySharedPreferences() }
            }
        }
    }

    @Test
    fun `concurrent upsertAppBlockerScheduleRule across separate loader instances preserves all rules without race loss`() {
        val threadCount = 20
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)

        try {
            val tasks = (1..threadCount).map { idx ->
                Callable {
                    startLatch.await(5, TimeUnit.SECONDS)
                    // Each thread uses a distinct SavedPreferencesLoader instance
                    val instanceLoader = SavedPreferencesLoader(testContext)
                    instanceLoader.upsertAppBlockerScheduleRule(
                        AppBlockScheduleRule(
                            id = "concurrent_rule_$idx",
                            title = "Rule $idx",
                            packageName = "com.example.app$idx",
                            startMinute = 60,
                            endMinute = 120
                        )
                    )
                }
            }

            val futures = tasks.map { executor.submit(it) }
            startLatch.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }

            val verifierLoader = SavedPreferencesLoader(testContext)
            val savedRules = verifierLoader.loadAppBlockerScheduleRules()
            assertEquals("All $threadCount concurrently inserted schedule rules must be preserved", threadCount, savedRules.size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `concurrent addAppLaunchLimitRule and trackAppLaunch across separate loader instances preserves all limits and counts`() {
        val threadCount = 20
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)

        try {
            val tasks = (1..threadCount).map { idx ->
                Callable {
                    startLatch.await(5, TimeUnit.SECONDS)
                    val instanceLoader = SavedPreferencesLoader(testContext)
                    val pkg = "com.example.launch$idx"
                    instanceLoader.addAppLaunchLimitRule(
                        AppLaunchLimitRule(
                            id = "limit_$idx",
                            packageName = pkg,
                            maxLaunches = 5
                        )
                    )
                    instanceLoader.trackAppLaunch(pkg)
                    instanceLoader.trackAppLaunch(pkg)
                }
            }

            val futures = tasks.map { executor.submit(it) }
            startLatch.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }

            val verifierLoader = SavedPreferencesLoader(testContext)
            val savedLimits = verifierLoader.loadAppLaunchLimitRules()
            assertEquals("All $threadCount concurrently added launch limit rules must be preserved", threadCount, savedLimits.size)

            for (idx in 1..threadCount) {
                val pkg = "com.example.launch$idx"
                assertEquals("Launch count for $pkg must be 2", 2, verifierLoader.getCurrentLaunchCount(pkg))
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
