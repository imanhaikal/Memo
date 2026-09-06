package com.imanhaikal.memo.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.imanhaikal.memo.ui.components.MemoInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.imanhaikal.memo.utils.rememberStrongHaptics
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.components.CurrencyPicker
import com.imanhaikal.memo.ui.components.MemoDialog
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.ui.theme.MemoTheme
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.autoFocusOnceAttached
import java.util.Currency
import java.util.Locale

@Composable
fun SetupDialog(
    onConfirm: (amountCents: Long, days: Int, currency: String) -> Unit,
    onDismiss: () -> Unit // Although usually setup isn't dismissible without action, we'll include it for standard dialog API
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var daysText by rememberSaveable { mutableStateOf("30") }
    // Default to the device locale's currency when we support it
    val defaultCurrency = remember {
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }
            .getOrNull()
            ?.takeIf { it in CurrencyUtils.SUPPORTED_CURRENCIES }
            ?: "MYR"
    }
    var selectedCurrency by rememberSaveable { mutableStateOf(defaultCurrency) }
    val amountCents = CurrencyUtils.parseAmountToCents(amountText)
    val days = daysText.toIntOrNull()
    val isValid = amountCents != null && days != null && days > 0

    val haptic = rememberStrongHaptics()
    val amountFocusRequester = remember { FocusRequester() }

    // Nothing behind this dialog is usable until setup completes, and onDismiss is a
    // no-op — so an outside tap or back press must not start the exit animation, which
    // would leave the window in place at zero alpha with no way to bring it back.
    MemoDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = false,
        dismissOnBackPress = false
    ) { close ->
        // Route the confirm through `close` so the dialog plays its exit before the
        // caller's state flag removes it from the tree
        val submit = {
            if (amountCents != null && days != null && days > 0) {
                haptic.success()
                close { onConfirm(amountCents, days, selectedCurrency) }
            }
        }

        Text(
            text = "Welcome",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Let's set up your budget.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Amount Input
        MemoInput(
            value = amountText,
            onValueChange = {
                if (CurrencyUtils.isValidAmountInput(it)) {
                    amountText = it
                }
            },
            label = "Total Budget",
            placeholder = "e.g. 1000",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .autoFocusOnceAttached(amountFocusRequester)
        )

        if (amountText.isNotEmpty() && amountCents == null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter an amount greater than zero",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Red,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Days Input
        MemoInput(
            value = daysText,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() }) {
                    daysText = newValue
                }
            },
            label = "Number of Days",
            placeholder = "e.g. 30",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )

        if (daysText.isNotEmpty() && (days == null || days <= 0)) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter at least 1 day",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Red,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Currency Selector — Field fill so it matches the MemoInputs above it
        CurrencyPicker(
            selectedCurrency = selectedCurrency,
            onCurrencySelected = { selectedCurrency = it },
            modifier = Modifier.fillMaxWidth(),
            containerColor = AppColors.Field,
            borderColor = Color.Transparent
        )

        Spacer(modifier = Modifier.height(24.dp))

        val confirmInteraction = remember { MutableInteractionSource() }
        Button(
            onClick = submit,
            enabled = isValid,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Yellow,
                contentColor = AppColors.OnYellow,
                disabledContainerColor = AppColors.Disabled,
                disabledContentColor = AppColors.OnDisabled
            ),
            interactionSource = confirmInteraction,
            modifier = Modifier
                .springPress(confirmInteraction)
                .fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "Start Budget",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Preview
@Composable
fun SetupDialogPreview() {
    MemoTheme {
        SetupDialog(onConfirm = { _, _, _ -> }, onDismiss = {})
    }
}
