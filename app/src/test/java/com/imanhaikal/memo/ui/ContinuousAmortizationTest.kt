package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.testing.MemoTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The "fluid pool" rules: today's spending never changes today's allowance, past spending
 * re-spreads what is left, and overspending today is re-amortized immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousAmortizationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val now: Instant = Instant.parse("2024-01-15T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, zoneId)
    private val today: LocalDate = now.atZone(zoneId).toLocalDate()
    private val nowMillis = now.toEpochMilli()

    private lateinit var harness: MemoTestHarness
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        harness = MemoTestHarness(clock, today)
        viewModel = harness.viewModel(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun settle() = testDispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `spending today does not reduce today's daily limit`() = runTest {
        collectState()
        harness.seedBudget(amountCents = 100_000L, totalDays = 10, startDate = today)
        settle()

        assertEquals(10_000L, viewModel.uiState.value.dailyLimit)

        harness.transactionDao.insertTransaction(
            Transaction(amount = 10_000L, note = "Spending Today", date = nowMillis, budgetId = 1L)
        )
        settle()

        // The allowance for today was fixed this morning; spending it just uses it up.
        val state = viewModel.uiState.value
        assertEquals(10_000L, state.dailyLimit)
        assertEquals(0L, state.availableToday)
    }

    @Test
    fun `spending yesterday re-spreads what is left over the remaining days`() = runTest {
        collectState()
        val yesterday = today.minusDays(1)
        harness.seedBudget(amountCents = 100_000L, totalDays = 10, startDate = yesterday)
        harness.transactionDao.insertTransaction(
            Transaction(
                amount = 10_000L,
                note = "Spending Yesterday",
                date = yesterday.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                budgetId = 1L
            )
        )
        settle()

        // Pool 900 over 9 remaining days is back to 100 a day.
        val state = viewModel.uiState.value
        assertEquals(9, state.daysRemaining)
        assertEquals(10_000L, state.dailyLimit)
        assertEquals(10_000L, state.availableToday)
    }

    @Test
    fun `spending past the whole budget floors the daily limit at zero`() = runTest {
        collectState()
        harness.seedBudget(amountCents = 10_000L, totalDays = 10, startDate = today)
        harness.transactionDao.insertTransaction(
            Transaction(amount = 20_000L, note = "Over Budget", date = nowMillis, budgetId = 1L)
        )
        settle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.dailyLimit)
        assertEquals(-19_000L, state.availableToday)
    }

    @Test
    fun `overspending today re-amortizes across the remaining days`() = runTest {
        collectState()
        harness.seedBudget(amountCents = 100_000L, totalDays = 10, startDate = today)
        harness.transactionDao.insertTransaction(
            Transaction(amount = 19_000L, note = "Over Limit Today", date = nowMillis, budgetId = 1L)
        )
        settle()

        // (1000 - 190) spread over the 9 days that are left.
        val state = viewModel.uiState.value
        assertEquals(9_000L, state.dailyLimit)
        assertEquals(-9_000L, state.availableToday)
        assertEquals(BudgetStatus.OVER_LIMIT, state.status)
    }

    @Test
    fun `on the last day the daily limit is the whole remaining pool`() = runTest {
        collectState()
        harness.seedBudget(
            amountCents = 10_000L,
            totalDays = 10,
            startDate = today.minusDays(9)
        )
        settle()

        val state = viewModel.uiState.value
        assertEquals(1, state.daysRemaining)
        assertEquals(10_000L, state.dailyLimit)
        assertEquals(10_000L, state.availableToday)
    }

    @Test
    fun `a refund posted today gives back the allowance it cancels`() = runTest {
        collectState()
        harness.seedBudget(amountCents = 100_000L, totalDays = 10, startDate = today)
        harness.transactionDao.insertTransaction(
            Transaction(amount = 8_000L, note = "Jacket", date = nowMillis, budgetId = 1L)
        )
        settle()
        assertEquals(2_000L, viewModel.uiState.value.availableToday)

        harness.transactionDao.insertTransaction(
            Transaction(
                amount = 8_000L,
                note = "Returned it",
                date = nowMillis,
                budgetId = 1L,
                type = com.imanhaikal.memo.data.TransactionType.INCOME
            )
        )
        settle()

        assertEquals(10_000L, viewModel.uiState.value.availableToday)
    }
}
