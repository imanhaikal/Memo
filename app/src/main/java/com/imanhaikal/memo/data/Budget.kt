package com.imanhaikal.memo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One budget the user tracks against, e.g. "Monthly" or "Travel". Several may exist
 * at once; exactly one is active at a time (see [BudgetPreferences.activeBudgetId]).
 *
 * [firstCycleStartDate] is an epoch *day*, not millis. Cycle boundaries are pure
 * calendar dates — storing them as instants makes a budget silently shift by a day
 * when the user changes timezone.
 */
@Entity(tableName = "budgets")
@Serializable
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val totalDays: Int,
    val currencyCode: String,
    val firstCycleStartDate: Long,
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0
)
