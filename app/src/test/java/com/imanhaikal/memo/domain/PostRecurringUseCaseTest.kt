package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Cadence
import com.imanhaikal.memo.data.RecurringRule
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.testing.FakeRecurringRuleDao
import com.imanhaikal.memo.testing.FakeTransactionDao
import com.imanhaikal.memo.testing.ImmediateTransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class PostRecurringUseCaseTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2024, 3, 10)
    private val clock: Clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private class Fixture(clock: Clock) {
        val transactionDao = FakeTransactionDao()
        val ruleDao = FakeRecurringRuleDao()
        val useCase = PostRecurringUseCase(
            recurringRuleDao = ruleDao,
            transactionDao = transactionDao,
            runInTransaction = ImmediateTransactionRunner,
            calculator = RecurringScheduleCalculator(),
            clock = clock
        )
    }

    private suspend fun Fixture.addRule(
        cadence: Cadence = Cadence.DAILY,
        start: LocalDate,
        nextDue: LocalDate = start,
        paused: Boolean = false,
        type: TransactionType = TransactionType.EXPENSE
    ): Long = ruleDao.insert(
        RecurringRule(
            budgetId = 1L,
            amountCents = 1_000L,
            note = "Rent",
            type = type,
            cadence = cadence,
            startDate = start.toEpochDay(),
            nextDueDate = nextDue.toEpochDay(),
            isPaused = paused
        )
    )

    @Test
    fun `catching up a gap posts one row per missed day`() = runTest {
        val fixture = Fixture(clock)
        fixture.addRule(start = today.minusDays(4))

        val posted = fixture.useCase.catchUp(today)

        assertEquals(5, posted.size)
        assertEquals(5, fixture.transactionDao.rows.value.size)
    }

    @Test
    fun `running twice posts nothing the second time`() = runTest {
        val fixture = Fixture(clock)
        fixture.addRule(start = today.minusDays(4))

        fixture.useCase.catchUp(today)
        val second = fixture.useCase.catchUp(today)

        assertTrue(second.isEmpty())
        assertEquals(5, fixture.transactionDao.rows.value.size)
    }

    @Test
    fun `a paused rule posts nothing`() = runTest {
        val fixture = Fixture(clock)
        fixture.addRule(start = today.minusDays(4), paused = true)

        val posted = fixture.useCase.catchUp(today)

        assertTrue(posted.isEmpty())
        assertTrue(fixture.transactionDao.rows.value.isEmpty())
    }

    @Test
    fun `posted rows are date-only, tagged with their rule, and on its budget`() = runTest {
        val fixture = Fixture(clock)
        val ruleId = fixture.addRule(start = today)

        fixture.useCase.catchUp(today)

        val row = fixture.transactionDao.rows.value.single()
        assertEquals(ruleId, row.recurringRuleId)
        assertEquals(1L, row.budgetId)
        assertEquals(1_000L, row.amount)
        assertFalse(row.hasTime)
    }

    @Test
    fun `a recurring income posts as income`() = runTest {
        val fixture = Fixture(clock)
        fixture.addRule(start = today, type = TransactionType.INCOME)

        fixture.useCase.catchUp(today)

        assertEquals(TransactionType.INCOME, fixture.transactionDao.rows.value.single().type)
    }

    @Test
    fun `the cursor advances past what was posted`() = runTest {
        val fixture = Fixture(clock)
        val ruleId = fixture.addRule(start = today.minusDays(2))

        fixture.useCase.catchUp(today)

        val rule = fixture.ruleDao.getAll().single { it.id == ruleId }
        assertEquals(today.plusDays(1).toEpochDay(), rule.nextDueDate)
    }

    @Test
    fun `a monthly rule posts once when only one month has passed`() = runTest {
        val fixture = Fixture(clock)
        fixture.addRule(cadence = Cadence.MONTHLY, start = today.minusMonths(1))

        val posted = fixture.useCase.catchUp(today)

        assertEquals(2, posted.size)
    }

    @Test
    fun `nothing posts before the rule's start date`() = runTest {
        val fixture = Fixture(clock)
        fixture.addRule(start = today.plusDays(3))

        val posted = fixture.useCase.catchUp(today)

        assertTrue(posted.isEmpty())
    }
}
