package com.guibilar.financeinsights.documents

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
import com.guibilar.financeinsights.documents.ui.DocumentScreen
import com.guibilar.financeinsights.documents.ui.DocumentSource
import com.guibilar.financeinsights.documents.ui.DocumentViewModel
import com.guibilar.financeinsights.documents.ui.DocumentsListScreen
import com.guibilar.financeinsights.documents.ui.DocumentsListViewModel
import com.guibilar.financeinsights.documents.ui.UploadScreen
import com.guibilar.financeinsights.documents.ui.UploadViewModel
import kotlinx.serialization.Serializable
import java.time.LocalDate

// The Documents module's way in: its routes, how other pages open them (Today, Capture, Modules), and
// documentsGraph, which the app's navigation host calls. Its APIs come from the signed-in client.

/** The Documents module's home: the list. Web route `/documents`. */
@Serializable
data object DocumentsRoute

/** One document. Web route `/documents/<id>`. [notice] shows once, as a message (a link that failed after an upload). */
@Serializable
data class DocumentRoute(val id: String, val notice: String? = null)

/**
 * A new document; [source] (a [DocumentSource] name) opens that picker at once. With [transactionId], the upload links
 * the new document to that transaction, then goes back (a transaction's "Add document" in Money).
 */
@Serializable
data class AddDocumentRoute(val source: String? = null, val transactionId: String? = null)

/** Whether a page is one of the Documents module's: the app's bar shows them under Modules. */
fun NavDestination.isDocumentsPage(): Boolean =
    hasRoute<DocumentsRoute>() || hasRoute<DocumentRoute>() || hasRoute<AddDocumentRoute>()

/**
 * Opens the Documents list from outside its pages (Today, Modules), as a tab opens: over the first page, and Back
 * returns there.
 */
fun NavHostController.openDocuments() {
    navigate(DocumentsRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/** Opens one document from outside (a Today item): over the list, so Back shows the list, then where it came from. */
fun NavHostController.openDocument(id: String) {
    if (runCatching { getBackStackEntry<DocumentsRoute>() }.isFailure) openDocuments()
    navigate(DocumentRoute(id)) { popUpTo<DocumentsRoute>() }
}

/** Opens a new document over the page that is open (Capture): Back returns there. */
fun NavHostController.addDocument(source: DocumentSource? = null) {
    navigate(AddDocumentRoute(source?.name))
}

/**
 * Opens a new document for one transaction, over the page that is open (a transaction in Money). After the upload,
 * the document is linked to the transaction and the page goes back. If the link fails, the new document opens.
 */
fun NavHostController.addDocumentTo(transactionId: String) {
    navigate(AddDocumentRoute(transactionId = transactionId))
}

/**
 * The Documents pages, for the app's navigation host. [canWrite] false (a read-only user) hides every write action.
 * [openTransaction] opens a linked transaction in Money. [tasks] gives a document its "Tasks" section (T84), and
 * [items] its "Items" section (T88).
 */
fun NavGraphBuilder.documentsGraph(
    nav: NavHostController,
    client: ApiClient,
    contentPadding: PaddingValues,
    openTransaction: (String) -> Unit,
    canWrite: Boolean = true,
    today: () -> LocalDate = { LocalDate.now() },
    tasks: DocumentTasks? = null,
    items: DocumentItems? = null,
    people: DocumentPeople? = null,
) {
    composable<DocumentsRoute> {
        DocumentsListScreen(
            model = viewModel { DocumentsListViewModel(client.api(), today) },
            contentPadding = contentPadding,
            open = { document -> nav.navigate(DocumentRoute(document.id)) },
            add = { scan -> nav.addDocument(if (scan) DocumentSource.Scan else null) },
            canWrite = canWrite,
            today = today(),
            onBack = { nav.popBackStack() },
        )
    }
    composable<DocumentRoute> { backStack ->
        val route = backStack.toRoute<DocumentRoute>()
        DocumentScreen(
            model = viewModel { DocumentViewModel(client.api(), client.api(), route.id, notice = route.notice) },
            contentPadding = contentPadding,
            onBack = { nav.popBackStack() },
            openTransaction = openTransaction,
            canWrite = canWrite,
            today = today(),
            tasks = tasks,
            items = items,
            people = people,
        )
    }
    composable<AddDocumentRoute> { backStack ->
        val route = backStack.toRoute<AddDocumentRoute>()
        val model = viewModel { UploadViewModel(client.api(), linkTo = route.transactionId) }
        UploadScreen(
            model = model,
            contentPadding = contentPadding,
            onClose = { nav.popBackStack() },
            uploaded = { id ->
                val state = model.state.value
                if (state.linked) {
                    // Back to the transaction, which shows the new document.
                    nav.popBackStack()
                } else {
                    nav.navigate(DocumentRoute(id, notice = state.linkError)) { popUpTo<AddDocumentRoute> { inclusive = true } }
                }
            },
            start = route.source?.let { name -> DocumentSource.entries.firstOrNull { it.name == name } },
        )
    }
}
