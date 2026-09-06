package com.imanhaikal.memo.ui

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.imanhaikal.memo.MemoApplication
import com.imanhaikal.memo.work.MemoWorkScheduler
import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetCycle
import com.imanhaikal.memo.data.AppearancePreferences
import com.imanhaikal.memo.data.BudgetRepository
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.NotificationPreferencesStore
import com.imanhaikal.memo.data.NotificationSettings
import com.imanhaikal.memo.data.RecurringRule
import com.imanhaikal.memo.data.RecurringRuleDao
import com.imanhaikal.memo.data.CycleTotals
import com.imanhaikal.memo.data.ThemeMode
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.data.backup.BackupRepository
import com.imanhaikal.memo.data.backup.ImportMode
import com.imanhaikal.memo.data.backup.ImportResult
import com.imanhaikal.memo.data.backup.MemoBackup
import com.imanhaikal.memo.data.receipt.ReceiptScanner
import com.imanhaikal.memo.data.receipt.ScanFailureReason
import com.imanhaikal.memo.data.receipt.ScanOutcome
import com.imanhaikal.memo.domain.BudgetCalculatorUseCase
import com.imanhaikal.memo.domain.DayTicker
import com.imanhaikal.memo.domain.PostRecurringUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

private const val SEARCH_DEBOUNCE_MS = 250L
private const val TAG = "MainViewModel"

enum class BudgetStatus {
    ON_TRACK, CAREFUL, OVER_LIMIT
}

sealed interface ScanState {
    data object Idle : ScanState
    data object Processing : ScanState
    data class Success(
        val amountCents: Long,
        val note: String,
        val category: Category? = null,
        val description: String = "",
        val dateMillis: Long? = null,
        val dateHasTime: Boolean = true
    ) : ScanState
    data class Error(val reason: ScanFailureReason) : ScanState
}

@Immutable
/** Total spent in the active cycle for one category; null category = uncategorized. */
data class CategoryTotal(
    val category: Category?,
    val totalCents: Long,
    /** The user's limit for this category, or null when uncapped. */
    val capCents: Long? = null,
    val isOverCap: Boolean = false
)

/** One finished cycle plus its totals, computed from the transactions still on file. */
@Immutable
data class CycleSummary(
    val cycle: BudgetCycle,
    val totals: CycleTotals
)

data class BudgetUiState(
    val isLoading: Boolean = true,
    val isSetup: Boolean = false,
    val availableToday: Long = 0L,
    val dailyLimit: Long = 0L,
    val daysRemaining: Int = 1,
    val transactions: List<Transaction> = emptyList(),
    val status: BudgetStatus = BudgetStatus.ON_TRACK,
    val totalBudget: Long = 0L,
    /** Net of income, matching the pool arithmetic. */
    val spentToday: Long = 0L,
    val spentThisCycle: Long = 0L,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    /** First day of the active cycle; null until setup completes. */
    val cycleStartDate: LocalDate? = null,
    val totalDays: Int = 30,
    val currencyCode: String = "MYR",
    val budgetId: Long = 0L,
    val budgetName: String = "",
    val allBudgets: List<Budget> = emptyList()
)

