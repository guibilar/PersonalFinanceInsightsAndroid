package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.RulesResult
import com.guibilar.financeinsights.core.model.TransactionType
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

/** Longest texts the API accepts (Finance.Contracts TextLimits). */
object TextLimits {
    const val BUDGET_NAME = 100
    const val BUDGET_DESCRIPTION = 500
    const val BUDGET_MERCHANT = 100
    const val TRANSACTION_DESCRIPTION = 200
    const val CATEGORY_RULE_KEYWORD = 100
}

/**
 * An amount as typed: "12.5", "1,234.56", "$40". Null when it isn't a number, or isn't in whole cents
 * (12.505), which the API refuses.
 */
fun parseAmount(text: String): Double? {
    val cleaned = text.trim().removePrefix("$").replace(",", "").trim()
    if (cleaned.isEmpty() || !cleaned.matches(Regex("""\d*\.?\d*"""))) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    return if (abs(round(value * 100) - value * 100) < 1e-6) value else null
}

/** Why an amount can't be used, or null when it can. */
fun amountError(text: String): String? {
    if (text.isBlank()) return "Enter an amount."
    val cleaned = text.trim().removePrefix("$").replace(",", "")
    val value = cleaned.toDoubleOrNull() ?: return "Enter a number, like 12.50."
    if (value < 0.01) return "The amount must be more than zero."
    return if (parseAmount(text) == null) "Use whole cents: at most 2 decimal places." else null
}

/** The flows a transaction can have: money out can't be income, money in can't be spending. */
fun flowsFor(direction: TransactionType?): List<Flow> = when (direction) {
    TransactionType.Debit -> listOf(Flow.Spending, Flow.Transfer, Flow.Ignored)
    TransactionType.Credit -> listOf(Flow.Refund, Flow.Income, Flow.Transfer, Flow.Ignored)
    null -> Flow.entries
}

/** One line on what each flow does to the reports. */
val Flow.description
    get() = when (this) {
        Flow.Spending -> "Counts as spending."
        Flow.Refund -> "Money back: takes it off your spending."
        Flow.Income -> "Counts as income."
        Flow.Transfer -> "Between your own accounts, like a card payment: neither spending nor income."
        Flow.Ignored -> "Left out of the reports, like a pending hold."
    }

// Card processors put their own name before the merchant's: "SQ *CORNER STORE", "TST-PIZZA PLACE".
private val PROCESSOR_PREFIX = Regex("""^(SQ\s*\*|TST\s*[-*]|PAYPAL\s*\*|PP\s*\*|SP\s+)\s*""")

/**
 * A merchant keyword for a category rule, guessed from a bank description: the processor prefix dropped, then the
 * leading words up to the first one with a digit or '#', upper-case. "SQ *CORNER STORE #123 TORONTO ON" gives
 * "CORNER STORE". The whole description when nothing is left.
 */
fun suggestRuleKeyword(description: String): String {
    val upper = description.trim().uppercase()
    val words = upper.replace(PROCESSOR_PREFIX, "").split(Regex("""\s+"""))
    val end = words.indexOfFirst { it.contains(Regex("[\\d#]")) }
    val keyword = (if (end == -1) words else words.take(end)).joinToString(" ").trim()
    return keyword.ifEmpty { upper }.take(TextLimits.CATEGORY_RULE_KEYWORD).trim()
}

fun describeRulesResult(result: RulesResult): String =
    if (result.updated == 0) "All ${result.checked} transactions already match the rules"
    else "Updated ${result.updated} of ${result.checked} transactions"

/** What a budget's period is called on its badge; null for a total budget. */
fun periodName(period: BudgetPeriod): String? = when (period) {
    BudgetPeriod.Monthly -> "Monthly"
    BudgetPeriod.Yearly -> "Yearly"
    BudgetPeriod.Custom -> "Custom"
    BudgetPeriod.Total -> null
}

/** What each period means, for the form. */
val BudgetPeriod.hint
    get() = when (this) {
        BudgetPeriod.Total -> "Everything linked counts, e.g. a trip or a project."
        BudgetPeriod.Monthly -> "Only this month's transactions count; it starts over every month."
        BudgetPeriod.Yearly -> "Only this year's transactions count; it starts over every year."
        BudgetPeriod.Custom -> "Only transactions between two dates count, e.g. a holiday or a season."
    }

/** Follows "of $500.00" to say when that is: " this month", " this year", " · Jul 1 – Aug 15, 2026". */
fun periodSuffix(budget: Budget): String = when (budget.period) {
    BudgetPeriod.Monthly -> " this month"
    BudgetPeriod.Yearly -> " this year"
    BudgetPeriod.Custom -> {
        val start = budget.startDate?.take(10)?.let(LocalDate::parse)
        val end = budget.endDate?.take(10)?.let(LocalDate::parse)
        if (start != null && end != null) " · ${describeRange(DateRange(start, end))}" else ""
    }
    BudgetPeriod.Total -> ""
}

/** How full a budget is: fine, nearly there (80%), or over. */
enum class BudgetHealth { Ok, Warning, Over }

fun budgetHealth(percentageUsed: Double) = when {
    percentageUsed >= 100 -> BudgetHealth.Over
    percentageUsed >= 80 -> BudgetHealth.Warning
    else -> BudgetHealth.Ok
}
