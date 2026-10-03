package com.guibilar.financeinsights.core.api

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
import com.guibilar.financeinsights.core.model.BudgetInput
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
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/** The backend's REST API, under `<server>/api/`. Every call but [login] needs the session cookie [login] sets. */
interface FinanceApi {
    @POST("Auth/login")
    suspend fun login(@Body login: LoginInput): CurrentUser

    @POST("Auth/logout")
    suspend fun logout()

    @GET("Auth/me")
    suspend fun me(): CurrentUser

    @GET("Reports/monthly-categorized-graph")
    suspend fun yearReport(): CategorizedReport

    @GET("Reports/current-month-categorized-graph")
    suspend fun monthReport(): CategorizedReport

    @GET("Reports/cash-flow")
    suspend fun cashFlow(): CashFlowReport

    /** Totals for what the list's criteria pick, per period, with running totals and the top merchants. */
    @POST("Reports/breakdown")
    suspend fun breakdown(@Body input: BreakdownInput): Breakdown

    /** A page of the merchants the list's criteria spent at, largest first; page 1 is the breakdown's top ones. */
    @POST("Reports/merchants")
    suspend fun merchants(@Body input: MerchantsInput): Page<MerchantTotal>

    @POST("Transaction/Filter")
    suspend fun transactions(@Body filter: TransactionFilter): Page<Transaction>

    @POST("Budget/Filter")
    suspend fun budgets(@Body filter: BudgetFilter): Page<Budget>

    /** Every category, by name. */
    @GET("Categories")
    suspend fun categories(): List<Category>

    /** Every account: chequing, then savings, then credit cards, each by name. */
    @GET("Accounts")
    suspend fun accounts(): List<Account>

    /** Adds a transaction by hand; an identical one is a 409 unless [TransactionInput.allowDuplicate]. */
    @POST("Transaction")
    suspend fun addTransaction(@Body input: TransactionInput): Transaction

    @PUT("Transaction/{id}")
    suspend fun updateTransaction(@Path("id") id: String, @Body update: TransactionUpdate): Transaction

    /** Hides it from lists, reports and budgets; kept so a re-import doesn't add it back, and [restoreTransaction] undoes it. */
    @DELETE("Transaction/{id}")
    suspend fun deleteTransaction(@Path("id") id: String)

    @POST("Transaction/{id}/Restore")
    suspend fun restoreTransaction(@Path("id") id: String)

    @POST("Transaction/LinkTransaction")
    suspend fun linkTransaction(@Body link: TransactionLink)

    @POST("Transaction/UnlinkTransaction/{id}")
    suspend fun unlinkTransaction(@Path("id") id: String)

    /** Re-runs the category and flow rules over the stored transactions; picks made by hand stay. */
    @POST("Transaction/ApplyRules")
    suspend fun applyRules(): RulesResult

    /**
     * Imports a statement file into an account. A file that looks like another account's is a 409 with code
     * "account-mismatch", unless [confirmed] is "true".
     */
    @Multipart
    @POST("Transaction/Import")
    suspend fun importStatement(
        @Part file: MultipartBody.Part,
        @Part("accountId") accountId: RequestBody,
        @Part("confirmed") confirmed: RequestBody?,
    ): ImportResult

    @POST("CategoryRules")
    suspend fun addCategoryRule(@Body rule: CategoryRuleInput): CategoryRule

    /** What adding this rule first and re-applying the rules would change. */
    @POST("CategoryRules/Preview")
    suspend fun previewCategoryRule(@Body rule: CategoryRulePreviewInput): CategoryRulePreview

    @POST("Budget")
    suspend fun addBudget(@Body budget: BudgetInput): Budget

    @PUT("Budget/{id}")
    suspend fun updateBudget(@Path("id") id: String, @Body budget: BudgetInput): Budget

    /** Its linked transactions stay, unlinked. */
    @DELETE("Budget/{id}")
    suspend fun deleteBudget(@Path("id") id: String)
}
