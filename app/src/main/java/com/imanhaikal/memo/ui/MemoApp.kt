package com.imanhaikal.memo.ui

import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.imanhaikal.memo.data.receipt.GalleryPublisher
import android.net.Uri
import androidx.core.content.FileProvider
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
import com.imanhaikal.memo.data.backup.ImportMode
import com.imanhaikal.memo.ui.components.DashboardSkeleton
import com.imanhaikal.memo.ui.components.MemoFab
import com.imanhaikal.memo.ui.components.MemoScanFab
import com.imanhaikal.memo.ui.dialogs.AddExpenseDialog
import com.imanhaikal.memo.ui.dialogs.ReceiptSourceSheet
import com.imanhaikal.memo.ui.dialogs.ReceiptViewerDialog
import com.imanhaikal.memo.ui.dialogs.ScanErrorDialog
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
    val receiptStorage by viewModel.receiptStorage.collectAsStateWithLifecycle()

    // Wraps everything below, so every rememberStrongHaptics() in the tree — including
    // the one this function uses for Undo — sees the user's preference
    CompositionLocalProvider(LocalHapticsEnabled provides hapticsEnabled) {
    var showAddExpenseDialog by rememberSaveable { mutableStateOf(false) }
    var transactionToEditId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showScanChooser by rememberSaveable { mutableStateOf(false) }
    // Uri kept as String so it survives process death while the camera app is open
    var cameraImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    // A stored file name, never a Uri: the image is copied into internal storage the moment
    // the picker returns, so this survives process death without a content:// grant — which
    // is exactly what the camera flow above cannot rely on.
    var pendingReceiptFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var showAttachChooser by rememberSaveable { mutableStateOf(false) }
    var viewerFileName by rememberSaveable { mutableStateOf<String?>(null) }
    // Which flow the next picker result belongs to. A Boolean rather than an enum so
    // rememberSaveable needs no custom Saver.
    var pickForAttach by rememberSaveable { mutableStateOf(false) }
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
            pendingReceiptFileName = null
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
    // Copy into internal storage first, always, and only then scan. The read grant on a
    // picker uri lasts no longer than the process, and being killed while the camera app is
    // foregrounded is routine — deferring the copy to the moment the user taps Add would
    // lose the image silently on exactly that path.
    val keepReceipt: (Uri) -> Unit = { uri ->
        viewModel.saveReceiptImage(uri) { fileName ->
            // Only overwrite on success. Clearing here would mean a failed *replace* on an
            // entry that already had a receipt silently dropped the one it had — the user
            // asked for a different image, not for no image.
            if (fileName == null) {
                showMessage("Couldn't save that image")
            } else {
                pendingReceiptFileName = fileName
            }
        }
    }
    val galleryCopy: (Uri) -> Unit = { uri ->
        viewModel.copyCaptureToGallery(uri) { copied ->
            // Quiet on success — the photo simply shows up in the gallery. A failure is
            // worth a word, since the user may be counting on that copy.
            if (!copied) showMessage("Couldn't save a copy to your gallery")
        }
    }
    // Android 8-9 only: shared storage needs a runtime grant there. The capture waits here
    // as a string while the permission dialog is up, like cameraImageUriString above.
    var galleryCopyAwaitingPermission by rememberSaveable { mutableStateOf<String?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val uri = galleryCopyAwaitingPermission?.let(Uri::parse)
        galleryCopyAwaitingPermission = null
        if (uri == null) return@rememberLauncherForActivityResult
        if (granted) {
            galleryCopy(uri)
        } else {
            // The receipt itself is already saved in the app; only the extra copy is skipped.
            showMessage("Receipt saved in Memo, but not to your gallery")
        }
    }
    val copyCaptureToGallery: (Uri) -> Unit = { uri ->
        val needsGrant = GalleryPublisher.needsStoragePermission &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsGrant) {
            galleryCopyAwaitingPermission = uri.toString()
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            galleryCopy(uri)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // Consumed here, on every result including a cancel, so the flag can never outlive
        // the pick it was set for.
        val forAttach = pickForAttach
        pickForAttach = false
        if (uri != null) {
            keepReceipt(uri)
            if (!forAttach) viewModel.scanReceipt(uri)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val forAttach = pickForAttach
        pickForAttach = false
        val uri = cameraImageUriString?.let(Uri::parse)
        if (success && uri != null) {
            keepReceipt(uri)
            // Camera captures only. A gallery pick is already in the gallery, and copying it
            // back would duplicate every receipt the user chose from there.
            copyCaptureToGallery(uri)
            if (!forAttach) viewModel.scanReceipt(uri)
        }
    }
    val launchCamera: () -> Unit = {
        val uri = ImageUtils.createReceiptCaptureUri(context)
        cameraImageUriString = uri.toString()
        try {
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            // Devices without a camera app (some tablets/emulators)
            showMessage("No camera app available — choose from gallery instead")
        }
    }
    val launchGallery: () -> Unit = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    val openViewer: (String) -> Unit = { viewerFileName = it }

    // Resolved once per change rather than on every recomposition — it stats the file.
    val pendingReceiptFile = remember(pendingReceiptFileName) {
        pendingReceiptFileName?.let(viewModel::receiptFile)
    }

    val shareReceipt: (String) -> Unit = { fileName ->
        val file = viewModel.receiptFile(fileName)
        if (file == null) {
            showMessage("That receipt image isn't on this device")
        } else {
            // Needs the <files-path> root in res/xml/file_paths.xml; FileProvider throws
            // "Failed to find configured root" without it.
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(intent, "Share receipt"))
            } catch (e: ActivityNotFoundException) {
                showMessage("Nothing on this device can share an image")
            }
        }
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
                                pickForAttach = false
                                pendingReceiptFileName = null
                                showScanChooser = true
                            })
                        }
                        MemoFab(onClick = {
                            transactionToEditId = null
                            pendingReceiptFileName = null
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
                                    pendingReceiptFileName = null
                                    showAddExpenseDialog = true
                                },
                                onEditTransaction = { transaction ->
                                    transactionToEditId = transaction.id
                                    pendingReceiptFileName = transaction.receiptFileName
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
                                receiptStorage = receiptStorage,
                                onRefreshReceiptStorage = viewModel::refreshReceiptStorage,
                                onDeleteAllReceipts = viewModel::deleteAllReceipts,
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
                                    pendingReceiptFileName = transaction.receiptFileName
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
                        receiptFileName = pendingReceiptFileName,
                        receiptFile = pendingReceiptFile,
                        onAttachReceipt = {
                            pickForAttach = true
                            showAttachChooser = true
                        },
                        onViewReceipt = openViewer,
                        onRemoveReceipt = { pendingReceiptFileName = null },
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
                                        type = draft.type,
                                        receiptFileName = draft.receiptFileName
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
                                    type = draft.type,
                                    receiptFileName = draft.receiptFileName
                                )
                            }
                            showAddExpenseDialog = false
                            transactionToEditId = null
                            pendingReceiptFileName = null
                        },
                        onDelete = if (transactionToEdit != null) {
                            {
                                showAddExpenseDialog = false
                                transactionToEditId = null
                                pendingReceiptFileName = null
                                deleteWithUndo(transactionToEdit)
                            }
                        } else null,
                        onDismiss = {
                            showAddExpenseDialog = false
                            transactionToEditId = null
                            // Whatever was staged and not confirmed is now an orphan; the
                            // startup sweep collects it. Deleting it here would be wrong —
                            // on an edit it may be the image the row still points at.
                            pendingReceiptFileName = null
                        }
                    )
                }

                if (showScanChooser) {
                    ReceiptSourceSheet(
                        onCamera = {
                            showScanChooser = false
                            launchCamera()
                        },
                        onGallery = {
                            showScanChooser = false
                            launchGallery()
                        },
                        onDismiss = { showScanChooser = false }
                    )
                }

                if (showAttachChooser) {
                    ReceiptSourceSheet(
                        title = "Attach Receipt",
                        message = "Keep a photo of the receipt with this entry. " +
                            "Photos you take are also saved to your gallery.",
                        onCamera = {
                            showAttachChooser = false
                            launchCamera()
                        },
                        onGallery = {
                            showAttachChooser = false
                            launchGallery()
                        },
                        onDismiss = {
                            showAttachChooser = false
                            pickForAttach = false
                        }
                    )
                }

                viewerFileName?.let { fileName ->
                    ReceiptViewerDialog(
                        file = viewModel.receiptFile(fileName),
                        onShare = { shareReceipt(fileName) },
                        // Only ever opened from the add/edit dialog, where removing is
                        // staged rather than committed — the row is written on confirm.
                        onDelete = {
                            if (pendingReceiptFileName == fileName) pendingReceiptFileName = null
                            viewerFileName = null
                        },
                        onDismiss = { viewerFileName = null }
                    )
                }

                when (val scan = scanState) {
                    is ScanState.Processing -> ScanningReceiptDialog(
                        onCancel = { viewModel.cancelScan() }
                    )
                    is ScanState.Success -> AddExpenseDialog(
                        // The scanned image is already saved, so it arrives pre-attached —
                        // which is also how the user finds out it is being kept, and where
                        // they remove it if they would rather it weren't.
                        receiptFileName = pendingReceiptFileName,
                        receiptFile = pendingReceiptFile,
                        onAttachReceipt = {
                            pickForAttach = true
                            showAttachChooser = true
                        },
                        onViewReceipt = openViewer,
                        onRemoveReceipt = { pendingReceiptFileName = null },
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
                                type = draft.type,
                                receiptFileName = draft.receiptFileName
                            )
                            viewModel.clearScanState()
                            pendingReceiptFileName = null
                        },
                        onDismiss = {
                            viewModel.clearScanState()
                            pendingReceiptFileName = null
                        }
                    )
                    is ScanState.Error -> ScanErrorDialog(
                        reason = scan.reason,
                        onRetry = {
                            viewModel.clearScanState()
                            pendingReceiptFileName = null
                            showScanChooser = true
                        },
                        onManual = {
                            viewModel.clearScanState()
                            transactionToEditId = null
                            // Deliberately kept: the scan failed to read the receipt, but
                            // the photo saved fine and is worth attaching to the entry the
                            // user is about to type by hand.
                            showAddExpenseDialog = true
                        },
                        onDismiss = {
                            viewModel.clearScanState()
                            pendingReceiptFileName = null
                        }
                    )
                    is ScanState.Idle -> Unit
                }
            }
        }
    }
    }
}
