package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetCycle
import com.imanhaikal.memo.data.BudgetCycleDao
import java.time.Clock
import java.time.LocalDate

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
 *
 * Edits to a budget can move today into a cycle that already has a row: lengthening a
 * 7-day cycle to 30 days renumbers today from cycle 2 back to cycle 0. That row is
 * reopened and re-synced rather than replaced, because the DAO inserts with REPLACE and
 * would otherwise delete a finished cycle out of the user's history.
 *
 * `today` is passed in rather than read from [clock]: the UI's notion of the current day
 * comes from the day ticker, and if the two disagreed a cycle could roll over on screen
 * without ever being archived. The clock is only used to stamp when a cycle closed.
 */
class CycleRolloverUseCase(
    private val budgetCycleDao: BudgetCycleDao,
    private val clock: Clock
) {

    /** Returns the cycle containing [today], creating and closing rows as needed. */
    suspend fun ensureCurrentCycle(
        budget: Budget,
        today: LocalDate = LocalDate.now(clock)
    ): BudgetCycle {
        val currentIndex = CycleMath
            .cycleIndexFor(budget.firstCycleStartDate, budget.totalDays, today.toEpochDay())
            .coerceAtLeast(0)

        budgetCycleDao.getOpenCycle(budget.id)?.let { open ->
            if (open.cycleIndex == currentIndex) return syncWithBudget(open, budget)

            // Closed whichever way the index moved. Ahead of today means the budget's
            // period length or start date was edited so that today now falls in an
            // earlier cycle; the period that row described is over either way, and
            // leaving it open would give the budget two open cycles at once.
            budgetCycleDao.close(open.id, clock.millis())

            // A period the user skipped entirely still deserves a row, so history has
            // no holes when the app goes unopened for longer than a whole cycle.
            for (index in (open.cycleIndex + 1) until currentIndex) {
                if (budgetCycleDao.getByIndex(budget.id, index) != null) continue
                val skipped = insertCycle(budget, index)
                budgetCycleDao.close(skipped.id, clock.millis())
            }
        }

        return openCycle(budget, currentIndex)
    }

    /**
     * The open cycle for [index], reusing the row already on that index rather than
     * inserting over it.
     *
     * [BudgetCycleDao.insert] resolves conflicts with REPLACE against the unique
     * (budgetId, cycleIndex) constraint, so inserting blind would delete a closed cycle's
     * history the moment an edit moved today back onto an index that has already been
     * used — which is what happens when the user lengthens the cycle.
     */
    private suspend fun openCycle(budget: Budget, index: Int): BudgetCycle {
        val existing = budgetCycleDao.getByIndex(budget.id, index) ?: return insertCycle(budget, index)
        if (existing.closedAt != null) budgetCycleDao.reopen(existing.id)
        return syncWithBudget(existing.copy(closedAt = null), budget)
    }

    /**
     * Keeps the open cycle in step with edits to its budget.
     *
     * The calculator works from the cycle's own amount and dates, so without this a change
     * to the budget would not reach the current cycle at all — contradicting the Settings
     * screen, which promises changes apply right away. Closed cycles are never touched.
     */
    private suspend fun syncWithBudget(cycle: BudgetCycle, budget: Budget): BudgetCycle {
        val startDate = CycleMath.cycleStartDay(
            budget.firstCycleStartDate,
            budget.totalDays,
            cycle.cycleIndex
        )
        val endDateExclusive = CycleMath.cycleEndDayExclusive(
            budget.firstCycleStartDate,
            budget.totalDays,
            cycle.cycleIndex
        )
        if (cycle.startDate == startDate &&
            cycle.endDateExclusive == endDateExclusive &&
            cycle.budgetAmountCents == budget.amountCents
        ) {
            return cycle
        }

        budgetCycleDao.syncOpenCycle(cycle.id, startDate, endDateExclusive, budget.amountCents)
        return cycle.copy(
            startDate = startDate,
            endDateExclusive = endDateExclusive,
            budgetAmountCents = budget.amountCents
        )
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
