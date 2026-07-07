package com.imanhaikal.memo.data.receipt

import android.content.ContentResolver
import android.net.Uri
import com.imanhaikal.memo.BuildConfig
import com.imanhaikal.memo.utils.ImageUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ReceiptScanner {
    val isAvailable: Boolean
    suspend fun scan(uri: Uri): ScanOutcome
}

class GeminiReceiptScanner(
    private val contentResolver: ContentResolver,
    private val service: GeminiReceiptService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReceiptScanner {

    override val isAvailable: Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    override suspend fun scan(uri: Uri): ScanOutcome = withContext(ioDispatcher) {
        val jpegBase64 = ImageUtils.uriToScaledJpegBase64(contentResolver, uri)
        if (jpegBase64 == null) {
            android.util.Log.w("ReceiptScan", "Could not decode image at $uri")
            return@withContext ScanOutcome.Failure(ScanFailureReason.UNREADABLE)
        }
        service.extractReceipt(jpegBase64)
    }
}
