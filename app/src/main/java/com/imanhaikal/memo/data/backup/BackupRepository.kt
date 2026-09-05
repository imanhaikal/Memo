package com.imanhaikal.memo.data.backup

import com.imanhaikal.memo.data.ActiveBudgetStore
import com.imanhaikal.memo.data.Budget
import com.imanhaikal.memo.data.BudgetCycle
import com.imanhaikal.memo.data.BudgetCycleDao
import com.imanhaikal.memo.data.BudgetDao
import com.imanhaikal.memo.data.Cadence
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.CategoryCap
import com.imanhaikal.memo.data.CategoryCapDao
import com.imanhaikal.memo.data.RecurringRule
import com.imanhaikal.memo.data.RecurringRuleDao
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.data.TransactionDao
import com.imanhaikal.memo.data.TransactionRunner
import com.imanhaikal.memo.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Clock

/**
 * Whole-database export and import as a plain, schema-versioned JSON file.
 *
 * CSV export stays as it is — it is for reading a ledger in a spreadsheet, and it is lossy.
 * This is the one that can actually put a phone back the way it was.
 *
 * The database is deliberately excluded from Android's cloud backup (see
 * `res/xml/backup_rules.xml`), so this is user-driven: they choose the file and where it
 * goes, and nothing leaves the device on its own.
 */
