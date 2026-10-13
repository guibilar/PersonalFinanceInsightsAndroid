package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentFile
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.DocumentLink
import com.guibilar.financeinsights.core.documents.DocumentLinkKinds
import com.guibilar.financeinsights.core.documents.OcrStatus
import java.time.LocalDate

/**
 * Documents as the server gives them, on Saturday, October 3, 2026 ([TODAY], the day of the other samples). The
 * receipt links to "METRO 221", a transaction of the recorded finance responses (`FakeFinanceApi`).
 */
object DocumentsSamples {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 3)

    /** The METRO 221 transaction of the recorded finance responses (Oct 3, 2026, $41.92). */
    const val METRO_TRANSACTION = "01a0ff22-674a-7e98-bb70-9dab0d96bb50"

    /** The smallest JPEG start that the type check knows. */
    val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)

    private fun pdf(id: String, name: String, size: Long) =
        DocumentFile(id, name, "application/pdf", size, "a1b2", "2026-09-01T12:00:00Z")

    private fun jpeg(id: String, name: String, size: Long) =
        DocumentFile(id, name, "image/jpeg", size, "c3d4", "2026-09-28T18:30:00Z")

    val carInsurance = Document(
        id = "d1",
        title = "Car insurance policy",
        kind = DocumentKind.Insurance,
        tags = listOf("car"),
        issuedOn = "2025-10-25",
        expiresOn = "2026-10-25",
        notes = "Renew online before the end of the month.",
        createdAt = "2025-10-26T10:00:00Z",
        updatedAt = "2025-10-26T10:00:00Z",
        ocrStatus = OcrStatus.Done,
        ocrText = "Auto insurance policy\nPolicy number 4471-22\nTerm: Oct 25, 2025 to Oct 25, 2026",
        files = listOf(pdf("f1", "policy.pdf", 418_000)),
    )

    val dishwasher = Document(
        id = "d2",
        title = "Dishwasher warranty",
        kind = DocumentKind.Warranty,
        tags = listOf("kitchen", "appliance"),
        issuedOn = "2024-11-02",
        expiresOn = "2026-11-02",
        createdAt = "2024-11-03T09:00:00Z",
        updatedAt = "2024-11-03T09:00:00Z",
        ocrStatus = OcrStatus.Done,
        ocrText = "Limited warranty, 2 years parts and labour.",
        files = listOf(pdf("f2", "warranty.pdf", 1_250_000)),
    )

    val receipt = Document(
        id = "d3",
        title = "Metro receipt",
        kind = DocumentKind.Receipt,
        tags = listOf("groceries"),
        issuedOn = "2026-10-03",
        createdAt = "2026-10-03T14:10:00Z",
        updatedAt = "2026-10-03T14:10:00Z",
        ocrStatus = OcrStatus.Done,
        ocrText = "METRO 221\nTOTAL 41.92",
        files = listOf(jpeg("f3", "receipt.jpg", 820_000)),
        links = listOf(DocumentLink("l1", DocumentLinkKinds.MONEY_TRANSACTION, METRO_TRANSACTION, "2026-10-03T14:11:00Z")),
    )

    val taxSlip = Document(
        id = "d4",
        title = "T4 slip 2025",
        kind = DocumentKind.Tax,
        tags = listOf("2025"),
        issuedOn = "2026-02-20",
        createdAt = "2026-10-03T13:00:00Z",
        updatedAt = "2026-10-03T13:00:00Z",
        ocrStatus = OcrStatus.Pending,
        files = listOf(pdf("f4", "t4.pdf", 96_000)),
    )

    val lease = Document(
        id = "d5",
        title = "Apartment lease",
        kind = DocumentKind.Lease,
        tags = listOf("home"),
        issuedOn = "2026-02-01",
        expiresOn = "2027-01-31",
        createdAt = "2026-02-02T08:00:00Z",
        updatedAt = "2026-02-02T08:00:00Z",
        ocrStatus = OcrStatus.Failed,
        ocrError = "page-2.heic: the image could not be read.",
        files = listOf(pdf("f5", "lease.pdf", 2_400_000), jpeg("f6", "page-2.jpg", 1_100_000)),
    )

    /** Newest first, as the list answers. */
    val all = listOf(receipt, taxSlip, lease, carInsurance, dishwasher)
}
