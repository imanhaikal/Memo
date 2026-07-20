package com.imanhaikal.memo.ui

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.imanhaikal.memo.MemoApplication
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.ThemeMode
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.receipt.ReceiptScanner
import com.imanhaikal.memo.data.receipt.ScanFailureReason
import com.imanhaikal.memo.data.receipt.ScanOutcome
import com.imanhaikal.memo.domain.BudgetCalculatorUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

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
    val totalCents: Long
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
    val spentToday: Long = 0L,
    val spentThisCycle: Long = 0L,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    /** First day of the active (rolled-forward) cycle; null until setup completes. */
    val cycleStartDate: java.time.LocalDate? = null,
    val totalDays: Int = 30,
    val currencyCode: String = "MYR"
)

class MainViewModel(
    private val transactionDao: TransactionDao,
    private val budgetPreferences: BudgetPreferences,
    private val clock: Clock,
    private val receiptScanner: ReceiptScanner,
    private val budgetCalculator: BudgetCalculatorUseCase = BudgetCalculatorUseCase(clock),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private var scanJob: kotlinx.coroutines.Job? = null

    val themeMode: StateFlow<ThemeMode> = budgetPreferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            budgetPreferences.setThemeMode(mode)
        }
    }

    val isScanAvailable: Boolean get() = receiptScanner.isAvailable

    val uiState: StateFlow<BudgetUiState> = combine(
        transactionDao.getAllTransactions(),
        budgetPreferences.budgetConfig
    ) { transactions, config ->
        budgetCalculator.calculate(
            transactions = transactions,
            totalBudgetCents = config.totalBudgetCents,
            cycleStartDateMillis = config.cycleStartDateMillis,
            totalDays = config.totalDays,
            currencyCode = config.currencyCode
        )
        // The calculation is O(n) over all transactions with per-item date
        // conversions; keep it off the main thread.
    }.flowOn(defaultDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetUiState(isLoading = true)
    )

    fun setupBudget(amountCents: Long, days: Int, currency: String = "MYR") {
        viewModelScope.launch {
            budgetPreferences.saveBudgetSettings(amountCents, clock.millis(), days, currency)
        }
    }

    fun updateBudget(amountCents: Long, days: Int, currency: String) {
        viewModelScope.launch {
            budgetPreferences.updateBudgetConfig(amountCents, days, currency)
        }
    }

    fun addTransaction(
        amountCents: Long,
        note: String,
        dateMillis: Long? = null,
        category: Category? = null,
        description: String = "",
        hasTime: Boolean = true
    ) {
        viewModelScope.launch {
            val newTransaction = Transaction(
                amount = amountCents,
                note = note,
                date = dateMillis ?: clock.millis(),
                category = category,
                description = description,
                hasTime = hasTime
            )
            transactionDao.insertTransaction(newTransaction)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.insertTransaction(transaction)
        }
    }

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

    fun resetBudget() {
        viewModelScope.launch {
            // Clear transactions
            transactionDao.deleteAllTransactions()
            // Reset preferences (setting budget to 0 effectively un-sets it based on our isSetup logic)
            budgetPreferences.saveBudgetSettings(0L, clock.millis(), 30)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MemoApplication)
                MainViewModel(
                    transactionDao = application.container.transactionDao,
                    budgetPreferences = application.container.budgetPreferences,
                    clock = application.container.clock,
                    receiptScanner = application.container.receiptScanner
                )
            }
        }
    }
}
