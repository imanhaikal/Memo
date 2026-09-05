package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetCycle
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.ui.BudgetStatus
import com.imanhaikal.memo.ui.BudgetUiState
import com.imanhaikal.memo.ui.CategoryTotal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * The "fluid pool" engine: what can be spent today so the budget still lands at zero.
 *
 * Now a pure function of its arguments — the cycle comes from a persisted [BudgetCycle]
 * rather than being silently rolled forward here, and "today" is injected by the day
 * ticker rather than read from a clock, so both are testable and neither drifts.
 */
class BudgetCalculatorUseCase(private val zone: ZoneId) {

    fun calculate(
        transactions: List<Transaction>,
        budget: Budget,
        cycle: BudgetCycle,
        caps: Map<Category, Long> = emptyMap(),
        today: LocalDate,
        allBudgets: List<Budget> = emptyList()
    ): BudgetUiState {
        val todayDay = today.toEpochDay()
        val startDate = LocalDate.ofEpochDay(cycle.startDate)

        val activeTransactions = transactions.filter { transaction ->
            val day = Instant.ofEpochMilli(transaction.date).atZone(zone).toLocalDate().toEpochDay()
            day >= cycle.startDate && day < cycle.endDateExclusive
        }

        val daysPassed = (todayDay - cycle.startDate).toInt()
        val daysRemaining = max(1, (cycle.endDateExclusive - todayDay).toInt())

        val (todayRows, earlierRows) = activeTransactions.partition { transaction ->
            Instant.ofEpochMilli(transaction.date).atZone(zone).toLocalDate().toEpochDay() == todayDay
        }

        // Income is a negative expense: a refund puts money back in the pool, which is
        // what makes the daily limit rise again instead of punishing the user for it.
        val netSpentBeforeToday = earlierRows.sumOf { it.signedAmount }
        val netSpentToday = todayRows.sumOf { it.signedAmount }

        val pool = cycle.budgetAmountCents - netSpentBeforeToday
        val baselineLimit = if (pool < 0) 0L else pool / daysRemaining
        val availableToday = baselineLimit - netSpentToday

        // Once today's spending exceeds the allowance, the overspend must come out of
        // the remaining days, so the displayed limit re-amortizes immediately instead
        // of waiting for the next day's recalculation.
        val dailyLimit = if (netSpentToday > baselineLimit && daysRemaining > 1) {
            max(0L, (pool - netSpentToday) / (daysRemaining - 1))
        } else {
            baselineLimit
        }

        val expenseThisCycle = activeTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }
        val incomeThisCycle = activeTransactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val categoryTotals = activeTransactions
            .groupBy { it.category }
            .map { (category, rows) ->
                val total = rows.sumOf { it.signedAmount }
                val cap = category?.let { caps[it] }
                CategoryTotal(
                    category = category,
                    totalCents = total,
                    capCents = cap,
                    isOverCap = cap != null && total > cap
                )
            }
            .sortedByDescending { it.totalCents }

        val status = when {
            availableToday < 0 -> BudgetStatus.OVER_LIMIT
            dailyLimit > 0 && availableToday < (dailyLimit / 5) -> BudgetStatus.CAREFUL
            else -> BudgetStatus.ON_TRACK
        }

        return BudgetUiState(
            isLoading = false,
            isSetup = true,
            availableToday = availableToday,
            dailyLimit = dailyLimit,
            daysRemaining = daysRemaining,
            transactions = transactions,
            status = status,
            totalBudget = cycle.budgetAmountCents,
            spentToday = netSpentToday,
            spentThisCycle = netSpentBeforeToday + netSpentToday,
            expenseThisCycle = expenseThisCycle,
            incomeThisCycle = incomeThisCycle,
            categoryTotals = categoryTotals,
            cycleStartDate = startDate,
            totalDays = budget.totalDays,
            currencyCode = budget.currencyCode,
            budgetId = budget.id,
            budgetName = budget.name,
            cycleIndex = cycle.cycleIndex,
            allBudgets = allBudgets,
            today = today,
            daysPassed = daysPassed
        )
    }
}
