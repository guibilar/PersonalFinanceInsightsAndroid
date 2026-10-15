package com.guibilar.financeinsights.inbox

import com.guibilar.financeinsights.core.journal.JournalApi
import com.guibilar.financeinsights.core.journal.JournalText
import com.guibilar.financeinsights.core.tasks.NewTask
import com.guibilar.financeinsights.core.tasks.TasksApi

/** Where a note can go: the Inbox (to sort later), a task, or today's journal entry. */
enum class NoteTarget(val label: String) { Inbox("Inbox"), Task("Task"), Journal("Journal") }

/** The longest task title (HestiaBackend, Tasks module). */
private const val TASK_TITLE = 200

/** A note as a task: the first line is the title (at most 200 characters), the rest the notes. */
fun taskFromNote(text: String): NewTask {
    val lines = text.trim().lines()
    var title = lines.first().trim()
    var rest = lines.drop(1).joinToString("\n").trim()
    if (title.length > TASK_TITLE) {
        rest = listOf(title.substring(TASK_TITLE - 1), rest).filter { it.isNotEmpty() }.joinToString("\n")
        title = title.take(TASK_TITLE - 1) + "…"
    }
    return NewTask(title = title, notes = rest.ifEmpty { null })
}

/** Today's journal text with the note added as a new paragraph. */
fun journalWithNote(existing: String?, note: String): String =
    if (existing.isNullOrBlank()) note.trim() else "${existing.trim()}\n\n${note.trim()}"

/**
 * Puts a note into another module (T18): the Inbox module has no write contracts with the others, so the app adds the
 * task, or writes today's journal entry, itself.
 */
class NoteSender(private val tasks: TasksApi, private val journal: JournalApi) {
    suspend fun toTask(text: String) {
        tasks.create(taskFromNote(text))
    }

    suspend fun toJournal(text: String) {
        val summary = journal.summary()
        journal.save(summary.date, JournalText(journalWithNote(summary.today?.text, text)))
    }
}
