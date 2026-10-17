package com.guibilar.financeinsights.core.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DocumentRulesTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun text(value: String) = value.toByteArray(Charsets.US_ASCII)

    @Test
    fun `finds the type in the first bytes, as the server does`() {
        assertEquals(FileType.Pdf, FileType.of(text("%PDF-1.4\n")))
        assertEquals(FileType.Jpeg, FileType.of(bytes(0xFF, 0xD8, 0xFF, 0xE1, 0, 0)))
        assertEquals(FileType.Png, FileType.of(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(FileType.WebP, FileType.of(text("RIFF") + bytes(0, 0, 0, 0) + text("WEBPVP8 ")))
        assertEquals(FileType.Heic, FileType.of(bytes(0, 0, 0, 0x18) + text("ftypheic") + bytes(0, 0, 0, 0)))
        assertEquals(FileType.Heic, FileType.of(bytes(0, 0, 0, 0x18) + text("ftypmif1")))
        assertNull(FileType.of(bytes(0, 0, 0, 0x18) + text("ftypmp42")))
        assertNull(FileType.of(text("id,amount\n1,2")))
        assertNull(FileType.of(ByteArray(0)))
    }

    @Test
    fun `checks the files before the upload`() {
        val pdf = UploadFile("a.pdf", text("%PDF-1.7"))
        assertNull(uploadProblem(listOf(pdf)))
        assertEquals("Add a file: a scan, a photo or a PDF.", uploadProblem(emptyList()))
        assertEquals(
            "Only PDF, JPEG, PNG, HEIC and WebP files can go: notes.txt.",
            uploadProblem(listOf(pdf, UploadFile("notes.txt", text("hello")))),
        )
        val big = UploadFile("big.pdf", text("%PDF") + ByteArray(MAX_FILE_BYTES.toInt()))
        assertEquals("Larger than 25 MB: big.pdf.", uploadProblem(listOf(big)))
        assertEquals("A document can have at most 10 files.", uploadProblem(List(11) { pdf }))
        val twentyMb = UploadFile("part.pdf", text("%PDF") + ByteArray(21 * 1024 * 1024))
        assertEquals("All files together are larger than 100 MB.", uploadProblem(List(5) { twentyMb }))
    }

    @Test
    fun `reads tags as typed`() {
        assertEquals(listOf("kitchen", "warranty"), parseTags(" Kitchen, warranty ,, kitchen "))
        assertEquals(emptyList<String>(), parseTags("  "))
    }

    @Test
    fun `says sizes as people read them`() {
        assertEquals("900 B", formatSize(900))
        assertEquals("820 KB", formatSize(820L * 1024))
        assertEquals("2.3 MB", formatSize(2_400_000))
        assertEquals("25 MB", formatSize(25L * 1024 * 1024))
        // Just under 1 MB rounds to 1 MB, not "1024 KB".
        assertEquals("1023 KB", formatSize(1_048_063))
        assertEquals("1 MB", formatSize(1_048_064))
        assertEquals("1 MB", formatSize(1_048_575))
    }

    @Test
    fun `scales a large photo to 2500 pixels on its long side, and keeps a small one`() {
        assertEquals(2500 to 1875, scaledSize(4000, 3000))
        assertEquals(1875 to 2500, scaledSize(3000, 4000))
        assertEquals(2500 to 1, scaledSize(10000, 1))
        assertEquals(1200 to 900, scaledSize(1200, 900))
        assertEquals(2500 to 2500, scaledSize(2500, 2500))
    }

    @Test
    fun `counts the days to the expiry`() {
        val today = LocalDate.of(2026, 10, 3)
        assertEquals(22L, daysUntil("2026-10-25", today))
        assertEquals(0L, daysUntil("2026-10-03", today))
        assertEquals(-2L, daysUntil("2026-10-01", today))
        assertNull(daysUntil(null, today))
        assertNull(daysUntil("soon", today))
        assertEquals("Expired", expiryLabel(-2))
        assertEquals("Today", expiryLabel(0))
        assertEquals("Tomorrow", expiryLabel(1))
        assertEquals("22 days", expiryLabel(22))
        assertEquals("2026-10-03" to "2026-11-02", expireSoonRange(today))
    }

    @Test
    fun `a document gives its details and its transaction links`() {
        val document = Document(
            id = "d1",
            title = "Lease",
            kind = DocumentKind.Lease,
            tags = listOf("home"),
            expiresOn = "2027-01-31",
            links = listOf(
                DocumentLink("l1", DocumentLinkKinds.MONEY_TRANSACTION, "t1"),
                DocumentLink("l2", "Home.Item", "i1"),
            ),
        )
        assertEquals(DocumentDetails("Lease", DocumentKind.Lease, listOf("home"), null, "2027-01-31", null), document.details)
        assertEquals(listOf("t1"), document.transactionLinks.map { it.targetId })
        assertTrue(document.summary.links.size == 2)
    }
}
