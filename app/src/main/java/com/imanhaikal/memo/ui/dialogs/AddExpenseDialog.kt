package com.imanhaikal.memo.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.data.Transaction
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.imanhaikal.memo.utils.rememberStrongHaptics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.imanhaikal.memo.ui.components.PressScale
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.ui.theme.MemoTheme
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.autoFocusOnceAttached
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    transaction: Transaction? = null,
    initialAmountCents: Long? = null,
    initialNote: String? = null,
    onConfirm: (amountCents: Long, note: String, dateMillis: Long?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var amountText by rememberSaveable(transaction?.id, initialAmountCents) {
        mutableStateOf(
            transaction?.amount?.let(CurrencyUtils::formatAmountInput)
                ?: initialAmountCents?.let(CurrencyUtils::formatAmountInput)
                ?: ""
        )
    }
    var noteText by rememberSaveable(transaction?.id, initialNote) {
        mutableStateOf(transaction?.note ?: initialNote ?: "")
    }
    // null = untouched "Today"; the ViewModel stamps clock.millis() at submit time.
    var selectedDateMillis by rememberSaveable(transaction?.id) {
        mutableStateOf(transaction?.date)
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val amountCents = CurrencyUtils.parseAmountToCents(amountText)

    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }
    val haptic = rememberStrongHaptics()
    val amountFocusRequester = remember { FocusRequester() }

    val submit = {
        if (amountCents != null) {
            haptic.success()
            onConfirm(amountCents, noteText, selectedDateMillis)
        }
    }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = AppColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (transaction == null) "Add Expense" else "Edit Expense",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Amount Input
                MemoInput(
                    value = amountText,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() || char == '.' }) {
                            amountText = it
                        }
                    },
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

                Spacer(modifier = Modifier.height(16.dp))

                // Note Input
                MemoInput(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = "Note",
                    placeholder = "e.g. Lunch",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    DateChip(
                        label = dateChipLabel(selectedDateMillis),
                        onClick = {
                            haptic.tick()
                            showDatePicker = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp), // Reset arrangement
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (transaction != null && onDelete != null) {
                        val deleteInteraction = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = {
                                haptic.tick()
                                onDelete()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppColors.Red
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Red),
                            interactionSource = deleteInteraction,
                            modifier = Modifier
                                .springPress(deleteInteraction)
                                .width(50.dp)
                                .height(50.dp),
                            shape = RoundedCornerShape(50),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = AppColors.Red
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    val confirmInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = submit,
                        enabled = amountCents != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Yellow,
                            contentColor = AppColors.TextPrimary
                        ),
                        interactionSource = confirmInteraction,
                        modifier = Modifier
                            .springPress(confirmInteraction)
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (transaction == null) "Add" else "Save",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val chipDate = selectedDateMillis
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: LocalDate.now(zone)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = chipDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !day.isAfter(LocalDate.now(ZoneId.systemDefault()))
                }

                override fun isSelectableYear(year: Int): Boolean =
                    year <= LocalDate.now(ZoneId.systemDefault()).year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            shape = RoundedCornerShape(32.dp),
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            selectedDateMillis = combinePickedDayWithTime(utcMillis, selectedDateMillis)
                        }
                        showDatePicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) {
                    Text("OK", color = AppColors.TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun DateChip(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .springPress(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "Change date",
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextPrimary
        )
    }
}

private fun dateChipLabel(dateMillis: Long?): String {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val date = dateMillis
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: return "Today"
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val pattern = if (date.year == today.year) "MMM d" else "MMM d, yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern))
        }
    }
}

/**
 * The picker reports the chosen day as UTC midnight. Re-anchor that day in the local
 * zone, keeping the time-of-day of the value being replaced (or now, if untouched) so
 * transactions sort and display with a meaningful time.
 */
private fun combinePickedDayWithTime(pickedUtcMillis: Long, previousMillis: Long?): Long {
    val zone = ZoneId.systemDefault()
    val pickedDay = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val timeOfDay = Instant.ofEpochMilli(previousMillis ?: System.currentTimeMillis())
        .atZone(zone).toLocalTime()
    return pickedDay.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
}

@Preview
@Composable
fun AddExpenseDialogPreview() {
    MemoTheme {
        AddExpenseDialog(onConfirm = { _, _, _ -> }, onDismiss = {})
    }
}
