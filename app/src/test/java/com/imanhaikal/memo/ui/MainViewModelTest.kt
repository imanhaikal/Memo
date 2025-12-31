package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
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
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetPreferences: BudgetPreferences
    private val testDispatcher = StandardTestDispatcher()

    // Mock data flows
    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    private val totalBudgetFlow = MutableStateFlow(0.0)
    private val cycleStartDateFlow = MutableStateFlow(0L)
    private val totalDaysFlow = MutableStateFlow(30)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionDao = mockk()
        budgetPreferences = mockk()

        every { transactionDao.getAllTransactions() } returns transactionsFlow
        every { budgetPreferences.totalBudget } returns totalBudgetFlow
        every { budgetPreferences.cycleStartDate } returns cycleStartDateFlow
        every { budgetPreferences.totalDays } returns totalDaysFlow

        viewModel = MainViewModel(transactionDao, budgetPreferences)
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
        val now = System.currentTimeMillis()
        viewModel.setSystemTimeOverride(now)
        
        totalBudgetFlow.value = 3000.0
        cycleStartDateFlow.value = now
        totalDaysFlow.value = 30
        transactionsFlow.value = emptyList()
        
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Ensure setup is complete
        assertEquals("Total Budget", 3000.0, state.totalBudget, 0.01)
        // Days remaining starts at totalDays if daysPassed is 0.
        // logic: daysPassed = between(startDate, today)
        // startDate = now, today = now -> daysPassed = 0
        // daysRemaining = max(1, 30 - 0) = 30
        assertEquals(30, state.daysRemaining)
        assertEquals(100.0, state.dailyLimit, 0.01) // 3000 / 30 = 100
        assertEquals(100.0, state.availableToday, 0.01) // 100 - 0 = 100
        assertEquals(BudgetStatus.ON_TRACK, state.status)
    }

    @Test
    fun `Test Case 2 Spending Impact`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        // Setup: Budget 3000, 30 days
        val now = System.currentTimeMillis()
        viewModel.setSystemTimeOverride(now)

        totalBudgetFlow.value = 3000.0
        cycleStartDateFlow.value = now
        totalDaysFlow.value = 30
        
        // Add a transaction of 50 today
        transactionsFlow.value = listOf(
            Transaction(id = 1, amount = 50.0, note = "Food", date = now)
        )

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Calculation:
        // Pool Start Of Day = 3000 - (50 - 50) = 3000
        // Daily Limit = 3000 / 30 = 100
        // Available Today = 100 - 50 = 50
        
        assertEquals(50.0, state.spentToday, 0.01)
        assertEquals(100.0, state.dailyLimit, 0.01)
        assertEquals(50.0, state.availableToday, 0.01)
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
        val zoneId = ZoneId.systemDefault()
        val now = Instant.now()
        val todayDate = now.atZone(zoneId).toLocalDate()
        val yesterdayDate = todayDate.minusDays(1)
        
        // We need epoch millis that fall into 'today' and 'yesterday' in system zone
        val todayMillis = now.toEpochMilli()
        val startOfYesterdayMillis = yesterdayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

        viewModel.setSystemTimeOverride(todayMillis)

        totalBudgetFlow.value = 3000.0
        cycleStartDateFlow.value = startOfYesterdayMillis // Started yesterday
        totalDaysFlow.value = 30
        
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
        assertEquals(103.45, state.dailyLimit, 0.1)
        assertEquals(103.45, state.availableToday, 0.1)
    }

    @Test
    fun `Test Case 4 Status Logic`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        // Setup: Budget 3000, 30 days
        val now = System.currentTimeMillis()
        viewModel.setSystemTimeOverride(now)
        
        totalBudgetFlow.value = 3000.0
        cycleStartDateFlow.value = now
        totalDaysFlow.value = 30
        
        // 1. Over Limit
        // Daily limit is 100. If we spend 101, available is -1.
        transactionsFlow.value = listOf(Transaction(amount = 101.0, note = "Big Spend", date = now))
        testDispatcher.scheduler.advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals("Available: ${state.availableToday}", BudgetStatus.OVER_LIMIT, state.status)

        // 2. Careful
        // Daily limit 100. careful if available < 20% (20). So spend 81 -> available 19.
        transactionsFlow.value = listOf(Transaction(amount = 81.0, note = "Careful Spend", date = now))
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals("Available: ${state.availableToday}", BudgetStatus.CAREFUL, state.status)

        // 3. On Track
        // Spend 50 -> available 50 (> 20)
        transactionsFlow.value = listOf(Transaction(amount = 50.0, note = "Normal Spend", date = now))
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals("Available: ${state.availableToday}", BudgetStatus.ON_TRACK, state.status)
    }
}