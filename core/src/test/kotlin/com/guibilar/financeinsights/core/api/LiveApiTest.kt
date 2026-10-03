package com.guibilar.financeinsights.core.api

import com.guibilar.financeinsights.core.model.BreakdownInput
import com.guibilar.financeinsights.core.model.BudgetInput
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.CategoryRulePreviewInput
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.core.model.TransactionInput
import com.guibilar.financeinsights.core.model.TransactionLink
import com.guibilar.financeinsights.core.model.TransactionType
import com.guibilar.financeinsights.core.model.TransactionUpdate
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.LoginInput
import com.guibilar.financeinsights.core.model.MerchantsInput
import com.guibilar.financeinsights.core.model.TransactionFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Every call the app makes, against a running backend: set FINANCE_LIVE_API to its address, and FINANCE_LIVE_USER and
 * FINANCE_LIVE_PASSWORD to an account on it. Skipped otherwise (as in CI).
 */
class LiveApiTest {
    private val server = System.getenv("FINANCE_LIVE_API")

    @Test
    fun `signs in, reads everything, and signs out`() = runTest {
        assumeTrue("Set FINANCE_LIVE_API to run against a backend", server != null)
        var stored: String? = null
        val jar = PersistentCookieJar(object : CookieStorage {
            override fun read() = stored
            override fun write(value: String?) {
                stored = value
            }
        })
        var unauthorized = 0
        val api = createFinanceApi(ServerAddress.normalize(server!!)!!, jar, onUnauthorized = { unauthorized++ })

        val user = api.login(LoginInput(System.getenv("FINANCE_LIVE_USER"), System.getenv("FINANCE_LIVE_PASSWORD")))
        assertEquals(user.username, api.me().username)
        api.yearReport()
        api.monthReport()
        api.cashFlow()
        val page = api.transactions(TransactionFilter(page = 1, pageSize = 50))
        assertTrue(page.totalCount >= page.items.size)
        val breakdown = api.breakdown(BreakdownInput(interval = BreakdownInterval.Week))
        api.merchants(MerchantsInput(page = 2, pageSize = 8))
        api.budgets(BudgetFilter(page = 1, pageSize = 100))
        api.categories()
        api.accounts()
        assertEquals(page.totalCount, breakdown.count)

        api.logout()
        assertTrue(jar.isEmpty)
        assertTrue(apiCall { api.me() }.apiError!!.isUnauthorized)
        assertEquals(1, unauthorized)
    }

    @Test
    fun `adds, edits, links, deletes and restores, and imports a statement`() = runTest {
        assumeTrue("Set FINANCE_LIVE_API to run against a backend", server != null)
        var stored: String? = null
        val jar = PersistentCookieJar(object : CookieStorage {
            override fun read() = stored
            override fun write(value: String?) {
                stored = value
            }
        })
        val api = createFinanceApi(ServerAddress.normalize(server!!)!!, jar)
        api.login(LoginInput(System.getenv("FINANCE_LIVE_USER"), System.getenv("FINANCE_LIVE_PASSWORD")))
        val accounts = api.accounts()
        val manual = accounts.first { it.format == StatementFormat.Manual }
        val categories = api.categories()
        val dining = categories.first { it.name == "Dining" }
        val description = "Live test ${System.currentTimeMillis()}"

        val added = api.addTransaction(TransactionInput("2026-10-02", description, 12.34, TransactionType.Debit, manual.id))
        val duplicate = apiCall { api.addTransaction(TransactionInput("2026-10-02", description, 12.34, TransactionType.Debit, manual.id)) }.apiError!!
        assertTrue(duplicate.conflictOr("").isNotEmpty())
        val edited = api.updateTransaction(added.id, TransactionUpdate("$description edited", dining.id, Flow.Ignored))
        assertEquals(Triple("Dining", Flow.Ignored, true), Triple(edited.category.name, edited.flow, edited.flowLocked))
        api.previewCategoryRule(CategoryRulePreviewInput("LIVE TEST", dining.id, added.id))

        val budget = api.addBudget(BudgetInput("Live test budget", "d", Status.Active, 50.0, BudgetPeriod.Total))
        api.linkTransaction(TransactionLink(added.id, budget.id))
        assertEquals(budget.id, api.transactions(TransactionFilter(budgetId = budget.id)).items.single().budget!!.id)
        api.unlinkTransaction(added.id)
        api.updateBudget(budget.id, BudgetInput("Live test budget", "d", Status.Inactive, 60.0, BudgetPeriod.Custom, "2026-07-01", "2026-08-15"))
        api.deleteBudget(budget.id)

        api.deleteTransaction(added.id)
        assertTrue(api.transactions(TransactionFilter(hidden = true, description = description)).items.any { it.id == added.id })
        api.restoreTransaction(added.id)
        api.deleteTransaction(added.id)

        val cibc = accounts.first { it.format == StatementFormat.Cibc && it.type.name == "Chequing" }
        val result = api.importStatement("live.csv", "2026-09-30,LIVE TEST IMPORT ROW,1.23,\n".toByteArray(), cibc.id, confirmed = true)
        assertEquals(1, result.read)
        val notCsv = apiCall { api.importStatement("x.csv", "nothing here".toByteArray(), cibc.id) }.apiError!!
        assertEquals("No transactions found in this file. Is it a CIBC CSV export (date, description, money out, money in)?", notCsv.importMessage())
    }
}
