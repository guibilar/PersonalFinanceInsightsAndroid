package com.guibilar.financeinsights.core.today

import com.guibilar.financeinsights.core.today.TodayDestination.Money
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

    // One transaction (T33 and T56): "/money/transactions?id=<id>".

    @Test
    fun `a transaction link opens the transaction`() {
        val id = "01a0ff22-674a-7e98-bb70-9dab0d96bb50"
        assertEquals(TodayDestination.Transaction(id), todayDestination("/money/transactions?id=$id"))
        assertEquals(TodayDestination.Transaction(id), todayDestination("/money/transactions/?sort=date&id=$id#row"))
        // The Transactions page checks the id itself.
        assertEquals(TodayDestination.Transaction("not a GUID"), todayDestination("/money/transactions?id=not%20a%20GUID"))
    }

    @Test
    fun `a transaction link without an id opens the transactions`() {
        listOf("/money/transactions?id=", "/money/transactions?id", "/money/transactions?transactionId=x").forEach { link ->
            assertEquals(link, Money(MoneyLink.Transactions), todayDestination(link))
        }
        // Only the Transactions page takes an id.
        assertEquals(Money(MoneyLink.Budgets), todayDestination("/money/budgets?id=b1"))
    }

    // "Statement to import" for one account (T63): "/money/transactions?importAccount=<id>#import".

    @Test
    fun `a statement to import opens the import with its account`() {
        val id = "0199a1a1-0000-7000-8000-000000000003"
        assertEquals(Money(MoneyLink.Import, id), todayDestination("/money/transactions?importAccount=$id#import"))
        assertEquals(Money(MoneyLink.Import, "a b"), todayDestination("/money/transactions?sort=date&importAccount=a%20b#import"))
        // "No recent import" has no account.
        assertEquals(Money(MoneyLink.Import), todayDestination("/money/transactions#import"))
        assertEquals(Money(MoneyLink.Import), todayDestination("/money/transactions?importAccount=#import"))
        // Without the fragment, the transactions open as before.
        assertEquals(Money(MoneyLink.Transactions), todayDestination("/money/transactions?importAccount=$id"))
    }

    // Tasks (T19 sends "/tasks" on its tile, "/tasks/<id>" for an overdue task, "/tasks?due=overdue" for many).

    @Test
    fun `the Tasks tile opens the Tasks screen`() {
        assertEquals(TodayDestination.Tasks(), todayDestination("/tasks"))
        assertEquals(TodayDestination.Tasks(), todayDestination("/tasks/"))
        assertEquals(TodayDestination.Tasks(), todayDestination("/tasks?due=today"))
        assertEquals(TodayDestination.Tasks(), todayDestination("/tasks/t1/history"))
    }

    @Test
    fun `many overdue tasks open the overdue tasks only`() {
        assertEquals(TodayDestination.Tasks(overdueOnly = true), todayDestination("/tasks?due=overdue"))
        assertEquals(TodayDestination.Tasks(overdueOnly = true), todayDestination("/tasks/?tag=home&due=overdue#top"))
    }

    @Test
    fun `an overdue task opens the task`() {
        assertEquals(TodayDestination.Task("0199c1a2-0000-7000-8000-000000000001"), todayDestination("/tasks/0199c1a2-0000-7000-8000-000000000001"))
        assertEquals(TodayDestination.Task("t1"), todayDestination("/tasks/t1/?x=1"))
        assertEquals(Money(MoneyLink.Home), todayDestination("/tasksx"))
    }

    // Home (T22 sends "/home/items/<id>" for a warranty that ends soon; the web has "/home" and "/home/rooms/<id>").

    @Test
    fun `a warranty alert opens the item`() {
        assertEquals(TodayDestination.Item("0199d1a2-0000-7000-8000-000000000001"), todayDestination("/home/items/0199d1a2-0000-7000-8000-000000000001"))
        assertEquals(TodayDestination.Item("i1"), todayDestination("/home/items/i1/?x=1#top"))
    }

    @Test
    fun `the Home links open the Home screen or a room`() {
        assertEquals(TodayDestination.Home, todayDestination("/home"))
        assertEquals(TodayDestination.Home, todayDestination("/home/"))
        assertEquals(TodayDestination.Home, todayDestination("/home?q=bosch"))
        assertEquals(TodayDestination.Home, todayDestination("/home/items/new"))
        assertEquals(TodayDestination.Home, todayDestination("/home/items"))
        assertEquals(TodayDestination.Home, todayDestination("/home/items/i1/history"))
        assertEquals(TodayDestination.Room("r1"), todayDestination("/home/rooms/r1"))
        assertEquals(Money(MoneyLink.Home), todayDestination("/homes"))
    }
}
