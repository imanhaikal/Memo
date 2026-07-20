package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.BudgetConfig
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.receipt.FakeReceiptScanner
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTransactionTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetPreferences: BudgetPreferences
    private val testDispatcher = StandardTestDispatcher()

    private val fixedNowMillis = 1_752_300_000_000L
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(fixedNowMillis), ZoneId.of("Asia/Kuala_Lumpur"))

    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    private val configFlow = MutableStateFlow(BudgetConfig(100_000L, 0L, 30, "MYR"))

    private val insertedTransaction = slot<Transaction>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionDao = mockk()
        budgetPreferences = mockk()

        every { transactionDao.getAllTransactions() } returns transactionsFlow
        every { budgetPreferences.budgetConfig } returns configFlow
        coEvery { transactionDao.insertTransaction(capture(insertedTransaction)) } returns Unit

        viewModel = MainViewModel(transactionDao, budgetPreferences, fixedClock, FakeReceiptScanner(), defaultDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addTransaction without date stamps clock now`() = runTest(testDispatcher.scheduler) {
        viewModel.addTransaction(1250L, "Lunch")
        advanceUntilIdle()

        assertEquals(fixedNowMillis, insertedTransaction.captured.date)
        assertEquals(1250L, insertedTransaction.captured.amount)
        assertEquals("Lunch", insertedTransaction.captured.note)
    }

    @Test
    fun `addTransaction with explicit date stores it verbatim`() = runTest(testDispatcher.scheduler) {
        val yesterday = fixedNowMillis - 86_400_000L
        viewModel.addTransaction(900L, "Forgotten lunch", yesterday)
        advanceUntilIdle()

        assertEquals(yesterday, insertedTransaction.captured.date)
    }

    @Test
    fun `addTransaction defaults to uncategorized with empty description and a time`() = runTest(testDispatcher.scheduler) {
        viewModel.addTransaction(1250L, "Lunch")
        advanceUntilIdle()

        assertNull(insertedTransaction.captured.category)
        assertEquals("", insertedTransaction.captured.description)
        assertTrue(insertedTransaction.captured.hasTime)
    }

    @Test
    fun `addTransaction can store a date-only expense`() = runTest(testDispatcher.scheduler) {
        val yesterdayNoon = fixedNowMillis - 86_400_000L
        viewModel.addTransaction(900L, "Market", yesterdayNoon, hasTime = false)
        advanceUntilIdle()

        assertEquals(yesterdayNoon, insertedTransaction.captured.date)
        assertFalse(insertedTransaction.captured.hasTime)
    }

    @Test
    fun `addTransaction persists category and description`() = runTest(testDispatcher.scheduler) {
        viewModel.addTransaction(
            1250L,
            "Lunch",
            category = Category.FOOD,
            description = "Nasi lemak and teh tarik"
        )
        advanceUntilIdle()

        assertEquals(Category.FOOD, insertedTransaction.captured.category)
        assertEquals("Nasi lemak and teh tarik", insertedTransaction.captured.description)
    }
}
