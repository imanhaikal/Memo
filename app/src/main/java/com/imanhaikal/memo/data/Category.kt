package com.imanhaikal.memo.data

import kotlinx.serialization.Serializable

/**
 * Expense category. [id] is the stable identifier persisted in the database and
 * used as the enum vocabulary for AI extraction — never change existing ids.
 */
@Serializable
enum class Category(val id: String, val label: String) {
    FOOD("food", "Food"),
    TRANSPORT("transport", "Transport"),
    SHOPPING("shopping", "Shopping"),
    BILLS("bills", "Bills"),
    ENTERTAINMENT("entertainment", "Entertainment"),
    HEALTH("health", "Health"),
    OTHER("other", "Other");

    companion object {
        /**
         * Null/blank means uncategorized; an unknown non-blank id maps to [OTHER]
         * so rows written by a future version still resolve to something sensible.
         */
        fun fromId(id: String?): Category? {
            if (id.isNullOrBlank()) return null
            return entries.firstOrNull { it.id == id } ?: OTHER
        }
    }
}
