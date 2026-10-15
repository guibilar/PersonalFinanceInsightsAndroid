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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.guibilar.financeinsights.core.today.AlertsApi
import com.guibilar.financeinsights.core.today.TodayApi
import com.guibilar.financeinsights.core.today.TodayDestination
import com.guibilar.financeinsights.core.today.todayDestination
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.AuthState
import com.guibilar.financeinsights.data.FinancePreferences
import com.guibilar.financeinsights.data.Session
import com.guibilar.financeinsights.documents.DocumentRoute
import com.guibilar.financeinsights.documents.DocumentsRoute
import com.guibilar.financeinsights.documents.addDocument
import com.guibilar.financeinsights.documents.documentsGraph
import com.guibilar.financeinsights.documents.isDocumentsPage
import com.guibilar.financeinsights.documents.openDocument
import com.guibilar.financeinsights.documents.openDocuments
import com.guibilar.financeinsights.documents.ui.DocumentSource
import com.guibilar.financeinsights.home.HomeRoute
import com.guibilar.financeinsights.home.addItem
import com.guibilar.financeinsights.home.homeGraph
import com.guibilar.financeinsights.home.isHomePage
import com.guibilar.financeinsights.home.openHome
import com.guibilar.financeinsights.home.openHomeItem
import com.guibilar.financeinsights.home.openRoom
import com.guibilar.financeinsights.people.PeopleRoute
import com.guibilar.financeinsights.people.addPerson
import com.guibilar.financeinsights.people.isPeoplePage
import com.guibilar.financeinsights.people.openPeople
import com.guibilar.financeinsights.people.openPerson
import com.guibilar.financeinsights.people.peopleGraph
import com.guibilar.financeinsights.notifications.AlertNotificationSettings
import com.guibilar.financeinsights.notifications.AlertNotifications
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.tasks.TasksRoute
import com.guibilar.financeinsights.tasks.addTask
import com.guibilar.financeinsights.tasks.isTasksPage
import com.guibilar.financeinsights.tasks.openTask
import com.guibilar.financeinsights.tasks.openTasks
import com.guibilar.financeinsights.tasks.tasksGraph
import com.guibilar.financeinsights.security.authenticate
import com.guibilar.financeinsights.security.canAuthenticate
import com.guibilar.financeinsights.ui.common.MEDIUM_WIDTH
import com.guibilar.financeinsights.ui.common.windowWidth
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
import com.guibilar.financeinsights.ui.today.TodayViewModel
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
    /** The link of a tapped alert notification ("" for Today), until it is opened (T68). */
    alertLink: MutableStateFlow<String?> = MutableStateFlow(null),
    alertSettings: AlertNotificationSettings? = null,
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
                    canWrite = current.canWrite,
                    account = AccountInfo(current.username, session.server.orEmpty()),
                    onSignOut = { session.signOut() },
                    sharedStatement = sharedStatement,
                    quickAction = quickAction,
                    preferences = preferences,
                    financePreferences = financePreferences,
                    lock = lock,
                    alertLink = alertLink,
                    alertSettings = alertSettings,
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

