package de.mybudgets.app.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {
    fun format(amount: Double, currencyCode: String = "EUR"): String {
        return try {
            val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)
            fmt.currency = Currency.getInstance(currencyCode)
            fmt.format(amount)
        } catch (_: Exception) {
            "%.2f %s".format(amount, currencyCode)
        }
    }
}
