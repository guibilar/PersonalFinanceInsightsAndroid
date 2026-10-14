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
import com.guibilar.financeinsights.FinanceDocuments
import com.guibilar.financeinsights.QuickAction
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.logic.ReviewPeriod
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.TransactionCriteria
import com.guibilar.financeinsights.core.today.AlertsApi
import com.guibilar.financeinsights.core.today.MoneyLink
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
    /**
     * One transaction to show (a link to it): the list opens on its day and account, with its row highlighted. It
     * replaces the other criteria. Use [openTransaction] to open it.
     */
    val transactionId: String? = null,
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

/**
 * Opens one transaction from anywhere (Documents, Today, and later Timeline and Search): the Transactions page on its day
 * and account, with its row highlighted for a short time. A deleted or unknown one shows a short message over the
 * normal list. Back returns to the Money home, and then to where the user came from. This is the one way in: other
 * modules get it through the app, not by a dependency on this module.
 */
fun NavHostController.openTransaction(id: String) = openFinancePage(TransactionsRoute(transactionId = id))

private fun NavHostController.hasMoneyHome() = runCatching { getBackStackEntry<MoneyHomeRoute>() }.isSuccess

/**
 * The route of a finance page. [MoneyLink.Import] is the transactions: the caller also asks for the import. The link
 * itself comes from the one link map in `:core:today` (`todayDestination`).
 */
val MoneyLink.route: Any
    get() = when (this) {
        MoneyLink.Home -> MoneyHomeRoute
        MoneyLink.Dashboard -> DashboardRoute
        MoneyLink.Transactions, MoneyLink.Import -> TransactionsRoute()
        MoneyLink.NetWorth -> NetWorthRoute
        MoneyLink.Budgets -> BudgetsRoute
        MoneyLink.Subscriptions -> SubscriptionsRoute
        MoneyLink.Review -> ReviewRoute()
        MoneyLink.Rules -> RulesRoute
        MoneyLink.Categories -> CategoriesRoute
        MoneyLink.Tags -> TagsRoute
        MoneyLink.Accounts -> AccountsRoute
    }

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
    private val importAccountState: MutableState<String?>,
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

    /** With [importRequested]: the account to pick in the import (T63); null picks as before. */
    var importAccount: String?
        get() = importAccountState.value
        internal set(value) {
            importAccountState.value = value
        }

    internal fun statementTaken() {
        sharedStatement.value = null
    }
}

/**
 * Opens what a Money tile or attention item leads to, from outside the Money pages (Today). For [MoneyLink.Import],
 * [importAccount] is the account to pick in the import ("Statement to import", T63).
 */
fun FinanceRequests.open(nav: NavHostController, link: MoneyLink, importAccount: String? = null) {
    if (link == MoneyLink.Import) {
        this.importAccount = importAccount
        importRequested = true
    }
    nav.openFinancePage(link.route)
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
    val importAccount = rememberSaveable { mutableStateOf<String?>(null) }
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
                importAccount.value = null
                importRequested.value = true
                if (entry?.destination?.hasRoute<TransactionsRoute>() != true) nav.openFinancePage(TransactionsRoute())
            }
            null -> {}
        }
        quickAction.value = null
    }
    return remember(sharedStatement, statement, addRequested, importRequested, importAccount) {
        FinanceRequests(sharedStatement, statement, addRequested, importRequested, importAccount)
    }
}

/** The finance pages, with the Money home, for the app's navigation host. */
fun NavGraphBuilder.financeGraph(
    nav: NavHostController,
    api: FinanceApi,
    /** The "Needs attention" items of the Money home and the dashboard. */
    alerts: AlertsApi,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    contentPadding: PaddingValues,
    requests: FinanceRequests,
    amountsHidden: StateFlow<Boolean>? = null,
    setAmountsHidden: ((Boolean) -> Unit)? = null,
    documents: FinanceDocuments? = null,
) {
    val padding = contentPadding
    val back: () -> Unit = { nav.popBackStack() }
    // The import, with an account picked when a "Needs attention" item names one (T63).
    val openAccountImport: (String?) -> Unit = { account ->
        requests.importAccount = account
        requests.importRequested = true
        nav.navigate(TransactionsRoute()) { popUpTo<MoneyHomeRoute>() }
    }
    val openImport: () -> Unit = { openAccountImport(null) }
    composable<MoneyHomeRoute> {
        MoneyHomeScreen(
            model = viewModel { DashboardViewModel(api, alerts) },
            contentPadding = padding,
            onBack = back,
            open = { route -> nav.navigate(route) },
            openImport = openImport,
            openAccountImport = { openAccountImport(it) },
        )
    }
    composable<DashboardRoute> {
        val model = viewModel { DashboardViewModel(api, alerts) }
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
            openAccountImport = { openAccountImport(it) },
            openPage = { route ->
                when (route) {
                    DashboardRoute -> {}
                    // One transaction, or a new list: Back returns to the dashboard.
                    is TransactionsRoute -> nav.navigate(route) { popUpTo<DashboardRoute>() }
                    else -> nav.navigate(route)
                }
            },
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
            importAccount = requests.importAccount,
            onImportTaken = {
                requests.importRequested = false
                requests.importAccount = null
            },
            documents = documents,
        )
    }
}
