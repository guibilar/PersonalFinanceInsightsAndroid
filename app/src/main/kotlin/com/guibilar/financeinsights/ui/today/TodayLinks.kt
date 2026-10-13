package com.guibilar.financeinsights.ui.today

import com.guibilar.financeinsights.ui.MoneyLink

// The one place that turns a Today card's link into a page of this app. The server sends web routes (the same links as
// the web app, for example "/money/budgets", "/money/rules#unsorted" or "/documents/<id>"), so both clients use one
// contract.

/** A page of the app that a Today card opens. */
sealed interface TodayDestination {
    /** A finance page (or the Money home). */
    data class Money(val link: MoneyLink) : TodayDestination

    /** The Documents list. */
    data object Documents : TodayDestination

    /** One document. */
    data class Document(val id: String) : TodayDestination
}

/** The finance pages by their web path (under "/money"), as the web app names them (`money.routes.ts`). */
private val moneyPages = mapOf(
    "" to MoneyLink.Dashboard,
    "dashboard" to MoneyLink.Dashboard,
    "transactions" to MoneyLink.Transactions,
    "budgets" to MoneyLink.Budgets,
    "subscriptions" to MoneyLink.Subscriptions,
    "net-worth" to MoneyLink.NetWorth,
    "review" to MoneyLink.Review,
    "rules" to MoneyLink.Rules,
    "categories" to MoneyLink.Categories,
    "tags" to MoneyLink.Tags,
    "accounts" to MoneyLink.Accounts,
)

/** What an unknown link opens: the Money home. */
private val fallback = TodayDestination.Money(MoneyLink.Home)

/**
 * Where a card's [link] leads in this app; null when the card has no link (it then does not open anything).
 * A link that the app does not know, for example a page of a newer server, opens the Money home.
 */
fun todayDestination(link: String?): TodayDestination? {
    if (link.isNullOrBlank()) return null
    val fragment = link.substringAfter('#', missingDelimiterValue = "")
    val path = link.substringBefore('#').substringBefore('?').trimEnd('/')
    return when {
        path == "/money" || path.startsWith("/money/") -> TodayDestination.Money(moneyPage(path.removePrefix("/money").removePrefix("/"), fragment))
        path == "/documents" -> TodayDestination.Documents
        path.startsWith("/documents/") -> documentPage(path.removePrefix("/documents/"))
        else -> fallback
    }
}

private fun moneyPage(page: String, fragment: String): MoneyLink {
    // "/money/transactions#import": the transactions, with the import open.
    if (page == "transactions" && fragment == "import") return MoneyLink.Import
    return moneyPages[page] ?: MoneyLink.Home
}

/** "/documents/<id>" opens the document; a deeper path (a page of a newer web app) opens the list. */
private fun documentPage(rest: String): TodayDestination =
    if (rest.isNotBlank() && '/' !in rest) TodayDestination.Document(rest) else TodayDestination.Documents
