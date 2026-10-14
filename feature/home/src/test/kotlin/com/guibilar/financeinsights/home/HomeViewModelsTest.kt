package com.guibilar.financeinsights.home

import com.guibilar.financeinsights.DocumentsSamples
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.FakeHomeApi
import com.guibilar.financeinsights.HomeSamples
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.home.ItemDraft
import com.guibilar.financeinsights.core.home.ItemLinkKinds
import com.guibilar.financeinsights.core.home.NO_ROOM
import com.guibilar.financeinsights.core.home.NewItem
import com.guibilar.financeinsights.core.home.WarrantyFilter
import com.guibilar.financeinsights.core.tasks.NewTask
import com.guibilar.financeinsights.core.tasks.RepeatMode
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.core.tasks.TaskLinkKinds
import com.guibilar.financeinsights.home.ui.HomeViewModel
import com.guibilar.financeinsights.home.ui.ItemPicker
import com.guibilar.financeinsights.home.ui.ItemViewModel
import com.guibilar.financeinsights.home.ui.Linked
import com.guibilar.financeinsights.home.ui.NewItemViewModel
import com.guibilar.financeinsights.home.ui.Photo
import com.guibilar.financeinsights.home.ui.PhotoKind
import com.guibilar.financeinsights.home.ui.RoomViewModel
import com.guibilar.financeinsights.home.ui.maintenanceOf
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeHomeApi()
    private val tasks = HomeTestData.tasks()
    private val documents = HomeTestData.documents()
    private val finance = FakeFinanceApi()

    /** A phone whose clock is a day off: the server's today counts. */
    private val phoneDay = { LocalDate.of(2026, 10, 4) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** An item page. Plain JVM tests can't make pictures: the decoder gives none. */
    private fun item(id: String = HomeSamples.dishwasher.id) =
        ItemViewModel(api, documents, finance, tasks, id, decode = { _, _ -> null }, decoding = dispatcher)

    // The Home screen.

    @Test
    fun `the screen loads the summary and the maintenance jobs, with the server's today`() = runTest(dispatcher) {
        val model = HomeViewModel(api, tasks, phoneDay)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(HomeSamples.TODAY, state.today)
        assertEquals(7, state.summary.valueOrNull?.itemCount)
        // Only the repeated tasks that link to an item, the next due first.
        assertEquals(listOf(HomeSamples.BATTERY_TASK, HomeSamples.FILTER_TASK), state.maintenance.valueOrNull?.map { it.id })
    }

    @Test
    fun `maintenance jobs are open repeated tasks with an item link`() {
        val jobs = maintenanceOf(listOf(HomeTestData.filter.summary, HomeTestData.batteries.summary.copy(repeat = null), HomeTestData.filter.summary.copy(id = "x", links = emptyList())))
        assertEquals(listOf(HomeSamples.FILTER_TASK), jobs.map { it.id })
    }

    @Test
    fun `without the tasks, the inventory still shows`() = runTest(dispatcher) {
        tasks.failing += "tasks"
        val model = HomeViewModel(api, tasks, phoneDay)
        advanceUntilIdle()

        assertTrue(model.state.value.summary is LoadState.Loaded)
        assertTrue(model.state.value.maintenance is LoadState.Failed)
    }

    @Test
    fun `a search lists the matching items once typing pauses`() = runTest(dispatcher) {
        val model = HomeViewModel(api, tasks, phoneDay)
        advanceUntilIdle()

        model.search("bos")
        model.search("bosch")
        advanceUntilIdle()

        assertEquals(listOf("Dishwasher"), model.state.value.results.valueOrNull?.map { it.name })
        assertEquals(1, api.sent.count { it.first == "items" })

        model.showEnding(true)
        advanceUntilIdle()
        assertTrue(api.sent.last().second == listOf(null, null, null, "bosch", WarrantyFilter.EXPIRING, 1, HomeViewModel.RESULTS))

        model.clearSearch()
        advanceUntilIdle()
        assertFalse(model.state.value.query.isActive)
    }

    @Test
    fun `a new room, and a name that exists`() = runTest(dispatcher) {
        val model = HomeViewModel(api, tasks, phoneDay)
        advanceUntilIdle()

        assertNull(model.addRoom(" Garage "))
        advanceUntilIdle()
        assertEquals("Garage", api.rooms.last().name)
        assertEquals("Garage", model.state.value.summary.valueOrNull?.rooms?.mapNotNull { it.name }?.last())

        assertEquals("A room with this name exists.", model.addRoom("kitchen"))

        api.refusing["createRoom"] = FakeHomeApi.readOnly
        assertEquals("You can only read the home inventory.", model.addRoom("Attic"))
    }

    @Test
    fun `the export gives the file, or says why not`() = runTest(dispatcher) {
        val model = HomeViewModel(api, tasks, phoneDay)
        advanceUntilIdle()

        val csv = model.export()
        assertNotNull(csv)
        assertTrue(String(csv!!, Charsets.UTF_8).contains("Dishwasher"))

        api.failing += "export"
        assertNull(model.export())
        assertFalse(model.state.value.exporting)
    }

    // A room.

    @Test
    fun `a room lists its items, and the items without a room have their own page`() = runTest(dispatcher) {
        val kitchen = RoomViewModel(api, HomeSamples.kitchen.id)
        val none = RoomViewModel(api, NO_ROOM)
        advanceUntilIdle()

        assertEquals("Kitchen", kitchen.state.value.name)
        assertEquals(listOf("Dishwasher", "Espresso machine"), kitchen.state.value.items.valueOrNull?.map { it.name })
        assertEquals("No room", none.state.value.name)
        assertFalse(none.isRoom)
        assertEquals(listOf("Smoke detectors"), none.state.value.items.valueOrNull?.map { it.name })
    }

    @Test
    fun `a room with items is not deleted, an empty one is`() = runTest(dispatcher) {
        val kitchen = RoomViewModel(api, HomeSamples.kitchen.id)
        advanceUntilIdle()
        kitchen.delete()
        advanceUntilIdle()
        assertFalse(kitchen.state.value.deleted)
        assertTrue(api.rooms.any { it.id == HomeSamples.kitchen.id })

        api.items.removeAll { it.roomId == HomeSamples.bedroom.id }
        val bedroom = RoomViewModel(api, HomeSamples.bedroom.id)
        advanceUntilIdle()
        bedroom.delete()
        advanceUntilIdle()
        assertTrue(bedroom.state.value.deleted)
        assertTrue(api.rooms.none { it.id == HomeSamples.bedroom.id })
    }

    @Test
    fun `a room is renamed, and its items follow`() = runTest(dispatcher) {
        val office = RoomViewModel(api, HomeSamples.office.id)
        advanceUntilIdle()

        assertNull(office.rename("Study"))
        advanceUntilIdle()
        assertEquals("Study", office.state.value.name)
        assertEquals("Study", api.items.first { it.id == HomeSamples.laptop.id }.roomName)
        assertEquals("A room with this name exists.", office.rename("Kitchen"))
    }

    // An item.

    @Test
    fun `an item loads with its documents and its purchase`() = runTest(dispatcher) {
        val laptop = item(HomeSamples.laptop.id)
        val dishwasher = item()
        advanceUntilIdle()

        val found = laptop.state.value.transactions[HomeSamples.METRO_TRANSACTION]
        assertTrue(found is Linked.Found)
        assertEquals("Dishwasher", dishwasher.item?.name)
        // The photo is an image, but this decoder gives nothing; the warranty is a PDF.
        assertEquals(Photo.None, dishwasher.state.value.photos[HomeSamples.PHOTO_DOCUMENT])
        assertEquals(Photo.None, dishwasher.state.value.photos[HomeSamples.WARRANTY_DOCUMENT])
        assertTrue(documents.sent.contains("file" to "f9"))
        assertFalse(documents.sent.contains("file" to "f2"))
    }

    @Test
    fun `saving sends all the details`() = runTest(dispatcher) {
        val model = item(HomeSamples.sofa.id)
        advanceUntilIdle()

        assertNull(model.save(ItemDraft.of(model.item!!).copy(currentValue = "800", quantity = "2")))
        advanceUntilIdle()
        assertEquals(1600.0, model.item!!.value, 0.0)
        assertEquals(1200.0, api.items.first { it.id == HomeSamples.sofa.id }.purchasePrice!!, 0.0)
    }

    @Test
    fun `linking the purchase fills the day, but keeps the price`() = runTest(dispatcher) {
        val model = item(HomeSamples.sofa.id)
        advanceUntilIdle()
        model.openPicker(ItemPicker.Transaction)
        advanceUntilIdle()
        val metro = model.state.value.transactionCandidates.valueOrNull!!.first { it.id == HomeSamples.METRO_TRANSACTION }

        model.closePicker()
        model.linkTransaction(metro)
        advanceUntilIdle()

        val sofa = api.items.first { it.id == HomeSamples.sofa.id }
        assertEquals(1200.0, sofa.purchasePrice!!, 0.0)
        assertEquals(metro.day.toString(), sofa.purchasedOn)
        assertTrue(sofa.links.any { it.kind == ItemLinkKinds.TRANSACTION && it.targetId == metro.id })
    }

    @Test
    fun `a document links, and the link goes again`() = runTest(dispatcher) {
        val model = item(HomeSamples.sofa.id)
        advanceUntilIdle()
        model.openPicker(ItemPicker.Document)
        advanceUntilIdle()
        val lease = model.state.value.documentCandidates.valueOrNull!!.first { it.id == "d5" }

        model.closePicker()
        model.linkDocument(lease)
        advanceUntilIdle()
        val link = model.item!!.links.single()
        assertEquals("d5", link.targetId)

        model.unlink(link)
        advanceUntilIdle()
        assertTrue(model.item!!.links.isEmpty())
    }

    @Test
    fun `a photo of the receipt goes to Documents and links to the item`() = runTest(dispatcher) {
        val model = item(HomeSamples.sofa.id)
        advanceUntilIdle()

        model.addPhoto(UploadFile("photo.jpg", DocumentsSamples.jpegBytes), PhotoKind.Receipt)
        advanceUntilIdle()

        val upload = documents.uploads.single()
        assertTrue(upload.contains("title" to "Sofa receipt"))
        assertTrue(upload.contains("kind" to DocumentKind.Receipt.name))
        assertEquals(ItemLinkKinds.DOCUMENT, api.items.first { it.id == HomeSamples.sofa.id }.links.single().kind)
        assertFalse(model.state.value.uploading)
    }

    @Test
    fun `a maintenance task repeats after each done, and links to the item`() = runTest(dispatcher) {
        val model = item(HomeSamples.sofa.id)
        advanceUntilIdle()

        assertEquals("Use 1 to 1000.", model.addMaintenance("Vacuum the sofa", 0, RepeatUnit.Weeks, HomeSamples.TODAY))
        assertNull(model.addMaintenance(" Vacuum the sofa ", 2, RepeatUnit.Weeks, HomeSamples.TODAY))

        val task = tasks.sent.last { it.first == "create" }.second as NewTask
        assertEquals("Vacuum the sofa", task.title)
        assertEquals("2026-10-03", task.dueOn)
        assertEquals(RepeatMode.FromCompletion, task.repeat?.mode)
        assertEquals(2, task.repeat?.every)
        assertEquals(TaskLinkKinds.HOME_ITEM, task.links.single().kind)
        assertEquals(HomeSamples.sofa.id, task.links.single().targetId)
    }

    @Test
    fun `delete closes the item, and a read-only user is told why not`() = runTest(dispatcher) {
        val model = item(HomeSamples.sofa.id)
        advanceUntilIdle()
        api.refusing["delete"] = FakeHomeApi.readOnly
        model.delete()
        advanceUntilIdle()
        assertFalse(model.state.value.deleted)

        api.refusing.clear()
        model.delete()
        advanceUntilIdle()
        assertTrue(model.state.value.deleted)
        assertTrue(api.items.none { it.id == HomeSamples.sofa.id })
    }

    // A new item.

    @Test
    fun `a new item from a purchase links the transaction, then uploads its photo`() = runTest(dispatcher) {
        val draft = ItemDraft.fromPurchase("Air fryer", -129.99, HomeSamples.TODAY, roomId = HomeSamples.kitchen.id)
        val model = NewItemViewModel(api, documents, draft, transactionId = HomeSamples.METRO_TRANSACTION)
        advanceUntilIdle()
        assertEquals(4, model.state.value.rooms.size)

        model.photo(UploadFile("photo.jpg", DocumentsSamples.jpegBytes))
        model.save(draft)
        advanceUntilIdle()

        val sent = api.sent.first { it.first == "create" }.second as NewItem
        assertEquals("Air fryer", sent.name)
        assertEquals(129.99, sent.purchasePrice!!, 0.0)
        assertEquals("2026-10-03", sent.purchasedOn)
        assertEquals(HomeSamples.METRO_TRANSACTION, sent.links.single().targetId)
        val created = api.items.last()
        assertEquals(created.id, model.state.value.created)
        assertNull(model.state.value.notice)
        assertTrue(documents.uploads.single().contains("title" to "Air fryer photo"))
        assertEquals(2, created.links.size)
    }

    @Test
    fun `a photo that does not go leaves the item, with a notice`() = runTest(dispatcher) {
        documents.failing += "upload"
        val model = NewItemViewModel(api, documents, ItemDraft(name = "Lamp"))
        model.photo(UploadFile("photo.jpg", DocumentsSamples.jpegBytes))
        model.save(ItemDraft(name = "Lamp"))
        advanceUntilIdle()

        assertNotNull(model.state.value.created)
        assertEquals("The item was added, but the photo could not be uploaded. Add it again on this page.", model.state.value.notice)
    }

    @Test
    fun `a refused new item says why, and stays on the form`() = runTest(dispatcher) {
        api.refusing["create"] = ApiError.Http(400, "Bad request", null, null, mapOf("name" to listOf("The name is too long.")))
        val model = NewItemViewModel(api, documents, ItemDraft(name = "Lamp"))
        model.save(ItemDraft(name = "Lamp"))
        advanceUntilIdle()

        assertNull(model.state.value.created)
        assertEquals("The name is too long.", model.state.value.error)
    }
}
