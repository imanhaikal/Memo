package com.imanhaikal.memo.data.backup

import kotlinx.serialization.Serializable

/**
 * The on-disk backup format.
 *
 * These are deliberately DTOs rather than the Room entities, even though those are already
 * `@Serializable`. Entity field names are a database detail; freezing them into a file
 * format would make every future rename a breaking change to files users already hold.
 *
 * Every list defaults to empty so a section added later still loads an older file, and an
 * older app can be told what it is looking at rather than silently dropping data.
 */
@Serializable
data class MemoBackup(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAtMillis: Long,
    val appVersionCode: Int = 0,
    val activeBudgetId: Long? = null,
    val budgets: List<BackupBudget> = emptyList(),
    val cycles: List<BackupCycle> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val categoryCaps: List<BackupCategoryCap> = emptyList(),
    val recurringRules: List<BackupRecurringRule> = emptyList()
) {
    companion object {
        const val FORMAT = "memo-budget-backup"

        /** Bump only for a breaking change; additive fields do not need it. */
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupBudget(
    val id: Long,
    val name: String,
    val amountCents: Long,
    val totalDays: Int,
    val currencyCode: String,
    val firstCycleStartDate: Long,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val sortOrder: Int = 0
)

@Serializable
data class BackupCycle(
    val id: Long,
    val budgetId: Long,
    val cycleIndex: Int,
    val startDate: Long,
    val endDateExclusive: Long,
    val budgetAmountCents: Long,
    val closedAt: Long? = null
)

@Serializable
data class BackupTransaction(
    val id: Int,
    val budgetId: Long,
    val amount: Long,
    val type: String,
    val note: String,
    val date: Long,
    val category: String? = null,
    val description: String = "",
    val hasTime: Boolean = true,
    val recurringRuleId: Long? = null
)

@Serializable
data class BackupCategoryCap(
    val budgetId: Long,
    val category: String,
    val capCents: Long
)

@Serializable
data class BackupRecurringRule(
    val id: Long,
    val budgetId: Long,
    val amountCents: Long,
    val note: String,
    val category: String? = null,
    val type: String,
    val cadence: String,
    val intervalCount: Int = 1,
    val startDate: Long,
    val endDate: Long? = null,
    val nextDueDate: Long,
    val isPaused: Boolean = false,
    val lastPostedAt: Long? = null
)

/** How an import treats data already in the database. */
enum class ImportMode {
    /** Adds what is missing and leaves everything already there alone. */
    MERGE,

    /** Replaces the entire database with the file's contents. */
    REPLACE
}

sealed interface ImportResult {
    data class Success(
        val budgets: Int,
        val transactions: Int,
        val skipped: Int
    ) : ImportResult

    data class Failure(val reason: ImportFailure) : ImportResult
}

enum class ImportFailure {
    NOT_A_MEMO_BACKUP,
    NEWER_SCHEMA,
    CORRUPT,
    IO_ERROR;

    /** Wording shown in the snackbar; kept here so the UI has nothing to invent. */
    val message: String
        get() = when (this) {
            NOT_A_MEMO_BACKUP -> "That file isn't a Memo backup"
            NEWER_SCHEMA -> "That backup was made by a newer version of Memo"
            CORRUPT -> "That backup file is damaged and can't be read"
            IO_ERROR -> "Couldn't read that file"
        }
}
