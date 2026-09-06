package com.imanhaikal.memo.utils

import com.imanhaikal.memo.data.Transaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {

    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Builds an RFC 4180 style CSV of [transactions] (dates in [zone] local time).
     *
     * A `type` column carries expense vs income rather than a leading minus on the amount:
     * a bare "-12.50" is ambiguous with a malformed row, and this round-trips into a
     * spreadsheet without anyone having to guess.
     */
    fun buildCsv(
        transactions: List<Transaction>,
        currencyCode: String,
        zone: ZoneId = ZoneId.systemDefault(),
        budgetName: String = ""
    ): String = buildString {
        append("date,time,amount,currency,type,budget,category,note,description\r\n")
        transactions.forEach { tx ->
            val dateTime = Instant.ofEpochMilli(tx.date).atZone(zone)
            append(DATE_FORMAT.format(dateTime))
            append(',')
            append(TIME_FORMAT.format(dateTime))
            append(',')
            append(CurrencyUtils.formatAmountInput(tx.amount))
            append(',')
            append(currencyCode)
            append(',')
            append(tx.type.id)
            append(',')
            append(escape(budgetName))
            append(',')
            append(tx.category?.id.orEmpty())
            append(',')
            append(escape(tx.note))
            append(',')
            append(escape(tx.description))
            append("\r\n")
        }
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
}
