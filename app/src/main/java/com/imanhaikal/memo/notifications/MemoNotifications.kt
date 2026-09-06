package com.imanhaikal.memo.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.imanhaikal.memo.MainActivity
import com.imanhaikal.memo.R

/**
 * Notification channels and posting.
 *
 * One channel per kind, so the user can silence the daily nudge without losing the
 * over-limit alert — a single "Memo" channel would make those an all-or-nothing choice.
 */
object MemoNotifications {

    const val CHANNEL_DAILY_REMINDER = "daily_reminder"
    const val CHANNEL_OVER_LIMIT = "over_limit"
    const val CHANNEL_CYCLE_SUMMARY = "cycle_summary"
    const val CHANNEL_RECURRING = "recurring_posted"

    private const val ID_DAILY_REMINDER = 1001
    private const val ID_OVER_LIMIT = 1002
    private const val ID_CYCLE_SUMMARY = 1003
    private const val ID_RECURRING = 1004

    /** minSdk is 26, so channels always exist and need no version guard. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                channel(
                    CHANNEL_DAILY_REMINDER,
                    "Daily limit",
                    "A morning note of what you can spend today",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                channel(
                    CHANNEL_OVER_LIMIT,
                    "Over limit",
                    "When an expense takes you past today's allowance",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                channel(
                    CHANNEL_CYCLE_SUMMARY,
                    "Cycle summary",
                    "How a finished budget cycle went",
                    NotificationManager.IMPORTANCE_LOW
                ),
                channel(
                    CHANNEL_RECURRING,
                    "Recurring expenses",
                    "When a scheduled expense is added for you",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        )
    }

    fun showDailyReminder(context: Context, text: NotificationText) =
        show(context, CHANNEL_DAILY_REMINDER, ID_DAILY_REMINDER, text)

    fun showOverLimit(context: Context, text: NotificationText) =
        show(context, CHANNEL_OVER_LIMIT, ID_OVER_LIMIT, text)

    fun showCycleSummary(context: Context, text: NotificationText) =
        show(context, CHANNEL_CYCLE_SUMMARY, ID_CYCLE_SUMMARY, text)

    fun showRecurringPosted(context: Context, text: NotificationText) =
        show(context, CHANNEL_RECURRING, ID_RECURRING, text)

    /**
     * POST_NOTIFICATIONS only exists from API 33. minSdk is 26, and on an older platform
     * the permission is unknown to the package manager, so `checkSelfPermission` reports
     * it denied however the manifest declares it — without this guard every notification
     * would be dropped on Android 8 to 12 while the settings toggles looked healthy.
     */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun channel(
        id: String,
        name: String,
        description: String,
        importance: Int
    ) = NotificationChannel(id, name, importance).apply {
        this.description = description
    }

    /**
     * Returns whether the notification actually reached the system, so a caller that keeps a
     * once-a-day guard can avoid burning it on a post that was never made.
     */
    private fun show(context: Context, channelId: String, id: Int, text: NotificationText): Boolean {
        // On API 33+ posting without the runtime permission is silently dropped; checking
        // keeps that from looking like a bug in the scheduling.
        if (!hasPermission(context)) return false
        return runCatching {
            NotificationManagerCompat.from(context).notify(id, build(context, channelId, text))
        }.isSuccess
    }

    private fun build(context: Context, channelId: String, text: NotificationText): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
    }
}
