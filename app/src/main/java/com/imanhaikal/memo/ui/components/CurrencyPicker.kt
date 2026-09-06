package com.imanhaikal.memo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.rememberStrongHaptics

/**
 * Currency dropdown shared by Settings, the budget editor and setup. Extracted so the
 * three copies of the menu can't drift from each other.
 *
 * [containerColor] and [borderColor] exist because the same control needs two fills: on
 * Settings it sits on the page background and reads as a card, while inside a dialog it
 * sits beside [MemoInput] fields and has to match them.
 */
@Composable
fun CurrencyPicker(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppColors.Surface,
    borderColor: Color = AppColors.Border
) {
    val haptic = rememberStrongHaptics()
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AppColors.TextPrimary,
                containerColor = containerColor
            ),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Text(
                text = CurrencyUtils.SUPPORTED_CURRENCIES[selectedCurrency] ?: selectedCurrency,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .weight(1f),
                textAlign = TextAlign.Start
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppColors.Surface)
        ) {
            CurrencyUtils.SUPPORTED_CURRENCIES.forEach { (code, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (code == selectedCurrency) {
                                AppColors.TextPrimary
                            } else {
                                AppColors.TextSecondary
                            },
                            fontWeight = if (code == selectedCurrency) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    },
                    onClick = {
                        haptic.tick()
                        onCurrencySelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}
