package com.imanhaikal.memo.data.backup

import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetCycle
import com.imanhaikal.memo.data.Cadence
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.CategoryCap
import com.imanhaikal.memo.data.RecurringRule
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.testing.FakeActiveBudgetStore
import com.imanhaikal.memo.testing.FakeBudgetCycleDao
import com.imanhaikal.memo.testing.FakeBudgetDao
import com.imanhaikal.memo.testing.FakeCategoryCapDao
import com.imanhaikal.memo.testing.FakeRecurringRuleDao
import com.imanhaikal.memo.testing.FakeTransactionDao
import com.imanhaikal.memo.testing.ImmediateTransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class BackupRepositoryTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2024-06-01T10:00:00Z"), ZoneId.of("UTC"))

    private class Fixture(clock: Clock) {
        val transactionDao = FakeTransactionDao()
        val budgetDao = FakeBudgetDao()
        val cycleDao = FakeBudgetCycleDao(transactionDao)
        val capDao = FakeCategoryCapDao()
        val ruleDao = FakeRecurringRuleDao()
        val activeBudgetStore = FakeActiveBudgetStore()

        val repository = BackupRepository(
            runInTransaction = ImmediateTransactionRunner,
            budgetDao = budgetDao,
            budgetCycleDao = cycleDao,
            categoryCapDao = capDao,
            recurringRuleDao = ruleDao,
            transactionDao = transactionDao,
            activeBudgetStore = activeBudgetStore,
            clock = clock
        )
    }

    private suspend fun Fixture.seed() {
        budgetDao.insert(
            Budget(
                id = 1L,
                name = "Monthly",
                amountCents = 300_000L,
                totalDays = 30,
                currencyCode = "MYR",
                firstCycleStartDate = 19_700L,
                createdAt = 1_700_000_000_000L
            )
        )
        budgetDao.insert(
            Budget(
                id = 2L,
                name = "Travel",
                amountCents = 150_000L,
                totalDays = 14,
                currencyCode = "USD",
                firstCycleStartDate = 19_800L,
                createdAt = 1_700_000_100_000L,
                sortOrder = 1
            )
        )
        cycleDao.insert(
            BudgetCycle(
                id = 1L,
                budgetId = 1L,
                cycleIndex = 0,
                startDate = 19_700L,
                endDateExclusive = 19_730L,
                budgetAmountCents = 300_000L,
                closedAt = 1_700_500_000_000L
            )
        )
        capDao.upsert(CategoryCap(budgetId = 1L, category = Category.FOOD.id, capCents = 50_000L))
        ruleDao.insert(
            RecurringRule(
                id = 1L,
                budgetId = 1L,
                amountCents = 120_000L,
                note = "Rent",
                cadence = Cadence.MONTHLY,
                startDate = 19_700L,
                nextDueDate = 19_730L
            )
        )
        transactionDao.insertTransaction(
            Transaction(
                id = 1,
                amount = 1_250L,
                note = "Lunch",
                date = 1_700_000_200_000L,
                category = Category.FOOD,
                description = "Nasi lemak",
                budgetId = 1L
            )
        )
        transactionDao.insertTransaction(
            Transaction(
                id = 2,
                amount = 5_000L,
                note = "Refund",
                date = 1_700_000_300_000L,
                budgetId = 1L,
                type = TransactionType.INCOME
            )
        )
        transactionDao.insertTransaction(
            Transaction(
                id = 3,
                amount = 9_900L,
                note = "Hotel",
                date = 1_700_000_400_000L,
                budgetId = 2L
            )
        )
    }

    @Test
    fun `export and import into an empty database round-trips everything`() = runTest {
        val source = Fixture(clock).apply { seed() }
        val json = source.repository.export()

        val target = Fixture(clock)
        val result = target.repository.import(json, ImportMode.REPLACE)

        assertTrue(result is ImportResult.Success)
        assertEquals(source.budgetDao.getAll(), target.budgetDao.getAll())
        assertEquals(
            source.transactionDao.getAll().sortedBy { it.id },
            target.transactionDao.getAll().sortedBy { it.id }
        )
        assertEquals(source.cycleDao.getAll(), target.cycleDao.getAll())
        assertEquals(source.capDao.getAll(), target.capDao.getAll())
        assertEquals(source.ruleDao.getAll(), target.ruleDao.getAll())
    }

    @Test
    fun `income survives the round trip as income`() = runTest {
        val source = Fixture(clock).apply { seed() }
        val target = Fixture(clock)
        target.repository.import(source.repository.export(), ImportMode.REPLACE)

        val refund = target.transactionDao.getAll().single { it.note == "Refund" }
        assertEquals(TransactionType.INCOME, refund.type)
        assertEquals(5_000L, refund.amount)
        assertEquals(-5_000L, refund.signedAmount)
    }

    @Test
    fun `replace wipes what was already there`() = runTest {
        val source = Fixture(clock).apply { seed() }
        val target = Fixture(clock)
        target.budgetDao.insert(
            Budget(
                id = 9L,
                name = "Old",
                amountCents = 1_000L,
                totalDays = 7,
                currencyCode = "MYR",
                firstCycleStartDate = 19_000L,
                createdAt = 1L
            )
        )
        target.transactionDao.insertTransaction(
            Transaction(id = 99, amount = 1L, note = "Stale", date = 1L, budgetId = 9L)
        )

        target.repository.import(source.repository.export(), ImportMode.REPLACE)

        assertTrue(target.budgetDao.getAll().none { it.name == "Old" })
        assertTrue(target.transactionDao.getAll().none { it.note == "Stale" })
    }

    @Test
    fun `importing the same file twice in merge mode adds nothing the second time`() = runTest {
        val source = Fixture(clock).apply { seed() }
        val json = source.repository.export()
        val target = Fixture(clock)

        val first = target.repository.import(json, ImportMode.MERGE) as ImportResult.Success
        val countAfterFirst = target.transactionDao.getAll().size
        val second = target.repository.import(json, ImportMode.MERGE) as ImportResult.Success

        assertEquals(3, first.transactions)
        assertEquals(0, second.transactions)
        assertEquals(3, second.skipped)
        assertEquals(countAfterFirst, target.transactionDao.getAll().size)
        assertEquals(2, target.budgetDao.getAll().size)
    }

    @Test
    fun `merge keeps existing data and matches budgets by name`() = runTest {
        val source = Fixture(clock).apply { seed() }
        val target = Fixture(clock)
        val existingId = target.budgetDao.insert(
            Budget(
                id = 0L,
                name = "Monthly",
                amountCents = 999L,
                totalDays = 30,
                currencyCode = "MYR",
                firstCycleStartDate = 19_700L,
                createdAt = 5L
            )
        )
        target.transactionDao.insertTransaction(
            Transaction(id = 50, amount = 700L, note = "Mine", date = 5L, budgetId = existingId)
        )

        target.repository.import(source.repository.export(), ImportMode.MERGE)

        // The existing "Monthly" is reused rather than duplicated, and its own rows stay.
        assertEquals(2, target.budgetDao.getAll().size)
        assertNotNull(target.transactionDao.getAll().firstOrNull { it.note == "Mine" })
        assertEquals(999L, target.budgetDao.getById(existingId)!!.amountCents)
        // The imported rows landed on that same budget.
        assertTrue(target.transactionDao.getAll().any { it.note == "Lunch" && it.budgetId == existingId })
    }

    @Test
    fun `a file from a newer version is refused rather than partially loaded`() = runTest {
        val target = Fixture(clock)
        val json = """
            {"format":"memo-budget-backup","schemaVersion":99,"exportedAtMillis":1}
        """.trimIndent()

        val result = target.repository.import(json, ImportMode.REPLACE)

        assertEquals(ImportFailure.NEWER_SCHEMA, (result as ImportResult.Failure).reason)
    }

    @Test
    fun `an unrelated json file is rejected`() = runTest {
        val target = Fixture(clock)
        val result = target.repository.import("""{"hello":"world"}""", ImportMode.REPLACE)

        assertEquals(ImportFailure.NOT_A_MEMO_BACKUP, (result as ImportResult.Failure).reason)
    }

    @Test
    fun `a damaged file fails cleanly instead of throwing`() = runTest {
        val target = Fixture(clock)
        val result = target.repository.import("not json at all {{{", ImportMode.REPLACE)

        assertEquals(ImportFailure.CORRUPT, (result as ImportResult.Failure).reason)
    }

    @Test
    fun `restoring sets the active budget the backup was taken with`() = runTest {
        val source = Fixture(clock).apply {
            seed()
            activeBudgetStore.setActiveBudgetId(2L)
        }
        val target = Fixture(clock)

        target.repository.import(source.repository.export(), ImportMode.REPLACE)

        assertEquals(2L, target.activeBudgetStore.state.value)
    }
}
