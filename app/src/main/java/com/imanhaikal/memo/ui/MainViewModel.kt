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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

enum class BudgetStatus {
    ON_TRACK, CAREFUL, OVER_LIMIT
}

data class BudgetUiState(
    val isLoading: Boolean = true,
    val isSetup: Boolean = false,
    val availableToday: Double = 0.0,
    val dailyLimit: Double = 0.0,
    val daysRemaining: Int = 1,
    val transactions: List<Transaction> = emptyList(),
    val status: BudgetStatus = BudgetStatus.ON_TRACK,
    val totalBudget: Double = 0.0,
    val spentToday: Double = 0.0
)

class MainViewModel(
    private val transactionDao: TransactionDao,
    private val budgetPreferences: BudgetPreferences
) : ViewModel() {

    private val _systemTimeOverride = MutableStateFlow<Long?>(null) // For testing logic if needed, or forcing updates

    val uiState: StateFlow<BudgetUiState> = combine(
        transactionDao.getAllTransactions(),
        budgetPreferences.totalBudget,
        budgetPreferences.cycleStartDate,
        budgetPreferences.totalDays,
        _systemTimeOverride
    ) { transactions, totalBudget, cycleStartDate, totalDays, systemTimeOverride ->
        calculateBudget(transactions, totalBudget, cycleStartDate, totalDays, systemTimeOverride)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetUiState(isLoading = true)
    )

    private fun calculateBudget(
        transactions: List<Transaction>,
        totalBudget: Double,
        cycleStartDate: Long,
        totalDays: Int,
        systemTimeOverride: Long?
    ): BudgetUiState {
        // Basic Setup Check
        val isSetup = totalBudget > 0 && totalDays > 0
        if (!isSetup) {
            return BudgetUiState(
                isLoading = false,
                isSetup = false,
                transactions = transactions
            )
        }

        // Time calculations
        val zoneId = ZoneId.systemDefault()
        val now = systemTimeOverride?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        val todayDate = now.atZone(zoneId).toLocalDate()
        val startDate = Instant.ofEpochMilli(cycleStartDate).atZone(zoneId).toLocalDate()

        // Days Passed & Remaining
        // Days passed: difference between start date and today.
        // If start date is today, days passed is 0.
        val daysPassed = ChronoUnit.DAYS.between(startDate, todayDate).toInt()
        
        // Days remaining: Total days - days passed.
        // Ensure at least 1 day remaining to avoid division by zero.
        // Even on the last day (daysPassed = totalDays - 1), we have 1 day remaining.
        // If we are past the cycle (daysPassed >= totalDays), technically 0 or negative, but logic says max(1, ...)
        val daysRemaining = max(1, totalDays - daysPassed)

        // Financials
        val spentTotal = transactions.sumOf { it.amount }
        
        val spentToday = transactions.filter {
            val txDate = Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate()
            txDate.isEqual(todayDate)
        }.sumOf { it.amount }

        // Logic Replication:
        // poolStartOfDay = totalBudget - (spentTotal - spentToday)
        val poolStartOfDay = totalBudget - (spentTotal - spentToday)

        // dailyLimit = poolStartOfDay / daysRemaining
        // If poolStartOfDay is negative, dailyLimit will be negative (which is correct behavior per reqs to show 0/negative)
        // However, requirements say: "If Pool becomes negative... newDailyLimit should be 0"
        val rawDailyLimit = poolStartOfDay / daysRemaining
        val dailyLimit = if (poolStartOfDay < 0) 0.0 else rawDailyLimit

        // availableToday = dailyLimit - spentToday
        val availableToday = dailyLimit - spentToday

        // Status Determination
        // If Available Today < 0, Status = "Over Limit"
        // If Available Today < 20% of Daily Limit, Status = "Careful"
        val status = when {
            availableToday < 0 -> BudgetStatus.OVER_LIMIT
            dailyLimit > 0 && availableToday < (dailyLimit * 0.2) -> BudgetStatus.CAREFUL
            else -> BudgetStatus.ON_TRACK
        }

        return BudgetUiState(
            isLoading = false,
            isSetup = true,
            availableToday = availableToday,
            dailyLimit = dailyLimit,
            daysRemaining = daysRemaining,
            transactions = transactions,
            status = status,
            totalBudget = totalBudget,
            spentToday = spentToday
        )
    }

    fun setupBudget(amount: Double, days: Int) {
        viewModelScope.launch {
            // When setting up a new budget, we start from NOW
            val startDate = System.currentTimeMillis()
            budgetPreferences.saveBudgetSettings(amount, startDate, days)
        }
    }

    fun addTransaction(amount: Double, note: String) {
        viewModelScope.launch {
            val newTransaction = Transaction(
                amount = amount,
                note = note,
                date = System.currentTimeMillis()
            )
            transactionDao.insertTransaction(newTransaction)
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
            budgetPreferences.saveBudgetSettings(0.0, System.currentTimeMillis(), 30)
        }
    }

    @org.jetbrains.annotations.VisibleForTesting
    fun setSystemTimeOverride(time: Long) {
        _systemTimeOverride.value = time
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MemoApplication)
                MainViewModel(
                    transactionDao = application.container.transactionDao,
                    budgetPreferences = application.container.budgetPreferences
                )
            }
        }
    }
}