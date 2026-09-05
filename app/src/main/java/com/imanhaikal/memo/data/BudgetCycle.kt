package com.imanhaikal.memo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One elapsed or in-progress period of a [Budget].
 *
 * Deliberately stores no spend totals. Totals are derived from the transactions in the
 * cycle's date range — a stored total would be wrong the moment the user backdates an
 * expense into a cycle that has already closed. [budgetAmountCents] *is* snapshotted,
 * because it cannot be recovered: editing the budget would otherwise rewrite history.
 *
 * [startDate] and [endDateExclusive] are epoch days, matching [Budget.firstCycleStartDate].
 */
@Entity(
    tableName = "budget_cycles",
    foreignKeys = [
        ForeignKey(
            entity = Budget::class,
            parentColumns = ["id"],
            childColumns = ["budgetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["budgetId", "cycleIndex"], unique = true)]
)
@Serializable
data class BudgetCycle(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val budgetId: Long,
    /** 0-based period number since the budget's first cycle. */
    val cycleIndex: Int,
    val startDate: Long,
    val endDateExclusive: Long,
    /** The budget amount as it stood for this cycle; later edits don't rewrite history. */
    val budgetAmountCents: Long,
    /** Null while this is the cycle containing today. */
    val closedAt: Long? = null
)

/** Aggregate spend for one cycle, computed on read rather than stored. */
data class CycleTotals(
    val spentCents: Long,
    val incomeCents: Long,
    val transactionCount: Int
) {
    /** What the cycle actually cost: income gives money back to the pool. */
    val netSpentCents: Long get() = spentCents - incomeCents

    companion object {
        val EMPTY = CycleTotals(0L, 0L, 0)
    }
}
