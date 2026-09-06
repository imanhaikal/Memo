package com.imanhaikal.memo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Transaction::class,
        Budget::class,
        BudgetCycle::class,
        CategoryCap::class,
        RecurringRule::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun budgetCycleDao(): BudgetCycleDao
    abstract fun categoryCapDao(): CategoryCapDao
    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        /**
         * The budget every pre-v5 transaction is assigned to. The row is created empty by
         * the migration and filled in from DataStore by [BudgetBootstrap], because a Room
         * migration runs on raw SQLite and cannot read DataStore.
         */
        const val DEFAULT_BUDGET_ID = 1L

        @Volatile
        private var Instance: AppDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        date INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO transactions_new (id, amount, note, date)
                    SELECT id, CAST(ROUND(amount * 100) AS INTEGER), note, date
                    FROM transactions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN hasTime INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Multi-budget foundation: budgets own transactions, cycles record history, caps
         * limit categories, and rules post recurring entries. Existing rows are assigned
         * to [DEFAULT_BUDGET_ID], whose real values arrive from [BudgetBootstrap].
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budgets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `totalDays` INTEGER NOT NULL,
                        `currencyCode` TEXT NOT NULL,
                        `firstCycleStartDate` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget_cycles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `budgetId` INTEGER NOT NULL,
                        `cycleIndex` INTEGER NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `endDateExclusive` INTEGER NOT NULL,
                        `budgetAmountCents` INTEGER NOT NULL,
                        `closedAt` INTEGER,
                        FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_budget_cycles_budgetId_cycleIndex` " +
                        "ON `budget_cycles` (`budgetId`, `cycleIndex`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `category_caps` (
                        `budgetId` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `capCents` INTEGER NOT NULL,
                        PRIMARY KEY(`budgetId`, `category`),
                        FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurring_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `budgetId` INTEGER NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `category` TEXT,
                        `type` TEXT NOT NULL,
                        `cadence` TEXT NOT NULL,
                        `intervalCount` INTEGER NOT NULL DEFAULT 1,
                        `startDate` INTEGER NOT NULL,
                        `endDate` INTEGER,
                        `nextDueDate` INTEGER NOT NULL,
                        `isPaused` INTEGER NOT NULL DEFAULT 0,
                        `lastPostedAt` INTEGER,
                        FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_rules_budgetId` " +
                        "ON `recurring_rules` (`budgetId`)"
                )

                // Placeholder so the DEFAULT below points at a real row from the first
                // moment. BudgetBootstrap replaces these values with the user's own.
                db.execSQL(
                    """
                    INSERT INTO `budgets`
                        (`id`, `name`, `amountCents`, `totalDays`, `currencyCode`,
                         `firstCycleStartDate`, `isArchived`, `createdAt`, `sortOrder`)
                    VALUES ($DEFAULT_BUDGET_ID, 'Budget', 0, 30, 'MYR', 0, 0, 0, 0)
                    """.trimIndent()
                )

                db.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `budgetId` INTEGER NOT NULL " +
                        "DEFAULT $DEFAULT_BUDGET_ID"
                )
                db.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'expense'"
                )
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `recurringRuleId` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_budgetId_date` " +
                        "ON `transactions` (`budgetId`, `date`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "memo_database")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
