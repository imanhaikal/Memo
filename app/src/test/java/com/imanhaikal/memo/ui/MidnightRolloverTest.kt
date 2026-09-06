package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.testing.MemoTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The state used to recompute only when a transaction or setting changed, so an app left
 * open past midnight kept showing yesterday's number. The day ticker is now an input to
 * the state, and these are the behaviours that depend on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MidnightRolloverTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("UTC")
    private val day1: LocalDate = LocalDate.of(2024, 1, 10)

    private lateinit var harness: MemoTestHarness
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // The clock stays on day 1; only the ticker moves, which is exactly the case
        // that used to be invisible to the UI.
        harness = MemoTestHarness(Clock.fixed(day1.atStartOfDay(zone).toInstant(), zone), day1)
        viewModel = harness.viewModel(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `crossing midnight decrements the days remaining`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = day1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(30, viewModel.uiState.value.daysRemaining)

        harness.dayTicker.advanceTo(day1.plusDays(1))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(29, viewModel.uiState.value.daysRemaining)
    }

    @Test
    fun `yesterday's spending stops counting against today`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = day1)
        harness.transactionDao.insertTransaction(
            Transaction(
                amount = 4_000L,
                note = "Dinner",
                date = harness.millisAtNoon(day1),
                budgetId = 1L
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(4_000L, viewModel.uiState.value.spentToday)

        harness.dayTicker.advanceTo(day1.plusDays(1))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.spentToday)
        // It still belongs to the cycle, just not to today.
        assertEquals(4_000L, state.spentThisCycle)
    }

    @Test
    fun `crossing a cycle boundary archives the finished cycle while the app is open`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        harness.seedBudget(amountCents = 300_000L, totalDays = 7, startDate = day1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, harness.cycleDao.rows.value.single().cycleIndex)

        harness.dayTicker.advanceTo(day1.plusDays(7))
        testDispatcher.scheduler.advanceUntilIdle()

        val closed = harness.cycleDao.rows.value.single { it.cycleIndex == 0 }
        assertNotNull(closed.closedAt)
        // ...and the next one opened, rather than leaving the budget with no open cycle.
        assertEquals(1, harness.cycleDao.rows.value.single { it.closedAt == null }.cycleIndex)
    }
}
