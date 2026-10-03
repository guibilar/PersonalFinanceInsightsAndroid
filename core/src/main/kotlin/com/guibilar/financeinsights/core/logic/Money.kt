package com.guibilar.financeinsights.core.logic

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/** Amounts as the web app shows them: Canadian dollars, "$1,234.56", "-$5.00". */
object Money {
    private val format = ThreadLocal.withInitial {
        NumberFormat.getCurrencyInstance(Locale.US).apply { currency = Currency.getInstance("USD") }
    }

    fun format(amount: Double): String {
        // Locale.US gives "$" and "-$5.00"; CAD in en-CA reads the same, and avoids "CA$" on some devices.
        val text = format.get().format(abs(amount))
        return if (amount < 0 && round(amount * 100) != 0.0) "-$text" else text
    }

    /** "$5K", "$1.2K", "$950": for axis ticks, which carry the values that aren't labelled. */
    fun compact(amount: Double): String {
        val sign = if (amount < 0) "-" else ""
        val value = abs(amount)
        fun oneDecimal(v: Double) = (round(v * 10) / 10).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
        return sign + when {
            value >= 1_000_000 -> "$${oneDecimal(value / 1_000_000)}M"
            value >= 1_000 -> "$${oneDecimal(value / 1_000)}K"
            else -> "$${round(value).toLong()}"
        }
    }

    /** A percentage, rounded: "12%". */
    fun percent(value: Double) = "${round(value).toLong()}%"
}
