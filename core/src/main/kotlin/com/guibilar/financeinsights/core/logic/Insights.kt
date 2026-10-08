package com.guibilar.financeinsights.core.logic

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/** How much an amount changed, as people say it: "12% more", "5% less", "about the same" (within 1%). */
data class Change(val text: String, val up: Boolean) {
    /** "12% more than August", "about the same as usual". */
    fun than(other: String) = "$text ${if (text == SAME) "as" else "than"} $other"

    companion object {
        const val SAME = "about the same"

        /** Null when there is nothing before to compare with. */
        fun of(now: Double, before: Double): Change? {
            if (before <= 0) return null
            val change = (now - before) / before * 100
            if (abs(change) < 1) return Change(SAME, false)
            return Change("${abs(change).roundToInt()}% ${if (change > 0) "more" else "less"}", change > 0)
        }
    }
}


/** A month review ([month] set) or a year review. */
data class ReviewPeriod(val year: Int, val month: Int?) {
    val isYear get() = month == null

    /**
     * The review after or before ([step] 1 or -1); null past the running month, or before the first month with data
     * ([earliest], "2025-03"). A year is there while any of its months is.
     */
    fun step(step: Int, today: LocalDate, earliest: String?): ReviewPeriod? {
        val next = if (month == null) ReviewPeriod(year + step, null)
        else YearMonth.of(year, month).plusMonths(step.toLong()).let { ReviewPeriod(it.year, it.monthValue) }
        val first = YearMonth.of(next.year, next.month ?: 1)
        val last = YearMonth.of(next.year, next.month ?: 12)
        if (first > YearMonth.from(today)) return null
        if (earliest != null && last < YearMonth.parse(earliest)) return null
        return next
    }
}
