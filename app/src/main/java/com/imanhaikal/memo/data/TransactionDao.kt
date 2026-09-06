package com.imanhaikal.memo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /** Every row across every budget — for backup and whole-database export only. */
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE budgetId = :budgetId ORDER BY date DESC")
    fun observeForBudget(budgetId: Long): Flow<List<Transaction>>

    /** [startMillis] inclusive, [endMillisExclusive] exclusive. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE budgetId = :budgetId AND date >= :startMillis AND date < :endMillisExclusive
        ORDER BY date DESC
        """
    )
    suspend fun getForCycle(
        budgetId: Long,
        startMillis: Long,
        endMillisExclusive: Long
    ): List<Transaction>

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE budgetId = :budgetId")
    suspend fun deleteAllForBudget(budgetId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    /**
     * Filtered history search.
     *
     * One static query with `IS NULL OR` rather than a RawQuery: Room verifies it at
     * compile time and it stays observable. Filtering in memory was the alternative, but
     * the dashboard already holds every row for the budget and that list only grows.
     *
     * [categoryId] and [type] are raw strings rather than enums — Room's converter path
     * makes `:param IS NULL` unreliable for a nullable enum parameter.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE budgetId = :budgetId
          AND (:query = '' OR note LIKE '%' || :query || '%'
                           OR description LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR category = :categoryId)
          AND (:type IS NULL OR type = :type)
        ORDER BY date DESC
        """
    )
    fun search(
        budgetId: Long,
        query: String,
        categoryId: String?,
        type: String?
    ): Flow<List<Transaction>>

    /** True when this rule already posted an occurrence covering [dayStart]..[dayEndExclusive]. */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE recurringRuleId = :ruleId AND date >= :dayStart AND date < :dayEndExclusive
        """
    )
    suspend fun countPostedForRuleOnDay(
        ruleId: Long,
        dayStart: Long,
        dayEndExclusive: Long
    ): Int
}
