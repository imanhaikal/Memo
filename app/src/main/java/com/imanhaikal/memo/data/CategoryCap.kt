package com.imanhaikal.memo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable

/** An optional per-category spending limit within one budget's cycle. */
@Entity(
    tableName = "category_caps",
    primaryKeys = ["budgetId", "category"],
    foreignKeys = [
        ForeignKey(
            entity = Budget::class,
            parentColumns = ["id"],
            childColumns = ["budgetId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@Serializable
data class CategoryCap(
    val budgetId: Long,
    /** [Category.id]; stored as its stable string rather than the enum ordinal. */
    val category: String,
    val capCents: Long
)
