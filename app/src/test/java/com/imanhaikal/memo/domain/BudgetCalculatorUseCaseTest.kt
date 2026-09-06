package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetCycle
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BudgetCalculatorUseCaseTest {

    private val zoneId: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2024, 1, 15)
    private val calculator = BudgetCalculatorUseCase(zoneId)

    private val cycleStart = LocalDate.of(2024, 1, 10)

    private fun budget(amountCents: Long = 100_000L, totalDays: Int = 7) = Budget(
        id = 1L,
        name = "Monthly",
        amountCents = amountCents,
        totalDays = totalDays,
        currencyCode = "USD",
        firstCycleStartDate = cycleStart.toEpochDay(),
        createdAt = 0L
    )

    private fun cycle(budget: Budget, index: Int = 0) = BudgetCycle(
        id = 1L,
        budgetId = budget.id,
        cycleIndex = index,
        startDate = cycleStart.toEpochDay(),
        endDateExclusive = cycleStart.toEpochDay() + budget.totalDays,
        budgetAmountCents = budget.amountCents
    )

    @Test
    fun `ignores transactions before and after active budget cycle`() {
        val transactions = listOf(
            Transaction(amount = 50_000L, note = "Before cycle", date = millis(LocalDate.of(2024, 1, 9))),
            Transaction(amount = 10_000L, note = "Inside cycle", date = millis(LocalDate.of(2024, 1, 14))),
            Transaction(amount = 5_000L, note = "Today", date = millis(today)),
            Transaction(amount = 25_000L, note = "After cycle", date = millis(LocalDate.of(2024, 1, 20)))
        )
        val budget = budget()

        val state = calculator.calculate(
            transactions = transactions,
            budget = budget,
            cycle = cycle(budget),
            today = today
        )

        assertEquals(2, state.daysRemaining)
        assertEquals(45_000L, state.dailyLimit)
        assertEquals(40_000L, state.availableToday)
        assertEquals(5_000L, state.spentToday)
    }

    @Test
    fun `income before today raises the pool and the daily limit`() {
        val budget = budget()
        val withoutIncome = calculator.calculate(
            transactions = listOf(
                Transaction(amount = 10_000L, note = "Spend", date = millis(LocalDate.of(2024, 1, 14)))
            ),
            budget = budget,
            cycle = cycle(budget),
            today = today
        )

        val withIncome = calculator.calculate(
            transactions = listOf(
                Transaction(amount = 10_000L, note = "Spend", date = millis(LocalDate.of(2024, 1, 14))),
                Transaction(
                    amount = 4_000L,
                    note = "Refund",
                    date = millis(LocalDate.of(2024, 1, 14)),
                    type = TransactionType.INCOME
                )
            ),
            budget = budget,
            cycle = cycle(budget),
            today = today
        )

        // Pool rises by the refund, spread over the 2 remaining days.
        assertEquals(45_000L, withoutIncome.dailyLimit)
        assertEquals(47_000L, withIncome.dailyLimit)
        assertEquals(47_000L, withIncome.availableToday)
    }

    @Test
    fun `income today offsets what was spent today`() {
        val budget = budget()
        val state = calculator.calculate(
            transactions = listOf(
                Transaction(amount = 6_000L, note = "Lunch", date = millis(today)),
                Transaction(
                    amount = 2_000L,
                    note = "Refund",
                    date = millis(today),
                    type = TransactionType.INCOME
                )
            ),
            budget = budget,
            cycle = cycle(budget),
            today = today
        )

        // Net spend today is 4_000 against a 50_000 baseline over 2 days.
        assertEquals(4_000L, state.spentToday)
        assertEquals(46_000L, state.availableToday)
    }

    @Test
    fun `category totals expose caps and flag the ones that are over`() {
        val budget = budget()
        val state = calculator.calculate(
            transactions = listOf(
                Transaction(
                    amount = 12_000L,
                    note = "Groceries",
                    date = millis(today),
                    category = Category.FOOD
                ),
                Transaction(
                    amount = 3_000L,
                    note = "Bus",
                    date = millis(today),
                    category = Category.TRANSPORT
                )
            ),
            budget = budget,
            cycle = cycle(budget),
            caps = mapOf(Category.FOOD to 10_000L, Category.TRANSPORT to 5_000L),
            today = today
        )

        val food = state.categoryTotals.first { it.category == Category.FOOD }
        val transport = state.categoryTotals.first { it.category == Category.TRANSPORT }

        assertEquals(10_000L, food.capCents)
        assertTrue(food.isOverCap)
        assertEquals(5_000L, transport.capCents)
        assertFalse(transport.isOverCap)
    }

    @Test
    fun `a refund reduces its own category total`() {
        val budget = budget()
        val state = calculator.calculate(
            transactions = listOf(
                Transaction(
                    amount = 12_000L,
                    note = "Jacket",
                    date = millis(today),
                    category = Category.SHOPPING
                ),
                Transaction(
                    amount = 12_000L,
                    note = "Returned it",
                    date = millis(today),
                    category = Category.SHOPPING,
                    type = TransactionType.INCOME
                )
            ),
            budget = budget,
            cycle = cycle(budget),
            today = today
        )

        assertEquals(0L, state.categoryTotals.first { it.category == Category.SHOPPING }.totalCents)
    }

    @Test
    fun `overspending today re-amortizes across the remaining days`() {
        val budget = budget()
        val state = calculator.calculate(
            transactions = listOf(
                Transaction(amount = 80_000L, note = "Splurge", date = millis(today))
            ),
            budget = budget,
            cycle = cycle(budget),
            today = today
        )

        // 100_000 pool, 80_000 gone today, 1 day left after today.
        assertEquals(20_000L, state.dailyLimit)
        assertEquals(-30_000L, state.availableToday)
    }

    private fun millis(date: LocalDate): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
}
