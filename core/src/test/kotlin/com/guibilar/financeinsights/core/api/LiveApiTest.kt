package com.guibilar.financeinsights.core.api

import com.guibilar.financeinsights.core.model.BreakdownInput
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
}
