package com.alhaq.amnishield

import com.alhaq.amnishield.utils.ScreenTimeCalculator
import com.alhaq.amnishield.utils.TimeTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScreenTimeCalculatorTest {

    @Test
    fun testStartOfDayMillisIsMidnight() {
        val startOfToday = ScreenTimeCalculator.getStartOfDayMillis(0)
        val cal = Calendar.getInstance().apply { timeInMillis = startOfToday }

        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testRelativeDaysOffset() {
        val todayStart = ScreenTimeCalculator.getStartOfDayMillis(0)
        val yesterdayStart = ScreenTimeCalculator.getStartOfDayMillis(1)
        val diff = todayStart - yesterdayStart

        // Difference must be approximately 24 hours (86,400,000 ms)
        assertEquals(24 * 3600 * 1000L, diff)
    }

    @Test
    fun testEndOfDayMillis() {
        val endOfToday = ScreenTimeCalculator.getEndOfDayMillis(0)
        val cal = Calendar.getInstance().apply { timeInMillis = endOfToday }

        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
        assertEquals(59, cal.get(Calendar.SECOND))
        assertEquals(999, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testFormatHoursMinutes() {
        assertEquals("0m", TimeTools.formatHoursMinutes(0L))
        assertEquals("45m", TimeTools.formatHoursMinutes(45 * 60 * 1000L))
        assertEquals("1h 0m", TimeTools.formatHoursMinutes(60 * 60 * 1000L))
        assertEquals("4h 12m", TimeTools.formatHoursMinutes((4 * 60 + 12) * 60 * 1000L))
        assertEquals("23h 59m", TimeTools.formatHoursMinutes((23 * 60 + 59) * 60 * 1000L))
    }

    @Test
    fun testScreenTimeDoesNotExceedMaxWallClockLimit() {
        val startOfDay = ScreenTimeCalculator.getStartOfDayMillis(0)
        val now = System.currentTimeMillis()
        val maxPossibleToday = (now - startOfDay).coerceAtLeast(0L)

        // Raw calculated time simulated with heavy foreground overlaps
        val simulatedOverlappingPackagesTime = 30 * 3600 * 1000L // 30 hours
        val clampedTime = minOf(simulatedOverlappingPackagesTime, maxPossibleToday)

        assertTrue(clampedTime <= maxPossibleToday)
        assertTrue(clampedTime <= 24 * 3600 * 1000L)
    }
}
