package com.imanhaikal.memo.ui

import androidx.compose.runtime.Immutable
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.TransactionType

/**
 * What the history search is currently filtering by.
 *
 * Held separately from [BudgetUiState] so typing in the search box doesn't invalidate the
 * dashboard's state and re-run the budget calculation on every keystroke.
 */
@Immutable
data class SearchCriteria(
    val query: String = "",
    val category: Category? = null,
    val type: TransactionType? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null
) {
    val isEmpty: Boolean
        get() = query.isBlank() && category == null && type == null &&
            minCents == null && maxCents == null && fromMillis == null && toMillis == null
}
