package com.imanhaikal.memo.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

object ImageUtils {

    private const val RECEIPTS_DIR = "receipts"

    /**
     * Decodes the image at [uri], downscales it so its longest edge is at most
     * [maxDimension] px, honors EXIF rotation, and returns it as base64 JPEG.
     * Returns null if the uri can't be read or isn't a decodable image.
     */
    fun uriToScaledJpegBase64(
        contentResolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = 1536,
        jpegQuality: Int = 80
    ): String? = uriToScaledJpegBytes(contentResolver, uri, maxDimension, jpegQuality)
        ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    /**
     * The shared decode pipeline: two-pass decode, downscale to [maxDimension], EXIF
     * rotation, JPEG re-encode. Returns null if the uri can't be read or isn't a decodable
     * image.
     *
     * Two callers with different budgets: the Gemini upload takes the smaller default, and
     * the stored receipt copy asks for more. Note that because the output is re-encoded
     * from a [Bitmap] rather than copied, **no EXIF survives** — the GPS coordinates a
     * camera stamps onto a photo never reach storage. That privacy property would be lost
     * by "optimizing" this into a raw byte copy.
     */
    fun uriToScaledJpegBytes(
        contentResolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = 1536,
        jpegQuality: Int = 80
    ): ByteArray? {
        return try {
            // Pass 1: bounds only, to pick a power-of-two sample size.
            // decodeStream always returns null in bounds mode; success is
            // signalled via outWidth/outHeight.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = contentResolver.openInputStream(uri) ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            }
            var bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            bitmap = scaleToMaxDimension(bitmap, maxDimension)

            val rotationDegrees = readExifRotation(contentResolver, uri)
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }

            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
            bitmap.recycle()
            output.toByteArray()
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    fun createReceiptCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, RECEIPTS_DIR).apply { mkdirs() }
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Deletes camera captures last modified more than [olderThanMillis] ago; 0 clears them
     * all. The in-app copy is made the moment the camera returns, so a capture is only ever
     * needed for the few seconds after that.
     */
    fun purgeReceiptCaptures(context: Context, olderThanMillis: Long = 0) {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        File(context.cacheDir, RECEIPTS_DIR).listFiles()
            ?.filter { olderThanMillis <= 0 || it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    /** How long a startup purge leaves a capture alone; see [purgeReceiptCaptures]. */
    const val CAPTURE_GRACE_MS = 60 * 60 * 1000L

    /** Also used by the receipt preview, which decodes straight from a file. */
    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        // Keep the decoded bitmap within ~2x of the target so the exact scale
        // below stays cheap without visibly losing detail.
        while (maxOf(width, height) / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        if (longestEdge <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longestEdge
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun readExifRotation(contentResolver: ContentResolver, uri: Uri): Int {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: IOException) {
            0
        }
    }
}
