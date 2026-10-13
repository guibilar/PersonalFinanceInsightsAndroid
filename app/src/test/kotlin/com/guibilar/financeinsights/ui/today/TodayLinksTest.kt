package com.guibilar.financeinsights.ui.today

import com.guibilar.financeinsights.ui.MoneyLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayLinksTest {
    // The links that the server's finance cards send today (Hestia, FinanceCards.cs), each with its page.

    @Test
    fun `budgets open the budgets`() = assertEquals(MoneyLink.Budgets, todayDestination("/money/budgets"))

    @Test
    fun `net worth opens net worth`() = assertEquals(MoneyLink.NetWorth, todayDestination("/money/net-worth"))

    @Test
    fun `statements open the transactions`() = assertEquals(MoneyLink.Transactions, todayDestination("/money/transactions"))

    @Test
    fun `price changes open the subscriptions`() = assertEquals(MoneyLink.Subscriptions, todayDestination("/money/subscriptions"))

    @Test
    fun `unsorted transactions open the rules`() = assertEquals(MoneyLink.Rules, todayDestination("/money/rules#unsorted"))

    // The other finance pages of the web app.

    @Test
    fun `the other finance pages open their pages`() {
        val pages = mapOf(
            "/money" to MoneyLink.Dashboard,
            "/money/" to MoneyLink.Dashboard,
            "/money/review" to MoneyLink.Review,
            "/money/categories" to MoneyLink.Categories,
            "/money/tags" to MoneyLink.Tags,
            "/money/accounts" to MoneyLink.Accounts,
            "/money/transactions?categoryId=c1" to MoneyLink.Transactions,
            "/money/transactions#import" to MoneyLink.Import,
        )
        pages.forEach { (link, page) -> assertEquals(link, page, todayDestination(link)) }
    }

    @Test
    fun `a card without a link opens nothing`() {
        assertNull(todayDestination(null))
        assertNull(todayDestination(""))
    }

    @Test
    fun `a link the app does not know opens the Money home`() {
        listOf("/money/garden", "/documents/d1", "https://example.com", "/").forEach { link ->
            assertEquals(link, MoneyLink.Home, todayDestination(link))
        }
    }
}
