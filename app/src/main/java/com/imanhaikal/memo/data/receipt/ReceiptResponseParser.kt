package com.imanhaikal.memo.data.receipt

import com.imanhaikal.memo.utils.CurrencyUtils
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object ReceiptResponseParser {

    fun extractPayloadText(body: String, json: Json): String? {
        val response = try {
            json.decodeFromString<GeminiResponse>(body)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        return response.candidates.firstOrNull()
            ?.content?.parts
            ?.firstNotNullOfOrNull { it.text }
    }

    // Gemini occasionally wraps payloads in ```json fences even when
    // responseMimeType is application/json.
    fun stripMarkdownFences(text: String): String {
        var result = text.trim()
        if (result.startsWith("```")) {
            result = result.removePrefix("```json").removePrefix("```").trim()
        }
        if (result.endsWith("```")) {
            result = result.removeSuffix("```").trim()
        }
        return result
    }

    fun parseExtraction(payload: String, json: Json): ReceiptExtraction? {
        return try {
            json.decodeFromString<ReceiptExtraction>(payload)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun totalToCents(total: String): Long? {
        val cleaned = total.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        if (cleaned.isEmpty()) return null

        val normalized = when {
            // "1,234.56" — commas are thousands separators
            cleaned.contains('.') -> cleaned.replace(",", "")
            // "12,50" — single comma acts as decimal separator; "1,234" is
            // ambiguous, but 2-digit tails are far more likely to be decimals
            cleaned.count { it == ',' } == 1 && cleaned.substringAfter(',').length == 2 ->
                cleaned.replace(',', '.')
            else -> cleaned.replace(",", "")
        }
        return CurrencyUtils.parseAmountToCents(normalized)
    }
}
