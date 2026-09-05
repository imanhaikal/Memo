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
    fun migrate3To4DefaultsHasTimeToTrue() {
        helper.createDatabase(TEST_DB, 3).use { database ->
            database.execSQL(
                "INSERT INTO transactions (id, amount, note, date, description) VALUES (1, 1234, 'Lunch', 1700000000000, '')"
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            AppDatabase.MIGRATION_3_4,
        ).use { database ->
            database.query("SELECT hasTime FROM transactions WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate4To5AssignsEveryRowToTheDefaultBudget() {
        helper.createDatabase(TEST_DB, 4).use { database ->
            database.execSQL(
                "INSERT INTO transactions (id, amount, note, date, category, description, hasTime) " +
                    "VALUES (1, 1234, 'Lunch', 1700000000000, 'food', 'Nasi lemak', 1)"
            )
            database.execSQL(
                "INSERT INTO transactions (id, amount, note, date, category, description, hasTime) " +
                    "VALUES (2, 500, 'Bus', 1700000100000, NULL, '', 0)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).use { database ->
            // The placeholder budget must exist before anything points at it.
            database.query("SELECT COUNT(*) FROM budgets WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }

            database.query(
                "SELECT budgetId, type, recurringRuleId FROM transactions ORDER BY id"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals("expense", cursor.getString(1))
                assertTrue(cursor.isNull(2))

                assertTrue(cursor.moveToNext())
                assertEquals(1L, cursor.getLong(0))
                assertEquals("expense", cursor.getString(1))
            }

            // The pre-existing columns must survive untouched.
            database.query(
                "SELECT amount, note, category, description, hasTime FROM transactions WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1_234L, cursor.getLong(0))
                assertEquals("Lunch", cursor.getString(1))
                assertEquals("food", cursor.getString(2))
                assertEquals("Nasi lemak", cursor.getString(3))
                assertEquals(1, cursor.getInt(4))
            }
        }
    }

    @Test
    fun migrate4To5PreservesEveryRow() {
        val rowCount = 200
        helper.createDatabase(TEST_DB, 4).use { database ->
            repeat(rowCount) { index ->
                database.execSQL(
                    "INSERT INTO transactions (amount, note, date, category, description, hasTime) " +
                        "VALUES (${index + 1}, 'Row $index', ${1_700_000_000_000L + index}, NULL, '', 1)"
                )
            }
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).use { database ->
            database.query("SELECT COUNT(*), SUM(amount) FROM transactions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(rowCount, cursor.getInt(0))
                // 1 + 2 + ... + 200
                assertEquals((rowCount * (rowCount + 1) / 2).toLong(), cursor.getLong(1))
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
            5,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
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
