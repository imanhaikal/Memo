package com.imanhaikal.memo.data.receipt

import android.util.Log
import com.imanhaikal.memo.BuildConfig
import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.Transaction
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiReceiptService(
    private val apiKey: String,
    private val clock: Clock,
    private val baseUrl: String = DEFAULT_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // Config fields (responseMimeType, thinkingConfig, ...) rely on
        // property defaults and must still reach the wire.
        encodeDefaults = true
    }
) {

    suspend fun extractReceipt(jpegBase64: String): ScanOutcome =
        withContext(Dispatchers.IO) { blockingExtract(jpegBase64) }

    /** Blocking HTTP and parsing; only ever reached through [extractReceipt]. */
    private fun blockingExtract(jpegBase64: String): ScanOutcome {
        val requestBody = json.encodeToString(buildRequest(jpegBase64))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(baseUrl)
            .header("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    warn("API error ${response.code}") { response.body?.string()?.take(500) }
                    return ScanOutcome.Failure(ScanFailureReason.API_ERROR)
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network failure calling Gemini", e)
            return ScanOutcome.Failure(ScanFailureReason.NETWORK)
        } ?: return ScanOutcome.Failure(ScanFailureReason.PARSE)

        val payload = ReceiptResponseParser.extractPayloadText(body, json)
            ?.let(ReceiptResponseParser::extractJsonObject)
        if (payload == null) {
            warn("No text payload in response") { body.take(500) }
            return ScanOutcome.Failure(ScanFailureReason.PARSE)
        }
        val extraction = ReceiptResponseParser.parseExtraction(payload, json)
        if (extraction == null) {
            warn("Unparseable extraction payload") { payload.take(500) }
            return ScanOutcome.Failure(ScanFailureReason.PARSE)
        }
        val amountCents = ReceiptResponseParser.totalToCents(extraction.total)
        if (amountCents == null) {
            warn("Unreadable total") { "'${extraction.total}' (note='${extraction.note}')" }
            return ScanOutcome.Failure(ScanFailureReason.UNREADABLE)
        }

        val moment = ReceiptResponseParser.parseReceiptDatetime(
            raw = extraction.datetime,
            zone = clock.zone,
            nowMillis = clock.millis()
        )
        if (moment == null && extraction.datetime.isNotBlank()) {
            warn("Discarded receipt datetime") { "'${extraction.datetime}'" }
        }
        return ScanOutcome.Success(
            amountCents = amountCents,
            note = extraction.note.trim().take(Transaction.NOTE_MAX_CHARS),
            category = Category.fromId(extraction.category.trim().lowercase()),
            description = extraction.description.trim().take(Transaction.DESCRIPTION_MAX_CHARS),
            dateMillis = moment?.millis,
            dateHasTime = moment?.hasTime ?: true
        )
    }

    /**
     * Logs [safe] always, appending [detail] only in debug builds: the detail carries
     * receipt contents (merchant name, line items, raw API payloads), and logcat is
     * readable by bug-report capture and privileged tooling.
     */
    private fun warn(safe: String, detail: () -> String?) {
        if (BuildConfig.DEBUG) Log.w(TAG, "$safe: ${detail()}") else Log.w(TAG, safe)
    }

    private fun buildRequest(jpegBase64: String) = GeminiRequest(
        contents = listOf(
            GeminiContent(
                parts = listOf(
                    GeminiPart(text = buildPrompt(LocalDate.now(clock))),
                    GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = jpegBase64))
                )
            )
        ),
        generationConfig = GenerationConfig(responseSchema = RESPONSE_SCHEMA)
    )

    companion object {
        private const val TAG = "ReceiptScan"

        const val DEFAULT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent"

        internal fun buildPrompt(today: LocalDate) = """
            You are extracting data from a photo of a purchase receipt.
            Today's date is $today.
            Return JSON with:
            - total: the FINAL amount actually paid, after tax, service charge, discounts
              and cash rounding. A plain decimal string with a dot as decimal separator,
              no currency symbol, no thousands separators (e.g. "12.50", "1234.00").
            - note: a short label for this expense, preferably the merchant/store name
              (e.g. "Tesco", "Nasi Kandar Pelita"). Max 30 characters.
            - category: the best-fitting expense category based on the merchant and items,
              one of: food, transport, shopping, bills, entertainment, health, other.
              Use "other" only when nothing else fits; omit the field if you cannot tell.
            - description: a 1-2 sentence summary of what was purchased, mentioning
              notable line items (e.g. "2x cappuccino, 1x croissant"). Max 200 characters.
              Empty if unreadable.
            - datetime: the transaction date and time printed on the receipt, formatted
              exactly as "yyyy-MM-dd HH:mm" in 24-hour time. If only the date is printed,
              return "yyyy-MM-dd". Transcribe exactly what is printed — do not guess.
              Receipts often print dates as day/month/year; two-digit years mean 20xx.
              If the year is not printed, use the most recent year that keeps the date
              on or before today. Empty if no date is printed or it is unreadable.
            Receipts are usually in Malaysian Ringgit (RM / MYR) but may be in other
            currencies; always return only the numeric amount.
            If the image is not a readable receipt, return total "0" and note "".
        """.trimIndent()

        private val RESPONSE_SCHEMA = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("total") {
                    put("type", "STRING")
                    put("description", "Final total paid as a plain decimal string, e.g. \"12.50\". \"0\" if unreadable.")
                }
                putJsonObject("note") {
                    put("type", "STRING")
                    put("description", "Merchant name or short expense description, max 30 chars. Empty if unreadable.")
                }
                putJsonObject("category") {
                    put("type", "STRING")
                    putJsonArray("enum") {
                        Category.entries.forEach { add(it.id) }
                    }
                    put("description", "Best-fitting expense category. Omit if unknown.")
                }
                putJsonObject("description") {
                    put("type", "STRING")
                    put("description", "Short summary of purchased items, max 200 chars. Empty if unreadable.")
                }
                putJsonObject("datetime") {
                    put("type", "STRING")
                    put("description", "Receipt date/time as \"yyyy-MM-dd HH:mm\" (24h) or \"yyyy-MM-dd\". Empty if not printed.")
                }
            }
            putJsonArray("required") {
                add("total")
                add("note")
                // Optional STRING fields are frequently omitted by the model even when
                // extractable; requiring them (with "" as the unknown value) makes it
                // actually fill them in. category stays optional — an enum can't hold "".
                add("description")
                add("datetime")
            }
        }
    }
}
