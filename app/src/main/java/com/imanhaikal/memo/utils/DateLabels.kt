package com.imanhaikal.memo.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Date/time formatting and combining helpers shared by the add dialog and the list. */
object DateLabels {

    // DateTimeFormatter is immutable and thread-safe; ofPattern re-parses the
    // pattern on every call, so hoist the formatters out of the hot paths.
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
    private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d")
    private val MONTH_DAY_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy")

    /** "Today" / "Yesterday" / "Jul 15" / "Jul 15, 2024". */
    fun relativeDayLabel(
        date: LocalDate,
        today: LocalDate = LocalDate.now(ZoneId.systemDefault())
    ): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(
            if (date.year == today.year) MONTH_DAY_FORMATTER else MONTH_DAY_YEAR_FORMATTER
        )
    }

    /** "3:41 PM". */
    fun timeLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        TIME_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(zone))

    /**
     * The date picker reports the chosen day as UTC midnight. Re-anchor that day in the
     * local zone, keeping the time-of-day of the value being replaced (or now, if
     * untouched) so transactions sort and display with a meaningful time.
     */
    fun combinePickedDayWithTime(
        pickedUtcMillis: Long,
        previousMillis: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val pickedDay = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val timeOfDay = Instant.ofEpochMilli(previousMillis ?: nowMillis).atZone(zone).toLocalTime()
        return pickedDay.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
    }

    /** Keep the day of [previousMillis] (or today if null) and set the picked time-of-day. */
    fun combineDateWithPickedTime(
        hour: Int,
        minute: Int,
        previousMillis: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val day = Instant.ofEpochMilli(previousMillis ?: nowMillis).atZone(zone).toLocalDate()
        return day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    }
}
