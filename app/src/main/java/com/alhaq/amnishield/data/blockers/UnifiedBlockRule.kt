package com.alhaq.amnishield.data.blockers

import com.alhaq.amnishield.security.AuthType
import java.util.Calendar
import java.util.Locale
import java.util.UUID

enum class BlockRuleStatus {
    ACTIVE,
    INACTIVE_MANUAL,
    PAUSED_UNTIL,
    ARCHIVED
}

enum class AppInterceptionLevel {
    BLOCK_ENTIRE_APP,
    BLOCK_SHORTFORM_ONLY
}

enum class LimitPeriod {
    HOURLY,
    DAILY
}

enum class BlockInterceptionStyle {
    STANDARD_WARNING_DIALOG,
    MINDFUL_BREATHING_PAUSE,
    SILENT_CLEAN,
    INSTANT_HOME_OR_BACK;

    fun titleLabel(): String {
        return when (this) {
            STANDARD_WARNING_DIALOG -> "Standard Warning Dialog"
            MINDFUL_BREATHING_PAUSE -> "Mindful Breathing Pause"
            SILENT_CLEAN -> "Silent Clean"
            INSTANT_HOME_OR_BACK -> "Instant Home / Exit"
        }
    }

    fun subtitleLabel(): String {
        return when (this) {
            STANDARD_WARNING_DIALOG -> "Shows your motivational quote and countdown timer"
            MINDFUL_BREATHING_PAUSE -> "Launches a guided AmniSpace breathing exercise"
            SILENT_CLEAN -> "Navigates back or cleans the blocked page silently"
            INSTANT_HOME_OR_BACK -> "Immediately returns to Android Home"
        }
    }
}

enum class ShortformInterceptionStyle {
    REDIRECT_TO_FEED,
    EXIT_TO_HOME,
    WARNING_SCREEN,
    MINDFUL_BREATHING_PAUSE;

    fun titleLabel(): String {
        return when (this) {
            REDIRECT_TO_FEED -> "Redirect to Main Feed"
            EXIT_TO_HOME -> "Exit to Android Home"
            WARNING_SCREEN -> "Show Warning Dialog"
            MINDFUL_BREATHING_PAUSE -> "Mindful Breathing Pause"
        }
    }

    fun subtitleLabel(): String {
        return when (this) {
            REDIRECT_TO_FEED -> "Navigates out of Reels/Shorts back to the main app tab"
            EXIT_TO_HOME -> "Closes the app to the device home screen"
            WARNING_SCREEN -> "Displays the warning overlay screen"
            MINDFUL_BREATHING_PAUSE -> "Launches the AmniSpace breathing exercise"
        }
    }
}

data class WeeklySchedulePreset(
    val id: String,
    val label: String,
    val daysOfWeek: Set<Int>,
    val startTimeMinutes: Int,
    val endTimeMinutes: Int
) {
    fun toTrigger(): WeeklyScheduleTrigger = WeeklyScheduleTrigger(
        id = UUID.randomUUID().toString(),
        daysOfWeek = daysOfWeek,
        startTimeMinutes = startTimeMinutes,
        endTimeMinutes = endTimeMinutes
    )
}

data class ScheduleNormalizationResult(
    val schedules: List<WeeklyScheduleTrigger>,
    val adjustments: List<String> = emptyList()
)

data class TypeOverEvaluation(
    val isComplete: Boolean,
    val matchedChars: Int,
    val targetLength: Int,
    val firstMismatchIndex: Int?
)

object OverrideChallengeValidator {
    const val ANY_NFC_TAG = "ANY_NFC_TAG"
    const val MIN_PASSAGE_LENGTH = 10

    fun normalizeSmartPunctuation(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '\u2018', '\u2019', '\u201A', '\u201B' -> sb.append('\'')
                '\u201C', '\u201D', '\u201E', '\u201F' -> sb.append('"')
                '\u2013', '\u2014' -> sb.append('-')
                '\u00A0', '\t', '\r', '\n' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizeOverrideText(input: String): String {
        val punctuationNormalized = normalizeSmartPunctuation(input).trim()
        return punctuationNormalized.replace(Regex(" {2,}"), " ")
    }

    /**
     * Detects multi-character paste attempts including select-and-replace pastes where
     * `next.length - previous.length` alone might look small.
     */
    fun isPasteAttempt(previous: String, next: String, maxAllowedInsertChars: Int = 2): Boolean {
        if (next.isEmpty()) return false
        var prefix = 0
        val minLen = minOf(previous.length, next.length)
        while (prefix < minLen && previous[prefix] == next[prefix]) {
            prefix++
        }
        var suffix = 0
        while (
            suffix < (minLen - prefix) &&
            previous[previous.length - 1 - suffix] == next[next.length - 1 - suffix]
        ) {
            suffix++
        }
        val insertedLength = next.length - prefix - suffix
        return insertedLength > maxAllowedInsertChars
    }

    fun evaluateTypeOverText(typedRaw: String, targetRaw: String): TypeOverEvaluation {
        val normalizedTarget = normalizeOverrideText(targetRaw)
        if (normalizedTarget.isEmpty()) {
            return TypeOverEvaluation(
                isComplete = false,
                matchedChars = 0,
                targetLength = 0,
                firstMismatchIndex = null
            )
        }
        val typedForPrefix = normalizeSmartPunctuation(typedRaw).trimStart()
        val typedNormalized = normalizeOverrideText(typedRaw)
        val compareLen = minOf(typedForPrefix.length, normalizedTarget.length)
        var firstMismatch: Int? = null
        var matched = 0
        for (i in 0 until compareLen) {
            if (typedForPrefix[i] == normalizedTarget[i]) {
                matched++
            } else {
                firstMismatch = i
                break
            }
        }
        if (firstMismatch == null && typedForPrefix.length > normalizedTarget.length) {
            // Trailing spaces are ignored on final match; extra non-space chars are a mismatch
            val extra = typedForPrefix.substring(normalizedTarget.length)
            if (extra.isNotBlank()) {
                firstMismatch = normalizedTarget.length
            }
        }
        val isComplete = typedNormalized == normalizedTarget
        return TypeOverEvaluation(
            isComplete = isComplete,
            matchedChars = if (isComplete) normalizedTarget.length else matched,
            targetLength = normalizedTarget.length,
            firstMismatchIndex = if (isComplete) null else firstMismatch
        )
    }

    /**
     * Auto-corrects NFC tag UID input (strips colons/spaces/dashes, uppercases, and defaults
     * blank input to [ANY_NFC_TAG] so users are never locked out by leaving the default blank).
     */
    fun sanitizeNfcTagIdHex(raw: String?): String {
        val trimmed = raw.orEmpty().trim()
        if (trimmed.isEmpty() || trimmed.equals(ANY_NFC_TAG, ignoreCase = true)) {
            return ANY_NFC_TAG
        }
        return trimmed.replace(Regex("[:\\-\\s]+"), "").uppercase()
    }

    fun isValidNfcTagIdHex(raw: String?): Boolean {
        val sanitized = sanitizeNfcTagIdHex(raw)
        if (sanitized == ANY_NFC_TAG) return true
        return sanitized.length in 4..32 && sanitized.all { it in '0'..'9' || it in 'A'..'F' }
    }
}

object WeeklyScheduleNormalizer {
    private val validDayRange = Calendar.SUNDAY..Calendar.SATURDAY

