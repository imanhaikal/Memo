package com.imanhaikal.memo.ui

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
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Cycle-length arithmetic across month boundaries, leap years and elapsed cycles. */
@OptIn(ExperimentalCoroutinesApi::class)
class DaysRemainingTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId: ZoneId = ZoneId.systemDefault()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Seeds a budget starting on [start], with the clock fixed to [today], and returns the
     * resulting days-remaining once the state settles.
     */
    private suspend fun daysRemainingFor(
        start: LocalDate,
        today: LocalDate,
        totalDays: Int = 30,
        collect: (MainViewModel) -> Unit
    ): Int {
        val clock = Clock.fixed(today.atStartOfDay(zoneId).toInstant(), zoneId)
        val harness = MemoTestHarness(clock, today)
        val viewModel = harness.viewModel(testDispatcher)
        collect(viewModel)
        harness.seedBudget(amountCents = 100_000L, totalDays = totalDays, startDate = start)
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value.daysRemaining
    }

    @Test
    fun `start of cycle has the full cycle remaining`() = runTest {
        val today = LocalDate.of(2023, 1, 1)
        val result = daysRemainingFor(start = today, today = today) { viewModel ->
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        }
        assertEquals(30, result)
    }

    @Test
    fun `mid cycle counts down by the days that have passed`() = runTest {
        val result = daysRemainingFor(
            start = LocalDate.of(2023, 1, 1),
            today = LocalDate.of(2023, 1, 16)
        ) { viewModel ->
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        }
        assertEquals(15, result)
    }

    @Test
    fun `the last day of a cycle has one day remaining`() = runTest {
        val result = daysRemainingFor(
            start = LocalDate.of(2023, 1, 1),
            today = LocalDate.of(2023, 1, 30)
        ) { viewModel ->
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        }
        assertEquals(1, result)
    }

    @Test
    fun `an elapsed cycle rolls over into a fresh one instead of freezing at one day`() = runTest {
        // 35 days after the start, so one whole 30-day cycle has finished. The new cycle
        // began on Jan 31, putting today 5 days in.
        val result = daysRemainingFor(
            start = LocalDate.of(2023, 1, 1),
            today = LocalDate.of(2023, 2, 5)
        ) { viewModel ->
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        }
        assertEquals(25, result)
    }

    @Test
    fun `leap day counts as a real day`() = runTest {
        // 2024 is a leap year: 28 Feb -> 29 Feb -> 1 Mar is two days.
        val result = daysRemainingFor(
            start = LocalDate.of(2024, 2, 28),
            today = LocalDate.of(2024, 3, 1)
        ) { viewModel ->
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        }
        assertEquals(28, result)
    }

    @Test
    fun `a non-leap February rolls straight into March`() = runTest {
        val result = daysRemainingFor(
            start = LocalDate.of(2023, 2, 28),
            today = LocalDate.of(2023, 3, 1)
        ) { viewModel ->
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        }
        assertEquals(29, result)
    }
}
