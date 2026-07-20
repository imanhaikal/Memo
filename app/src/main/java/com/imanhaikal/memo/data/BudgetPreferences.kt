package com.imanhaikal.memo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToLong

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "budget_preferences")

data class BudgetConfig(
    val totalBudgetCents: Long,
    val cycleStartDateMillis: Long,
    val totalDays: Int,
    val currencyCode: String
)

/** In-app theme override; SYSTEM follows the device dark-mode setting. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromId(id: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: SYSTEM
    }
}

class BudgetPreferences(private val context: Context) {

    companion object {
        private val LEGACY_TOTAL_BUDGET = doublePreferencesKey("total_budget")
        val TOTAL_BUDGET = longPreferencesKey("total_budget_cents")
        val CYCLE_START_DATE = longPreferencesKey("cycle_start_date") // Epoch millis
        val TOTAL_DAYS = intPreferencesKey("total_days")
        val CURRENCY = stringPreferencesKey("currency_code")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { preferences -> ThemeMode.fromId(preferences[THEME_MODE]) }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    val totalBudget: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[TOTAL_BUDGET]
            ?: preferences[LEGACY_TOTAL_BUDGET]?.let { (it * 100).roundToLong() }
            ?: 0L
    }

    val cycleStartDate: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[CYCLE_START_DATE] ?: 0L
    }

    val totalDays: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[TOTAL_DAYS] ?: 30 // Default to 30 days
    }

    val currency: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CURRENCY] ?: "MYR"
    }

    val budgetConfig: Flow<BudgetConfig> = context.dataStore.data.map { preferences ->
        BudgetConfig(
            totalBudgetCents = preferences[TOTAL_BUDGET]
                ?: preferences[LEGACY_TOTAL_BUDGET]?.let { (it * 100).roundToLong() }
                ?: 0L,
            cycleStartDateMillis = preferences[CYCLE_START_DATE] ?: 0L,
            totalDays = preferences[TOTAL_DAYS] ?: 30,
            currencyCode = preferences[CURRENCY] ?: "MYR"
        )
    }.distinctUntilChanged()

    suspend fun saveBudgetSettings(budgetCents: Long, startDate: Long, days: Int, currency: String = "MYR") {
        context.dataStore.edit { preferences ->
            preferences[TOTAL_BUDGET] = budgetCents
            preferences[CYCLE_START_DATE] = startDate
            preferences[TOTAL_DAYS] = days
            preferences[CURRENCY] = currency
        }
    }

    suspend fun updateBudgetConfig(budgetCents: Long, days: Int, currency: String) {
        context.dataStore.edit { preferences ->
            preferences[TOTAL_BUDGET] = budgetCents
            preferences[TOTAL_DAYS] = days
            preferences[CURRENCY] = currency
        }
    }
}
