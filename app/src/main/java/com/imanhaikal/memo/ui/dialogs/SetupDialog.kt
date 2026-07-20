package com.imanhaikal.memo.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.imanhaikal.memo.ui.components.MemoInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.imanhaikal.memo.utils.rememberStrongHaptics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.ui.theme.MemoTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.autoFocusOnceAttached
import kotlinx.coroutines.launch
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
    var showCurrencyDropdown by rememberSaveable { mutableStateOf(false) }
    val amountCents = CurrencyUtils.parseAmountToCents(amountText)
    val days = daysText.toIntOrNull()
    val isValid = amountCents != null && days != null && days > 0

    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }
    val haptic = rememberStrongHaptics()
    val amountFocusRequester = remember { FocusRequester() }

    val submit = {
        if (amountCents != null && days != null && days > 0) {
            haptic.success()
            onConfirm(amountCents, days, selectedCurrency)
        }
    }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = AppColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                // Currency Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showCurrencyDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppColors.TextPrimary,
                            containerColor = AppColors.Field
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent)
                    ) {
                        Text(
                            text = CurrencyUtils.SUPPORTED_CURRENCIES[selectedCurrency] ?: selectedCurrency,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }

                    DropdownMenu(
                        expanded = showCurrencyDropdown,
                        onDismissRequest = { showCurrencyDropdown = false },
                        modifier = Modifier.background(AppColors.Surface)
                    ) {
                        CurrencyUtils.SUPPORTED_CURRENCIES.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        color = if (code == selectedCurrency) AppColors.TextPrimary else AppColors.TextSecondary,
                                        fontWeight = if (code == selectedCurrency) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    haptic.tick()
                                    selectedCurrency = code
                                    showCurrencyDropdown = false
                                }
                            )
                        }
                    }
                }

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
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                }
            }
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
