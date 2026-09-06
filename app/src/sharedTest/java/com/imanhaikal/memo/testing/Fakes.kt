package com.imanhaikal.memo.testing

import com.imanhaikal.memo.data.ActiveBudgetStore
import com.imanhaikal.memo.data.AppearancePreferences
import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetCycle
import com.imanhaikal.memo.data.BudgetCycleDao
import com.imanhaikal.memo.data.BudgetDao
import com.imanhaikal.memo.data.CategoryCap
import com.imanhaikal.memo.data.CategoryCapDao
import com.imanhaikal.memo.data.CycleTotals
import com.imanhaikal.memo.data.NotificationPreferencesStore
import com.imanhaikal.memo.data.NotificationSettings
import com.imanhaikal.memo.data.RecurringRule
import com.imanhaikal.memo.data.RecurringRuleDao
import com.imanhaikal.memo.data.ThemeMode
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.TransactionRunner
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.domain.DayTicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory doubles for the data layer.
 *
 * Shared rather than nested in a test class: the DAO interfaces change whenever the schema
 * does, and there used to be two byte-identical copies of the transaction fake that both
 * had to be edited by hand.
 */

class FakeTransactionDao(initial: List<Transaction> = emptyList()) : TransactionDao {
    val rows = MutableStateFlow(initial)
    private val nextId = AtomicInteger(initial.maxOfOrNull { it.id }?.plus(1) ?: 1)

    override fun getAllTransactions(): Flow<List<Transaction>> =
        rows.map { list -> list.sortedByDescending { it.date } }

    override fun observeForBudget(budgetId: Long): Flow<List<Transaction>> =
        rows.map { list -> list.filter { it.budgetId == budgetId }.sortedByDescending { it.date } }

    override fun observeForCycle(
        budgetId: Long,
        startMillis: Long,
        endMillisExclusive: Long
    ): Flow<List<Transaction>> = rows.map { list ->
        list.filter { it.budgetId == budgetId && it.date >= startMillis && it.date < endMillisExclusive }
            .sortedByDescending { it.date }
    }

    override suspend fun getForCycle(
        budgetId: Long,
        startMillis: Long,
        endMillisExclusive: Long
    ): List<Transaction> = rows.value
        .filter { it.budgetId == budgetId && it.date >= startMillis && it.date < endMillisExclusive }
        .sortedByDescending { it.date }

    override suspend fun getAll(): List<Transaction> = rows.value

    /** Snapshot for Compose tests, which poll from outside a coroutine. */
    fun getTransactionsBlocking(): List<Transaction> = rows.value

    override suspend fun insertTransaction(transaction: Transaction) {
        val row = if (transaction.id == 0) transaction.copy(id = nextId.getAndIncrement()) else transaction
        rows.value = rows.value.filterNot { it.id == row.id } + row
    }

    override suspend fun insertAll(transactions: List<Transaction>) {
        transactions.forEach { insertTransaction(it) }
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        rows.value = rows.value.filterNot { it.id == transaction.id }
    }

    override suspend fun deleteAllForBudget(budgetId: Long) {
        rows.value = rows.value.filterNot { it.budgetId == budgetId }
    }

    override suspend fun deleteAllTransactions() {
        rows.value = emptyList()
    }

    override fun search(
        budgetId: Long,
        query: String,
        categoryId: String?,
        type: String?,
        minCents: Long?,
        maxCents: Long?,
        fromMillis: Long?,
        toMillis: Long?
    ): Flow<List<Transaction>> = rows.map { list ->
        list.filter { row ->
            row.budgetId == budgetId &&
                (query.isEmpty() ||
                    row.note.contains(query, ignoreCase = true) ||
                    row.description.contains(query, ignoreCase = true)) &&
                (categoryId == null || row.category?.id == categoryId) &&
                (type == null || row.type.id == type) &&
                (minCents == null || row.amount >= minCents) &&
                (maxCents == null || row.amount <= maxCents) &&
                (fromMillis == null || row.date >= fromMillis) &&
                (toMillis == null || row.date < toMillis)
        }.sortedByDescending { it.date }
    }

    override suspend fun countPostedForRuleOnDay(
        ruleId: Long,
        dayStart: Long,
        dayEndExclusive: Long
    ): Int = rows.value.count {
        it.recurringRuleId == ruleId && it.date >= dayStart && it.date < dayEndExclusive
    }
}

