package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.TransactionDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSettingsTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetPreferences: BudgetPreferences
    private val testDispatcher = StandardTestDispatcher()

    // Mock data flows
    private val transactionsFlow = MutableStateFlow(emptyList<com.imanhaikal.memo.data.Transaction>())
    private val totalBudgetFlow = MutableStateFlow(0.0)
    private val cycleStartDateFlow = MutableStateFlow(0L)
    private val totalDaysFlow = MutableStateFlow(30)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionDao = mockk(relaxed = true) // Relaxed to allow calls without specific stubbing
        budgetPreferences = mockk(relaxed = true)

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
    fun `updateBudget updates budget and days`() = runTest {
        // Arrange
        val newBudget = 5000.0
        val newDays = 15

        // Act
        viewModel.updateBudget(newBudget, newDays)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { 
            budgetPreferences.updateBudgetConfig(newBudget, newDays) 
        }
    }

    @Test
    fun `updateBudget does NOT reset start date or clear transactions`() = runTest {
        // Arrange
        val newBudget = 5000.0
        val newDays = 15

        // Act
        viewModel.updateBudget(newBudget, newDays)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        // Verify updateBudgetConfig IS called
        coVerify(exactly = 1) { 
            budgetPreferences.updateBudgetConfig(newBudget, newDays) 
        }

        // Verify saveBudgetSettings (which sets start date) is NOT called
        coVerify(exactly = 0) {
            budgetPreferences.saveBudgetSettings(any(), any(), any())
        }

        // Verify deleteAllTransactions is NOT called
        coVerify(exactly = 0) {
            transactionDao.deleteAllTransactions()
        }
    }
}