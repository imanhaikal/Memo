package com.imanhaikal.memo.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.ui.components.MemoDialog
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.rememberStrongHaptics

/**
 * Confirms deleting a budget. Names the consequence explicitly, because deleting a budget
 * also deletes every transaction recorded against it.
 */
@Composable
fun DeleteBudgetDialog(
    budget: Budget,
    isLast: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberStrongHaptics()

    MemoDialog(onDismissRequest = onDismiss) { close ->
        Text(
            text = "Delete \"${budget.name}\"?",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = buildString {
                append("This permanently deletes the budget, every expense recorded ")
                append("against it, and its cycle history. This can't be undone.")
                if (isLast) {
                    append("\n\nIt's your only budget, so you'll be asked to set up a new one.")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = { close(onDismiss) }) {
                Text("Cancel", color = AppColors.TextPrimary)
            }
            Button(
                onClick = {
                    haptic.error()
                    close(onConfirm)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.RedSubtle,
                    contentColor = AppColors.Red
                )
            ) {
                Text(text = "Delete")
            }
        }
    }
}
