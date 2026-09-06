package com.imanhaikal.memo.receipt

import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.imanhaikal.memo.BuildConfig
import com.imanhaikal.memo.data.receipt.GeminiReceiptScanner
import com.imanhaikal.memo.data.receipt.GeminiReceiptService
import com.imanhaikal.memo.data.receipt.ScanOutcome
import java.io.File
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device end-to-end check of the receipt scan pipeline: decodes a bundled
 * receipt image exactly like the app does and calls the real Gemini API with
 * the BuildConfig key. Skipped when no key is configured.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptScanE2eTest {

    @Test
    fun scanBundledReceipt() {
        assumeTrue("GEMINI_API_KEY not configured", BuildConfig.GEMINI_API_KEY.isNotBlank())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context

        // Stage the asset in the same cache dir + FileProvider uri the camera flow uses
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "e2e_receipt.jpg")
        testContext.assets.open("test_receipt.jpg").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val scanner = GeminiReceiptScanner(
            contentResolver = context.contentResolver,
            service = GeminiReceiptService(
                apiKey = BuildConfig.GEMINI_API_KEY,
                clock = Clock.systemDefaultZone()
            )
        )

        val outcome = runBlocking { scanner.scan(uri) }
        Log.i("ReceiptScanE2E", "Outcome: $outcome")

        assertTrue("Expected Success but got $outcome", outcome is ScanOutcome.Success)
        val success = outcome as ScanOutcome.Success
        Log.i("ReceiptScanE2E", "amountCents=${success.amountCents} note=${success.note}")
        assertTrue("Expected 2860 cents, got ${success.amountCents}", success.amountCents == 2860L)
    }
}
