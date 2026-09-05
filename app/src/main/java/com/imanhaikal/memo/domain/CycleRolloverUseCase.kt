package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetCycle
import com.imanhaikal.memo.data.BudgetCycleDao
import java.time.Clock

/**
 * Closes an elapsed cycle and opens the next one.
 *
 * Before v5 the calculator silently slid the cycle start forward, so a finished period
 * simply disappeared from the stats while its transactions lingered in the list. Now each
 * period is a row, and the next opens immediately so the dashboard never sits in a dead
 * state waiting for the user.
 *
 * Closing only stamps `closedAt`; totals are computed from transactions on read, so a
 * backdated expense still lands in the right cycle's history.
 *
 * Safe to call repeatedly — a no-op once the open cycle contains today.
 */
class CycleRolloverUseCase(
    private val budgetCycleDao: BudgetCycleDao,
    private val clock: Clock
) {

    /** Returns the cycle containing today, creating and closing rows as needed. */
    suspend fun ensureCurrentCycle(budget: Budget): BudgetCycle {
        val today = CycleMath.toEpochDay(clock.millis(), clock.zone)
        val currentIndex = CycleMath
            .cycleIndexFor(budget.firstCycleStartDate, budget.totalDays, today)
            .coerceAtLeast(0)

        budgetCycleDao.getOpenCycle(budget.id)?.let { open ->
            if (open.cycleIndex == currentIndex) return open
            if (open.cycleIndex < currentIndex) {
                budgetCycleDao.close(open.id, clock.millis())
                // A period the user skipped entirely still deserves a row, so history has
                // no holes when the app goes unopened for longer than a whole cycle.
                for (index in (open.cycleIndex + 1) until currentIndex) {
                    val skipped = insertCycle(budget, index)
                    budgetCycleDao.close(skipped.id, clock.millis())
                }
            }
        }

        budgetCycleDao.getLatest(budget.id)?.let { latest ->
            if (latest.cycleIndex == currentIndex && latest.closedAt == null) return latest
        }
        return insertCycle(budget, currentIndex)
    }

    private suspend fun insertCycle(budget: Budget, index: Int): BudgetCycle {
        val cycle = BudgetCycle(
            budgetId = budget.id,
            cycleIndex = index,
            startDate = CycleMath.cycleStartDay(budget.firstCycleStartDate, budget.totalDays, index),
            endDateExclusive = CycleMath.cycleEndDayExclusive(
                budget.firstCycleStartDate,
                budget.totalDays,
                index
            ),
            budgetAmountCents = budget.amountCents
        )
        return cycle.copy(id = budgetCycleDao.insert(cycle))
    }
}
