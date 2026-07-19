package com.imanhaikal.memo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Transaction::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
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

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "memo_database")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
