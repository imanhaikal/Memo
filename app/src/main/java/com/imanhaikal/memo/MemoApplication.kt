package com.imanhaikal.memo

import android.app.Application
import com.imanhaikal.memo.data.AppDatabase
import com.imanhaikal.memo.data.BudgetBootstrap
import com.imanhaikal.memo.data.BudgetCycleDao
import com.imanhaikal.memo.data.BudgetDao
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.BudgetRepository
import com.imanhaikal.memo.data.CategoryCapDao
import com.imanhaikal.memo.data.NotificationPreferences
import com.imanhaikal.memo.data.NotificationPreferencesStore
import com.imanhaikal.memo.data.PreUpgradeSnapshot
import com.imanhaikal.memo.data.RecurringRuleDao
import com.imanhaikal.memo.data.RoomTransactionRunner
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.backup.BackupRepository
import com.imanhaikal.memo.data.receipt.GeminiReceiptScanner
import com.imanhaikal.memo.data.receipt.GeminiReceiptService
import com.imanhaikal.memo.data.receipt.ReceiptScanner
import com.imanhaikal.memo.domain.BudgetCalculatorUseCase
import com.imanhaikal.memo.domain.BudgetSummaryProvider
import com.imanhaikal.memo.domain.CycleRolloverUseCase
import com.imanhaikal.memo.domain.PostRecurringUseCase
import com.imanhaikal.memo.domain.RecurringScheduleCalculator
import com.imanhaikal.memo.domain.DayTicker
import com.imanhaikal.memo.domain.SystemDayTicker
import com.imanhaikal.memo.notifications.MemoNotifications
import com.imanhaikal.memo.notifications.OverLimitNotifier
import com.imanhaikal.memo.widget.WidgetUpdater
import com.imanhaikal.memo.work.MemoWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
        MemoNotifications.createChannels(this)

        // Keeps the home-screen widget in step with the app: any transaction write, a
        // budget switch, or the day rolling over changes the number it shows.
        container.applicationScope.launch {
            container.startupMigration.await()
            combine(
                container.transactionDao.getAllTransactions(),
                container.budgetRepository.observeActiveBudget(),
                container.dayTicker.today
            ) { _, _, today -> today }
                .collect { today ->
                    WidgetUpdater.refresh(this@MemoApplication)
                    // Never let a notification failure take the widget refresh down with it.
                    runCatching { container.overLimitNotifier.checkAndNotify(today) }
                }
        }

        container.applicationScope.launch {
            container.startupMigration.await()
            // The migration ran and the database opened, so the pre-upgrade copy has done
            // its job. Without this every upgraded install keeps a permanent duplicate.
            PreUpgradeSnapshot.discard(this@MemoApplication)
            // The worker is the convenience; this is the guarantee. Vendor battery
            // managers suppress background work for days, and a rent expense that
            // silently never posts is a data-integrity bug, not a missed reminder.
            runCatching { container.postRecurring.catchUp() }
            runCatching {
                MemoWorkScheduler.sync(
                    context = this@MemoApplication,
                    settings = container.notificationPreferences.current(),
                    clock = container.clock
                )
            }
        }
    }
}

interface AppContainer {
    val transactionDao: TransactionDao
    val recurringRuleDao: RecurringRuleDao
    val budgetRepository: BudgetRepository
    val backupRepository: BackupRepository
    val notificationPreferences: NotificationPreferencesStore
    val postRecurring: PostRecurringUseCase
    val budgetSummaryProvider: BudgetSummaryProvider
    val overLimitNotifier: OverLimitNotifier
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

    private val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }

    override val transactionDao: TransactionDao by lazy { database.transactionDao() }

    private val budgetDao: BudgetDao by lazy { database.budgetDao() }

    private val budgetCycleDao: BudgetCycleDao by lazy { database.budgetCycleDao() }

    private val categoryCapDao: CategoryCapDao by lazy { database.categoryCapDao() }

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

    override val backupRepository: BackupRepository by lazy {
        BackupRepository(
            runInTransaction = RoomTransactionRunner(database),
            budgetDao = budgetDao,
            budgetCycleDao = budgetCycleDao,
            categoryCapDao = categoryCapDao,
            recurringRuleDao = recurringRuleDao,
            transactionDao = transactionDao,
            activeBudgetStore = budgetPreferences,
            clock = clock,
            appVersionCode = BuildConfig.VERSION_CODE
        )
    }

    override val notificationPreferences: NotificationPreferencesStore by lazy {
        NotificationPreferences(context)
    }

    override val postRecurring: PostRecurringUseCase by lazy {
        PostRecurringUseCase(
            recurringRuleDao = recurringRuleDao,
            transactionDao = transactionDao,
            runInTransaction = RoomTransactionRunner(database),
            calculator = RecurringScheduleCalculator(),
            clock = clock
        )
    }

    override val budgetSummaryProvider: BudgetSummaryProvider by lazy {
        BudgetSummaryProvider(
            budgetRepository = budgetRepository,
            transactionDao = transactionDao,
            calculator = BudgetCalculatorUseCase(clock.zone)
        )
    }

    override val overLimitNotifier: OverLimitNotifier by lazy {
        OverLimitNotifier(
            summaryProvider = budgetSummaryProvider,
            preferences = notificationPreferences,
            clock = clock,
            notify = { text -> MemoNotifications.showOverLimit(context, text) }
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
            service = GeminiReceiptService(apiKey = BuildConfig.GEMINI_API_KEY, clock = clock)
        )
    }
}
