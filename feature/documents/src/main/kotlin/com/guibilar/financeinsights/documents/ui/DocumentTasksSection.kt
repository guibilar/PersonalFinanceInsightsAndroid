package com.guibilar.financeinsights.documents.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.tasks.TaskLinkKinds
import com.guibilar.financeinsights.core.tasks.TaskSummary
import com.guibilar.financeinsights.core.tasks.TasksApi
import com.guibilar.financeinsights.core.tasks.dueText
import com.guibilar.financeinsights.core.tasks.renewTask
import com.guibilar.financeinsights.core.tasks.repeatText
import com.guibilar.financeinsights.documents.DocumentTasks
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.load as loadState
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The tasks of one document (T84), from the Tasks API of Hestia.Api (`by-link`), and "Add task" for a document with an
 * expiry date: "Renew <title>", due 14 days before the expiry, linked to the document. Compose state, so the section
 * follows it; the calls are suspend functions.
 */
class DocumentTasksModel(private val api: TasksApi, val documentId: String) {
    /** The tasks linked to the document, open first (the server's order: by due date). */
    var tasks by mutableStateOf<LoadState<List<TaskSummary>>>(LoadState.Loading)
        private set

    /** A task is being added. */
    var busy by mutableStateOf(false)
        private set

    /** Why the last add failed. */
    var error by mutableStateOf<String?>(null)
        private set

    /** The server refused a write: this user can only read tasks. */
    var readOnly by mutableStateOf(false)
        private set

    suspend fun load() {
        val loaded = loadState(LOAD_FAILED) { api.byLink(TaskLinkKinds.DOCUMENT, documentId).sortedBy { it.isDone } }
        // A failed reload keeps the list on screen.
        if (loaded is LoadState.Failed && tasks is LoadState.Loaded) return
        tasks = loaded
    }

    /** "Retry" after a failed load. */
    suspend fun retry() {
        tasks = LoadState.Loading
        load()
    }

    /** Adds "Renew <title>" for [document]; the id of the new task, or null when it failed (see [error]). */
    suspend fun addRenewTask(document: Document, today: LocalDate): String? {
        val task = renewTask(document.id, document.title, document.expiresOn, today) ?: return null
        if (busy) return null
        busy = true
        error = null
        val result = apiCall { api.create(task) }
        busy = false
        val failure = result.apiError
        if (failure != null) {
            if (failure is ApiError.Http && failure.status == 403) {
                readOnly = true
            } else {
                error = failure.validationOr(failure.loadMessage("Could not add the task."))
            }
            return null
        }
        DataChanges.changed()
        load()
        return result.getOrThrow().id
    }

    companion object {
        const val LOAD_FAILED = "Could not load the tasks."
    }
}

/**
 * The "Tasks" section of a document: its tasks (a tap opens one in Tasks), and "Add task" on a document with an expiry
 * date. [DocumentTasks.canWrite] false hides "Add task"; so does a write that the server refused with 403.
 */
@Composable
fun DocumentTasksSection(document: Document, tasks: DocumentTasks, today: LocalDate, canWrite: Boolean) {
    val model = remember(document.id, tasks) { DocumentTasksModel(tasks.api, document.id) }
    LaunchedEffect(model) { model.load() }
    val scope = rememberCoroutineScope()
    DocumentTasksContent(
        tasks = model.tasks,
        today = today,
        canAdd = canWrite && tasks.canWrite && !model.readOnly && document.expiresOn != null,
        busy = model.busy,
        readOnly = model.readOnly,
        error = model.error,
        renewTitle = "Renew ${document.title}",
        open = { tasks.openTask(it.id) },
        add = { scope.launch { model.addRenewTask(document, today)?.let(tasks::openTask) } },
        retry = { scope.launch { model.retry() } },
    )
}

/** What [DocumentTasksSection] shows, without the API: for the screenshots. */
@Composable
fun DocumentTasksContent(
    tasks: LoadState<List<TaskSummary>>,
    today: LocalDate,
    canAdd: Boolean,
    open: (TaskSummary) -> Unit,
    add: () -> Unit,
    retry: () -> Unit,
    busy: Boolean = false,
    readOnly: Boolean = false,
    error: String? = null,
    renewTitle: String = "",
) {
    SectionCard("Tasks", subtitle = "What to do about this document") {
        when (tasks) {
            LoadState.Loading -> Text("Loading the tasks…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LoadState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tasks.message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = retry) { Text("Retry") }
            }
            is LoadState.Loaded -> if (tasks.value.isEmpty()) {
                Text("No tasks for this document.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                tasks.value.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider()
                    TaskLine(task, today) { open(task) }
                }
            }
        }
        if (canAdd) {
            OutlinedButton(onClick = add, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add task")
            }
            Text(
                "Adds “$renewTitle”, due 14 days before the expiry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (readOnly) {
            Text("You can only read tasks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun TaskLine(task: TaskSummary, today: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (task.isDone) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            if (task.isDone) "Done" else "Open",
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                if (task.isDone) task.doneAt?.let { "Done ${formatDay(it)}" } else dueText(task.dueOn, today),
                task.repeat?.let(::repeatText),
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
