package com.guibilar.financeinsights.ui

import androidx.navigation.NavHostController
import com.guibilar.financeinsights.core.api.ApiClient
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
