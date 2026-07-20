package com.imanhaikal.memo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.R
import com.imanhaikal.memo.ui.CategoryTotal
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils

/** Overall budget progress for the active cycle: spent vs total, and cycle day. */
@Composable
fun CycleProgressCard(
    spentCents: Long,
    totalBudgetCents: Long,
    daysRemaining: Int,
    totalDays: Int,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val fraction = if (totalBudgetCents > 0) {
        (spentCents.toFloat() / totalBudgetCents).coerceIn(0f, 1f)
    } else 0f
    val overBudget = spentCents > totalBudgetCents
    val dayOfCycle = (totalDays - daysRemaining + 1).coerceIn(1, totalDays)

    MemoCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        elevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BUDGET USED",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = AppColors.TextSecondary
                )
                Text(
                    text = "DAY $dayOfCycle OF $totalDays",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = AppColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProgressTrack(
                fraction = fraction,
                fillColor = if (overBudget) AppColors.Red else AppColors.Yellow,
                height = 8.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CurrencyUtils.formatCurrency(spentCents, currencyCode),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (overBudget) AppColors.Red else AppColors.TextPrimary
                )
                Text(
                    text = " of ${CurrencyUtils.formatCurrency(totalBudgetCents, currencyCode)} spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

/** Per-category totals for the active cycle, largest first. */
@Composable
fun CategoryBreakdownCard(
    categoryTotals: List<CategoryTotal>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val maxTotal = categoryTotals.maxOfOrNull { it.totalCents } ?: return

    MemoCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "THIS CYCLE BY CATEGORY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                color = AppColors.TextSecondary
            )

            categoryTotals.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(entry.category?.iconRes ?: R.drawable.ic_cat_other),
                        contentDescription = null,
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = entry.category?.label ?: "Uncategorized",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = CurrencyUtils.formatCurrency(entry.totalCents, currencyCode),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        ProgressTrack(
                            fraction = (entry.totalCents.toFloat() / maxTotal).coerceIn(0f, 1f),
                            fillColor = AppColors.Yellow,
                            height = 6.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(
    fraction: Float,
    fillColor: androidx.compose.ui.graphics.Color,
    height: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(AppColors.Field)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(fillColor)
            )
        }
    }
}