class BackupRepository(
    private val runInTransaction: TransactionRunner,
    private val budgetDao: BudgetDao,
    private val budgetCycleDao: BudgetCycleDao,
    private val categoryCapDao: CategoryCapDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val transactionDao: TransactionDao,
    private val activeBudgetStore: ActiveBudgetStore,
    private val clock: Clock,
    private val appVersionCode: Int = 0
) {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun export(): String {
        val backup = MemoBackup(
            exportedAtMillis = clock.millis(),
            appVersionCode = appVersionCode,
            activeBudgetId = activeBudgetStore.activeBudgetId.first(),
            budgets = budgetDao.getAll().map { it.toBackup() },
            cycles = budgetCycleDao.getAll().map { it.toBackup() },
            transactions = transactionDao.getAll().map { it.toBackup() },
            categoryCaps = categoryCapDao.getAll().map { it.toBackup() },
            recurringRules = recurringRuleDao.getAll().map { it.toBackup() }
        )
        return json.encodeToString(backup)
    }

    suspend fun import(contents: String, mode: ImportMode): ImportResult {
        val backup = parse(contents).getOrElse { return ImportResult.Failure(it) }

        return runCatching {
            when (mode) {
                ImportMode.REPLACE -> replaceAll(backup)
                ImportMode.MERGE -> merge(backup)
            }
        }.getOrElse { ImportResult.Failure(ImportFailure.IO_ERROR) }
    }

    /** Reads the manifest without writing anything, so the user can be shown what they picked. */
    fun preview(contents: String): Result<MemoBackup> =
        parse(contents).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IllegalArgumentException(it.message)) }
        )

    private fun parse(contents: String): ParseResult {
        // The envelope is read first so picking the wrong file says "that isn't a Memo
        // backup" rather than "damaged" — decoding the whole thing would fail on a
        // missing required field long before it got as far as checking the format.
        val root = try {
            json.parseToJsonElement(contents) as? JsonObject
                ?: return ParseResult.failure(ImportFailure.NOT_A_MEMO_BACKUP)
        } catch (_: SerializationException) {
            return ParseResult.failure(ImportFailure.CORRUPT)
        }

        val format = (root["format"] as? JsonPrimitive)?.contentOrNull
        if (format != MemoBackup.FORMAT) {
            return ParseResult.failure(ImportFailure.NOT_A_MEMO_BACKUP)
        }
        // Refuse rather than load partially: `ignoreUnknownKeys` would silently drop a
        // newer version's fields, and the user would not know what they had lost.
        val version = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        if (version != null && version > MemoBackup.SCHEMA_VERSION) {
            return ParseResult.failure(ImportFailure.NEWER_SCHEMA)
        }

        val backup = try {
            json.decodeFromJsonElement<MemoBackup>(root)
        } catch (_: SerializationException) {
            return ParseResult.failure(ImportFailure.CORRUPT)
        } catch (_: IllegalArgumentException) {
            return ParseResult.failure(ImportFailure.CORRUPT)
        }
        return ParseResult.success(backup)
    }

    private suspend fun replaceAll(backup: MemoBackup): ImportResult {
        runInTransaction {
            transactionDao.deleteAllTransactions()
            recurringRuleDao.deleteAll()
            categoryCapDao.deleteAll()
            budgetCycleDao.deleteAll()
            budgetDao.deleteAll()

            // Budgets first: cycles, caps and rules all reference them.
            budgetDao.insertAll(backup.budgets.map { it.toEntity() })
            budgetCycleDao.insertAll(backup.cycles.map { it.toEntity() })
            categoryCapDao.insertAll(backup.categoryCaps.map { it.toEntity() })
            recurringRuleDao.insertAll(backup.recurringRules.map { it.toEntity() })
            transactionDao.insertAll(backup.transactions.map { it.toEntity() })
        }
        backup.activeBudgetId
            ?.takeIf { id -> backup.budgets.any { it.id == id } }
            ?.let { activeBudgetStore.setActiveBudgetId(it) }

        return ImportResult.Success(
            budgets = backup.budgets.size,
            transactions = backup.transactions.size,
            skipped = 0,
            mode = ImportMode.REPLACE
        )
    }

    /**
     * Adds what is missing without touching what is there.
     *
     * Budgets match on name, because ids from another device mean nothing here. Transactions
     * are deduplicated on the tuple that makes two entries the same expense in practice —
     * budget, date, amount, type, note and category — so importing the same file twice adds
     * nothing the second time.
     */
    private suspend fun merge(backup: MemoBackup): ImportResult {
        var importedBudgets = 0
        var importedTransactions = 0
        var skipped = 0

        runInTransaction {
            val existingBudgets = budgetDao.getAll()
            val budgetIdMap = mutableMapOf<Long, Long>()

            backup.budgets.forEach { incoming ->
                val match = existingBudgets.firstOrNull { it.name == incoming.name }
                if (match != null) {
                    budgetIdMap[incoming.id] = match.id
                } else {
                    val newId = budgetDao.insert(incoming.toEntity().copy(id = 0L))
                    budgetIdMap[incoming.id] = newId
                    importedBudgets++
                }
            }

            val existingCycles = budgetCycleDao.getAll()
            backup.cycles.forEach { incoming ->
                val budgetId = budgetIdMap[incoming.budgetId] ?: return@forEach
                val duplicate = existingCycles.any {
                    it.budgetId == budgetId && it.cycleIndex == incoming.cycleIndex
                }
                if (!duplicate) {
                    budgetCycleDao.insert(incoming.toEntity().copy(id = 0L, budgetId = budgetId))
                }
            }

            backup.categoryCaps.forEach { incoming ->
                val budgetId = budgetIdMap[incoming.budgetId] ?: return@forEach
                categoryCapDao.upsert(incoming.toEntity().copy(budgetId = budgetId))
            }

            val existingRules = recurringRuleDao.getAll()
            backup.recurringRules.forEach { incoming ->
                val budgetId = budgetIdMap[incoming.budgetId] ?: return@forEach
                val duplicate = existingRules.any {
                    it.budgetId == budgetId && it.note == incoming.note &&
                        it.amountCents == incoming.amountCents && it.cadence.id == incoming.cadence
                }
                if (!duplicate) {
                    recurringRuleDao.insert(incoming.toEntity().copy(id = 0L, budgetId = budgetId))
                }
            }

            val existingKeys = transactionDao.getAll().map { it.dedupeKey() }.toMutableSet()
            backup.transactions.forEach { incoming ->
                val budgetId = budgetIdMap[incoming.budgetId] ?: return@forEach
                val entity = incoming.toEntity().copy(id = 0, budgetId = budgetId)
                if (existingKeys.add(entity.dedupeKey())) {
                    transactionDao.insertTransaction(entity)
                    importedTransactions++
                } else {
                    skipped++
                }
            }
        }

        return ImportResult.Success(
            budgets = importedBudgets,
            transactions = importedTransactions,
            skipped = skipped,
            mode = ImportMode.MERGE
        )
    }

    private fun Transaction.dedupeKey() =
        listOf(budgetId, date, amount, type.id, note, category?.id.orEmpty())
}

