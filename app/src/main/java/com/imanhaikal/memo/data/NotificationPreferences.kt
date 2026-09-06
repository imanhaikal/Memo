package com.imanhaikal.memo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Which notifications the user wants, and when the daily one fires. */
data class NotificationSettings(
    val dailyReminder: Boolean = false,
    /** Minutes past local midnight; 9:00 by default. */
    val dailyReminderMinuteOfDay: Int = DEFAULT_REMINDER_MINUTE,
    val overLimit: Boolean = false,
    val cycleEnd: Boolean = false,
    val recurringPosted: Boolean = false
) {
    val anyEnabled: Boolean
        get() = dailyReminder || overLimit || cycleEnd || recurringPosted

    companion object {
        const val DEFAULT_REMINDER_MINUTE = 9 * 60
    }
}

/**
 * Notification settings.
 *
 * Everything defaults to off. A budgeting app that starts pushing notifications before
 * being asked is the kind that gets uninstalled, and the runtime permission is only
 * requested when the user turns the first one on.
 */
interface NotificationPreferencesStore {
    val settings: Flow<NotificationSettings>
    suspend fun update(settings: NotificationSettings)
    suspend fun current(): NotificationSettings

    /** Guards the over-limit alert to once a day. */
    suspend fun lastOverLimitDay(): Long
    suspend fun setLastOverLimitDay(epochDay: Long)

    /** The last cycle already reported on, so a summary is never sent twice. */
    suspend fun lastReportedCycleId(): Long
    suspend fun setLastReportedCycleId(cycleId: Long)
}

class NotificationPreferences(
    private val context: Context
) : NotificationPreferencesStore {

    private companion object {
        val DAILY_ENABLED = booleanPreferencesKey("notif_daily_enabled")
        val DAILY_MINUTE = intPreferencesKey("notif_daily_minute_of_day")
        val OVER_LIMIT_ENABLED = booleanPreferencesKey("notif_over_limit_enabled")
        val CYCLE_END_ENABLED = booleanPreferencesKey("notif_cycle_end_enabled")
        val RECURRING_ENABLED = booleanPreferencesKey("notif_recurring_enabled")
        val LAST_OVER_LIMIT_DAY = longPreferencesKey("notif_last_over_limit_day")
        val LAST_REPORTED_CYCLE = longPreferencesKey("notif_last_reported_cycle")
    }

    override val settings: Flow<NotificationSettings> = context.memoDataStore.data
        .map { preferences ->
            NotificationSettings(
                dailyReminder = preferences[DAILY_ENABLED] ?: false,
                dailyReminderMinuteOfDay = preferences[DAILY_MINUTE]
                    ?: NotificationSettings.DEFAULT_REMINDER_MINUTE,
                overLimit = preferences[OVER_LIMIT_ENABLED] ?: false,
                cycleEnd = preferences[CYCLE_END_ENABLED] ?: false,
                recurringPosted = preferences[RECURRING_ENABLED] ?: false
            )
        }
        .distinctUntilChanged()

    override suspend fun update(settings: NotificationSettings) {
        context.memoDataStore.edit { preferences ->
            preferences[DAILY_ENABLED] = settings.dailyReminder
            preferences[DAILY_MINUTE] = settings.dailyReminderMinuteOfDay
            preferences[OVER_LIMIT_ENABLED] = settings.overLimit
            preferences[CYCLE_END_ENABLED] = settings.cycleEnd
            preferences[RECURRING_ENABLED] = settings.recurringPosted
        }
    }

    override suspend fun current(): NotificationSettings = settings.first()

    override suspend fun lastOverLimitDay(): Long =
        context.memoDataStore.data.first()[LAST_OVER_LIMIT_DAY] ?: Long.MIN_VALUE

    override suspend fun setLastOverLimitDay(epochDay: Long) {
        context.memoDataStore.edit { preferences ->
            preferences[LAST_OVER_LIMIT_DAY] = epochDay
        }
    }

    override suspend fun lastReportedCycleId(): Long =
        context.memoDataStore.data.first()[LAST_REPORTED_CYCLE] ?: -1L

    override suspend fun setLastReportedCycleId(cycleId: Long) {
        context.memoDataStore.edit { preferences ->
            preferences[LAST_REPORTED_CYCLE] = cycleId
        }
    }
}
