package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.BudgetConfig
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.receipt.FakeReceiptScanner
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousAmortizationTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetPreferences: BudgetPreferences
    private val testDispatcher = StandardTestDispatcher()

    // Mock data flows
    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    private val configFlow = MutableStateFlow(BudgetConfig(0L, 0L, 30, "USD"))
    private val zoneId = ZoneId.systemDefault()
    private val now = Instant.parse("2024-01-15T12:00:00Z")
    private lateinit var clock: Clock

    private fun setTotalBudget(cents: Long) {
        configFlow.value = configFlow.value.copy(totalBudgetCents = cents)
    }

    private fun setCycleStartDate(millis: Long) {
        configFlow.value = configFlow.value.copy(cycleStartDateMillis = millis)
    }

    private fun setTotalDays(days: Int) {
        configFlow.value = configFlow.value.copy(totalDays = days)
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionDao = mockk()
        budgetPreferences = mockk()

        every { transactionDao.getAllTransactions() } returns transactionsFlow
        every { budgetPreferences.budgetConfig } returns configFlow

        clock = Clock.fixed(now, zoneId)
        viewModel = MainViewModel(transactionDao, budgetPreferences, clock, FakeReceiptScanner(), defaultDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Scenario 1 Strict Daily Pool - Spending TODAY does not reduce the dailyLimit`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        // Setup: TotalBudget = 1000, Days = 10, StartDate = Today
        val nowMillis = now.toEpochMilli()

        setTotalBudget(100_000L)
        setCycleStartDate(nowMillis)
        setTotalDays(10)

        // Initial State Check (No spending)
        transactionsFlow.value = emptyList()
        testDispatcher.scheduler.advanceUntilIdle()
        
        var state = viewModel.uiState.value
        // Pool = 1000. DaysRemaining = 10. DailyLimit = 100.
        assertEquals(10_000L, state.dailyLimit)

        // Spend 100 Today
        transactionsFlow.value = listOf(
            Transaction(amount = 10_000L, note = "Spending Today", date = nowMillis)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        // Logic Check:
        // SpentBeforeToday = 0
        // Pool = 1000
        // DailyLimit = 1000 / 10 = 100.0
        // SpentToday = 100
        // AvailableToday = DailyLimit - SpentToday = 100 - 100 = 0.0

        assertEquals(10_000L, state.dailyLimit)
        assertEquals(0L, state.availableToday)
    }

    @Test
    fun `Scenario 2 Past Spending - Spending YESTERDAY reduces the dailyLimit`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        val todayDate = now.atZone(zoneId).toLocalDate()
        val yesterdayDate = todayDate.minusDays(1)
        
        val todayMillis = now.toEpochMilli()
        val yesterdayMillis = yesterdayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

        // Setup: TotalBudget = 1000, Days = 10, StartDate = Yesterday
        setTotalBudget(100_000L)
        setCycleStartDate(yesterdayMillis)
        setTotalDays(10)

        // Spend 100 Yesterday
        transactionsFlow.value = listOf(
            Transaction(amount = 10_000L, note = "Spending Yesterday", date = yesterdayMillis)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Logic Check:
        // DaysPassed = 1. DaysRemaining = 9.
        // TotalSpent = 100.
        // CurrentPool = 1000 - 100 = 900.
        // DailyLimit = 900 / 9 = 100.0.
        // SpentToday = 0.
        // AvailableToday = 100 - 0 = 100.0.

        assertEquals(9, state.daysRemaining)
        assertEquals(10_000L, state.dailyLimit)
        assertEquals(10_000L, state.availableToday)
    }

    @Test
    fun `Scenario 3 Bankruptcy - If TotalSpent gt TotalBudget, dailyLimit should be 0`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        val nowMillis = now.toEpochMilli()

        // Setup: TotalBudget = 100, Days = 10, StartDate = Today
        setTotalBudget(10_000L)
        setCycleStartDate(nowMillis)
        setTotalDays(10)

        // Spend 200 Today (Over Budget)
        transactionsFlow.value = listOf(
            Transaction(amount = 20_000L, note = "Over Budget", date = nowMillis)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Logic Check:
        // SpentBeforeToday = 0.
        // Pool = 100.
        // BaselineLimit = 10.0.
        // SpentToday = 200 (over the allowance) -> overspend amortized over remaining days:
        // (100 - 200) / 9 is negative -> clamped to 0.
        // AvailableToday = 10 - 200 = -190.0.

        assertEquals(0L, state.dailyLimit)
        assertEquals(-19_000L, state.availableToday)
    }

    @Test
    fun `Scenario 5 Over Limit - Overspending TODAY re-amortizes the dailyLimit across remaining days`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        val nowMillis = now.toEpochMilli()

        // Setup: TotalBudget = 1000, Days = 10, StartDate = Today
        setTotalBudget(100_000L)
        setCycleStartDate(nowMillis)
        setTotalDays(10)

        // Spend 190 Today (baseline limit is 100)
        transactionsFlow.value = listOf(
            Transaction(amount = 19_000L, note = "Over Limit Today", date = nowMillis)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Logic Check:
        // Pool = 1000. BaselineLimit = 1000 / 10 = 100.
        // SpentToday = 190 > 100 -> DailyLimit = (1000 - 190) / 9 = 90.
        // AvailableToday = 100 - 190 = -90.

        assertEquals(9_000L, state.dailyLimit)
        assertEquals(-9_000L, state.availableToday)
        assertEquals(BudgetStatus.OVER_LIMIT, state.status)
    }

    @Test
    fun `Scenario 4 Last Day - If daysRemaining = 1, dailyLimit = currentPool`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        val todayDate = now.atZone(zoneId).toLocalDate()
        val nineDaysAgoDate = todayDate.minusDays(9)
        
        val todayMillis = now.toEpochMilli()
        val nineDaysAgoMillis = nineDaysAgoDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

        // Setup: TotalBudget = 100, Days = 10, StartDate = 9 days ago
        setTotalBudget(10_000L)
        setCycleStartDate(nineDaysAgoMillis)
        setTotalDays(10)
        transactionsFlow.value = emptyList()

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Logic Check:
        // DaysPassed = 9.
        // DaysRemaining = max(1, 10 - 9) = 1.
        // TotalSpent = 0.
        // CurrentPool = 100.
        // DailyLimit = 100 / 1 = 100.0.
        // AvailableToday = 100.0.

        assertEquals(1, state.daysRemaining)
        assertEquals(10_000L, state.dailyLimit)
        assertEquals(10_000L, state.availableToday)
    }
}
