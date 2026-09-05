package com.imanhaikal.memo.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionType
import com.imanhaikal.memo.data.backup.ImportMode
import com.imanhaikal.memo.ui.components.DashboardSkeleton
import com.imanhaikal.memo.ui.components.MemoFab
import com.imanhaikal.memo.ui.components.MemoScanFab
import com.imanhaikal.memo.ui.dialogs.AddExpenseDialog
import com.imanhaikal.memo.ui.dialogs.ScanErrorDialog
import com.imanhaikal.memo.ui.dialogs.ScanReceiptChooserSheet
import com.imanhaikal.memo.ui.dialogs.ScanningReceiptDialog
import com.imanhaikal.memo.ui.dialogs.SetupDialog
import com.imanhaikal.memo.ui.navigation.MemoNavHost
import com.imanhaikal.memo.ui.navigation.Screen
import com.imanhaikal.memo.ui.navigation.rememberMemoBackStack
import com.imanhaikal.memo.ui.screens.BackupSummary
import com.imanhaikal.memo.ui.screens.BudgetsScreen
import com.imanhaikal.memo.ui.screens.CategoryCapsScreen
import com.imanhaikal.memo.ui.screens.CycleHistoryScreen
import com.imanhaikal.memo.ui.screens.DashboardScreen
import com.imanhaikal.memo.ui.screens.RecurringScreen
import com.imanhaikal.memo.ui.screens.SearchScreen
import com.imanhaikal.memo.ui.screens.SettingsScreen
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.DateLabels
import com.imanhaikal.memo.utils.ImageUtils
import com.imanhaikal.memo.utils.LocalHapticsEnabled
import com.imanhaikal.memo.utils.rememberStrongHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun MemoApp(
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
    /** Emits true when the widget or a launcher shortcut asked to add an expense. */
    quickAddRequests: MutableStateFlow<Boolean> = remember { MutableStateFlow(false) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val cycleHistory by viewModel.cycleHistory.collectAsStateWithLifecycle()
    val categoryCaps by viewModel.categoryCaps.collectAsStateWithLifecycle()
    val recurringRules by viewModel.recurringRules.collectAsStateWithLifecycle()
    val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val searchCriteria by viewModel.searchCriteria.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    // Wraps everything below, so every rememberStrongHaptics() in the tree — including
    // the one this function uses for Undo — sees the user's preference
    CompositionLocalProvider(LocalHapticsEnabled provides hapticsEnabled) {
    var showAddExpenseDialog by rememberSaveable { mutableStateOf(false) }
    var transactionToEditId by rememberSaveable { mutableStateOf<Int?>(null) }
    var addAsIncome by rememberSaveable { mutableStateOf(false) }
    var showScanChooser by rememberSaveable { mutableStateOf(false) }
    // Uri kept as String so it survives process death while the camera app is open
    var cameraImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val backStack = rememberMemoBackStack()
    val transactionToEdit = remember(transactionToEditId, state.transactions) {
        state.transactions.firstOrNull { it.id == transactionToEditId }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val quickAdd by quickAddRequests.collectAsStateWithLifecycle()

    // Opening from the widget lands straight in the add dialog, but only once the user
    // actually has a budget — otherwise setup is the thing that needs answering first.
    LaunchedEffect(quickAdd, state.isSetup) {
        if (quickAdd && state.isSetup) {
            transactionToEditId = null
            addAsIncome = false
            showAddExpenseDialog = true
            quickAddRequests.value = false
        }
    }
    val scope = rememberCoroutineScope()
    val haptic = rememberStrongHaptics()

    // One styled surface for every transient message, rather than dropping to
    // system-themed Toasts that ignore the app's palette
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    // Deletes take effect immediately; the snackbar's Undo restores the exact row
    val deleteWithUndo: (Transaction) -> Unit = { transaction ->
        viewModel.deleteTransaction(transaction)
        // Replace any showing snackbar so rapid deletes don't queue up and
        // silently burn their undo windows while waiting to be displayed
        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Expense deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                // Undo is a real action and the row reappears — acknowledge it
                haptic.tick()
                viewModel.restoreTransaction(transaction)
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(viewModel::scanReceipt)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraImageUriString?.let(Uri::parse)
        if (success && uri != null) viewModel.scanReceipt(uri)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppColors.Background
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(16.dp),
                        containerColor = AppColors.InverseSurface,
                        contentColor = AppColors.OnInverse,
                        actionColor = AppColors.Yellow
                    )
                }
            },
            floatingActionButton = {
                // Only show FABs if setup is complete and we're on the dashboard
                if (state.isSetup && backStack.current == Screen.Dashboard) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (viewModel.isScanAvailable) {
                            MemoScanFab(onClick = {
                                viewModel.viewModelScope.launch(Dispatchers.IO) {
                                    ImageUtils.purgeReceiptCaptures(context)
                                }
                                showScanChooser = true
                            })
                        }
                        MemoFab(onClick = {
                            transactionToEditId = null
                            addAsIncome = false
                            showAddExpenseDialog = true
                        })
                    }
                }
            },
            containerColor = AppColors.Background
        ) { innerPadding ->
            if (state.isLoading) {
                // The splash covers the first load, but a reset, a slow cold read or the
                // one-time DataStore-to-Room handoff can land here later — show the shape
                // of the dashboard, never a bare screen
                DashboardSkeleton(contentPadding = innerPadding)
            } else {
                if (!state.isSetup) {
                    // Force setup before showing any content
                    SetupDialog(
                        onConfirm = { amount, days, currency ->
                            viewModel.setupBudget(amount, days, currency)
                        },
                        onDismiss = { /* Not dismissible until setup */ }
                    )
                } else {
                    MemoNavHost(backStack = backStack) { screen, screenModifier ->
                        when (screen) {
                            Screen.Dashboard -> DashboardScreen(
                                state = state,
                                onOpenSettings = { backStack.push(Screen.Settings) },
                                onOpenBudgets = { backStack.push(Screen.Budgets) },
                                onOpenHistory = { backStack.push(Screen.CycleHistory) },
                                onOpenCategoryCaps = { backStack.push(Screen.CategoryCaps) },
                                onOpenSearch = { backStack.push(Screen.Search) },
                                onAddExpense = {
                                    transactionToEditId = null
                                    addAsIncome = false
                                    showAddExpenseDialog = true
                                },
                                onEditTransaction = { transaction ->
                                    transactionToEditId = transaction.id
                                    showAddExpenseDialog = true
                                },
                                onDeleteTransaction = deleteWithUndo,
                                contentPadding = innerPadding,
                                modifier = screenModifier
                            )

                            Screen.Settings -> SettingsScreen(
                                state = state,
                                themeMode = themeMode,
                                onThemeModeChange = viewModel::setThemeMode,
                                hapticsEnabled = hapticsEnabled,
                                onHapticsEnabledChange = viewModel::setHapticsEnabled,
                                scanAvailable = viewModel.isScanAvailable,
                                onBack = { backStack.pop() },
                                onOpenBudgets = { backStack.push(Screen.Budgets) },
                                onOpenHistory = { backStack.push(Screen.CycleHistory) },
                                onOpenCategoryCaps = { backStack.push(Screen.CategoryCaps) },
                                onOpenRecurring = { backStack.push(Screen.Recurring) },
                                notificationSettings = notificationSettings,
                                onNotificationSettingsChange = viewModel::updateNotificationSettings,
                                onSave = { amount, days, currency ->
                                    viewModel.updateBudget(amount, days, currency)
                                },
                                onClearData = { viewModel.clearActiveBudgetData() },
                                onBuildBackup = { viewModel.buildBackupJson() },
                                onImportBackup = { contents, replace, onFinished ->
                                    viewModel.importBackup(
                                        contents = contents,
                                        mode = if (replace) ImportMode.REPLACE else ImportMode.MERGE,
                                        onFinished = onFinished
                                    )
                                },
                                onPreviewBackup = { contents ->
                                    viewModel.previewBackup(contents)?.let { backup ->
                                        BackupSummary(
                                            budgets = backup.budgets.size,
                                            transactions = backup.transactions.size,
                                            exportedOn = DateLabels.relativeDayLabel(
                                                Instant.ofEpochMilli(backup.exportedAtMillis)
                                                    .atZone(ZoneId.systemDefault())
                                                    .toLocalDate()
                                            )
                                        )
                                    }
                                },
                                onReset = {
                                    viewModel.resetBudget()
                                    backStack.popToRoot()
                                },
                                onMessage = showMessage,
                                contentPadding = innerPadding,
                                modifier = screenModifier
                            )

                            Screen.Budgets -> BudgetsScreen(
                                budgets = state.allBudgets,
                                activeBudgetId = state.budgetId,
                                onSelect = viewModel::selectBudget,
                                onCreate = { name, amount, days, currency ->
                                    viewModel.createBudget(name, amount, days, currency)
                                },
                                onRename = viewModel::renameBudget,
                                onArchive = viewModel::setBudgetArchived,
                                onDelete = viewModel::deleteBudget,
                                onBack = { backStack.pop() },
                                contentPadding = innerPadding,
                                modifier = screenModifier
                            )

                            Screen.CycleHistory -> CycleHistoryScreen(
                                cycles = cycleHistory,
                                currencyCode = state.currencyCode,
                                onBack = { backStack.pop() },
                                contentPadding = innerPadding,
                                modifier = screenModifier
                            )

                            Screen.Recurring -> RecurringScreen(
                                rules = recurringRules,
                                currencyCode = state.currencyCode,
                                onSave = viewModel::saveRecurringRule,
                                onSetPaused = viewModel::setRecurringPaused,
                                onDelete = viewModel::deleteRecurringRule,
                                onBack = { backStack.pop() },
                                contentPadding = innerPadding,
                                modifier = screenModifier
                            )

                            Screen.Search -> SearchScreen(
                                criteria = searchCriteria,
                                results = searchResults,
                                currencyCode = state.currencyCode,
                                onCriteriaChange = viewModel::updateSearch,
                                onEditTransaction = { transaction ->
                                    transactionToEditId = transaction.id
                                    showAddExpenseDialog = true
                                },
                                onDeleteTransaction = deleteWithUndo,
                                onBack = {
                                    viewModel.clearSearch()
                                    backStack.pop()
                                },
                                contentPadding = innerPadding,
                                modifier = screenModifier
                            )

                            Screen.CategoryCaps -> CategoryCapsScreen(
                                caps = categoryCaps,
                                currencyCode = state.currencyCode,
                                onCapChanged = viewModel::setCategoryCap,
                                onBack = { backStack.pop() },
                                contentPadding = innerPadding,
                                modifier = screenModifier
                            )
                        }
                    }
                }

                if (showAddExpenseDialog) {
                    AddExpenseDialog(
                        transaction = transactionToEdit,
                        initialType = if (addAsIncome) {
                            TransactionType.INCOME
                        } else {
                            TransactionType.EXPENSE
                        },
                        onConfirm = { draft ->
                            if (transactionToEdit != null) {
                                viewModel.updateTransaction(
                                    transactionToEdit.copy(
                                        amount = draft.amountCents,
                                        note = draft.note,
                                        date = draft.dateMillis ?: transactionToEdit.date,
                                        category = draft.category,
                                        description = draft.description,
                                        hasTime = draft.hasTime,
                                        type = draft.type
                                    )
                                )
                            } else {
                                viewModel.addTransaction(
                                    amountCents = draft.amountCents,
                                    note = draft.note,
                                    dateMillis = draft.dateMillis,
                                    category = draft.category,
                                    description = draft.description,
                                    hasTime = draft.hasTime,
                                    type = draft.type
                                )
                            }
                            showAddExpenseDialog = false
                            transactionToEditId = null
                        },
                        onDelete = if (transactionToEdit != null) {
                            {
                                showAddExpenseDialog = false
                                transactionToEditId = null
                                deleteWithUndo(transactionToEdit)
                            }
                        } else null,
                        onDismiss = {
                            showAddExpenseDialog = false
                            transactionToEditId = null
                        }
                    )
                }

                if (showScanChooser) {
                    ScanReceiptChooserSheet(
                        onCamera = {
                            showScanChooser = false
                            val uri = ImageUtils.createReceiptCaptureUri(context)
                            cameraImageUriString = uri.toString()
                            try {
                                cameraLauncher.launch(uri)
                            } catch (e: ActivityNotFoundException) {
                                // Devices without a camera app (some tablets/emulators)
                                showMessage("No camera app available — choose from gallery instead")
                            }
                        },
                        onGallery = {
                            showScanChooser = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onDismiss = { showScanChooser = false }
                    )
                }

                when (val scan = scanState) {
                    is ScanState.Processing -> ScanningReceiptDialog(
                        onCancel = { viewModel.cancelScan() }
                    )
                    is ScanState.Success -> AddExpenseDialog(
                        initialAmountCents = scan.amountCents,
                        initialNote = scan.note,
                        initialCategory = scan.category,
                        initialDescription = scan.description.ifBlank { null },
                        initialDateMillis = scan.dateMillis,
                        initialDateHasTime = scan.dateHasTime,
                        // A stray outside tap must not throw away the scanned receipt
                        dismissOnClickOutside = false,
                        onConfirm = { draft ->
                            viewModel.addTransaction(
                                amountCents = draft.amountCents,
                                note = draft.note,
                                dateMillis = draft.dateMillis,
                                category = draft.category,
                                description = draft.description,
                                hasTime = draft.hasTime,
                                type = draft.type
                            )
                            viewModel.clearScanState()
                        },
                        onDismiss = { viewModel.clearScanState() }
                    )
                    is ScanState.Error -> ScanErrorDialog(
                        reason = scan.reason,
                        onRetry = {
                            viewModel.clearScanState()
                            showScanChooser = true
                        },
                        onManual = {
                            viewModel.clearScanState()
                            transactionToEditId = null
                            addAsIncome = false
                            showAddExpenseDialog = true
                        },
                        onDismiss = { viewModel.clearScanState() }
                    )
                    is ScanState.Idle -> Unit
                }
            }
        }
    }
    }
}
