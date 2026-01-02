package com.imanhaikal.memo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.ui.components.MemoFab
import com.imanhaikal.memo.ui.dialogs.AddExpenseDialog
import com.imanhaikal.memo.ui.dialogs.DeleteConfirmationDialog
import com.imanhaikal.memo.ui.dialogs.SetupDialog
import com.imanhaikal.memo.ui.screens.DashboardScreen
import com.imanhaikal.memo.ui.screens.SettingsScreen
import com.imanhaikal.memo.ui.theme.AppColors

@Composable
fun MemoApp(
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppColors.Background
    ) {
        // Main Scaffold
        Scaffold(
            floatingActionButton = {
                // Only show FAB if setup is complete and not in settings
                if (state.isSetup && !showSettings) {
                    MemoFab(onClick = {
                        transactionToEdit = null
                        showAddExpenseDialog = true
                    })
                }
            },
            containerColor = AppColors.Background
        ) { innerPadding ->
            if (!state.isLoading) {
                if (!state.isSetup) {
                    // Show Setup Dialog (Overlay)
                    // We can still show the dashboard behind it or just the dialog.
                    // Given the design usually implies a modal over content, but if not setup,
                    // the content might be empty or confusing.
                    // However, DashboardScreen handles empty states gracefully (usually).
                    // But to force setup, we show the dialog.
                    SetupDialog(
                        onConfirm = { amount, days ->
                            viewModel.setupBudget(amount, days)
                        },
                        onDismiss = { /* Not dismissible until setup */ }
                    )
                } else if (showSettings) {
                    SettingsScreen(
                        state = state,
                        onBack = { showSettings = false },
                        onSave = { amount, days ->
                            viewModel.updateBudget(amount, days)
                        },
                        onReset = {
                            viewModel.resetBudget()
                            showSettings = false
                        }
                    )
                } else {
                    // Show Dashboard
                    DashboardScreen(
                        state = state,
                        onOpenSettings = { showSettings = true },
                        onEditTransaction = { transaction ->
                            transactionToEdit = transaction
                            showAddExpenseDialog = true
                        },
                        onDeleteTransaction = { transaction ->
                            transactionToDelete = transaction
                        },
                        contentPadding = innerPadding
                    )
                }

                if (showAddExpenseDialog) {
                    AddExpenseDialog(
                        transaction = transactionToEdit,
                        onConfirm = { amount, note ->
                            if (transactionToEdit != null) {
                                viewModel.updateTransaction(transactionToEdit!!.copy(amount = amount, note = note))
                            } else {
                                viewModel.addTransaction(amount, note)
                            }
                            showAddExpenseDialog = false
                            transactionToEdit = null
                        },
                        onDelete = if (transactionToEdit != null) {
                            {
                                transactionToDelete = transactionToEdit
                                showAddExpenseDialog = false
                                transactionToEdit = null
                            }
                        } else null,
                        onDismiss = {
                            showAddExpenseDialog = false
                            transactionToEdit = null
                        }
                    )
                }

                if (transactionToDelete != null) {
                    DeleteConfirmationDialog(
                        onConfirm = {
                            viewModel.deleteTransaction(transactionToDelete!!)
                            transactionToDelete = null
                        },
                        onDismiss = {
                            transactionToDelete = null
                        }
                    )
                }
            }
        }
    }
}