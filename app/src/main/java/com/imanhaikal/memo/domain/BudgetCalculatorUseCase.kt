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
        var startDate = Instant.ofEpochMilli(cycleStartDateMillis).atZone(clock.zone).toLocalDate()

        // Roll the cycle forward by whole periods so today always falls within the active cycle,
        // instead of leaving the calculator stuck showing a stale, already-ended cycle.
        val daysSinceStart = ChronoUnit.DAYS.between(startDate, todayDate)
        if (daysSinceStart >= totalDays) {
            val cyclesElapsed = daysSinceStart / totalDays
            startDate = startDate.plusDays(cyclesElapsed * totalDays)
        }
        val endDateExclusive = startDate.plusDays(totalDays.toLong())

        val transactionsWithDates = transactions.map { it to Instant.ofEpochMilli(it.date).atZone(clock.zone).toLocalDate() }
        val activeTransactions = transactionsWithDates.filter { (_, txDate) ->
            !txDate.isBefore(startDate) && txDate.isBefore(endDateExclusive)
        }

        // Days Passed & Remaining
        val daysPassed = ChronoUnit.DAYS.between(startDate, todayDate).toInt()
        val daysRemaining = max(1, totalDays - daysPassed)

        // Strict Daily Pool Logic
        // Spent before today
        val spentBeforeToday = activeTransactions.filter { (_, txDate) -> txDate.isBefore(todayDate) }
            .sumOf { (tx, _) -> tx.amount }

        // Spent today
        val spentToday = activeTransactions.filter { (_, txDate) -> txDate.isEqual(todayDate) }
            .sumOf { (tx, _) -> tx.amount }

        // Pool is based only on past spending
        val pool = totalBudgetCents - spentBeforeToday
        
        // Baseline daily limit based on pool and remaining days
        val rawDailyLimit = pool / daysRemaining
        val baselineLimit = if (pool < 0) 0L else rawDailyLimit

        // Available today is measured against today's original allowance
        val availableToday = baselineLimit - spentToday

        // Once today's spending exceeds the allowance, the overspend must come out of
        // the remaining days, so the displayed limit re-amortizes immediately instead
        // of waiting for the next day's recalculation.
        val dailyLimit = if (spentToday > baselineLimit && daysRemaining > 1) {
            max(0L, (pool - spentToday) / (daysRemaining - 1))
        } else {
            baselineLimit
        }

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
