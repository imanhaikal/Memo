package com.imanhaikal.memo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY isArchived ASC, sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    fun observeById(id: Long): Flow<Budget?>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): Budget?

    @Query("SELECT * FROM budgets ORDER BY isArchived ASC, sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<Budget>

    /** The fallback when no active id is stored, or the stored one has been deleted. */
    @Query("SELECT * FROM budgets WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC LIMIT 1")
    suspend fun getFirstActive(): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<Budget>)

    @Update
    suspend fun update(budget: Budget)

    @Query("UPDATE budgets SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM budgets")
    suspend fun count(): Int
}
