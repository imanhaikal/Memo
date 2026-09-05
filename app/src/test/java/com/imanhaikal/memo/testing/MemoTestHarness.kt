package com.imanhaikal.memo.testing

import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.BudgetRepository
import com.imanhaikal.memo.data.ThemeMode
import com.imanhaikal.memo.data.receipt.ScanOutcome
import com.imanhaikal.memo.domain.BudgetCalculatorUseCase
import com.imanhaikal.memo.domain.CycleMath
import com.imanhaikal.memo.domain.CycleRolloverUseCase
import com.imanhaikal.memo.data.receipt.FakeReceiptScanner
import com.imanhaikal.memo.ui.MainViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate

/**
 * Assembles a [MainViewModel] over in-memory fakes.
 *
 * The ViewModel now composes a repository, a day ticker and a startup migration, so the
 * old per-test `mockk<TransactionDao>()` setup no longer says much. Building the real
 * repository over fakes keeps the tests exercising actual wiring instead of stubs.
 */
class MemoTestHarness(
    val clock: Clock,
    today: LocalDate = LocalDate.now(clock.zone)
) {
    val transactionDao = FakeTransactionDao()
    val budgetDao = FakeBudgetDao()
    val cycleDao = FakeBudgetCycleDao(transactionDao)
    val capDao = FakeCategoryCapDao()
    val recurringDao = FakeRecurringRuleDao()
    val activeBudgetStore = FakeActiveBudgetStore()
    val dayTicker = FakeDayTicker(today)
    val scanner = FakeReceiptScanner()

    val cycleRollover = CycleRolloverUseCase(cycleDao, clock)

    val repository = BudgetRepository(
        runInTransaction = ImmediateTransactionRunner,
        budgetDao = budgetDao,
        budgetCycleDao = cycleDao,
        categoryCapDao = capDao,
        transactionDao = transactionDao,
        preferences = activeBudgetStore,
        cycleRollover = cycleRollover,
        clock = clock
    )

    /** Only theme and haptics are read off this; budget values live in Room now. */
    val preferences: BudgetPreferences = mockk(relaxed = true) {
        every { themeMode } returns flowOf(ThemeMode.SYSTEM)
        every { hapticsEnabled } returns flowOf(true)
    }

    /**
     * Seeds a budget the way BudgetBootstrap would, including its open cycle, and makes
     * it active. [startDate] defaults to today so day 1 of the cycle is today.
     */
    suspend fun seedBudget(
        amountCents: Long,
        totalDays: Int = 30,
        startDate: LocalDate = LocalDate.now(clock.zone),
        name: String = "Monthly",
        currencyCode: String = "MYR"
    ): Budget {
        val budget = Budget(
            name = name,
            amountCents = amountCents,
            totalDays = totalDays,
            currencyCode = currencyCode,
            firstCycleStartDate = startDate.toEpochDay(),
            createdAt = clock.millis()
        )
        val id = budgetDao.insert(budget)
        val stored = budget.copy(id = id)
        cycleRollover.ensureCurrentCycle(stored)
        activeBudgetStore.setActiveBudgetId(id)
        return stored
    }

    suspend fun updateBudget(budget: Budget) = budgetDao.update(budget)

    fun scanReturns(outcome: ScanOutcome) {
        scanner.outcome = outcome
    }

    fun viewModel(dispatcher: CoroutineDispatcher): MainViewModel = MainViewModel(
        budgetRepository = repository,
        transactionDao = transactionDao,
        budgetPreferences = preferences,
        clock = clock,
        receiptScanner = scanner,
        dayTicker = dayTicker,
        // Nothing to migrate in tests; already complete so uiState emits immediately.
        startupMigration = CompletableDeferred(Unit),
        budgetCalculator = BudgetCalculatorUseCase(clock.zone),
        defaultDispatcher = dispatcher
    )

    /** Epoch millis at local noon on [date] — the convention for date-only entries. */
    fun millisAtNoon(date: LocalDate): Long =
        CycleMath.dayStartMillis(date.toEpochDay(), clock.zone) + 12 * 60 * 60 * 1000L
}
