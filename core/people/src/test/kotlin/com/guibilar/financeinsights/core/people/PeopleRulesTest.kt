package com.guibilar.financeinsights.core.people

import com.guibilar.financeinsights.PeopleSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeopleRulesTest {
    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun `makes initials and group labels, and reads typed groups`() {
        assertEquals("MS", initialsOf("Marco Silva"))
        assertEquals("AL", initialsOf("  ana maria  lima "))
        assertEquals("P", initialsOf("Priya"))
        assertEquals("?", initialsOf(" "))
        assertEquals("Gaming group", groupLabel("gaming group"))
        assertEquals(listOf("family", "gaming group"), parseGroups(" Family, gaming   Group ,family,, "))
    }

    @Test
    fun `says how long ago a day was`() {
        assertEquals("today", agoText(today, today))
        assertEquals("yesterday", agoText(today.minusDays(1), today))
        assertEquals("5 days ago", agoText(today.minusDays(5), today))
        assertEquals("6 weeks ago", agoText(today.minusDays(42), today))
        assertEquals("3 months ago", agoText(today.minusDays(92), today))
        assertEquals("3 years ago", agoText(today.minusYears(3), today))
        assertEquals("No contact yet", lastContactText(null, today))
        assertEquals("Last contact 6 weeks ago", lastContactText("2026-09-02", today))
    }

    @Test
    fun `writes reminders, birthdays, and upcoming lines`() {
        assertEquals("Every 6 weeks", reminderText(42))
        assertEquals("Every 3 weeks", reminderText(21))
        assertEquals("Every 10 days", reminderText(10))
        assertEquals("None", reminderText(null))
        assertEquals("Nov 2", birthdayText(Birthday(11, 2)))
        assertEquals("Feb 29, 1996", birthdayText(Birthday(2, 29, 1996)))
        assertEquals("In 19 days · turns 36 · 2 gift ideas", upcomingText(UpcomingBirthday("p", "Marco", "2026-11-02", 19, 36, 2)))
        assertEquals("Today", upcomingText(UpcomingBirthday("p", "Ana", "2026-10-14", 0)))
        assertEquals("NOV", monthShort(LocalDate.of(2026, 11, 2)))
        assertEquals("Lent to Marco Silva · due Aug 1, 2026", loanText(PeopleSamples.stove))
        assertEquals("Friend · Gaming group", groupsLine(PeopleSamples.marcoSummary))
    }

    @Test
    fun `checks the person form as the server does`() {
        assertEquals(setOf("name"), PersonDraft().check(today).keys)
        assertTrue(PersonDraft(name = "Ana").check(today).isEmpty())
        assertEquals(setOf("birthday"), PersonDraft(name = "A", birthMonth = 2, birthDay = "30").check(today).keys)
        assertEquals(setOf("birthday"), PersonDraft(name = "A", birthMonth = 2, birthDay = "29", birthYear = "2023").check(today).keys)
        assertTrue(PersonDraft(name = "A", birthMonth = 2, birthDay = "29").check(today).isEmpty())
        assertEquals(setOf("birthday"), PersonDraft(name = "A", birthYear = "1990").check(today).keys)
        assertEquals(setOf("birthday"), PersonDraft(name = "A", birthMonth = 12, birthDay = "1", birthYear = "2026").check(today).keys)
        // A year that no date can hold is refused, not a crash.
        assertEquals(setOf("birthday"), PersonDraft(name = "A", birthMonth = 5, birthDay = "1", birthYear = "1000000000").check(today).keys)
        // A year before 1900 is refused for what it is, not as one in the future (the server's words).
        assertEquals(
            mapOf("birthday" to "The year must be from 1900 to 2026."),
            PersonDraft(name = "A", birthMonth = 5, birthDay = "1", birthYear = "1850").check(today),
        )
        assertEquals(setOf("email"), PersonDraft(name = "A", email = "not an email").check(today).keys)
        assertEquals(setOf("groups"), PersonDraft(name = "A", groups = "a|b").check(today).keys)
    }

    @Test
    fun `sends the form cleaned, and fills it from a person`() {
        val details = PersonDraft(name = " Marco ", groups = "Friends, Gaming group", birthMonth = 11, birthDay = "2", contactEveryDays = 42).details()
        assertEquals(
            PersonDetails("Marco", groups = listOf("friends", "gaming group"), birthday = Birthday(11, 2), contactEveryDays = 42),
            details,
        )
        val draft = PersonDraft.of(PeopleSamples.marco)
        assertEquals("Gaming group", draft.groups)
        assertEquals(10, draft.birthMonth)
        assertEquals("1990", draft.birthYear)
        assertEquals(PeopleSamples.marco.person.birthday, draft.details().birthday)
    }

    @Test
    fun `marks a gift as given and a loan as back, and sorts the history`() {
        val gift = PeopleSamples.marco.giftIdeas.first()
        assertEquals("2026-10-14", gift.given(today).givenOn)
        assertNull(gift.given(null).givenOn)
        val back = PeopleSamples.stove.returned(today)
        assertEquals("2026-10-14", back.returnedOn)
        assertEquals("2026-08-01", back.dueOn)
        assertEquals("2026-07-04", PeopleSamples.stove.returned(LocalDate.of(2026, 7, 1)).returnedOn)
        assertEquals(listOf("c-c1", "l-loan1"), historyOf(PeopleSamples.marco).map { it.key })
    }
}