// The Money, Documents, Tasks, Home and People pages, the app's settings and the account open from Modules, so they show under it.
private val topLevel = listOf(
    TopLevel("Today", Icons.Outlined.WbSunny, TodayRoute, { it.hasRoute<TodayRoute>() }),
    TopLevel("Timeline", Icons.Outlined.Timeline, TimelineRoute, { it.hasRoute<TimelineRoute>() }),
    TopLevel("Capture", Icons.Outlined.Add, CaptureRoute, { it.hasRoute<CaptureRoute>() }, action = true),
    TopLevel("Modules", Icons.Outlined.GridView, ModulesRoute, {
        it.hasRoute<ModulesRoute>() || it.isFinancePage() || it.isDocumentsPage() || it.isTasksPage() ||
            it.isHomePage() || it.isPeoplePage() || it.hasRoute<AppSettingsRoute>() || it.hasRoute<AccountSettingsRoute>()
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
    /** False for a read-only user (T60): Capture, the Documents, Tasks and Home pages hide or disable their write actions. */
    canWrite: Boolean = true,
    alertLink: MutableStateFlow<String?> = MutableStateFlow(null),
    alertSettings: AlertNotificationSettings? = null,
) {
    // Each module makes its API from the signed-in client (the client keeps it, so it is the same object every time).
    val api: FinanceApi = client.api()
    val todayApi: TodayApi = client.api()
    val alertsApi: AlertsApi = client.api()
    val nav = rememberNavController()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    // Shared statements and quick actions (shortcut, widget, notifications, Capture) open their finance pages.
    val requests = rememberFinanceRequests(nav, sharedStatement, quickAction)
    // A Today card or an alert notification opens its page here, through the one link map (`todayDestination`).
    fun openDestination(destination: TodayDestination) {
        when (destination) {
            is TodayDestination.Money -> requests.open(nav, destination.link, destination.importAccount)
            is TodayDestination.Transaction -> nav.openTransaction(destination.id)
            TodayDestination.Documents -> nav.openDocuments()
            is TodayDestination.Document -> nav.openDocument(destination.id)
            is TodayDestination.Tasks -> nav.openTasks(destination.overdueOnly)
            is TodayDestination.Task -> nav.openTask(destination.id)
            TodayDestination.Home -> nav.openHome()
            is TodayDestination.Room -> nav.openRoom(destination.id)
            is TodayDestination.Item -> nav.openHomeItem(destination.id)
            TodayDestination.People -> nav.openPeople()
            is TodayDestination.Person -> nav.openPerson(destination.id)
        }
    }
    // A tapped alert notification (T68): its page, or Today for an alert with no link (and for the summary).
    val pendingAlert by alertLink.collectAsState()
    LaunchedEffect(pendingAlert) {
        val link = pendingAlert ?: return@LaunchedEffect
        val destination = todayDestination(link)
        if (destination == null) nav.openTab(TodayRoute) else openDestination(destination)
        alertLink.value = null
    }
    // A transaction's documents in Money: the app joins the two feature modules here (D14).
    val financeDocuments = remember(nav, client, canWrite) { AppFinanceDocuments(nav, client, canWrite) }
    // A document's tasks: the same, for the Documents and Tasks modules (T84).
    val documentTasks = remember(nav, client, canWrite) { AppDocumentTasks(nav, client, canWrite) }
    // The Home module and the others (T88): a document's and a task's items, "Add to inventory" in Money, and what the
    // Home pages open and how they read photos.
    val documentItems = remember(nav, client, canWrite) { AppDocumentItems(nav, client, canWrite) }
    val taskItems = remember(nav, client) { AppTaskItems(nav, client) }
    val documentPeople = remember(nav, client, canWrite) { AppDocumentPeople(nav, client, canWrite) }
    val taskPeople = remember(nav, client) { AppTaskPeople(nav, client) }
    val financeHome = remember(nav, canWrite) { AppFinanceHome(nav, canWrite) }
    val homeHost = remember(nav) { AppHomeHost(nav) }
    // The People module (T92): its links open the pages of the other modules.
    val peopleHost = remember(nav, client) { AppPeopleHost(nav, client) }
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
            // T75: the Money pages hide their write actions for a read-only user.
            CompositionLocalProvider(LocalFinanceCanWrite provides canWrite) {
                NavHost(nav, startDestination = TodayRoute, modifier = Modifier.weight(1f)) {
                    composable<TodayRoute> {
                        TodayScreen(
                            model = viewModel { TodayViewModel(todayApi) },
                            modules = MODULES,
                            contentPadding = padding,
                            open = { openDestination(it) },
                            onRefresh = { alertSettings?.let { AlertNotifications.pollSoon(context, it) } },
                            openModule = { module ->
                                when (module.route) {
                                    MoneyHomeRoute -> nav.openMoneyHome()
                                    DocumentsRoute -> nav.openDocuments()
                                    is TasksRoute -> nav.openTasks()
                                    HomeRoute -> nav.openHome()
                                    PeopleRoute -> nav.openPeople()
                                    else -> module.route?.let { nav.navigate(it) }
                                }
                            },
                        )
                    }
                    composable<TimelineRoute> {
                        PlaceholderScreen("Timeline", Icons.Outlined.Timeline, "Everything that happened, day by day, from all modules.", padding)
                    }
                    composable<CaptureRoute> {
                        CaptureScreen(
                            padding,
                            canWrite = canWrite,
                            onExpense = { quickAction.value = QuickAction.AddTransaction },
                            onScan = { nav.addDocument(DocumentSource.Scan) },
                            onPhoto = { nav.addDocument(DocumentSource.Photos) },
                            onTask = { nav.addTask() },
                            onItem = { nav.addItem() },
                            onPerson = { nav.addPerson() },
                        )
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
                        alerts = alertsApi,
                        account = account,
                        onSignOut = onSignOut,
                        contentPadding = padding,
                        requests = requests,
                        amountsHidden = preferences?.hideAmounts,
                        setAmountsHidden = preferences?.let { it::setHideAmounts },
                        documents = financeDocuments,
                        home = financeHome,
                    )
                    documentsGraph(
                        nav = nav,
                        client = client,
                        contentPadding = padding,
                        openTransaction = { id -> nav.openTransaction(id) },
                        canWrite = canWrite,
                        tasks = documentTasks,
                        items = documentItems,
                        people = documentPeople,
                    )
                    tasksGraph(
                        nav = nav,
                        client = client,
                        contentPadding = padding,
                        // A linked document opens over the tasks, so Back returns to the task.
                        openDocument = { id -> nav.navigate(DocumentRoute(id)) },
                        openTransaction = { id -> nav.openTransaction(id) },
                        canWrite = canWrite,
                        items = taskItems,
                        people = taskPeople,
                    )
                    homeGraph(
                        nav = nav,
                        client = client,
                        contentPadding = padding,
                        host = homeHost,
                        canWrite = canWrite,
                    )
                    peopleGraph(
                        nav = nav,
                        client = client,
                        contentPadding = padding,
                        host = peopleHost,
                        canWrite = canWrite,
                    )
                    composable<AppSettingsRoute> {
                        if (preferences != null && financePreferences != null) {
                            AppSettingsScreen(
                                preferences, financePreferences, lock, padding, onBack = { nav.popBackStack() },
                                alertSettings = alertSettings,
                            )
                        }
                    }
                    composable<AccountSettingsRoute> {
                        AccountSettingsScreen(viewModel { AccountSettingsViewModel(client.auth) }, account, onSignOut, padding, onBack = { nav.popBackStack() })
                    }
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
