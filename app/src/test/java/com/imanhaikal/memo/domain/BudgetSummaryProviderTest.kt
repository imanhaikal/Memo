package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.testing.MemoTestHarness
import com.imanhaikal.memo.ui.BudgetStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The snapshot path used by the widget and the notification workers. It must agree with
 * the dashboard, which is the whole reason it runs the same calculator.
 */
class BudgetSummaryProviderTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2024, 4, 10)
    private val clock: Clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private fun provider(harness: MemoTestHarness) = BudgetSummaryProvider(
        budgetRepository = harness.repository,
        transactionDao = harness.transactionDao,
        calculator = BudgetCalculatorUseCase(zone)
    )

    @Test
    fun `no budget means no summary rather than a zeroed one`() = runTest {
        val harness = MemoTestHarness(clock, today)

        assertNull(provider(harness).summarizeActive(today))
    }

    @Test
    fun `the summary matches what the dashboard would show`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        harness.transactionDao.insertTransaction(
            Transaction(
                amount = 2_500L,
                note = "Lunch",
                date = harness.millisAtNoon(today),
                budgetId = 1L
            )
        )

        val summary = provider(harness).summarizeActive(today)!!

        assertEquals(10_000L, summary.dailyLimitCents)
        assertEquals(7_500L, summary.availableTodayCents)
        assertEquals("MYR", summary.currencyCode)
        assertEquals(BudgetStatus.ON_TRACK, summary.status)
    }

    @Test
    fun `overspending is reported as over limit`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        harness.transactionDao.insertTransaction(
            Transaction(
                amount = 15_000L,
                note = "Splurge",
                date = harness.millisAtNoon(today),
                budgetId = 1L
            )
        )

        val summary = provider(harness).summarizeActive(today)!!

        assertEquals(BudgetStatus.OVER_LIMIT, summary.status)
        assertEquals(-5_000L, summary.availableTodayCents)
    }

    @Test
    fun `the summary follows the active budget`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today, name = "Monthly")
        harness.seedBudget(
            amountCents = 140_000L,
            totalDays = 14,
            startDate = today,
            name = "Travel"
        )

        val summary = provider(harness).summarizeActive(today)!!

        assertEquals("Travel", summary.budgetName)
        assertEquals(10_000L, summary.dailyLimitCents)
    }
}
