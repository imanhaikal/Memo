package com.imanhaikal.memo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryCapDao {
    @Query("SELECT * FROM category_caps WHERE budgetId = :budgetId")
    fun observeForBudget(budgetId: Long): Flow<List<CategoryCap>>

    @Query("SELECT * FROM category_caps")
    suspend fun getAll(): List<CategoryCap>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cap: CategoryCap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(caps: List<CategoryCap>)

    @Query("DELETE FROM category_caps WHERE budgetId = :budgetId AND category = :categoryId")
    suspend fun delete(budgetId: Long, categoryId: String)

    @Query("DELETE FROM category_caps")
    suspend fun deleteAll()
}
