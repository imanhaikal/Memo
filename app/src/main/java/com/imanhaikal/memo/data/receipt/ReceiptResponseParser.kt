package com.imanhaikal.memo.data.receipt

import com.imanhaikal.memo.utils.CurrencyUtils
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ReceiptResponseParser {

    private val RECEIPT_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private const val MAX_RECEIPT_AGE_DAYS = 2 * 365L

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

    /**
     * Parses the model-reported receipt timestamp ("yyyy-MM-dd HH:mm", ISO-8601, or a
     * bare "yyyy-MM-dd", which lands at noon so it sorts sensibly within its day) into
     * epoch millis in [zone]. Returns null — meaning "fall back to now" — for anything
     * unparseable, in the future, or implausibly old (likely OCR garbage).
     */
    fun datetimeToEpochMillis(
        raw: String,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val dateTime: LocalDateTime = parseDateTime(text) ?: return null
        val millis = dateTime.atZone(zone).toInstant().toEpochMilli()

        if (millis > nowMillis) return null
        if (millis < nowMillis - MAX_RECEIPT_AGE_DAYS * 86_400_000L) return null
        return millis
    }

    private fun parseDateTime(text: String): LocalDateTime? {
        try {
            return LocalDateTime.parse(text, RECEIPT_DATETIME_FORMAT)
        } catch (e: DateTimeParseException) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text) // ISO-8601, e.g. "2026-07-18T14:35:00"
        } catch (e: DateTimeParseException) {
            // fall through
        }
        return try {
            LocalDate.parse(text).atTime(12, 0)
        } catch (e: DateTimeParseException) {
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
