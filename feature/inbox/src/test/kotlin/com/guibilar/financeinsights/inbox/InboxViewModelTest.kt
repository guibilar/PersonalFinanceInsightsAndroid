package com.guibilar.financeinsights.inbox

import com.guibilar.financeinsights.FakeJournalApi
import com.guibilar.financeinsights.FakeTasksApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeInboxApi()
    private val tasks = FakeTasksApi()
    private val journal = FakeJournalApi()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a second tap while a change runs does nothing, so a note makes one task`() = runTest(dispatcher) {
        val model = InboxViewModel(api, NoteSender(tasks, journal))
        advanceUntilIdle()
        val note = api.notes.first { it.id == "n1" }

        model.sortTo(note, NoteTarget.Task)
        model.sortTo(note, NoteTarget.Task)
        // Another note while the first runs: it waits for its own tap.
        model.done(api.notes.first { it.id == "n2" })
        advanceUntilIdle()

        assertEquals(1, tasks.sent.count { it.first == "create" })
        assertEquals(listOf("n1" to "Task"), api.sent.filter { it.first == "done" }.map { it.second })
        assertNull(model.state.value.busy)
    }
}
