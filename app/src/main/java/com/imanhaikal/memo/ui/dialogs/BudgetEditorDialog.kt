package com.imanhaikal.memo.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.components.CurrencyPicker
import com.imanhaikal.memo.ui.components.MemoDialog
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.autoFocusOnceAttached
import com.imanhaikal.memo.utils.rememberStrongHaptics

/**
 * Creates or renames a budget. [nameOnly] hides the amount, length and currency fields —
 * renaming an existing budget shouldn't invite an accidental change to its money.
 */
@Composable
fun BudgetEditorDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, amountCents: Long, days: Int, currency: String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialAmountCents: Long? = null,
    initialDays: Int = 30,
    initialCurrency: String = "MYR",
    nameOnly: Boolean = false
) {
    var nameText by rememberSaveable { mutableStateOf(initialName) }
    var amountText by rememberSaveable {
        mutableStateOf(initialAmountCents?.let { CurrencyUtils.formatAmountInput(it) } ?: "")
    }
    var daysText by rememberSaveable { mutableStateOf(initialDays.toString()) }
    var currency by rememberSaveable { mutableStateOf(initialCurrency) }

    val amountCents = CurrencyUtils.parseAmountToCents(amountText)
    val days = daysText.toIntOrNull()
    val trimmedName = nameText.trim()
    val isValid = if (nameOnly) {
        trimmedName.isNotEmpty()
    } else {
        trimmedName.isNotEmpty() && amountCents != null && days != null && days > 0
    }

    val haptic = rememberStrongHaptics()
    val nameFocusRequester = remember { FocusRequester() }

    MemoDialog(onDismissRequest = onDismiss) { close ->
        val submit = {
            if (isValid) {
                haptic.success()
                close {
                    onConfirm(
                        trimmedName,
                        amountCents ?: initialAmountCents ?: 0L,
                        days ?: initialDays,
                        currency
                    )
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(20.dp))

        MemoInput(
            value = nameText,
            onValueChange = { nameText = it.take(NAME_MAX_CHARS) },
            label = "Name",
            placeholder = "e.g. Travel",
            keyboardOptions = KeyboardOptions(
                imeAction = if (nameOnly) ImeAction.Done else ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .autoFocusOnceAttached(nameFocusRequester)
        )

        if (!nameOnly) {
            Spacer(modifier = Modifier.height(12.dp))

            MemoInput(
                value = amountText,
                onValueChange = {
                    if (CurrencyUtils.isValidAmountInput(it)) amountText = it
                },
                label = "Total Budget",
                placeholder = "e.g. 1000",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (amountText.isNotEmpty() && amountCents == null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter an amount greater than zero",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.Red,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            MemoInput(
                value = daysText,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 4) daysText = input
                },
                label = "Cycle Length (Days)",
                placeholder = "e.g. 30",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (daysText.isNotEmpty() && (days == null || days <= 0)) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter at least 1 day",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.Red,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            CurrencyPicker(
                selectedCurrency = currency,
                onCurrencySelected = { currency = it },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = { close(onDismiss) }) {
                Text("Cancel", color = AppColors.TextSecondary)
            }
            Button(
                onClick = submit,
                enabled = isValid,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Yellow,
                    contentColor = AppColors.OnYellow,
                    disabledContainerColor = AppColors.Disabled,
                    disabledContentColor = AppColors.OnDisabled
                )
            ) {
                Text(text = confirmLabel)
            }
        }
    }
}

private const val NAME_MAX_CHARS = 40
