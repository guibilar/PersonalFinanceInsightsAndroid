package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.home.Item
import com.guibilar.financeinsights.core.home.ItemDocument
import com.guibilar.financeinsights.core.home.ItemLink
import com.guibilar.financeinsights.core.home.ItemLinkKinds
import com.guibilar.financeinsights.core.home.ItemTask
import com.guibilar.financeinsights.core.home.Room
import java.time.LocalDate

/**
 * Rooms and items as the server gives them, on Saturday, October 3, 2026 ([TODAY], the day of the other samples). The
 * dishwasher links to its warranty ("d2" of `DocumentsSamples`) and has a monthly maintenance task; its warranty ends
 * in 17 days. The laptop links to the METRO 221 transaction of the recorded finance responses; its warranty ended.
 */
object HomeSamples {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 3)

    /** The dishwasher warranty of the document samples. */
    const val WARRANTY_DOCUMENT = "d2"

    /** A photo of the dishwasher (not in the document samples: a test adds it when it needs the file). */
    const val PHOTO_DOCUMENT = "d9"

    /** The METRO 221 transaction of the recorded finance responses (Oct 3, 2026, $41.92). */
    const val METRO_TRANSACTION = "01a0ff22-674a-7e98-bb70-9dab0d96bb50"

    /** The monthly maintenance task of the dishwasher. */
    const val FILTER_TASK = "t20"

    /** The yearly maintenance task of the smoke detectors. */
    const val BATTERY_TASK = "t21"

    private const val CREATED = "2026-09-20T10:00:00Z"

    val kitchen = Room("r1", "Kitchen", 1, createdAt = CREATED, updatedAt = CREATED)
    val office = Room("r2", "Office", 2, createdAt = CREATED, updatedAt = CREATED)
    val livingRoom = Room("r3", "Living room", 3, createdAt = CREATED, updatedAt = CREATED)
    val bedroom = Room("r4", "Bedroom", 4, createdAt = CREATED, updatedAt = CREATED)

    val dishwasher = Item(
        id = "i1",
        name = "Dishwasher",
        roomId = "r1",
        roomName = "Kitchen",
        category = "Appliance",
        brand = "Bosch",
        model = "SHX78",
        serialNumber = "FD 9811 04532",
        purchasedOn = "2024-11-02",
        purchasePrice = 899.99,
        value = 899.99,
        warrantyUntil = "2026-10-20",
        notes = "Installed by Ray's Appliances.",
        tags = listOf("kitchen"),
        createdAt = CREATED,
        updatedAt = CREATED,
        links = listOf(
            ItemLink("li1", ItemLinkKinds.DOCUMENT, WARRANTY_DOCUMENT, CREATED),
            ItemLink("li2", ItemLinkKinds.DOCUMENT, PHOTO_DOCUMENT, CREATED),
        ),
        documents = listOf(
            ItemDocument(PHOTO_DOCUMENT, "Dishwasher photo", "Other"),
            ItemDocument(WARRANTY_DOCUMENT, "Dishwasher warranty", "Warranty", "2026-11-02"),
        ),
        maintenance = listOf(
            ItemTask(FILTER_TASK, "Clean the dishwasher filter", "Open", "2026-10-25", 1, "Months", "2026-09-25T18:00:00Z"),
        ),
    )

    val espresso = Item(
        id = "i2",
        name = "Espresso machine",
        roomId = "r1",
        roomName = "Kitchen",
        category = "Appliance",
        brand = "Breville",
        model = "BES870",
        purchasedOn = "2025-12-20",
        purchasePrice = 749.0,
        currentValue = 500.0,
        value = 500.0,
        warrantyUntil = "2027-12-20",
        createdAt = CREATED,
        updatedAt = CREATED,
    )

    val laptop = Item(
        id = "i3",
        name = "Laptop",
        roomId = "r2",
        roomName = "Office",
        category = "Electronics",
        brand = "Lenovo",
        model = "ThinkPad X1",
        serialNumber = "PF3K2Z",
        purchasedOn = "2025-03-10",
        purchasePrice = 1899.0,
        currentValue = 1200.0,
        value = 1200.0,
        warrantyUntil = "2026-09-01",
        tags = listOf("work"),
        createdAt = CREATED,
        updatedAt = CREATED,
        links = listOf(ItemLink("li3", ItemLinkKinds.TRANSACTION, METRO_TRANSACTION, CREATED)),
    )

    val chair = Item(
        id = "i4",
        name = "Desk chair",
        roomId = "r2",
        roomName = "Office",
        category = "Furniture",
        brand = "Herman Miller",
        model = "Aeron",
        purchasedOn = "2023-06-01",
        purchasePrice = 1450.0,
        value = 1450.0,
        createdAt = CREATED,
        updatedAt = CREATED,
    )

    val sofa = Item(
        id = "i5",
        name = "Sofa",
        roomId = "r3",
        roomName = "Living room",
        category = "Furniture",
        purchasePrice = 1200.0,
        value = 1200.0,
        createdAt = CREATED,
        updatedAt = CREATED,
    )

    val smokeDetectors = Item(
        id = "i6",
        name = "Smoke detectors",
        category = "Safety",
        brand = "Kidde",
        quantity = 3,
        purchasePrice = 39.99,
        value = 119.97,
        createdAt = CREATED,
        updatedAt = CREATED,
        maintenance = listOf(ItemTask(BATTERY_TASK, "Smoke detector batteries", "Open", "2026-10-09", 12, "Months")),
    )

    val bed = Item(
        id = "i7",
        name = "Bed frame",
        roomId = "r4",
        roomName = "Bedroom",
        category = "Furniture",
        purchasePrice = 650.0,
        value = 650.0,
        createdAt = CREATED,
        updatedAt = CREATED,
    )

    /** In list order. */
    val rooms = listOf(kitchen, office, livingRoom, bedroom)

    val items = listOf(dishwasher, espresso, laptop, chair, sofa, smokeDetectors, bed)
}
