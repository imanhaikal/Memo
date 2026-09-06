package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Cadence
import com.imanhaikal.memo.data.RecurringRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringScheduleCalculatorTest {

    private val calculator = RecurringScheduleCalculator()

    private fun rule(
        cadence: Cadence,
        start: LocalDate,
        nextDue: LocalDate = start,
        intervalCount: Int = 1,
        end: LocalDate? = null,
        paused: Boolean = false
    ) = RecurringRule(
        id = 1L,
        budgetId = 1L,
        amountCents = 120_000L,
        note = "Rent",
        cadence = cadence,
        intervalCount = intervalCount,
        startDate = start.toEpochDay(),
        endDate = end?.toEpochDay(),
        nextDueDate = nextDue.toEpochDay(),
        isPaused = paused
    )

    @Test
    fun `a daily rule fires once per day up to today`() {
        val start = LocalDate.of(2024, 3, 1)
        val due = calculator.dueDates(rule(Cadence.DAILY, start), LocalDate.of(2024, 3, 5))

        assertEquals(5, due.size)
        assertEquals(start, due.first())
        assertEquals(LocalDate.of(2024, 3, 5), due.last())
    }

    @Test
    fun `a weekly rule fires every seven days`() {
        val start = LocalDate.of(2024, 3, 1)
        val due = calculator.dueDates(rule(Cadence.WEEKLY, start), LocalDate.of(2024, 3, 29))

        assertEquals(
            listOf(1, 8, 15, 22, 29).map { LocalDate.of(2024, 3, it) },
            due
        )
    }

    @Test
    fun `an interval greater than one skips periods`() {
        val start = LocalDate.of(2024, 3, 1)
        val due = calculator.dueDates(
            rule(Cadence.DAILY, start, intervalCount = 3),
            LocalDate.of(2024, 3, 10)
        )

        assertEquals(listOf(1, 4, 7, 10).map { LocalDate.of(2024, 3, it) }, due)
    }

    @Test
    fun `a monthly rule on the 31st clamps to short months and then recovers`() {
        val start = LocalDate.of(2024, 1, 31)
        val due = calculator.dueDates(rule(Cadence.MONTHLY, start), LocalDate.of(2024, 5, 31))

        // 2024 is a leap year, so February clamps to the 29th — and crucially March
        // returns to the 31st rather than being dragged to the 29th as well.
        assertEquals(
            listOf(
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 3, 31),
                LocalDate.of(2024, 4, 30),
                LocalDate.of(2024, 5, 31)
            ),
            due
        )
    }

    @Test
    fun `a monthly rule on the 31st clamps to 28 in a non-leap February`() {
        val start = LocalDate.of(2023, 1, 31)
        val due = calculator.dueDates(rule(Cadence.MONTHLY, start), LocalDate.of(2023, 3, 31))

        assertEquals(
            listOf(
                LocalDate.of(2023, 1, 31),
                LocalDate.of(2023, 2, 28),
                LocalDate.of(2023, 3, 31)
            ),
            due
        )
    }

    @Test
    fun `nothing is due before the rule starts`() {
        val start = LocalDate.of(2024, 6, 1)
        val due = calculator.dueDates(rule(Cadence.DAILY, start), LocalDate.of(2024, 5, 20))

        assertTrue(due.isEmpty())
    }

    @Test
    fun `a paused rule is never due`() {
        val start = LocalDate.of(2024, 3, 1)
        val due = calculator.dueDates(
            rule(Cadence.DAILY, start, paused = true),
            LocalDate.of(2024, 3, 10)
        )

        assertTrue(due.isEmpty())
    }

    @Test
    fun `an end date stops the schedule`() {
        val start = LocalDate.of(2024, 3, 1)
        val due = calculator.dueDates(
            rule(Cadence.DAILY, start, end = LocalDate.of(2024, 3, 3)),
            LocalDate.of(2024, 3, 10)
        )

        assertEquals(3, due.size)
        assertEquals(LocalDate.of(2024, 3, 3), due.last())
    }

    @Test
    fun `nextDueDate is null once the rule has ended`() {
        val start = LocalDate.of(2024, 3, 1)
        val next = calculator.nextDueDate(
            rule(Cadence.DAILY, start, end = LocalDate.of(2024, 3, 3)),
            LocalDate.of(2024, 3, 3)
        )

        assertNull(next)
    }

    @Test
    fun `a very long gap is capped rather than inserting years of rows`() {
        val start = LocalDate.of(2000, 1, 1)
        val due = calculator.dueDates(rule(Cadence.DAILY, start), LocalDate.of(2024, 1, 1))

        // Catching up should not mean thousands of inserts on first launch.
        assertEquals(400, due.size)
    }
}
