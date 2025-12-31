package com.imanhaikal.memo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.ui.theme.AppColors
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionList(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(transactions, key = { it.id }) { transaction ->
            TransactionItem(transaction = transaction)
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Surface)
            .border(
                width = 1.dp,
                color = AppColors.Border,
                shape = shape
            )
            .padding(vertical = 20.dp, horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.note.ifBlank { "Expense" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary
                )
                
                val dateStr = try {
                    val instant = Instant.ofEpochMilli(transaction.date)
                    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
                        .withZone(ZoneId.systemDefault())
                    formatter.format(instant)
                } catch (e: Exception) {
                    ""
                }

                if (dateStr.isNotEmpty()) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            val formattedAmount = NumberFormat.getCurrencyInstance(Locale.US).format(transaction.amount)
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )
        }
    }
}