package com.guibilar.financeinsights.ui.settings

import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.describeRulesResult
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountBalance
import com.guibilar.financeinsights.core.model.AccountBalanceInput
import com.guibilar.financeinsights.core.model.AccountInput
import com.guibilar.financeinsights.core.model.AccountUpdate
import com.guibilar.financeinsights.core.model.Category
import com.guibilar.financeinsights.core.model.CategoryInput
import com.guibilar.financeinsights.core.model.CategoryRule
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.CategoryRuleMove
import com.guibilar.financeinsights.core.model.FlowRule
import com.guibilar.financeinsights.core.model.FlowRuleInput
import com.guibilar.financeinsights.core.model.NetWorthReport
import com.guibilar.financeinsights.core.model.Tag
import com.guibilar.financeinsights.core.model.TagInput
import com.guibilar.financeinsights.core.model.UncategorisedReport
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RulesState(
    val rules: LoadState<List<CategoryRule>> = LoadState.Loading,
    val flowRules: LoadState<List<FlowRule>> = LoadState.Loading,
    val categories: List<Category> = emptyList(),
    /** A move is on its way: positions are about to change, so the move buttons wait. */
    val moving: Boolean = false,
    /** The merchants left in Other because no rule names them; null while unknown. */
    val uncategorised: UncategorisedReport? = null,
)

/**
 * The rules: category rules in the order they are checked (the first whose keyword is in a description decides),
 * and flow rules (the longest keyword wins). Changes apply to later imports; applying them updates what is stored.
 */
class RulesViewModel(private val api: FinanceApi) : SettingsViewModel() {
    private val _state = MutableStateFlow(RulesState())
    val state: StateFlow<RulesState> = _state.asStateFlow()

    init {
        reload()
    }

    override fun reloadOnResume() = reload()

    fun reload() {
        viewModelScope.launch {
            val rules = load("Could not load the category rules.") { api.categoryRules() }
            _state.update { it.copy(rules = rules) }
        }
        viewModelScope.launch {
            val flowRules = load("Could not load the flow rules.") { api.flowRules() }
            _state.update { it.copy(flowRules = flowRules) }
        }
        viewModelScope.launch {
            apiCall { api.categories() }.getOrNull()?.let { categories -> _state.update { it.copy(categories = categories) } }
        }
        viewModelScope.launch {
            apiCall { api.uncategorised() }.getOrNull()?.let { report -> _state.update { it.copy(uncategorised = report) } }
        }
    }

    /**
     * A merchant left in Other sorted at once: a rule for [keyword] into [category], then the rules applied. Null when
     * done (with a message saying how many moved), or why not. A rule into Other keeps them there on purpose: the
     * merchant isn't listed again.
     */
    suspend fun sortMerchant(keyword: String, category: Category): String? = outlivingScreen sort@{
        val trimmed = keyword.trim().uppercase()
        if (trimmed.isEmpty()) return@sort "Enter a keyword."
        val added = apiCall { api.addCategoryRule(CategoryRuleInput(trimmed, category.id)) }
        added.apiError?.let { return@sort it.conflictOr(it.validationOr(it.loadMessage("Could not add the rule."))) }
        val applied = apiCall { api.applyRules() }.getOrNull()
        say(
            if (applied == null) "Rule “$trimmed” added, but the rules could not be applied."
            else if (category.isFallback) "Rule “$trimmed” added: it stays in ${category.name} and isn't listed here any more"
            // Applying runs every rule, so the count is all it changed, not only this merchant's.
            else "Rule “$trimmed” added for ${category.name}: ${applied.updated} ${if (applied.updated == 1) "transaction" else "transactions"} updated by the rules",
        )
        reload()
        null
    }

    /**
     * Where a rule moves to: the place of the rule shown above it (-1) or below it (+1) in [visible], so a move while
     * searching passes the rule you can see. Null at either end.
     */
    fun movePosition(visible: List<CategoryRule>, rule: CategoryRule, offset: Int): Int? =
        visible.getOrNull(visible.indexOfFirst { it.id == rule.id } + offset)?.position

