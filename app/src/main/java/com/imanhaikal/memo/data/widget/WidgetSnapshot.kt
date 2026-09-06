package com.imanhaikal.memo.data.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.imanhaikal.memo.ui.BudgetStatus
import kotlinx.coroutines.flow.first

/** The handful of numbers the home-screen widget draws. */
data class WidgetSnapshot(
    val hasBudget: Boolean,
    val budgetName: String,
    val availableTodayCents: Long,
    val dailyLimitCents: Long,
    val currencyCode: String,
    val status: BudgetStatus,
    val updatedAtMillis: Long
) {
    companion object {
        val EMPTY = WidgetSnapshot(
            hasBudget = false,
            budgetName = "",
            availableTodayCents = 0L,
            dailyLimitCents = 0L,
            currencyCode = "MYR",
            status = BudgetStatus.ON_TRACK,
            updatedAtMillis = 0L
        )
    }
}

/**
 * A separate store from the app's own preferences, and deliberately not Room.
 *
 * Glance can recompose in a process where the app's singletons are cold; opening the
 * database there risks a second AppDatabase instance and disk I/O on the wrong thread for
 * what is a handful of numbers. The app writes this snapshot, the widget only reads it.
 */
private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "widget_snapshot"
)

class WidgetSnapshotRepository(private val context: Context) {

    private companion object {
        val HAS_BUDGET = booleanPreferencesKey("has_budget")
        val BUDGET_NAME = stringPreferencesKey("budget_name")
        val AVAILABLE_TODAY = longPreferencesKey("available_today")
        val DAILY_LIMIT = longPreferencesKey("daily_limit")
        val CURRENCY = stringPreferencesKey("currency")
        val STATUS = stringPreferencesKey("status")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    suspend fun read(): WidgetSnapshot {
        val preferences = context.widgetDataStore.data.first()
        return WidgetSnapshot(
            hasBudget = preferences[HAS_BUDGET] ?: false,
            budgetName = preferences[BUDGET_NAME].orEmpty(),
            availableTodayCents = preferences[AVAILABLE_TODAY] ?: 0L,
            dailyLimitCents = preferences[DAILY_LIMIT] ?: 0L,
            currencyCode = preferences[CURRENCY] ?: "MYR",
            status = preferences[STATUS]
                ?.let { name -> BudgetStatus.entries.firstOrNull { it.name == name } }
                ?: BudgetStatus.ON_TRACK,
            updatedAtMillis = preferences[UPDATED_AT] ?: 0L
        )
    }

    suspend fun write(snapshot: WidgetSnapshot) {
        context.widgetDataStore.edit { preferences ->
            preferences[HAS_BUDGET] = snapshot.hasBudget
            preferences[BUDGET_NAME] = snapshot.budgetName
            preferences[AVAILABLE_TODAY] = snapshot.availableTodayCents
            preferences[DAILY_LIMIT] = snapshot.dailyLimitCents
            preferences[CURRENCY] = snapshot.currencyCode
            preferences[STATUS] = snapshot.status.name
            preferences[UPDATED_AT] = snapshot.updatedAtMillis
        }
    }
}
