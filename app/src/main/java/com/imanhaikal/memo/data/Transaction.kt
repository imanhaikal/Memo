package com.imanhaikal.memo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "transactions")
@Serializable
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Long, // Stored in cents
    val note: String,
    val date: Long, // Epoch millis; anchored at local noon when hasTime is false
    val category: Category? = null, // null = uncategorized
    @ColumnInfo(defaultValue = "")
    val description: String = "",
    // False when only the day is known (e.g. backdated entry, date-only receipt)
    @ColumnInfo(defaultValue = "1")
    val hasTime: Boolean = true
)