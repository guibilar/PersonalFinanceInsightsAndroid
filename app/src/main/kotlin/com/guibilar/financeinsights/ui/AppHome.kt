package com.guibilar.financeinsights.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.navigation.NavHostController
import com.guibilar.financeinsights.FinanceHome
import com.guibilar.financeinsights.core.api.ApiClient
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.people.PeopleApi
import com.guibilar.financeinsights.documents.DocumentItems
import com.guibilar.financeinsights.documents.DocumentPeople
import com.guibilar.financeinsights.documents.DocumentRoute
import com.guibilar.financeinsights.documents.ui.decodeUpright
import com.guibilar.financeinsights.documents.ui.preparePhoto
import com.guibilar.financeinsights.documents.ui.readPicked
import com.guibilar.financeinsights.home.HomeHost
import com.guibilar.financeinsights.home.ItemRoute
import com.guibilar.financeinsights.home.addItemFromPurchase
import com.guibilar.financeinsights.people.PersonRoute
import com.guibilar.financeinsights.tasks.TaskItems
import com.guibilar.financeinsights.tasks.TaskPeople
import com.guibilar.financeinsights.tasks.openTask

// The app joins the Home module (T88) with the other modules here: `:feature:home` and the other feature modules do not
// depend on each other (D14). A page of another module opens over the page that is open, so Back returns there.

/** The other modules' pages and the Documents photo rules (T12), for the Home pages. */
class AppHomeHost(private val nav: NavHostController) : HomeHost {
    override fun openDocument(id: String) {
        nav.navigate(DocumentRoute(id))
    }

    override fun openTransaction(id: String) {
        nav.openTransaction(id)
    }

    override fun openTask(id: String) {
        nav.openTask(id)
    }

    override fun readPhoto(context: Context, uri: Uri, name: String): UploadFile? = readPicked(context, uri, name)?.let(::preparePhoto)

    override fun decodePhoto(bytes: ByteArray, maxSide: Int): Bitmap? = decodeUpright(bytes, maxSide)
}

/** The Home pages for a document's "Items" section. */
class AppDocumentItems(
    private val nav: NavHostController,
    private val client: ApiClient,
    /** False for a read-only user (T60): the section hides "Link to item". */
    override val canWrite: Boolean = true,
) : DocumentItems {
    // Made at the first use, when a document's section loads.
    override val api: HomeApi by lazy { client.api() }

    override fun openItem(id: String) {
        nav.navigate(ItemRoute(id))
    }
}

/** The Home pages for the items of a task. */
class AppTaskItems(private val nav: NavHostController, private val client: ApiClient) : TaskItems {
    override val api: HomeApi by lazy { client.api() }

    override fun openItem(id: String) {
        nav.navigate(ItemRoute(id))
    }
}

/** The People pages for a document's "People" section (T93). */
class AppDocumentPeople(
    private val nav: NavHostController,
    private val client: ApiClient,
    /** False for a read-only user (T60): the section hides "Link a person". */
    override val canWrite: Boolean = true,
) : DocumentPeople {
    override val api: PeopleApi by lazy { client.api() }

    override fun openPerson(id: String) {
        nav.navigate(PersonRoute(id))
    }
}

/** The People pages for the people of a task (T93). */
class AppTaskPeople(private val nav: NavHostController, private val client: ApiClient) : TaskPeople {
    override val api: PeopleApi by lazy { client.api() }

    override fun openPerson(id: String) {
        nav.navigate(PersonRoute(id))
    }
}

/** "Add to inventory" on a transaction in Money: a new item from the purchase. */
class AppFinanceHome(
    private val nav: NavHostController,
    /** False for a read-only user (T60): the action does not show. */
    override val canWrite: Boolean = true,
) : FinanceHome {
    override fun addToInventory(transaction: Transaction) {
        nav.addItemFromPurchase(transaction.id, transaction.description, transaction.amount, transaction.day)
    }
}
