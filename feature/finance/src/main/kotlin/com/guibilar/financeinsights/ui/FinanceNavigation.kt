package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.guibilar.financeinsights.QuickAction
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.logic.ReviewPeriod
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.TransactionCriteria
import com.guibilar.financeinsights.ui.budgets.BudgetsScreen
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
import com.guibilar.financeinsights.ui.dashboard.DashboardScreen
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.insights.RecurringScreen
import com.guibilar.financeinsights.ui.insights.RecurringViewModel
import com.guibilar.financeinsights.ui.insights.ReviewScreen
import com.guibilar.financeinsights.ui.insights.ReviewViewModel
import com.guibilar.financeinsights.ui.money.MoneyHomeScreen
import com.guibilar.financeinsights.ui.settings.AccountsScreen
import com.guibilar.financeinsights.ui.settings.AccountsViewModel
import com.guibilar.financeinsights.ui.settings.CategoriesScreen
import com.guibilar.financeinsights.ui.settings.CategoriesViewModel
import com.guibilar.financeinsights.ui.settings.NetWorthScreen
import com.guibilar.financeinsights.ui.settings.NetWorthViewModel
import com.guibilar.financeinsights.ui.settings.RulesScreen
import com.guibilar.financeinsights.ui.settings.RulesViewModel
import com.guibilar.financeinsights.ui.settings.TagsScreen
import com.guibilar.financeinsights.ui.settings.TagsViewModel
import com.guibilar.financeinsights.ui.transactions.PickedFile
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

// The finance module's way in: its routes, the Money home, what it takes from outside the screens (shared statements,
// quick actions), and financeGraph, which the app's navigation host calls. The app's own pages stay in the app.

/** The Money module's home: the headline tiles and a row for each finance page. */
@Serializable
data object MoneyHomeRoute

@Serializable
data object DashboardRoute

@Serializable
data object BudgetsRoute

@Serializable
data object RulesRoute

@Serializable
data object CategoriesRoute

@Serializable
data object AccountsRoute

@Serializable
data object SubscriptionsRoute

@Serializable
data object TagsRoute

@Serializable
data object NetWorthRoute

/** A month's review, or a year's without [month]; the last complete month without either. */
@Serializable
data class ReviewRoute(val year: Int? = null, val month: Int? = null)

/** The transactions list, optionally opened on criteria (the dashboard's links) with its "Over time" chart. */
@Serializable
data class TransactionsRoute(
    val categoryId: String? = null,
    /** A tag's transactions (from Tags). */
    val tagId: String? = null,
    /** Part of the description: a merchant's transactions. */
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val overTime: Boolean = false,
    val interval: BreakdownInterval? = null,
) {
    val criteria get() = TransactionCriteria(description = description, categoryId = categoryId, tagId = tagId, startDate = startDate, endDate = endDate)
}

/** Whether a page is one of the Money module's: the app's bar shows them under Modules. */
fun NavDestination.isFinancePage(): Boolean =
    hasRoute<MoneyHomeRoute>() || hasRoute<DashboardRoute>() || hasRoute<TransactionsRoute>() || hasRoute<BudgetsRoute>() ||
        hasRoute<NetWorthRoute>() || hasRoute<ReviewRoute>() || hasRoute<SubscriptionsRoute>() || hasRoute<AccountsRoute>() ||
        hasRoute<CategoriesRoute>() || hasRoute<TagsRoute>() || hasRoute<RulesRoute>()

/**
 * Opens the Money home from outside the Money pages (Today, Capture, the shortcut, a notification), as a tab opens:
 * over the first page, and the page that was open keeps its own back stack. Back from the Money home returns there.
 */
