package com.imanhaikal.memo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.imanhaikal.memo.MemoApplication
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.domain.BudgetCalculatorUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

enum class BudgetStatus {
    ON_TRACK, CAREFUL, OVER_LIMIT
}

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
    val totalDays: Int = 30,
    val currencyCode: String = "USD"
)

class MainViewModel(
    private val transactionDao: TransactionDao,
    private val budgetPreferences: BudgetPreferences,
    private val clock: Clock,
    private val budgetCalculator: BudgetCalculatorUseCase = BudgetCalculatorUseCase(clock)
) : ViewModel() {

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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetUiState(isLoading = true)
    )

    fun setupBudget(amountCents: Long, days: Int, currency: String = "USD") {
        viewModelScope.launch {
            budgetPreferences.saveBudgetSettings(amountCents, clock.millis(), days, currency)
        }
    }

    fun updateBudget(amountCents: Long, days: Int, currency: String) {
        viewModelScope.launch {
            budgetPreferences.updateBudgetConfig(amountCents, days, currency)
        }
    }

    fun addTransaction(amountCents: Long, note: String) {
        viewModelScope.launch {
            val newTransaction = Transaction(
                amount = amountCents,
                note = note,
                date = clock.millis()
            )
            transactionDao.insertTransaction(newTransaction)
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
                    clock = application.container.clock
                )
            }
        }
    }
}