/** Small Result-alike so parse failures carry an [ImportFailure] rather than an exception. */
private class ParseResult private constructor(
    private val value: MemoBackup?,
    private val failure: ImportFailure?
) {
    inline fun getOrElse(onFailure: (ImportFailure) -> Nothing): MemoBackup =
        value ?: onFailure(failure!!)

    fun <R> fold(onSuccess: (MemoBackup) -> R, onFailure: (ImportFailure) -> R): R =
        if (value != null) onSuccess(value) else onFailure(failure!!)

    companion object {
        fun success(backup: MemoBackup) = ParseResult(backup, null)
        fun failure(reason: ImportFailure) = ParseResult(null, reason)
    }
}

// ---- Entity <-> DTO -------------------------------------------------------------

private fun Budget.toBackup() = BackupBudget(
    id = id,
    name = name,
    amountCents = amountCents,
    totalDays = totalDays,
    currencyCode = currencyCode,
    firstCycleStartDate = firstCycleStartDate,
    isArchived = isArchived,
    createdAt = createdAt,
    sortOrder = sortOrder
)

private fun BackupBudget.toEntity() = Budget(
    id = id,
    name = name,
    amountCents = amountCents,
    totalDays = totalDays,
    currencyCode = currencyCode,
    firstCycleStartDate = firstCycleStartDate,
    isArchived = isArchived,
    createdAt = createdAt,
    sortOrder = sortOrder
)

private fun BudgetCycle.toBackup() = BackupCycle(
    id = id,
    budgetId = budgetId,
    cycleIndex = cycleIndex,
    startDate = startDate,
    endDateExclusive = endDateExclusive,
    budgetAmountCents = budgetAmountCents,
    closedAt = closedAt
)

private fun BackupCycle.toEntity() = BudgetCycle(
    id = id,
    budgetId = budgetId,
    cycleIndex = cycleIndex,
    startDate = startDate,
    endDateExclusive = endDateExclusive,
    budgetAmountCents = budgetAmountCents,
    closedAt = closedAt
)

private fun Transaction.toBackup() = BackupTransaction(
    id = id,
    budgetId = budgetId,
    amount = amount,
    type = type.id,
    note = note,
    date = date,
    category = category?.id,
    description = description,
    hasTime = hasTime,
    recurringRuleId = recurringRuleId
)

private fun BackupTransaction.toEntity() = Transaction(
    id = id,
    budgetId = budgetId,
    amount = amount,
    type = TransactionType.fromId(type),
    note = note,
    date = date,
    category = Category.fromId(category),
    description = description,
    hasTime = hasTime,
    recurringRuleId = recurringRuleId
)

private fun CategoryCap.toBackup() = BackupCategoryCap(
    budgetId = budgetId,
    category = category,
    capCents = capCents
)

private fun BackupCategoryCap.toEntity() = CategoryCap(
    budgetId = budgetId,
    category = category,
    capCents = capCents
)

private fun RecurringRule.toBackup() = BackupRecurringRule(
    id = id,
    budgetId = budgetId,
    amountCents = amountCents,
    note = note,
    category = category?.id,
    type = type.id,
    cadence = cadence.id,
    intervalCount = intervalCount,
    startDate = startDate,
    endDate = endDate,
    nextDueDate = nextDueDate,
    isPaused = isPaused,
    lastPostedAt = lastPostedAt
)

private fun BackupRecurringRule.toEntity() = RecurringRule(
    id = id,
    budgetId = budgetId,
    amountCents = amountCents,
    note = note,
    category = Category.fromId(category),
    type = TransactionType.fromId(type),
    cadence = Cadence.fromId(cadence),
    intervalCount = intervalCount,
    startDate = startDate,
    endDate = endDate,
    nextDueDate = nextDueDate,
    isPaused = isPaused,
    lastPostedAt = lastPostedAt
)
