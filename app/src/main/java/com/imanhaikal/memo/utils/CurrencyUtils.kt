package com.imanhaikal.memo.utils

import java.text.DecimalFormat
import java.text.NumberFormat
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

object CurrencyUtils {

    val SUPPORTED_CURRENCIES = mapOf(
        "USD" to "$ (USD)",
        "MYR" to "RM (MYR)",
        "EUR" to "€ (EUR)",
        "GBP" to "£ (GBP)",
        "JPY" to "¥ (JPY)"
    )

    // NumberFormat construction is expensive and formatCurrency runs per list row
    // and per animation frame. We use ThreadLocal to ensure zero lock contention
    // while guaranteeing that every thread gets its own safe instance of NumberFormat,
    // since NumberFormat is not thread-safe in Java.
    private val formatterCache = object : ThreadLocal<MutableMap<String, NumberFormat>>() {
        override fun initialValue() = mutableMapOf<String, NumberFormat>()
    }

    private fun formatterFor(code: String): NumberFormat =
        formatterCache.get()!!.getOrPut(code) {
            val format = NumberFormat.getCurrencyInstance(Locale.US)
            try {
                val currency = Currency.getInstance(code)
                format.currency = currency

                // Custom symbol override for MYR to ensure "RM" is used instead of "MYR"
                if (code == "MYR") {
                    val symbols = (format as DecimalFormat).decimalFormatSymbols
                    symbols.currencySymbol = "RM"
                    format.decimalFormatSymbols = symbols
                }
            } catch (e: Exception) {
                // Fallback to USD/Default if code is invalid
            }
            format
        }

    fun formatCurrency(amountCents: Long, code: String): String =
        formatterFor(code).format(amountCents / 100.0)

    fun parseAmountToCents(input: String): Long? {
        val normalized = input.trim()
        if (normalized.isEmpty()) return null

        return runCatching {
            normalized.toBigDecimalOrNull()
                ?.takeIf { it > BigDecimal.ZERO }
                ?.movePointRight(2)
                ?.setScale(0, RoundingMode.HALF_UP)
                ?.longValueExact()
        }.getOrNull()
    }

    fun formatAmountInput(amountCents: Long): String {
        val units = amountCents / 100
        val cents = kotlin.math.abs(amountCents % 100)
        return if (cents == 0L) units.toString() else "$units.${cents.toString().padStart(2, '0')}"
    }
}
