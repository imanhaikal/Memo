package com.imanhaikal.memo.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Copies the database file aside before the v4 → v5 migration runs.
 *
 * [AppDatabase.MIGRATION_4_5] is the one irreversible step in this codebase: if it goes
 * wrong the user's entire spending history is gone with no way back. A file copy taken
 * before Room ever opens the database costs a few milliseconds once, and turns that from
 * unrecoverable into a Settings row.
 *
 * Must be called before anything touches [AppDatabase.getDatabase].
 */
object PreUpgradeSnapshot {

    private const val TAG = "PreUpgradeSnapshot"
    private const val DB_NAME = "memo_database"
    private const val BACKUP_DIR = "pre_upgrade_backup"
    private const val SNAPSHOT_VERSION = 4

    private val suffixes = listOf("", "-wal", "-shm")

    /** Takes a snapshot when an on-disk v4 database is about to be migrated. */
    fun captureIfNeeded(context: Context) {
        runCatching {
            val database = context.getDatabasePath(DB_NAME)
            if (!database.exists()) return
            if (readUserVersion(database) != SNAPSHOT_VERSION) return

            val target = File(context.filesDir, BACKUP_DIR)
            if (target.exists() && File(target, DB_NAME).exists()) return
            target.mkdirs()

            suffixes.forEach { suffix ->
                val source = File(database.path + suffix)
                if (source.exists()) source.copyTo(File(target, DB_NAME + suffix), overwrite = true)
            }
        }.onFailure {
            // A missing snapshot must never stop the app from opening — the migration
            // itself is still the source of truth for correctness.
            Log.w(TAG, "Could not capture pre-upgrade snapshot", it)
        }
    }

    fun hasSnapshot(context: Context): Boolean =
        File(File(context.filesDir, BACKUP_DIR), DB_NAME).exists()

    /**
     * Puts the snapshot back. The caller is responsible for restarting the process
     * afterwards — Room caches an open handle to the file being replaced.
     */
    fun restore(context: Context): Boolean = runCatching {
        val target = File(context.filesDir, BACKUP_DIR)
        if (!File(target, DB_NAME).exists()) return false
        val database = context.getDatabasePath(DB_NAME)
        suffixes.forEach { suffix ->
            val source = File(target, DB_NAME + suffix)
            val destination = File(database.path + suffix)
            if (source.exists()) source.copyTo(destination, overwrite = true) else destination.delete()
        }
        true
    }.getOrElse {
        Log.w(TAG, "Could not restore pre-upgrade snapshot", it)
        false
    }

    fun discard(context: Context) {
        runCatching { File(context.filesDir, BACKUP_DIR).deleteRecursively() }
    }

    private fun readUserVersion(database: File): Int =
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
}
