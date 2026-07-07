package com.imanhaikal.memo.data.receipt

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
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

    // Caller is responsible for running this on an IO dispatcher.
    fun extractReceipt(jpegBase64: String): ScanOutcome {
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
                    Log.w(TAG, "API error ${response.code}: ${response.body?.string()?.take(500)}")
                    return ScanOutcome.Failure(ScanFailureReason.API_ERROR)
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network failure calling Gemini", e)
            return ScanOutcome.Failure(ScanFailureReason.NETWORK)
        } ?: return ScanOutcome.Failure(ScanFailureReason.PARSE)

        val payload = ReceiptResponseParser.extractPayloadText(body, json)
            ?.let(ReceiptResponseParser::stripMarkdownFences)
        if (payload == null) {
            Log.w(TAG, "No text payload in response: ${body.take(500)}")
            return ScanOutcome.Failure(ScanFailureReason.PARSE)
        }
        val extraction = ReceiptResponseParser.parseExtraction(payload, json)
        if (extraction == null) {
            Log.w(TAG, "Unparseable extraction payload: ${payload.take(500)}")
            return ScanOutcome.Failure(ScanFailureReason.PARSE)
        }
        val amountCents = ReceiptResponseParser.totalToCents(extraction.total)
        if (amountCents == null) {
            Log.w(TAG, "Unreadable total '${extraction.total}' (note='${extraction.note}')")
            return ScanOutcome.Failure(ScanFailureReason.UNREADABLE)
        }

        return ScanOutcome.Success(amountCents, extraction.note.trim().take(60))
    }

    private fun buildRequest(jpegBase64: String) = GeminiRequest(
        contents = listOf(
            GeminiContent(
                parts = listOf(
                    GeminiPart(text = PROMPT),
                    GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = jpegBase64))
                )
            )
        ),
        generationConfig = GenerationConfig(responseSchema = RESPONSE_SCHEMA)
    )

    companion object {
        private const val TAG = "ReceiptScan"

        const val DEFAULT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"

        val PROMPT = """
            You are extracting data from a photo of a purchase receipt.
            Return JSON with:
            - total: the FINAL amount actually paid, after tax, service charge, discounts
              and cash rounding. A plain decimal string with a dot as decimal separator,
              no currency symbol, no thousands separators (e.g. "12.50", "1234.00").
            - note: a short label for this expense, preferably the merchant/store name
              (e.g. "Tesco", "Nasi Kandar Pelita"). Max 30 characters.
            - confidence: 0 to 1.
            Receipts are usually in Malaysian Ringgit (RM / MYR) but may be in other
            currencies; always return only the numeric amount.
            If the image is not a readable receipt, return total "0", note "" and confidence 0.
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
                putJsonObject("confidence") {
                    put("type", "NUMBER")
                    put("description", "Extraction confidence from 0 to 1.")
                }
            }
            putJsonArray("required") {
                add("total")
                add("note")
            }
        }
    }
}
