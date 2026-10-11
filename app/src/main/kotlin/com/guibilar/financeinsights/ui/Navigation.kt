package com.guibilar.financeinsights.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guibilar.financeinsights.BuildConfig
import com.guibilar.financeinsights.QuickAction
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
import com.guibilar.financeinsights.ui.settings.AccountSettingsScreen
import com.guibilar.financeinsights.ui.settings.AccountSettingsViewModel
import com.guibilar.financeinsights.ui.settings.AppSettingsScreen
import com.guibilar.financeinsights.ui.signin.LockScreen
import com.guibilar.financeinsights.ui.signin.SignInScreen
import com.guibilar.financeinsights.ui.signin.UnreachableScreen
import com.guibilar.financeinsights.ui.transactions.PickedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

// The app's pages. The finance pages and their routes are the finance module's (financeGraph).

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
                    api = current.api,
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

private data class Tab(val label: String, val icon: ImageVector, val route: Any, val matches: (NavDestination) -> Boolean)

// The finance tabs; More also holds the app's own pages (the account and the app's settings).
private val tabs = financeTabs.map { tab ->
    val matches: (NavDestination) -> Boolean = if (tab.route == MoreRoute) {
        { destination -> tab.matches(destination) || destination.hasRoute<AccountSettingsRoute>() || destination.hasRoute<AppSettingsRoute>() }
    } else {
        tab.matches
    }
    Tab(tab.label, tab.icon, tab.route, matches)
}

@Composable
fun MainScreen(
    api: FinanceApi,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    sharedStatement: MutableStateFlow<List<PickedFile>?> = MutableStateFlow(null),
    quickAction: MutableStateFlow<QuickAction?> = MutableStateFlow(null),
    preferences: AppPreferences? = null,
    lock: AppLock? = null,
    financePreferences: FinancePreferences? = null,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    // Shared statements and quick actions open their finance pages.
    val requests = rememberFinanceRequests(nav, sharedStatement, quickAction)
    // A rail at the side on a wide screen (a tablet, an unfolded foldable, landscape); the bar below otherwise.
    val rail = windowWidth() >= MEDIUM_WIDTH
    fun select(tab: Tab, selected: Boolean) {
        if (!selected) {
            nav.openTab(tab.route)
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
                            // Five tabs on a narrow phone: one line, a size smaller, so "Transactions" doesn't wrap.
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), maxLines = 1, softWrap = false) },
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
            NavHost(nav, startDestination = FinanceStartRoute, modifier = Modifier.weight(1f)) {
                financeGraph(
                    nav = nav,
                    api = api,
                    account = account,
                    onSignOut = onSignOut,
                    contentPadding = padding,
                    requests = requests,
                    appVersion = BuildConfig.VERSION_NAME,
                    openAccount = { nav.navigate(AccountSettingsRoute) },
                    openSettings = { nav.navigate(AppSettingsRoute) },
                    amountsHidden = preferences?.hideAmounts,
                    setAmountsHidden = preferences?.let { it::setHideAmounts },
                )
                composable<AppSettingsRoute> {
                    if (preferences != null && financePreferences != null) {
                        AppSettingsScreen(preferences, financePreferences, lock, padding, onBack = { nav.popBackStack() })
                    }
                }
                composable<AccountSettingsRoute> {
                    AccountSettingsScreen(viewModel { AccountSettingsViewModel(api) }, account, onSignOut, padding, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
