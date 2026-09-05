package com.imanhaikal.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
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
import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.ui.components.MemoCard
import com.imanhaikal.memo.ui.components.MemoIconButton
import com.imanhaikal.memo.ui.components.PressScale
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.dialogs.BudgetEditorDialog
import com.imanhaikal.memo.ui.dialogs.DeleteBudgetDialog
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.rememberStrongHaptics

/**
 * Lists every budget and switches between them. Creating, renaming, archiving and deleting
 * all live here so the dashboard header stays a single tap away from any of them.
 */
@Composable
fun BudgetsScreen(
    budgets: List<Budget>,
    activeBudgetId: Long,
    onSelect: (Long) -> Unit,
    onCreate: (name: String, amountCents: Long, days: Int, currency: String) -> Unit,
    onRename: (Long, String) -> Unit,
    onArchive: (Long, Boolean) -> Unit,
    onDelete: (Budget) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val haptic = rememberStrongHaptics()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Budget?>(null) }
    var deleteTarget by remember { mutableStateOf<Budget?>(null) }

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
                    .padding(start = 8.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
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
                        text = "Budgets",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AppColors.TextPrimary
                    )
                }
                MemoIconButton(
                    onClick = {
                        haptic.click()
                        showCreate = true
                    },
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New budget"
                )
            }
        }

        items(budgets, key = { it.id }) { budget ->
            BudgetRow(
                budget = budget,
                isActive = budget.id == activeBudgetId,
                onSelect = {
                    haptic.tick()
                    onSelect(budget.id)
                    onBack()
                },
                onRename = { renameTarget = budget },
                onArchive = { onArchive(budget.id, !budget.isArchived) },
                onDelete = { deleteTarget = budget },
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }

    if (showCreate) {
        BudgetEditorDialog(
            title = "New Budget",
            confirmLabel = "Create",
            onConfirm = { name, amount, days, currency ->
                onCreate(name, amount, days, currency)
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }

    renameTarget?.let { target ->
        BudgetEditorDialog(
            title = "Rename Budget",
            confirmLabel = "Save",
            initialName = target.name,
            initialAmountCents = target.amountCents,
            initialDays = target.totalDays,
            initialCurrency = target.currencyCode,
            nameOnly = true,
            onConfirm = { name, _, _, _ ->
                onRename(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        DeleteBudgetDialog(
            budget = target,
            isLast = budgets.size <= 1,
            onConfirm = {
                onDelete(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun BudgetRow(
    budget: Budget,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    MemoCard(
        modifier = modifier
            .fillMaxWidth()
            .springPress(interactionSource, PressScale.Surface)
            .clip(RoundedCornerShape(24.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onSelect),
        shape = RoundedCornerShape(24.dp),
        elevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Spacer(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AppColors.Yellow)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = budget.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (budget.isArchived) AppColors.TextTertiary else AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = CurrencyUtils.formatCurrency(budget.amountCents, budget.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("${budget.totalDays}-day cycle")
                    if (budget.isArchived) append(" · Archived")
                },
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RowAction("Rename", AppColors.TextSecondary, onRename)
                RowAction(
                    if (budget.isArchived) "Unarchive" else "Archive",
                    AppColors.TextSecondary,
                    onArchive
                )
                RowAction("Delete", AppColors.Red, onDelete)
            }
        }
    }
}

@Composable
private fun RowAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AppColors.Field)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
