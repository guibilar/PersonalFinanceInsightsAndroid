package com.guibilar.financeinsights.tasks

import com.guibilar.financeinsights.DocumentsSamples
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.FakeTasksApi
import com.guibilar.financeinsights.TasksSamples
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.tasks.RepeatMode
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.core.tasks.TaskDraft
import com.guibilar.financeinsights.core.tasks.TaskLinkKinds
import com.guibilar.financeinsights.core.tasks.TaskRepeat
import com.guibilar.financeinsights.core.tasks.TaskStatus
import com.guibilar.financeinsights.tasks.ui.LinkPicker
import com.guibilar.financeinsights.tasks.ui.Linked
import com.guibilar.financeinsights.tasks.ui.TaskViewModel
import com.guibilar.financeinsights.tasks.ui.TasksViewModel
import com.guibilar.financeinsights.tasks.ui.quickDueLabel
import com.guibilar.financeinsights.ui.common.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeTasksApi()
    private val documents = FakeDocumentsApi()
    private val finance = FakeFinanceApi()
    private val today = TasksSamples.TODAY

    /** A phone whose clock is a day off: the server's today counts. */
    private val phoneDay = { LocalDate.of(2026, 10, 4) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // The Tasks screen.

    @Test
    fun `the screen loads the open and the done tasks, in sections, with the server's today`() = runTest(dispatcher) {
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(today, state.today)
        assertEquals(listOf("t1"), state.sections.overdue.map { it.id })
        assertEquals(listOf("t2", "t3", "t4"), state.sections.today.map { it.id })
        assertEquals(listOf("t7"), state.sections.upcoming.map { it.id })
        assertEquals(listOf("t8"), state.sections.later.map { it.id })
        assertEquals(listOf("t6", "t5"), state.sections.recurring.map { it.id })
        assertEquals(listOf("t9"), state.done.map { it.id })
        assertTrue(api.sent.contains("tasks" to listOf(TaskStatus.Open, null, null, null, 1, 100)))
        assertTrue(api.sent.contains("tasks" to listOf(TaskStatus.Done, null, null, null, 1, 100)))
    }

    @Test
    fun `without the summary, the phone's day counts`() = runTest(dispatcher) {
        api.failing += "summary"
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()

        assertEquals(phoneDay(), model.state.value.today)
        assertNull(model.state.value.error)
    }

    @Test
    fun `the open tasks load page after page`() = runTest(dispatcher) {
        api.tasks.clear()
        repeat(150) { index -> api.tasks += TasksSamples.paper.copy(id = "p$index") }
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()

        assertEquals(150, model.state.value.open.size)
        assertEquals(listOf(1, 2), api.sent.filter { it.first == "tasks" && (it.second as List<*>)[0] == TaskStatus.Open }.map { (it.second as List<*>)[4] })
    }

    @Test
    fun `the overdue link shows only the overdue tasks, until Show all`() = runTest(dispatcher) {
        val model = TasksViewModel(api, overdueOnly = true, today = phoneDay)
        advanceUntilIdle()

        val sections = model.state.value.sections
        assertEquals(listOf("t1"), sections.overdue.map { it.id })
        assertTrue(sections.today.isEmpty() && sections.recurring.isEmpty())

        model.showAll()
        assertEquals(3, model.state.value.sections.today.size)
    }

    @Test
    fun `a failed load says why, and a quiet reload keeps the tasks`() = runTest(dispatcher) {
        api.failing += "tasks"
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()
        assertEquals("Can't reach the server. Check your connection and that Tailscale is on.", model.state.value.error)

        api.failing.clear()
        model.refresh()
        advanceUntilIdle()
        assertNull(model.state.value.error)

        model.resumed()
        api.failing += "tasks"
        model.resumed()
        advanceUntilIdle()
        assertNull(model.state.value.error)
        assertEquals(8, model.state.value.open.size)
    }

    @Test
    fun `the quick add adds a task with its day, and Today loads again`() = runTest(dispatcher) {
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()
        val version = DataChanges.version

        assertTrue(model.add(" Buy milk ", today))
        advanceUntilIdle()

        assertEquals("Buy milk", api.tasks.last().title)
        assertEquals("2026-10-03", api.tasks.last().dueOn)
        assertTrue(model.state.value.sections.today.any { it.title == "Buy milk" })
        assertTrue(DataChanges.version > version)
        assertEquals("Added “Buy milk”", model.messages.first().text)
    }

    @Test
    fun `the quick add without a date, and an empty title`() = runTest(dispatcher) {
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()

        assertFalse(model.add("   ", today))
        assertTrue(model.add("Fix the fence", null))
        assertNull(api.tasks.last().dueOn)
        assertTrue(model.state.value.sections.later.any { it.title == "Fix the fence" })
    }

    @Test
    fun `a failed quick add keeps the text and says why`() = runTest(dispatcher) {
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()
        api.refusing["create"] = FakeTasksApi.readOnly

        assertFalse(model.add("Buy milk", today))
        assertEquals("You can only read tasks.", model.messages.first().text)
    }

    @Test
    fun `done moves a task to Done, and Undo brings it back`() = runTest(dispatcher) {
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()
        val version = DataChanges.version

        model.done(model.state.value.sections.today.first { it.id == "t4" })
        advanceUntilIdle()

        assertFalse(model.state.value.open.any { it.id == "t4" })
        assertEquals("t4", model.state.value.done.first().id)
        assertTrue(DataChanges.version > version)
        val message = model.messages.first()
        assertEquals("Done: Buy printer paper", message.text)
        assertEquals("Undo", message.actionLabel)

        message.action!!.invoke()
        advanceUntilIdle()
        assertTrue(model.state.value.sections.today.any { it.id == "t4" })
        assertFalse(model.state.value.done.any { it.id == "t4" })
        assertEquals(listOf("done", "undo"), api.sent.map { it.first }.filter { it == "done" || it == "undo" })
    }

    @Test
    fun `done on a repeated task moves it to its next date`() = runTest(dispatcher) {
        api.tasks.replaceAll { if (it.id == "t5") it.copy(dueOn = "2026-10-03") else it }
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()

        model.done(model.state.value.sections.today.first { it.id == "t5" })
        advanceUntilIdle()

        // Every 4 days after each done: Oct 7.
        val plants = model.state.value.open.first { it.id == "t5" }
        assertEquals("2026-10-07", plants.dueOn)
        assertFalse(model.state.value.sections.today.any { it.id == "t5" })
        assertEquals("Done. Next: Wed", model.messages.first().text)
    }

    @Test
    fun `a done task unchecked is open again`() = runTest(dispatcher) {
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()

        model.undo("t9")
        advanceUntilIdle()

        assertTrue(model.state.value.sections.today.any { it.id == "t9" })
        assertTrue(model.state.value.done.isEmpty())
    }

    @Test
    fun `a task done on another device gets the server's reason, and the lists load again`() = runTest(dispatcher) {
        val model = TasksViewModel(api, today = phoneDay)
        advanceUntilIdle()
        api.tasks.replaceAll { if (it.id == "t4") it.copy(status = TaskStatus.Done, doneAt = "2026-10-03T10:00:00Z") else it }

        model.done(model.state.value.sections.today.first { it.id == "t4" })
        advanceUntilIdle()

        assertEquals("The task is done already.", model.messages.first().text)
        assertFalse(model.state.value.open.any { it.id == "t4" })
    }

    @Test
    fun `the date chip names the day`() {
        assertEquals("Due today", quickDueLabel(today, today))
        assertEquals("Due tomorrow", quickDueLabel(today.plusDays(1), today))
        assertEquals("Due Oct 10", quickDueLabel(today.plusDays(7), today))
        assertEquals("Due Jan 4, 2027", quickDueLabel(LocalDate.of(2027, 1, 4), today))
        assertEquals("No date", quickDueLabel(null, today))
    }

    // One task (the sheet).

    private fun task(id: String, changed: () -> Unit = {}) = TaskViewModel(api, documents, finance, id, onChanged = changed)

    @Test
    fun `a task shows its linked document and transaction`() = runTest(dispatcher) {
        api.tasks.replaceAll { if (it.id == "t3") it.copy(links = it.links + TasksSamples.statement.links) else it }
        val model = task("t3")
        advanceUntilIdle()

        val document = model.state.value.documents[TasksSamples.INSURANCE_DOCUMENT]
        assertEquals("Car insurance policy", (document as Linked.Found).value.title)
        val transaction = model.state.value.transactions[DocumentsSamples.METRO_TRANSACTION]
        assertEquals("METRO 221", (transaction as Linked.Found).value.description)
    }

    @Test
    fun `a link to a deleted document says so`() = runTest(dispatcher) {
        documents.documents.removeAll { it.id == TasksSamples.INSURANCE_DOCUMENT }
        val model = task("t3")
        advanceUntilIdle()

        assertEquals(Linked.Missing, model.state.value.documents[TasksSamples.INSURANCE_DOCUMENT])
    }

    @Test
    fun `save sends the repeat form, and the screen hears of it`() = runTest(dispatcher) {
        var changes = 0
        val model = task("t8") { changes++ }
        advanceUntilIdle()

        val draft = TaskDraft.of(model.task!!).copy(repeats = true, every = "3", unit = RepeatUnit.Months, mode = RepeatMode.FromDueDate)
        assertNull(model.save(draft))

        val saved = api.tasks.first { it.id == "t8" }
        assertEquals(TaskRepeat(3, RepeatUnit.Months, RepeatMode.FromDueDate), saved.repeat)
        // A repeated task without a date is due today.
        assertEquals("2026-10-03", saved.dueOn)
        assertEquals(saved.repeat, model.task!!.repeat)
        assertEquals(1, changes)
    }

    @Test
    fun `a refused save says why`() = runTest(dispatcher) {
        val model = task("t8")
        advanceUntilIdle()
        api.refusing["update"] = com.guibilar.financeinsights.core.api.ApiError.Http(
            400, "Bad request", null, null, mapOf("repeat.every" to listOf("Repeat every 1 to 1000 units.")),
        )

        assertEquals("Repeat every 1 to 1000 units.", model.save(TaskDraft.of(model.task!!)))
    }

    @Test
    fun `done and undo in the sheet`() = runTest(dispatcher) {
        var changes = 0
        val model = task("t6") { changes++ }
        advanceUntilIdle()

        model.done()
        advanceUntilIdle()
        // Every week on a fixed schedule, from Oct 4: Oct 11.
        assertEquals("2026-10-11", model.task!!.dueOn)
        assertEquals(2, model.task!!.completions.size)

        model.undo()
        advanceUntilIdle()
        assertEquals("2026-10-04", model.task!!.dueOn)
        assertEquals(1, model.task!!.completions.size)
        assertEquals(2, changes)
    }

    @Test
    fun `link a document and a transaction, then remove a link`() = runTest(dispatcher) {
        val model = task("t8")
        advanceUntilIdle()

        model.openPicker(LinkPicker.Document)
        advanceUntilIdle()
        val candidates = (model.state.value.documentCandidates as LoadState.Loaded).value
        assertEquals(5, candidates.size)
        model.closePicker()
        model.linkDocument(candidates.first { it.id == "d5" })
        advanceUntilIdle()

        model.openPicker(LinkPicker.Transaction)
        advanceUntilIdle()
        val transactions = (model.state.value.transactionCandidates as LoadState.Loaded).value
        assertTrue(transactions.isNotEmpty())
        model.closePicker()
        model.linkTransaction(transactions.first())
        advanceUntilIdle()

        val links = model.task!!.links
        assertEquals(listOf(TaskLinkKinds.DOCUMENT, TaskLinkKinds.TRANSACTION), links.map { it.kind })
        assertTrue(model.state.value.documents["d5"] is Linked.Found)
        assertTrue(model.state.value.transactions[transactions.first().id] is Linked.Found)
        assertNull(model.state.value.picker)

        model.unlink(links.first())
        advanceUntilIdle()
        assertEquals(listOf(TaskLinkKinds.TRANSACTION), api.tasks.first { it.id == "t8" }.links.map { it.kind })
    }

    @Test
    fun `delete closes the sheet`() = runTest(dispatcher) {
        var changes = 0
        val model = task("t7") { changes++ }
        advanceUntilIdle()

        model.delete()
        advanceUntilIdle()

        assertTrue(model.state.value.deleted)
        assertFalse(api.tasks.any { it.id == "t7" })
        assertEquals(1, changes)
    }

    @Test
    fun `a read-only user gets a clear refusal`() = runTest(dispatcher) {
        val model = task("t7")
        advanceUntilIdle()
        api.refusing["done"] = FakeTasksApi.readOnly

        model.done()
        advanceUntilIdle()

        assertEquals("You can only read tasks.", model.messages.first().text)
        assertEquals("2026-10-08", model.task!!.dueOn)
    }
}
