package com.imanhaikal.memo.notifications

import com.imanhaikal.memo.data.NotificationPreferencesStore
import com.imanhaikal.memo.domain.BudgetSummaryProvider
import java.time.Clock
import java.time.LocalDate

/**
 * Fires the "over today's limit" alert when spending crosses the day's allowance.
 *
 * The other three notifications are time-scheduled workers, but this one is event-shaped —
 * the toggle promises a warning "when an expense takes you past today's allowance", not at
 * a fixed hour. It is driven from the same application-scope collector that refreshes the
 * widget, which already emits on every transaction write, budget switch, and day tick.
 *
 * [notify] is injected rather than calling [MemoNotifications] directly so the decision
 * logic can be tested on the JVM without a NotificationManager. It reports whether the
 * notification actually reached the system.
 */
class OverLimitNotifier(
    private val summaryProvider: BudgetSummaryProvider,
    private val preferences: NotificationPreferencesStore,
    private val clock: Clock,
    private val notify: (NotificationText) -> Boolean
) {

    /**
     * Notifies at most once per calendar day, so an evening of small overspends doesn't
     * turn into a stream of alerts. The guard is only advanced when something is actually
     * sent, so a day spent under the limit never burns it — and neither does a day where
     * the post was dropped. That last case is real: the in-app toggle can be on while the
     * OS runtime permission is denied, and burning the guard on those silent attempts would
     * mean granting the permission at noon bought silence until midnight.
     */
    suspend fun checkAndNotify(today: LocalDate = LocalDate.now(clock)) {
        if (!preferences.current().overLimit) return

        val summary = summaryProvider.summarizeActive(today) ?: return
        if (summary.availableTodayCents >= 0) return

        val day = today.toEpochDay()
        if (preferences.lastOverLimitDay() == day) return

        val posted = notify(
            NotificationContent.overLimit(summary.availableTodayCents, summary.currencyCode)
        )
        if (posted) preferences.setLastOverLimitDay(day)
    }
}