    @Suppress("USELESS_ELVIS")
    fun sanitizeTrigger(trigger: WeeklyScheduleTrigger): WeeklyScheduleTrigger {
        val safeId = (trigger.id ?: "").ifBlank { UUID.randomUUID().toString() }
        val safeDays = (trigger.daysOfWeek ?: emptySet())
            .filter { it in validDayRange }
            .toSet()
            .ifEmpty { WeeklyScheduleTrigger.ALL_DAYS }
        val start = trigger.startTimeMinutes.coerceIn(0, 1439)
        val end = trigger.endTimeMinutes.coerceIn(0, 1439)
        val isAllDay = start == end || (start == 0 && end == 1439)
        return WeeklyScheduleTrigger(
            id = safeId,
            daysOfWeek = safeDays,
            startTimeMinutes = if (isAllDay) 0 else start,
            endTimeMinutes = if (isAllDay) 0 else end
        )
    }

    fun coversTimeRange(outer: WeeklyScheduleTrigger, inner: WeeklyScheduleTrigger): Boolean {
        if (outer.isAllDay()) return true
        if (inner.isAllDay()) return false
        val oStart = outer.startTimeMinutes
        val oEnd = outer.endTimeMinutes
        val iStart = inner.startTimeMinutes
        val iEnd = inner.endTimeMinutes
        return when {
            oStart < oEnd && iStart < iEnd -> oStart <= iStart && oEnd >= iEnd
            oStart > oEnd && iStart > iEnd -> oStart <= iStart && oEnd >= iEnd
            oStart > oEnd && iStart < iEnd -> iStart >= oStart || iEnd <= oEnd
            else -> false
        }
    }

    /**
     * Normalizes weekly schedules by:
     * 1. Sanitizing day sets and minute bounds.
     * 2. Combining duplicate time windows across different days into one entry.
     * 3. Merging overlapping or touching windows sharing the same day set (including overnight).
     * 4. Trimming/removing redundant windows fully covered by a broader window on overlapping days.
     */
    fun normalize(rawSchedules: List<WeeklyScheduleTrigger>): ScheduleNormalizationResult {
        if (rawSchedules.isEmpty()) return ScheduleNormalizationResult(emptyList(), emptyList())
        val adjustments = mutableListOf<String>()

        var current = rawSchedules.map { sanitizeTrigger(it) }
        current = mergeIdenticalTimeWindows(current, adjustments)
        current = mergeSameDaySetIntervals(current, adjustments)
        current = trimRedundantCrossDayOverlaps(current, adjustments)
        current = mergeIdenticalTimeWindows(current, adjustments)

        val sorted = current.sortedWith(
            compareBy<WeeklyScheduleTrigger>(
                { !it.isAllDay() },
                { it.startTimeMinutes },
                { it.endTimeMinutes },
                { it.daysOfWeek.minOrNull() ?: 0 }
            )
        )
        return ScheduleNormalizationResult(
            schedules = sorted,
            adjustments = adjustments.distinct()
        )
    }

    private fun mergeIdenticalTimeWindows(
        items: List<WeeklyScheduleTrigger>,
        adjustments: MutableList<String>
    ): List<WeeklyScheduleTrigger> {
        val grouped = LinkedHashMap<Pair<Int, Int>, MutableList<WeeklyScheduleTrigger>>()
        items.forEach { trigger ->
            grouped.getOrPut(trigger.startTimeMinutes to trigger.endTimeMinutes) { mutableListOf() }.add(trigger)
        }
        return grouped.values.map { group ->
            if (group.size == 1) {
                group.first()
            } else {
                val combinedDays = group.flatMap { it.daysOfWeek }.toSet()
                val merged = group.first().copy(daysOfWeek = combinedDays)
                val allSameDays = group.all { it.daysOfWeek == combinedDays }
                if (allSameDays) {
                    adjustments.add("Removed duplicate window (${merged.formatTimeRange()}).")
                } else {
                    adjustments.add("Combined days for ${merged.formatTimeRange()} into ${merged.formatDaysSummary()}.")
                }
                merged
            }
        }
    }

