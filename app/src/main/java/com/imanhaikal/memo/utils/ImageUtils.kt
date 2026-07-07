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
    ): String? {
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
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
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

    fun purgeReceiptCaptures(context: Context) {
        File(context.cacheDir, RECEIPTS_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
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
