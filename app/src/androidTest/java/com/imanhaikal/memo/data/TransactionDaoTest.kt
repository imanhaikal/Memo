package com.imanhaikal.memo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {
    private lateinit var transactionDao: TransactionDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).allowMainThreadQueries() // Allow main thread queries for testing
        .build()
        transactionDao = db.transactionDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveTransaction() = runBlocking {
        val transaction = Transaction(amount = 100.0, note = "Test", date = System.currentTimeMillis())
        transactionDao.insertTransaction(transaction)
        
        val transactions = transactionDao.getAllTransactions().first()
        assertEquals(1, transactions.size)
        assertEquals(100.0, transactions[0].amount, 0.0)
        assertEquals("Test", transactions[0].note)
    }

    @Test
    fun deleteTransaction() = runBlocking {
        val transaction = Transaction(id = 1, amount = 100.0, note = "Test", date = System.currentTimeMillis())
        transactionDao.insertTransaction(transaction)
        
        var transactions = transactionDao.getAllTransactions().first()
        assertEquals(1, transactions.size)
        
        // We need to retrieve the actual inserted item to get the ID if auto-generated, 
        // but here we set ID to 1 manually or rely on Room to ignore it if 0 and autogenerate.
        // The Entity definition in Transaction.kt usually has autoGenerate = true and default 0.
        // Let's check the retrieved item to be sure we are deleting the right one.
        val insertedTransaction = transactions[0]
        
        transactionDao.deleteTransaction(insertedTransaction)
        
        transactions = transactionDao.getAllTransactions().first()
        assertTrue(transactions.isEmpty())
    }

    @Test
    fun verifyDateOrdering() = runBlocking {
        val now = System.currentTimeMillis()
        val older = now - 10000
        val newer = now + 10000
        
        val t1 = Transaction(amount = 10.0, note = "Older", date = older)
        val t2 = Transaction(amount = 20.0, note = "Newer", date = newer)
        val t3 = Transaction(amount = 30.0, note = "Now", date = now)

        transactionDao.insertTransaction(t1)
        transactionDao.insertTransaction(t2)
        transactionDao.insertTransaction(t3)

        val transactions = transactionDao.getAllTransactions().first()
        
        // Expecting ORDER BY date DESC (Newer first)
        assertEquals(3, transactions.size)
        assertEquals("Newer", transactions[0].note)
        assertEquals("Now", transactions[1].note)
        assertEquals("Older", transactions[2].note)
    }
}