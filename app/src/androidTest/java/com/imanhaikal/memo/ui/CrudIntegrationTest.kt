package com.imanhaikal.memo.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger

class CrudIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var preferences: BudgetPreferences
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = BudgetPreferences(context)
        transactionDao = FakeTransactionDao()
        viewModel = MainViewModel(
            transactionDao,
            preferences,
            Clock.systemDefaultZone(),
            (context.applicationContext as com.imanhaikal.memo.MemoApplication).container.receiptScanner
        )

        runBlocking {
            // Setup budget so we are on Dashboard
            preferences.saveBudgetSettings(300_000L, System.currentTimeMillis(), 30)
        }

        composeTestRule.setContent {
            MemoApp(viewModel = viewModel)
        }

        // Wait for Dashboard to settle
        composeTestRule.onNodeWithText("Memo.").assertIsDisplayed()
    }

    @After
    fun tearDown() {
        runBlocking {
            preferences.saveBudgetSettings(0L, System.currentTimeMillis(), 30)
        }
    }

    @Test
    fun verifyEditTransactionFlow() {
        // 1. Add a transaction
        val note = "Test Item Edit"
        val amount = 12_300L
        viewModel.addTransaction(amount, note)

        // Wait for item to appear
        composeTestRule.waitUntil {
            transactionDao.getTransactionsBlocking().any { it.note == note }
        }
        
        // Wait for UI to catch up
        composeTestRule.onNodeWithText(note).assertIsDisplayed()

        // 2. Tap on transaction in the list
        composeTestRule.onNodeWithText(note).performClick()

        // 3. Verify Edit Dialog
        composeTestRule.onNodeWithText("Edit Expense").assertIsDisplayed()

        // 4. Change amount
        // Use hasSetTextAction to ensure we target the input field, not the static text in the list (if visible)
        val amountStr = "123"
        composeTestRule.onNode(hasText(amountStr) and hasSetTextAction()).performTextClearance()
        // After clearance, placeholder "0.00" should be visible
        composeTestRule.onNode(hasText("0.00") and hasSetTextAction()).performTextInput("456")

        // 5. Change note
        composeTestRule.onNode(hasText(note) and hasSetTextAction()).performTextClearance()
        // After clearance, placeholder "e.g. Lunch" should be visible
        composeTestRule.onNode(hasText("e.g. Lunch") and hasSetTextAction()).performTextInput("Updated Item")

        // 6. Save
        composeTestRule.onNodeWithText("Save").performClick()

        // 7. Verify list updates
        // Wait for dialog to close and list to update
        composeTestRule.waitUntil {
            transactionDao.getTransactionsBlocking().any { it.note == "Updated Item" }
        }
        
        composeTestRule.onNodeWithText("Updated Item").assertIsDisplayed()
        // Format through the same utility the list uses (default currency is MYR).
        // Both the day-group header total and the row show the amount, so match all.
        val expectedAmount = com.imanhaikal.memo.utils.CurrencyUtils.formatCurrency(45_600L, "MYR")
        composeTestRule.onAllNodesWithText(expectedAmount).onFirst().assertIsDisplayed()
    }

    @Test
    fun verifyDeleteFromDialogFlow() {
        val note = "Delete Me Dialog"
        viewModel.addTransaction(5_000L, note)

        composeTestRule.waitUntil {
            transactionDao.getTransactionsBlocking().any { it.note == note }
        }
        composeTestRule.onNodeWithText(note).assertIsDisplayed()

        // Open Dialog
        composeTestRule.onNodeWithText(note).performClick()

        // Click Delete icon button
        // The delete button in AddExpenseDialog has contentDescription "Delete"
        composeTestRule.onNodeWithContentDescription("Delete").performClick()

        // Deletes are immediate; the undo snackbar is the safety net
        composeTestRule.waitUntil {
            transactionDao.getTransactionsBlocking().none { it.note == note }
        }
        composeTestRule.onNodeWithText(note).assertDoesNotExist()
        composeTestRule.onNodeWithText("Expense deleted").assertIsDisplayed()
    }

    @Test
    fun verifySwipeToDeleteFlow() {
        val note = "Swipe Me"
        viewModel.addTransaction(7_500L, note)

        composeTestRule.waitUntil {
            transactionDao.getTransactionsBlocking().any { it.note == note }
        }
        composeTestRule.onNodeWithText(note).assertIsDisplayed()

        // Swipe Right to Left
        composeTestRule.onNodeWithText(note).performTouchInput {
            swipeLeft()
        }

        // The swipe itself commits the delete — no confirmation dialog
        composeTestRule.waitUntil {
            transactionDao.getTransactionsBlocking().none { it.note == note }
        }
        composeTestRule.onNodeWithText(note).assertDoesNotExist()
        composeTestRule.onNodeWithText("Expense deleted").assertIsDisplayed()
    }

    @Test
    fun verifyUndoRestoresTransaction() {
        val note = "Undo Me"
        viewModel.addTransaction(4_200L, note)

        composeTestRule.waitUntil {
            transactionDao.getTransactionsBlocking().any { it.note == note }
        }
        composeTestRule.onNodeWithText(note).assertIsDisplayed()

        composeTestRule.onNodeWithText(note).performTouchInput {
            swipeLeft()
        }

        composeTestRule.waitUntil {
            transactionDao.getTransactionsBlocking().none { it.note == note }
        }

        // Undo from the snackbar brings the row back with its original data
        composeTestRule.onNodeWithText("Undo").performClick()

        composeTestRule.waitUntil {
            transactionDao.getTransactionsBlocking().any { it.note == note }
        }
        composeTestRule.onNodeWithText(note).assertIsDisplayed()
    }

    class FakeTransactionDao : TransactionDao {
        private val transactions = MutableStateFlow<List<Transaction>>(emptyList())
        private val idCounter = AtomicInteger(0)

        override fun getAllTransactions(): Flow<List<Transaction>> = transactions

        fun getTransactionsBlocking(): List<Transaction> = transactions.value

        override suspend fun insertTransaction(transaction: Transaction) {
            val current = transactions.value.toMutableList()
            if (transaction.id == 0) {
                val newTx = transaction.copy(id = idCounter.incrementAndGet())
                current.add(newTx)
            } else {
                current.removeIf { it.id == transaction.id }
                current.add(transaction)
            }
            transactions.value = current
        }

        override suspend fun deleteTransaction(transaction: Transaction) {
            val current = transactions.value.toMutableList()
            current.removeIf { it.id == transaction.id }
            transactions.value = current
        }

        override suspend fun deleteAllTransactions() {
            transactions.value = emptyList()
        }
    }
}
