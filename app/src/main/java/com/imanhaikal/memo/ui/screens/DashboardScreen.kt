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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.ui.BudgetUiState
import com.imanhaikal.memo.ui.components.HeroSection
import com.imanhaikal.memo.ui.components.MemoCard
import com.imanhaikal.memo.ui.components.StatsGrid
import com.imanhaikal.memo.ui.components.TransactionItem
import com.imanhaikal.memo.ui.components.groupTransactionsByDay
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    state: BudgetUiState,
    onOpenSettings: () -> Unit,
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AppColors.TextSecondary
                        )
                    }
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

        if (state.transactions.isNotEmpty()) {
            item {
                StaggeredEntrance(index = 3, play = playEntrance) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            dayGroups.forEach { group ->
                item(key = "day-${group.date}", contentType = "day-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp)
                            .animateItem(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextSecondary
                        )
                        Text(
                            text = remember(group.totalCents, state.currencyCode) {
                                CurrencyUtils.formatCurrency(group.totalCents, state.currencyCode)
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
                        currencyCode = state.currencyCode,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem()
                    )
                }
            }
        } else {
            item {
                StaggeredEntrance(index = 3, play = playEntrance) {
                    EmptyTransactions(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        
        item {
            // Bottom spacing for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

internal const val ENTRANCE_ITEM_COUNT = 4L
internal const val ENTRANCE_STAGGER_MS = 100L
internal const val ENTRANCE_DURATION_MS = 300L

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
private fun EmptyTransactions(modifier: Modifier = Modifier) {
    MemoCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No expenses yet",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap Add Expense to log your first one — or scan a receipt and let Memo fill it in.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
