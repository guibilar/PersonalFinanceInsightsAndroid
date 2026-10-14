package com.guibilar.financeinsights.core.today

import java.net.URLDecoder

// The one place that turns the link of a Today card or an alert into a page of this app. The server sends web routes
// (the same links as the web app, for example "/money/budgets", "/money/rules#unsorted", "/money/transactions?id=<id>"
// or "/documents/<id>"), so both clients use one contract. Today (in :app) and the Money pages (in :feature:finance)
// both use this map, so it is here and not in one of them.

/** A finance page that a link leads to. Each Money page turns it into its route. */
enum class MoneyLink { Home, Dashboard, Transactions, Import, Budgets, NetWorth, Subscriptions, Review, Rules, Categories, Tags, Accounts }

/** A page of the app that a Today card or an alert opens. */
sealed interface TodayDestination {
    /** A finance page (or the Money home). */
    data class Money(val link: MoneyLink) : TodayDestination

    /** One transaction ("/money/transactions?id=<id>"), highlighted on its day and account. */
    data class Transaction(val id: String) : TodayDestination

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
    val query = link.substringBefore('#').substringAfter('?', missingDelimiterValue = "")
    // "/money/transactions?id=<id>": one transaction. The Transactions page checks the id (an id that is not a GUID
    // shows "does not exist").
    val transactionId = if (path == "/money/transactions") queryValue(query, "id") else null
    return when {
        transactionId != null -> TodayDestination.Transaction(transactionId)
        path == "/money" || path.startsWith("/money/") -> TodayDestination.Money(moneyPage(path.removePrefix("/money").removePrefix("/"), fragment))
        path == "/documents" -> TodayDestination.Documents
        path.startsWith("/documents/") -> documentPage(path.removePrefix("/documents/"))
        else -> fallback
    }
}

/** The first non-blank value of [name] in a query ("a=1&id=x"), decoded; null when there is none. */
private fun queryValue(query: String, name: String): String? =
    query.split('&').firstNotNullOfOrNull { part ->
        val key = part.substringBefore('=')
        val value = part.substringAfter('=', missingDelimiterValue = "")
        if (key == name) runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()?.takeIf { it.isNotBlank() } else null
    }

private fun moneyPage(page: String, fragment: String): MoneyLink {
    // "/money/transactions#import": the transactions, with the import open.
    if (page == "transactions" && fragment == "import") return MoneyLink.Import
    return moneyPages[page] ?: MoneyLink.Home
}

/** "/documents/<id>" opens the document; a deeper path (a page of a newer web app) opens the list. */
private fun documentPage(rest: String): TodayDestination =
    if (rest.isNotBlank() && '/' !in rest) TodayDestination.Document(rest) else TodayDestination.Documents
