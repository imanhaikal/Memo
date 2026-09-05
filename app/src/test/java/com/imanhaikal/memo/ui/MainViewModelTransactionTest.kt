package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.testing.MemoTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val testDispatcher = StandardTestDispatcher()
    private val fixedNowMillis = 1_752_300_000_000L
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(fixedNowMillis), zone)
    private val today = Instant.ofEpochMilli(fixedNowMillis).atZone(zone).toLocalDate()

    private lateinit var harness: MemoTestHarness
    private lateinit var viewModel: MainViewModel

    /** The single row written by the action under test. */
    private val inserted: Transaction get() = harness.transactionDao.rows.value.single()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        harness = MemoTestHarness(fixedClock, today)
        viewModel = harness.viewModel(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seed() = harness.seedBudget(amountCents = 100_000L, startDate = today)

    @Test
    fun `addTransaction without date stamps clock now`() = runTest(testDispatcher.scheduler) {
        seed()
        viewModel.addTransaction(1250L, "Lunch")
        advanceUntilIdle()

        assertEquals(fixedNowMillis, inserted.date)
        assertEquals(1250L, inserted.amount)
        assertEquals("Lunch", inserted.note)
    }

    @Test
    fun `addTransaction with explicit date stores it verbatim`() = runTest(testDispatcher.scheduler) {
        seed()
        val yesterday = fixedNowMillis - 86_400_000L
        viewModel.addTransaction(900L, "Forgotten lunch", yesterday)
        advanceUntilIdle()

        assertEquals(yesterday, inserted.date)
    }

    @Test
    fun `addTransaction defaults to an uncategorized expense with a time`() =
        runTest(testDispatcher.scheduler) {
            seed()
            viewModel.addTransaction(1250L, "Lunch")
            advanceUntilIdle()

            assertNull(inserted.category)
            assertEquals("", inserted.description)
            assertTrue(inserted.hasTime)
            assertEquals(TransactionType.EXPENSE, inserted.type)
        }

    @Test
    fun `addTransaction can store a date-only expense`() = runTest(testDispatcher.scheduler) {
        seed()
        val yesterdayNoon = fixedNowMillis - 86_400_000L
        viewModel.addTransaction(900L, "Market", yesterdayNoon, hasTime = false)
        advanceUntilIdle()

        assertEquals(yesterdayNoon, inserted.date)
        assertFalse(inserted.hasTime)
    }

    @Test
    fun `addTransaction persists category and description`() = runTest(testDispatcher.scheduler) {
        seed()
        viewModel.addTransaction(
            1250L,
            "Lunch",
            category = Category.FOOD,
            description = "Nasi lemak and teh tarik"
        )
        advanceUntilIdle()

        assertEquals(Category.FOOD, inserted.category)
        assertEquals("Nasi lemak and teh tarik", inserted.description)
    }

    @Test
    fun `addTransaction can record income`() = runTest(testDispatcher.scheduler) {
        seed()
        viewModel.addTransaction(5_000L, "Refund", type = TransactionType.INCOME)
        advanceUntilIdle()

        assertEquals(TransactionType.INCOME, inserted.type)
        // The magnitude stays positive; the type carries the sign.
        assertEquals(5_000L, inserted.amount)
        assertEquals(-5_000L, inserted.signedAmount)
    }

    @Test
    fun `a new transaction is filed against the active budget`() = runTest(testDispatcher.scheduler) {
        seed()
        val travel = harness.seedBudget(amountCents = 50_000L, startDate = today, name = "Travel")

        viewModel.addTransaction(1250L, "Taxi")
        advanceUntilIdle()

        assertEquals(travel.id, inserted.budgetId)
    }

    @Test
    fun `nothing is written when no budget exists yet`() = runTest(testDispatcher.scheduler) {
        viewModel.addTransaction(1250L, "Lunch")
        advanceUntilIdle()

        assertTrue(harness.transactionDao.rows.value.isEmpty())
    }
}
