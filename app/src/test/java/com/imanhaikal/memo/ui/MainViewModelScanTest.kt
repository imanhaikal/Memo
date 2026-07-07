package com.imanhaikal.memo.ui

import android.net.Uri
import app.cash.turbine.test
import com.imanhaikal.memo.data.BudgetConfig
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.receipt.FakeReceiptScanner
import com.imanhaikal.memo.data.receipt.ScanFailureReason
import com.imanhaikal.memo.data.receipt.ScanOutcome
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelScanTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetPreferences: BudgetPreferences
    private lateinit var scanner: FakeReceiptScanner
    private val testDispatcher = StandardTestDispatcher()

    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    private val configFlow = MutableStateFlow(BudgetConfig(100_000L, 0L, 30, "MYR"))

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionDao = mockk()
        budgetPreferences = mockk()
        scanner = FakeReceiptScanner()

        every { transactionDao.getAllTransactions() } returns transactionsFlow
        every { budgetPreferences.budgetConfig } returns configFlow

        viewModel = MainViewModel(transactionDao, budgetPreferences, Clock.systemDefaultZone(), scanner)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful scan emits processing then success`() = runTest(testDispatcher.scheduler) {
        scanner.outcome = ScanOutcome.Success(1250L, "Tesco")

        viewModel.scanState.test {
            assertEquals(ScanState.Idle, awaitItem())
            viewModel.scanReceipt(mockk<Uri>())
            assertEquals(ScanState.Processing, awaitItem())
            assertEquals(ScanState.Success(1250L, "Tesco"), awaitItem())
        }
    }

    @Test
    fun `failed scan emits processing then error`() = runTest(testDispatcher.scheduler) {
        scanner.outcome = ScanOutcome.Failure(ScanFailureReason.NETWORK)

        viewModel.scanState.test {
            assertEquals(ScanState.Idle, awaitItem())
            viewModel.scanReceipt(mockk<Uri>())
            assertEquals(ScanState.Processing, awaitItem())
            assertEquals(ScanState.Error(ScanFailureReason.NETWORK), awaitItem())
        }
    }

    @Test
    fun `clearScanState resets to idle`() = runTest(testDispatcher.scheduler) {
        scanner.outcome = ScanOutcome.Success(500L, "KFC")

        viewModel.scanState.test {
            assertEquals(ScanState.Idle, awaitItem())
            viewModel.scanReceipt(mockk<Uri>())
            assertEquals(ScanState.Processing, awaitItem())
            assertEquals(ScanState.Success(500L, "KFC"), awaitItem())
            viewModel.clearScanState()
            assertEquals(ScanState.Idle, awaitItem())
        }
    }

    @Test
    fun `scan availability mirrors scanner`() {
        assertTrue(viewModel.isScanAvailable)

        val unavailable = MainViewModel(
            transactionDao,
            budgetPreferences,
            Clock.systemDefaultZone(),
            FakeReceiptScanner(isAvailable = false)
        )
        assertFalse(unavailable.isScanAvailable)
    }
}
