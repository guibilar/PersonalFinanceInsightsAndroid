package com.guibilar.financeinsights.core.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeopleImportRulesTest {
    @Test
    fun `birthdays in the forms that phones keep`() {
        assertEquals(Birthday(10, 20, 1990), phoneBirthday("1990-10-20"))
        assertEquals(Birthday(10, 20, 1990), phoneBirthday("19901020"))
        assertEquals(Birthday(10, 20), phoneBirthday("--10-20"))
        assertEquals(Birthday(10, 20), phoneBirthday("--1020"))
        assertEquals(Birthday(10, 20), phoneBirthday("1604-10-20"))
        assertEquals(Birthday(10, 20, 1990), phoneBirthday("1990-10-20T00:00:00Z"))
        assertEquals(Birthday(2, 29), phoneBirthday("--02-29"))
        assertNull(phoneBirthday("1990-02-30"))
        assertNull(phoneBirthday("soon"))
    }

    @Test
    fun `says what the import does`() {
        val ana = PersonDetails("Ana Souza")
        assertEquals("New person", importActionText(PeopleImportItem(0, "Add", ana)))
        assertEquals("Adds email, birthday to Ana S.", importActionText(PeopleImportItem(0, "Merge", ana, "p1", "Ana S.", "phone", listOf("email", "birthday"))))
        assertEquals("Already here as Ana S.", importActionText(PeopleImportItem(0, "Same", ana, "p1", "Ana S.")))
        assertEquals("Enter a name.", importActionText(PeopleImportItem(0, "Skip", ana, reason = "Enter a name.")))
        assertEquals("2 new, 1 to update, 3 already here, 1 skipped", importCounts(PeopleImportResult(added = 2, merged = 1, unchanged = 3, skipped = 1)))
    }
}