    fun move(visible: List<CategoryRule>, rule: CategoryRule, offset: Int) {
        moveTo(rule, movePosition(visible, rule, offset) ?: return)
    }

    /** Moves [rule] to [position] (dragged there); the others shift to make room. */
    fun moveTo(rule: CategoryRule, position: Int) {
        if (position == rule.position || _state.value.moving) return
        _state.update { it.copy(moving = true) }
        viewModelScope.launch {
            val result = apiCall { api.moveCategoryRule(rule.id, CategoryRuleMove(position)) }
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(rules = LoadState.Loaded(it), moving = false) },
                    onFailure = { state.copy(moving = false) },
                )
            }
            if (result.isFailure) {
                say("Could not move “${rule.keyword}”.")
                reload()
            }
        }
    }

    suspend fun saveRule(editing: CategoryRule?, input: CategoryRuleInput): String? = change(
        if (editing == null) "Could not add the rule." else "Could not save the rule.",
        { if (editing == null) api.addCategoryRule(input) else api.updateCategoryRule(editing.id, input) },
    ) {
        reload()
        say(if (editing == null) "Added “${input.keyword.trim().uppercase()}”" else "Saved “${input.keyword.trim().uppercase()}”")
    }

    fun deleteRule(rule: CategoryRule) = viewModelScope.launch {
        val error = change("Could not delete “${rule.keyword}”.", { api.deleteCategoryRule(rule.id) }) {
            reload()
            say("Deleted “${rule.keyword}”")
        }
        error?.let(::say)
    }

    suspend fun saveFlowRule(editing: FlowRule?, input: FlowRuleInput): String? = change(
        if (editing == null) "Could not add the rule." else "Could not save the rule.",
        { if (editing == null) api.addFlowRule(input) else api.updateFlowRule(editing.id, input) },
    ) {
        reload()
        say(if (editing == null) "Added “${input.keyword.trim().uppercase()}”" else "Saved “${input.keyword.trim().uppercase()}”")
    }

    fun deleteFlowRule(rule: FlowRule) = viewModelScope.launch {
        val error = change("Could not delete “${rule.keyword}”.", { api.deleteFlowRule(rule.id) }) {
            reload()
            say("Deleted “${rule.keyword}”")
        }
        error?.let(::say)
    }

    fun applyRules() = viewModelScope.launch {
        val result = apiCall { api.applyRules() }
        say(result.getOrNull()?.let(::describeRulesResult) ?: result.apiError!!.loadMessage("Could not apply the rules."))
        // What is left in Other changes with them: "Sort them" used to keep counting the merchants just sorted.
        if (result.isSuccess) reload()
    }
}

/** The categories transactions, rules and budgets use: add, rename and recolour, delete (moving what used it). */
class CategoriesViewModel(private val api: FinanceApi) : SettingsViewModel() {
    private val _state = MutableStateFlow<LoadState<List<Category>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<Category>>> = _state.asStateFlow()

    init {
        reload()
    }

    override fun reloadOnResume() = reload()

    fun reload() {
        viewModelScope.launch { _state.value = load("Could not load the categories.") { api.categories() } }
    }

    suspend fun save(editing: Category?, input: CategoryInput): String? = change(
        if (editing == null) "Could not add the category." else "Could not save the category.",
        { if (editing == null) api.addCategory(input) else api.updateCategory(editing.id, input) },
    ) {
        reload()
        say(if (editing == null) "Added “${input.name.trim()}”" else "Saved “${input.name.trim()}”")
    }

    /** Deletes it; what used it (transactions and rules) moves to [moveTo]. */
    fun delete(category: Category, moveTo: Category?) = viewModelScope.launch {
        val error = change("Could not delete “${category.name}”.", { api.deleteCategory(category.id, moveTo?.id) }) {
            reload()
            val moved = category.transactionCount + category.ruleCount > 0
            say("Deleted “${category.name}”" + if (moved && moveTo != null) "; what used it is now in “${moveTo.name}”" else "")
        }
        if (error != null) {
            reload()
            say(error)
        }
    }
}