    private fun mergeSameDaySetIntervals(
        items: List<WeeklyScheduleTrigger>,
        adjustments: MutableList<String>
    ): List<WeeklyScheduleTrigger> {
        val grouped = LinkedHashMap<Set<Int>, MutableList<WeeklyScheduleTrigger>>()
        items.forEach { trigger ->
            grouped.getOrPut(trigger.daysOfWeek) { mutableListOf() }.add(trigger)
        }
        val result = mutableListOf<WeeklyScheduleTrigger>()
        grouped.forEach { (_, group) ->
            if (group.size == 1) {
                result.add(group.first())
                return@forEach
            }
            val existingAllDay = group.firstOrNull { it.isAllDay() }
            if (existingAllDay != null) {
                adjustments.add("All day (${existingAllDay.formatDaysSummary()}) already covers other windows on those days.")
                result.add(existingAllDay)
                return@forEach
            }

            val dayWindows = group.filter { it.startTimeMinutes < it.endTimeMinutes }
                .sortedBy { it.startTimeMinutes }
                .toMutableList()
            val overnightWindows = group.filter { it.startTimeMinutes > it.endTimeMinutes }

            // Merge daytime windows
            val mergedDay = mutableListOf<WeeklyScheduleTrigger>()
            for (win in dayWindows) {
                if (mergedDay.isEmpty()) {
                    mergedDay.add(win)
                } else {
                    val last = mergedDay.last()
                    if (win.startTimeMinutes <= last.endTimeMinutes) {
                        val combined = last.copy(endTimeMinutes = maxOf(last.endTimeMinutes, win.endTimeMinutes))
                        mergedDay[mergedDay.lastIndex] = combined
                        adjustments.add("Merged overlapping windows into ${combined.formatTimeRange()} (${combined.formatDaysSummary()}).")
                    } else {
                        mergedDay.add(win)
                    }
                }
            }

            // Merge overnight windows (all overnight windows share midnight)
            var mergedOvernight: WeeklyScheduleTrigger? = null
            if (overnightWindows.isNotEmpty()) {
                val minStart = overnightWindows.minOf { it.startTimeMinutes }
                val maxEnd = overnightWindows.maxOf { it.endTimeMinutes }
                if (overnightWindows.size > 1) {
                    val temp = overnightWindows.first().copy(startTimeMinutes = minStart, endTimeMinutes = maxEnd)
                    adjustments.add("Merged overnight windows into ${temp.formatTimeRange()} (${temp.formatDaysSummary()}).")
                }
                if (minStart <= maxEnd) {
                    val allDay = overnightWindows.first().copy(startTimeMinutes = 0, endTimeMinutes = 0)
                    adjustments.add("Merged windows cover full 24h (${allDay.formatDaysSummary()}).")
                    result.add(allDay)
                    return@forEach
                }
                mergedOvernight = overnightWindows.first().copy(
                    startTimeMinutes = minStart,
                    endTimeMinutes = maxEnd
                )
            }

            if (mergedOvernight == null) {
                // Check if a merged daytime window spans 00:00-23:59
                val normalizedDay = mergedDay.map { sanitizeTrigger(it) }
                val allDayHit = normalizedDay.firstOrNull { it.isAllDay() }
                if (allDayHit != null) {
                    adjustments.add("Merged windows cover full 24h (${allDayHit.formatDaysSummary()}).")
                    result.add(allDayHit)
                } else {
                    result.addAll(normalizedDay)
                }
            } else {
                var ovStart = mergedOvernight.startTimeMinutes
                var ovEnd = mergedOvernight.endTimeMinutes
                val remainingDay = mergedDay.toMutableList()
                var changed = true
                while (changed) {
                    changed = false
                    val iterator = remainingDay.iterator()
                    while (iterator.hasNext()) {
                        val dw = iterator.next()
                        var absorbed = false
                        if (dw.startTimeMinutes <= ovEnd) {
                            ovEnd = maxOf(ovEnd, dw.endTimeMinutes)
                            absorbed = true
                        }
                        if (dw.endTimeMinutes >= ovStart) {
                            ovStart = minOf(ovStart, dw.startTimeMinutes)
                            absorbed = true
                        }
                        if (absorbed) {
                            iterator.remove()
                            changed = true
                        }
                    }
                }
                if (ovStart != mergedOvernight.startTimeMinutes || ovEnd != mergedOvernight.endTimeMinutes) {
                    if (ovStart <= ovEnd) {
                        val allDay = mergedOvernight.copy(startTimeMinutes = 0, endTimeMinutes = 0)
                        adjustments.add("Merged windows cover full 24h (${allDay.formatDaysSummary()}).")
                        result.add(allDay)
                        return@forEach
                    } else {
                        mergedOvernight = mergedOvernight.copy(startTimeMinutes = ovStart, endTimeMinutes = ovEnd)
                        adjustments.add("Merged overlapping windows into ${mergedOvernight.formatTimeRange()} (${mergedOvernight.formatDaysSummary()}).")
                    }
                }
                result.addAll(remainingDay)
                result.add(mergedOvernight)
            }
        }
        return result
    }

