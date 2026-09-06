package com.imanhaikal.memo.data

import kotlinx.serialization.Serializable

/**
 * Whether a row takes money out of the budget or puts it back in. [id] is the
 * stable identifier persisted in the database — never change existing ids.
 *
 * Amounts are always stored as a positive magnitude; this type carries the sign,
 * so the amount input keeps its "greater than zero" rule.
 */
@Serializable
enum class TransactionType(val id: String) {
    EXPENSE("expense"),
    INCOME("income");

    companion object {
        /** An unknown or missing id falls back to [EXPENSE], matching the column default. */
        fun fromId(id: String?): TransactionType =
            entries.firstOrNull { it.id == id } ?: EXPENSE
    }
}
