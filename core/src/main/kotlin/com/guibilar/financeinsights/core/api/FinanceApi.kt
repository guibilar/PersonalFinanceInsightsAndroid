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
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
}
