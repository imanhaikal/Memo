package com.imanhaikal.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.data.RecurringRule
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.ui.components.MemoCard
import com.imanhaikal.memo.ui.components.MemoIconButton
import com.imanhaikal.memo.ui.dialogs.RecurringRuleDialog
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.DateLabels
import com.imanhaikal.memo.utils.rememberStrongHaptics
import java.time.LocalDate

/**
 * Rent, subscriptions and anything else that repeats.
 *
 * Rules post themselves on their due date whether or not the app is opened, so this screen
 * is about setting them up and pausing them rather than confirming each occurrence.
 */
@Composable
fun RecurringScreen(
    rules: List<RecurringRule>,
    currencyCode: String,
    onSave: (RecurringRule) -> Unit,
    onSetPaused: (RecurringRule, Boolean) -> Unit,
    onDelete: (RecurringRule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val haptic = rememberStrongHaptics()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringRule?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = CONTENT_MAX_WIDTH),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemoIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                    Text(
                        text = "Recurring",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AppColors.TextPrimary
                    )
                }
                MemoIconButton(
                    onClick = {
                        haptic.click()
                        editing = null
                        showEditor = true
                    },
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New recurring expense"
                )
            }
        }

        if (rules.isEmpty()) {
            item {
                MemoCard(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Column {
                        Text(
                            text = "Nothing repeating yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add rent or a subscription and it'll be recorded on its " +
                                "own each time it's due, so your daily limit stays honest.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            RecurringRow(
                rule = rule,
                currencyCode = currencyCode,
                onEdit = {
                    editing = rule
                    showEditor = true
                },
                onSetPaused = { onSetPaused(rule, it) },
                onDelete = { onDelete(rule) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }

    if (showEditor) {
        RecurringRuleDialog(
            existing = editing,
            onConfirm = {
                onSave(it)
                showEditor = false
                editing = null
            },
            onDismiss = {
                showEditor = false
                editing = null
            }
        )
    }
}

@Composable
private fun RecurringRow(
    rule: RecurringRule,
    currencyCode: String,
    onEdit: () -> Unit,
    onSetPaused: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = rule.type == TransactionType.INCOME

    MemoCard(modifier = modifier, shape = RoundedCornerShape(20.dp), elevation = 6.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.note.ifBlank { if (isIncome) "Income" else "Expense" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rule.isPaused) AppColors.TextTertiary else AppColors.TextPrimary
                    )
                    Text(
                        text = buildString {
                            append(rule.cadence.label)
                            if (rule.intervalCount > 1) append(" ×${rule.intervalCount}")
                            append(" · ")
                            append(
                                if (rule.isPaused) {
                                    "Paused"
                                } else {
                                    "Next ${DateLabels.relativeDayLabel(LocalDate.ofEpochDay(rule.nextDueDate))}"
                                }
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextTertiary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = (if (isIncome) "+ " else "") +
                        CurrencyUtils.formatCurrency(rule.amountCents, currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isIncome) AppColors.Green else AppColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = AppColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.Field)
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = AppColors.Red,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.Field)
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = !rule.isPaused,
                    onCheckedChange = { onSetPaused(!it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.OnYellow,
                        checkedTrackColor = AppColors.Yellow,
                        uncheckedThumbColor = AppColors.TextTertiary,
                        uncheckedTrackColor = AppColors.Field
                    )
                )
            }
        }
    }
}
