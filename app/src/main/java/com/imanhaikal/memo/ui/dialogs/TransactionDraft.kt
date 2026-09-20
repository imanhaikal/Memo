package com.imanhaikal.memo.ui.dialogs

import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.TransactionType

/**
 * What [AddExpenseDialog] hands back on confirm.
 *
 * Replaces a seven-argument positional lambda whose middle was three nullables and a
 * boolean — at that width the call sites stop being readable and start being a place to
 * transpose two arguments.
 */
data class TransactionDraft(
    val amountCents: Long,
    val note: String,
    val dateMillis: Long?,
    val hasTime: Boolean,
    val category: Category?,
    val description: String,
    val type: TransactionType = TransactionType.EXPENSE,
    /**
     * The receipt image to file against this entry, already copied into internal storage
     * by the time it gets here. Null means no image — which on an edit is how the user
     * removes the one that was there.
     */
    val receiptFileName: String? = null
)
