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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.ui.components.MemoCard
import com.imanhaikal.memo.ui.components.MemoScreenHeader
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.ui.components.iconRes
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils

/**
 * Optional per-category spending limits for the active budget.
 *
 * Caps are advisory: exceeding one colours the category red on the dashboard but never
 * blocks an entry. A budgeting app that refuses to record what you actually spent stops
 * being a record of what you actually spent.
 */
@Composable
fun CategoryCapsScreen(
    caps: Map<Category, Long>,
    currencyCode: String,
    onCapChanged: (Category, Long?) -> Unit,
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
                title = "Category limits",
                onBack = onBack,
                bottomPadding = 4.dp
            )
        }

        item {
            Text(
                text = "Leave a limit empty for no cap. Going over shows in red on the " +
                    "dashboard — it never stops you recording an expense.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        items(Category.entries.toList(), key = { it.id }) { category ->
            CapRow(
                category = category,
                capCents = caps[category],
                currencyCode = currencyCode,
                onCapChanged = { onCapChanged(category, it) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun CapRow(
    category: Category,
    capCents: Long?,
    currencyCode: String,
    onCapChanged: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keyed on the stored value so an external change (or a discarded edit) resyncs the
    // field, matching how SettingsScreen keeps its budget inputs honest.
    var text by rememberSaveable(capCents) {
        mutableStateOf(capCents?.let { CurrencyUtils.formatAmountInput(it) } ?: "")
    }

    MemoCard(modifier = modifier, shape = RoundedCornerShape(20.dp), elevation = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(category.iconRes),
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = capCents?.let { "Limit ${CurrencyUtils.formatCurrency(it, currencyCode)}" }
                        ?: "No limit",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            MemoInput(
                value = text,
                onValueChange = { input ->
                    if (CurrencyUtils.isValidAmountInput(input)) {
                        text = input
                        // An empty field clears the cap; anything unparseable is simply
                        // not committed, so a half-typed "12." never wipes a saved limit.
                        if (input.isBlank()) {
                            onCapChanged(null)
                        } else {
                            CurrencyUtils.parseAmountToCents(input)?.let(onCapChanged)
                        }
                    }
                },
                label = "Limit",
                placeholder = "None",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.width(120.dp)
            )
        }
    }
}
