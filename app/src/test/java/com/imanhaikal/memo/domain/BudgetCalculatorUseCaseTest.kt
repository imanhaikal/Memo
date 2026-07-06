package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BudgetCalculatorUseCaseTest {

    private val zoneId: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2024, 1, 15)
    private val clock: Clock = Clock.fixed(today.atStartOfDay(zoneId).toInstant(), zoneId)
    private val calculator = BudgetCalculatorUseCase(clock)

    @Test
    fun `ignores transactions before and after active budget cycle`() {
        val cycleStart = LocalDate.of(2024, 1, 10)
        val transactions = listOf(
            Transaction(amount = 50_000L, note = "Before cycle", date = millis(LocalDate.of(2024, 1, 9))),
            Transaction(amount = 10_000L, note = "Inside cycle", date = millis(LocalDate.of(2024, 1, 14))),
            Transaction(amount = 5_000L, note = "Today", date = millis(today)),
            Transaction(amount = 25_000L, note = "After cycle", date = millis(LocalDate.of(2024, 1, 20))),
        )

        val state = calculator.calculate(
            transactions = transactions,
            totalBudgetCents = 100_000L,
            cycleStartDateMillis = millis(cycleStart),
            totalDays = 7,
            currencyCode = "USD",
        )

        assertEquals(2, state.daysRemaining)
        assertEquals(45_000L, state.dailyLimit)
        assertEquals(40_000L, state.availableToday)
        assertEquals(5_000L, state.spentToday)
    }

    @Test
    fun `requires a valid cycle start date to be setup`() {
        val state = calculator.calculate(
            transactions = emptyList(),
            totalBudgetCents = 100_000L,
            cycleStartDateMillis = 0L,
            totalDays = 30,
            currencyCode = "USD",
        )

        assertFalse(state.isSetup)
    }

    private fun millis(date: LocalDate): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
}
