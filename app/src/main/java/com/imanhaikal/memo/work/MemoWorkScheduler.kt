package com.imanhaikal.memo.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.imanhaikal.memo.data.NotificationSettings
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily background work.
 *
 * Deliberately `PeriodicWorkRequest` and nothing more — no `AlarmManager`, no
 * `SCHEDULE_EXACT_ALARM`, no expedited work. A 9:00 reminder may land at 9:20, which a
 * reminder tolerates, and it keeps the app out of the exact-alarm permission and the
 * foreground-service quota system entirely.
 */
object MemoWorkScheduler {

    fun sync(context: Context, settings: NotificationSettings, clock: Clock) {
        val workManager = WorkManager.getInstance(context)

        // Recurring expenses post whether or not the user wants to hear about it; the
        // notification toggle only controls whether it is announced.
        workManager.enqueueUniquePeriodicWork(
            RecurringPostWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RecurringPostWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextLocalTime(clock, LocalTime.of(0, 30)))
                .build()
        )

        if (settings.dailyReminder) {
            val reminderTime = LocalTime.ofSecondOfDay(
                settings.dailyReminderMinuteOfDay.toLong() * 60
            )
            workManager.enqueueUniquePeriodicWork(
                DailyReminderWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayUntilNextLocalTime(clock, reminderTime))
                    .build()
            )
        } else {
            workManager.cancelUniqueWork(DailyReminderWorker.WORK_NAME)
        }

        if (settings.cycleEnd) {
            workManager.enqueueUniquePeriodicWork(
                CycleEndWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CycleEndWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayUntilNextLocalTime(clock, LocalTime.of(9, 5)))
                    .build()
            )
        } else {
            workManager.cancelUniqueWork(CycleEndWorker.WORK_NAME)
        }
    }

    /** Time from now until the next occurrence of [target] in the clock's own zone. */
    internal fun delayUntilNextLocalTime(clock: Clock, target: LocalTime): Duration {
        val now = clock.instant().atZone(clock.zone)
        var next = LocalDate.now(clock).atTime(target).atZone(clock.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
