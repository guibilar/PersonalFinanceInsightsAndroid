package com.guibilar.financeinsights.ui.today

import com.guibilar.financeinsights.ui.MoneyLink

// The one place that turns a Today card's link into a page of this app. The server sends web routes (the same links as
// the web app, for example "/money/budgets" or "/money/rules#unsorted"), so both clients use one contract.

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

/**
 * Where a card's [link] leads in this app; null when the card has no link (it then does not open anything).
 * A link that the app does not know, for example a page of a newer server, opens the Money home.
 */
fun todayDestination(link: String?): MoneyLink? {
    if (link.isNullOrBlank()) return null
    val fragment = link.substringAfter('#', missingDelimiterValue = "")
    val path = link.substringBefore('#').substringBefore('?').trimEnd('/')
    if (path != "/money" && !path.startsWith("/money/")) return MoneyLink.Home
    val page = path.removePrefix("/money").removePrefix("/")
    // "/money/transactions#import": the transactions, with the import open.
    if (page == "transactions" && fragment == "import") return MoneyLink.Import
    return moneyPages[page] ?: MoneyLink.Home
}
