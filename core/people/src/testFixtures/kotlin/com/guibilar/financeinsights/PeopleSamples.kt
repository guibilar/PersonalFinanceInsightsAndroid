package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.people.Birthday
import com.guibilar.financeinsights.core.people.GiftIdea
import com.guibilar.financeinsights.core.people.Interaction
import com.guibilar.financeinsights.core.people.Loan
import com.guibilar.financeinsights.core.people.LoanDirection
import com.guibilar.financeinsights.core.people.Person
import com.guibilar.financeinsights.core.people.PersonSummary
import java.time.LocalDate

/**
 * People as the server gives them, on Saturday, October 3, 2026 ([TODAY], the day of the other samples). Marco is a
 * friend with a 6-week reminder and his last contact on Aug 15 (overdue), a birthday on Oct 22 (in 19 days), two gift
 * ideas and a given gift, and a camping stove lent to him (due Aug 1, not back). Ana is family, contacted 2 days ago.
 */
object PeopleSamples {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 3)

    const val MARCO = "p1"
    const val ANA = "p2"
    const val STOVE = "loan1"

    private const val CREATED = "2026-01-10T10:00:00Z"

    val marcoSummary = PersonSummary(
        id = MARCO,
        name = "Marco Silva",
        relationship = "Friend",
        groups = listOf("gaming group"),
        birthday = Birthday(10, 22, 1990),
        nextBirthday = "2026-10-22",
        email = "marco@example.com",
        contactEveryDays = 42,
        lastContactOn = "2026-08-15",
        contactDueOn = "2026-09-26",
        contactOverdue = true,
        createdAt = CREATED,
        updatedAt = CREATED,
    )

    val anaSummary = PersonSummary(
        id = ANA,
        name = "Ana Lima",
        groups = listOf("family"),
        lastContactOn = "2026-10-01",
        createdAt = CREATED,
        updatedAt = CREATED,
    )

    val stove = Loan(
        id = STOVE,
        personId = MARCO,
        personName = "Marco Silva",
        itemName = "Camping stove",
        direction = LoanDirection.Lent,
        lentOn = "2026-07-04",
        dueOn = "2026-08-01",
        overdue = true,
    )

    val marco = Person(
        person = marcoSummary,
        interactions = listOf(
            Interaction("c1", MARCO, "2026-08-15", "Coffee", "Talked about his new job."),
        ),
        interactionCount = 1,
        giftIdeas = listOf(
            GiftIdea("g1", MARCO, "Pour-over coffee kettle", price = 49.5, occasion = "Birthday"),
            GiftIdea("g2", MARCO, "Hiking day pack"),
            GiftIdea("g3", MARCO, "Board game", price = 64.0, occasion = "Christmas", givenOn = "2025-12-25"),
        ),
        loans = listOf(stove),
    )

    val ana = Person(
        person = anaSummary,
        interactions = listOf(Interaction("c2", ANA, "2026-10-01", "Call")),
        interactionCount = 1,
    )

    val people = listOf(marco, ana)
}
