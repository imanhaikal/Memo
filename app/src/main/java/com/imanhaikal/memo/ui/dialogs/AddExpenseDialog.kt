package com.imanhaikal.memo.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.imanhaikal.memo.utils.rememberStrongHaptics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imanhaikal.memo.R
import com.imanhaikal.memo.ui.components.PressScale
import com.imanhaikal.memo.ui.components.iconRes
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.ui.theme.MemoTheme
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.DateLabels
import com.imanhaikal.memo.utils.autoFocusOnceAttached
import kotlinx.coroutines.launch
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
    onConfirm: (amountCents: Long, note: String, dateMillis: Long?, hasTime: Boolean, category: Category?, description: String) -> Unit,
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
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val amountCents = CurrencyUtils.parseAmountToCents(amountText)

    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }
    val haptic = rememberStrongHaptics()
    val amountFocusRequester = remember { FocusRequester() }

    val submit = {
        if (amountCents != null) {
            haptic.success()
            onConfirm(
                amountCents,
                noteText,
                selectedDateMillis,
                // An untouched entry is stamped "now", which is a real time
                timeExplicit || selectedDateMillis == null,
                selectedCategory,
                descriptionText.trim()
            )
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

    Dialog(
        onDismissRequest = onDismiss,
        // Full-size window that doesn't fit system windows: the wrap-content default
        // lets the window itself pan/clip around the keyboard, which corrupts the
        // scroll viewport (content scrolls down but can never scroll back up).
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Must be read inside the Dialog: its window hosts a separate focus owner,
        // and the activity's FocusManager cannot clear focus in here.
        val focusManager = LocalFocusManager.current
        // imePadding on the container bounds the card above the keyboard, so the
        // inner verticalScroll always has a correct, fully reachable range.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                // The window covers the screen, so outside-tap dismissal is ours now;
                // the card below swallows its own taps.
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = AppColors.Surface,
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .scale(scale.value)
                    .alpha(alpha.value)
            ) {
            Column(
                modifier = Modifier
                    // Tapping non-interactive card area clears focus, hiding the keyboard
                    // (and swallows the tap so it doesn't dismiss the dialog); children
                    // (fields, chips, buttons) consume their own taps first.
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    }
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
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
                    hasDetails = selectedCategory != null || descriptionText.isNotBlank(),
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

                        MemoInput(
                            value = descriptionText,
                            onValueChange = { descriptionText = it },
                            label = "Description",
                            placeholder = "Add more details…",
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 96.dp)
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
                            timeExplicit = true
                            selectedDateMillis = DateLabels.combineDateWithPickedTime(
                                timePickerState.hour,
                                timePickerState.minute,
                                selectedDateMillis
                            )
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
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF5F5F5))
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
    val interaction = remember { MutableInteractionSource() }
    val background = if (selected) AppColors.Yellow else Color(0xFFF5F5F5)
    val contentColor = if (selected) AppColors.TextPrimary else AppColors.TextSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .springPress(interaction, pressedScale = PressScale.Surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            painter = painterResource(category.iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextPrimary
        )
    }
}

private fun toLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Preview
@Composable
fun AddExpenseDialogPreview() {
    MemoTheme {
        AddExpenseDialog(onConfirm = { _, _, _, _, _, _ -> }, onDismiss = {})
    }
}
