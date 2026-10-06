package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.CashFlowMonth
import com.guibilar.financeinsights.core.model.CashFlowReport
import com.guibilar.financeinsights.core.model.RecurringReport
import com.guibilar.financeinsights.core.model.RecurringStatus
import com.guibilar.financeinsights.core.model.RegisteredPlan
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.core.model.isoDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val shortDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * This month against a usual one at the same point: the monthly average spread evenly over the month, up to today.
 * A whole usual month against a few days of this one would always read as far under.
 */
fun usualHint(spent: Double, monthlyAverage: Double, today: LocalDate): SpentHint? {
    if (monthlyAverage <= 0) return null
    val expected = monthlyAverage * today.dayOfMonth / YearMonth.from(today).lengthOfMonth()
    val difference = spent - expected
    return SpentHint(
        "${Money.format(abs(difference))} ${if (difference > 0) "over" else "under"} a usual month by now",
        up = difference > 0,
        worse = difference > 0,
    )
}

/** This month's income, spending and what was left over; null before anything is recorded this month. */
fun leftOverThisMonth(report: CashFlowReport, today: LocalDate): CashFlowMonth? =
    report.months.firstOrNull { monthOf(it.month) == YearMonth.from(today) }

/**
 * The day an account's imported statements reach: its latest transaction or its latest balance (Wealthsimple
 * statements record one for their last day, even in a month without activity). Null when it has neither.
 */
fun statementsReach(account: Account): LocalDate? =
    listOfNotNull(account.lastTransactionDate, account.balance?.date).maxOfOrNull { isoDay(it) }

/**
 * Accounts whose statements stop before last month began: last month's statement is out and not imported yet.
 * Accounts added by hand, and ones nothing was ever imported into, are left alone.
 */
fun accountsBehind(accounts: List<Account>, today: LocalDate): List<Account> {
    val lastMonthStart = YearMonth.from(today).minusMonths(1).atDay(1)
    return accounts.filter { account ->
        account.format != StatementFormat.Manual && statementsReach(account)?.isBefore(lastMonthStart) == true
    }
}

enum class AttentionKind { Budget, Statement, Price, Unsorted }

/** Something on the dashboard that needs a look; its kind says where to deal with it. */
data class AttentionItem(val kind: AttentionKind, val text: String)

/** Price changes this recent are worth a mention. */
private const val PRICE_CHANGE_DAYS = 45L

/** What needs a look, most pressing first; empty when all is well. */
fun attentionItems(
    unsorted: Int,
    budgets: List<Budget>,
    recurring: RecurringReport?,
    accounts: List<Account>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<AttentionItem> = buildList {
    budgets.filter { it.percentageUsed >= 100 }.forEach { budget ->
        add(AttentionItem(AttentionKind.Budget, "${budget.name} is over its limit (${budget.percentageUsed.toInt()}% used)"))
    }
    lastImportDay(accounts, zone)?.let { last ->
        val days = ChronoUnit.DAYS.between(last, today)
        if (days > IMPORT_REMINDER_DAYS) add(AttentionItem(AttentionKind.Statement, "No statements imported in $days days"))
    }
    accountsBehind(accounts, today).forEach { account ->
        add(
            AttentionItem(
                AttentionKind.Statement,
                "${account.name}: statements reach ${shortDate.format(statementsReach(account))}. Import last month's",
            ),
        )
    }
    recurring?.payments.orEmpty().forEach { payment ->
        val change = payment.priceChange ?: return@forEach
        if (payment.status == RecurringStatus.Stopped) return@forEach
        if (ChronoUnit.DAYS.between(isoDay(change.since), today) > PRICE_CHANGE_DAYS) return@forEach
        val verb = if (change.to > change.from) "went up" else "went down"
        add(AttentionItem(AttentionKind.Price, "${payment.name} $verb from ${Money.format(change.from)} to ${Money.format(change.to)}"))
    }
    if (unsorted > 0) {
        val one = unsorted == 1
        add(
            AttentionItem(
                AttentionKind.Unsorted,
                "$unsorted ${if (one) "transaction is" else "transactions are"} in Other because no rule names ${if (one) "its merchant" else "their merchants"}",
            ),
        )
    }
}

/** Investment accounts, and savings held in a registered plan, by balance. */
fun investments(accounts: List<Account>): List<Account> =
    accounts.filter { it.type == AccountType.Investment || it.plan != RegisteredPlan.None }
        .sortedByDescending { it.balance?.amount ?: 0.0 }

/** "Balance on Aug 31, 2026 · $5,300.00 in this year · $1,700.00 room left", under an investment's name. */
fun investmentLine(account: Account): String = buildList {
    add(account.balance?.let { "Balance on ${shortDate.format(isoDay(it.date))}" } ?: "No balance yet")
    if (account.plan != RegisteredPlan.None) {
        add("${Money.format(account.depositedThisYear)} in this year")
        account.roomLeft?.let { add(if (it < 0) "over by ${Money.format(-it)}" else "${Money.format(it)} room left") }
    }
}.joinToString(" · ")