    private fun trimRedundantCrossDayOverlaps(
        items: List<WeeklyScheduleTrigger>,
        adjustments: MutableList<String>
    ): List<WeeklyScheduleTrigger> {
        if (items.size <= 1) return items
        // Process broader windows first so narrow/subset windows get trimmed cleanly
        val ordered = items.sortedWith(
            compareByDescending<WeeklyScheduleTrigger> { it.durationMinutes() }
                .thenByDescending { it.daysOfWeek.size }
        )
        val kept = mutableListOf<WeeklyScheduleTrigger>()
        for (candidate in ordered) {
            var remainingDays = candidate.daysOfWeek
            for (broader in kept) {
                if (coversTimeRange(broader, candidate)) {
                    val overlapDays = remainingDays intersect broader.daysOfWeek
                    if (overlapDays.isNotEmpty()) {
                        remainingDays = remainingDays - overlapDays
                    }
                }
            }
            if (remainingDays.isEmpty()) {
                adjustments.add("Removed redundant window ${candidate.formatTimeRange()} already covered by a broader schedule.")
            } else if (remainingDays != candidate.daysOfWeek) {
                val trimmed = candidate.copy(daysOfWeek = remainingDays)
                adjustments.add("Adjusted ${trimmed.formatTimeRange()} to ${trimmed.formatDaysSummary()} to avoid duplicate overlap.")
                kept.add(trimmed)
            } else {
                kept.add(candidate)
            }
        }
        return kept
    }
}

data class WeeklyScheduleTrigger(
    val id: String = UUID.randomUUID().toString(),
    val daysOfWeek: Set<Int> = ALL_DAYS,
    val startTimeMinutes: Int = 22 * 60,
    val endTimeMinutes: Int = 7 * 60
) {
    @Suppress("USELESS_ELVIS")
    fun sanitizedDays(): Set<Int> {
        val raw = daysOfWeek ?: emptySet()
        return raw.filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
            .toSet()
            .ifEmpty { ALL_DAYS }
    }

    fun isAllDay(): Boolean {
        val start = startTimeMinutes.coerceIn(0, 1439)
        val end = endTimeMinutes.coerceIn(0, 1439)
        return start == end || (start == 0 && end == 1439)
    }

    fun isOvernight(): Boolean {
        val start = startTimeMinutes.coerceIn(0, 1439)
        val end = endTimeMinutes.coerceIn(0, 1439)
        return !isAllDay() && start > end
    }

    fun durationMinutes(): Int {
        if (isAllDay()) return 1440
        val start = startTimeMinutes.coerceIn(0, 1439)
        val end = endTimeMinutes.coerceIn(0, 1439)
        return if (end > start) end - start else (1440 - start) + end
    }

    fun remainingActiveMinutes(nowWallClockMillis: Long): Int {
        if (!isCurrentlyInWindow(nowWallClockMillis)) return 0
        val cal = Calendar.getInstance().apply { timeInMillis = nowWallClockMillis }
        val currentMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (isAllDay()) return 1440 - currentMinute
        val start = startTimeMinutes.coerceIn(0, 1439)
        val end = endTimeMinutes.coerceIn(0, 1439)
        return if (start < end) {
            (end - currentMinute).coerceAtLeast(1)
        } else {
            if (currentMinute >= start) {
                (1440 - currentMinute) + end
            } else {
                (end - currentMinute).coerceAtLeast(1)
            }
        }
    }

    fun isCurrentlyInWindow(nowWallClockMillis: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowWallClockMillis }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val days = sanitizedDays()

        if (isAllDay()) {
            return days.contains(currentDay)
        }

        val start = startTimeMinutes.coerceIn(0, 1439)
        val end = endTimeMinutes.coerceIn(0, 1439)

        return if (start < end) {
            days.contains(currentDay) && currentMinute in start until end
        } else {
            // Overnight schedule (e.g. 22:00 to 07:00)
            val inEveningPart = days.contains(currentDay) && currentMinute >= start
            val previousDay = if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY else currentDay - 1
            val inMorningPart = days.contains(previousDay) && currentMinute < end
            inEveningPart || inMorningPart
        }
    }

    fun formatStartTime(): String {
        val s = startTimeMinutes.coerceIn(0, 1439)
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    fun formatEndTime(): String {
        val e = endTimeMinutes.coerceIn(0, 1439)
        return String.format(Locale.US, "%02d:%02d", e / 60, e % 60)
    }

    fun formatTimeRange(): String {
        return if (isAllDay()) "All day (24h)" else "${formatStartTime()} - ${formatEndTime()}"
    }

    fun formatDurationSummary(): String {
        if (isAllDay()) return "24h"
        val mins = durationMinutes()
        val h = mins / 60
        val m = mins % 60
        val dur = when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
        return if (isOvernight()) "$dur • Overnight" else dur
    }

    fun formatDaysSummary(): String {
        val safeDays = sanitizedDays()
        if (safeDays.size >= 7) return "Every day"
        if (safeDays == WEEKDAYS) return "Weekdays"
        if (safeDays == WEEKENDS) return "Weekends"
        val ordered = listOf(
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat",
            Calendar.SUNDAY to "Sun"
        )
        return ordered.filter { safeDays.contains(it.first) }.joinToString(", ") { it.second }
    }

    companion object {
        val ALL_DAYS: Set<Int> = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
        val WEEKDAYS: Set<Int> = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
        val WEEKENDS: Set<Int> = setOf(
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )

        val PRESETS: List<WeeklySchedulePreset> = listOf(
            WeeklySchedulePreset(
                id = "sleep",
                label = "Sleep",
                daysOfWeek = ALL_DAYS,
                startTimeMinutes = 22 * 60,
                endTimeMinutes = 7 * 60
            ),
            WeeklySchedulePreset(
                id = "study",
                label = "Study",
                daysOfWeek = WEEKDAYS,
                startTimeMinutes = 9 * 60,
                endTimeMinutes = 12 * 60
            ),
            WeeklySchedulePreset(
                id = "work",
                label = "Work",
                daysOfWeek = WEEKDAYS,
                startTimeMinutes = 9 * 60,
                endTimeMinutes = 17 * 60
            ),
            WeeklySchedulePreset(
                id = "morning",
                label = "Morning",
                daysOfWeek = ALL_DAYS,
                startTimeMinutes = 6 * 60,
                endTimeMinutes = 9 * 60
            ),
            WeeklySchedulePreset(
                id = "evening",
                label = "Evening",
                daysOfWeek = ALL_DAYS,
                startTimeMinutes = 19 * 60,
                endTimeMinutes = 22 * 60
            ),
            WeeklySchedulePreset(
                id = "school",
                label = "School",
                daysOfWeek = WEEKDAYS,
                startTimeMinutes = 8 * 60,
                endTimeMinutes = 15 * 60
            )
        )

        fun shortDayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            Calendar.SUNDAY -> "Sun"
            else -> ""
        }
    }
}

