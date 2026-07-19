package com.imanhaikal.memo.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2ConvertsDollarAmountsToCents() {
        // createDatabase builds the v1 table from schemas/1.json
        helper.createDatabase(TEST_DB, 1).use { database ->
            database.execSQL(
                "INSERT INTO transactions (id, amount, note, date) VALUES (1, 12.34, 'Lunch', 1700000000000)"
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        ).use { database ->
            database.assertMigratedAmount(1_234L)
        }
    }

    @Test
    fun migrate2To3AddsUncategorizedDefaults() {
        helper.createDatabase(TEST_DB, 2).use { database ->
            database.execSQL(
                "INSERT INTO transactions (id, amount, note, date) VALUES (1, 1234, 'Lunch', 1700000000000)"
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query(
                "SELECT category, description FROM transactions WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals("", cursor.getString(1))
            }
        }
    }

    @Test
    fun migrateAllTheWayFrom1() {
        helper.createDatabase(TEST_DB, 1).use { database ->
            database.execSQL(
                "INSERT INTO transactions (id, amount, note, date) VALUES (1, 12.34, 'Lunch', 1700000000000)"
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
        ).use { database ->
            database.assertMigratedAmount(1_234L)
        }
    }

    private fun SupportSQLiteDatabase.assertMigratedAmount(expectedAmount: Long) {
        query("SELECT amount FROM transactions WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expectedAmount, cursor.getLong(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
