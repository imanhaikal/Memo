package com.imanhaikal.memo.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.CycleSummary
import com.imanhaikal.memo.ui.components.MemoCard
import com.imanhaikal.memo.ui.components.MemoScreenHeader
import com.imanhaikal.memo.ui.components.ProgressTrack
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Finished cycles, newest first.
 *
 * Before v5 a completed cycle simply vanished: the calculator slid its start date forward
 * and the old transactions stayed in the list looking like they still counted. This is
 * where that history now lives.
 */
@Composable
fun CycleHistoryScreen(
    cycles: List<CycleSummary>,
    currencyCode: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = CONTENT_MAX_WIDTH),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MemoScreenHeader(
                title = "History",
                onBack = onBack
            )
        }

        if (cycles.isEmpty()) {
            item {
                MemoCard(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Column {
                        Text(
                            text = "No finished cycles yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "When your current cycle ends it'll be summarised here, " +
                                "and a new one starts automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
        }

        items(cycles, key = { it.cycle.id }) { summary ->
            CycleSummaryCard(
                summary = summary,
                currencyCode = currencyCode,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun CycleSummaryCard(
    summary: CycleSummary,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val cycle = summary.cycle
    val totals = summary.totals
    val budget = cycle.budgetAmountCents
    val net = totals.netSpentCents
    val overBudget = net > budget
    val leftover = budget - net

    MemoCard(modifier = modifier, shape = RoundedCornerShape(24.dp), elevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatRange(cycle.startDate, cycle.endDateExclusive),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = AppColors.TextSecondary
                )
                Text(
                    text = "${totals.transactionCount} entries",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = CurrencyUtils.formatCurrency(net, currencyCode),
                style = MaterialTheme.typography.headlineMedium,
                color = if (overBudget) AppColors.Red else AppColors.TextPrimary
            )
            Text(
                text = "of ${CurrencyUtils.formatCurrency(budget, currencyCode)} spent",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProgressTrack(
                fraction = if (budget > 0) {
                    (net.toFloat() / budget).coerceIn(0f, 1f)
                } else 0f,
                fillColor = if (overBudget) AppColors.Red else AppColors.Yellow,
                height = 8.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (overBudget) {
                    "Over by ${CurrencyUtils.formatCurrency(-leftover, currencyCode)}"
                } else {
                    "${CurrencyUtils.formatCurrency(leftover, currencyCode)} left over"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (overBudget) AppColors.Red else AppColors.Green
            )

            if (totals.incomeCents > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Includes ${CurrencyUtils.formatCurrency(totals.incomeCents, currencyCode)} income",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary
                )
            }
        }
    }
}

private val RANGE_FORMAT = DateTimeFormatter.ofPattern("d MMM")
private val RANGE_FORMAT_WITH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun formatRange(startEpochDay: Long, endEpochDayExclusive: Long): String {
    val start = LocalDate.ofEpochDay(startEpochDay)
    val end = LocalDate.ofEpochDay(endEpochDayExclusive - 1)
    val thisYear = LocalDate.now().year
    val formatter = if (end.year == thisYear) RANGE_FORMAT else RANGE_FORMAT_WITH_YEAR
    return "${RANGE_FORMAT.format(start)} – ${formatter.format(end)}"
}
