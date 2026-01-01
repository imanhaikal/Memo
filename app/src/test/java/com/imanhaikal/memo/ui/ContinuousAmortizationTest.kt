package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
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
    fun `Scenario 1 Intra-day Update - Spending TODAY reduces the dailyLimit immediately`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        // Setup: TotalBudget = 1000, Days = 10, StartDate = Today
        val now = System.currentTimeMillis()
        viewModel.setSystemTimeOverride(now)

        totalBudgetFlow.value = 1000.0
        cycleStartDateFlow.value = now
        totalDaysFlow.value = 10
        
        // Initial State Check (No spending)
        transactionsFlow.value = emptyList()
        testDispatcher.scheduler.advanceUntilIdle()
        
        var state = viewModel.uiState.value
        // CurrentPool = 1000. DaysRemaining = 10. DailyLimit = 100.
        assertEquals(100.0, state.dailyLimit, 0.01)

        // Spend 100 Today
        transactionsFlow.value = listOf(
            Transaction(amount = 100.0, note = "Spending Today", date = now)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        // Logic Check:
        // TotalSpent = 100
        // CurrentPool = 1000 - 100 = 900
        // DaysRemaining = 10
        // DailyLimit = 900 / 10 = 90.0
        // SpentToday = 100
        // AvailableToday = DailyLimit - SpentToday = 90 - 100 = -10.0

        assertEquals(90.0, state.dailyLimit, 0.01)
        assertEquals(-10.0, state.availableToday, 0.01)
    }

    @Test
    fun `Scenario 2 Past Spending - Spending YESTERDAY reduces the dailyLimit`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        val zoneId = ZoneId.systemDefault()
        val now = Instant.now()
        val todayDate = now.atZone(zoneId).toLocalDate()
        val yesterdayDate = todayDate.minusDays(1)
        
        val todayMillis = now.toEpochMilli()
        val yesterdayMillis = yesterdayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

        viewModel.setSystemTimeOverride(todayMillis)

        // Setup: TotalBudget = 1000, Days = 10, StartDate = Yesterday
        totalBudgetFlow.value = 1000.0
        cycleStartDateFlow.value = yesterdayMillis
        totalDaysFlow.value = 10

        // Spend 100 Yesterday
        transactionsFlow.value = listOf(
            Transaction(amount = 100.0, note = "Spending Yesterday", date = yesterdayMillis)
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
        assertEquals(100.0, state.dailyLimit, 0.01)
        assertEquals(100.0, state.availableToday, 0.01)
    }

    @Test
    fun `Scenario 3 Bankruptcy - If TotalSpent gt TotalBudget, dailyLimit should be 0`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        val now = System.currentTimeMillis()
        viewModel.setSystemTimeOverride(now)

        // Setup: TotalBudget = 100, Days = 10, StartDate = Today
        totalBudgetFlow.value = 100.0
        cycleStartDateFlow.value = now
        totalDaysFlow.value = 10

        // Spend 200 Today (Over Budget)
        transactionsFlow.value = listOf(
            Transaction(amount = 200.0, note = "Over Budget", date = now)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        // Logic Check:
        // TotalSpent = 200.
        // CurrentPool = 100 - 200 = -100.
        // DailyLimit = 0.0 (Clamped because CurrentPool < 0).
        // SpentToday = 200.
        // AvailableToday = 0 - 200 = -200.0.

        assertEquals(0.0, state.dailyLimit, 0.01)
        assertEquals(-200.0, state.availableToday, 0.01)
    }

    @Test
    fun `Scenario 4 Last Day - If daysRemaining = 1, dailyLimit = currentPool`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        val zoneId = ZoneId.systemDefault()
        val now = Instant.now()
        val todayDate = now.atZone(zoneId).toLocalDate()
        val nineDaysAgoDate = todayDate.minusDays(9)
        
        val todayMillis = now.toEpochMilli()
        val nineDaysAgoMillis = nineDaysAgoDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

        viewModel.setSystemTimeOverride(todayMillis)

        // Setup: TotalBudget = 100, Days = 10, StartDate = 9 days ago
        totalBudgetFlow.value = 100.0
        cycleStartDateFlow.value = nineDaysAgoMillis
        totalDaysFlow.value = 10
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
        assertEquals(100.0, state.dailyLimit, 0.01)
        assertEquals(100.0, state.availableToday, 0.01)
    }
}