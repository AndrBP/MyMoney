package com.mymoney.app.domain.rules

import java.time.LocalDate

/**
 * Date arithmetic for recurrence rules. Uses epoch days + java.time only —
 * no timezone surprises (all math is calendar-date based, never instants).
 */
object RecurrenceEngine {

    /**
     * Returns every occurrence date from [from] up to and including [until],
     * respecting start/end dates and the rule's frequency. Occurrences before
     * the rule start date are skipped; ignored occurrences are filtered out.
     */
    fun occurrencesBetween(
        startDateEpochDay: Long,
        endDateEpochDay: Long?,
        frequency: com.mymoney.app.domain.model.RecurrenceFrequency,
        customIntervalDays: Int,
        dueDay: Int,
        fromEpochDay: Long,
        untilEpochDay: Long,
        ignored: Set<Long>,
        maxCount: Int = 1000,
    ): List<Long> {
        val out = ArrayList<Long>()
        var cursor = maxOf(startDateEpochDay, firstOccurrenceOnOrAfter(startDateEpochDay, frequency, dueDay))
        val end = minOf(untilEpochDay, endDateEpochDay ?: Long.MAX_VALUE)
        if (cursor > end) return out
        var guard = 0
        while (cursor <= end && guard++ < maxCount) {
            if (cursor >= fromEpochDay && cursor !in ignored) out.add(cursor)
            cursor = nextOccurrence(cursor, startDateEpochDay, frequency, customIntervalDays, dueDay)
        }
        return out
    }

    /** Next occurrence strictly after [current]. */
    fun nextOccurrence(
        currentEpochDay: Long,
        anchorEpochDay: Long,
        frequency: com.mymoney.app.domain.model.RecurrenceFrequency,
        customIntervalDays: Int,
        dueDay: Int,
    ): Long {
        frequency.stepDays()?.let { step ->
            return currentEpochDay + step
        }
        if (frequency == com.mymoney.app.domain.model.RecurrenceFrequency.CUSTOM) {
            return currentEpochDay + customIntervalDays.coerceAtLeast(1)
        }
        val months = frequency.stepMonths() ?: 1
        val cur = LocalDate.ofEpochDay(currentEpochDay)
        val target = cur.plusMonths(months.toLong())
        val day = if (frequency.stepMonths() != null) clampDay(target.year, target.monthValue, dueDay) else target.dayOfMonth
        return LocalDate.of(target.year, target.monthValue, day).toEpochDay()
    }

    /** The very first occurrence may be shifted to the rule's due day. */
    fun firstOccurrenceOnOrAfter(anchorEpochDay: Long, frequency: com.mymoney.app.domain.model.RecurrenceFrequency, dueDay: Int): Long {
        val anchor = LocalDate.ofEpochDay(anchorEpochDay)
        return when {
            frequency.stepDays() != null || frequency == com.mymoney.app.domain.model.RecurrenceFrequency.CUSTOM -> anchorEpochDay
            else -> {
                val day = clampDay(anchor.year, anchor.monthValue, dueDay)
                val candidate = LocalDate.of(anchor.year, anchor.monthValue, day)
                if (candidate.toEpochDay() >= anchorEpochDay) candidate.toEpochDay()
                else {
                    val next = anchor.plusMonths(1)
                    LocalDate.of(next.year, next.monthValue, clampDay(next.year, next.monthValue, dueDay)).toEpochDay()
                }
            }
        }
    }

    private fun clampDay(year: Int, month: Int, dueDay: Int): Int {
        val max = java.time.YearMonth.of(year, month).lengthOfMonth()
        return dueDay.coerceIn(1, max)
    }

    /** Next scheduled occurrence on/after [todayEpochDay], or null if the rule ended. */
    fun nextUpcoming(rule: com.mymoney.app.domain.model.RecurrenceRule, todayEpochDay: Long): Long? {
        val list = occurrencesBetween(
            rule.startDateEpochDay, rule.endDateEpochDay, rule.frequency,
            rule.customIntervalDays, rule.dueDay,
            todayEpochDay, todayEpochDay + 366, rule.ignoredOccurrences.toSet(), 12,
        )
        return list.firstOrNull()
    }
}
