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
    val tagId: String? = null,
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
    val tagId: String? = null,
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
                tagId = criteria.tagId,
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
    /** Part of the name, ignoring case. */
    val name: String? = null,
    val status: Status? = null,
    val page: Int = 1,
    val pageSize: Int = 10,
)

@Serializable
data class BreakdownInput(
    val description: String? = null,
    val categoryId: String? = null,
    val tagId: String? = null,
    val accountId: String? = null,
    val flow: Flow? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val interval: BreakdownInterval = BreakdownInterval.Month,
) {
    companion object {
        fun of(criteria: TransactionCriteria, interval: BreakdownInterval) = BreakdownInput(
            criteria.description, criteria.categoryId, criteria.tagId, criteria.accountId, criteria.flow,
            criteria.startDate, criteria.endDate, interval,
        )
    }
}

@Serializable
data class MerchantsInput(
    val description: String? = null,
    val categoryId: String? = null,
    val tagId: String? = null,
    val accountId: String? = null,
    val flow: Flow? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val page: Int = 1,
    val pageSize: Int = 8,
) {
    companion object {
        fun of(criteria: TransactionCriteria, page: Int, pageSize: Int) = MerchantsInput(
            criteria.description, criteria.categoryId, criteria.tagId, criteria.accountId, criteria.flow,
            criteria.startDate, criteria.endDate, page, pageSize,
        )
    }
}

/** A transaction added by hand. Category and flow left out: the rules decide, as for an imported row. */
@Serializable
data class TransactionInput(
    /** "yyyy-MM-dd". */
    val date: String,
    val description: String,
    /** Always positive; [transactionType] says which way the money moved. */
    val amount: Double,
    val transactionType: TransactionType,
    val accountId: String,
    val categoryId: String? = null,
    val flow: Flow? = null,
    val note: String? = null,
    /** Adds it although an identical one exists (two coffees at the same price on the same day). */
    val allowDuplicate: Boolean = false,
)

/** Corrects an imported transaction: a changed category or flow is then kept when the rules are re-applied. */
@Serializable
data class TransactionUpdate(
    val description: String,
    val categoryId: String,
    /** Sent only when changed. */
    val flow: Flow? = null,
    /** Sent only when changed; empty clears it. */
    val note: String? = null,
    /** The full set, sent only when changed. */
    val tagIds: List<String>? = null,
)

@Serializable
data class TagInput(val name: String)

@Serializable
data class TransactionLink(val transactionId: String, val budgetId: String)

@Serializable
data class CategoryRuleInput(
    val keyword: String,
    val categoryId: String,
    /** 0 is checked first; at the end when left out. */
    val position: Int? = null,
)

@Serializable
data class CategoryRulePreviewInput(
    val keyword: String,
    val categoryId: String,
    val excludeTransactionId: String? = null,
)

@Serializable
data class BudgetInput(
    val name: String,
    val description: String,
    val status: Status,
    val amount: Double,
    val period: BudgetPeriod,
    /** Custom periods only, "yyyy-MM-dd". */
    val startDate: String? = null,
    val endDate: String? = null,
    /** Any period but Total; not with a merchant. */
    val categoryIds: List<String> = emptyList(),
    /** Any period but Total; not with categories. */
    val merchant: String? = null,
)

@Serializable
data class CategoryRuleMove(val position: Int)

@Serializable
data class FlowRuleInput(val keyword: String, val flow: Flow)

@Serializable
data class CategoryInput(
    val name: String,
    /** "#RRGGBB". */
    val color: String,
)

/** A new account; its type can't change afterwards. */
@Serializable
data class AccountInput(val name: String, val type: AccountType, val format: StatementFormat)

/** Renames an account and/or corrects its statement format. */
@Serializable
data class AccountUpdate(val name: String, val format: StatementFormat)

@Serializable
data class ChangePasswordInput(val currentPassword: String, val newPassword: String)
