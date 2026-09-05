package com.imanhaikal.memo.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.imanhaikal.memo.MemoApplication
import com.imanhaikal.memo.notifications.MemoNotifications
import com.imanhaikal.memo.notifications.NotificationContent
import java.time.LocalDate

/**
 * Adds any recurring expenses that have fallen due, and says so.
 *
 * The same catch-up runs on every app start. This worker is the convenience for people
 * who don't open the app daily; it is deliberately not the only path, because vendor
 * battery managers suppress periodic work for days at a time.
 */
class RecurringPostWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MemoApplication)?.container ?: return Result.success()

        return runCatching {
            val posted = container.postRecurring.catchUp()
            if (posted.isNotEmpty() && container.notificationPreferences.current().recurringPosted) {
                val budget = container.budgetRepository.resolveActiveBudget()
                MemoNotifications.showRecurringPosted(
                    applicationContext,
                    NotificationContent.recurringPosted(
                        count = posted.size,
                        totalCents = posted.sumOf { it.transaction.amount },
                        currencyCode = budget?.currencyCode ?: "MYR"
                    )
                )
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val WORK_NAME = "memo-recurring-post"
    }
}

/** The morning "here's what you can spend today" note. */
class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MemoApplication)?.container ?: return Result.success()

        return runCatching {
            if (!container.notificationPreferences.current().dailyReminder) return Result.success()

            val budget = container.budgetRepository.resolveActiveBudget() ?: return Result.success()
            val summary = container.budgetSummaryProvider.summarize(budget, LocalDate.now(container.clock))
            MemoNotifications.showDailyReminder(
                applicationContext,
                NotificationContent.dailyReminder(summary.availableTodayCents, budget.currencyCode)
            )
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val WORK_NAME = "memo-daily-reminder"
    }
}

/**
 * Reports on a cycle that has just finished.
 *
 * Checks whether a cycle closed since it last ran rather than being scheduled for a
 * specific date, so a cycle boundary crossed while the phone was off still gets reported.
 */
class CycleEndWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MemoApplication)?.container ?: return Result.success()

        return runCatching {
            if (!container.notificationPreferences.current().cycleEnd) return Result.success()

            val budget = container.budgetRepository.resolveActiveBudget() ?: return Result.success()
            // Opening the current cycle is what closes a finished one.
            container.budgetRepository.ensureCurrentCycle(budget)

            val closed = container.budgetRepository.mostRecentlyClosedCycle(budget.id)
                ?: return Result.success()
            // Report each cycle once, however often this runs.
            if (container.notificationPreferences.lastReportedCycleId() == closed.id) {
                return Result.success()
            }
            container.notificationPreferences.setLastReportedCycleId(closed.id)

            val totals = container.budgetRepository.totalsFor(closed)
            MemoNotifications.showCycleSummary(
                applicationContext,
                NotificationContent.cycleEnd(
                    spentCents = totals.netSpentCents,
                    budgetCents = closed.budgetAmountCents,
                    currencyCode = budget.currencyCode
                )
            )
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val WORK_NAME = "memo-cycle-end"
    }
}
