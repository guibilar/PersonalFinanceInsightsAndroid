package com.guibilar.financeinsights.ui.today

import com.guibilar.financeinsights.ui.MoneyLink
import com.guibilar.financeinsights.ui.today.TodayDestination.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayLinksTest {
    // The links that the server's finance cards send today (Hestia, FinanceCards.cs), each with its page.

    @Test
    fun `budgets open the budgets`() = assertEquals(Money(MoneyLink.Budgets), todayDestination("/money/budgets"))

    @Test
    fun `net worth opens net worth`() = assertEquals(Money(MoneyLink.NetWorth), todayDestination("/money/net-worth"))

    @Test
    fun `statements open the transactions`() = assertEquals(Money(MoneyLink.Transactions), todayDestination("/money/transactions"))

    @Test
    fun `price changes open the subscriptions`() = assertEquals(Money(MoneyLink.Subscriptions), todayDestination("/money/subscriptions"))

    @Test
    fun `unsorted transactions open the rules`() = assertEquals(Money(MoneyLink.Rules), todayDestination("/money/rules#unsorted"))

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
        pages.forEach { (link, page) -> assertEquals(link, Money(page), todayDestination(link)) }
    }

    @Test
    fun `a card without a link opens nothing`() {
        assertNull(todayDestination(null))
        assertNull(todayDestination(""))
    }

    @Test
    fun `a link the app does not know opens the Money home`() {
        listOf("/money/garden", "/garden/d1", "https://example.com", "/", "/documentsx").forEach { link ->
            assertEquals(link, Money(MoneyLink.Home), todayDestination(link))
        }
    }

    // Documents (T11 sends "/documents/<id>" for a document that expires soon, or that expired).

    @Test
    fun `a document item opens the document`() {
        assertEquals(TodayDestination.Document("0199c1a2-0000-7000-8000-000000000001"), todayDestination("/documents/0199c1a2-0000-7000-8000-000000000001"))
        assertEquals(TodayDestination.Document("d1"), todayDestination("/documents/d1/"))
        assertEquals(TodayDestination.Document("d1"), todayDestination("/documents/d1?tab=files#text"))
    }

    @Test
    fun `the Documents module opens the list`() {
        assertEquals(TodayDestination.Documents, todayDestination("/documents"))
        assertEquals(TodayDestination.Documents, todayDestination("/documents/"))
        assertEquals(TodayDestination.Documents, todayDestination("/documents/d1/files"))
    }
}
