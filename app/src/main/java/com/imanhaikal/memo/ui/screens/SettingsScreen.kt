package com.imanhaikal.memo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.data.ThemeMode
import com.imanhaikal.memo.ui.BudgetUiState
import com.imanhaikal.memo.ui.components.MemoIconButton
import com.imanhaikal.memo.ui.components.MemoInput
import com.imanhaikal.memo.ui.components.PressScale
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.CsvExporter
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.dismissKeyboardOnDragDown
import com.imanhaikal.memo.utils.rememberStrongHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: BudgetUiState,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsEnabledChange: (Boolean) -> Unit,
    scanAvailable: Boolean,
    onBack: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCategoryCaps: () -> Unit,
    onClearData: () -> Unit,
    onBuildBackup: suspend () -> String,
    onImportBackup: (contents: String, replace: Boolean, onFinished: (String) -> Unit) -> Unit,
    onPreviewBackup: (String) -> BackupSummary?,
    onSave: (Long, Int, String) -> Unit,
    onReset: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    // Keyed on the saved values so the form resyncs whenever they change
    // underneath us (e.g. after a reset) instead of showing stale numbers
    var budgetInput by rememberSaveable(state.totalBudget) {
        mutableStateOf(CurrencyUtils.formatAmountInput(state.totalBudget))
    }
    var daysInput by rememberSaveable(state.totalDays) { mutableStateOf(state.totalDays.toString()) }
    var selectedCurrency by rememberSaveable(state.currencyCode) { mutableStateOf(state.currencyCode) }
    var showCurrencyDropdown by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showCurrencyChangeDialog by rememberSaveable { mutableStateOf(false) }
    val haptic = rememberStrongHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // SAF document picker: the user chooses where the CSV lands, we just write it
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val transactions = state.transactions
            val currencyCode = state.currencyCode
            val budgetName = state.budgetName
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(
                                CsvExporter.buildCsv(
                                    transactions = transactions,
                                    currencyCode = currencyCode,
                                    budgetName = budgetName
                                ).toByteArray()
                            )
                        } != null
                    }.getOrDefault(false)
                }
                onMessage(
                    if (written) "Exported ${transactions.size} expenses" else "Export failed"
                )
            }
        }
    }

    // Full-fidelity JSON backup: everything CSV can't carry (budgets, cycles, caps,
    // recurring rules) so a reinstall can actually be undone.
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }

    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        val payload = onBuildBackup()
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(payload.toByteArray())
                        } != null
                    }.getOrDefault(false)
                }
                onMessage(if (written) "Backup saved" else "Backup failed")
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        // Some file pickers hand JSON back as octet-stream, so accept the wider types
        // rather than leaving the user's own backup greyed out.
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val contents = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.readBytes().decodeToString()
                        }
                    }.getOrNull()
                }
                if (contents == null) {
                    onMessage("Couldn't read that file")
                } else {
                    val summary = onPreviewBackup(contents)
                    if (summary == null) {
                        onMessage("That file isn't a Memo backup")
                    } else {
                        pendingImport = PendingImport(contents, summary)
                    }
                }
            }
        }
    }

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
        modifier = modifier
            .fillMaxSize()
            // Match the dashboard: cap and centre the column on wide screens
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = CONTENT_MAX_WIDTH)
            .padding(contentPadding)
            .statusBarsPadding()
            .navigationBarsPadding()
            // Keep the form above the keyboard, moving in sync with the IME's own curve,
            // and let a downward drag on the form dismiss it interactively
            .imePadding()
            .dismissKeyboardOnDragDown()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemoIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = AppColors.TextPrimary
            )
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

            // Appearance Selector — applies immediately, no Save needed
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeModeChip("System", themeMode == ThemeMode.SYSTEM) {
                        haptic.tick()
                        onThemeModeChange(ThemeMode.SYSTEM)
                    }
                    ThemeModeChip("Light", themeMode == ThemeMode.LIGHT) {
                        haptic.tick()
                        onThemeModeChange(ThemeMode.LIGHT)
                    }
                    ThemeModeChip("Dark", themeMode == ThemeMode.DARK) {
                        haptic.tick()
                        onThemeModeChange(ThemeMode.DARK)
                    }
                }
            }

            // Haptics — applies immediately, no Save needed. The device-wide touch
            // feedback setting still wins over this.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Haptics",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "Subtle vibration on taps, swipes and confirmations.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextTertiary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Confirm the switch by feel, but only once the new preference has
                // actually propagated — ticking inside onCheckedChange fires while
                // haptics are still disabled, so nothing would be felt.
                var hapticsWereEnabled by remember { mutableStateOf(hapticsEnabled) }
                LaunchedEffect(hapticsEnabled) {
                    if (hapticsEnabled && !hapticsWereEnabled) haptic.tick()
                    hapticsWereEnabled = hapticsEnabled
                }
                Switch(
                    checked = hapticsEnabled,
                    onCheckedChange = onHapticsEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.OnYellow,
                        checkedTrackColor = AppColors.Yellow,
                        checkedBorderColor = AppColors.Yellow,
                        uncheckedThumbColor = AppColors.TextTertiary,
                        uncheckedTrackColor = AppColors.Field,
                        uncheckedBorderColor = AppColors.Border
                    )
                )
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
                        if (CurrencyUtils.isValidAmountInput(it)) {
                            budgetInput = it
                        }
                    },
                    placeholder = "0.00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (budgetInput.isNotEmpty() && currentBudgetCents == null) {
                    Text(
                        text = "Enter an amount greater than zero",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Red
                    )
                }
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    // Last field in the form: Done commits rather than just closing
                    keyboardActions = KeyboardActions(onDone = { if (isValid && hasChanges) performSave() })
                )
                if (daysInput.isNotEmpty() && (currentDays == null || currentDays <= 0)) {
                    Text(
                        text = "Enter at least 1 day",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Red
                    )
                }
                Text(
                    text = "Changes apply to your current cycle right away — the cycle keeps its original start date.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary
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
                    contentColor = AppColors.OnYellow,
                    disabledContainerColor = AppColors.Disabled,
                    disabledContentColor = AppColors.OnDisabled
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

        // Manage Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Manage",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextSecondary
            )

            SettingsNavRow(
                title = "Budgets",
                subtitle = if (state.allBudgets.size > 1) {
                    "${state.allBudgets.size} budgets · ${state.budgetName} active"
                } else {
                    "Add a second budget, e.g. Travel"
                },
                onClick = onOpenBudgets
            )
            SettingsNavRow(
                title = "Category limits",
                subtitle = "Cap what you spend per category",
                onClick = onOpenCategoryCaps
            )
            SettingsNavRow(
                title = "Cycle history",
                subtitle = "Review finished cycles",
                onClick = onOpenHistory
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColors.Border)
        )

        // Data Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Data",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextSecondary
            )

            DataButton(
                label = "Back up everything (JSON)",
                onClick = {
                    haptic.tick()
                    backupExportLauncher.launch("memo-backup-${LocalDate.now()}.json")
                }
            )

            DataButton(
                label = "Restore from a backup",
                onClick = {
                    haptic.tick()
                    backupImportLauncher.launch(
                        arrayOf("application/json", "application/octet-stream", "text/plain")
                    )
                }
            )

            val exportInteraction = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = {
                    haptic.tick()
                    exportLauncher.launch("memo-expenses-${LocalDate.now()}.csv")
                },
                enabled = state.transactions.isNotEmpty(),
                interactionSource = exportInteraction,
                modifier = Modifier
                    .springPress(exportInteraction)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.TextPrimary,
                    disabledContentColor = AppColors.OnDisabled
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
            ) {
                Text(
                    text = if (state.transactions.isEmpty()) {
                        "No expenses to export"
                    } else {
                        "Export expenses (CSV)"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (!scanAvailable) {
                // Explain the missing scan FAB instead of leaving the feature invisible
                Text(
                    text = "Receipt scanning is unavailable in this build — it needs a " +
                        "GEMINI_API_KEY in local.properties at build time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary
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

            // Two separate outs: keep the budget but wipe its history, or remove the
            // budget entirely. Previously the only option deleted every row in the
            // database, including budgets the user wasn't even looking at.
            DangerAction(
                label = "Clear this budget's expenses",
                onClick = {
                    haptic.tick()
                    showClearDialog = true
                }
            )
            DangerAction(
                label = "Delete this budget",
                onClick = {
                    haptic.tick()
                    showResetDialog = true
                }
            )
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
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = "Delete \"${state.budgetName}\"?") },
            text = {
                Text(
                    text = "This deletes the budget, every expense recorded against it, " +
                        "and its cycle history. Other budgets are left alone. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.error()
                        onReset()
                        showResetDialog = false
                    }
                ) {
                    Text("Delete", color = AppColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = AppColors.TextPrimary)
                }
            },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }

    pendingImport?.let { pending ->
        ImportBackupDialog(
            summary = pending.summary,
            onMerge = {
                onImportBackup(pending.contents, false, onMessage)
                pendingImport = null
            },
            onReplace = {
                onImportBackup(pending.contents, true, onMessage)
                pendingImport = null
                onBack()
            },
            onDismiss = { pendingImport = null }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(text = "Clear expenses?") },
            text = {
                Text(
                    text = "This deletes every expense and the cycle history for " +
                        "\"${state.budgetName}\", but keeps the budget itself and its " +
                        "settings. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.error()
                        onClearData()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = AppColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = AppColors.TextPrimary)
                }
            },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }
}

