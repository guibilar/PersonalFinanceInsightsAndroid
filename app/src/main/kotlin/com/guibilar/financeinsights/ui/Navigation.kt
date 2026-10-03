package com.guibilar.financeinsights.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.guibilar.financeinsights.BuildConfig
import com.guibilar.financeinsights.QuickAction
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.TransactionCriteria
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.AuthState
import com.guibilar.financeinsights.data.Session
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.security.authenticate
import com.guibilar.financeinsights.ui.budgets.BudgetsScreen
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
import com.guibilar.financeinsights.ui.dashboard.DashboardScreen
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.settings.AccountSettingsScreen
import com.guibilar.financeinsights.ui.settings.AccountSettingsViewModel
import com.guibilar.financeinsights.ui.settings.AccountsScreen
import com.guibilar.financeinsights.ui.settings.AccountsViewModel
import com.guibilar.financeinsights.ui.settings.AppSettingsScreen
import com.guibilar.financeinsights.ui.settings.CategoriesScreen
import com.guibilar.financeinsights.ui.settings.CategoriesViewModel
import com.guibilar.financeinsights.ui.settings.MoreScreen
import com.guibilar.financeinsights.ui.settings.RulesScreen
import com.guibilar.financeinsights.ui.settings.RulesViewModel
import com.guibilar.financeinsights.ui.signin.LockScreen
import com.guibilar.financeinsights.ui.signin.SignInScreen
import com.guibilar.financeinsights.ui.signin.UnreachableScreen
import com.guibilar.financeinsights.ui.transactions.PickedFile
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

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
data object AccountSettingsRoute

@Serializable
data object AppSettingsRoute

/** The transactions list, optionally opened on criteria (the dashboard's links) with its "Over time" chart. */
@Serializable
data class TransactionsRoute(
    val categoryId: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val overTime: Boolean = false,
    val interval: BreakdownInterval? = null,
) {
    val criteria get() = TransactionCriteria(categoryId = categoryId, startDate = startDate, endDate = endDate)
}

