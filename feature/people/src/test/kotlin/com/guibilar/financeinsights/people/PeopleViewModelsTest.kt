package com.guibilar.financeinsights.people

import com.guibilar.financeinsights.FakePeopleApi
import com.guibilar.financeinsights.PeopleSamples
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.people.GiftDetails
import com.guibilar.financeinsights.core.people.InteractionDetails
import com.guibilar.financeinsights.core.people.LoanDetails
import com.guibilar.financeinsights.core.people.LoanDirection
import com.guibilar.financeinsights.people.ui.PeopleViewModel
import com.guibilar.financeinsights.people.ui.PersonFormViewModel
import com.guibilar.financeinsights.people.ui.PersonViewModel
import com.guibilar.financeinsights.ui.common.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PeopleViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakePeopleApi()
    private val today = { PeopleSamples.TODAY }

    /** A phone whose clock is a day off: the server's today counts. */
    private val phoneDay = { LocalDate.of(2026, 10, 4) }

    private val readOnly = ApiError.Http(403, "Forbidden", null, null, emptyMap())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the screen loads the summary, the loans, the groups and everybody, with the server's today`() = runTest(dispatcher) {
        val model = PeopleViewModel(api, phoneDay)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(PeopleSamples.TODAY, state.today)
        assertEquals(listOf("Marco Silva"), state.summary.valueOrNull?.reachOut?.map { it.name })
        assertEquals(listOf(19), state.summary.valueOrNull?.upcoming?.map { it.days })
        assertEquals(listOf("Camping stove"), state.loans.map { it.itemName })
        assertEquals(listOf("family", "gaming group"), state.groups.map { it.name })
        assertEquals(listOf("Ana Lima", "Marco Silva"), state.people.valueOrNull?.map { it.name })
        assertEquals(2, state.totalCount)
    }

    @Test
    fun `a group and a search filter the list`() = runTest(dispatcher) {
        val model = PeopleViewModel(api, today)
        advanceUntilIdle()

        model.pickGroup("family")
        advanceUntilIdle()
        assertEquals(listOf("Ana Lima"), model.state.value.people.valueOrNull?.map { it.name })

        model.pickGroup(null)
        model.search("mar")
        model.search("marco")
        advanceUntilIdle()
        assertEquals(listOf("Marco Silva"), model.state.value.people.valueOrNull?.map { it.name })
        assertEquals(listOf(null, "marco", "name"), api.sent.last { it.first == "people" }.second)
    }

    @Test
    fun `without the summary, the screen says so`() = runTest(dispatcher) {
        api.failing += "summary"
        val model = PeopleViewModel(api, today)
        advanceUntilIdle()

        assertTrue(model.state.value.summary is LoadState.Failed)
        assertTrue(model.state.value.people is LoadState.Loaded)
    }

    @Test
    fun `a contact moves the last contact, and a contact in the future is refused`() = runTest(dispatcher) {
        val model = PersonViewModel(api, PeopleSamples.MARCO, today)
        advanceUntilIdle()
        assertTrue(model.state.value.person.valueOrNull!!.person.contactOverdue)

        assertTrue(model.logContact("Call", " Quick call ", null))
        advanceUntilIdle()

        assertEquals("logInteraction" to (PeopleSamples.MARCO to InteractionDetails(null, "Call", "Quick call")), api.sent.last { it.first == "logInteraction" })
        val person = model.state.value.person.valueOrNull!!.person
        assertEquals(PeopleSamples.TODAY.toString(), person.lastContactOn)
        assertFalse(person.contactOverdue)

        assertFalse(model.logContact("Call", "", PeopleSamples.TODAY.plusDays(1)))
        assertEquals(1, api.sent.count { it.first == "logInteraction" })
    }

    @Test
    fun `a gift idea is added, given today, and an idea again`() = runTest(dispatcher) {
        val model = PersonViewModel(api, PeopleSamples.MARCO, today)
        advanceUntilIdle()

        assertFalse(model.addGift(" ", null, ""))
        assertTrue(model.addGift("Book", 25.0, "Birthday"))
        advanceUntilIdle()
        assertEquals(PeopleSamples.MARCO to GiftDetails("Book", price = 25.0, occasion = "Birthday"), api.sent.last { it.first == "addGift" }.second)

        val kettle = model.state.value.person.valueOrNull!!.giftIdeas.first { it.id == "g1" }
        model.toggleGiven(kettle)
        advanceUntilIdle()
        val given = model.state.value.person.valueOrNull!!.giftIdeas.first { it.id == "g1" }
        assertEquals(PeopleSamples.TODAY.toString(), given.givenOn)

        model.toggleGiven(given)
        advanceUntilIdle()
        assertNull(model.state.value.person.valueOrNull!!.giftIdeas.first { it.id == "g1" }.givenOn)
    }

    @Test
    fun `a loan is added and comes back`() = runTest(dispatcher) {
        val model = PersonViewModel(api, PeopleSamples.MARCO, today)
        advanceUntilIdle()

        assertTrue(model.addLoan("Drill", lent = false, dueOn = PeopleSamples.TODAY.plusDays(10)))
        advanceUntilIdle()
        assertEquals(
            PeopleSamples.MARCO to LoanDetails("Drill", LoanDirection.Borrowed, dueOn = "2026-10-13"),
            api.sent.last { it.first == "addLoan" }.second,
        )

        model.returned(PeopleSamples.stove)
        advanceUntilIdle()
        val stove = model.state.value.person.valueOrNull!!.loans.first { it.id == PeopleSamples.STOVE }
        assertEquals(PeopleSamples.TODAY.toString(), stove.returnedOn)
        assertFalse(stove.overdue)
    }

    @Test
    fun `a read-only user is told why a write failed, and a delete closes`() = runTest(dispatcher) {
        val model = PersonViewModel(api, PeopleSamples.MARCO, today)
        advanceUntilIdle()

        api.refusing["addGift"] = readOnly
        assertFalse(model.addGift("Book", null, ""))

        assertTrue(model.delete())
        assertTrue(api.people.none { it.person.id == PeopleSamples.MARCO })
    }

    @Test
    fun `the form checks, adds a person, and changes one in full`() = runTest(dispatcher) {
        val add = PersonFormViewModel(api, null, today)
        assertNull(add.save())
        assertEquals(setOf("name"), add.state.value.errors.keys)

        add.edit { it.copy(name = " Priya Rao ", groups = "Friends", birthMonth = 2, birthDay = "29", contactEveryDays = 30) }
        val id = add.save()
        advanceUntilIdle()
        val created = api.people.first { it.person.id == id }.person
        assertEquals("Priya Rao", created.name)
        assertEquals(listOf("friends"), created.groups)
        assertEquals(30, created.contactEveryDays)

        val change = PersonFormViewModel(api, PeopleSamples.MARCO, today)
        advanceUntilIdle()
        assertEquals("Marco Silva", change.state.value.draft.name)
        change.edit { it.copy(relationship = "", contactEveryDays = null) }
        assertEquals(PeopleSamples.MARCO, change.save())
        val changed = api.people.first { it.person.id == PeopleSamples.MARCO }.person
        assertNull(changed.relationship)
        assertNull(changed.contactEveryDays)
        assertEquals(PeopleSamples.marcoSummary.birthday, changed.birthday)
    }
}