fun NavHostController.openMoneyHome() {
    navigate(MoneyHomeRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/**
 * Opens a finance page from outside it, over the Money home: Back returns to the Money home, and then to where the
 * user came from. Inside the Money pages, it replaces what is over the Money home.
 */
fun NavHostController.openFinancePage(route: Any) {
    if (!hasMoneyHome()) openMoneyHome()
    if (route != MoneyHomeRoute) navigate(route) { popUpTo<MoneyHomeRoute>() }
}

private fun NavHostController.hasMoneyHome() = runCatching { getBackStackEntry<MoneyHomeRoute>() }.isSuccess

/** Where a Money tile or an attention item leads. */
enum class MoneyLink { NetWorth, Budgets, Import, Subscriptions, Rules }

/**
 * What came from outside the screens, until they take it: statements shared to the app, and the "Add transaction"
 * and import requests (from the shortcut, the widget, a notification or the dashboard).
 */
@Stable
class FinanceRequests internal constructor(
    private val sharedStatement: MutableStateFlow<List<PickedFile>?>,
    private val statementState: State<List<PickedFile>?>,
    private val addState: MutableState<Boolean>,
    private val importState: MutableState<Boolean>,
) {
    val statement: List<PickedFile>? get() = statementState.value
    var addRequested: Boolean
        get() = addState.value
        internal set(value) {
            addState.value = value
        }
    var importRequested: Boolean
        get() = importState.value
        internal set(value) {
            importState.value = value
        }

    internal fun statementTaken() {
        sharedStatement.value = null
    }
}

/** Opens what a Money tile or attention item leads to, from outside the Money pages (Today). */
fun FinanceRequests.open(nav: NavHostController, link: MoneyLink) {
    when (link) {
        MoneyLink.NetWorth -> nav.openFinancePage(NetWorthRoute)
        MoneyLink.Budgets -> nav.openFinancePage(BudgetsRoute)
        MoneyLink.Subscriptions -> nav.openFinancePage(SubscriptionsRoute)
        MoneyLink.Rules -> nav.openFinancePage(RulesRoute)
        MoneyLink.Import -> {
            importRequested = true
            nav.openFinancePage(TransactionsRoute())
        }
    }
}

/**
 * Follows shared statements and quick actions: each opens its finance page on [nav]. Call it in the composable that
 * holds the navigation host, before the host.
 */
@Composable
fun rememberFinanceRequests(
    nav: NavHostController,
    sharedStatement: MutableStateFlow<List<PickedFile>?>,
    quickAction: MutableStateFlow<QuickAction?>,
): FinanceRequests {
    val entry by nav.currentBackStackEntryAsState()
    val statement = sharedStatement.collectAsState()
    // A statement shared to the app opens the transactions, where the import takes it.
    LaunchedEffect(statement.value) {
        if (statement.value != null && entry?.destination?.hasRoute<TransactionsRoute>() != true) {
            nav.openFinancePage(TransactionsRoute())
        }
    }
    // The launcher shortcut, the widget, the notifications and Capture. Each opens its page over the Money home.
    val action by quickAction.collectAsState()
    val addRequested = rememberSaveable { mutableStateOf(false) }
    // The import button and the import reminder: the transactions open with the import sheet.
    val importRequested = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(action) {
        when (action) {
            QuickAction.AddTransaction -> {
                if (entry?.destination?.hasRoute<TransactionsRoute>() != true) nav.openFinancePage(TransactionsRoute())
                addRequested.value = true
            }
            QuickAction.Dashboard -> nav.openFinancePage(DashboardRoute)
            QuickAction.Budgets -> nav.openFinancePage(BudgetsRoute)
            QuickAction.Subscriptions -> nav.openFinancePage(SubscriptionsRoute)
            QuickAction.Review -> nav.openFinancePage(ReviewRoute())
            // The import reminder: the transactions, with the import open.
            QuickAction.Import -> {
                importRequested.value = true
                if (entry?.destination?.hasRoute<TransactionsRoute>() != true) nav.openFinancePage(TransactionsRoute())
            }
            null -> {}
        }
        quickAction.value = null
    }
    return remember(sharedStatement, statement, addRequested, importRequested) {
        FinanceRequests(sharedStatement, statement, addRequested, importRequested)
    }
}

/** The finance pages, with the Money home, for the app's navigation host. */
fun NavGraphBuilder.financeGraph(
    nav: NavHostController,
    api: FinanceApi,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    contentPadding: PaddingValues,
    requests: FinanceRequests,
    amountsHidden: StateFlow<Boolean>? = null,
    setAmountsHidden: ((Boolean) -> Unit)? = null,
) {
    val padding = contentPadding
    val back: () -> Unit = { nav.popBackStack() }
    val openImport: () -> Unit = {
        requests.importRequested = true
        nav.navigate(TransactionsRoute()) { popUpTo<MoneyHomeRoute>() }
    }
    composable<MoneyHomeRoute> {
        MoneyHomeScreen(
            model = viewModel { DashboardViewModel(api) },
            contentPadding = padding,
            onBack = back,
            open = { route -> nav.navigate(route) },
            openImport = openImport,
        )
    }
    composable<DashboardRoute> {
        val model = viewModel { DashboardViewModel(api) }
        val hidden = amountsHidden?.collectAsState()?.value ?: false
        DashboardScreen(
            amountsHidden = hidden,
            toggleAmounts = setAmountsHidden?.let { { it(!hidden) } },
            model = model,
            account = account,
            onSignOut = onSignOut,
            contentPadding = padding,
            onBack = back,
            openTransactions = { route ->
                // A new list on these criteria, Back returning to the dashboard.
                nav.navigate(route) { popUpTo<DashboardRoute>() }
            },
            openBudgets = { nav.navigate(BudgetsRoute) },
            openRules = { nav.navigate(RulesRoute) },
            openReview = { nav.navigate(ReviewRoute()) },
            openSubscriptions = { nav.navigate(SubscriptionsRoute) },
            openNetWorth = { nav.navigate(NetWorthRoute) },
            openAccounts = { nav.navigate(AccountsRoute) },
            openImport = openImport,
        )
    }
    composable<SubscriptionsRoute> {
        RecurringScreen(
            viewModel { RecurringViewModel(api) },
            padding,
            onBack = back,
            openTransactions = { name -> nav.navigate(TransactionsRoute(description = name)) },
        )
    }
    composable<ReviewRoute> { backStack ->
        val route = backStack.toRoute<ReviewRoute>()
        val model = viewModel { ReviewViewModel(api, route.year?.let { ReviewPeriod(it, route.month) }) }
        ReviewScreen(model, padding, onBack = back, openSubscriptions = { nav.navigate(SubscriptionsRoute) })
    }
    composable<RulesRoute> {
        RulesScreen(viewModel { RulesViewModel(api) }, padding, onBack = back)
    }
    composable<CategoriesRoute> {
        CategoriesScreen(viewModel { CategoriesViewModel(api) }, padding, onBack = back)
    }
    composable<NetWorthRoute> {
        NetWorthScreen(
            viewModel { NetWorthViewModel(api) },
            padding,
            onBack = back,
            openAccounts = { nav.navigate(AccountsRoute) },
        )
    }
    composable<TagsRoute> {
        TagsScreen(
            viewModel { TagsViewModel(api) },
            padding,
            onBack = back,
            openTransactions = { tag -> nav.navigate(TransactionsRoute(tagId = tag.id)) },
        )
    }
    composable<AccountsRoute> {
        AccountsScreen(viewModel { AccountsViewModel(api) }, padding, onBack = back)
    }
    composable<BudgetsRoute> {
        val model = viewModel { BudgetsViewModel(api) }
        BudgetsScreen(model = model, account = account, onSignOut = onSignOut, contentPadding = padding, onBack = back)
    }
    composable<TransactionsRoute> { backStack ->
        val route = backStack.toRoute<TransactionsRoute>()
        val model = viewModel { TransactionsViewModel(api, route) }
        TransactionsScreen(
            model = model,
            account = account,
            onSignOut = onSignOut,
            contentPadding = padding,
            onBack = back,
            sharedFiles = requests.statement,
            onSharedFileTaken = { requests.statementTaken() },
            addRequested = requests.addRequested,
            onAddTaken = { requests.addRequested = false },
            importRequested = requests.importRequested,
            onImportTaken = { requests.importRequested = false },
        )
    }
}
