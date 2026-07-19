package com.imanhaikal.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.BudgetUiState
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.ui.components.PressScale
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.rememberStrongHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: BudgetUiState,
    onBack: () -> Unit,
    onSave: (Long, Int, String) -> Unit,
    onReset: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var budgetInput by rememberSaveable {
        mutableStateOf(CurrencyUtils.formatAmountInput(state.totalBudget))
    }
    var daysInput by rememberSaveable { mutableStateOf(state.totalDays.toString()) }
    var selectedCurrency by rememberSaveable { mutableStateOf(state.currencyCode) }
    var showCurrencyDropdown by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showCurrencyChangeDialog by rememberSaveable { mutableStateOf(false) }
    val haptic = rememberStrongHaptics()

    // We'll initialize with state values.
    // Ideally we want to detect changes to enable the save button.
    val currentBudgetCents = CurrencyUtils.parseAmountToCents(budgetInput)
    val currentDays = daysInput.toIntOrNull()
    
    val hasChanges = (currentBudgetCents != null && currentBudgetCents != state.totalBudget) ||
                      (currentDays != null && currentDays != state.totalDays) ||
                      (selectedCurrency != state.currencyCode)
    val isValid = currentBudgetCents != null && currentDays != null && currentDays > 0

    val performSave = {
        val validBudgetCents = currentBudgetCents
        val validDays = currentDays
        if (validBudgetCents != null && validDays != null && validDays > 0) {
            haptic.success()
            onSave(validBudgetCents, validDays, selectedCurrency)
            onBack() // Go back after saving
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .statusBarsPadding()
            .navigationBarsPadding()
            // Keep the form above the keyboard, moving in sync with the IME's own curve,
            // and let a downward drag on the form dismiss it interactively
            .imePadding()
            .imeNestedScroll()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppColors.TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = AppColors.TextPrimary
            )
        }

        // Configuration Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextSecondary
            )

            // Currency Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Currency",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextPrimary
                )
                
                Box {
                    OutlinedButton(
                        onClick = { showCurrencyDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppColors.TextPrimary,
                            containerColor = AppColors.Surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
                    ) {
                        Text(
                            text = CurrencyUtils.SUPPORTED_CURRENCIES[selectedCurrency] ?: selectedCurrency,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 4.dp).weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
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
                                        color = if(code == selectedCurrency) AppColors.TextPrimary else AppColors.TextSecondary,
                                        fontWeight = if(code == selectedCurrency) FontWeight.Bold else FontWeight.Normal
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Total Budget",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextPrimary
                )
                MemoInput(
                    value = budgetInput,
                    modifier = Modifier.testTag("BudgetInput"),
                    onValueChange = {
                        // Allow only numeric input
                        if (it.all { char -> char.isDigit() || char == '.' }) {
                            budgetInput = it 
                        }
                    },
                    placeholder = "0.00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cycle Length (Days)",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextPrimary
                )
                MemoInput(
                    value = daysInput,
                    modifier = Modifier.testTag("DaysInput"),
                    onValueChange = {
                        // Allow only integer input
                        if (it.all { char -> char.isDigit() }) {
                            daysInput = it
                        }
                    },
                    placeholder = "30",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            val saveInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    // Amounts are stored as plain numbers, so switching currency relabels
                    // recorded expenses without converting them — confirm before doing that
                    if (selectedCurrency != state.currencyCode && state.transactions.isNotEmpty()) {
                        haptic.tick()
                        showCurrencyChangeDialog = true
                    } else {
                        performSave()
                    }
                },
                enabled = isValid && hasChanges,
                interactionSource = saveInteraction,
                modifier = Modifier
                    .springPress(saveInteraction)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Yellow,
                    contentColor = AppColors.TextPrimary,
                    disabledContainerColor = Color.LightGray,
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Save Changes",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColors.Border)
        )

        // Danger Zone
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = AppColors.Red,
                    fontWeight = FontWeight.Bold
                ),
            )

            val resetInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .springPress(resetInteraction, pressedScale = PressScale.Surface)
                    // Clip before clickable so the ripple honors the rounded shape
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.RedSubtle)
                    .clickable(
                        interactionSource = resetInteraction,
                        indication = LocalIndication.current
                    ) {
                        haptic.tick()
                        showResetDialog = true
                    }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset All Data",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = AppColors.Red,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    if (showCurrencyChangeDialog) {
        val oldLabel = CurrencyUtils.SUPPORTED_CURRENCIES[state.currencyCode] ?: state.currencyCode
        val newLabel = CurrencyUtils.SUPPORTED_CURRENCIES[selectedCurrency] ?: selectedCurrency
        AlertDialog(
            onDismissRequest = { showCurrencyChangeDialog = false },
            title = { Text(text = "Change Currency?") },
            text = {
                Text(
                    text = "Your recorded expenses won't be converted. " +
                        "Amounts entered in $oldLabel will simply be shown as $newLabel instead."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCurrencyChangeDialog = false
                        performSave()
                    }
                ) {
                    Text("Change Anyway", color = AppColors.TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCurrencyChangeDialog = false }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            },
            containerColor = Color.White,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = "Reset All Data") },
            text = { Text(text = "Are you sure? This will delete all transactions and reset your budget settings. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.error()
                        onReset()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = AppColors.TextPrimary)
                }
            },
            containerColor = Color.White,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }
}