data class ScreenTimeLimitTrigger(
    val id: String = UUID.randomUUID().toString(),
    val period: LimitPeriod = LimitPeriod.DAILY,
    val maxMinutes: Int = 60
) {
    fun formatSummary(): String {
        val hours = maxMinutes / 60
        val mins = maxMinutes % 60
        val durationStr = when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
        val periodLabel = if (period == LimitPeriod.HOURLY) "hr" else "day"
        return "$durationStr / $periodLabel"
    }
}

data class OpenLimitTrigger(
    val id: String = UUID.randomUUID().toString(),
    val period: LimitPeriod = LimitPeriod.DAILY,
    val maxLaunches: Int = 10
) {
    fun formatSummary(): String {
        val periodLabel = if (period == LimitPeriod.HOURLY) "hr" else "day"
        return "${maxLaunches}x / $periodLabel"
    }
}

enum class PauseOptionType {
    STRICT_NO_PAUSING,
    DELAY,
    TYPE_OVER_TEXT,
    SCAN_QR_OR_BARCODE,
    SCAN_NFC_TAG
}

data class PauseOptionConfig(
    val type: PauseOptionType = PauseOptionType.DELAY,
    val delaySeconds: Int = 10,
    val maxPauseDurationMinutes: Int = 60,
    val customOrRandomText: String = DEFAULT_CHALLENGE_TEXT,
    val registeredCodeValue: String = "",
    val registeredTagIdHex: String = "",
    val protectOutsideScheduleWindow: Boolean = false
) {
    fun titleLabel(): String {
        return when (type) {
            PauseOptionType.STRICT_NO_PAUSING -> "Strict: no pausing"
            PauseOptionType.DELAY -> "Delay"
            PauseOptionType.TYPE_OVER_TEXT -> "Type over text"
            PauseOptionType.SCAN_QR_OR_BARCODE -> "Scan QR or barcode"
            PauseOptionType.SCAN_NFC_TAG -> "Scan NFC tag"
        }
    }

    fun subtitleLabel(): String {
        return when (type) {
            PauseOptionType.STRICT_NO_PAUSING -> "You won't be able to stop the block while a restriction is active"
            PauseOptionType.DELAY -> "Wait for some time to pause"
            PauseOptionType.TYPE_OVER_TEXT -> "Type over custom or randomly generated text to pause"
            PauseOptionType.SCAN_QR_OR_BARCODE -> "Select a QR or barcode to scan before pausing"
            PauseOptionType.SCAN_NFC_TAG -> "Select an NFC tag to scan before pausing"
        }
    }

    fun formatDelayBadge(): String {
        return when {
            delaySeconds >= 3600 -> "${delaySeconds / 3600}h"
            delaySeconds >= 60 -> "${delaySeconds / 60}m"
            else -> "${delaySeconds}s"
        }
    }

    fun formatMaxPauseBadge(): String {
        return when {
            maxPauseDurationMinutes >= 60 && maxPauseDurationMinutes % 60 == 0 ->
                "Max ${maxPauseDurationMinutes / 60} hr"
            maxPauseDurationMinutes > 60 ->
                "Max ${maxPauseDurationMinutes / 60}h ${maxPauseDurationMinutes % 60}m"
            else -> "Max $maxPauseDurationMinutes min"
        }
    }

    companion object {
        const val DEFAULT_CHALLENGE_TEXT =
            "I choose intentional focus over mindless scrolling and value my time."

        fun computePauseDurationOptions(maxPauseDurationMinutes: Int): List<Int> {
            val safeMax = maxPauseDurationMinutes.coerceIn(1, 720)
            val options = listOf(1, 5, 15, 30, 60, 120)
                .filter { it <= safeMax }
                .toMutableSet()
            options.add(safeMax)
            return options.sorted()
        }
    }
}

