package com.imanhaikal.memo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules WHERE budgetId = :budgetId ORDER BY nextDueDate ASC")
    fun observeForBudget(budgetId: Long): Flow<List<RecurringRule>>

    /** Every rule across every budget — the worker posts for all of them, not just the active one. */
    @Query("SELECT * FROM recurring_rules WHERE isPaused = 0")
    suspend fun getActiveRules(): List<RecurringRule>

    @Query("SELECT * FROM recurring_rules")
    suspend fun getAll(): List<RecurringRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RecurringRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<RecurringRule>)

    @Update
    suspend fun update(rule: RecurringRule)

    @Delete
    suspend fun delete(rule: RecurringRule)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()
}
