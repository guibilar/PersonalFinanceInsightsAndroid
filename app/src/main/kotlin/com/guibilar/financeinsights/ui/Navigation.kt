package com.guibilar.financeinsights.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guibilar.financeinsights.BuildConfig
import com.guibilar.financeinsights.QuickAction
import com.guibilar.financeinsights.core.api.ApiClient
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.AuthState
import com.guibilar.financeinsights.data.FinancePreferences
import com.guibilar.financeinsights.data.Session
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.security.authenticate
import com.guibilar.financeinsights.security.canAuthenticate
import com.guibilar.financeinsights.ui.common.MEDIUM_WIDTH
import com.guibilar.financeinsights.ui.common.windowWidth
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.modules.MODULES
import com.guibilar.financeinsights.ui.modules.ModulesScreen
import com.guibilar.financeinsights.ui.settings.AccountSettingsScreen
import com.guibilar.financeinsights.ui.settings.AccountSettingsViewModel
import com.guibilar.financeinsights.ui.settings.AppSettingsScreen
import com.guibilar.financeinsights.ui.shell.CaptureScreen
import com.guibilar.financeinsights.ui.shell.PlaceholderScreen
import com.guibilar.financeinsights.ui.signin.LockScreen
import com.guibilar.financeinsights.ui.signin.SignInScreen
import com.guibilar.financeinsights.ui.signin.UnreachableScreen
import com.guibilar.financeinsights.ui.today.TodayScreen
import com.guibilar.financeinsights.ui.transactions.PickedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

// The app's pages: the five top-level pages (Today, Timeline, Capture, Modules, Search), the app's settings and the
// account. The finance pages and their routes are the finance module's (financeGraph), under Modules.

@Serializable
data object TodayRoute

@Serializable
data object TimelineRoute

@Serializable
data object CaptureRoute

@Serializable
data object ModulesRoute

@Serializable
data object SearchRoute

@Serializable
data object AccountSettingsRoute

@Serializable
data object AppSettingsRoute

/** Signed in or not decides what shows; signing out (or the session ending) brings back the sign-in screen. */
@Composable
fun FinanceRoot(
    session: Session,
    sharedStatement: MutableStateFlow<List<PickedFile>?> = MutableStateFlow(null),
    quickAction: MutableStateFlow<QuickAction?> = MutableStateFlow(null),
    lock: AppLock? = null,
    preferences: AppPreferences? = null,
    financePreferences: FinancePreferences? = null,
) {
    val state by session.state.collectAsState()
    val locked = lock?.locked?.collectAsState()?.value == true
    val activity = LocalActivity.current as? FragmentActivity
    // The signed-in screens' saved state (tab, back stack, scroll, sheets and what was typed) is kept while the app
    // is locked, so unlocking returns to the same place; it is dropped on signing out.
    val saved = rememberSaveableStateHolder()
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
        is AuthState.SignedOut -> {
            LaunchedEffect(Unit) { saved.removeState(SIGNED_IN_KEY) }
            SignInScreen(
                initialServer = session.server,
                initialUsername = session.lastUsername,
                initialMessage = current.reason,
                signIn = session::signIn,
            )
        }
        // Locked, nothing of the account shows; signed out or unreachable shows none anyway.
        // Without an activity that can ask for a fingerprint, it stays locked rather than showing the account.
        is AuthState.SignedIn -> if (locked && lock != null) {
            LockScreen(onUnlock = {
                if (activity == null) return@LockScreen
                if (canAuthenticate(activity)) {
                    authenticate(activity, "Unlock Finance Insights") { ok -> if (ok) lock.unlocked() }
                } else {
                    // The phone's screen lock was removed: nothing can confirm the owner, and the prompt would fail
                    // for good. The app lock goes off rather than locking its owner out.
                    preferences?.setLock(false)
                    lock.enabledChanged(false)
                }
            })
        } else {
            saved.SaveableStateProvider(SIGNED_IN_KEY) {
                MainScreen(
                    client = current.client,
                    account = AccountInfo(current.username, session.server.orEmpty()),
                    onSignOut = { session.signOut() },
                    sharedStatement = sharedStatement,
                    quickAction = quickAction,
                    preferences = preferences,
                    financePreferences = financePreferences,
                    lock = lock,
                )
            }
        }
    }
}

private const val SIGNED_IN_KEY = "signed-in"

/** A top-level page in the bar (or the rail): [matches] says which pages belong to it. */
private data class TopLevel(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val matches: (NavDestination) -> Boolean,
    /** Capture: the round action in the middle. */
    val action: Boolean = false,
)

