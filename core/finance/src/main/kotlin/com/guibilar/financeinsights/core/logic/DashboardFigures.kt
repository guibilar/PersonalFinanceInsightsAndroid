package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.CashFlowMonth
import com.guibilar.financeinsights.core.model.CashFlowReport
import com.guibilar.financeinsights.core.model.RegisteredPlan
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// Figures of the dashboard's tiles and cards. What needs attention is not here: the server's Alerts module owns those
// rules (Hestia T15), and the Money pages read them from GET /api/alerts (T67).

/**
 * This month against a usual one at the same point: the monthly average spread evenly over the month, up to today.
 * A whole usual month against a few days of this one would always read as far under.
 */
fun usualHint(spent: Double, monthlyAverage: Double, today: LocalDate): SpentHint? {
    if (monthlyAverage <= 0) return null
    val expected = monthlyAverage * today.dayOfMonth / YearMonth.from(today).lengthOfMonth()
    val difference = spent - expected
    // Under a cent either way is neither: not "$0.00 under".
    if (abs(difference) < 0.005) return SpentHint("about the same as a usual month by now", up = false, worse = false)
    return SpentHint(
        "${Money.format(abs(difference))} ${if (difference > 0) "over" else "under"} a usual month by now",
        up = difference > 0,
        worse = difference > 0,
    )
}

/** This month's income, spending and what was left over; null before anything is recorded this month. */
fun leftOverThisMonth(report: CashFlowReport, today: LocalDate): CashFlowMonth? =
    report.months.firstOrNull { monthOf(it.month) == YearMonth.from(today) }

/** Investment accounts, and savings held in a registered plan, by balance. */
fun investments(accounts: List<Account>): List<Account> =
    accounts.filter { it.type == AccountType.Investment || it.plan != RegisteredPlan.None }
        .sortedByDescending { it.balance?.amount ?: 0.0 }

/** "Balance on Aug 31, 2026 · $5,300.00 in this year · $1,700.00 room left", under an investment's name. */
fun investmentLine(account: Account): String = buildList {
    add(account.balance?.let { "Balance on ${formatDay(it.date)}" } ?: "No balance yet")
    if (account.plan != RegisteredPlan.None) {
        add("${Money.format(account.depositedThisYear)} in this year")
        account.roomLeft?.let { add(if (it < 0) "over by ${Money.format(-it)}" else "${Money.format(it)} room left") }
    }
}.joinToString(" · ")
