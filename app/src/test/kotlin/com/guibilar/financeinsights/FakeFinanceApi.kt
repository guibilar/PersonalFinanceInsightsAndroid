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
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.model.BudgetInput
import com.guibilar.financeinsights.core.model.CategoryReference
import com.guibilar.financeinsights.core.model.CategoryRule
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.CategoryRulePreview
import com.guibilar.financeinsights.core.model.CategoryRulePreviewInput
import com.guibilar.financeinsights.core.model.ImportResult
import com.guibilar.financeinsights.core.model.RulesResult
import com.guibilar.financeinsights.core.model.TransactionInput
import com.guibilar.financeinsights.core.model.TransactionLink
import com.guibilar.financeinsights.core.model.TransactionUpdate
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
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
    /** Calls (by name) answered with this API error instead. */
    val refusing = mutableMapOf<String, ApiError.Http>()
    /** What was sent, by call: the writes the app made. */
    val sent = mutableListOf<Pair<String, Any?>>()

    private inline fun <reified T> fixture(name: String): T {
        val text = javaClass.classLoader!!.getResource("fixtures/$name.json")!!.readText()
        return ApiJson.decodeFromString(text)
    }

    private fun check(call: String) {
        if (call in failing) throw IOException("offline")
        refusing[call]?.let { throw it }
    }

    private fun <T> write(call: String, body: Any?, answer: () -> T): T {
        sent += call to body
        check(call)
        return answer()
    }

    private fun transaction(id: String) = fixture<Page<Transaction>>("transactions").items.first { it.id == id }

    companion object {
        fun conflict(detail: String, code: String? = null) = ApiError.Http(409, "Conflict", detail, code, emptyMap())
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

    override suspend fun addTransaction(input: TransactionInput) = write("addTransaction", input) {
        fixture<Page<Transaction>>("recent").items.first().copy(id = "new", description = input.description)
    }

    override suspend fun updateTransaction(id: String, update: TransactionUpdate) = write("updateTransaction", id to update) { transaction(id) }
    override suspend fun deleteTransaction(id: String) = write("deleteTransaction", id) {}
    override suspend fun restoreTransaction(id: String) = write("restoreTransaction", id) {}
    override suspend fun linkTransaction(link: TransactionLink) = write("linkTransaction", link) {}
    override suspend fun unlinkTransaction(id: String) = write("unlinkTransaction", id) {}
    override suspend fun applyRules() = write("applyRules", null) { RulesResult(87, 3) }

    override suspend fun importStatement(file: MultipartBody.Part, accountId: RequestBody, confirmed: RequestBody?) =
        write("importStatement", listOf(accountId.text(), confirmed?.text())) { ImportResult(read = 10, added = 7, skipped = 3) }

    override suspend fun addCategoryRule(rule: CategoryRuleInput) = write("addCategoryRule", rule) {
        CategoryRule("r1", rule.keyword, CategoryReference(rule.categoryId, "Dining", "#1F76B5"), 0)
    }

    override suspend fun previewCategoryRule(rule: CategoryRulePreviewInput) = write("previewCategoryRule", rule) {
        CategoryRulePreview(4, listOf("NO FRILLS #3121", "NO FRILLS 12 CALGARY"))
    }

    override suspend fun addBudget(budget: BudgetInput) = write("addBudget", budget) { fixture<Page<Budget>>("budgets").items.first() }
    override suspend fun updateBudget(id: String, budget: BudgetInput) = write("updateBudget", id to budget) { fixture<Page<Budget>>("budgets").items.first() }
    override suspend fun deleteBudget(id: String) = write("deleteBudget", id) {}

    private fun RequestBody.text() = Buffer().also { writeTo(it) }.readUtf8()
}
