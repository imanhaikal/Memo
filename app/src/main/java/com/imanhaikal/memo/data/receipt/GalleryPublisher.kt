package com.imanhaikal.memo.data.receipt

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Puts a copy of an in-app camera capture into the phone's gallery, under Pictures/Memo.
 *
 * Only camera captures come through here: an image picked *from* the gallery is already in
 * it, and copying it back would duplicate every receipt.
 *
 * The copy is the camera's original file, byte for byte — full resolution and with its
 * EXIF intact, so it looks like any other photo the user took. That is deliberately not the
 * same as the in-app copy, which [ReceiptStore] downsizes and strips. The two are
 * independent afterwards: removing a receipt in Memo leaves the gallery photo alone, which
 * is what a user who opted into a gallery copy would expect.
 */
class GalleryPublisher(
    private val contentResolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    /** True when the copy landed; false on any failure, leaving no half-written entry. */
    suspend fun publish(source: Uri): Boolean = withContext(io) {
        val scoped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (scoped) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$ALBUM"
                )
                // Hidden from other apps until the bytes are fully written, so a gallery
                // app never shows a broken thumbnail for a copy still in progress.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                // No RELATIVE_PATH before 10, so name the file outright to land in the same
                // Pictures/Memo album rather than wherever the provider defaults to.
                @Suppress("DEPRECATION")
                val album = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    ALBUM
                )
                album.mkdirs()
                @Suppress("DEPRECATION")
                put(
                    MediaStore.Images.Media.DATA,
                    File(album, getAsString(MediaStore.Images.Media.DISPLAY_NAME)).path
                )
            }
        }
        val collection = if (scoped) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            // Pre-10 this needs WRITE_EXTERNAL_STORAGE, which the caller has already asked for.
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        // Named exceptions rather than Exception, which would also swallow cancellation.
        // Some OEM MediaStores throw IllegalArgument/IllegalState for an unusable volume.
        val target = try {
            contentResolver.insert(collection, values)
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IllegalStateException) {
            null
        } ?: return@withContext false

        try {
            val input = contentResolver.openInputStream(source) ?: throw IOException("no source")
            val output = contentResolver.openOutputStream(target) ?: throw IOException("no target")
            input.use { from -> output.use { to -> from.copyTo(to) } }
            if (scoped) {
                contentResolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            true
        } catch (e: IOException) {
            discard(target)
        } catch (e: SecurityException) {
            discard(target)
        } catch (e: IllegalArgumentException) {
            discard(target)
        } catch (e: IllegalStateException) {
            discard(target)
        }
    }

    /** Best-effort removal of a half-written entry; always reports the publish as failed. */
    private fun discard(target: Uri): Boolean {
        runCatching { contentResolver.delete(target, null, null) }
        return false
    }

    private fun displayName(): String =
        "Receipt_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"

    companion object {
        const val ALBUM = "Memo"

        /** Android 8-9 need a runtime storage permission to write to the shared gallery. */
        val needsStoragePermission: Boolean
            get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }
}