class FakeBudgetDao(initial: List<Budget> = emptyList()) : BudgetDao {
    val rows = MutableStateFlow(initial)
    private val nextId = AtomicLong(initial.maxOfOrNull { it.id }?.plus(1) ?: 1L)

    private fun sorted(list: List<Budget>) =
        list.sortedWith(compareBy({ it.isArchived }, { it.sortOrder }, { it.createdAt }))

    override fun observeAll(): Flow<List<Budget>> = rows.map(::sorted)

    override fun observeById(id: Long): Flow<Budget?> =
        rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getById(id: Long): Budget? = rows.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<Budget> = sorted(rows.value)

    override suspend fun getFirstActive(): Budget? = sorted(rows.value).firstOrNull { !it.isArchived }

    override suspend fun insert(budget: Budget): Long {
        val row = if (budget.id == 0L) budget.copy(id = nextId.getAndIncrement()) else budget
        rows.value = rows.value.filterNot { it.id == row.id } + row
        return row.id
    }

    override suspend fun insertAll(budgets: List<Budget>) {
        budgets.forEach { insert(it) }
    }

    override suspend fun update(budget: Budget) {
        rows.value = rows.value.map { if (it.id == budget.id) budget else it }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(isArchived = archived) else it }
    }

    override suspend fun delete(budget: Budget) {
        rows.value = rows.value.filterNot { it.id == budget.id }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun count(): Int = rows.value.size
}

