package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.testing.MemoTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

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

    /** Starts the upstream combine, which only runs while something is collecting. */
    private fun TestScope.collectState() {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
    }

    @Test
    fun `initial calculation spreads the budget over the whole cycle`() = runTest {
        collectState()
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals("Total Budget", 300_000L, state.totalBudget)
        assertEquals(30, state.daysRemaining)
        assertEquals(10_000L, state.dailyLimit)
        assertEquals(10_000L, state.availableToday)
        assertEquals(BudgetStatus.ON_TRACK, state.status)
    }

    @Test
    fun `spending today reduces what is available today`() = runTest {
        collectState()
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        harness.transactionDao.insertTransaction(
            Transaction(amount = 5_000L, note = "Food", date = now.toEpochMilli(), budgetId = 1L)
        )

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(5_000L, state.spentToday)
        assertEquals(10_000L, state.dailyLimit)
        assertEquals(5_000L, state.availableToday)
        assertEquals(BudgetStatus.ON_TRACK, state.status)
    }

    @Test
    fun `a cycle that started yesterday has one fewer day to spread over`() = runTest {
        collectState()
        harness.seedBudget(
            amountCents = 300_000L,
            totalDays = 30,
            startDate = today.minusDays(1)
        )

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(29, state.daysRemaining)
        assertEquals(10_344L, state.dailyLimit)
        assertEquals(10_344L, state.availableToday)
    }

    @Test
    fun `status moves through on track, careful and over limit`() = runTest {
        collectState()
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        val nowMillis = now.toEpochMilli()

        // Daily limit is 10_000. Spending 10_100 leaves a negative balance.
        harness.transactionDao.insertTransaction(
            Transaction(id = 1, amount = 10_100L, note = "Big Spend", date = nowMillis, budgetId = 1L)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals("Available: ${state.availableToday}", BudgetStatus.OVER_LIMIT, state.status)

        // Careful once under 20% of the limit remains: spend 8_100, leaving 1_900.
        harness.transactionDao.insertTransaction(
            Transaction(id = 1, amount = 8_100L, note = "Careful Spend", date = nowMillis, budgetId = 1L)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals("Available: ${state.availableToday}", BudgetStatus.CAREFUL, state.status)

        harness.transactionDao.insertTransaction(
            Transaction(id = 1, amount = 5_000L, note = "Normal Spend", date = nowMillis, budgetId = 1L)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals("Available: ${state.availableToday}", BudgetStatus.ON_TRACK, state.status)
    }

    @Test
    fun `no budget means not set up`() = runTest {
        collectState()

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(false, state.isLoading)
        assertEquals(false, state.isSetup)
    }
}
