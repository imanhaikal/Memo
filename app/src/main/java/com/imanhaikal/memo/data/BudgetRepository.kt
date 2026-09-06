package com.imanhaikal.memo.data

import com.imanhaikal.memo.domain.CycleMath
import com.imanhaikal.memo.domain.CycleRolloverUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * The single seam for budget state. The ViewModel, the recurring worker and the widget all
 * read through this rather than each reaching into three DAOs.
 */
class BudgetRepository(
    private val runInTransaction: TransactionRunner,
    private val budgetDao: BudgetDao,
    private val budgetCycleDao: BudgetCycleDao,
    private val categoryCapDao: CategoryCapDao,
    private val transactionDao: TransactionDao,
    private val preferences: ActiveBudgetStore,
    private val cycleRollover: CycleRolloverUseCase,
    private val clock: Clock
) {

    fun observeBudgets(): Flow<List<Budget>> = budgetDao.observeAll()

    /**
     * The budget the dashboard is showing, or null when the user has none yet.
     *
     * Falls back to the first unarchived budget when the stored id points at a budget that
     * has since been deleted, so a stale preference can never strand the user on an empty
     * dashboard.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveBudget(): Flow<Budget?> =
        preferences.activeBudgetId
            .flatMapLatest { id -> budgetDao.observeById(id) }
            .distinctUntilChanged()

    suspend fun resolveActiveBudget(): Budget? {
        budgetDao.getById(currentActiveId())?.let { return it }
        val fallback = budgetDao.getFirstActive() ?: return null
        preferences.setActiveBudgetId(fallback.id)
        return fallback
    }

    private suspend fun currentActiveId(): Long = preferences.activeBudgetId.first()

    suspend fun setActiveBudget(id: Long) = preferences.setActiveBudgetId(id)

    suspend fun createBudget(
        name: String,
        amountCents: Long,
        totalDays: Int,
        currencyCode: String,
        makeActive: Boolean = true
    ): Long {
        val now = clock.millis()
        val budget = Budget(
            name = name,
            amountCents = amountCents,
            totalDays = totalDays,
            currencyCode = currencyCode,
            firstCycleStartDate = CycleMath.toEpochDay(now, clock.zone),
            createdAt = now,
            sortOrder = budgetDao.count()
        )
        val id = budgetDao.insert(budget)
        cycleRollover.ensureCurrentCycle(budget.copy(id = id))
        if (makeActive) preferences.setActiveBudgetId(id)
        return id
    }

    suspend fun updateBudget(budget: Budget) = budgetDao.update(budget)

    suspend fun setArchived(id: Long, archived: Boolean) {
        budgetDao.setArchived(id, archived)
        if (archived && currentActiveId() == id) {
            budgetDao.getFirstActive()?.let { preferences.setActiveBudgetId(it.id) }
        }
    }

    /**
     * Removes a budget and everything belonging to it.
     *
     * `transactions` deliberately has no foreign key on `budgetId` — adding one would have
     * meant rebuilding the table in the v5 migration, and a `DROP TABLE` on the user's whole
     * spending history is the one operation worth designing around. The cascade is done
     * here instead, in a single database transaction so a failure cannot leave orphans.
     */
    suspend fun deleteBudget(budget: Budget) {
        runInTransaction {
            transactionDao.deleteAllForBudget(budget.id)
            budgetDao.delete(budget) // cycles, caps and rules cascade via their foreign keys
        }
        if (currentActiveId() == budget.id) {
            budgetDao.getFirstActive()?.let { preferences.setActiveBudgetId(it.id) }
        }
    }

    /** Clears a budget's transactions and history without deleting the budget itself. */
    suspend fun clearBudgetData(budget: Budget) {
        runInTransaction {
            transactionDao.deleteAllForBudget(budget.id)
            budgetCycleDao.deleteForBudget(budget.id)
        }
        cycleRollover.ensureCurrentCycle(budget)
    }

    fun observeCaps(budgetId: Long): Flow<Map<Category, Long>> =
        categoryCapDao.observeForBudget(budgetId).map { caps ->
            caps.mapNotNull { cap ->
                Category.fromId(cap.category)?.let { it to cap.capCents }
            }.toMap()
        }

    suspend fun setCap(budgetId: Long, category: Category, capCents: Long?) {
        if (capCents == null || capCents <= 0L) {
            categoryCapDao.delete(budgetId, category.id)
        } else {
            categoryCapDao.upsert(CategoryCap(budgetId, category.id, capCents))
        }
    }

    fun observeClosedCycles(budgetId: Long): Flow<List<BudgetCycle>> =
        budgetCycleDao.observeClosed(budgetId)

    /** The cycle that finished most recently, for the end-of-cycle summary. */
    suspend fun mostRecentlyClosedCycle(budgetId: Long): BudgetCycle? =
        budgetCycleDao.getAll()
            .filter { it.budgetId == budgetId && it.closedAt != null }
            .maxByOrNull { it.cycleIndex }

    suspend fun totalsFor(cycle: BudgetCycle): CycleTotals = budgetCycleDao.getTotals(
        budgetId = cycle.budgetId,
        startMillis = CycleMath.dayStartMillis(cycle.startDate, clock.zone),
        endMillisExclusive = CycleMath.dayStartMillis(cycle.endDateExclusive, clock.zone)
    )

    suspend fun ensureCurrentCycle(
        budget: Budget,
        today: LocalDate = LocalDate.now(clock)
    ): BudgetCycle = cycleRollover.ensureCurrentCycle(budget, today)
}
