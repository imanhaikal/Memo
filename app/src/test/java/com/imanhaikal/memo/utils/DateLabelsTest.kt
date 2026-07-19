package com.imanhaikal.memo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DateLabelsTest {

    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private val today = LocalDate.of(2026, 7, 19)

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    // --- relativeDayLabel ---

    @Test
    fun `today label`() {
        assertEquals("Today", DateLabels.relativeDayLabel(today, today))
    }

    @Test
    fun `yesterday label`() {
        assertEquals("Yesterday", DateLabels.relativeDayLabel(today.minusDays(1), today))
    }

    @Test
    fun `same year label omits year`() {
        assertEquals("Jul 15", DateLabels.relativeDayLabel(LocalDate.of(2026, 7, 15), today))
    }

    @Test
    fun `older year label includes year`() {
        assertEquals("Dec 31, 2025", DateLabels.relativeDayLabel(LocalDate.of(2025, 12, 31), today))
    }

    // --- timeLabel ---

    @Test
    fun `time label formats as 12 hour`() {
        // The AM/PM marker text is locale-dependent; assert only the clock digits
        assertTrue(DateLabels.timeLabel(millisOf(2026, 7, 19, 15, 41), zone).startsWith("3:41 "))
        assertTrue(DateLabels.timeLabel(millisOf(2026, 7, 19, 9, 5), zone).startsWith("9:05 "))
    }

    // --- combinePickedDayWithTime ---

    @Test
    fun `picked day keeps previous time of day`() {
        val previous = millisOf(2026, 7, 19, 15, 41)
        // Picker reports the chosen day as UTC midnight
        val pickedUtc = LocalDate.of(2026, 7, 10)
            .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

        val combined = DateLabels.combinePickedDayWithTime(pickedUtc, previous, zone)

        assertEquals(millisOf(2026, 7, 10, 15, 41), combined)
    }

    @Test
    fun `picked day uses now when untouched`() {
        val now = millisOf(2026, 7, 19, 8, 30)
        val pickedUtc = LocalDate.of(2026, 7, 10)
            .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

        val combined = DateLabels.combinePickedDayWithTime(pickedUtc, null, zone, nowMillis = now)

        assertEquals(millisOf(2026, 7, 10, 8, 30), combined)
    }

    // --- combineDateWithPickedTime ---

    @Test
    fun `picked time keeps previous day`() {
        val previous = millisOf(2026, 7, 10, 15, 41)

        val combined = DateLabels.combineDateWithPickedTime(9, 15, previous, zone)

        assertEquals(millisOf(2026, 7, 10, 9, 15), combined)
    }

    @Test
    fun `picked time uses today when untouched`() {
        val now = millisOf(2026, 7, 19, 8, 30)

        val combined = DateLabels.combineDateWithPickedTime(21, 45, null, zone, nowMillis = now)

        assertEquals(millisOf(2026, 7, 19, 21, 45), combined)
    }
}
