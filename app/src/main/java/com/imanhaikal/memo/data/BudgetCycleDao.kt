package com.imanhaikal.memo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetCycleDao {
    @Query("SELECT * FROM budget_cycles WHERE budgetId = :budgetId ORDER BY cycleIndex DESC")
    fun observeForBudget(budgetId: Long): Flow<List<BudgetCycle>>

    /** Finished cycles only, newest first — what the history screen shows. */
    @Query(
        """
        SELECT * FROM budget_cycles
        WHERE budgetId = :budgetId AND closedAt IS NOT NULL
        ORDER BY cycleIndex DESC
        """
    )
    fun observeClosed(budgetId: Long): Flow<List<BudgetCycle>>

    @Query("SELECT * FROM budget_cycles WHERE id = :id")
    suspend fun getById(id: Long): BudgetCycle?

    @Query("SELECT * FROM budget_cycles WHERE budgetId = :budgetId AND closedAt IS NULL LIMIT 1")
    suspend fun getOpenCycle(budgetId: Long): BudgetCycle?

    /**
     * The row already holding [cycleIndex], if any.
     *
     * `budget_cycles` is unique on (budgetId, cycleIndex) and [insert] resolves with
     * REPLACE, so anything about to create a cycle must look here first: replacing would
     * delete the history row sitting on that index.
     */
    @Query("SELECT * FROM budget_cycles WHERE budgetId = :budgetId AND cycleIndex = :cycleIndex")
    suspend fun getByIndex(budgetId: Long, cycleIndex: Int): BudgetCycle?

    /** Un-closes a cycle that today has fallen back into after a change to its budget. */
    @Query("UPDATE budget_cycles SET closedAt = NULL WHERE id = :id")
    suspend fun reopen(id: Long)

    @Query("SELECT * FROM budget_cycles")
    suspend fun getAll(): List<BudgetCycle>

    /**
     * Spend for one cycle's date range, computed from the live transaction rows so a
     * backdated edit is reflected instead of contradicting a stored total.
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS spentCents,
            COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS incomeCents,
            COUNT(*) AS transactionCount
        FROM transactions
        WHERE budgetId = :budgetId AND date >= :startMillis AND date < :endMillisExclusive
        """
    )
    suspend fun getTotals(
        budgetId: Long,
        startMillis: Long,
        endMillisExclusive: Long
    ): CycleTotals

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cycle: BudgetCycle): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cycles: List<BudgetCycle>)

    @Query("UPDATE budget_cycles SET closedAt = :closedAt WHERE id = :id")
    suspend fun close(id: Long, closedAt: Long)

    /**
     * Re-syncs an open cycle with its budget after the user edits it.
     *
     * Guarded on `closedAt IS NULL`: a finished cycle keeps the amount and dates it
     * actually ran with, so editing the budget can never rewrite history.
     */
    @Query(
        """
        UPDATE budget_cycles
        SET startDate = :startDate,
            endDateExclusive = :endDateExclusive,
            budgetAmountCents = :budgetAmountCents
        WHERE id = :id AND closedAt IS NULL
        """
    )
    suspend fun syncOpenCycle(
        id: Long,
        startDate: Long,
        endDateExclusive: Long,
        budgetAmountCents: Long
    )

    @Query("DELETE FROM budget_cycles WHERE budgetId = :budgetId")
    suspend fun deleteForBudget(budgetId: Long)

    @Query("DELETE FROM budget_cycles")
    suspend fun deleteAll()
}
