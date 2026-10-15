package com.guibilar.financeinsights.ui

import androidx.navigation.NavHostController
import com.guibilar.financeinsights.core.api.ApiClient
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.home.itemSubtitle
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.model.SortDirection
import com.guibilar.financeinsights.core.model.TransactionFilter
import com.guibilar.financeinsights.core.model.TransactionSortField
import com.guibilar.financeinsights.core.people.PersonLinkKinds
import com.guibilar.financeinsights.core.tasks.TaskStatus
import com.guibilar.financeinsights.core.tasks.TasksApi
import com.guibilar.financeinsights.documents.DocumentRoute
import com.guibilar.financeinsights.home.ItemRoute
import com.guibilar.financeinsights.people.LinkCandidate
import com.guibilar.financeinsights.people.PeopleHost
import com.guibilar.financeinsights.tasks.openTask

// The app joins the People module (T92) with the other modules here: `:feature:people` and the other feature modules
// do not depend on each other (D14). A linked page opens over the person, so Back returns there.

/** The pages of the things a person links to, and the search of "Add a link" (T94) in their modules. */
class AppPeopleHost(private val nav: NavHostController, private val client: ApiClient) : PeopleHost {
    override val canFindLinks: Boolean get() = true

    override suspend fun linkCandidates(kind: String, text: String): List<LinkCandidate> {
        val q = text.trim().ifEmpty { null }
        return when (kind) {
            PersonLinkKinds.DOCUMENT -> client.api<DocumentsApi>().documents(q = q, pageSize = CANDIDATES).items
                .map { LinkCandidate(it.id, it.title, it.kind.label) }
            PersonLinkKinds.TASK -> client.api<TasksApi>().tasks(status = TaskStatus.Open, q = q, page = 1, pageSize = CANDIDATES).items
                .map { LinkCandidate(it.id, it.title, it.dueOn?.let { day -> "Due ${formatDay(day)}" }.orEmpty()) }
            PersonLinkKinds.HOME_ITEM -> client.api<HomeApi>().items(q = q, pageSize = CANDIDATES).items
                .map { LinkCandidate(it.id, it.name, itemSubtitle(it, withRoom = true)) }
            PersonLinkKinds.TRANSACTION -> client.api<FinanceApi>().transactions(
                TransactionFilter(
                    description = q,
                    sortBy = TransactionSortField.Date,
                    sortDirection = SortDirection.Descending,
                    page = 1,
                    pageSize = CANDIDATES,
                ),
            ).items.map { LinkCandidate(it.id, it.description, "${formatDay(it.date)} · ${Money.format(it.amount)}") }
            else -> emptyList()
        }
    }

    override fun openLink(kind: String, targetId: String) {
        when (kind) {
            PersonLinkKinds.DOCUMENT -> nav.navigate(DocumentRoute(targetId))
            PersonLinkKinds.TASK -> nav.openTask(targetId)
            PersonLinkKinds.HOME_ITEM -> nav.navigate(ItemRoute(targetId))
            PersonLinkKinds.TRANSACTION -> nav.openTransaction(targetId)
        }
    }

    private companion object {
        const val CANDIDATES = 20
    }
}
