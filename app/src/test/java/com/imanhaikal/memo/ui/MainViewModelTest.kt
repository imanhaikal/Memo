package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.BudgetConfig
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.ThemeMode
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.receipt.FakeReceiptScanner
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class MainViewModelTest {

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
        every { budgetPreferences.themeMode } returns kotlinx.coroutines.flow.flowOf(ThemeMode.SYSTEM)
        every { budgetPreferences.hapticsEnabled } returns kotlinx.coroutines.flow.flowOf(true)

        clock = Clock.fixed(now, zoneId)
        viewModel = MainViewModel(transactionDao, budgetPreferences, clock, FakeReceiptScanner(), defaultDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Test Case 1 Initial Calculation`() = runTest {
        // Start collecting the flow in the background to trigger the upstream combine
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        // Setup: Budget 3000, 30 days, Start Date is NOW
        val nowMillis = now.toEpochMilli()

        setTotalBudget(300_000L)
        setCycleStartDate(nowMillis)
        setTotalDays(30)
        transactionsFlow.value = emptyList()
        
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Ensure setup is complete
        assertEquals("Total Budget", 300_000L, state.totalBudget)
        // Days remaining starts at totalDays if daysPassed is 0.
        // logic: daysPassed = between(startDate, today)
        // startDate = now, today = now -> daysPassed = 0
        // daysRemaining = max(1, 30 - 0) = 30
        assertEquals(30, state.daysRemaining)
        assertEquals(10_000L, state.dailyLimit) // 3000 / 30 = 100
        assertEquals(10_000L, state.availableToday) // 100 - 0 = 100
        assertEquals(BudgetStatus.ON_TRACK, state.status)
    }

    @Test
    fun `Test Case 2 Spending Impact`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        // Setup: Budget 3000, 30 days
        val nowMillis = now.toEpochMilli()

        setTotalBudget(300_000L)
        setCycleStartDate(nowMillis)
        setTotalDays(30)

        // Add a transaction of 50 today
        transactionsFlow.value = listOf(
            Transaction(id = 1, amount = 5_000L, note = "Food", date = nowMillis)
        )

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Calculation (Strict Daily Pool):
        // Pool = 3000 - 0 spent before today = 3000
        // Days Remaining = 30
        // Daily Limit = 3000 / 30 = 100
        // Spent Today = 50
        // Available Today = 100 - 50 = 50
        
        assertEquals(5_000L, state.spentToday)
        assertEquals(10_000L, state.dailyLimit)
        assertEquals(5_000L, state.availableToday)
        assertEquals(BudgetStatus.ON_TRACK, state.status)
    }

    @Test
    fun `Test Case 3 Next Day Recalculation`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        // Setup: Budget 3000, 30 days
        // Start date was yesterday
        // We need to ensure we are using system default zone for LocalDate conversion inside ViewModel
        val todayDate = now.atZone(zoneId).toLocalDate()
        val yesterdayDate = todayDate.minusDays(1)
        
        // We need epoch millis that fall into 'today' and 'yesterday' in system zone
        val todayMillis = now.toEpochMilli()
        val startOfYesterdayMillis = yesterdayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

        setTotalBudget(300_000L)
        setCycleStartDate(startOfYesterdayMillis) // Started yesterday
        setTotalDays(30)

        // No spending
        transactionsFlow.value = emptyList()

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Days Passed = 1
        // Days Remaining = 29
        // Pool Start Of Day = 3000 - (0 - 0) = 3000
        // Daily Limit = 3000 / 29 = 103.448
        // Available Today = 103.448 - 0 = 103.448

        assertEquals(29, state.daysRemaining)
        assertEquals(10_344L, state.dailyLimit)
        assertEquals(10_344L, state.availableToday)
    }

    @Test
    fun `Test Case 4 Status Logic`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        // Setup: Budget 3000, 30 days
        val nowMillis = now.toEpochMilli()
        
        setTotalBudget(300_000L)
        setCycleStartDate(nowMillis)
        setTotalDays(30)

        // 1. Over Limit
        // Daily limit is 100. If we spend 101, available is -1.
        transactionsFlow.value = listOf(Transaction(amount = 10_100L, note = "Big Spend", date = nowMillis))
        testDispatcher.scheduler.advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals("Available: ${state.availableToday}", BudgetStatus.OVER_LIMIT, state.status)

        // 2. Careful
        // Daily limit 100. careful if available < 20% (20). So spend 81 -> available 19.
        transactionsFlow.value = listOf(Transaction(amount = 8_100L, note = "Careful Spend", date = nowMillis))
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals("Available: ${state.availableToday}", BudgetStatus.CAREFUL, state.status)

        // 3. On Track
        // Spend 50 -> available 50 (> 20)
        transactionsFlow.value = listOf(Transaction(amount = 5_000L, note = "Normal Spend", date = nowMillis))
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals("Available: ${state.availableToday}", BudgetStatus.ON_TRACK, state.status)
    }
}
