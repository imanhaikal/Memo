package com.imanhaikal.memo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["budgetId", "date"])]
)
@Serializable
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Long, // Stored in cents, always positive; `type` carries the sign
    val note: String,
    val date: Long, // Epoch millis; anchored at local noon when hasTime is false
    val category: Category? = null, // null = uncategorized
    @ColumnInfo(defaultValue = "")
    val description: String = "",
    // False when only the day is known (e.g. backdated entry, date-only receipt)
    @ColumnInfo(defaultValue = "1")
    val hasTime: Boolean = true,
    // Owning budget. No foreign key: a transaction outliving a deleted budget is
    // preferable to cascading a user's spending history away.
    @ColumnInfo(defaultValue = "1")
    val budgetId: Long = 1,
    @ColumnInfo(defaultValue = "expense")
    val type: TransactionType = TransactionType.EXPENSE,
    /** Set when this row was posted by a [RecurringRule], null when entered by hand. */
    val recurringRuleId: Long? = null
) {
    /** Signed contribution to spending: income gives money back to the pool. */
    val signedAmount: Long
        get() = if (type == TransactionType.INCOME) -amount else amount

    companion object {
        /**
         * Caps applied to AI-extracted text before it is offered to the user. The expense
         * dialog deliberately does not enforce [NOTE_MAX_CHARS] on typing: a note is seeded
         * from the stored row when editing, so capping there would truncate an existing
         * longer note on the first keystroke.
         */
        const val NOTE_MAX_CHARS = 40
        const val DESCRIPTION_MAX_CHARS = 280
    }
}
