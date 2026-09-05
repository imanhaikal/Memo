package com.imanhaikal.memo.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.data.Cadence
import com.imanhaikal.memo.data.RecurringRule
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.ui.components.MemoDialog
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.autoFocusOnceAttached
import com.imanhaikal.memo.utils.rememberStrongHaptics
import java.time.LocalDate

/**
 * Creates or edits a repeating entry.
 *
 * A new rule's first occurrence is today, so adding "rent, monthly" on the day rent is due
 * records it immediately rather than waiting a whole month to do anything.
 */
@Composable
fun RecurringRuleDialog(
    existing: RecurringRule?,
    onConfirm: (RecurringRule) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.amountCents?.let(CurrencyUtils::formatAmountInput) ?: "")
    }
    var noteText by rememberSaveable(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var cadence by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.cadence ?: Cadence.MONTHLY)
    }
    var type by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.type ?: TransactionType.EXPENSE)
    }

    val amountCents = CurrencyUtils.parseAmountToCents(amountText)
    val isValid = amountCents != null && noteText.isNotBlank()

    val haptic = rememberStrongHaptics()
    val amountFocusRequester = remember { FocusRequester() }

    MemoDialog(onDismissRequest = onDismiss) { close ->
        val submit = {
            if (amountCents != null && noteText.isNotBlank()) {
                haptic.success()
                val today = LocalDate.now()
                close {
                    onConfirm(
                        existing?.copy(
                            amountCents = amountCents,
                            note = noteText.trim(),
                            cadence = cadence,
                            type = type
                        ) ?: RecurringRule(
                            budgetId = 0L, // stamped with the active budget on save
                            amountCents = amountCents,
                            note = noteText.trim(),
                            type = type,
                            cadence = cadence,
                            startDate = today.toEpochDay(),
                            nextDueDate = today.toEpochDay()
                        )
                    )
                }
            }
        }

        Text(
            text = if (existing == null) "New recurring" else "Edit recurring",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        SegmentedRow(
            options = TransactionType.entries.map {
                it to if (it == TransactionType.INCOME) "Income" else "Expense"
            },
            selected = type,
            onSelected = {
                if (it != type) haptic.tick()
                type = it
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        MemoInput(
            value = amountText,
            onValueChange = { if (CurrencyUtils.isValidAmountInput(it)) amountText = it },
            label = "Amount",
            placeholder = "0.00",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .autoFocusOnceAttached(amountFocusRequester)
        )

        Spacer(modifier = Modifier.height(12.dp))

        MemoInput(
            value = noteText,
            onValueChange = { noteText = it.take(NOTE_MAX_CHARS) },
            label = "What is it",
            placeholder = "e.g. Rent",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        SegmentedRow(
            options = Cadence.entries.map { it to it.label },
            selected = cadence,
            onSelected = {
                if (it != cadence) haptic.tick()
                cadence = it
            }
        )

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
                Text(text = if (existing == null) "Add" else "Save")
            }
        }
    }
}

/** A pill row of mutually exclusive options, matching the Expense/Income control. */
@Composable
private fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(AppColors.Field)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) AppColors.Surface else AppColors.Field)
                    .clickable { onSelected(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary
                )
            }
        }
    }
}

private const val NOTE_MAX_CHARS = 40
