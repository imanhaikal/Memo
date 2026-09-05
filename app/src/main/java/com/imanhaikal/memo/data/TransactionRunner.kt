package com.imanhaikal.memo.data

import androidx.room.withTransaction

/**
 * Runs a block of writes atomically.
 *
 * Exists so [BudgetRepository] depends on this rather than on [AppDatabase] directly:
 * `withTransaction` needs a real Room instance, which would push every repository test
 * onto a device for no benefit.
 */
interface TransactionRunner {
    suspend operator fun <R> invoke(block: suspend () -> R): R
}

class RoomTransactionRunner(private val database: AppDatabase) : TransactionRunner {
    override suspend operator fun <R> invoke(block: suspend () -> R): R =
        database.withTransaction { block() }
}
