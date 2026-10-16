package com.guibilar.financeinsights

import org.junit.Assert.assertEquals
import org.junit.Test

/** T101: what a shared file is, by its type, or by its name when the type is vague. */
class SharedKindTest {
    @Test
    fun `each file goes where it belongs`() {
        assertEquals(SharedKind.Calendar, sharedKind("text/calendar", "invite.ics"))
        assertEquals(SharedKind.Calendar, sharedKind("application/octet-stream", "invite.ics"))
        assertEquals(SharedKind.Contacts, sharedKind("text/x-vcard", "contacts.vcf"))
        assertEquals(SharedKind.Statement, sharedKind("text/comma-separated-values", "may.csv"))
        assertEquals(SharedKind.Pdf, sharedKind("application/pdf", "bill.pdf"))
        assertEquals(SharedKind.Image, sharedKind("image/jpeg", "IMG_1.jpg"))
        assertEquals(SharedKind.Image, sharedKind(null, "photo.HEIC"))
        assertEquals(SharedKind.Other, sharedKind("application/zip", "x.zip"))
    }
}
