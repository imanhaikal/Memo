package com.imanhaikal.memo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure calendar arithmetic for budget cycles.
 *
 * Cycle boundaries are epoch *days*, not instants: a cycle is a range of calendar dates,
 * and storing it as millis makes a budget shift by a day when the user changes timezone.
 * Transactions keep real millis timestamps, so anything comparing the two converts here.
 */
object CycleMath {

    /** Which cycle [day] falls in, counting from [firstStartDay]. Negative before the start. */
    fun cycleIndexFor(firstStartDay: Long, totalDays: Int, day: Long): Int {
        require(totalDays > 0) { "totalDays must be positive" }
        return Math.floorDiv(day - firstStartDay, totalDays.toLong()).toInt()
    }

    fun cycleStartDay(firstStartDay: Long, totalDays: Int, cycleIndex: Int): Long =
        firstStartDay + cycleIndex.toLong() * totalDays

    fun cycleEndDayExclusive(firstStartDay: Long, totalDays: Int, cycleIndex: Int): Long =
        cycleStartDay(firstStartDay, totalDays, cycleIndex) + totalDays

    /** Start-of-day epoch millis for [day] in [zone] — the inclusive edge of a range query. */
    fun dayStartMillis(day: Long, zone: ZoneId): Long =
        LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()

    fun toEpochDay(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
}
