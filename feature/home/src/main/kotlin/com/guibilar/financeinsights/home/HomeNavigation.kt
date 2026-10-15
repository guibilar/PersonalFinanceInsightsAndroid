package com.guibilar.financeinsights.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.guibilar.financeinsights.core.api.ApiClient
import com.guibilar.financeinsights.core.home.ItemDraft
import com.guibilar.financeinsights.core.home.NO_ROOM
import com.guibilar.financeinsights.core.home.homeDay
import com.guibilar.financeinsights.home.ui.HomeScreen
import com.guibilar.financeinsights.home.ui.HomeViewModel
import com.guibilar.financeinsights.home.ui.ItemScreen
import com.guibilar.financeinsights.home.ui.ItemViewModel
import com.guibilar.financeinsights.home.ui.NewItemScreen
import com.guibilar.financeinsights.home.ui.NewItemViewModel
import com.guibilar.financeinsights.home.ui.PhotoSource
import com.guibilar.financeinsights.home.ui.RoomScreen
import com.guibilar.financeinsights.home.ui.RoomViewModel
import kotlinx.serialization.Serializable
import java.time.LocalDate

// The Home module's way in: its routes, how other pages open them (Today, Capture, Modules, Money, a document, a task),
// and homeGraph, which the app's navigation host calls. Its APIs come from the signed-in client.

/** The Home screen. Web route `/home`. */
@Serializable
data object HomeRoute

/** One room; [id] is a room id, or `none` for the items without a room. Web route `/home/rooms/<id>`. */
@Serializable
data class RoomRoute(val id: String)

/** One item. Web route `/home/items/<id>`. [notice] shows once, as a message (a photo that did not go). */
@Serializable
data class ItemRoute(val id: String, val notice: String? = null)

/**
 * A new item. [roomId] puts it in that room; [photo] (a [PhotoSource] name) opens the camera or the photo picker at
 * once. From a transaction ("Add to inventory" in Money): [transactionId] links it, and [name], [price] and [day]
 * fill the form.
 */
@Serializable
data class NewItemRoute(
    val roomId: String? = null,
    val photo: String? = null,
    val transactionId: String? = null,
    val name: String? = null,
    /** The amount, as text ("41.92"): a route argument can't be a Double. */
    val price: String? = null,
    val day: String? = null,
)

/** Whether a page is one of the Home module's: the app's bar shows them under Modules. */
fun NavDestination.isHomePage(): Boolean =
    hasRoute<HomeRoute>() || hasRoute<RoomRoute>() || hasRoute<ItemRoute>() || hasRoute<NewItemRoute>()

/** Opens the Home screen from outside its pages (Today, Modules), as a tab opens: Back returns to the first page. */
fun NavHostController.openHome() {
    navigate(HomeRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/** Opens one item from outside (a warranty alert): over the Home screen, so Back shows it, then where it came from. */
fun NavHostController.openHomeItem(id: String) {
    if (runCatching { getBackStackEntry<HomeRoute>() }.isFailure) openHome()
    navigate(ItemRoute(id)) { popUpTo<HomeRoute>() }
}

/** Opens one room from outside (a Today link): over the Home screen. */
fun NavHostController.openRoom(id: String) {
    if (runCatching { getBackStackEntry<HomeRoute>() }.isFailure) openHome()
    navigate(RoomRoute(id)) { popUpTo<HomeRoute>() }
}

/** Opens a new item over the page that is open (Capture): Back returns there. */
fun NavHostController.addItem(photo: PhotoSource? = null) {
    navigate(NewItemRoute(photo = photo?.name))
}

/**
 * Opens a new item from a transaction ("Add to inventory" in Money), over the page that is open: the description,
 * the amount (as a positive price) and the day fill the form, and the item links to the transaction.
 */
fun NavHostController.addItemFromPurchase(transactionId: String, description: String, amount: Double, day: LocalDate?) {
    navigate(NewItemRoute(transactionId = transactionId, name = description, price = amount.toString(), day = day?.toString()))
}

/**
 * The Home pages, for the app's navigation host. [canWrite] false (a read-only user) hides every write action; the
 * export still works. [host] opens the pages of the other modules and reads photos.
 */
fun NavGraphBuilder.homeGraph(
    nav: NavHostController,
    client: ApiClient,
    contentPadding: PaddingValues,
    host: HomeHost,
    canWrite: Boolean = true,
    today: () -> LocalDate = { LocalDate.now() },
) {
    composable<HomeRoute> {
        HomeScreen(
            model = viewModel { HomeViewModel(client.api(), today) },
            contentPadding = contentPadding,
            openRoom = { id -> nav.navigate(RoomRoute(id)) },
            openItem = { id -> nav.navigate(ItemRoute(id)) },
            openTask = host::openTask,
            addItem = { nav.navigate(NewItemRoute()) },
            canWrite = canWrite,
            onBack = { nav.popBackStack() },
        )
    }
    composable<RoomRoute> { backStack ->
        val route = backStack.toRoute<RoomRoute>()
        RoomScreen(
            model = viewModel { RoomViewModel(client.api(), route.id) },
            contentPadding = contentPadding,
            today = today(),
            openItem = { id -> nav.navigate(ItemRoute(id)) },
            addItem = { nav.navigate(NewItemRoute(roomId = route.id.takeIf { it != NO_ROOM })) },
            onBack = { nav.popBackStack() },
            canWrite = canWrite,
        )
    }
    composable<ItemRoute> { backStack ->
        val route = backStack.toRoute<ItemRoute>()
        ItemScreen(
            model = viewModel {
                ItemViewModel(client.api(), client.api(), client.api(), client.api(), route.id, host::decodePhoto, notice = route.notice)
            },
            host = host,
            contentPadding = contentPadding,
            today = today(),
            onBack = { nav.popBackStack() },
            canWrite = canWrite,
        )
    }
    composable<NewItemRoute> { backStack ->
        val route = backStack.toRoute<NewItemRoute>()
        val initial = if (route.transactionId != null || route.name != null) {
            ItemDraft.fromPurchase(route.name.orEmpty(), route.price?.toDoubleOrNull(), homeDay(route.day), route.roomId)
        } else {
            ItemDraft(roomId = route.roomId)
        }
        val model = viewModel { NewItemViewModel(client.api(), client.api(), initial, route.transactionId) }
        NewItemScreen(
            model = model,
            host = host,
            contentPadding = contentPadding,
            onClose = { nav.popBackStack() },
            created = { id, notice ->
                // The new item takes the place of the form, so Back returns to where "Add item" was.
                nav.navigate(ItemRoute(id, notice)) { popUpTo<NewItemRoute> { inclusive = true } }
            },
            start = route.photo?.let { name -> PhotoSource.entries.firstOrNull { it.name == name } },
        )
    }
}