data class UnifiedBlockRule(
    override val id: String = UUID.randomUUID().toString(),
    val name: String = "Block",
    val status: BlockRuleStatus = BlockRuleStatus.ACTIVE,
    val pausedUntilElapsedRealtime: Long = 0L,
    val pausedUntilWallClockMillis: Long = 0L,
    val appTargets: Map<String, AppInterceptionLevel> = emptyMap(),
    val blockedDomains: List<String> = emptyList(),
    val blockedKeywords: List<String> = emptyList(),
    val blockNewlyInstalledApps: Boolean = false,
    val includeInBrowserShortform: Boolean = false,
    val blockAppLaunch: Boolean = true,
    val blockNotifications: Boolean = false,
    val weeklySchedules: List<WeeklyScheduleTrigger> = emptyList(),
    val screenTimeLimits: List<ScreenTimeLimitTrigger> = emptyList(),
    val openLimits: List<OpenLimitTrigger> = emptyList(),
    val pauseOption: PauseOptionConfig = PauseOptionConfig(),
    val interceptionStyle: BlockInterceptionStyle = BlockInterceptionStyle.STANDARD_WARNING_DIALOG,
    val shortformInterceptionStyle: ShortformInterceptionStyle = ShortformInterceptionStyle.REDIRECT_TO_FEED,
    val customWarningMessage: String = DEFAULT_WARNING_MESSAGE,
    val closeDelaySeconds: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    override val authType: AuthType = AuthType.NONE,
    override val rulePasswordHash: String? = null,
    override val rulePasswordSalt: String? = null
) : BaseRule {

    @Suppress("USELESS_ELVIS")
    fun sanitize(): UnifiedBlockRule {
        val safeId = if (id.isNullOrBlank()) UUID.randomUUID().toString() else id
        val safeName = (name ?: "Block").trim().ifBlank { "Block" }
        val safeStatus = status ?: BlockRuleStatus.ACTIVE
        val safeApps = (appTargets ?: emptyMap()).entries
            .filter { it.key.isNotBlank() }
            .associate { it.key to (it.value ?: AppInterceptionLevel.BLOCK_ENTIRE_APP) }
        val safeDomains = (blockedDomains ?: emptyList()).map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val safeKeywords = (blockedKeywords ?: emptyList()).map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val safeSchedules = WeeklyScheduleNormalizer.normalize(weeklySchedules ?: emptyList()).schedules
        val safeScreenLimits = (screenTimeLimits ?: emptyList()).map {
            ScreenTimeLimitTrigger(
                id = if (it.id.isNullOrBlank()) UUID.randomUUID().toString() else it.id,
                period = it.period ?: LimitPeriod.DAILY,
                maxMinutes = it.maxMinutes.coerceAtLeast(1)
            )
        }
        val safeOpenLimits = (openLimits ?: emptyList()).map {
            OpenLimitTrigger(
                id = if (it.id.isNullOrBlank()) UUID.randomUUID().toString() else it.id,
                period = it.period ?: LimitPeriod.DAILY,
                maxLaunches = it.maxLaunches.coerceAtLeast(1)
            )
        }
        val rawPause = pauseOption ?: PauseOptionConfig()
        val safePauseType = rawPause.type ?: PauseOptionType.DELAY
        val normalizedPassage = OverrideChallengeValidator.normalizeOverrideText(
            rawPause.customOrRandomText ?: ""
        )
        val safePassage = if (normalizedPassage.length >= 4) {
            normalizedPassage
        } else {
            PauseOptionConfig.DEFAULT_CHALLENGE_TEXT
        }
        val safeNfcTag = if (safePauseType == PauseOptionType.SCAN_NFC_TAG) {
            OverrideChallengeValidator.sanitizeNfcTagIdHex(rawPause.registeredTagIdHex)
        } else {
            (rawPause.registeredTagIdHex ?: "").trim()
        }
        val safePause = PauseOptionConfig(
            type = safePauseType,
            delaySeconds = rawPause.delaySeconds.coerceIn(3, 3600),
            maxPauseDurationMinutes = rawPause.maxPauseDurationMinutes.coerceIn(1, 720),
            customOrRandomText = safePassage,
            registeredCodeValue = (rawPause.registeredCodeValue ?: "").trim(),
            registeredTagIdHex = safeNfcTag,
            protectOutsideScheduleWindow = rawPause.protectOutsideScheduleWindow
        )
        val safeInterceptionStyle = interceptionStyle ?: BlockInterceptionStyle.STANDARD_WARNING_DIALOG
        val safeShortformStyle = shortformInterceptionStyle ?: ShortformInterceptionStyle.REDIRECT_TO_FEED
        val safeWarningMsg = (customWarningMessage ?: DEFAULT_WARNING_MESSAGE).trim().ifBlank { DEFAULT_WARNING_MESSAGE }
        val safeCloseDelay = closeDelaySeconds.coerceIn(0, 60)

        return copy(
            id = safeId,
            name = safeName,
            status = safeStatus,
            appTargets = safeApps,
            blockedDomains = safeDomains,
            blockedKeywords = safeKeywords,
            weeklySchedules = safeSchedules,
            screenTimeLimits = safeScreenLimits,
            openLimits = safeOpenLimits,
            pauseOption = safePause,
            interceptionStyle = safeInterceptionStyle,
            shortformInterceptionStyle = safeShortformStyle,
            customWarningMessage = safeWarningMsg,
            closeDelaySeconds = safeCloseDelay,
            authType = authType ?: AuthType.NONE
        )
    }

    private fun didDeviceRebootDuringPause(nowElapsedRealtime: Long): Boolean {
        if (pausedUntilElapsedRealtime <= nowElapsedRealtime) return false
        val elapsedDiff = pausedUntilElapsedRealtime - nowElapsedRealtime
        val maxPlausibleMs = pauseOption.maxPauseDurationMinutes.coerceIn(1, 720) * 60_000L + 60_000L
        return elapsedDiff > maxPlausibleMs
    }

    fun isPaused(nowElapsedRealtime: Long, nowWallClockMillis: Long): Boolean {
        if (status != BlockRuleStatus.PAUSED_UNTIL) return false
        if (pausedUntilElapsedRealtime > 0L && nowElapsedRealtime < pausedUntilElapsedRealtime) {
            if (!didDeviceRebootDuringPause(nowElapsedRealtime)) {
                return true
            }
            return pausedUntilWallClockMillis > nowWallClockMillis
        }
        // Fallback if device rebooted while paused and wall clock is still within pause window
        if (pausedUntilElapsedRealtime <= 0L && pausedUntilWallClockMillis > nowWallClockMillis) {
            return true
        }
        return false
    }

    fun remainingPauseMinutes(nowElapsedRealtime: Long, nowWallClockMillis: Long): Int {
        if (!isPaused(nowElapsedRealtime, nowWallClockMillis)) return 0
        val useWallClock = pausedUntilElapsedRealtime <= nowElapsedRealtime ||
            didDeviceRebootDuringPause(nowElapsedRealtime)
        val remainingMs = if (!useWallClock) {
            pausedUntilElapsedRealtime - nowElapsedRealtime
        } else {
            (pausedUntilWallClockMillis - nowWallClockMillis).coerceAtLeast(0L)
        }
        return ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    fun hasAnyTriggers(): Boolean {
        return weeklySchedules.isNotEmpty() || screenTimeLimits.isNotEmpty() || openLimits.isNotEmpty()
    }

    fun hasAnyTargets(): Boolean {
        return appTargets.isNotEmpty() ||
            blockedDomains.isNotEmpty() ||
            blockedKeywords.isNotEmpty() ||
            includeInBrowserShortform ||
            blockNewlyInstalledApps
    }

    fun isEffectivelyActive(
        nowElapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
        nowWallClockMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return (status == BlockRuleStatus.ACTIVE || status == BlockRuleStatus.PAUSED_UNTIL) &&
            !isPaused(nowElapsedRealtime, nowWallClockMillis)
    }

    /**
     * Checks whether this rule is currently in an active enforcement state for unconditional
     * schedule or manual triggers (limits are evaluated per-package).
     */
    fun isUnconditionalOrScheduledActiveNow(
        nowElapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
        nowWallClockMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isEffectivelyActive(nowElapsedRealtime, nowWallClockMillis)) {
            return false
        }
        // If no triggers are configured, manual activation means always active
        if (!hasAnyTriggers()) {
            return true
        }
        // If weekly schedules are present, check if any schedule window is currently active
        if (weeklySchedules.any { it.isCurrentlyInWindow(nowWallClockMillis) }) {
            return true
        }
        return false
    }

    /**
     * Checks whether this rule is enabled and not paused (used for website/keyword rules that may
     * be combined with schedules or limits).
     */
    fun isActiveOrScheduledForContentNow(
        nowElapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
        nowWallClockMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isEffectivelyActive(nowElapsedRealtime, nowWallClockMillis)) {
            return false
        }
        if (weeklySchedules.isEmpty()) {
            return true
        }
        return weeklySchedules.any { it.isCurrentlyInWindow(nowWallClockMillis) }
    }

    fun warningSummarySubtitle(): String {
        val mainLabel = interceptionStyle.titleLabel()
        val delayBadge = if (interceptionStyle == BlockInterceptionStyle.STANDARD_WARNING_DIALOG) {
            " • ${closeDelaySeconds}s close delay"
        } else {
            ""
        }
        val hasShortform = includeInBrowserShortform ||
            appTargets.values.any { it == AppInterceptionLevel.BLOCK_SHORTFORM_ONLY }
        val shortformSuffix = if (hasShortform) {
            " • Short-form: ${shortformInterceptionStyle.titleLabel()}"
        } else {
            ""
        }
        return "$mainLabel$delayBadge$shortformSuffix"
    }

    private fun formatNextUpcomingActivation(nowWallClockMillis: Long): String {
        if (weeklySchedules.isEmpty()) return "Activate manually"
        val cal = Calendar.getInstance().apply { timeInMillis = nowWallClockMillis }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        var bestDeltaMinutes = Int.MAX_VALUE
        var bestSchedule: WeeklyScheduleTrigger? = null
        var bestDayOfWeek = currentDay

        for (dayOffset in 0..7) {
            val candidateDay = ((currentDay - 1 + dayOffset) % 7) + 1
            for (schedule in weeklySchedules) {
                if (!schedule.sanitizedDays().contains(candidateDay)) continue
                val startMin = if (schedule.isAllDay()) 0 else schedule.startTimeMinutes.coerceIn(0, 1439)
                val delta = dayOffset * 1440 + startMin - currentMinute
                if (delta > 0 && delta < bestDeltaMinutes) {
                    bestDeltaMinutes = delta
                    bestSchedule = schedule
                    bestDayOfWeek = candidateDay
                }
            }
            if (bestSchedule != null && bestDeltaMinutes <= 24 * 60) {
                break
            }
        }

        val target = bestSchedule ?: weeklySchedules.first()
        return if (bestDeltaMinutes <= 24 * 60) {
            "Activating ${target.formatStartTime()}"
        } else {
            val dayLabel = WeeklyScheduleTrigger.shortDayLabel(bestDayOfWeek)
            "Activating $dayLabel ${target.formatStartTime()}"
        }
    }

    /**
     * Produces the card status subtitle matching the UI design (e.g., "Activate manually",
     * "Activating 22:00", "Activating Mon 09:00", "Blocking until 07:00", "Paused (12m remaining)").
     */
    fun statusSubtitle(nowElapsedRealtime: Long, nowWallClockMillis: Long): String {
        if (status == BlockRuleStatus.ARCHIVED) return "Archived"
        if (isPaused(nowElapsedRealtime, nowWallClockMillis)) {
            val mins = remainingPauseMinutes(nowElapsedRealtime, nowWallClockMillis)
            return "Paused (${mins}m remaining)"
        }
        if (status == BlockRuleStatus.INACTIVE_MANUAL) {
            return if (weeklySchedules.isNotEmpty()) {
                formatNextUpcomingActivation(nowWallClockMillis)
            } else {
                "Activate manually"
            }
        }
        // Effectively ACTIVE (either ACTIVE or expired PAUSED_UNTIL)
        if (!hasAnyTriggers()) {
            return "Blocking now (Manual)"
        }
        val activeSchedules = weeklySchedules.filter { it.isCurrentlyInWindow(nowWallClockMillis) }
        if (activeSchedules.isNotEmpty()) {
            if (activeSchedules.any { it.isAllDay() }) {
                return "Blocking all day"
            }
            val longestActive = activeSchedules.maxByOrNull { it.remainingActiveMinutes(nowWallClockMillis) }
                ?: activeSchedules.first()
            return "Blocking until ${longestActive.formatEndTime()}"
        }
        if (weeklySchedules.isNotEmpty()) {
            return formatNextUpcomingActivation(nowWallClockMillis)
        }
        val parts = mutableListOf<String>()
        screenTimeLimits.firstOrNull()?.let { parts.add("Limit ${it.formatSummary()}") }
        openLimits.firstOrNull()?.let { parts.add("Opens ${it.formatSummary()}") }
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else "Activate manually"
    }

    companion object {
        const val DEFAULT_WARNING_MESSAGE =
            "Stay focused on your goals. This content is currently blocked."

        val SHORTFORM_CAPABLE_PACKAGES: Map<String, String> = mapOf(
            "com.instagram.android" to "Reels",
            "com.myinsta.android" to "Reels",
            "com.instagram.lite" to "Reels",
            "com.google.android.youtube" to "Shorts",
            "app.revanced.android.youtube" to "Shorts",
            "app.morphe.android.youtube" to "Shorts",
            "com.zhiliaoapp.musically" to "TikTok Feed",
            "com.ss.android.ugc.trill" to "TikTok Feed",
            "com.ss.android.ugc.aweme" to "TikTok Feed",
            "com.zhiliao.musically.go" to "TikTok Feed"
        )

        fun isShortformCapable(packageName: String): Boolean {
            return SHORTFORM_CAPABLE_PACKAGES.containsKey(packageName)
        }

        fun shortformLabelForPackage(packageName: String): String {
            return SHORTFORM_CAPABLE_PACKAGES[packageName] ?: "Short Videos"
        }

        fun shortformBadgePrefixForPackage(packageName: String): String {
            return when (packageName) {
                "com.instagram.android", "com.myinsta.android", "com.instagram.lite" -> "IG"
                "com.google.android.youtube", "app.revanced.android.youtube", "app.morphe.android.youtube" -> "YT"
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme", "com.zhiliao.musically.go" -> "TT"
                else -> "SF"
            }
        }
    }
}