/**
 * What an import will do, shown before anything is written. Replace is destructive, so it
 * gets the same red treatment as the Danger Zone rather than a bare "OK".
 */
@Composable
private fun ImportBackupDialog(
    summary: BackupSummary,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberStrongHaptics()
    var confirmingReplace by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (confirmingReplace) "Replace everything?" else "Restore backup") },
        text = {
            Text(
                text = if (confirmingReplace) {
                    "This deletes everything currently in Memo and replaces it with the " +
                        "backup. This can't be undone."
                } else {
                    buildString {
                        append("This backup holds ${summary.transactions} expenses")
                        if (summary.budgets > 0) append(" across ${summary.budgets} budgets")
                        summary.exportedOn?.let { append(", saved $it") }
                        append(".\n\n")
                        append("Add merges it in, skipping anything you already have. ")
                        append("Replace wipes Memo first.")
                    }
                }
            )
        },
        confirmButton = {
            if (confirmingReplace) {
                TextButton(
                    onClick = {
                        haptic.error()
                        onReplace()
                    }
                ) {
                    Text("Replace", color = AppColors.Red)
                }
            } else {
                TextButton(
                    onClick = {
                        haptic.success()
                        onMerge()
                    }
                ) {
                    Text("Add", color = AppColors.TextPrimary)
                }
            }
        },
        dismissButton = {
            if (confirmingReplace) {
                TextButton(onClick = { confirmingReplace = false }) {
                    Text("Back", color = AppColors.TextPrimary)
                }
            } else {
                Row {
                    TextButton(onClick = { confirmingReplace = true }) {
                        Text("Replace", color = AppColors.Red)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = AppColors.TextSecondary)
                    }
                }
            }
        },
        containerColor = AppColors.Surface,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary
    )
}

/** Full-width outlined action in the Data section. */
@Composable
private fun DataButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .springPress(interaction)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/** A tappable row that opens another screen. */
@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springPress(interaction, pressedScale = PressScale.Surface)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Field)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextTertiary
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextSecondary
        )
    }
}

/** A destructive, full-width action in the Danger Zone. */
@Composable
private fun DangerAction(
    label: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .springPress(interaction, pressedScale = PressScale.Surface)
            // Clip before clickable so the ripple honors the rounded shape
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.RedSubtle)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = AppColors.Red,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    // Cross-fade selection rather than hard-swapping the fill
    val background by animateColorAsState(
        targetValue = if (selected) AppColors.Yellow else AppColors.Field,
        animationSpec = tween(durationMillis = 180),
        label = "themeChipBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) AppColors.OnYellow else AppColors.TextSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "themeChipContent"
    )
    Box(
        modifier = Modifier
            .springPress(interaction, pressedScale = PressScale.Surface)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = contentColor
        )
    }
}
