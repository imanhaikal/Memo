package com.imanhaikal.memo.data

import com.imanhaikal.memo.domain.CycleMath
import java.time.Clock

/**
 * Copies the pre-v5 DataStore budget into the `budgets` table, once.
 *
 * A Room migration runs on raw SQLite and cannot read DataStore, so [AppDatabase.MIGRATION_4_5]
 * only creates a placeholder row and this fills it in. Until [run] completes the UI must not
 * decide whether the user has a budget — otherwise an upgrading user is shown the setup
 * dialog over a full transaction history and can wipe their own data.
 */
class BudgetBootstrap(
    private val budgetDao: BudgetDao,
    private val budgetCycleDao: BudgetCycleDao,
    private val preferences: BudgetPreferences,
    private val clock: Clock
) {

    suspend fun run() {
        if (preferences.isMigratedToRoom()) return

        val legacy = preferences.readLegacyBudget()
        if (legacy == null) {
            // Either a fresh install (no placeholder row exists) or an upgrade from an
            // install that never completed setup. Drop the empty placeholder so the app
            // shows setup rather than an unnamed zero-amount budget.
            budgetDao.getById(AppDatabase.DEFAULT_BUDGET_ID)
                ?.takeIf { it.amountCents <= 0L }
                ?.let { budgetDao.delete(it) }
            preferences.setMigratedToRoom()
            return
        }

        val budget = Budget(
            id = AppDatabase.DEFAULT_BUDGET_ID,
            name = "Monthly",
            amountCents = legacy.totalBudgetCents,
            totalDays = legacy.totalDays,
            currencyCode = legacy.currencyCode,
            firstCycleStartDate = CycleMath.toEpochDay(legacy.cycleStartDateMillis, clock.zone),
            isArchived = false,
            createdAt = legacy.cycleStartDateMillis,
            sortOrder = 0
        )
        budgetDao.insert(budget)
        backfillCycles(budget)

        preferences.setActiveBudgetId(AppDatabase.DEFAULT_BUDGET_ID)
        preferences.setMigratedToRoom()
    }

    /**
     * Recreates the history the old calculator threw away: one row per period that has
     * already elapsed, plus the open cycle containing today. Totals are not stored —
     * they are computed from the transactions that are still in the database.
     */
    private suspend fun backfillCycles(budget: Budget) {
        val today = CycleMath.toEpochDay(clock.millis(), clock.zone)
        val currentIndex = CycleMath
            .cycleIndexFor(budget.firstCycleStartDate, budget.totalDays, today)
            .coerceAtLeast(0)
        val now = clock.millis()

        val cycles = (0..currentIndex).map { index ->
            BudgetCycle(
                budgetId = budget.id,
                cycleIndex = index,
                startDate = CycleMath.cycleStartDay(budget.firstCycleStartDate, budget.totalDays, index),
                endDateExclusive = CycleMath.cycleEndDayExclusive(
                    budget.firstCycleStartDate,
                    budget.totalDays,
                    index
                ),
                budgetAmountCents = budget.amountCents,
                closedAt = if (index == currentIndex) null else now
            )
        }
        budgetCycleDao.insertAll(cycles)
    }
}
