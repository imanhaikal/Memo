package com.imanhaikal.memo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.ui.components.MemoFab
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.ui.components.TransactionItem
import com.imanhaikal.memo.ui.theme.MemoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5PolishTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testFabInteraction() {
        var clicked = false
        composeTestRule.setContent {
            MemoTheme {
                // Wrap in a Surface to ensure correct content color and background
                androidx.compose.material3.Surface {
                    MemoFab(onClick = { clicked = true })
                }
            }
        }

        // Verify FAB is displayed
        // Use useUnmergedTree = true to find nodes inside other nodes (like buttons)
        composeTestRule.onNodeWithText("Add Expense", useUnmergedTree = true).assertIsDisplayed()
        
        // Verify Click (Triggers Haptics + Animation internally)
        composeTestRule.onNodeWithText("Add Expense", useUnmergedTree = true).performClick()
        
        assert(clicked) { "FAB onClick should be triggered" }
    }

    @Test
    fun testMemoInputFocus() {
        composeTestRule.setContent {
            MemoTheme {
                androidx.compose.material3.Surface {
                    MemoInput(
                        value = "",
                        onValueChange = {},
                        label = "Test Input"
                    )
                }
            }
        }

        // Find the text field using semantics (it has a set text action)
        val textField = composeTestRule.onNode(hasSetTextAction())
        
        textField.assertIsDisplayed()
        textField.assertIsNotFocused()
        
        // Perform click to focus (Triggers Border Animation)
        textField.performClick()
        
        textField.assertIsFocused()
    }

    @Test
    fun testTransactionItemInteraction() {
        val sampleTransaction = Transaction(
            id = 1,
            amount = 12.50,
            note = "Coffee",
            date = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            MemoTheme {
                androidx.compose.material3.Surface {
                    TransactionItem(transaction = sampleTransaction)
                }
            }
        }

        // Verify Item is displayed
        composeTestRule.onNodeWithText("Coffee").assertIsDisplayed()
        
        // Verify Click (Triggers Haptics internally)
        composeTestRule.onNodeWithText("Coffee").performClick()
    }
}