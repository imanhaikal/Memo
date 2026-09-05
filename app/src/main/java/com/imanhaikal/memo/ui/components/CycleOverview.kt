package com.imanhaikal.memo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * Per-category totals for the active cycle, largest first.
 *
 * A category with a cap is measured against that cap; an uncapped one is measured against
 * the largest category, which is only a relative sense of scale. Mixing the two in one
 * chart is deliberate — a cap is the more meaningful denominator whenever the user has
 * set one.
 */
@Composable
fun CategoryBreakdownCard(
    categoryTotals: List<CategoryTotal>,
    currencyCode: String,
    modifier: Modifier = Modifier,
    onEditCaps: (() -> Unit)? = null
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "THIS CYCLE BY CATEGORY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = AppColors.TextSecondary
                )
                if (onEditCaps != null) {
                    Text(
                        text = "LIMITS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = AppColors.TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onEditCaps)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

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
                                text = if (entry.capCents != null) {
                                    CurrencyUtils.formatCurrency(entry.totalCents, currencyCode) +
                                        " / " +
                                        CurrencyUtils.formatCurrency(entry.capCents, currencyCode)
                                } else {
                                    CurrencyUtils.formatCurrency(entry.totalCents, currencyCode)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.isOverCap) AppColors.Red else AppColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val denominator = entry.capCents ?: maxTotal
                        ProgressTrack(
                            fraction = if (denominator > 0) {
                                (entry.totalCents.toFloat() / denominator).coerceIn(0f, 1f)
                            } else 0f,
                            fillColor = if (entry.isOverCap) AppColors.Red else AppColors.Yellow,
                            height = 6.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgressTrack(
    fraction: Float,
    fillColor: androidx.compose.ui.graphics.Color,
    height: androidx.compose.ui.unit.Dp
) {
    // These bars sit directly under a balance that rolls over ~a second; a bar that
    // jumps to its new value in the same glance gives the mismatch away. No bounce —
    // an overshoot would briefly show more spent than there is.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progressFraction"
    )
    val animatedFill by animateColorAsState(
        targetValue = fillColor,
        animationSpec = tween(durationMillis = 240),
        label = "progressFill"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(AppColors.Field)
    ) {
        if (animatedFraction > 0f) {
            Box(
                modifier = Modifier
                    // Callers already clamp and the spring cannot overshoot, but
                    // fillMaxWidth throws outside 0..1 and the guard costs nothing.
                    .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(animatedFill)
            )
        }
    }
}
