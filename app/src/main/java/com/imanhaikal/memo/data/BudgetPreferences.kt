package com.imanhaikal.memo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "budget_preferences")

class BudgetPreferences(private val context: Context) {

    companion object {
        val TOTAL_BUDGET = doublePreferencesKey("total_budget")
        val CYCLE_START_DATE = longPreferencesKey("cycle_start_date") // Epoch millis
        val TOTAL_DAYS = intPreferencesKey("total_days")
    }

    val totalBudget: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[TOTAL_BUDGET] ?: 0.0
    }

    val cycleStartDate: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[CYCLE_START_DATE] ?: 0L
    }

    val totalDays: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[TOTAL_DAYS] ?: 30 // Default to 30 days
    }

    suspend fun saveBudgetSettings(budget: Double, startDate: Long, days: Int) {
        context.dataStore.edit { preferences ->
            preferences[TOTAL_BUDGET] = budget
            preferences[CYCLE_START_DATE] = startDate
            preferences[TOTAL_DAYS] = days
        }
    }
}