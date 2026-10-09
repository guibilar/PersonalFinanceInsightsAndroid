package com.guibilar.financeinsights.core.logic

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/** Amounts as the web app shows them: Canadian dollars, "$1,234.56", "-$5.00". */
object Money {
    private val format = ThreadLocal.withInitial {
        NumberFormat.getCurrencyInstance(Locale.US).apply { currency = Currency.getInstance("USD") }
    }

    /** What every amount reads in privacy mode: the same length whatever the amount, so its size doesn't show either. */
    const val HIDDEN = "$•••••"

    /**
     * Privacy mode, asked each time an amount is formatted. A function so the app can back it with Compose state:
     * the screens that formatted an amount then redraw when it's switched.
     */
    @Volatile
    var hidden: () -> Boolean = { false }

    /** "$1,234.56", "-$5.00"; "$•••••" in privacy mode. */
    fun format(amount: Double): String {
        if (hidden()) return if (amount < 0 && round(amount * 100) != 0.0) "-$HIDDEN" else HIDDEN
        // Locale.US gives "$" and "-$5.00"; CAD in en-CA reads the same, and avoids "CA$" on some devices.
        val text = format.get().format(abs(amount))
        return if (amount < 0 && round(amount * 100) != 0.0) "-$text" else text
    }

    /** An amount to edit in a field: "7000", "12.50". */
    fun plain(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format(Locale.US, "%.2f", amount)

    /** "$5K", "$1.2K", "$950": for axis ticks, which carry the values that aren't labelled. Blank in privacy mode. */
    fun compact(amount: Double): String {
        if (hidden()) return ""
        // No sign on what rounds to nothing: "-$0" read oddly.
        val sign = if (amount < 0 && round(amount) != 0.0) "-" else ""
        val value = abs(amount)
        fun oneDecimal(v: Double) = (round(v * 10) / 10).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
        // The unit is picked after rounding: 999,960 is "$1M", not "$1000K"; 999.6 is "$1K", not "$1000".
        return sign + when {
            round(value / 100) >= 10_000 -> "$${oneDecimal(value / 1_000_000)}M"
            round(value) >= 1_000 -> "$${oneDecimal(value / 1_000)}K"
            else -> "$${round(value).toLong()}"
        }
    }

    /** A percentage, rounded: "12%". */
    fun percent(value: Double) = "${round(value).toLong()}%"

    /**
     * How much of a budget is used. Below 100 it rounds down, so a budget that isn't full never reads "100%" (in
     * the colour of one that is nearly full).
     */
    fun percentUsed(value: Double) = if (value < 100) "${floor(value).toLong().coerceAtLeast(0)}%" else percent(value)
}
