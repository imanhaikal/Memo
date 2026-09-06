package com.imanhaikal.memo.ui.screens

import androidx.compose.runtime.Immutable

/**
 * What a chosen backup file contains, so the user can be shown it before anything is
 * written. Deliberately a UI type rather than the parsed backup itself — the screen only
 * needs the counts, not a whole database in memory twice.
 */
@Immutable
data class BackupSummary(
    val budgets: Int,
    val transactions: Int,
    val exportedOn: String?
)

/** A file the user picked, held until they choose Add or Replace. */
@Immutable
data class PendingImport(
    val contents: String,
    val summary: BackupSummary
)
