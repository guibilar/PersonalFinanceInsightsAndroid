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
    const val CATEGORY_NAME = 50
    const val ACCOUNT_NAME = 100
    const val FLOW_RULE_KEYWORD = 100
    const val BUDGET_NAME = 100
    const val BUDGET_DESCRIPTION = 500
    const val BUDGET_MERCHANT = 100
    const val TRANSACTION_DESCRIPTION = 200
    const val CATEGORY_RULE_KEYWORD = 100
}

/**
 * An amount as typed, with a point or a comma for decimals as the keyboard's language has it: "12.5", "12,50",
 * "1,234.56", "1.234,56", "1 234,56", "$40". The last separator is the decimal one when 1 or 2 digits (or none)
 * follow it, as in any locale; otherwise it groups thousands ("1,234").
 */
private fun normalizeAmount(text: String): String? {
    val cleaned = text.trim().removePrefix("$").replace(Regex("""[\s\u00A0\u202F]"""), "")
    if (cleaned.isEmpty() || !cleaned.matches(Regex("""[\d.,]+"""))) return null
    val last = maxOf(cleaned.lastIndexOf('.'), cleaned.lastIndexOf(','))
    if (last < 0) return cleaned
    val separator = cleaned[last]
    val digitsAfter = cleaned.length - last - 1
    val bothKinds = '.' in cleaned && ',' in cleaned
    val repeated = cleaned.count { it == separator } > 1
    // With both kinds, the last is the decimal one ("1.234,56", "1,234.56"). With one kind: repeated, it groups
    // thousands ("1,234,567"); alone, a comma before exactly three digits groups thousands as in English ("1,234"),
    // and anything else is decimals ("12,50", "12.505").
    val isDecimal = bothKinds || (!repeated && !(separator == ',' && digitsAfter == 3))
    val integer = cleaned.substring(0, last).replace(".", "").replace(",", "")
    return if (isDecimal) "$integer.${cleaned.substring(last + 1)}" else cleaned.replace(".", "").replace(",", "")
}

/** An amount as typed (see [normalizeAmount]); null when it isn't one, or isn't in whole cents (12.505). */
fun parseAmount(text: String): Double? {
    val value = normalizeAmount(text)?.let { if (it.endsWith(".")) it.dropLast(1) else it }?.toDoubleOrNull() ?: return null
    return if (value >= 0 && abs(round(value * 100) - value * 100) < 1e-6) value else null
}

/** Why an amount can't be used, or null when it can. */
fun amountError(text: String): String? {
    if (text.isBlank()) return "Enter an amount."
    val value = normalizeAmount(text)?.trimEnd('.')?.toDoubleOrNull() ?: return "Enter a number, like 12.50."
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

/** Same rule as the API: length, not composition. */
const val MIN_PASSWORD_LENGTH = 12

/**
 * Colours for categories: the ones the first categories had, then lighter shades of them. A new category starts on the
 * first one no category has yet.
 */
val CATEGORY_PALETTE = listOf(
    "#1F76B5", "#FF7E0F", "#2CA12D", "#D72628", "#9466BC", "#8C564B", "#E277C3", "#7F7E7E", "#BDBD23", "#16BFCF",
    "#800080", "#AEC7E8", "#FFBB78", "#98DF8A", "#FF9896", "#C5B0D5", "#C49C94", "#F7B6D2", "#DBDB8D", "#9EDAE5",
)

/** The first palette colour no category has yet; the first one when all are taken. */
fun freeCategoryColor(used: Collection<String>): String {
    val taken = used.map { it.uppercase() }.toSet()
    return CATEGORY_PALETTE.firstOrNull { it !in taken } ?: CATEGORY_PALETTE.first()
}

/** The flows a flow rule can set: money out is spending already without a rule. */
val FLOW_RULE_FLOWS = listOf(Flow.Transfer, Flow.Ignored, Flow.Refund, Flow.Income)

/** Which transactions a rule for each flow can apply to. */
val Flow.ruleScope
    get() = when (this) {
        Flow.Refund, Flow.Income -> "Applies to money in only."
        Flow.Transfer, Flow.Ignored -> "Applies to money in and out."
        Flow.Spending -> ""
    }

fun formatLabel(format: com.guibilar.financeinsights.core.model.StatementFormat) = when (format) {
    com.guibilar.financeinsights.core.model.StatementFormat.Cibc -> "CIBC"
    com.guibilar.financeinsights.core.model.StatementFormat.Neo -> "Neo Financial"
    com.guibilar.financeinsights.core.model.StatementFormat.Manual -> "No files (add by hand)"
}

fun accountTypeLabel(type: com.guibilar.financeinsights.core.model.AccountType) = when (type) {
    com.guibilar.financeinsights.core.model.AccountType.Chequing -> "Chequing"
    com.guibilar.financeinsights.core.model.AccountType.Savings -> "Savings"
    com.guibilar.financeinsights.core.model.AccountType.CreditCard -> "Credit card"
}

/** What a password change's refusal says, by field: "currentPassword", "newPassword", or "other". */
fun passwordErrors(error: com.guibilar.financeinsights.core.api.ApiError): Map<String, String> {
    if (error is com.guibilar.financeinsights.core.api.ApiError.Http && error.status == 400) {
        fun first(key: String) = error.fieldErrors.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.firstOrNull()
        val current = first("currentPassword")
        val new = first("newPassword")
        if (current != null || new != null) return buildMap {
            current?.let { put("currentPassword", it) }
            new?.let { put("newPassword", it) }
        }
    }
    return mapOf("other" to error.loadMessage("Could not change the password. Try again."))
}
