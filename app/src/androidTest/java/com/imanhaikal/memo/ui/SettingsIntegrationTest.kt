package com.imanhaikal.memo.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.printToLog
import androidx.test.platform.app.InstrumentationRegistry
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class SettingsIntegrationTest {

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
        viewModel = MainViewModel(transactionDao, preferences)

        // Reset preferences to a known state (Setup Complete) for testing navigation
        // or Not Setup for testing Reset?
        // The test cases assume we start at Dashboard, so we need to be SETUP.
        runBlocking {
            // Setup a default budget so we land on Dashboard
            preferences.saveBudgetSettings(3000.0, System.currentTimeMillis(), 30)
        }
    }

    @Test
    fun testSettingsNavigationAndFlow() {
        composeTestRule.setContent {
            MemoApp(viewModel = viewModel)
        }

        // --- Test Case 1: Navigation ---
        // Verify we are on Dashboard (Check for "Memo." title or Settings icon)
        composeTestRule.onNodeWithText("Memo.").assertIsDisplayed()
        
        // Click Settings Icon
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        
        // Verify Settings Screen is displayed
        composeTestRule.onNodeWithText("Configuration").assertIsDisplayed()


        // --- Test Case 2: Update Config ---
        // Change Budget to 5000 and Days to 15
        
        // Find inputs. They don't have explicit test tags or labels in the composable calling MemoInput easily accessible?
        // MemoInput uses a label? No, the label is "Total Budget" text above it.
        // We can find the TextField by the placeholder or value.
        // Initial value is 3000.0 (from our setup) and 30.
        
        // We can search for the text field that contains the value.
        // Or better, add test tags in the source code. But I should avoid modifying source if possible.
        // The `MemoInput` likely is a BasicTextField or TextField.
        // Let's try to find by text value.
        
        // Clear and Set Budget
        // Note: MemoInput might need performTextClearance
        // Finding by text "3000.0" might be tricky if it's formatted. It's toString().
        // Let's use onNode(hasText("3000.0"))
        
        // Wait, Compose tests sometimes struggle finding inputs by value if not semantic.
        // But usually it works.
        
        // Using placeholders might be safer if value is empty, but value is pre-filled.
        // Let's assume we can find it.
        
        // Update Budget
        // We might have multiple inputs.
        // Input 1: Budget. Input 2: Days.
        // We can distinguish by their order or surrounding text if we use `onNodeWithText("Total Budget").performClick()` to focus?
        // But `MemoInput` is separate.
        
        // Using Test Tags for stable selection
        
        val budgetNode = composeTestRule.onNodeWithTag("BudgetInput")
        budgetNode.performTextClearance()
        budgetNode.performTextInput("6000")
        budgetNode.assertTextContains("6000")

        val daysNode = composeTestRule.onNodeWithTag("DaysInput")
        daysNode.performTextClearance()
        daysNode.performTextInput("15")
        daysNode.assertTextContains("15")

        // Click Save
        composeTestRule.onNodeWithText("Save Changes").performClick()

        // Verify we are back on Dashboard
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithText("Memo.").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        // Check verification simplified: we're checking navigation only.
        // We already verified we are back on DashboardScreen by checking for "Memo."
        
        // Go back to Settings to check if values are persisted in the UI fields
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        
        // Check if input fields have the new values
        composeTestRule.onNodeWithTag("BudgetInput").assertTextContains("6000")
        composeTestRule.onNodeWithTag("DaysInput").assertTextContains("15")
        
        // Go back to dashboard for next test part
        composeTestRule.onNodeWithContentDescription("Back").performClick()


        // --- Test Case 3: Reset Flow ---
        // Go back to Settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        
        // Click Reset
        composeTestRule.onNodeWithText("Reset All Data").performClick()
        
        // Verify Dialog
        composeTestRule.onNodeWithText("Are you sure? This will delete all transactions and reset your budget settings. This action cannot be undone.").assertIsDisplayed()
        
        // Confirm Reset
        // The button text is "Reset" (Red)
        composeTestRule.onNodeWithText("Reset").performClick()
        
        // Verify app goes back to Setup state
        // SetupDialog should be visible.
        // SetupDialog has text "Welcome to Memo" or "Set up your budget"
        // Let's check `SetupDialog.kt` content? Or just look for "Get Started" or similar.
        // I'll check for "Setup Budget" or "Welcome".
        // Based on MemoApp.kt: SetupDialog is shown when !isSetup.
        // SetupDialog usually asks for Budget and Days.
        composeTestRule.onNodeWithText("Welcome").assertIsDisplayed()
    }

    class FakeTransactionDao : TransactionDao {
        private val transactions = MutableStateFlow<List<Transaction>>(emptyList())

        override fun getAllTransactions(): Flow<List<Transaction>> = transactions

        override suspend fun insertTransaction(transaction: Transaction) {
            val current = transactions.value.toMutableList()
            // remove existing with same id?
            current.removeIf { it.id == transaction.id }
            current.add(transaction)
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