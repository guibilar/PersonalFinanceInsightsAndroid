package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.api.ApiJson
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.Breakdown
import com.guibilar.financeinsights.core.model.BreakdownInput
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.CashFlowReport
import com.guibilar.financeinsights.core.model.CategorizedReport
import com.guibilar.financeinsights.core.model.Category
import com.guibilar.financeinsights.core.model.CurrentUser
import com.guibilar.financeinsights.core.model.LoginInput
import com.guibilar.financeinsights.core.model.MerchantTotal
import com.guibilar.financeinsights.core.model.MerchantsInput
import com.guibilar.financeinsights.core.model.Page
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionFilter
import java.io.IOException

/**
 * The API answering with responses recorded from a real backend (app/src/test/resources/fixtures). Each call is
 * recorded, and any can be made to fail.
 */
class FakeFinanceApi : FinanceApi {
    val transactionFilters = mutableListOf<TransactionFilter>()
    val breakdownInputs = mutableListOf<BreakdownInput>()
    val merchantInputs = mutableListOf<MerchantsInput>()
    /** Calls (by name) that fail as if the server were unreachable. */
    val failing = mutableSetOf<String>()

    private inline fun <reified T> fixture(name: String): T {
        val text = javaClass.classLoader!!.getResource("fixtures/$name.json")!!.readText()
        return ApiJson.decodeFromString(text)
    }

    private fun check(call: String) {
        if (call in failing) throw IOException("offline")
    }

    override suspend fun login(login: LoginInput) = CurrentUser("dev").also { check("login") }
    override suspend fun logout() = check("logout")
    override suspend fun me() = CurrentUser("dev").also { check("me") }
    override suspend fun yearReport(): CategorizedReport = check("yearReport").let { fixture("year") }
    override suspend fun monthReport(): CategorizedReport = check("monthReport").let { fixture("month") }
    override suspend fun cashFlow(): CashFlowReport = check("cashFlow").let { fixture("cash-flow") }

    override suspend fun breakdown(input: BreakdownInput): Breakdown {
        breakdownInputs += input
        check("breakdown")
        return fixture<Breakdown>("breakdown-month").copy(interval = input.interval)
    }

    override suspend fun merchants(input: MerchantsInput): Page<MerchantTotal> {
        merchantInputs += input
        check("merchants")
        return fixture("merchants-page2")
    }

    override suspend fun transactions(filter: TransactionFilter): Page<Transaction> {
        transactionFilters += filter
        check("transactions")
        val all = fixture<Page<Transaction>>("transactions")
        if (filter.pageSize <= 5) return fixture("recent")
        // Two pages of the recorded 50, so paging can be seen.
        val half = all.items.size / 2
        val items = if (filter.page == 1) all.items.take(half) else all.items.drop(half)
        return Page(items, filter.page, half, totalCount = all.items.size, totalPages = 2, hasNextPage = filter.page == 1)
    }

    override suspend fun budgets(filter: BudgetFilter): Page<Budget> = check("budgets").let { fixture("budgets") }
    override suspend fun categories(): List<Category> = check("categories").let { fixture("categories") }
    override suspend fun accounts(): List<Account> = check("accounts").let { fixture("accounts") }
}
