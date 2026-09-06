package com.imanhaikal.memo.domain

import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.testing.FakeBudgetCycleDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class CycleRolloverUseCaseTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val start = LocalDate.of(2024, 1, 1)

    private fun budget(totalDays: Int = 30) = Budget(
        id = 1L,
        name = "Monthly",
        amountCents = 300_000L,
        totalDays = totalDays,
        currencyCode = "MYR",
        firstCycleStartDate = start.toEpochDay(),
        createdAt = 0L
    )

    private fun rollover(dao: FakeBudgetCycleDao) =
        CycleRolloverUseCase(dao, Clock.fixed(start.atStartOfDay(zone).toInstant(), zone))

    @Test
    fun `opens the first cycle when none exists`() = runTest {
        val dao = FakeBudgetCycleDao()
        val cycle = rollover(dao).ensureCurrentCycle(budget(), start)

        assertEquals(0, cycle.cycleIndex)
        assertEquals(start.toEpochDay(), cycle.startDate)
        assertEquals(start.plusDays(30).toEpochDay(), cycle.endDateExclusive)
        assertNull(cycle.closedAt)
    }

    @Test
    fun `is a no-op while the open cycle still contains today`() = runTest {
        val dao = FakeBudgetCycleDao()
        val first = rollover(dao).ensureCurrentCycle(budget(), start)
        val again = rollover(dao).ensureCurrentCycle(budget(), start.plusDays(5))

        assertEquals(first.id, again.id)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `closes an elapsed cycle and opens the next`() = runTest {
        val dao = FakeBudgetCycleDao()
        rollover(dao).ensureCurrentCycle(budget(), start)

        val current = rollover(dao).ensureCurrentCycle(budget(), start.plusDays(35))

        assertEquals(1, current.cycleIndex)
        assertNull(current.closedAt)
        val closed = dao.rows.value.single { it.cycleIndex == 0 }
        assertNotNull(closed.closedAt)
    }

    @Test
    fun `leaves no holes when the app goes unopened for several cycles`() = runTest {
        val dao = FakeBudgetCycleDao()
        rollover(dao).ensureCurrentCycle(budget(), start)

        // 95 days on, three whole 30-day cycles have finished.
        val current = rollover(dao).ensureCurrentCycle(budget(), start.plusDays(95))

        assertEquals(3, current.cycleIndex)
        assertEquals(listOf(0, 1, 2, 3), dao.rows.value.map { it.cycleIndex }.sorted())
        assertEquals(3, dao.rows.value.count { it.closedAt != null })
    }

    @Test
    fun `running twice on the same day does not duplicate or re-close`() = runTest {
        val dao = FakeBudgetCycleDao()
        rollover(dao).ensureCurrentCycle(budget(), start)
        rollover(dao).ensureCurrentCycle(budget(), start.plusDays(35))
        val closedAtFirst = dao.rows.value.single { it.cycleIndex == 0 }.closedAt

        rollover(dao).ensureCurrentCycle(budget(), start.plusDays(35))

        assertEquals(2, dao.rows.value.size)
        assertEquals(closedAtFirst, dao.rows.value.single { it.cycleIndex == 0 }.closedAt)
    }

    @Test
    fun `raising the budget mid-cycle applies to the open cycle immediately`() = runTest {
        val dao = FakeBudgetCycleDao()
        rollover(dao).ensureCurrentCycle(budget(), start)

        val raised = budget().copy(amountCents = 600_000L)
        val current = rollover(dao).ensureCurrentCycle(raised, start.plusDays(3))

        assertEquals(600_000L, current.budgetAmountCents)
        assertEquals(600_000L, dao.rows.value.single().budgetAmountCents)
    }

    @Test
    fun `shortening the cycle length moves the open cycle's end date`() = runTest {
        val dao = FakeBudgetCycleDao()
        rollover(dao).ensureCurrentCycle(budget(totalDays = 30), start)

        val shorter = budget(totalDays = 15)
        val current = rollover(dao).ensureCurrentCycle(shorter, start.plusDays(3))

        assertEquals(start.toEpochDay(), current.startDate)
        assertEquals(start.plusDays(15).toEpochDay(), current.endDateExclusive)
    }

    @Test
    fun `lengthening the cycle keeps every past cycle and leaves one open`() = runTest {
        val dao = FakeBudgetCycleDao()
        val weekly = budget(totalDays = 7)
        // Three weekly periods have run: 0 and 1 are closed history, 2 is open.
        rollover(dao).ensureCurrentCycle(weekly, start)
        rollover(dao).ensureCurrentCycle(weekly, start.plusDays(20))
        assertEquals(3, dao.rows.value.size)

        // The user switches to a 30-day cycle, which renumbers today back to index 0.
        val monthly = budget(totalDays = 30)
        val current = rollover(dao).ensureCurrentCycle(monthly, start.plusDays(20))

        // Neither of the finished weeks may be dropped: `insert` resolves the unique
        // (budgetId, cycleIndex) constraint with REPLACE, so writing over index 0 used
        // to delete the first week outright.
        assertEquals(3, dao.rows.value.size)
        assertNotNull(dao.rows.value.firstOrNull { it.cycleIndex == 1 })
        assertNotNull(dao.rows.value.firstOrNull { it.cycleIndex == 2 })

        // And exactly one cycle stays open, spanning today under the new length.
        assertEquals(listOf(0), dao.rows.value.filter { it.closedAt == null }.map { it.cycleIndex })
        assertEquals(0, current.cycleIndex)
        assertEquals(start.toEpochDay(), current.startDate)
        assertEquals(start.plusDays(30).toEpochDay(), current.endDateExclusive)
    }

    @Test
    fun `the reopened cycle is the one the dashboard reads back`() = runTest {
        val dao = FakeBudgetCycleDao()
        rollover(dao).ensureCurrentCycle(budget(totalDays = 7), start)
        rollover(dao).ensureCurrentCycle(budget(totalDays = 7), start.plusDays(20))
        rollover(dao).ensureCurrentCycle(budget(totalDays = 30), start.plusDays(20))

        val open = dao.getOpenCycle(1L)
        assertNotNull(open)
        assertEquals(0, open!!.cycleIndex)
        assertEquals(start.plusDays(30).toEpochDay(), open.endDateExclusive)
    }

    @Test
    fun `editing the budget never rewrites a closed cycle`() = runTest {
        val dao = FakeBudgetCycleDao()
        rollover(dao).ensureCurrentCycle(budget(), start)
        // Cycle 0 closes, cycle 1 opens.
        rollover(dao).ensureCurrentCycle(budget(), start.plusDays(35))

        val raised = budget().copy(amountCents = 600_000L)
        rollover(dao).ensureCurrentCycle(raised, start.plusDays(35))

        assertEquals(300_000L, dao.rows.value.single { it.cycleIndex == 0 }.budgetAmountCents)
        assertEquals(600_000L, dao.rows.value.single { it.cycleIndex == 1 }.budgetAmountCents)
    }

    @Test
    fun `the cycle snapshot keeps the budget amount it was created with`() = runTest {
        val dao = FakeBudgetCycleDao()
        rollover(dao).ensureCurrentCycle(budget(), start)

        // The user raises the budget partway through; history must not be rewritten.
        val raised = budget().copy(amountCents = 500_000L)
        rollover(dao).ensureCurrentCycle(raised, start.plusDays(35))

        assertEquals(300_000L, dao.rows.value.single { it.cycleIndex == 0 }.budgetAmountCents)
        assertEquals(500_000L, dao.rows.value.single { it.cycleIndex == 1 }.budgetAmountCents)
    }
}
