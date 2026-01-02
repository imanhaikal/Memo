package com.imanhaikal.memo.utils

import java.text.DecimalFormat
import java.text.NumberFormat
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

    fun formatCurrency(amount: Double, code: String): String {
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
        
        return format.format(amount)
    }
}