package com.guibilar.financeinsights.journal

import com.guibilar.financeinsights.FakeJournalApi
import com.guibilar.financeinsights.JournalSamples
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.journal.ui.JournalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class JournalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeJournalApi()
    private val today = JournalSamples.TODAY

    /** A phone whose clock is a day off: the server's today counts. */
    private val phoneDay = { LocalDate.of(2026, 10, 9) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the screen loads the summary, today's empty entry, and the entries, with the server's today`() = runTest(dispatcher) {
        val model = JournalViewModel(api, today = phoneDay)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(today, state.today)
        assertEquals(today, state.day)
        assertEquals(6, state.summary.valueOrNull?.streak)
        assertNull(state.entry.valueOrNull)
        assertEquals("", state.text)
        assertFalse(state.changed)
        assertEquals(8, state.totalCount)
        assertEquals(today.minusDays(1).toString(), state.entries.valueOrNull?.first()?.day)
        // Today's entry comes with the summary: no request for it.
        assertTrue(api.sent.none { it.first == "entry" })
    }

    @Test
    fun `writing today saves the trimmed text, then the streak goes on`() = runTest(dispatcher) {
        val model = JournalViewModel(api) { today }
        advanceUntilIdle()
        val before = DataChanges.version

        model.edit("  Long walk by the river.  ")
        assertTrue(model.state.value.changed)
        model.save()
        advanceUntilIdle()

        assertEquals("save" to (today.toString() to "Long walk by the river."), api.sent.last { it.first == "save" })
        val state = model.state.value
        assertEquals(7, state.summary.valueOrNull?.streak)
        assertEquals("Long walk by the river.", state.text)
        assertFalse(state.changed)
        assertTrue(DataChanges.version > before)
        assertEquals("Saved", model.messages.first().text)
    }

    @Test
    fun `another day opens its entry, from the start day too, and a day after today opens today`() = runTest(dispatcher) {
        val model = JournalViewModel(api, startDay = today.minusDays(2).toString()) { today }
        advanceUntilIdle()
        assertEquals(today.minusDays(2), model.state.value.day)
        assertEquals("Rain all day. Read half of the new book.", model.state.value.text)

        model.openDay(today.minusDays(7))
        advanceUntilIdle()
        assertNull(model.state.value.entry.valueOrNull)
        assertEquals("", model.state.value.text)

        model.openDay(today.plusDays(3))
        advanceUntilIdle()
        assertEquals(today, model.state.value.day)
    }

    @Test
    fun `delete removes the entry of the day`() = runTest(dispatcher) {
        val model = JournalViewModel(api, startDay = today.minusDays(1).toString()) { today }
        advanceUntilIdle()

        model.delete()
        advanceUntilIdle()

        assertEquals("delete" to today.minusDays(1).toString(), api.sent.last { it.first == "delete" })
        assertNull(model.state.value.entry.valueOrNull)
        assertEquals(0, model.state.value.summary.valueOrNull?.streak)
    }

    @Test
    fun `the search waits for a pause, and a refused save says why`() = runTest(dispatcher) {
        val model = JournalViewModel(api) { today }
        advanceUntilIdle()

        model.search("marco")
        advanceTimeBy(100)
        model.search("marco game")
        advanceUntilIdle()
        assertEquals(1, api.sent.count { it.first == "entries" && (it.second as List<*>)[0] == "marco game" })
        assertEquals(0, api.sent.count { it.first == "entries" && (it.second as List<*>)[0] == "marco" })
        assertEquals(1, model.state.value.totalCount)

        api.refusing["save"] = ApiError.Http(403, "Forbidden", null, null, emptyMap())
        model.edit("Mine")
        model.save()
        advanceUntilIdle()
        assertEquals("You can only read the journal.", model.messages.first().text)
    }
}
