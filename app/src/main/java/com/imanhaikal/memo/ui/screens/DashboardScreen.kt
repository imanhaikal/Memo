package com.imanhaikal.memo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.R
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.ui.BudgetUiState
import com.imanhaikal.memo.ui.components.CategoryBreakdownCard
import com.imanhaikal.memo.ui.components.CycleProgressCard
import com.imanhaikal.memo.ui.components.HeroSection
import com.imanhaikal.memo.ui.components.MemoCard
import com.imanhaikal.memo.ui.components.MemoIconButton
import com.imanhaikal.memo.ui.components.StatsGrid
import com.imanhaikal.memo.ui.components.TransactionItem
import com.imanhaikal.memo.ui.components.groupTransactionsByDay
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.rememberStrongHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    state: BudgetUiState,
    onOpenSettings: () -> Unit,
    onAddExpense: () -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    // Entrance choreography plays once per visit; scrolling back must not replay it
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ENTRANCE_ITEM_COUNT * ENTRANCE_STAGGER_MS + ENTRANCE_DURATION_MS)
        entrancePlayed = true
    }
    val playEntrance = !entrancePlayed
    val dayGroups = remember(state.transactions) { groupTransactionsByDay(state.transactions) }
    // Older-cycle expenses stay visible but are visibly separated: they no longer
    // count toward the active budget and shouldn't look like they do.
    val cycleStart = state.cycleStartDate
    val (currentGroups, olderGroups) = remember(dayGroups, cycleStart) {
        if (cycleStart == null) {
            dayGroups to emptyList()
        } else {
            dayGroups.partition { !it.date.isBefore(cycleStart) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            // Keep the single-column layout readable on tablets and in landscape:
            // cap the width and centre it instead of stretching cards edge to edge
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = CONTENT_MAX_WIDTH),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Header with Staggered Entrance (Index 0)
            StaggeredEntrance(index = 0, play = playEntrance) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Memo.",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black, // Heavier weight for premium feel
                            fontSize = MaterialTheme.typography.titleLarge.fontSize * 1.2f // Slightly larger
                        ),
                        color = AppColors.TextPrimary
                    )
                    MemoIconButton(
                        onClick = onOpenSettings,
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = AppColors.TextSecondary
                    )
                }
            }
        }

        item {
            StaggeredEntrance(index = 1, play = playEntrance) {
                HeroSection(
                    availableAmount = state.availableToday,
                    status = state.status,
                    currencyCode = state.currencyCode,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )
            }
        }

        item {
            StaggeredEntrance(index = 2, play = playEntrance) {
                StatsGrid(
                    dailyLimit = state.dailyLimit,
                    daysRemaining = state.daysRemaining,
                    currencyCode = state.currencyCode,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item {
            StaggeredEntrance(index = 3, play = playEntrance) {
                CycleProgressCard(
                    spentCents = state.spentThisCycle,
                    totalBudgetCents = state.totalBudget,
                    daysRemaining = state.daysRemaining,
                    totalDays = state.totalDays,
                    currencyCode = state.currencyCode,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (state.categoryTotals.isNotEmpty()) {
            item {
                StaggeredEntrance(index = 4, play = playEntrance) {
                    CategoryBreakdownCard(
                        categoryTotals = state.categoryTotals,
                        currencyCode = state.currencyCode,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        if (state.transactions.isNotEmpty()) {
            if (currentGroups.isNotEmpty()) {
                item {
                    StaggeredEntrance(index = 5, play = playEntrance) {
                        Text(
                            text = "Recent Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                dayGroupItems(
                    groups = currentGroups,
                    currencyCode = state.currencyCode,
                    onEditTransaction = onEditTransaction,
                    onDeleteTransaction = onDeleteTransaction
                )
            }

            if (olderGroups.isNotEmpty()) {
                item(key = "previous-cycles-header", contentType = "section-header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp)
                            .animateItem()
                    ) {
                        Text(
                            text = "Previous Cycles",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Not counted in the current budget",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextTertiary
                        )
                    }
                }
                dayGroupItems(
                    groups = olderGroups,
                    currencyCode = state.currencyCode,
                    onEditTransaction = onEditTransaction,
                    onDeleteTransaction = onDeleteTransaction
                )
            }
        } else {
            item {
                StaggeredEntrance(index = 5, play = playEntrance) {
                    EmptyTransactions(
                        onAddExpense = onAddExpense,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
        
        item {
            // Bottom spacing for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/** Widest the single-column content grows before it stops stretching and centres. */
internal val CONTENT_MAX_WIDTH = 600.dp

internal const val ENTRANCE_ITEM_COUNT = 6L
internal const val ENTRANCE_STAGGER_MS = 100L
internal const val ENTRANCE_DURATION_MS = 300L

/** Day-header + transaction rows for one list section; keys stay unique across sections. */
private fun LazyListScope.dayGroupItems(
    groups: List<com.imanhaikal.memo.ui.components.DayGroup>,
    currencyCode: String,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit
) {
    groups.forEach { group ->
        item(key = "day-${group.date}", contentType = "day-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
                    .animateItem()
                    // One TalkBack stop per header: label and total read together
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
                Text(
                    text = remember(group.totalCents, currencyCode) {
                        CurrencyUtils.formatCurrency(group.totalCents, currencyCode)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary
                )
            }
        }
        items(
            items = group.transactions,
            key = { it.id },
            contentType = { "transaction" }
        ) { transaction ->
            TransactionItem(
                transaction = transaction,
                onClick = { onEditTransaction(transaction) },
                onDelete = { onDeleteTransaction(transaction) },
                currencyCode = currencyCode,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateItem()
            )
        }
    }
}

@Composable
fun StaggeredEntrance(
    index: Int,
    play: Boolean = true,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { 24.dp.toPx() }
    // Capture `play` at first composition: items re-entering after the entrance has
    // finished render at rest instead of replaying the choreography
    val alpha = remember { Animatable(if (play) 0f else 1f) }
    val translationY = remember { Animatable(if (play) initialOffsetPx else 0f) }

    LaunchedEffect(Unit) {
        if (alpha.value < 1f) {
            delay(index * ENTRANCE_STAGGER_MS)
            launch { alpha.animateTo(1f, animationSpec = tween(durationMillis = ENTRANCE_DURATION_MS.toInt())) }
            launch { translationY.animateTo(0f, animationSpec = tween(durationMillis = ENTRANCE_DURATION_MS.toInt())) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                this.translationY = translationY.value
            }
    ) {
        content()
    }
}

@Composable
private fun EmptyTransactions(
    onAddExpense: () -> Unit,
    modifier: Modifier = Modifier
) {
    MemoCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppColors.Field),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cat_other),
                    contentDescription = null,
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No expenses yet",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Log your first one below — or scan a receipt and let Memo fill it in.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // The copy used to point at the FAB; give the empty state its own way out
            val addInteraction = remember { MutableInteractionSource() }
            val haptic = rememberStrongHaptics()
            Button(
                onClick = {
                    haptic.click()
                    onAddExpense()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Yellow,
                    contentColor = AppColors.OnYellow
                ),
                interactionSource = addInteraction,
                modifier = Modifier
                    .springPress(addInteraction)
                    .height(48.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Add your first expense",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
