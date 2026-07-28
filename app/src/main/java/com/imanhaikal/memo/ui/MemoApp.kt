package com.imanhaikal.memo.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import com.imanhaikal.memo.ui.components.MemoFab
import com.imanhaikal.memo.ui.components.MemoScanFab
import com.imanhaikal.memo.ui.dialogs.AddExpenseDialog
import com.imanhaikal.memo.ui.dialogs.ScanErrorDialog
import com.imanhaikal.memo.ui.dialogs.ScanReceiptChooserDialog
import com.imanhaikal.memo.ui.dialogs.ScanningReceiptDialog
import com.imanhaikal.memo.ui.dialogs.SetupDialog
import com.imanhaikal.memo.ui.screens.DashboardScreen
import com.imanhaikal.memo.ui.screens.SettingsScreen
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MemoApp(
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showAddExpenseDialog by rememberSaveable { mutableStateOf(false) }
    var transactionToEditId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showScanChooser by rememberSaveable { mutableStateOf(false) }
    // Uri kept as String so it survives process death while the camera app is open
    var cameraImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val transactionToEdit = remember(transactionToEditId, state.transactions) {
        state.transactions.firstOrNull { it.id == transactionToEditId }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        // Main Scaffold
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
                // Only show FABs if setup is complete and not in settings
                if (state.isSetup && !showSettings) {
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
                            showAddExpenseDialog = true
                        })
                    }
                }
            },
            containerColor = AppColors.Background
        ) { innerPadding ->
            if (!state.isLoading) {
                if (!state.isSetup) {
                    // Force setup before showing any content
                    SetupDialog(
                        onConfirm = { amount, days, currency ->
                            viewModel.setupBudget(amount, days, currency)
                        },
                        onDismiss = { /* Not dismissible until setup */ }
                    )
                } else {
                    // System back mirrors the in-app back affordance
                    BackHandler(enabled = showSettings) { showSettings = false }

                    // Shared-axis-X style transition between Dashboard and Settings
                    AnimatedContent(
                        targetState = showSettings,
                        transitionSpec = {
                            val duration = 300
                            val distance = 60
                            if (targetState) {
                                (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { distance } +
                                    fadeIn(tween(duration))) togetherWith
                                    (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -distance } +
                                        fadeOut(tween(duration)))
                            } else {
                                (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -distance } +
                                    fadeIn(tween(duration))) togetherWith
                                    (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { distance } +
                                        fadeOut(tween(duration)))
                            }
                        },
                        label = "screenNav"
                    ) { inSettings ->
                        if (inSettings) {
                            SettingsScreen(
                                state = state,
                                themeMode = themeMode,
                                onThemeModeChange = viewModel::setThemeMode,
                                scanAvailable = viewModel.isScanAvailable,
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
                            DashboardScreen(
                                state = state,
                                onOpenSettings = { showSettings = true },
                                onEditTransaction = { transaction ->
                                    transactionToEditId = transaction.id
                                    showAddExpenseDialog = true
                                },
                                onDeleteTransaction = deleteWithUndo,
                                contentPadding = innerPadding
                            )
                        }
                    }
                }

                if (showAddExpenseDialog) {
                    AddExpenseDialog(
                        transaction = transactionToEdit,
                        onConfirm = { amount, note, dateMillis, hasTime, category, description ->
                            if (transactionToEdit != null) {
                                viewModel.updateTransaction(
                                    transactionToEdit.copy(
                                        amount = amount,
                                        note = note,
                                        date = dateMillis ?: transactionToEdit.date,
                                        category = category,
                                        description = description,
                                        hasTime = hasTime
                                    )
                                )
                            } else {
                                viewModel.addTransaction(amount, note, dateMillis, category, description, hasTime)
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
                    ScanReceiptChooserDialog(
                        onCamera = {
                            showScanChooser = false
                            val uri = ImageUtils.createReceiptCaptureUri(context)
                            cameraImageUriString = uri.toString()
                            try {
                                cameraLauncher.launch(uri)
                            } catch (e: ActivityNotFoundException) {
                                // Devices without a camera app (some tablets/emulators)
                                Toast.makeText(
                                    context,
                                    "No camera app available — choose from gallery instead",
                                    Toast.LENGTH_SHORT
                                ).show()
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
                        onConfirm = { amount, note, dateMillis, hasTime, category, description ->
                            viewModel.addTransaction(amount, note, dateMillis, category, description, hasTime)
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