/**
 * Classifies whether modifying an existing [UnifiedBlockRule] weakens its protection.
 * Strengthening changes (adding apps, websites, keywords, schedules, tightening limits,
 * or increasing pause friction) return false and apply with zero friction.
 */
object RuleMutationClassifier {

    fun pauseOptionStrengthRank(option: PauseOptionConfig): Int {
        return when (option.type) {
            PauseOptionType.STRICT_NO_PAUSING -> 4
            PauseOptionType.SCAN_NFC_TAG,
            PauseOptionType.SCAN_QR_OR_BARCODE -> 3
            PauseOptionType.TYPE_OVER_TEXT -> 2
            PauseOptionType.DELAY -> 1
        }
    }

    fun isWeakeningEdit(originalRaw: UnifiedBlockRule, updatedRaw: UnifiedBlockRule): Boolean {
        val original = originalRaw.sanitize()
        val updated = updatedRaw.sanitize()

        if (original.appTargets.any { (pkg, origLevel) ->
                val nextLevel = updated.appTargets[pkg] ?: return@any true
                origLevel == AppInterceptionLevel.BLOCK_ENTIRE_APP &&
                    nextLevel == AppInterceptionLevel.BLOCK_SHORTFORM_ONLY
            }
        ) {
            return true
        }

        if (original.blockedDomains.any { it !in updated.blockedDomains }) return true
        if (original.blockedKeywords.any { it !in updated.blockedKeywords }) return true

        if (original.blockNewlyInstalledApps && !updated.blockNewlyInstalledApps) return true
        if (original.includeInBrowserShortform && !updated.includeInBrowserShortform) return true
        if (original.blockAppLaunch && !updated.blockAppLaunch) return true
        if (original.blockNotifications && !updated.blockNotifications) return true

        val originalSchedulesCovered = original.weeklySchedules.all { origSched ->
            origSched.sanitizedDays().all { day ->
                updated.weeklySchedules.any { updSched ->
                    day in updSched.sanitizedDays() &&
                        WeeklyScheduleNormalizer.coversTimeRange(updSched, origSched)
                }
            }
        }
        if (!originalSchedulesCovered) return true

        if (original.screenTimeLimits.any { origLimit ->
                val matchingNext = updated.screenTimeLimits.firstOrNull { it.period == origLimit.period }
                matchingNext == null || matchingNext.maxMinutes > origLimit.maxMinutes
            }
        ) {
            return true
        }

        if (original.openLimits.any { origLimit ->
                val matchingNext = updated.openLimits.firstOrNull { it.period == origLimit.period }
                matchingNext == null || matchingNext.maxLaunches > origLimit.maxLaunches
            }
        ) {
            return true
        }

        val origRank = pauseOptionStrengthRank(original.pauseOption)
        val nextRank = pauseOptionStrengthRank(updated.pauseOption)
        if (nextRank < origRank) return true
        if (nextRank == origRank &&
            original.pauseOption.type == PauseOptionType.DELAY &&
            updated.pauseOption.type == PauseOptionType.DELAY &&
            updated.pauseOption.delaySeconds < original.pauseOption.delaySeconds
        ) {
            return true
        }
        if (original.pauseOption.type != PauseOptionType.STRICT_NO_PAUSING &&
            updated.pauseOption.type != PauseOptionType.STRICT_NO_PAUSING &&
            updated.pauseOption.maxPauseDurationMinutes > original.pauseOption.maxPauseDurationMinutes
        ) {
            return true
        }
        if (original.pauseOption.protectOutsideScheduleWindow && !updated.pauseOption.protectOutsideScheduleWindow) {
            return true
        }

        return false
    }
}
