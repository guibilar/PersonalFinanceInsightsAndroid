package com.guibilar.financeinsights.core.tasks

import com.guibilar.financeinsights.TasksSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskRulesTest {
    private val today = TasksSamples.TODAY
    private val all = TasksSamples.all.map { it.summary }

    @Test
    fun `the open tasks go into their sections`() {
        val sections = taskSections(all, today)

        assertEquals(listOf("t1"), sections.overdue.map { it.id })
        assertEquals(listOf("t2", "t3", "t4"), sections.today.map { it.id })
        assertEquals(listOf("t7"), sections.upcoming.map { it.id })
        assertEquals(listOf("t8"), sections.later.map { it.id })
        // The next due first.
        assertEquals(listOf("t6", "t5"), sections.recurring.map { it.id })
        assertFalse(sections.isEmpty)
    }

    @Test
    fun `a repeated task that is due shows in its day and in Recurring`() {
        val due = TasksSamples.plants.copy(dueOn = "2026-10-02").summary

        val sections = taskSections(listOf(due), today)

        assertEquals(listOf("t5"), sections.overdue.map { it.id })
        assertEquals(listOf("t5"), sections.recurring.map { it.id })
        assertTrue(sections.upcoming.isEmpty() && sections.later.isEmpty())
    }

    @Test
    fun `later holds the tasks after the next week and the ones without a date`() {
        val far = TasksSamples.dentist.copy(id = "far", dueOn = "2026-10-11").summary
        val edge = TasksSamples.dentist.copy(id = "edge", dueOn = "2026-10-10").summary

        val sections = taskSections(listOf(edge, far, TasksSamples.garage.summary), today)

        assertEquals(listOf("edge"), sections.upcoming.map { it.id })
        assertEquals(listOf("far", "t8"), sections.later.map { it.id })
    }

    @Test
    fun `the overdue tasks are the open tasks due before today`() {
        val doneLate = TasksSamples.handouts.copy(id = "late", dueOn = "2026-09-30").summary

        assertEquals(listOf("t1"), overdueTasks(all + doneLate, today).map { it.id })
    }

    @Test
    fun `the done section shows the latest done first, at most 20`() {
        val older = TasksSamples.handouts.copy(id = "old", doneAt = "2026-09-01T10:00:00Z").summary
        val many = (1..25).map { TasksSamples.handouts.copy(id = "d$it", doneAt = "2026-08-%02dT10:00:00Z".format(it)).summary }

        assertEquals(listOf("t9", "old"), latestDone(all + older, 20).map { it.id })
        val shown = latestDone(many)
        assertEquals(DONE_SHOWN, shown.size)
        assertEquals("d25", shown.first().id)
    }

    @Test
    fun `due dates read as short words`() {
        assertEquals("Today", dueText("2026-10-03", today))
        assertEquals("Tomorrow", dueText("2026-10-04", today))
        assertEquals("Yesterday", dueText("2026-10-02", today))
        assertEquals("2 days late", dueText("2026-10-01", today))
        assertEquals("Tue", dueText("2026-10-06", today))
        assertEquals("Sat", dueText("2026-10-10", today))
        assertEquals("Oct 11", dueText("2026-10-11", today))
        assertEquals("Jan 5, 2027", dueText("2027-01-05", today))
        assertNull(dueText(null, today))
        assertNull(dueText("soon", today))
    }

    @Test
    fun `a repeat reads as words`() {
        assertEquals("Every day", repeatText(TaskRepeat(1, RepeatUnit.Days)))
        assertEquals("Every 4 days", repeatText(TaskRepeat(4, RepeatUnit.Days)))
        assertEquals("Every week", repeatText(TaskRepeat(1, RepeatUnit.Weeks)))
        assertEquals("Every 12 months", repeatText(TaskRepeat(12, RepeatUnit.Months)))
        assertEquals("Every year", repeatText(TaskRepeat(1, RepeatUnit.Years)))
        assertEquals("Every 4 days, after each done", repeatLongText(TaskRepeat(4, RepeatUnit.Days, RepeatMode.FromCompletion)))
        assertEquals("Every month, on a fixed schedule", repeatLongText(TaskRepeat(1, RepeatUnit.Months, RepeatMode.FromDueDate)))
    }

    @Test
    fun `a recurring line has the rule and the next date`() {
        assertEquals("Every 4 days · next Tue", recurringText(TasksSamples.plants.summary, today))
        assertEquals("Every week · next Tomorrow", recurringText(TasksSamples.backup.summary, today))
        assertEquals("Every week · Today", recurringText(TasksSamples.backup.copy(dueOn = "2026-10-03").summary, today))
        assertEquals("Every week · 2 days late", recurringText(TasksSamples.backup.copy(dueOn = "2026-10-01").summary, today))
    }

    @Test
    fun `tags are kept in lower case, once each`() {
        assertEquals(listOf("home", "garden", "big job"), parseTags(" Home, garden ,home,, Big   Job "))
        assertTrue(parseTags("  ").isEmpty())
    }

    @Test
    fun `the quick add needs a title`() {
        assertNull(quickTask("   ", today))
        assertEquals(NewTask("Buy milk", dueOn = "2026-10-03"), quickTask(" Buy milk ", today))
        assertEquals(NewTask("Sort the garage"), quickTask("Sort the garage", null))
    }

    @Test
    fun `a renew task is due 14 days before the expiry, with the link`() {
        val renew = renewTask("d1", "Car insurance policy", "2026-10-25", today)

        assertEquals(NewTask("Renew Car insurance policy", dueOn = "2026-10-11", links = listOf(NewTaskLink.document("d1"))), renew)
    }

    @Test
    fun `a renew task for a document that expires soon is due today`() {
        assertEquals("2026-10-03", renewTask("d1", "Policy", "2026-10-10", today)?.dueOn)
        assertEquals("2026-10-03", renewTask("d1", "Policy", "2026-09-01", today)?.dueOn)
        assertNull(renewTask("d1", "Policy", null, today))
    }

    @Test
    fun `the repeat form checks its values`() {
        val draft = TaskDraft(title = "Water the plants", repeats = true, every = "4", unit = RepeatUnit.Days, mode = RepeatMode.FromCompletion)
        assertTrue(draft.canSave)
        assertEquals(TaskRepeat(4, RepeatUnit.Days, RepeatMode.FromCompletion), draft.repeat)

        assertEquals("Enter a number.", draft.copy(every = "").everyError)
        assertEquals("Use 1 to 1000.", draft.copy(every = "0").everyError)
        assertEquals("Use 1 to 1000.", draft.copy(every = "1001").everyError)
        assertFalse(draft.copy(every = "x").canSave)
        // Without a repeat, the number does not count.
        assertNull(draft.copy(repeats = false, every = "").everyError)
        assertNull(draft.copy(repeats = false).repeat)
        assertEquals("Enter a title.", draft.copy(title = " ").titleError)
        assertEquals("A title can have at most 200 characters.", draft.copy(title = "x".repeat(201)).titleError)
    }

    @Test
    fun `the form sends all the details, empty ones as nothing`() {
        val draft = TaskDraft.of(TasksSamples.plants).copy(title = " Water the plants ", notes = "  ", tags = "Home, balcony", every = " 5 ")

        assertEquals(
            TaskDetails(
                title = "Water the plants",
                notes = null,
                dueOn = "2026-10-06",
                tags = listOf("home", "balcony"),
                repeat = TaskRepeat(5, RepeatUnit.Days, RepeatMode.FromCompletion),
            ),
            draft.toDetails(),
        )
        val plain = TaskDraft.of(TasksSamples.garage)
        assertFalse(plain.repeats)
        assertEquals(TaskDetails(title = "Sort the garage", tags = listOf("home")), plain.toDetails())
        assertEquals(LocalDate.of(2026, 10, 3), TaskDraft.of(TasksSamples.paper).dueOn)
    }
}
