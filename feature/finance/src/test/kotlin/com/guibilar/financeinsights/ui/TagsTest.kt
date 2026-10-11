package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.TagReference
import com.guibilar.financeinsights.core.model.TransactionBulkUpdate
import com.guibilar.financeinsights.core.model.TagInput
import com.guibilar.financeinsights.core.model.TransactionUpdate
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.settings.TagsViewModel
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import com.guibilar.financeinsights.ui.transactions.editUpdate
import com.guibilar.financeinsights.ui.transactions.named
import com.guibilar.financeinsights.ui.transactions.tidyTagName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow as KFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Notes and tags: editing them, filtering by a tag, tagging many at once, and the Tags screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class TagsTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 4)
    private val vacation = "01a10460-5d8c-7d55-a0b8-a21750ee834e"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collect(messages: KFlow<UiMessage>): List<String> {
        val texts = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { messages.collect { texts += it.text } }
        return texts
    }

    @Test
    fun `a tag is found by its name, ignoring case and spaces`() {
        val tags = listOf(TagReference("a", "Vacation 2026"), TagReference("b", "Tax deductible"))
        assertEquals("Vacation 2026", tidyTagName("  Vacation   2026 "))
        assertEquals("a", tags.named(" vacation  2026")?.id)
        assertNull(tags.named("Vacation"))
    }

    @Test
    fun `an edit sends the note and tags only when they changed`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        advanceUntilIdle()
        val plain = model.state.value.list.items.first()
        val tagged = plain.copy(note = "With Sam", tags = listOf(TagReference("a", "A"), TagReference("b", "B")))

        assertEquals(
            TransactionUpdate(plain.description, plain.category.id),
            editUpdate(tagged, " ${plain.description} ", plain.category.id, plain.flow, " With Sam ", listOf("b", "a")),
        )
        assertEquals(
            TransactionUpdate(plain.description, plain.category.id, Flow.Transfer, note = "", tagIds = listOf("b")),
            editUpdate(tagged, plain.description, plain.category.id, Flow.Transfer, "  ", listOf("b")),
        )
        assertEquals("Road trip", editUpdate(plain, plain.description, plain.category.id, plain.flow, " Road trip ", emptyList()).note)
    }

    @Test
    fun `the list filters by a tag, from a link or the filters, and the totals follow`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute(tagId = vacation)) { today }
        advanceUntilIdle()
        assertEquals(vacation, api.transactionFilters.last().tagId)
        assertEquals(vacation, api.breakdownInputs.last().tagId)
        assertEquals(listOf("Streaming", "Vacation 2026"), model.state.value.tags.map { it.name })

        model.setTag(null)
        advanceUntilIdle()
        assertNull(api.transactionFilters.last().tagId)
    }

    @Test
    fun `a tag made while editing joins the list, and a taken name says so`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        advanceUntilIdle()

        val made = model.createTag("Gifts")
        assertEquals("Gifts", made.getOrThrow().name)
        assertEquals(listOf("Gifts", "Streaming", "Vacation 2026"), model.state.value.tags.map { it.name })

        api.refusing["addTag"] = FakeFinanceApi.conflict("A tag named “Gifts” already exists.")
        assertEquals("A tag named “Gifts” already exists.", model.createTag("gifts").exceptionOrNull()?.message)
    }

    @Test
    fun `ticked transactions get a tag at once`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        val messages = collect(model.messages)
        advanceUntilIdle()
        model.toggleSelected(model.state.value.list.items.first().id)

        model.bulk(TransactionBulkUpdate(emptyList(), addTagId = vacation))
        advanceUntilIdle()

        val sent = api.sent.single { it.first == "bulkUpdate" }.second as TransactionBulkUpdate
        assertEquals(1 to vacation, sent.transactionIds.size to sent.addTagId)
        assertEquals(listOf("Updated 1 transaction"), messages)
    }

    @Test
    fun `tags are added, renamed and deleted`() = runTest(dispatcher) {
        val model = TagsViewModel(api)
        val messages = collect(model.messages)
        advanceUntilIdle()
        val tags = (model.state.value as LoadState.Loaded).value
        assertEquals(listOf("Streaming" to 1, "Vacation 2026" to 5), tags.map { it.name to it.transactionCount })

        assertNull(model.save(null, "  Gifts "))
        assertNull(model.save(tags[1], "Trip to Japan"))
        model.delete(tags[0])
        advanceUntilIdle()

        assertEquals(
            listOf("addTag" to TagInput("Gifts"), "renameTag" to (vacation to TagInput("Trip to Japan")), "deleteTag" to tags[0].id),
            api.sent,
        )
        assertEquals(listOf("Added “Gifts”", "Renamed to “Trip to Japan”", "Deleted “Streaming”"), messages)

        api.refusing["addTag"] = FakeFinanceApi.conflict("A tag named “Streaming” already exists.")
        assertEquals("A tag named “Streaming” already exists.", model.save(null, "streaming"))
    }
}