/** Signed in or not decides what shows; signing out (or the session ending) brings back the sign-in screen. */
@Composable
fun FinanceRoot(
    session: Session,
    sharedStatement: MutableStateFlow<PickedFile?> = MutableStateFlow(null),
    quickAction: MutableStateFlow<QuickAction?> = MutableStateFlow(null),
    lock: AppLock? = null,
    preferences: AppPreferences? = null,
) {
    val state by session.state.collectAsState()
    val locked = lock?.locked?.collectAsState()?.value == true
    val activity = LocalActivity.current as? FragmentActivity
    when (val current = state) {
        AuthState.Checking -> {
            LaunchedEffect(Unit) { session.resume() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is AuthState.Unreachable -> UnreachableScreen(
            server = session.server.orEmpty(),
            message = current.message,
            onRetry = { session.resume() },
            onSignInInstead = session::signInInstead,
        )
        is AuthState.SignedOut -> SignInScreen(
            initialServer = session.server,
            initialUsername = session.lastUsername,
            initialMessage = current.reason,
            signIn = session::signIn,
        )
        // Locked, nothing of the account shows; signed out or unreachable shows none anyway.
        is AuthState.SignedIn -> if (locked && lock != null && activity != null) {
            LockScreen(onUnlock = {
                authenticate(activity, "Unlock Finance Insights") { ok -> if (ok) lock.unlocked() }
            })
        } else {
            MainScreen(
                api = current.api,
                account = AccountInfo(current.username, session.server.orEmpty()),
                onSignOut = { session.signOut() },
                sharedStatement = sharedStatement,
                quickAction = quickAction,
                preferences = preferences,
                lock = lock,
            )
        }
    }
}

/** Who is signed in where, for the account menu. */
data class AccountInfo(val username: String, val server: String)

/** From this width (dp) on, the tabs are a rail at the side. */
private const val RAIL_FROM_WIDTH = 600

private data class Tab(val label: String, val icon: ImageVector, val route: Any, val matches: (androidx.navigation.NavDestination) -> Boolean)

private val tabs = listOf(
    Tab("Dashboard", Icons.Outlined.Dashboard, DashboardRoute) { it.hasRoute<DashboardRoute>() },
    Tab("Transactions", Icons.Outlined.ReceiptLong, TransactionsRoute()) { it.hasRoute<TransactionsRoute>() },
    Tab("Budgets", Icons.Outlined.AccountBalanceWallet, BudgetsRoute) { it.hasRoute<BudgetsRoute>() },
    Tab("More", Icons.Outlined.Menu, MoreRoute) {
        it.hasRoute<MoreRoute>() || it.hasRoute<RulesRoute>() || it.hasRoute<CategoriesRoute>() ||
            it.hasRoute<AccountsRoute>() || it.hasRoute<AccountSettingsRoute>() || it.hasRoute<AppSettingsRoute>()
    },
)

@Composable
fun MainScreen(
    api: FinanceApi,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    sharedStatement: MutableStateFlow<PickedFile?> = MutableStateFlow(null),
    quickAction: MutableStateFlow<QuickAction?> = MutableStateFlow(null),
    preferences: AppPreferences? = null,
    lock: AppLock? = null,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val statement by sharedStatement.collectAsState()
    // A statement shared to the app opens the transactions, where the import takes it.
    LaunchedEffect(statement) {
        if (statement != null && entry?.destination?.hasRoute<TransactionsRoute>() != true) {
            nav.navigate(TransactionsRoute()) { popUpTo<DashboardRoute>() }
        }
    }
    // The launcher shortcut and widget's Add, and a budget alert's tap.
    val action by quickAction.collectAsState()
    var addRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(action) {
        when (action) {
            QuickAction.AddTransaction -> {
                if (entry?.destination?.hasRoute<TransactionsRoute>() != true) nav.navigate(TransactionsRoute()) { popUpTo<DashboardRoute>() }
                addRequested = true
            }
            QuickAction.Budgets -> nav.navigate(BudgetsRoute) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            null -> {}
        }
        quickAction.value = null
    }
    // A rail at the side on a wide screen (a tablet, an unfolded foldable, landscape); the bar below otherwise.
    val rail = LocalConfiguration.current.screenWidthDp >= RAIL_FROM_WIDTH
    fun select(tab: Tab, selected: Boolean) {
        if (!selected) {
            nav.navigate(tab.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else if (entry?.destination?.hasRoute(tab.route::class) == false) {
            // The tab again, from a page inside it (Rules under More): back to the tab's first page.
            nav.popBackStack(tab.route, inclusive = false)
        }
    }
    Scaffold(
        bottomBar = {
            if (!rail) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = entry?.destination?.let(tab.matches) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { select(tab, selected) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize()) {
            if (rail) {
                NavigationRail(Modifier.fillMaxHeight()) {
                    Spacer(Modifier.weight(1f))
                    tabs.forEach { tab ->
                        val selected = entry?.destination?.let(tab.matches) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { select(tab, selected) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            NavHost(nav, startDestination = DashboardRoute, modifier = Modifier.weight(1f)) {
                composable<DashboardRoute> {
                    val model = viewModel { DashboardViewModel(api) }
                    DashboardScreen(
                        model = model,
                        account = account,
                        onSignOut = onSignOut,
                        contentPadding = padding,
                        openTransactions = { route ->
                            // A new list on these criteria, Back returning to the dashboard.
                            nav.navigate(route) { popUpTo<DashboardRoute>() }
                        },
                        openBudgets = {
                            nav.navigate(BudgetsRoute) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable<MoreRoute> {
                    MoreScreen(
                        username = account.username,
                        version = BuildConfig.VERSION_NAME,
                        contentPadding = padding,
                        openRules = { nav.navigate(RulesRoute) },
                        openCategories = { nav.navigate(CategoriesRoute) },
                        openAccounts = { nav.navigate(AccountsRoute) },
                        openAccount = { nav.navigate(AccountSettingsRoute) },
                        openSettings = { nav.navigate(AppSettingsRoute) },
                    )
                }
                composable<AppSettingsRoute> {
                    if (preferences != null) AppSettingsScreen(preferences, lock, padding, onBack = { nav.popBackStack() })
                }
                composable<RulesRoute> {
                    RulesScreen(viewModel { RulesViewModel(api) }, padding, onBack = { nav.popBackStack() })
                }
                composable<CategoriesRoute> {
                    CategoriesScreen(viewModel { CategoriesViewModel(api) }, padding, onBack = { nav.popBackStack() })
                }
                composable<AccountsRoute> {
                    AccountsScreen(viewModel { AccountsViewModel(api) }, padding, onBack = { nav.popBackStack() })
                }
                composable<AccountSettingsRoute> {
                    AccountSettingsScreen(viewModel { AccountSettingsViewModel(api) }, account, onSignOut, padding, onBack = { nav.popBackStack() })
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
                        sharedFile = statement,
                        onSharedFileTaken = { sharedStatement.value = null },
                        addRequested = addRequested,
                        onAddTaken = { addRequested = false },
                    )
                }
            }
        }
    }
}