/** Needs the transaction fake to compute totals, the way the real query does. */
class FakeBudgetCycleDao(
    private val transactions: FakeTransactionDao = FakeTransactionDao()
) : BudgetCycleDao {
    val rows = MutableStateFlow<List<BudgetCycle>>(emptyList())
    private val nextId = AtomicLong(1L)

    override fun observeForBudget(budgetId: Long): Flow<List<BudgetCycle>> =
        rows.map { list -> list.filter { it.budgetId == budgetId }.sortedByDescending { it.cycleIndex } }

    override fun observeClosed(budgetId: Long): Flow<List<BudgetCycle>> =
        rows.map { list ->
            list.filter { it.budgetId == budgetId && it.closedAt != null }
                .sortedByDescending { it.cycleIndex }
        }

    override suspend fun getById(id: Long): BudgetCycle? = rows.value.firstOrNull { it.id == id }

    override suspend fun getOpenCycle(budgetId: Long): BudgetCycle? =
        rows.value.firstOrNull { it.budgetId == budgetId && it.closedAt == null }

    override suspend fun getLatest(budgetId: Long): BudgetCycle? =
        rows.value.filter { it.budgetId == budgetId }.maxByOrNull { it.cycleIndex }

    override suspend fun getByIndex(budgetId: Long, cycleIndex: Int): BudgetCycle? =
        rows.value.firstOrNull { it.budgetId == budgetId && it.cycleIndex == cycleIndex }

    override suspend fun reopen(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(closedAt = null) else it }
    }

    override suspend fun getAll(): List<BudgetCycle> = rows.value

    override suspend fun getTotals(
        budgetId: Long,
        startMillis: Long,
        endMillisExclusive: Long
    ): CycleTotals {
        val inRange = transactions.rows.value.filter {
            it.budgetId == budgetId && it.date >= startMillis && it.date < endMillisExclusive
        }
        return CycleTotals(
            spentCents = inRange.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
            incomeCents = inRange.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            transactionCount = inRange.size
        )
    }

    override suspend fun insert(cycle: BudgetCycle): Long {
        val row = if (cycle.id == 0L) cycle.copy(id = nextId.getAndIncrement()) else cycle
        // Mirrors the (budgetId, cycleIndex) unique index plus REPLACE.
        rows.value = rows.value.filterNot {
            it.id == row.id || (it.budgetId == row.budgetId && it.cycleIndex == row.cycleIndex)
        } + row
        return row.id
    }

    override suspend fun insertAll(cycles: List<BudgetCycle>) {
        cycles.forEach { insert(it) }
    }

    override suspend fun syncOpenCycle(
        id: Long,
        startDate: Long,
        endDateExclusive: Long,
        budgetAmountCents: Long
    ) {
        rows.value = rows.value.map { cycle ->
            if (cycle.id == id && cycle.closedAt == null) {
                cycle.copy(
                    startDate = startDate,
                    endDateExclusive = endDateExclusive,
                    budgetAmountCents = budgetAmountCents
                )
            } else {
                cycle
            }
        }
    }

    override suspend fun close(id: Long, closedAt: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(closedAt = closedAt) else it }
    }

    override suspend fun deleteForBudget(budgetId: Long) {
        rows.value = rows.value.filterNot { it.budgetId == budgetId }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

class FakeCategoryCapDao : CategoryCapDao {
    val rows = MutableStateFlow<List<CategoryCap>>(emptyList())

    override fun observeForBudget(budgetId: Long): Flow<List<CategoryCap>> =
        rows.map { list -> list.filter { it.budgetId == budgetId } }

    override suspend fun getAll(): List<CategoryCap> = rows.value

    override suspend fun upsert(cap: CategoryCap) {
        rows.value = rows.value.filterNot {
            it.budgetId == cap.budgetId && it.category == cap.category
        } + cap
    }

    override suspend fun insertAll(caps: List<CategoryCap>) {
        caps.forEach { upsert(it) }
    }

    override suspend fun delete(budgetId: Long, categoryId: String) {
        rows.value = rows.value.filterNot { it.budgetId == budgetId && it.category == categoryId }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

class FakeRecurringRuleDao : RecurringRuleDao {
    val rows = MutableStateFlow<List<RecurringRule>>(emptyList())
    private val nextId = AtomicLong(1L)

    override fun observeForBudget(budgetId: Long): Flow<List<RecurringRule>> =
        rows.map { list -> list.filter { it.budgetId == budgetId }.sortedBy { it.nextDueDate } }

    override suspend fun getActiveRules(): List<RecurringRule> = rows.value.filterNot { it.isPaused }

    override suspend fun getAll(): List<RecurringRule> = rows.value

    override suspend fun insert(rule: RecurringRule): Long {
        val row = if (rule.id == 0L) rule.copy(id = nextId.getAndIncrement()) else rule
        rows.value = rows.value.filterNot { it.id == row.id } + row
        return row.id
    }

    override suspend fun insertAll(rules: List<RecurringRule>) {
        rules.forEach { insert(it) }
    }

    override suspend fun update(rule: RecurringRule) {
        rows.value = rows.value.map { if (it.id == rule.id) rule else it }
    }

    override suspend fun delete(rule: RecurringRule) {
        rows.value = rows.value.filterNot { it.id == rule.id }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

class FakeActiveBudgetStore(initial: Long = 1L) : ActiveBudgetStore {
    val state = MutableStateFlow(initial)
    override val activeBudgetId: Flow<Long> = state
    override suspend fun setActiveBudgetId(id: Long) {
        state.value = id
    }
}

class FakeAppearancePreferences(
    initialTheme: ThemeMode = ThemeMode.SYSTEM,
    initialHaptics: Boolean = true
) : AppearancePreferences {
    private val theme = MutableStateFlow(initialTheme)
    private val haptics = MutableStateFlow(initialHaptics)

    override val themeMode: Flow<ThemeMode> = theme
    override val hapticsEnabled: Flow<Boolean> = haptics

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        haptics.value = enabled
    }
}

class FakeNotificationPreferences : NotificationPreferencesStore {
    val state = MutableStateFlow(NotificationSettings())
    private var overLimitDay = Long.MIN_VALUE
    private var reportedCycle = -1L

    override val settings: Flow<NotificationSettings> = state

    override suspend fun update(settings: NotificationSettings) {
        state.value = settings
    }

    override suspend fun current(): NotificationSettings = state.value

    override suspend fun lastOverLimitDay(): Long = overLimitDay

    override suspend fun setLastOverLimitDay(epochDay: Long) {
        overLimitDay = epochDay
    }

    override suspend fun lastReportedCycleId(): Long = reportedCycle

    override suspend fun setLastReportedCycleId(cycleId: Long) {
        reportedCycle = cycleId
    }
}

/** Runs the block inline; the real one wraps it in a Room transaction. */
object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R = block()
}

/** Lets a test move the calendar forward by hand. */
class FakeDayTicker(initial: LocalDate) : DayTicker {
    val state = MutableStateFlow(initial)
    override val today: Flow<LocalDate> = state

    fun advanceTo(date: LocalDate) {
        state.value = date
    }
}
