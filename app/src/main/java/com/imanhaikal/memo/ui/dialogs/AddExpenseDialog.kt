package com.imanhaikal.memo.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.data.Transaction
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.imanhaikal.memo.utils.rememberStrongHaptics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.imanhaikal.memo.R
import com.imanhaikal.memo.ui.components.MemoChip
import com.imanhaikal.memo.ui.components.MemoDialog
import com.imanhaikal.memo.ui.components.PressScale
import com.imanhaikal.memo.ui.components.iconRes
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.ui.theme.MemoTheme
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.DateLabels
import com.imanhaikal.memo.utils.autoFocusOnceAttached
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseDialog(
    transaction: Transaction? = null,
    initialAmountCents: Long? = null,
    initialNote: String? = null,
    initialCategory: Category? = null,
    initialDescription: String? = null,
    initialDateMillis: Long? = null,
    initialDateHasTime: Boolean = true,
    // Disable for pre-filled flows (receipt scan) where a stray outside tap would discard data
    dismissOnClickOutside: Boolean = true,
    onConfirm: (TransactionDraft) -> Unit,
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
    var selectedDateMillis by rememberSaveable(transaction?.id, initialDateMillis) {
        mutableStateOf(transaction?.date ?: initialDateMillis)
    }
    // True once a concrete time-of-day is known (edited transaction with a time, a
    // receipt-printed time, or the user picking one). An untouched "Now" entry gets
    // the submit-time timestamp instead, and a backdated entry without an explicit
    // time is saved as date-only.
    var timeExplicit by rememberSaveable(transaction?.id, initialDateMillis, initialDateHasTime) {
        mutableStateOf(
            transaction?.hasTime ?: (initialDateMillis != null && initialDateHasTime)
        )
    }
    var selectedCategory by rememberSaveable(transaction?.id, initialCategory) {
        mutableStateOf(transaction?.category ?: initialCategory)
    }
    var descriptionText by rememberSaveable(transaction?.id, initialDescription) {
        mutableStateOf(transaction?.description?.ifBlank { null } ?: initialDescription ?: "")
    }
    // Open with details visible whenever there is already something in them
    var detailsExpanded by rememberSaveable(transaction?.id, initialCategory, initialDescription) {
        mutableStateOf(
            (transaction?.category ?: initialCategory) != null ||
                !(transaction?.description?.ifBlank { null } ?: initialDescription).isNullOrBlank()
        )
    }
    var type by rememberSaveable(transaction?.id) {
        mutableStateOf(transaction?.type ?: TransactionType.EXPENSE)
    }
    val isIncome = type == TransactionType.INCOME
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val amountCents = CurrencyUtils.parseAmountToCents(amountText)

    val haptic = rememberStrongHaptics()
    val amountFocusRequester = remember { FocusRequester() }

    MemoDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = dismissOnClickOutside
    ) { close ->
        val focusManager = LocalFocusManager.current

        // Every path that closes the dialog goes through `close`, so the exit
        // animation plays before the caller's state flag removes it
        val submit = {
            if (amountCents != null) {
                haptic.success()
                close {
                    onConfirm(
                        TransactionDraft(
                            amountCents = amountCents,
                            note = noteText,
                            dateMillis = selectedDateMillis,
                            // An untouched entry is stamped "now", which is a real time
                            hasTime = timeExplicit || selectedDateMillis == null,
                            // Income has no expense category to file it under
                            category = if (isIncome) null else selectedCategory,
                            description = descriptionText.trim(),
                            type = type
                        )
                    )
                }
            }
        }

        Text(
            text = when {
                transaction != null -> "Edit Entry"
                isIncome -> "Add Income"
                else -> "Add Expense"
            },
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Above the amount deliberately: the sign is chosen before the number is typed,
        // and it can't be fat-fingered the way a leading minus could.
        TypeSelector(
            selected = type,
            onSelected = {
                if (it != type) haptic.tick()
                type = it
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Amount Input
        MemoInput(
            value = amountText,
            onValueChange = {
                if (CurrencyUtils.isValidAmountInput(it)) {
                    amountText = it
                }
            },
            label = if (isIncome) "Amount received" else "Amount",
            placeholder = "0.00",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .autoFocusOnceAttached(amountFocusRequester)
        )

        // The filter blocks malformed text, so the only invalid non-empty
        // input left is a zero amount — say so instead of a silently dead button
        if (amountText.isNotEmpty() && amountCents == null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter an amount greater than zero",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Red,
                modifier = Modifier.fillMaxWidth()
            )
        }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateChip(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Change date",
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = selectedDateMillis
                    ?.let { DateLabels.relativeDayLabel(toLocalDate(it)) }
                    ?: "Today",
                onClick = {
                    haptic.tick()
                    showDatePicker = true
                }
            )
            DateChip(
                icon = { tint ->
                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = "Change time",
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = when {
                    timeExplicit -> DateLabels.timeLabel(
                        selectedDateMillis ?: System.currentTimeMillis()
                    )
                    selectedDateMillis == null -> "Now"
                    else -> "Add time"
                },
                onClick = {
                    haptic.tick()
                    showTimePicker = true
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DetailsToggle(
            expanded = detailsExpanded,
            hasDetails = (!isIncome && selectedCategory != null) || descriptionText.isNotBlank(),
            onClick = {
                haptic.tick()
                if (detailsExpanded) focusManager.clearFocus()
                detailsExpanded = !detailsExpanded
            }
        )

        AnimatedVisibility(
            visible = detailsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(12.dp))

                // The categories are all expense-shaped (Food, Transport, ...), so they
                // are simply not offered for income rather than shown and ignored.
                if (!isIncome) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Category.entries.forEach { category ->
                            CategoryChip(
                                category = category,
                                selected = category == selectedCategory,
                                onClick = {
                                    haptic.tick()
                                    // Tapping the selected chip clears it (uncategorized)
                                    selectedCategory =
                                        if (selectedCategory == category) null else category
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                MemoInput(
                    value = descriptionText,
                    // Truncate rather than reject: a scan can prefill more than the cap,
                    // and a reject-on-length guard would then block every edit, deletions included.
                    onValueChange = {
                        descriptionText = it.take(Transaction.DESCRIPTION_MAX_CHARS)
                    },
                    label = "Description",
                    placeholder = "Add more details…",
                    singleLine = false,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    // Grows with the text up to `max`, then scrolls internally instead
                    // of stretching the dialog
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 160.dp)
                )
            }
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
                        close(onDelete)
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
                    contentColor = AppColors.OnYellow,
                    disabledContainerColor = AppColors.Disabled,
                    disabledContentColor = AppColors.OnDisabled
                ),
                interactionSource = confirmInteraction,
                modifier = Modifier
                    .springPress(confirmInteraction)
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = when {
                        transaction != null -> "Save"
                        isIncome -> "Add income"
                        else -> "Add"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
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
                        haptic.tick()
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val pickedDay = Instant.ofEpochMilli(utcMillis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            selectedDateMillis = when {
                                // Explicit time survives a date change
                                timeExplicit ->
                                    DateLabels.combinePickedDayWithTime(utcMillis, selectedDateMillis)
                                // Re-picking today on an untouched entry keeps "Today / Now"
                                selectedDateMillis == null && pickedDay == LocalDate.now(zone) -> null
                                // Backdated without a time: date-only, anchored at noon
                                else -> DateLabels.pickedDayAtNoon(utcMillis)
                            }
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

    if (showTimePicker) {
        val zone = ZoneId.systemDefault()
        val initialTime = Instant.ofEpochMilli(selectedDateMillis ?: System.currentTimeMillis())
            .atZone(zone).toLocalTime()
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute
        )
        // material3 1.3.x has no TimePickerDialog; wrap TimePicker to match DatePickerDialog styling
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = AppColors.Surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (timeExplicit) {
                            TextButton(onClick = {
                                haptic.tick()
                                timeExplicit = false
                                selectedDateMillis = selectedDateMillis?.let(DateLabels::sameDayAtNoon)
                                showTimePicker = false
                            }) {
                                Text("No time", color = AppColors.TextSecondary)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel", color = AppColors.TextSecondary)
                        }
                        TextButton(onClick = {
                            haptic.tick()
                            timeExplicit = true
                            val combined = DateLabels.combineDateWithPickedTime(
                                timePickerState.hour,
                                timePickerState.minute,
                                selectedDateMillis
                            )
                            // The date picker already forbids future days; clamp a
                            // future time-of-day on today the same way
                            selectedDateMillis = minOf(combined, System.currentTimeMillis())
                            showTimePicker = false
                        }) {
                            Text("OK", color = AppColors.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Expense / Income segmented control.
 *
 * Amounts are stored as a positive magnitude with the sign carried by the type, so this
 * is what makes a refund possible without loosening the amount parser — that same parser
 * validates the total budget field, where a negative value would break the pool maths.
 */
@Composable
private fun TypeSelector(
    selected: TransactionType,
    onSelected: (TransactionType) -> Unit,
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
        TransactionType.entries.forEach { type ->
            val isSelected = type == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) AppColors.Surface else Color.Transparent,
                animationSpec = tween(200),
                label = "typeChipBackground"
            )
            val contentColor by animateColorAsState(
                targetValue = when {
                    isSelected && type == TransactionType.INCOME -> AppColors.Green
                    isSelected -> AppColors.TextPrimary
                    else -> AppColors.TextSecondary
                },
                animationSpec = tween(200),
                label = "typeChipContent"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable { onSelected(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (type == TransactionType.INCOME) "Income" else "Expense",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun DateChip(
    icon: @Composable (tint: Color) -> Unit,
    label: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .springPress(interaction)
            // Clip before clickable so the touch target matches the pill, not its bounding box
            .clip(RoundedCornerShape(50))
            .background(AppColors.Field)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        icon(AppColors.TextSecondary)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
private fun DetailsToggle(
    expanded: Boolean,
    hasDetails: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "detailsChevron"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .springPress(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = when {
                expanded -> "Hide details"
                hasDetails -> "Show details"
                else -> "Add details"
            },
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier
                .size(18.dp)
                .rotate(chevronRotation)
        )
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit
) {
    MemoChip(
        label = category.label,
        selected = selected,
        onClick = onClick,
        leadingIcon = { tint ->
            Icon(
                painter = painterResource(category.iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

private fun toLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Preview
@Composable
fun AddExpenseDialogPreview() {
    MemoTheme {
        AddExpenseDialog(onConfirm = {}, onDismiss = {})
    }
}
