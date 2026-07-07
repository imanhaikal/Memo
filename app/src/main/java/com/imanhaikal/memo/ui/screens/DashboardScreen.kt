package com.imanhaikal.memo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.ui.BudgetUiState
import com.imanhaikal.memo.ui.components.HeroSection
import com.imanhaikal.memo.ui.components.StatsGrid
import com.imanhaikal.memo.ui.components.TransactionItem
import com.imanhaikal.memo.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    state: BudgetUiState,
    onOpenSettings: () -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Header with Staggered Entrance (Index 0)
            StaggeredEntrance(index = 0) {
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
            StaggeredEntrance(index = 1) {
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
            StaggeredEntrance(index = 2) {
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
                StaggeredEntrance(index = 3) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            items(
                items = state.transactions,
                key = { it.id }
            ) { transaction ->
                // List items slide in individually
                // We'll use a simple animation for them as they appear
                TransactionItem(
                    transaction = transaction,
                    onClick = { onEditTransaction(transaction) },
                    onDelete = { onDeleteTransaction(transaction) },
                    currencyCode = state.currencyCode,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItemPlacement()
                )
            }
        }
        
        item {
            // Bottom spacing for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun StaggeredEntrance(
    index: Int,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { 24.dp.toPx() }
    val alpha = remember { Animatable(0f) }
    val translationY = remember { Animatable(initialOffsetPx) }

    LaunchedEffect(Unit) {
        delay(index * 100L)
        launch { alpha.animateTo(1f, animationSpec = tween(durationMillis = 300)) }
        launch { translationY.animateTo(0f, animationSpec = tween(durationMillis = 300)) }
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
