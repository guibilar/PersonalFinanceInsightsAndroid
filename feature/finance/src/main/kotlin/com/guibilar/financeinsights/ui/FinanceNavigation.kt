package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ReceiptLong
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.guibilar.financeinsights.ui.settings.AccountsScreen
import com.guibilar.financeinsights.ui.settings.AccountsViewModel
import com.guibilar.financeinsights.ui.settings.CategoriesScreen
import com.guibilar.financeinsights.ui.settings.CategoriesViewModel
import com.guibilar.financeinsights.ui.settings.MoreScreen
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

// The finance module's way in: its routes and tabs, what it takes from outside the screens (shared statements, quick
// actions), and financeGraph, which the app's navigation host calls. The app's own pages stay in the app.

@Serializable
data object DashboardRoute

@Serializable
data object BudgetsRoute

@Serializable
data object MoreRoute

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

/** Where the finance screens start: the first tab's page. */
val FinanceStartRoute: Any = DashboardRoute

/** A tab of the finance screens: [matches] says which pages belong to it. */
data class FinanceTab(val label: String, val icon: ImageVector, val route: Any, val matches: (NavDestination) -> Boolean)

/** The finance tabs, in order. More also holds the app's own pages, which the app adds to its [FinanceTab.matches]. */
val financeTabs = listOf(
    FinanceTab("Home", Icons.Outlined.Dashboard, DashboardRoute) { it.hasRoute<DashboardRoute>() },
    FinanceTab("Transactions", Icons.Outlined.ReceiptLong, TransactionsRoute()) { it.hasRoute<TransactionsRoute>() },
    FinanceTab("Budgets", Icons.Outlined.AccountBalanceWallet, BudgetsRoute) { it.hasRoute<BudgetsRoute>() },
    FinanceTab("Net worth", Icons.AutoMirrored.Outlined.ShowChart, NetWorthRoute) { it.hasRoute<NetWorthRoute>() },
    FinanceTab("More", Icons.Outlined.Menu, MoreRoute) {
        it.hasRoute<MoreRoute>() || it.hasRoute<RulesRoute>() || it.hasRoute<CategoriesRoute>() ||
            it.hasRoute<AccountsRoute>() || it.hasRoute<SubscriptionsRoute>() || it.hasRoute<ReviewRoute>() ||
            it.hasRoute<TagsRoute>()
    },
)

/** Opens a tab's page as the tab bar does: each tab keeps its own back stack. */
fun NavHostController.openTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
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
            nav.navigate(TransactionsRoute()) { popUpTo<DashboardRoute>() }
        }
    }
    // The launcher shortcut and widget's Add, and a budget alert's tap.
    val action by quickAction.collectAsState()
    val addRequested = rememberSaveable { mutableStateOf(false) }
    // The dashboard's import button and the import reminder: the transactions open with the import sheet.
    val importRequested = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(action) {
        when (action) {
            QuickAction.AddTransaction -> {
                if (entry?.destination?.hasRoute<TransactionsRoute>() != true) nav.navigate(TransactionsRoute()) { popUpTo<DashboardRoute>() }
                addRequested.value = true
            }
            QuickAction.Budgets -> nav.openTab(BudgetsRoute)
            // From a notification: the page, Back returning to the dashboard.
            QuickAction.Subscriptions -> nav.navigate(SubscriptionsRoute) { popUpTo<DashboardRoute>() }
            QuickAction.Review -> nav.navigate(ReviewRoute()) { popUpTo<DashboardRoute>() }
            // The import reminder: the transactions, with the import open.
            QuickAction.Import -> {
                importRequested.value = true
                if (entry?.destination?.hasRoute<TransactionsRoute>() != true) nav.navigate(TransactionsRoute()) { popUpTo<DashboardRoute>() }
            }
            null -> {}
        }
        quickAction.value = null
    }
    return remember(sharedStatement, statement, addRequested, importRequested) {
        FinanceRequests(sharedStatement, statement, addRequested, importRequested)
    }
}

/**
 * The finance pages, for the app's navigation host. [openAccount] and [openSettings] open the app's own pages from
 * More; [appVersion] shows there. Without [amountsHidden], the dashboard has no button to hide the amounts.
 */
