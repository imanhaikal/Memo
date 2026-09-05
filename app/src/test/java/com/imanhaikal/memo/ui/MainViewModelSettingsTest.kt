package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.testing.MemoTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
    fun `raising the budget takes effect on the current cycle straight away`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(10_000L, viewModel.uiState.value.dailyLimit)

        // The Settings screen tells the user changes apply to the current cycle right
        // away, so the open cycle has to pick up the new amount rather than waiting.
        viewModel.updateBudget(600_000L, 30, "MYR")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(600_000L, viewModel.uiState.value.totalBudget)
        assertEquals(20_000L, viewModel.uiState.value.dailyLimit)
    }

    @Test
    fun `shortening the cycle length shortens the days remaining`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(30, viewModel.uiState.value.daysRemaining)

        viewModel.updateBudget(300_000L, 15, "MYR")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(15, viewModel.uiState.value.daysRemaining)
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
