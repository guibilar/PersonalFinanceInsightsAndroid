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
import com.guibilar.financeinsights.core.model.AccountInput
import com.guibilar.financeinsights.core.model.AccountUpdate
import com.guibilar.financeinsights.core.model.BudgetInput
import com.guibilar.financeinsights.core.model.CategoryInput
import com.guibilar.financeinsights.core.model.CategoryRuleMove
import com.guibilar.financeinsights.core.model.ChangePasswordInput
import com.guibilar.financeinsights.core.model.FlowRule
import com.guibilar.financeinsights.core.model.FlowRuleInput
import com.guibilar.financeinsights.core.model.SignedOutSessions
import com.guibilar.financeinsights.core.model.CategoryRule
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.CategoryRulePreview
import com.guibilar.financeinsights.core.model.CategoryRulePreviewInput
import com.guibilar.financeinsights.core.model.ImportResult
import com.guibilar.financeinsights.core.model.RulesResult
import com.guibilar.financeinsights.core.model.TransactionInput
import com.guibilar.financeinsights.core.model.TransactionLink
import com.guibilar.financeinsights.core.model.TransactionUpdate
import com.guibilar.financeinsights.core.model.BulkUpdateResult
import com.guibilar.financeinsights.core.model.RecurringReport
import com.guibilar.financeinsights.core.model.ReviewReport
import com.guibilar.financeinsights.core.model.SpendingForecast
import com.guibilar.financeinsights.core.model.TransactionBulkUpdate
import com.guibilar.financeinsights.core.model.UncategorisedReport
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
import retrofit2.http.Query

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

    /** Subscriptions, bills and rent: payments that come again and again. */
    @GET("Reports/recurring")
    suspend fun recurring(): RecurringReport

    /** Where this month's spending is heading, and each monthly and yearly budget. */
    @GET("Reports/forecast")
    suspend fun forecast(): SpendingForecast

    /** Merchants left in Other because no rule names them. */
    @GET("Reports/uncategorised")
    suspend fun uncategorised(): UncategorisedReport

    /** A month, or a year without [month]; the last complete month without either. */
    @GET("Reports/review")
    suspend fun review(@Query("year") year: Int? = null, @Query("month") month: Int? = null): ReviewReport

    /** One change (category, flow, budget) for many transactions at once. */
    @POST("Transaction/Bulk")
    suspend fun bulkUpdate(@Body update: TransactionBulkUpdate): BulkUpdateResult

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

    /** Every category rule, in the order they are checked (position 0 first). */
    @GET("CategoryRules")
    suspend fun categoryRules(): List<CategoryRule>

    @PUT("CategoryRules/{id}")
    suspend fun updateCategoryRule(@Path("id") id: String, @Body rule: CategoryRuleInput): CategoryRule

    @DELETE("CategoryRules/{id}")
    suspend fun deleteCategoryRule(@Path("id") id: String)

    /** Moves a rule to another position; answers with every rule in the new order. */
    @POST("CategoryRules/{id}/Move")
    suspend fun moveCategoryRule(@Path("id") id: String, @Body move: CategoryRuleMove): List<CategoryRule>

    /** Every flow rule, by keyword: the longest keyword in a description wins. */
    @GET("FlowRules")
    suspend fun flowRules(): List<FlowRule>

    @POST("FlowRules")
    suspend fun addFlowRule(@Body rule: FlowRuleInput): FlowRule

    @PUT("FlowRules/{id}")
    suspend fun updateFlowRule(@Path("id") id: String, @Body rule: FlowRuleInput): FlowRule

    @DELETE("FlowRules/{id}")
    suspend fun deleteFlowRule(@Path("id") id: String)

    @POST("Categories")
    suspend fun addCategory(@Body category: CategoryInput): Category

    /** Renames and/or recolours it; everything using it follows. */
    @PUT("Categories/{id}")
    suspend fun updateCategory(@Path("id") id: String, @Body category: CategoryInput): Category

    /** Deletes it after moving its transactions and rules to [moveTo] (Other when null); built-in ones are a 409. */
    @DELETE("Categories/{id}")
    suspend fun deleteCategory(@Path("id") id: String, @Query("moveTo") moveTo: String?)

    @POST("Accounts")
    suspend fun addAccount(@Body account: AccountInput): Account

    @PUT("Accounts/{id}")
    suspend fun updateAccount(@Path("id") id: String, @Body account: AccountUpdate): Account

    /** Only an account without transactions (deleted ones included) can be deleted; otherwise a 409. */
    @DELETE("Accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: String)

    /** Every other session ends; this one goes on. A wrong current password is a 400 on currentPassword. */
    @POST("Auth/change-password")
    suspend fun changePassword(@Body change: ChangePasswordInput)

    /** Ends every other session of this account (other phones and browsers); this one stays. */
    @POST("Auth/sign-out-others")
    suspend fun signOutOthers(): SignedOutSessions
}