/** The bank accounts statements are imported into: add, rename or correct the format, delete the empty ones. */
/** [today] gives the day of a new balance and this year's contribution room; tests give a fixed day (T28). */
class AccountsViewModel(private val api: FinanceApi, val today: () -> java.time.LocalDate = java.time.LocalDate::now) : SettingsViewModel() {
    private val _state = MutableStateFlow<LoadState<List<Account>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<Account>>> = _state.asStateFlow()

    init {
        reload()
    }

    override fun reloadOnResume() = reload()

    fun reload() {
        viewModelScope.launch { _state.value = load("Could not load the accounts.") { api.accounts() } }
    }

    suspend fun add(input: AccountInput): String? = change("Could not add the account.", { api.addAccount(input) }) {
        reload()
        say("Added “${input.name.trim()}”")
    }

    suspend fun update(account: Account, update: AccountUpdate): String? =
        change("Could not save the account.", { api.updateAccount(account.id, update) }) {
            reload()
            say("Saved “${update.name.trim()}”")
        }

    /** The balances entered for an account, newest first; null when they couldn't be loaded. */
    suspend fun balances(account: Account): List<AccountBalance>? = apiCall { api.balances(account.id) }.getOrNull()

    /** Its balance at the end of [date]; null when saved, or why not. */
    suspend fun setBalance(account: Account, date: java.time.LocalDate, amount: Double): String? =
        change("Could not save the balance.", { api.setBalance(account.id, AccountBalanceInput(date.toString(), amount)) }) {
            reload()
            say("Balance saved")
        }

    suspend fun deleteBalance(account: Account, balance: AccountBalance): String? =
        change("Could not delete the balance.", { api.deleteBalance(account.id, balance.id) }) { reload() }

    fun delete(account: Account) = viewModelScope.launch {
        val error = change("Could not delete “${account.name}”.", { api.deleteAccount(account.id) }) {
            reload()
            say("Deleted “${account.name}”")
        }
        if (error != null) {
            // A 409 says it has transactions after all (imported since the list loaded).
            reload()
            say(error)
        }
    }
}

/** Free labels across categories: add, rename, delete (taking it off every transaction). */
class TagsViewModel(private val api: FinanceApi) : SettingsViewModel() {
    private val _state = MutableStateFlow<LoadState<List<Tag>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<Tag>>> = _state.asStateFlow()

    init {
        reload()
    }

    override fun reloadOnResume() = reload()

    fun reload() {
        viewModelScope.launch { _state.value = load("Could not load the tags.") { api.tags() } }
    }

    suspend fun save(editing: Tag?, name: String): String? {
        val input = TagInput(name.trim())
        return change(
            if (editing == null) "Could not add the tag." else "Could not rename the tag.",
            { if (editing == null) api.addTag(input) else api.renameTag(editing.id, input) },
        ) {
            reload()
            say(if (editing == null) "Added “${input.name}”" else "Renamed to “${input.name}”")
        }
    }

    fun delete(tag: Tag) = viewModelScope.launch {
        val error = change("Could not delete “${tag.name}”.", { api.deleteTag(tag.id) }) {
            reload()
            say("Deleted “${tag.name}”")
        }
        if (error != null) {
            reload()
            say(error)
        }
    }
}

/** What everything is worth: today's net worth and each of the last twelve month ends, from the balances entered. */
class NetWorthViewModel(private val api: FinanceApi) : SettingsViewModel() {
    private val _state = MutableStateFlow<LoadState<NetWorthReport>>(LoadState.Loading)
    val state: StateFlow<LoadState<NetWorthReport>> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val result = load("Could not work out your net worth.") { api.netWorth() }
            // A failed reload on coming back keeps what was showing.
            if (result !is LoadState.Failed || _state.value !is LoadState.Loaded) _state.value = result
        }
    }

    override fun reloadOnResume() = reload()
}
