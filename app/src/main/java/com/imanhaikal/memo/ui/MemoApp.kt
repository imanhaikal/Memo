package com.imanhaikal.memo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddExpenseDialog by rememberSaveable { mutableStateOf(false) }
    var transactionToEditId by rememberSaveable { mutableStateOf<Int?>(null) }
    var transactionToDeleteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val transactionToEdit = state.transactions.firstOrNull { it.id == transactionToEditId }
    val transactionToDelete = state.transactions.firstOrNull { it.id == transactionToDeleteId }

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
                        transactionToEditId = null
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
                        onSave = { amount, days, currency ->
                            viewModel.updateBudget(amount, days, currency)
                        },
                        onReset = {
                            viewModel.resetBudget()
                            showSettings = false
                        },
                        contentPadding = innerPadding
                    )
                } else {
                    // Show Dashboard
                    DashboardScreen(
                        state = state,
                        onOpenSettings = { showSettings = true },
                        onEditTransaction = { transaction ->
                            transactionToEditId = transaction.id
                            showAddExpenseDialog = true
                        },
                        onDeleteTransaction = { transaction ->
                            transactionToDeleteId = transaction.id
                        },
                        contentPadding = innerPadding
                    )
                }

                if (showAddExpenseDialog) {
                    AddExpenseDialog(
                        transaction = transactionToEdit,
                        onConfirm = { amount, note ->
                            if (transactionToEdit != null) {
                                viewModel.updateTransaction(transactionToEdit.copy(amount = amount, note = note))
                            } else {
                                viewModel.addTransaction(amount, note)
                            }
                            showAddExpenseDialog = false
                            transactionToEditId = null
                        },
                        onDelete = if (transactionToEdit != null) {
                            {
                                transactionToDeleteId = transactionToEdit.id
                                showAddExpenseDialog = false
                                transactionToEditId = null
                            }
                        } else null,
                        onDismiss = {
                            showAddExpenseDialog = false
                            transactionToEditId = null
                        }
                    )
                }

                if (transactionToDelete != null) {
                    DeleteConfirmationDialog(
                        onConfirm = {
                            viewModel.deleteTransaction(transactionToDelete)
                            transactionToDeleteId = null
                        },
                        onDismiss = {
                            transactionToDeleteId = null
                        }
                    )
                }
            }
        }
    }
}
