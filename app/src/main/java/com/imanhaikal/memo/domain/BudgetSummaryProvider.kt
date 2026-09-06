package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetRepository
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.ui.BudgetStatus
import java.time.LocalDate

/**
 * A one-shot snapshot of a budget's current position.
 *
 * The dashboard gets this as a reactive `BudgetUiState`; workers and the home-screen
 * widget need the same numbers once, off the main thread, without a ViewModel. This is
 * that path, and it runs the same calculator so the two can't drift.
 */
data class BudgetSummary(
    val budgetName: String,
    val availableTodayCents: Long,
    val dailyLimitCents: Long,
    val currencyCode: String,
    val status: BudgetStatus
)

class BudgetSummaryProvider(
    private val budgetRepository: BudgetRepository,
    private val transactionDao: TransactionDao,
    private val calculator: BudgetCalculatorUseCase
) {

    suspend fun summarize(budget: Budget, today: LocalDate): BudgetSummary {
        val cycle = budgetRepository.ensureCurrentCycle(budget, today)
        val transactions = transactionDao.getForCycle(
            budgetId = budget.id,
            startMillis = CycleMath.dayStartMillis(cycle.startDate, calculator.zone),
            endMillisExclusive = CycleMath.dayStartMillis(cycle.endDateExclusive, calculator.zone)
        )
        val state = calculator.calculate(
            transactions = transactions,
            budget = budget,
            cycle = cycle,
            caps = emptyMap(),
            today = today
        )
        return BudgetSummary(
            budgetName = budget.name,
            availableTodayCents = state.availableToday,
            dailyLimitCents = state.dailyLimit,
            currencyCode = budget.currencyCode,
            status = state.status
        )
    }

    /** The active budget's summary, or null when the user has no budget yet. */
    suspend fun summarizeActive(today: LocalDate): BudgetSummary? =
        budgetRepository.resolveActiveBudget()?.let { summarize(it, today) }
}
