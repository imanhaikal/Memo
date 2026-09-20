package com.imanhaikal.memo.data.receipt

import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** What the Settings screen reports: what the receipt directory is actually costing. */
data class ReceiptStorageStats(
    val fileCount: Int,
    val totalBytes: Long
) {
    companion object {
        val EMPTY = ReceiptStorageStats(fileCount = 0, totalBytes = 0L)
    }
}

/**
 * Owns the directory holding saved receipt images.
 *
 * Takes a [directory] and a [decode] lambda rather than a `Context`, matching
 * [GeminiReceiptScanner]'s `ContentResolver` and `OverLimitNotifier`'s `notify`. That is
 * what lets this whole class be tested on the JVM, where `BitmapFactory` is a stub.
 *
 * Images live in internal storage, never the cache: the OS evicts cache under storage
 * pressure, which would silently destroy data the user believes is saved.
 */
class ReceiptStore(
    private val directory: File,
    private val decode: (Uri) -> ByteArray?,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Copies the image at [uri] into the receipt directory, returning its new file name —
     * or null when the source can't be read.
     *
     * Called the moment a picker returns, **not** when the user confirms the dialog. A
     * PhotoPicker grant does not survive process death, and being killed while the camera
     * app is foregrounded is routine; copying late would lose the image silently. Whatever
     * is written for a dialog the user then abandons becomes an orphan, which
     * [sweepOrphans] already has to collect for other reasons.
     */
    suspend fun save(uri: Uri): String? = withContext(io) {
        val bytes = decode(uri) ?: return@withContext null
        val name = "${UUID.randomUUID()}.jpg"
        try {
            directory.mkdirs()
            File(directory, name).writeBytes(bytes)
            name
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** The file holding [name], or null when [name] isn't one this store could have written. */
    fun fileFor(name: String): File? =
        if (isValidName(name)) File(directory, name) else null

    /**
     * Guards against a name that would escape the directory.
     *
     * Backup files are untrusted — the user imports them through SAF from Downloads, a chat
     * app, anywhere — and a hand-edited `"../databases/memo_database"` would otherwise make
     * [fileFor] resolve to the SQLite database, which the viewer's share action would then
     * hand to any app in the chooser. Every name this store writes is a UUID, so accept
     * exactly that and nothing else.
     */
    fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name)

    suspend fun stats(): ReceiptStorageStats = withContext(io) {
        val files = directory.listFiles().orEmpty()
        ReceiptStorageStats(
            fileCount = files.size,
            totalBytes = files.sumOf { it.length() }
        )
    }

    suspend fun deleteAll() = withContext(io) {
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    /**
     * Deletes every image no row points at any more.
     *
     * This replaces eager deletion, which cannot work here: the snackbar's Undo restores a
     * deleted row verbatim, so its image has to outlive the row; an edit that replaces an
     * image can still be cancelled; and `deleteAllForBudget` is a bulk SQL DELETE that
     * destroys the file names before anything can read them.
     *
     * [graceMillis] protects a file that has been written but not yet committed to a row —
     * an attachment sitting in an open dialog. Callers acting on a just-confirmed
     * destructive action pass 0, because those all originate on the Settings screen where
     * no attach flow can be in flight.
     */
    suspend fun sweepOrphans(referenced: Set<String>, graceMillis: Long) = withContext(io) {
        val now = System.currentTimeMillis()
        directory.listFiles()?.forEach { file ->
            val orphaned = file.name !in referenced
            // `>=`, not `>`: a zero grace has to mean "collect everything unreferenced",
            // and a file written in the current millisecond would otherwise survive it.
            val settled = now - file.lastModified() >= graceMillis
            if (orphaned && settled) file.delete()
        }
        Unit
    }

    companion object {
        /**
         * Deliberately more generous than the 1536/80 the Gemini upload uses. That copy is
         * disposable and can be regenerated; this one is the user's only remaining record of
         * a receipt they no longer physically hold, and the first thing they do in the
         * viewer is pinch in on a line item. ~300-500 KB each, and the Settings row makes
         * the cost visible and reclaimable.
         */
        const val MAX_DIMENSION = 2048
        const val JPEG_QUALITY = 85

        /** Long enough that an attachment picked before a process death is never swept. */
        const val ORPHAN_GRACE_MS = 24L * 60 * 60 * 1000

        private val NAME_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$")
    }
}
