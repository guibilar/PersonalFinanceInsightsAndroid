package com.guibilar.financeinsights.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginInput(
    val username: String,
    val password: String,
    /** Keeps the session for 14 days of inactivity, like "Remember me" on the web. */
    val rememberMe: Boolean = true,
)

/** What the transaction list shows: its criteria, without paging and sorting. */
@Serializable
data class TransactionCriteria(
    val description: String? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val flow: Flow? = null,
    /** Inclusive, "yyyy-MM-dd". */
    val startDate: String? = null,
    /** Inclusive, "yyyy-MM-dd". */
    val endDate: String? = null,
) {
    val isEmpty get() = this == TransactionCriteria()
}

@Serializable
data class TransactionFilter(
    val description: String? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val flow: Flow? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val budgetId: String? = null,
    /** True: only deleted (hidden) transactions. */
    val hidden: Boolean? = null,
    val sortBy: TransactionSortField? = null,
    val sortDirection: SortDirection? = null,
    val page: Int = 1,
    val pageSize: Int = 25,
) {
    companion object {
        fun of(criteria: TransactionCriteria, sortBy: TransactionSortField, direction: SortDirection, page: Int, pageSize: Int) =
            TransactionFilter(
                description = criteria.description,
                categoryId = criteria.categoryId,
                accountId = criteria.accountId,
                flow = criteria.flow,
                startDate = criteria.startDate,
                endDate = criteria.endDate,
                sortBy = sortBy,
                sortDirection = direction,
                page = page,
                pageSize = pageSize,
            )
    }
}

@Serializable
data class BudgetFilter(
    val name: String? = null,
    val status: Status? = null,
    val page: Int = 1,
    val pageSize: Int = 10,
)

@Serializable
data class BreakdownInput(
    val description: String? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val flow: Flow? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val interval: BreakdownInterval = BreakdownInterval.Month,
) {
    companion object {
        fun of(criteria: TransactionCriteria, interval: BreakdownInterval) = BreakdownInput(
            criteria.description, criteria.categoryId, criteria.accountId, criteria.flow,
            criteria.startDate, criteria.endDate, interval,
        )
    }
}

@Serializable
data class MerchantsInput(
    val description: String? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val flow: Flow? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val page: Int = 1,
    val pageSize: Int = 8,
) {
    companion object {
        fun of(criteria: TransactionCriteria, page: Int, pageSize: Int) = MerchantsInput(
            criteria.description, criteria.categoryId, criteria.accountId, criteria.flow,
            criteria.startDate, criteria.endDate, page, pageSize,
        )
    }
}
