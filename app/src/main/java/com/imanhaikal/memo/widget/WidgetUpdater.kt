package com.imanhaikal.memo.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.imanhaikal.memo.MemoApplication
import com.imanhaikal.memo.data.widget.WidgetSnapshot
import com.imanhaikal.memo.data.widget.WidgetSnapshotRepository
import java.time.LocalDate

/**
 * Recomputes the widget snapshot and asks Glance to redraw.
 *
 * Called from the app after any write that changes the numbers, and on each day tick. The
 * widget itself never computes anything — see [MemoWidget].
 */
object WidgetUpdater {

    suspend fun refresh(context: Context) {
        val application = context.applicationContext as? MemoApplication ?: return
        val container = application.container

        runCatching {
            val summary = container.budgetSummaryProvider
                .summarizeActive(LocalDate.now(container.clock))

            val snapshot = if (summary == null) {
                WidgetSnapshot.EMPTY
            } else {
                WidgetSnapshot(
                    hasBudget = true,
                    budgetName = summary.budgetName,
                    availableTodayCents = summary.availableTodayCents,
                    dailyLimitCents = summary.dailyLimitCents,
                    currencyCode = summary.currencyCode,
                    status = summary.status,
                    updatedAtMillis = container.clock.millis()
                )
            }

            WidgetSnapshotRepository(context).write(snapshot)
            MemoWidget().updateAll(context)
        }
    }
}
