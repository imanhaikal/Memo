package com.imanhaikal.memo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** How often a [RecurringRule] fires. [id] is persisted — never change existing ids. */
@Serializable
enum class Cadence(val id: String, val label: String) {
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly");

    companion object {
        fun fromId(id: String?): Cadence =
            entries.firstOrNull { it.id == id } ?: MONTHLY
    }
}

/**
 * A transaction that posts itself on a schedule — rent, a subscription, a salary.
 *
 * [startDate], [endDate] and [nextDueDate] are epoch days. [nextDueDate] is the
 * cursor: posting an occurrence advances it, which is what makes catch-up on launch
 * and the daily worker idempotent with each other.
 */
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = Budget::class,
            parentColumns = ["id"],
            childColumns = ["budgetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["budgetId"])]
)
@Serializable
data class RecurringRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val budgetId: Long,
    val amountCents: Long,
    val note: String,
    val category: Category? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val cadence: Cadence,
    /** Fire every [intervalCount] units of [cadence]; 1 = every day/week/month. */
    @ColumnInfo(defaultValue = "1")
    val intervalCount: Int = 1,
    val startDate: Long,
    val endDate: Long? = null,
    val nextDueDate: Long,
    @ColumnInfo(defaultValue = "0")
    val isPaused: Boolean = false,
    val lastPostedAt: Long? = null
)
