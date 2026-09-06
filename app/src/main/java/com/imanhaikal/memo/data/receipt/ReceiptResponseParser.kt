package com.imanhaikal.memo.data.receipt

import com.imanhaikal.memo.utils.CurrencyUtils
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

object ReceiptResponseParser {

    // The prompt asks for "yyyy-MM-dd HH:mm", but the model sometimes appends seconds
    // or uses ISO-8601 'T' separators anyway — accept all of those.
    private val RECEIPT_DATETIME_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    )

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

    /**
     * Pulls the JSON object out of a model payload. Gemini occasionally wraps payloads in
     * markdown json fences even when responseMimeType is application/json, and sometimes
     * adds conversational text around them, so take everything between the outermost braces.
     * Falls back to the trimmed input when there is no object to find, which then fails
     * in [parseExtraction] the same way it always did.
     */
    fun extractJsonObject(text: String): String {
        val first = text.indexOf('{')
        val last = text.lastIndexOf('}')
        return if (first != -1 && last > first) text.substring(first, last + 1) else text.trim()
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

    /** A receipt timestamp; [hasTime] is false when only the day was printed. */
    data class ReceiptMoment(val millis: Long, val hasTime: Boolean)

    /** A POS clock this far ahead of ours is drift; beyond it, the date was misread. */
    private val FUTURE_TOLERANCE_MILLIS = TimeUnit.MINUTES.toMillis(15)

    /**
     * Parses the model-reported receipt timestamp ("yyyy-MM-dd HH:mm", ISO-8601, or a
     * bare "yyyy-MM-dd", which lands at noon with hasTime=false so it sorts sensibly
     * within its day) into epoch millis in [zone]. Returns null — meaning "fall back to
     * now" — for anything unparseable, or far enough ahead of [nowMillis] to be a
     * misread. Merchant terminals are routinely a few minutes fast (and a device clock
     * can be slow), so timestamps within [FUTURE_TOLERANCE_MILLIS] are kept and clamped
     * to now rather than thrown away; we never store a transaction dated in the future.
     * Old receipts are accepted: the app allows backdating to any past day, and the
     * prefilled chips are visible in the dialog, so a misread date is easy to spot.
     */
    fun parseReceiptDatetime(
        raw: String,
        zone: ZoneId,
        nowMillis: Long
    ): ReceiptMoment? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val (dateTime, hasTime) = parseDateTime(text) ?: return null
        val millis = dateTime.atZone(zone).toInstant().toEpochMilli()

        if (millis > nowMillis + FUTURE_TOLERANCE_MILLIS) return null
        return ReceiptMoment(minOf(millis, nowMillis), hasTime)
    }

    private fun parseDateTime(text: String): Pair<LocalDateTime, Boolean>? {
        for (format in RECEIPT_DATETIME_FORMATS) {
            try {
                return LocalDateTime.parse(text, format) to true
            } catch (e: DateTimeParseException) {
                // try the next format
            }
        }
        return try {
            LocalDate.parse(text).atTime(12, 0) to false
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
