package com.imanhaikal.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.ui.SearchCriteria
import com.imanhaikal.memo.ui.components.MemoCard
import com.imanhaikal.memo.ui.components.MemoChip
import com.imanhaikal.memo.ui.components.MemoScreenHeader
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.ui.components.groupTransactionsByDay
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.dismissKeyboardOnDragDown
import com.imanhaikal.memo.utils.rememberStrongHaptics

/**
 * Text and filter search over the active budget's history.
 *
 * Results come from a Room query rather than filtering the dashboard's list in memory —
 * that list is already every row for the budget, and it only grows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    criteria: SearchCriteria,
    results: List<Transaction>,
    currencyCode: String,
    onCriteriaChange: (SearchCriteria) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val haptic = rememberStrongHaptics()
    val groups = groupTransactionsByDay(results)
    val total = results.sumOf { it.signedAmount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = CONTENT_MAX_WIDTH)
            .dismissKeyboardOnDragDown(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MemoScreenHeader(
                title = "Search",
                onBack = onBack,
                bottomPadding = 4.dp
            )
        }

        item {
            MemoInput(
                value = criteria.query,
                onValueChange = { onCriteriaChange(criteria.copy(query = it)) },
                label = "Search notes",
                placeholder = "e.g. coffee",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        item {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    label = "Expenses",
                    selected = criteria.type == TransactionType.EXPENSE,
                    onClick = {
                        haptic.tick()
                        onCriteriaChange(
                            criteria.copy(
                                type = if (criteria.type == TransactionType.EXPENSE) {
                                    null
                                } else {
                                    TransactionType.EXPENSE
                                }
                            )
                        )
                    }
                )
                FilterChip(
                    label = "Income",
                    selected = criteria.type == TransactionType.INCOME,
                    onClick = {
                        haptic.tick()
                        onCriteriaChange(
                            criteria.copy(
                                type = if (criteria.type == TransactionType.INCOME) {
                                    null
                                } else {
                                    TransactionType.INCOME
                                }
                            )
                        )
                    }
                )
                Category.entries.forEach { category ->
                    FilterChip(
                        label = category.label,
                        selected = criteria.category == category,
                        onClick = {
                            haptic.tick()
                            onCriteriaChange(
                                criteria.copy(
                                    category = if (criteria.category == category) null else category
                                )
                            )
                        }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (results.isEmpty()) "No matches" else "${results.size} matches",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = AppColors.TextSecondary
                )
                if (results.isNotEmpty()) {
                    Text(
                        text = CurrencyUtils.formatCurrency(total, currencyCode),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = AppColors.TextPrimary
                    )
                }
            }
        }

        if (results.isEmpty()) {
            item {
                MemoCard(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Column {
                        Text(
                            text = if (criteria.isEmpty) "Search your history" else "Nothing found",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (criteria.isEmpty) {
                                "Type to search notes and descriptions, or filter by category."
                            } else {
                                "Try a shorter search, or clear some filters."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
        } else {
            dayGroupItems(
                groups = groups,
                currencyCode = currencyCode,
                onEditTransaction = onEditTransaction,
                onDeleteTransaction = onDeleteTransaction
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    MemoChip(
        label = label,
        selected = selected,
        onClick = onClick,
        horizontalPadding = 14.dp,
        fontWeight = FontWeight.Bold
    )
}
