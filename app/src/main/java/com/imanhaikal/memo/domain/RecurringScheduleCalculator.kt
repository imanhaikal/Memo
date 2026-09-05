package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Cadence
import com.imanhaikal.memo.data.RecurringRule
import java.time.LocalDate

/**
 * Works out when a recurring rule falls due. Pure calendar arithmetic, no I/O.
 *
 * Monthly rules clamp to the end of a short month: a rule starting on the 31st fires on
 * the 28th or 29th in February and returns to the 31st afterwards, rather than drifting
 * earlier every month the way repeated "add one month" would.
 */
class RecurringScheduleCalculator {

    /**
     * Every occurrence from the rule's cursor up to and including [through].
     *
     * Capped at [MAX_OCCURRENCES]: a daily rule left alone for years should catch up, not
     * insert tens of thousands of rows and hang the app on first launch.
     */
    fun dueDates(rule: RecurringRule, through: LocalDate): List<LocalDate> {
        if (rule.isPaused) return emptyList()

        val start = LocalDate.ofEpochDay(rule.startDate)
        val end = rule.endDate?.let(LocalDate::ofEpochDay)
        val limit = listOfNotNull(through, end).min()

        var occurrence = LocalDate.ofEpochDay(rule.nextDueDate)
        // A cursor before the start date means the rule has never fired.
        if (occurrence.isBefore(start)) occurrence = start

        val dates = mutableListOf<LocalDate>()
        while (!occurrence.isAfter(limit) && dates.size < MAX_OCCURRENCES) {
            dates += occurrence
            occurrence = advance(rule, start, occurrence)
        }
        return dates
    }

    /** The first occurrence strictly after [after], for advancing the cursor. */
    fun nextDueDate(rule: RecurringRule, after: LocalDate): LocalDate? {
        val start = LocalDate.ofEpochDay(rule.startDate)
        val next = advance(rule, start, after)
        val end = rule.endDate?.let(LocalDate::ofEpochDay)
        return if (end != null && next.isAfter(end)) null else next
    }

    private fun advance(rule: RecurringRule, start: LocalDate, from: LocalDate): LocalDate {
        val step = rule.intervalCount.coerceAtLeast(1).toLong()
        return when (rule.cadence) {
            Cadence.DAILY -> from.plusDays(step)
            Cadence.WEEKLY -> from.plusWeeks(step)
            Cadence.MONTHLY -> {
                // Counted from the start date rather than the previous occurrence, so a
                // month that clamped the day doesn't drag every later month with it.
                val elapsed = monthsBetween(start, from)
                val target = start.plusMonths(elapsed + step)
                clampToMonth(target, start.dayOfMonth)
            }
        }
    }

    private fun monthsBetween(start: LocalDate, current: LocalDate): Long =
        (current.year - start.year) * 12L + (current.monthValue - start.monthValue)

    private fun clampToMonth(date: LocalDate, desiredDay: Int): LocalDate =
        date.withDayOfMonth(minOf(desiredDay, date.lengthOfMonth()))

    private companion object {
        const val MAX_OCCURRENCES = 400
    }
}
