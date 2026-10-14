package com.guibilar.financeinsights.core.home

import com.guibilar.financeinsights.FakeHomeApi
import com.guibilar.financeinsights.HomeSamples
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeRulesTest {
    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun `the warranty state follows the 30 days of the server`() {
        assertEquals(Warranty.None, warrantyOf(null, today))
        assertEquals(Warranty.Ended(LocalDate.of(2026, 10, 2)), warrantyOf("2026-10-02", today))
        assertEquals(Warranty.EndsSoon(today, 0), warrantyOf("2026-10-03", today))
        assertEquals(Warranty.EndsSoon(LocalDate.of(2026, 11, 2), 30), warrantyOf("2026-11-02", today))
        assertEquals(Warranty.Active(LocalDate.of(2026, 11, 3)), warrantyOf("2026-11-03", today))
    }

    @Test
    fun `the warranty in words`() {
        assertEquals("Under warranty until Dec 20, 2027", warrantyText(warrantyOf("2027-12-20", today)))
        assertEquals("Warranty ends in 17 days", warrantyText(warrantyOf("2026-10-20", today)))
        assertEquals("Warranty ends tomorrow", warrantyText(warrantyOf("2026-10-04", today)))
        assertEquals("Warranty ends today", warrantyText(warrantyOf("2026-10-03", today)))
        assertEquals("Warranty ended Sep 1, 2026", warrantyText(warrantyOf("2026-09-01", today)))
        assertNull(warrantyText(Warranty.None))
        // A list shows only the warranties that need a look.
        assertNull(warrantyBadge(warrantyOf("2027-12-20", today)))
        assertEquals("Warranty ends in 17 days", warrantyBadge(warrantyOf("2026-10-20", today)))
        assertEquals("Warranty ended", warrantyBadge(warrantyOf("2026-09-01", today)))
    }

    @Test
    fun `short labels`() {
        assertEquals("1 item", itemsText(1))
        assertEquals("38 items", itemsText(38))
        assertEquals("No room", roomLabel(null))
        assertEquals("Kitchen", roomLabel("Kitchen"))
        assertEquals("Bosch SHX78 · Appliance", itemSubtitle(HomeSamples.dishwasher.summary))
        assertEquals("No room · Kidde · Safety · × 3", itemSubtitle(HomeSamples.smokeDetectors.summary, withRoom = true))
        assertEquals("Today", daysLeftText("2026-10-03", today))
        assertEquals("1 day", daysLeftText("2026-10-04", today))
        assertEquals("22 days", daysLeftText("2026-10-25", today))
        assertEquals("3 days late", daysLeftText("2026-09-30", today))
        assertNull(daysLeftText(null, today))
        assertEquals("Every month", repeatText(1, "Months"))
        assertEquals("Every 12 months", repeatText(12, "Months"))
        assertNull(repeatText(null, null))
        assertEquals("home-inventory-2026-10-03.csv", exportFileName(today))
    }

    @Test
    fun `amounts as people type them`() {
        assertNull(parseAmount(" "))
        assertEquals(899.99, parseAmount("899.99")!!, 0.0)
        assertEquals(1299.0, parseAmount("$1,299")!!, 0.0)
        assertEquals(1299.5, parseAmount("1,299.50")!!, 0.0)
        assertEquals(12.5, parseAmount("12,50")!!, 0.0)
        assertEquals(10.01, parseAmount("10.005")!!, 0.0)
        assertThrows(AmountError::class.java) { parseAmount("-5") }
        assertThrows(AmountError::class.java) { parseAmount("ten") }
        assertThrows(AmountError::class.java) { parseAmount("10000000000") }
        assertEquals("899.99", amountText(899.99))
        assertEquals("1200", amountText(1200.0))
        assertEquals("12.50", amountText(12.5))
        assertEquals("", amountText(null))
    }

    @Test
    fun `the item form checks the name, the quantity and the amounts`() {
        val empty = ItemDraft()
        assertEquals("Enter a name.", empty.nameError)
        assertFalse(empty.canSave)
        val draft = ItemDraft(name = "Sofa", quantity = "0", purchasePrice = "abc")
        assertEquals("Use 1 to 100000.", draft.quantityError)
        assertEquals("Enter an amount, for example 899.99.", draft.purchasePriceError)
        assertTrue(ItemDraft(name = "Sofa", quantity = "", purchasePrice = "1,200").canSave)
        assertEquals("A name can have at most 200 characters.", ItemDraft(name = "x".repeat(201)).nameError)
    }

    @Test
    fun `the form sends all details, empty ones as nothing`() {
        val details = ItemDraft(name = " Sofa ", category = " ", quantity = "", purchasePrice = "1,200", tags = "Living, living ,Big").toDetails()
        assertEquals("Sofa", details.name)
        assertNull(details.category)
        assertEquals(1, details.quantity)
        assertEquals(1200.0, details.purchasePrice!!, 0.0)
        assertEquals(listOf("living", "big"), details.tags)

        // Back from an item: the same details.
        assertEquals(HomeSamples.dishwasher.details, ItemDraft.of(HomeSamples.dishwasher).toDetails())
    }

    @Test
    fun `a purchase fills the price and the day, but not over what was typed`() {
        val day = LocalDate.of(2026, 10, 3)
        val fromMoney = ItemDraft.fromPurchase("METRO 221", -41.92, day, roomId = "r1")
        assertEquals("METRO 221", fromMoney.name)
        assertEquals("41.92", fromMoney.purchasePrice)
        assertEquals(day, fromMoney.purchasedOn)
        assertEquals("r1", fromMoney.roomId)

        val typed = ItemDraft(name = "Laptop", purchasePrice = "1899").withPurchase(-41.92, day)
        assertEquals("1899", typed.purchasePrice)
        assertEquals(day, typed.purchasedOn)
        assertEquals("41.92", ItemDraft(name = "Laptop").withPurchase(-41.92, null).purchasePrice)
    }

    @Test
    fun `room names`() {
        assertEquals("Enter a name.", roomNameError(" "))
        assertNull(roomNameError("Garage"))
        assertEquals("A name can have at most 100 characters.", roomNameError("x".repeat(101)))
    }

    @Test
    fun `the fake answers as the server`() = runTest {
        val api = FakeHomeApi()
        val summary = api.summary()
        assertEquals(7, summary.itemCount)
        assertEquals(6019.96, summary.totalValue, 0.001)
        assertEquals(listOf("Kitchen", "Office", "Living room", "Bedroom", null), summary.rooms.map { it.name })
        assertEquals(1, summary.warrantiesEnding)
        assertEquals(listOf("Dishwasher", "Espresso machine"), api.items(room = "r1").items.map { it.name })
        assertEquals(listOf("Smoke detectors"), api.items(room = NO_ROOM).items.map { it.name })
        assertEquals(listOf("Laptop"), api.items(warranty = WarrantyFilter.EXPIRED).items.map { it.name })
        assertEquals(listOf("Dishwasher"), api.byLink(ItemLinkKinds.TASK, HomeSamples.FILTER_TASK).map { it.name })
        // UTF-8 with a byte order mark, so a spreadsheet reads the accents.
        val csv = api.export().bytes()
        assertEquals(listOf(0xEF, 0xBB, 0xBF), csv.take(3).map { it.toInt() and 0xFF })
        assertTrue(String(csv, Charsets.UTF_8).contains("Kitchen,Dishwasher"))
    }
}
