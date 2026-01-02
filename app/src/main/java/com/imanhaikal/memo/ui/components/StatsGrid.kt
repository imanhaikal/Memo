package com.imanhaikal.memo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.theme.AppColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StatsGrid(
    dailyLimit: Double,
    daysRemaining: Int,
    modifier: Modifier = Modifier,
    currencyCode: String = "USD"
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = "NEW DAILY LIMIT",
            content = {
                RollingCurrency(
                    value = dailyLimit,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary,
                    currencyCode = currencyCode
                )
            },
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "DAYS LEFT",
            content = {
                Text(
                    text = daysRemaining.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    MemoCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black),
                color = AppColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}