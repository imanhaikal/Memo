package com.imanhaikal.memo

import android.app.Application
import com.imanhaikal.memo.data.AppDatabase
import com.imanhaikal.memo.data.BudgetBootstrap
import com.imanhaikal.memo.data.BudgetCycleDao
import com.imanhaikal.memo.data.BudgetDao
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.BudgetRepository
import com.imanhaikal.memo.data.CategoryCapDao
import com.imanhaikal.memo.data.PreUpgradeSnapshot
import com.imanhaikal.memo.data.RecurringRuleDao
import com.imanhaikal.memo.data.RoomTransactionRunner
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.receipt.GeminiReceiptScanner
import com.imanhaikal.memo.data.receipt.GeminiReceiptService
import com.imanhaikal.memo.data.receipt.ReceiptScanner
import com.imanhaikal.memo.domain.CycleRolloverUseCase
import com.imanhaikal.memo.domain.DayTicker
import com.imanhaikal.memo.domain.SystemDayTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.time.Clock

class MemoApplication : Application() {

    // AppContainer instance used by the rest of classes to obtain dependencies
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        // Before anything can open the database and trigger MIGRATION_4_5.
        PreUpgradeSnapshot.captureIfNeeded(this)
        container = DefaultAppContainer(this)
        container.startupMigration.start()
    }
}

interface AppContainer {
    val database: AppDatabase
    val transactionDao: TransactionDao
    val budgetDao: BudgetDao
    val budgetCycleDao: BudgetCycleDao
    val categoryCapDao: CategoryCapDao
    val recurringRuleDao: RecurringRuleDao
    val budgetRepository: BudgetRepository
    val budgetPreferences: BudgetPreferences
    val clock: Clock
    val receiptScanner: ReceiptScanner
    val dayTicker: DayTicker
    val applicationScope: CoroutineScope

    /**
     * Completes once the pre-v5 DataStore budget has been copied into Room. The UI must
     * await this before deciding whether the user has a budget — otherwise an upgrading
     * user is shown the setup dialog over their own transaction history.
     */
    val startupMigration: Deferred<Unit>
}

class DefaultAppContainer(private val context: Application) : AppContainer {

    override val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }

    override val transactionDao: TransactionDao by lazy { database.transactionDao() }

    override val budgetDao: BudgetDao by lazy { database.budgetDao() }

    override val budgetCycleDao: BudgetCycleDao by lazy { database.budgetCycleDao() }

    override val categoryCapDao: CategoryCapDao by lazy { database.categoryCapDao() }

    override val recurringRuleDao: RecurringRuleDao by lazy { database.recurringRuleDao() }

    override val budgetPreferences: BudgetPreferences by lazy {
        BudgetPreferences(context)
    }

    override val clock: Clock by lazy {
        Clock.systemDefaultZone()
    }

    override val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override val dayTicker: DayTicker by lazy {
        SystemDayTicker(context, clock)
    }

    private val cycleRollover: CycleRolloverUseCase by lazy {
        CycleRolloverUseCase(budgetCycleDao, clock)
    }

    override val budgetRepository: BudgetRepository by lazy {
        BudgetRepository(
            runInTransaction = RoomTransactionRunner(database),
            budgetDao = budgetDao,
            budgetCycleDao = budgetCycleDao,
            categoryCapDao = categoryCapDao,
            transactionDao = transactionDao,
            preferences = budgetPreferences,
            cycleRollover = cycleRollover,
            clock = clock
        )
    }

    override val startupMigration: Deferred<Unit> by lazy {
        applicationScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            BudgetBootstrap(
                budgetDao = budgetDao,
                budgetCycleDao = budgetCycleDao,
                preferences = budgetPreferences,
                clock = clock
            ).run()
        }
    }

    override val receiptScanner: ReceiptScanner by lazy {
        GeminiReceiptScanner(
            contentResolver = context.contentResolver,
            service = GeminiReceiptService(apiKey = BuildConfig.GEMINI_API_KEY)
        )
    }
}
