package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.api.ApiJson
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.model.BulkUpdateResult
import com.guibilar.financeinsights.core.model.RecurringReport
import com.guibilar.financeinsights.core.model.ReviewReport
import com.guibilar.financeinsights.core.model.SpendingForecast
import com.guibilar.financeinsights.core.model.TransactionBulkUpdate
import com.guibilar.financeinsights.core.model.UncategorisedReport
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
import com.guibilar.financeinsights.core.model.AccountBalance
import com.guibilar.financeinsights.core.model.AccountBalanceInput
import com.guibilar.financeinsights.core.model.NetWorthReport
import com.guibilar.financeinsights.core.model.Tag
import com.guibilar.financeinsights.core.model.TagInput
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionFilter
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.model.AccountInput
import com.guibilar.financeinsights.core.model.AccountUpdate
import com.guibilar.financeinsights.core.model.BudgetInput
import com.guibilar.financeinsights.core.model.CategoryInput
import com.guibilar.financeinsights.core.model.CategoryRuleMove
import com.guibilar.financeinsights.core.model.ChangePasswordInput
import com.guibilar.financeinsights.core.model.FlowRule
import com.guibilar.financeinsights.core.model.FlowRuleInput
import com.guibilar.financeinsights.core.model.SignedOutSessions
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
    /** Changes the listed transactions (to give some a note and tags, say). */
    var decorate: (Transaction) -> Transaction = { it }
    val breakdownInputs = mutableListOf<BreakdownInput>()
    val merchantInputs = mutableListOf<MerchantsInput>()
    /** Calls (by name) that fail as if the server were unreachable. */
    val failing = mutableSetOf<String>()
    /** Calls (by name) answered with this API error instead. */
    val refusing = mutableMapOf<String, ApiError.Http>()
    /** Calls (by name) that take this long (ms) to answer. */
    val slow = mutableMapOf<String, Long>()
    /** How many budgets there are, when more than the recorded ones: the later pages repeat them under new ids. */
    var budgetCount: Int? = null
    /** How the next imports go, one per file: an [ImportResult], or an [ApiError.Http] to refuse that file. */
    val importAnswers = ArrayDeque<Any>()
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
    override suspend fun yearReport(): CategorizedReport {
        slow["yearReport"]?.let { kotlinx.coroutines.delay(it) }
        return check("yearReport").let { fixture("year") }
    }
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
        val items = (if (filter.page == 1) all.items.take(half) else all.items.drop(half)).map(decorate)
        return Page(items, filter.page, half, totalCount = all.items.size, totalPages = 2, hasNextPage = filter.page == 1)
    }

    override suspend fun recurring(): RecurringReport = check("recurring").let { fixture("recurring") }
    override suspend fun forecast(): SpendingForecast = check("forecast").let { fixture("forecast") }
    override suspend fun uncategorised(): UncategorisedReport = check("uncategorised").let { uncategorisedReport ?: fixture("uncategorised") }
    /** What the uncategorised report gives instead of the recorded one. */
    var uncategorisedReport: UncategorisedReport? = null
    /** The reviews asked for, as (year, month). */
    val reviewRequests = mutableListOf<Pair<Int?, Int?>>()
    override suspend fun review(year: Int?, month: Int?): ReviewReport {
        reviewRequests += year to month
        check("review")
        return if (year != null && month == null) fixture("review-year") else fixture("review-month")
    }
    override suspend fun bulkUpdate(update: TransactionBulkUpdate) = write("bulkUpdate", update) { BulkUpdateResult(update.transactionIds.size) }

    override suspend fun budgets(filter: BudgetFilter): Page<Budget> {
        check("budgets")
        val recorded = fixture<Page<Budget>>("budgets")
        val count = budgetCount ?: return recorded
        val items = recorded.items.map { it.copy(id = "${it.id}-${filter.page}") }
        return Page(items, filter.page, items.size, totalCount = count, totalPages = (count + items.size - 1) / items.size, hasNextPage = filter.page * items.size < count)
    }
    /** Categories added to the recorded ones, for cases the recording doesn't have. */
    val extraCategories = mutableListOf<Category>()
    override suspend fun categories(): List<Category> = check("categories").let { fixture<List<Category>>("categories") + extraCategories }
    override suspend fun accounts(): List<Account> = check("accounts").let { fixture<List<Account>>("accounts") + extraAccounts }

    /** Accounts beyond the recorded ones (an investment, say). */
    val extraAccounts = mutableListOf<Account>()
    /** The balances listed for any account, newest first. */
    var accountBalances = listOf(
        AccountBalance("b2", "2026-10-01T00:00:00Z", 12_500.0),
        AccountBalance("b1", "2026-09-30T00:00:00Z", 12_000.0),
    )

    override suspend fun balances(id: String): List<AccountBalance> = check("balances").let { accountBalances }
    override suspend fun setBalance(id: String, balance: AccountBalanceInput) =
        write("setBalance", id to balance) { AccountBalance("new", "${balance.date}T00:00:00Z", balance.amount) }
    override suspend fun deleteBalance(id: String, balanceId: String) = write("deleteBalance", id to balanceId) {}
    override suspend fun netWorth(): NetWorthReport = check("netWorth").let { netWorthReport ?: fixture("net-worth") }
    var netWorthReport: NetWorthReport? = null

    override suspend fun addTransaction(input: TransactionInput) = write("addTransaction", input) {
        fixture<Page<Transaction>>("recent").items.first().copy(id = "new", description = input.description)
    }

    override suspend fun updateTransaction(id: String, update: TransactionUpdate) = write("updateTransaction", id to update) { transaction(id) }
    override suspend fun deleteTransaction(id: String) = write("deleteTransaction", id) {}
    override suspend fun restoreTransaction(id: String) = write("restoreTransaction", id) {}
    override suspend fun linkTransaction(link: TransactionLink) = write("linkTransaction", link) {}
    override suspend fun unlinkTransaction(id: String) = write("unlinkTransaction", id) {}
    override suspend fun applyRules() = write("applyRules", null) { RulesResult(87, 3) }

    override suspend fun importStatement(file: MultipartBody.Part, accountId: RequestBody, confirmed: RequestBody?): ImportResult {
        slow["importStatement"]?.let { kotlinx.coroutines.delay(it) }
        return write("importStatement", listOf(accountId.text(), confirmed?.text())) {
            when (val answer = importAnswers.removeFirstOrNull()) {
                is ApiError.Http -> throw answer
                is ImportResult -> answer
                else -> ImportResult(read = 10, added = 7, skipped = 3)
            }
        }
    }

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

    override suspend fun categoryRules(): List<CategoryRule> = check("categoryRules").let { fixture("category-rules") }
    override suspend fun updateCategoryRule(id: String, rule: CategoryRuleInput) = write("updateCategoryRule", id to rule) { fixture<List<CategoryRule>>("category-rules").first { it.id == id } }
    override suspend fun deleteCategoryRule(id: String) = write("deleteCategoryRule", id) {}

    /** Moves it like the API: out of its place, into [move]'s, positions renumbered. */
    override suspend fun moveCategoryRule(id: String, move: CategoryRuleMove) = write("moveCategoryRule", id to move.position) {
        val rules = fixture<List<CategoryRule>>("category-rules").toMutableList()
        val rule = rules.first { it.id == id }
        rules.remove(rule)
        rules.add(move.position.coerceAtMost(rules.size), rule)
        rules.mapIndexed { index, r -> r.copy(position = index) }
    }

    override suspend fun flowRules(): List<FlowRule> = check("flowRules").let { fixture("flow-rules") }
    override suspend fun addFlowRule(rule: FlowRuleInput) = write("addFlowRule", rule) { FlowRule("f-new", rule.keyword.uppercase(), rule.flow) }
    override suspend fun updateFlowRule(id: String, rule: FlowRuleInput) = write("updateFlowRule", id to rule) { FlowRule(id, rule.keyword.uppercase(), rule.flow) }
    override suspend fun deleteFlowRule(id: String) = write("deleteFlowRule", id) {}
    override suspend fun addCategory(category: CategoryInput) = write("addCategory", category) { fixture<List<Category>>("categories").first() }
    override suspend fun updateCategory(id: String, category: CategoryInput) = write("updateCategory", id to category) { fixture<List<Category>>("categories").first { it.id == id } }
    override suspend fun deleteCategory(id: String, moveTo: String?) = write("deleteCategory", id to moveTo) {}
    override suspend fun addAccount(account: AccountInput) = write("addAccount", account) { fixture<List<Account>>("accounts").first() }
    override suspend fun updateAccount(id: String, account: AccountUpdate) = write("updateAccount", id to account) { fixture<List<Account>>("accounts").first { it.id == id } }
    override suspend fun deleteAccount(id: String) = write("deleteAccount", id) {}
    override suspend fun tags(): List<Tag> = check("tags").let { fixture<List<Tag>>("tags") }
    override suspend fun addTag(tag: TagInput) = write("addTag", tag) { Tag("new-tag", tag.name) }
    override suspend fun renameTag(id: String, tag: TagInput) = write("renameTag", id to tag) {
        fixture<List<Tag>>("tags").first { it.id == id }.copy(name = tag.name)
    }
    override suspend fun deleteTag(id: String) = write("deleteTag", id) {}
    override suspend fun changePassword(change: ChangePasswordInput) = write("changePassword", change) {}
    override suspend fun signOutOthers() = write("signOutOthers", null) { SignedOutSessions(2) }
}