// The Money pages, the app's settings and the account open from Modules, so they show under it.
private val topLevel = listOf(
    TopLevel("Today", Icons.Outlined.WbSunny, TodayRoute, { it.hasRoute<TodayRoute>() }),
    TopLevel("Timeline", Icons.Outlined.Timeline, TimelineRoute, { it.hasRoute<TimelineRoute>() }),
    TopLevel("Capture", Icons.Outlined.Add, CaptureRoute, { it.hasRoute<CaptureRoute>() }, action = true),
    TopLevel("Modules", Icons.Outlined.GridView, ModulesRoute, {
        it.hasRoute<ModulesRoute>() || it.isFinancePage() || it.hasRoute<AppSettingsRoute>() || it.hasRoute<AccountSettingsRoute>()
    }),
    TopLevel("Search", Icons.Outlined.Search, SearchRoute, { it.hasRoute<SearchRoute>() }),
)

/** Opens a top-level page as the bar does: each keeps its own back stack, and Back from it returns to Today. */
private fun NavHostController.openTab(route: Any, restore: Boolean = true) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = restore
    }
}

@Composable
fun MainScreen(
    client: ApiClient,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    sharedStatement: MutableStateFlow<List<PickedFile>?> = MutableStateFlow(null),
    quickAction: MutableStateFlow<QuickAction?> = MutableStateFlow(null),
    preferences: AppPreferences? = null,
    lock: AppLock? = null,
    financePreferences: FinancePreferences? = null,
) {
    // Each module makes its API from the signed-in client (the client keeps it, so it is the same object every time).
    val api: FinanceApi = client.api()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    // Shared statements and quick actions (shortcut, widget, notifications, Capture) open their finance pages.
    val requests = rememberFinanceRequests(nav, sharedStatement, quickAction)
    // A rail at the side on a wide screen (a tablet, an unfolded foldable, landscape); the bar below otherwise.
    val rail = windowWidth() >= MEDIUM_WIDTH
    fun select(item: TopLevel, selected: Boolean) {
        if (!selected) {
            nav.openTab(item.route)
        } else if (entry?.destination?.hasRoute(item.route::class) == false) {
            // The item again, from a page under it: back to its first page. A Money page opened from Today has no
            // Modules page under it; the Modules page then opens fresh.
            if (!nav.popBackStack(item.route, inclusive = false)) nav.openTab(item.route, restore = false)
        }
    }
    Scaffold(
        bottomBar = {
            if (!rail) {
                NavigationBar {
                    topLevel.forEach { item ->
                        val selected = entry?.destination?.let(item.matches) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { select(item, selected) },
                            icon = { TopLevelIcon(item) },
                            label = { Text(item.label, maxLines = 1, softWrap = false) },
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
                    topLevel.forEach { item ->
                        val selected = entry?.destination?.let(item.matches) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { select(item, selected) },
                            icon = { TopLevelIcon(item) },
                            label = { Text(item.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            NavHost(nav, startDestination = TodayRoute, modifier = Modifier.weight(1f)) {
                composable<TodayRoute> {
                    TodayScreen(
                        model = viewModel { DashboardViewModel(api) },
                        modules = MODULES,
                        contentPadding = padding,
                        open = { link -> requests.open(nav, link) },
                        openMoney = { nav.openMoneyHome() },
                    )
                }
                composable<TimelineRoute> {
                    PlaceholderScreen("Timeline", Icons.Outlined.Timeline, "Everything that happened, day by day, from all modules.", padding)
                }
                composable<CaptureRoute> {
                    CaptureScreen(padding, onExpense = { quickAction.value = QuickAction.AddTransaction })
                }
                composable<ModulesRoute> {
                    ModulesScreen(
                        modules = MODULES,
                        account = account,
                        version = BuildConfig.VERSION_NAME,
                        contentPadding = padding,
                        open = { module -> module.route?.let { nav.navigate(it) } },
                        openSettings = { nav.navigate(AppSettingsRoute) },
                        openAccount = { nav.navigate(AccountSettingsRoute) },
                    )
                }
                composable<SearchRoute> {
                    PlaceholderScreen("Search", Icons.Outlined.Search, "One search across all modules.", padding)
                }
                financeGraph(
                    nav = nav,
                    api = api,
                    account = account,
                    onSignOut = onSignOut,
                    contentPadding = padding,
                    requests = requests,
                    amountsHidden = preferences?.hideAmounts,
                    setAmountsHidden = preferences?.let { it::setHideAmounts },
                )
                composable<AppSettingsRoute> {
                    if (preferences != null && financePreferences != null) {
                        AppSettingsScreen(preferences, financePreferences, lock, padding, onBack = { nav.popBackStack() })
                    }
                }
                composable<AccountSettingsRoute> {
                    AccountSettingsScreen(viewModel { AccountSettingsViewModel(client.auth) }, account, onSignOut, padding, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/** An item's icon; Capture's sits in a round, filled button, as in the design. */
@Composable
private fun TopLevelIcon(item: TopLevel) {
    if (item.action) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    } else {
        Icon(item.icon, null)
    }
}