fun NavGraphBuilder.financeGraph(
    nav: NavHostController,
    api: FinanceApi,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    contentPadding: PaddingValues,
    requests: FinanceRequests,
    appVersion: String,
    openAccount: () -> Unit,
    openSettings: () -> Unit,
    amountsHidden: StateFlow<Boolean>? = null,
    setAmountsHidden: ((Boolean) -> Unit)? = null,
) {
    val padding = contentPadding
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
            openTransactions = { route ->
                // A new list on these criteria, Back returning to the dashboard.
                nav.navigate(route) { popUpTo<DashboardRoute>() }
            },
            openBudgets = { nav.openTab(BudgetsRoute) },
            openRules = { nav.navigate(RulesRoute) },
            openReview = { nav.navigate(ReviewRoute()) },
            openSubscriptions = { nav.navigate(SubscriptionsRoute) },
            openNetWorth = { nav.openTab(NetWorthRoute) },
            openAccounts = { nav.navigate(AccountsRoute) },
            openImport = {
                requests.importRequested = true
                nav.navigate(TransactionsRoute()) { popUpTo<DashboardRoute>() }
            },
        )
    }
    composable<MoreRoute> {
        MoreScreen(
            username = account.username,
            version = appVersion,
            contentPadding = padding,
            openRules = { nav.navigate(RulesRoute) },
            openCategories = { nav.navigate(CategoriesRoute) },
            openTags = { nav.navigate(TagsRoute) },
            openAccounts = { nav.navigate(AccountsRoute) },
            openAccount = openAccount,
            openSettings = openSettings,
            openSubscriptions = { nav.navigate(SubscriptionsRoute) },
            openReview = { nav.navigate(ReviewRoute()) },
        )
    }
    composable<SubscriptionsRoute> {
        RecurringScreen(
            viewModel { RecurringViewModel(api) },
            padding,
            onBack = { nav.popBackStack() },
            openTransactions = { name -> nav.navigate(TransactionsRoute(description = name)) },
        )
    }
    composable<ReviewRoute> { backStack ->
        val route = backStack.toRoute<ReviewRoute>()
        val model = viewModel { ReviewViewModel(api, route.year?.let { ReviewPeriod(it, route.month) }) }
        ReviewScreen(model, padding, onBack = { nav.popBackStack() }, openSubscriptions = { nav.navigate(SubscriptionsRoute) })
    }
    composable<RulesRoute> {
        RulesScreen(viewModel { RulesViewModel(api) }, padding, onBack = { nav.popBackStack() })
    }
    composable<CategoriesRoute> {
        CategoriesScreen(viewModel { CategoriesViewModel(api) }, padding, onBack = { nav.popBackStack() })
    }
    composable<NetWorthRoute> {
        NetWorthScreen(
            viewModel { NetWorthViewModel(api) },
            padding,
            // A tab: nothing to go back to.
            onBack = null,
            openAccounts = { nav.navigate(AccountsRoute) },
        )
    }
    composable<TagsRoute> {
        TagsScreen(
            viewModel { TagsViewModel(api) },
            padding,
            onBack = { nav.popBackStack() },
            openTransactions = { tag -> nav.navigate(TransactionsRoute(tagId = tag.id)) },
        )
    }
    composable<AccountsRoute> {
        AccountsScreen(viewModel { AccountsViewModel(api) }, padding, onBack = { nav.popBackStack() })
    }
    composable<BudgetsRoute> {
        val model = viewModel { BudgetsViewModel(api) }
        BudgetsScreen(model = model, account = account, onSignOut = onSignOut, contentPadding = padding)
    }
    composable<TransactionsRoute> { backStack ->
        val route = backStack.toRoute<TransactionsRoute>()
        val model = viewModel { TransactionsViewModel(api, route) }
        TransactionsScreen(
            model = model,
            account = account,
            onSignOut = onSignOut,
            contentPadding = padding,
            sharedFiles = requests.statement,
            onSharedFileTaken = { requests.statementTaken() },
            addRequested = requests.addRequested,
            onAddTaken = { requests.addRequested = false },
            importRequested = requests.importRequested,
            onImportTaken = { requests.importRequested = false },
        )
    }
}
