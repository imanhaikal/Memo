package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.ui.BudgetStatus
import com.imanhaikal.memo.ui.BudgetUiState
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max

class BudgetCalculatorUseCase(private val clock: Clock) {

    fun calculate(
        transactions: List<Transaction>,
        totalBudgetCents: Long,
        cycleStartDateMillis: Long,
        totalDays: Int,
        currencyCode: String
    ): BudgetUiState {
        // Basic Setup Check
        val isSetup = totalBudgetCents > 0 && totalDays > 0 && cycleStartDateMillis > 0
        if (!isSetup) {
            return BudgetUiState(
                isLoading = false,
                isSetup = false,
                transactions = transactions,
                totalDays = totalDays,
                currencyCode = currencyCode
            )
        }

        // Time calculations using Clock
        val todayDate = clock.instant().atZone(clock.zone).toLocalDate()
        val startDate = Instant.ofEpochMilli(cycleStartDateMillis).atZone(clock.zone).toLocalDate()
        val endDateExclusive = startDate.plusDays(totalDays.toLong())
        val activeTransactions = transactions.filter {
            val txDate = Instant.ofEpochMilli(it.date).atZone(clock.zone).toLocalDate()
            !txDate.isBefore(startDate) && txDate.isBefore(endDateExclusive)
        }

        // Days Passed & Remaining
        val daysPassed = ChronoUnit.DAYS.between(startDate, todayDate).toInt()
        val daysRemaining = max(1, totalDays - daysPassed)

        // Strict Daily Pool Logic
        // Spent before today
        val spentBeforeToday = activeTransactions.filter {
            val txDate = Instant.ofEpochMilli(it.date).atZone(clock.zone).toLocalDate()
            txDate.isBefore(todayDate)
        }.sumOf { it.amount }

        // Spent today
        val spentToday = activeTransactions.filter {
            val txDate = Instant.ofEpochMilli(it.date).atZone(clock.zone).toLocalDate()
            txDate.isEqual(todayDate)
        }.sumOf { it.amount }

        // Pool is based only on past spending
        val pool = totalBudgetCents - spentBeforeToday
        
        // Baseline daily limit based on pool and remaining days
        val rawDailyLimit = pool / daysRemaining
        val dailyLimit = if (pool < 0) 0L else rawDailyLimit

        // Available today
        val availableToday = dailyLimit - spentToday

        // Status Determination
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
            totalBudget = totalBudgetCents,
            spentToday = spentToday,
            totalDays = totalDays,
            currencyCode = currencyCode
        )
    }
}
