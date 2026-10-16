package com.guibilar.financeinsights.ui.today

import com.guibilar.financeinsights.FakeTodayApi
import com.guibilar.financeinsights.TodaySamples
import com.guibilar.financeinsights.core.logic.DataChanges
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeTodayApi()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the first load can use the server's cache, pull to refresh cannot`() = runTest(dispatcher) {
        val model = TodayViewModel(api)
        advanceUntilIdle()
        assertEquals(TodaySamples.complete, model.state.value.today)

        model.refresh()
        assertTrue(model.state.value.refreshing)
        advanceUntilIdle()

        assertEquals(listOf(false, true), api.calls)
        assertTrue(!model.state.value.refreshing)
    }

    @Test
    fun `back on Today after an import or a new transaction asks for new cards`() = runTest(dispatcher) {
        val model = TodayViewModel(api)
        advanceUntilIdle()
        // The screen opening: init has loaded it already.
        model.resumed()
        advanceUntilIdle()
        assertEquals(listOf(false), api.calls)

        // Back from another tab, nothing changed: the server's cache is fine.
        model.resumed()
        advanceUntilIdle()
        DataChanges.changed()
        model.resumed()
        advanceUntilIdle()
        // Once only: the answer after the change is new.
        model.resumed()
        advanceUntilIdle()

        assertEquals(listOf(false, false, true, false), api.calls)
    }

    @Test
    fun `a failed first load says why, and a retry asks for new cards`() = runTest(dispatcher) {
        api.failing = true
        val model = TodayViewModel(api)
        advanceUntilIdle()
        val failed = model.state.value
        assertNull(failed.today)
        assertTrue(!failed.loading)
        assertTrue(failed.error!!.startsWith("Can't reach the server"))

        api.failing = false
        model.retry()
        advanceUntilIdle()

        assertEquals(TodaySamples.complete, model.state.value.today)
        assertNull(model.state.value.error)
        assertEquals(listOf(false, true), api.calls)
    }

    @Test
    fun `a quiet reload that fails keeps the cards and says nothing`() = runTest(dispatcher) {
        val model = TodayViewModel(api)
        advanceUntilIdle()
        model.resumed()
        api.failing = true
        model.resumed()
        advanceUntilIdle()

        assertEquals(TodaySamples.complete, model.state.value.today)
        assertNull(model.state.value.error)
    }

    @Test
    fun `the day is the server's, or the phone's until the first answer`() {
        val phone = LocalDate.of(2026, 10, 4)
        assertEquals(phone, TodayState().day(phone))
        assertEquals(LocalDate.of(2026, 10, 3), TodayState(today = TodaySamples.complete).day(phone))
    }

    @Test
    fun `a note jotted on Today is saved, and Today loads again (T106)`() = runTest(dispatcher) {
        val notes = mutableListOf<String>()
        val model = TodayViewModel(api, saveNote = { notes += it })
        advanceUntilIdle()
        var cleared = false

        model.jot("  Buy furnace filters ") { cleared = true }
        advanceUntilIdle()

        assertEquals(listOf("Buy furnace filters"), notes)
        assertTrue(cleared)
        assertEquals(true, api.calls.last())
    }

    @Test
    fun `a task ticked on Today is done, and stays ticked (T106)`() = runTest(dispatcher) {
        val done = mutableListOf<String>()
        val model = TodayViewModel(api, completeTask = { done += it })
        advanceUntilIdle()

        model.complete(com.guibilar.financeinsights.TodaySamples.waterPlants)
        advanceUntilIdle()

        assertEquals(listOf("t1"), done)
        assertTrue("tasks.task-due.t1" in model.state.value.doneHere)
        assertEquals(true, api.calls.last())
    }
}
