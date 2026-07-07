package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.BudgetConfig
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DaysRemainingTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetPreferences: BudgetPreferences
    private val testDispatcher = StandardTestDispatcher()

    // Mock data flows
    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    private val configFlow = MutableStateFlow(BudgetConfig(100_000L, 0L, 30, "USD"))
    private val zoneId = ZoneId.systemDefault()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionDao = mockk()
        budgetPreferences = mockk()

        every { transactionDao.getAllTransactions() } returns transactionsFlow
        every { budgetPreferences.budgetConfig } returns configFlow

        viewModel = MainViewModel(transactionDao, budgetPreferences, Clock.systemDefaultZone())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setDate(date: LocalDate) {
        viewModel = MainViewModel(
            transactionDao = transactionDao,
            budgetPreferences = budgetPreferences,
            clock = Clock.fixed(date.atStartOfDay(zoneId).toInstant(), zoneId)
        )
    }

    private fun setStartDate(date: LocalDate) {
        val time = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        configFlow.value = configFlow.value.copy(cycleStartDateMillis = time)
    }

    @Test
    fun `Test Start of Cycle - Days remaining should equal total days`() = runTest {
        val today = LocalDate.of(2023, 1, 1)
        setDate(today)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        setStartDate(today)
        configFlow.value = configFlow.value.copy(totalDays = 30)

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(30, viewModel.uiState.value.daysRemaining)
    }

    @Test
    fun `Test Middle of Cycle - Days remaining should be correct difference`() = runTest {
        val start = LocalDate.of(2023, 1, 1)
        val today = LocalDate.of(2023, 1, 16) // 15 days passed
        setDate(today)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        setStartDate(start)
        configFlow.value = configFlow.value.copy(totalDays = 30)

        // passed = 15. remaining = 30 - 15 = 15.
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.daysRemaining)
    }

    @Test
    fun `Test End of Cycle - Last day should have 1 day remaining`() = runTest {
        val start = LocalDate.of(2023, 1, 1)
        val today = LocalDate.of(2023, 1, 30) // 29 days passed (if 1st is day 1, 30th is day 30)
        // ChronoUnit.DAYS.between(1st, 30th) = 29.
        // Remaining = 30 - 29 = 1.
        
        setDate(today)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        setStartDate(start)
        configFlow.value = configFlow.value.copy(totalDays = 30)

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.daysRemaining)
    }

    @Test
    fun `Test Past Cycle - Should roll over to a fresh cycle instead of freezing at 1`() = runTest {
        val start = LocalDate.of(2023, 1, 1)
        val today = LocalDate.of(2023, 2, 5) // 35 days after start, one 30-day cycle has fully elapsed

        setDate(today)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        setStartDate(start)
        configFlow.value = configFlow.value.copy(totalDays = 30)

        testDispatcher.scheduler.advanceUntilIdle()
        // Cycle rolls forward to Jan 31 (start + 30 days), so 5 days have passed in the new cycle.
        // Remaining = 30 - 5 = 25.
        assertEquals(25, viewModel.uiState.value.daysRemaining)
    }

    @Test
    fun `Test Leap Year - Should handle Feb 29 correctly`() = runTest {
        // Leap year 2024
        val start = LocalDate.of(2024, 2, 28)
        val today = LocalDate.of(2024, 3, 1) 
        
        // 2024 is leap. Feb 29 exists.
        // 28 Feb -> 29 Feb -> 1 Mar
        // Days passed should be 2.
        
        setDate(today)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        setStartDate(start)
        configFlow.value = configFlow.value.copy(totalDays = 30)

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
        // Non-Leap year 2023
        val start = LocalDate.of(2023, 2, 28)
        val today = LocalDate.of(2023, 3, 1) 
        
        // 28 Feb -> 1 Mar
        // Days passed should be 1.
        
        setDate(today)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        setStartDate(start)
        configFlow.value = configFlow.value.copy(totalDays = 30)

        testDispatcher.scheduler.advanceUntilIdle()
        
        // between(28, 1) = 1 day.
        // remaining = 30 - 1 = 29.

        assertEquals(29, viewModel.uiState.value.daysRemaining)
    }
}
