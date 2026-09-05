package com.imanhaikal.memo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.roundToLong

/**
 * The single budget that lived in DataStore before v5. Read once by [BudgetBootstrap]
 * and then left alone — nothing writes these keys any more.
 */
data class LegacyBudgetConfig(
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

/**
 * Which budget the app is currently showing.
 *
 * Split out of [BudgetPreferences] so [BudgetRepository] depends on this narrow contract
 * rather than on a Context-bound DataStore class, which would push its tests onto a device.
 */
interface ActiveBudgetStore {
    val activeBudgetId: Flow<Long>
    suspend fun setActiveBudgetId(id: Long)
}

/**
 * Device-scoped preferences: which budget is active, appearance, and the one-time
 * flag recording that the pre-v5 DataStore budget has been copied into Room.
 *
 * Budget values themselves now live in the `budgets` table, not here.
 */
class BudgetPreferences(private val context: Context) : ActiveBudgetStore {

    companion object {
        private val LEGACY_TOTAL_BUDGET_DOUBLE = doublePreferencesKey("total_budget")
        internal val LEGACY_TOTAL_BUDGET = longPreferencesKey("total_budget_cents")
        internal val LEGACY_CYCLE_START_DATE = longPreferencesKey("cycle_start_date")
        internal val LEGACY_TOTAL_DAYS = intPreferencesKey("total_days")
        internal val LEGACY_CURRENCY = stringPreferencesKey("currency_code")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val ACTIVE_BUDGET_ID = longPreferencesKey("active_budget_id")
        val MIGRATED_TO_ROOM = booleanPreferencesKey("migrated_to_room")
    }

    val themeMode: Flow<ThemeMode> = context.memoDataStore.data
        .map { preferences -> ThemeMode.fromId(preferences[THEME_MODE]) }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.memoDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    /** In-app haptics switch; the device-wide setting is checked separately. */
    val hapticsEnabled: Flow<Boolean> = context.memoDataStore.data
        .map { preferences -> preferences[HAPTICS_ENABLED] ?: true }
        .distinctUntilChanged()

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.memoDataStore.edit { preferences ->
            preferences[HAPTICS_ENABLED] = enabled
        }
    }

    /** Which budget the dashboard is showing. Falls back to the migrated default. */
    override val activeBudgetId: Flow<Long> = context.memoDataStore.data
        .map { preferences -> preferences[ACTIVE_BUDGET_ID] ?: AppDatabase.DEFAULT_BUDGET_ID }
        .distinctUntilChanged()

    override suspend fun setActiveBudgetId(id: Long) {
        context.memoDataStore.edit { preferences ->
            preferences[ACTIVE_BUDGET_ID] = id
        }
    }

    suspend fun isMigratedToRoom(): Boolean =
        context.memoDataStore.data.first()[MIGRATED_TO_ROOM] ?: false

    suspend fun setMigratedToRoom() {
        context.memoDataStore.edit { preferences ->
            preferences[MIGRATED_TO_ROOM] = true
        }
    }

    /**
     * The pre-v5 budget, or null when this install never had one (a fresh install, where
     * the budget amount was never set). The legacy Double key is still honoured so a very
     * old install migrates correctly.
     */
    suspend fun readLegacyBudget(): LegacyBudgetConfig? {
        val preferences = context.memoDataStore.data.first()
        val cents = preferences[LEGACY_TOTAL_BUDGET]
            ?: preferences[LEGACY_TOTAL_BUDGET_DOUBLE]?.let { (it * 100).roundToLong() }
            ?: return null
        val startDate = preferences[LEGACY_CYCLE_START_DATE] ?: return null
        if (cents <= 0L || startDate <= 0L) return null
        return LegacyBudgetConfig(
            totalBudgetCents = cents,
            cycleStartDateMillis = startDate,
            totalDays = preferences[LEGACY_TOTAL_DAYS] ?: 30,
            currencyCode = preferences[LEGACY_CURRENCY] ?: "MYR"
        )
    }
}
