package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.testing.MemoTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi as ExperimentalApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSettingsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val now: Instant = Instant.parse("2024-01-15T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, zoneId)
    private val today: LocalDate = now.atZone(zoneId).toLocalDate()

    private lateinit var harness: MemoTestHarness
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        harness = MemoTestHarness(clock, today)
        viewModel = harness.viewModel(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateBudget changes the amount and cycle length of the active budget`() = runTest {
        val seeded = harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)

        viewModel.updateBudget(500_000L, 15, "USD")
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = harness.budgetDao.getById(seeded.id)
        assertNotNull(updated)
        assertEquals(500_000L, updated!!.amountCents)
        assertEquals(15, updated.totalDays)
        assertEquals("USD", updated.currencyCode)
    }

    @Test
    fun `updateBudget keeps the cycle start date and the existing transactions`() = runTest {
        val seeded = harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        harness.transactionDao.insertTransaction(
            Transaction(amount = 1_000L, note = "Coffee", date = now.toEpochMilli(), budgetId = seeded.id)
        )

        viewModel.updateBudget(500_000L, 15, "USD")
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = harness.budgetDao.getById(seeded.id)!!
        // Editing the budget must not silently restart the cycle underneath the user.
        assertEquals(seeded.firstCycleStartDate, updated.firstCycleStartDate)
        assertEquals(1, harness.transactionDao.rows.value.size)
    }

    @Test
    fun `clearing a budget's data leaves other budgets untouched`() = runTest {
        val first = harness.seedBudget(amountCents = 300_000L, startDate = today, name = "Monthly")
        val second = harness.seedBudget(amountCents = 50_000L, startDate = today, name = "Travel")
        harness.transactionDao.insertTransaction(
            Transaction(amount = 1_000L, note = "Coffee", date = now.toEpochMilli(), budgetId = first.id)
        )
        harness.transactionDao.insertTransaction(
            Transaction(amount = 2_000L, note = "Taxi", date = now.toEpochMilli(), budgetId = second.id)
        )

        // Travel is active, having been seeded last.
        viewModel.clearActiveBudgetData()
        testDispatcher.scheduler.advanceUntilIdle()

        val remaining = harness.transactionDao.rows.value
        assertEquals(1, remaining.size)
        assertEquals(first.id, remaining.single().budgetId)
        // The budget itself survives; only its data was cleared.
        assertNotNull(harness.budgetDao.getById(second.id))
    }

    @Test
    fun `deleting a budget removes its transactions and falls back to another budget`() = runTest {
        val first = harness.seedBudget(amountCents = 300_000L, startDate = today, name = "Monthly")
        val second = harness.seedBudget(amountCents = 50_000L, startDate = today, name = "Travel")
        harness.transactionDao.insertTransaction(
            Transaction(amount = 2_000L, note = "Taxi", date = now.toEpochMilli(), budgetId = second.id)
        )

        viewModel.resetBudget()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, harness.budgetDao.getById(second.id))
        assertEquals(emptyList<Transaction>(), harness.transactionDao.rows.value)
        assertEquals(first.id, harness.activeBudgetStore.state.value)
    }
}
