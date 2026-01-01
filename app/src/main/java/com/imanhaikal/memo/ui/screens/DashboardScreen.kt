package com.imanhaikal.memo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.BudgetUiState
import com.imanhaikal.memo.ui.components.HeroSection
import com.imanhaikal.memo.ui.components.StatsGrid
import com.imanhaikal.memo.ui.components.TransactionItem
import com.imanhaikal.memo.ui.theme.AppColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    state: BudgetUiState,
    onOpenSettings: () -> Unit,
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
                items = state.transactions.sortedByDescending { it.date },
                key = { it.id }
            ) { transaction ->
                // List items slide in individually
                // We'll use a simple animation for them as they appear
                TransactionItem(
                    transaction = transaction,
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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 100L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 300)
        ) { it / 2 } + fadeIn(animationSpec = tween(durationMillis = 300)),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}