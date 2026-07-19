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
    val date: Long, // Epoch millis
    val category: Category? = null, // null = uncategorized
    @ColumnInfo(defaultValue = "")
    val description: String = ""
)