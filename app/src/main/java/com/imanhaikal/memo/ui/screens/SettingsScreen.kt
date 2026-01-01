package com.imanhaikal.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.BudgetUiState
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.ui.theme.AppColors

@Composable
fun SettingsScreen(
    state: BudgetUiState,
    onBack: () -> Unit,
    onSave: (Double, Int) -> Unit,
    onReset: () -> Unit
) {
    var budgetInput by remember {
        mutableStateOf(
            if (state.totalBudget % 1.0 == 0.0) state.totalBudget.toInt().toString()
            else state.totalBudget.toString()
        )
    }
    var daysInput by remember { mutableStateOf(state.totalDays.toString()) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Update inputs when state changes (e.g. initial load)
    LaunchedEffect(state) {
        // Only update if not already modified? No, typically sync with state on open.
        // But if user is typing, we shouldn't overwrite.
        // For simplicity, we assume state is stable while editing.
        // To prevent overwriting while typing, we can just initialize once or check simple equality
        if (budgetInput.toDoubleOrNull() == state.totalBudget && daysInput.toIntOrNull() == state.totalDays) {
            // Already synced or initial state
        } else {
             // If state changes externally, we might want to update, but usually this is just initial.
        }
    }
    
    // We'll initialize with state values.
    // Ideally we want to detect changes to enable the save button.
    val currentBudget = budgetInput.toDoubleOrNull()
    val currentDays = daysInput.toIntOrNull()
    
    val hasChanges = (currentBudget != null && currentBudget != state.totalBudget) ||
                     (currentDays != null && currentDays != state.totalDays)
    val isValid = currentBudget != null && currentBudget > 0 && currentDays != null && currentDays > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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

            Button(
                onClick = {
                    if (isValid) {
                        onSave(currentBudget!!, currentDays!!)
                        onBack() // Go back after saving
                    }
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.RedSubtle, RoundedCornerShape(12.dp))
                    .clickable { showResetDialog = true }
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

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = "Reset All Data") },
            text = { Text(text = "Are you sure? This will delete all transactions and reset your budget settings. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
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