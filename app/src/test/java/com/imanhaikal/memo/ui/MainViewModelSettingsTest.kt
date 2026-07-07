package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.BudgetConfig
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.receipt.FakeReceiptScanner
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
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSettingsTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetPreferences: BudgetPreferences
    private val testDispatcher = StandardTestDispatcher()

    // Mock data flows
    private val transactionsFlow = MutableStateFlow(emptyList<com.imanhaikal.memo.data.Transaction>())
    private val configFlow = MutableStateFlow(BudgetConfig(0L, 0L, 30, "USD"))

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionDao = mockk(relaxed = true) // Relaxed to allow calls without specific stubbing
        budgetPreferences = mockk(relaxed = true)

        every { transactionDao.getAllTransactions() } returns transactionsFlow
        every { budgetPreferences.budgetConfig } returns configFlow

        viewModel = MainViewModel(transactionDao, budgetPreferences, Clock.systemDefaultZone(), FakeReceiptScanner())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateBudget updates budget and days`() = runTest {
        // Arrange
        val newBudget = 500_000L
        val newDays = 15

        // Act
        viewModel.updateBudget(newBudget, newDays, "USD")
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { 
            budgetPreferences.updateBudgetConfig(newBudget, newDays, "USD") 
        }
    }

    @Test
    fun `updateBudget does NOT reset start date or clear transactions`() = runTest {
        // Arrange
        val newBudget = 500_000L
        val newDays = 15

        // Act
        viewModel.updateBudget(newBudget, newDays, "USD")
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        // Verify updateBudgetConfig IS called
        coVerify(exactly = 1) { 
            budgetPreferences.updateBudgetConfig(newBudget, newDays, "USD") 
        }

        // Verify saveBudgetSettings (which sets start date) is NOT called
        coVerify(exactly = 0) {
            budgetPreferences.saveBudgetSettings(any(), any(), any(), any())
        }

        // Verify deleteAllTransactions is NOT called
        coVerify(exactly = 0) {
            transactionDao.deleteAllTransactions()
        }
    }
}
