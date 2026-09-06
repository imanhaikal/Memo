package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.RecurringRuleDao
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.TransactionRunner
import java.time.Clock
import java.time.LocalDate

/**
 * Posts recurring rules that have fallen due.
 *
 * Runs on every app start as well as from a worker. WorkManager is the convenience —
 * OEM battery managers suppress background work for days at a time, and a rent expense
 * silently not posting is a data-integrity problem, not a missed notification. Launch
 * catch-up is what makes it a guarantee.
 *
 * Idempotent twice over: the rule's `nextDueDate` cursor advances inside the same
 * database transaction as the inserts, and each occurrence is additionally checked
 * against what is already filed for that rule on that day.
 */
class PostRecurringUseCase(
    private val recurringRuleDao: RecurringRuleDao,
    private val transactionDao: TransactionDao,
    private val runInTransaction: TransactionRunner,
    private val calculator: RecurringScheduleCalculator,
    private val clock: Clock
) {

    /** Returns the transactions it wrote, so the caller can notify about them. */
    suspend fun catchUp(today: LocalDate = LocalDate.now(clock)): List<Transaction> {
        val posted = mutableListOf<Transaction>()

        recurringRuleDao.getActiveRules().forEach { rule ->
            val due = calculator.dueDates(rule, today)
            if (due.isEmpty()) return@forEach

            runInTransaction {
                due.forEach { date ->
                    val dayStart = CycleMath.dayStartMillis(date.toEpochDay(), clock.zone)
                    val dayEnd = CycleMath.dayStartMillis(date.toEpochDay() + 1, clock.zone)

                    // Second guard: a worker and a launch catch-up firing in the same
                    // window must not post the same rent twice.
                    if (transactionDao.countPostedForRuleOnDay(rule.id, dayStart, dayEnd) > 0) {
                        return@forEach
                    }

                    val transaction = Transaction(
                        amount = rule.amountCents,
                        note = rule.note,
                        // Anchored at noon and marked date-only, matching how every other
                        // backdated entry is stored.
                        date = dayStart + NOON_OFFSET_MILLIS,
                        category = rule.category,
                        hasTime = false,
                        budgetId = rule.budgetId,
                        type = rule.type,
                        recurringRuleId = rule.id
                    )
                    transactionDao.insertTransaction(transaction)
                    posted += transaction
                }

                val next = calculator.nextDueDate(rule, due.last())
                recurringRuleDao.update(
                    rule.copy(
                        // A rule past its end date parks on the day after its last
                        // occurrence, so it simply never comes due again.
                        nextDueDate = next?.toEpochDay() ?: (due.last().toEpochDay() + 1),
                        isPaused = rule.isPaused || next == null,
                        lastPostedAt = clock.millis()
                    )
                )
            }
        }

        return posted
    }

    private companion object {
        const val NOON_OFFSET_MILLIS = 12L * 60 * 60 * 1000
    }
}
