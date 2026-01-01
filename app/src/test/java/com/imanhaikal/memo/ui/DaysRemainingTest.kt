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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DaysRemainingTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetPreferences: BudgetPreferences
    private val testDispatcher = StandardTestDispatcher()

    // Mock data flows
    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    private val totalBudgetFlow = MutableStateFlow(1000.0)
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

    private fun setDate(date: LocalDate) {
        val zoneId = ZoneId.systemDefault()
        val time = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        viewModel.setSystemTimeOverride(time)
    }

    private fun setStartDate(date: LocalDate) {
        val zoneId = ZoneId.systemDefault()
        val time = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        cycleStartDateFlow.value = time
    }

    @Test
    fun `Test Start of Cycle - Days remaining should equal total days`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val today = LocalDate.of(2023, 1, 1)
        setDate(today)
        setStartDate(today)
        totalDaysFlow.value = 30

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(30, viewModel.uiState.value.daysRemaining)
    }

    @Test
    fun `Test Middle of Cycle - Days remaining should be correct difference`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val start = LocalDate.of(2023, 1, 1)
        val today = LocalDate.of(2023, 1, 16) // 15 days passed
        setDate(today)
        setStartDate(start)
        totalDaysFlow.value = 30

        // passed = 15. remaining = 30 - 15 = 15.
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.daysRemaining)
    }

    @Test
    fun `Test End of Cycle - Last day should have 1 day remaining`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val start = LocalDate.of(2023, 1, 1)
        val today = LocalDate.of(2023, 1, 30) // 29 days passed (if 1st is day 1, 30th is day 30)
        // ChronoUnit.DAYS.between(1st, 30th) = 29.
        // Remaining = 30 - 29 = 1.
        
        setDate(today)
        setStartDate(start)
        totalDaysFlow.value = 30

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.daysRemaining)
    }

    @Test
    fun `Test Past Cycle - Should remain at 1 per current logic`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val start = LocalDate.of(2023, 1, 1)
        val today = LocalDate.of(2023, 2, 5) // Way past 30 days
        
        setDate(today)
        setStartDate(start)
        totalDaysFlow.value = 30

        testDispatcher.scheduler.advanceUntilIdle()
        // Logic is max(1, total - passed). Passed > 30. Result 1.
        assertEquals(1, viewModel.uiState.value.daysRemaining)
    }

    @Test
    fun `Test Leap Year - Should handle Feb 29 correctly`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        // Leap year 2024
        val start = LocalDate.of(2024, 2, 28)
        val today = LocalDate.of(2024, 3, 1) 
        
        // 2024 is leap. Feb 29 exists.
        // 28 Feb -> 29 Feb -> 1 Mar
        // Days passed should be 2.
        
        setDate(today)
        setStartDate(start)
        totalDaysFlow.value = 30

        testDispatcher.scheduler.advanceUntilIdle()
        
        // Expected passed = 2 (28->29 (1), 29->1 (1) -> total 2? No, between counts days.
        // 28 to 1st March.
        // 28 (start)
        // 29
        // 1 (current)
        // between(28, 1) = 2 days.
        // remaining = 30 - 2 = 28.

        assertEquals(28, viewModel.uiState.value.daysRemaining)
    }
    
    @Test
    fun `Test Non-Leap Year - Should handle Feb 28 to Mar 1 correctly`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        // Non-Leap year 2023
        val start = LocalDate.of(2023, 2, 28)
        val today = LocalDate.of(2023, 3, 1) 
        
        // 28 Feb -> 1 Mar
        // Days passed should be 1.
        
        setDate(today)
        setStartDate(start)
        totalDaysFlow.value = 30

        testDispatcher.scheduler.advanceUntilIdle()
        
        // between(28, 1) = 1 day.
        // remaining = 30 - 1 = 29.

        assertEquals(29, viewModel.uiState.value.daysRemaining)
    }
}