class MainViewModel(
    private val budgetRepository: BudgetRepository,
    private val backupRepository: BackupRepository,
    private val transactionDao: TransactionDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val postRecurring: PostRecurringUseCase,
    private val notificationPreferences: NotificationPreferencesStore,
    private val appearancePreferences: AppearancePreferences,
    private val clock: Clock,
    private val receiptScanner: ReceiptScanner,
    private val dayTicker: DayTicker,
    private val startupMigration: Deferred<Unit>,
    /** Lets the app re-schedule background work when the toggles change. */
    private val onNotificationSettingsChanged: (NotificationSettings) -> Unit = {},
    private val budgetCalculator: BudgetCalculatorUseCase = BudgetCalculatorUseCase(clock.zone),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private var scanJob: kotlinx.coroutines.Job? = null

    val themeMode: StateFlow<ThemeMode> = appearancePreferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appearancePreferences.setThemeMode(mode)
        }
    }

    val hapticsEnabled: StateFlow<Boolean> = appearancePreferences.hapticsEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true
    )

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appearancePreferences.setHapticsEnabled(enabled)
        }
    }

    val isScanAvailable: Boolean get() = receiptScanner.isAvailable

    /**
     * Waits for the DataStore → Room handoff before emitting anything but `isLoading`.
     * The splash screen is held on `isLoading`, so an upgrading user never sees the setup
     * dialog flash over their existing data.
     */
    val uiState: StateFlow<BudgetUiState> = flow {
        startupMigration.await()
        emitAll(budgetStateFlow())
    }
        // Without this, one throw anywhere upstream ends the flow for good: stateIn keeps
        // serving the last value and the dashboard silently stops updating, which looks
        // exactly like a frozen app. Better to surface a blank state than to strand it.
        .catch { error ->
            Log.e(TAG, "Budget state failed", error)
            emit(BudgetUiState(isLoading = false, isSetup = false))
        }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BudgetUiState(isLoading = true)
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun budgetStateFlow(): Flow<BudgetUiState> =
        combine(
            budgetRepository.observeActiveBudget(),
            dayTicker.today
        ) { budget, today -> budget to today }
            .flatMapLatest { (budget, today) ->
                if (budget == null) {
                    flowOf(BudgetUiState(isLoading = false, isSetup = false))
                } else {
                    // Idempotent: archives any elapsed cycle and opens the current one.
                    // Running it here means a midnight tick rolls the cycle over while the
                    // app is open, not just on next launch.
                    val cycle = budgetRepository.ensureCurrentCycle(budget, today)
                    combine(
                        transactionDao.observeForBudget(budget.id),
                        budgetRepository.observeCaps(budget.id),
                        budgetRepository.observeBudgets()
                    ) { transactions, caps, budgets ->
                        budgetCalculator.calculate(
                            transactions = transactions,
                            budget = budget,
                            cycle = cycle,
                            caps = caps,
                            today = today,
                            allBudgets = budgets
                        )
                    }
                }
            }

    // ---- Budgets -----------------------------------------------------------------

    fun setupBudget(amountCents: Long, days: Int, currency: String = "MYR") {
        viewModelScope.launch {
            budgetRepository.createBudget(
                name = "Monthly",
                amountCents = amountCents,
                totalDays = days,
                currencyCode = currency
            )
        }
    }

    fun createBudget(name: String, amountCents: Long, days: Int, currency: String) {
        viewModelScope.launch {
            budgetRepository.createBudget(name, amountCents, days, currency)
        }
    }

    /** Edits the active budget in place; the cycle keeps its original start date. */
    fun updateBudget(amountCents: Long, days: Int, currency: String) {
        viewModelScope.launch {
            val active = budgetRepository.resolveActiveBudget() ?: return@launch
            budgetRepository.updateBudget(
                active.copy(
                    amountCents = amountCents,
                    totalDays = days,
                    currencyCode = currency
                )
            )
        }
    }

    fun renameBudget(budgetId: Long, name: String) {
        viewModelScope.launch {
            val budget = budgetRepository.observeBudgets().first().firstOrNull { it.id == budgetId }
            if (budget != null) budgetRepository.updateBudget(budget.copy(name = name))
        }
    }

    fun selectBudget(id: Long) {
        viewModelScope.launch { budgetRepository.setActiveBudget(id) }
    }

    fun setBudgetArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { budgetRepository.setArchived(id, archived) }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { budgetRepository.deleteBudget(budget) }
    }

    fun setCategoryCap(category: Category, capCents: Long?) {
        viewModelScope.launch {
            val active = budgetRepository.resolveActiveBudget() ?: return@launch
            budgetRepository.setCap(active.id, category, capCents)
        }
    }

    /** Per-category limits for the active budget, for the caps editor. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryCaps: StateFlow<Map<Category, Long>> =
        budgetRepository.observeActiveBudget()
            .flatMapLatest { budget ->
                if (budget == null) flowOf(emptyMap()) else budgetRepository.observeCaps(budget.id)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    /** Closed cycles for the active budget, newest first, with totals attached. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cycleHistory: StateFlow<List<CycleSummary>> =
        budgetRepository.observeActiveBudget()
            .flatMapLatest { budget ->
                if (budget == null) flowOf(emptyList())
                else budgetRepository.observeClosedCycles(budget.id)
            }
            .map { cycles -> cycles.map { CycleSummary(it, budgetRepository.totalsFor(it)) } }
            .flowOn(defaultDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    // ---- Transactions ------------------------------------------------------------

    fun addTransaction(
        amountCents: Long,
        note: String,
        dateMillis: Long? = null,
        category: Category? = null,
        description: String = "",
        hasTime: Boolean = true,
        type: TransactionType = TransactionType.EXPENSE
    ) {
        viewModelScope.launch {
            val active = budgetRepository.resolveActiveBudget() ?: return@launch
            transactionDao.insertTransaction(
                Transaction(
                    amount = amountCents,
                    note = note,
                    date = dateMillis ?: clock.millis(),
                    category = category,
                    description = description,
                    hasTime = hasTime,
                    budgetId = active.id,
                    type = type
                )
            )
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.insertTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.deleteTransaction(transaction)
        }
    }

    fun restoreTransaction(transaction: Transaction) {
        viewModelScope.launch {
            // REPLACE insert with the original id/date puts it back exactly where it was
            transactionDao.insertTransaction(transaction)
        }
    }

    // ---- Destructive ---------------------------------------------------------------

    /**
     * Clears the active budget's transactions and history but keeps the budget itself.
     * Scoped to one budget — the pre-v5 version deleted every row in the database.
     */
    fun clearActiveBudgetData() {
        viewModelScope.launch {
            val active = budgetRepository.resolveActiveBudget() ?: return@launch
            budgetRepository.clearBudgetData(active)
        }
    }

    /** Deletes the active budget outright, returning the app to setup if it was the last. */
    fun resetBudget() {
        viewModelScope.launch {
            val active = budgetRepository.resolveActiveBudget() ?: return@launch
            budgetRepository.deleteBudget(active)
        }
    }

    // ---- Search ----------------------------------------------------------------------

    private val _searchCriteria = MutableStateFlow(SearchCriteria())
    val searchCriteria: StateFlow<SearchCriteria> = _searchCriteria.asStateFlow()

    fun updateSearch(criteria: SearchCriteria) {
        _searchCriteria.value = criteria
    }

    fun clearSearch() {
        _searchCriteria.value = SearchCriteria()
    }

    /**
     * Search results, filtered in SQL rather than over the in-memory list.
     *
     * Debounced so holding down a key doesn't run a query per character.
     */
    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val searchResults: StateFlow<List<Transaction>> =
        combine(
            budgetRepository.observeActiveBudget(),
            _searchCriteria.debounce { if (it.query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        ) { budget, criteria -> budget to criteria }
            .flatMapLatest { (budget, criteria) ->
                if (budget == null) {
                    flowOf(emptyList())
                } else {
                    transactionDao.search(
                        budgetId = budget.id,
                        query = criteria.query.trim(),
                        categoryId = criteria.category?.id,
                        type = criteria.type?.id
                    )
                }
            }
            .flowOn(defaultDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    // ---- Recurring -------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    val recurringRules: StateFlow<List<RecurringRule>> =
        budgetRepository.observeActiveBudget()
            .flatMapLatest { budget ->
                if (budget == null) flowOf(emptyList())
                else recurringRuleDao.observeForBudget(budget.id)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun saveRecurringRule(rule: RecurringRule) {
        viewModelScope.launch {
            val active = budgetRepository.resolveActiveBudget() ?: return@launch
            recurringRuleDao.insert(rule.copy(budgetId = active.id))
            // Post it immediately if it is already due, rather than waiting for tomorrow.
            postRecurring.catchUp()
        }
    }

    fun setRecurringPaused(rule: RecurringRule, paused: Boolean) {
        viewModelScope.launch {
            recurringRuleDao.update(rule.copy(isPaused = paused))
        }
    }

    fun deleteRecurringRule(rule: RecurringRule) {
        viewModelScope.launch { recurringRuleDao.delete(rule) }
    }

    // ---- Notifications ---------------------------------------------------------------

    val notificationSettings: StateFlow<NotificationSettings> =
        notificationPreferences.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationSettings()
        )

    fun updateNotificationSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            notificationPreferences.update(settings)
            onNotificationSettingsChanged(settings)
        }
    }

    // ---- Backup ----------------------------------------------------------------------

    /** Serializes the whole database; the caller writes the bytes to the chosen file. */
    suspend fun buildBackupJson(): String = backupRepository.export()

    fun importBackup(contents: String, mode: ImportMode, onFinished: (String) -> Unit) {
        viewModelScope.launch {
            val result = backupRepository.import(contents, mode)
            onFinished(
                when (result) {
                    is ImportResult.Success -> buildString {
                        append("Imported ${result.transactions} expenses")
                        if (result.budgets > 0) append(" across ${result.budgets} budgets")
                        if (result.skipped > 0) append(", skipped ${result.skipped} duplicates")
                    }
                    is ImportResult.Failure -> result.reason.message
                }
            )
        }
    }

    /** Reads the manifest so the import dialog can say what the file holds. */
    fun previewBackup(contents: String): MemoBackup? =
        backupRepository.preview(contents).getOrNull()

    // ---- Receipt scanning ------------------------------------------------------------

    fun scanReceipt(uri: Uri) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _scanState.value = ScanState.Processing
            _scanState.value = when (val outcome = receiptScanner.scan(uri)) {
                is ScanOutcome.Success -> ScanState.Success(
                    amountCents = outcome.amountCents,
                    note = outcome.note,
                    category = outcome.category,
                    description = outcome.description,
                    dateMillis = outcome.dateMillis,
                    dateHasTime = outcome.dateHasTime
                )
                is ScanOutcome.Failure -> ScanState.Error(outcome.reason)
            }
        }
    }

    fun clearScanState() {
        _scanState.value = ScanState.Idle
    }

    /** Stops an in-flight scan (the user backed out of the progress dialog). */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanState.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MemoApplication)
                val container = application.container
                MainViewModel(
                    budgetRepository = container.budgetRepository,
                    backupRepository = container.backupRepository,
                    transactionDao = container.transactionDao,
                    recurringRuleDao = container.recurringRuleDao,
                    postRecurring = container.postRecurring,
                    notificationPreferences = container.notificationPreferences,
                    appearancePreferences = container.budgetPreferences,
                    clock = container.clock,
                    receiptScanner = container.receiptScanner,
                    dayTicker = container.dayTicker,
                    startupMigration = container.startupMigration,
                    onNotificationSettingsChanged = { settings ->
                        MemoWorkScheduler.sync(application, settings, container.clock)
                    }
                )
            }
        }
    }
}
