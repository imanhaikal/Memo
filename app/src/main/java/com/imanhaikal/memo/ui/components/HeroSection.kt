package com.imanhaikal.memo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.BudgetStatus
import com.imanhaikal.memo.ui.theme.AppColors

@Composable
fun HeroSection(
    availableAmount: Double,
    status: BudgetStatus,
    modifier: Modifier = Modifier,
    currencyCode: String = "USD"
) {
    MemoCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AVAILABLE TODAY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black),
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            RollingCurrency(
                value = availableAmount,
                style = MaterialTheme.typography.displayLarge,
                color = AppColors.TextPrimary,
                modifier = Modifier,
                currencyCode = currencyCode
            )

            Spacer(modifier = Modifier.height(16.dp))

            StatusPill(status = status)
        }
    }
}

@Composable
private fun StatusPill(status: BudgetStatus) {
    val (containerColor, contentColor, text) = when (status) {
        BudgetStatus.OVER_LIMIT -> Triple(AppColors.RedSubtle, AppColors.Red, "Over Limit")
        BudgetStatus.CAREFUL -> Triple(AppColors.Border, AppColors.TextSecondary, "Careful")
        BudgetStatus.ON_TRACK -> Triple(AppColors.GreenSubtle, AppColors.Green, "On Track")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}