package com.imanhaikal.memo.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.imanhaikal.memo.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What [rememberReceiptBitmap] is currently holding. */
sealed interface ReceiptImageState {
    data object Loading : ReceiptImageState

    /** The file is gone — most often a row restored from a backup, which carries no images. */
    data object Missing : ReceiptImageState

    data class Loaded(val bitmap: ImageBitmap) : ReceiptImageState
}

/**
 * Decodes a stored receipt off the main thread, downscaled to [maxDimension].
 *
 * Hand-rolled rather than pulled from an image library: there are exactly two surfaces that
 * ever decode one — the attach preview and the full-screen viewer — both showing a single
 * image at a time, from a local file this app wrote itself at a known bound. There is no
 * network to layer, no disk cache worth keeping (the file *is* the cache), and no list of
 * thumbnails to keep out of memory, since rows carry a vector icon instead. Reach for Coil
 * if a browse-all-receipts grid ever lands; until then it would be the largest dependency
 * in the app serving two `Image` calls.
 *
 * No `recycle()` anywhere: from API 26 bitmap pixels are native allocations the GC tracks
 * and frees, and recycling by hand risks tearing one down while it is still being drawn.
 */
@Composable
fun rememberReceiptBitmap(file: File?, maxDimension: Int): State<ReceiptImageState> =
    produceState<ReceiptImageState>(
        initialValue = ReceiptImageState.Loading,
        key1 = file?.path,
        key2 = maxDimension
    ) {
        value = decodeReceipt(file, maxDimension)
    }

private suspend fun decodeReceipt(file: File?, maxDimension: Int): ReceiptImageState =
    withContext(Dispatchers.IO) {
        if (file == null || !file.exists()) return@withContext ReceiptImageState.Missing
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext ReceiptImageState.Missing
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = ImageUtils.calculateInSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxDimension = maxDimension
                )
            }
            BitmapFactory.decodeFile(file.path, options)
                ?.let { ReceiptImageState.Loaded(it.asImageBitmap()) }
                ?: ReceiptImageState.Missing
        } catch (e: OutOfMemoryError) {
            ReceiptImageState.Missing
        }
    }
