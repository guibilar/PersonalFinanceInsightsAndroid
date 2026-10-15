package com.guibilar.financeinsights.ui

import androidx.navigation.NavHostController
import com.guibilar.financeinsights.FinanceTasks
import com.guibilar.financeinsights.core.api.ApiClient
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.tasks.NewTask
import com.guibilar.financeinsights.core.tasks.NewTaskLink
import com.guibilar.financeinsights.core.tasks.TasksApi
import com.guibilar.financeinsights.documents.DocumentTasks
import com.guibilar.financeinsights.tasks.openTask

/**
 * The Tasks pages for a document's "Tasks" section (T84). `:feature:documents` does not depend on `:feature:tasks`
 * (D14): the app joins them here. A task opens over the document, so Back returns there.
 */
class AppDocumentTasks(
    private val nav: NavHostController,
    private val client: ApiClient,
    /** False for a read-only user (T60): the section hides "Add task". */
    override val canWrite: Boolean = true,
) : DocumentTasks {
    // Made at the first use, when a document's section loads.
    override val api: TasksApi by lazy { client.api() }

    override fun openTask(id: String) {
        nav.openTask(id)
    }
}

/**
 * "Add task" on a transaction in Money (T86): a task named after the transaction, linked to it, which then opens over
 * the transactions so its details can change.
 */
class AppFinanceTasks(
    private val nav: NavHostController,
    private val client: ApiClient,
    /** False for a read-only user (T60): the action does not show. */
    override val canWrite: Boolean = true,
) : FinanceTasks {
    private val api: TasksApi by lazy { client.api() }

    override suspend fun addTask(transaction: Transaction): String? {
        val title = transaction.description.trim().ifEmpty { "Transaction" }.take(TITLE_LIMIT)
        val result = apiCall { api.create(NewTask(title, links = listOf(NewTaskLink.transaction(transaction.id)))) }
        result.apiError?.let { return it.loadMessage("Could not add the task.") }
        DataChanges.changed()
        nav.openTask(result.getOrThrow().id)
        return null
    }

    private companion object {
        /** The longest task title (Hestia.Api, Tasks). */
        const val TITLE_LIMIT = 200
    }
}
