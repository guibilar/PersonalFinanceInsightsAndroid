package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.people.GiftDetails
import com.guibilar.financeinsights.core.people.GiftIdea
import com.guibilar.financeinsights.core.people.GroupCount
import com.guibilar.financeinsights.core.people.Interaction
import com.guibilar.financeinsights.core.people.InteractionDetails
import com.guibilar.financeinsights.core.people.Loan
import com.guibilar.financeinsights.core.people.LoanDetails
import com.guibilar.financeinsights.core.people.LoanStatus
import com.guibilar.financeinsights.core.people.NewPersonLink
import com.guibilar.financeinsights.core.people.PeopleApi
import com.guibilar.financeinsights.core.people.PeopleSummary
import com.guibilar.financeinsights.core.people.Person
import com.guibilar.financeinsights.core.people.PersonDetails
import com.guibilar.financeinsights.core.people.PersonLink
import com.guibilar.financeinsights.core.people.PersonPage
import com.guibilar.financeinsights.core.people.PersonSummary
import com.guibilar.financeinsights.core.people.UpcomingBirthday
import com.guibilar.financeinsights.core.people.peopleDay
import java.io.IOException
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/**
 * The People API on people in memory, as the server keeps them: people by name, the last contact and the reminder
 * computed, gift ideas and loans kept. "Today" is [today]. Each call is recorded, and any can be made to fail.
 */
class FakePeopleApi(
    people: List<Person> = PeopleSamples.people,
    var today: LocalDate = PeopleSamples.TODAY,
) : PeopleApi {
    val people = people.toMutableList()

    /** Calls (by name) that fail as if the server were unreachable. */
    val failing = mutableSetOf<String>()

    /** Calls (by name) answered with this API error instead. */
    val refusing = mutableMapOf<String, ApiError.Http>()

    /** Every call made, by name, with what was sent. */
    val sent = mutableListOf<Pair<String, Any?>>()

    private var nextId = 100

    private fun <T> call(name: String, body: Any?, answer: () -> T): T {
        sent += name to body
        if (name in failing) throw IOException("offline")
        refusing[name]?.let { throw it }
        return answer()
    }

    private fun notFound(id: String) = ApiError.Http(404, "Not found", "No $id.", null, emptyMap())

    private fun find(id: String) = people.firstOrNull { it.person.id == id } ?: throw notFound(id)

    private fun id(prefix: String) = "$prefix${nextId++}"

    /** The person with the last contact, the reach-out day, and the loan states as the server computes them. */
    private fun computed(person: Person): Person {
        val last = person.interactions.maxOfOrNull { it.occurredOn }
        val start = peopleDay(last) ?: peopleDay(person.person.createdAt) ?: today
        val due = person.person.contactEveryDays?.let { start.plusDays(it.toLong()) }
        val next = person.person.birthday?.let { b ->
            val day = MonthDay.of(b.month, minOf(b.day, if (b.month == 2) 28 else b.day)).atYear(today.year)
            if (day.isBefore(today)) day.plusYears(1) else day
        }
        return person.copy(
            person = person.person.copy(
                lastContactOn = last,
                contactDueOn = due?.toString(),
                contactOverdue = due != null && due.isBefore(today),
                nextBirthday = next?.toString(),
            ),
            interactions = person.interactions.sortedByDescending { it.occurredOn },
            interactionCount = person.interactions.size,
            loans = person.loans.map { it.copy(overdue = it.returnedOn == null && (peopleDay(it.dueOn)?.isBefore(today) ?: false)) }
                .sortedWith(compareBy<Loan> { it.returnedOn != null }.thenBy { it.lentOn }),
            giftIdeas = person.giftIdeas.sortedWith(compareBy<GiftIdea> { it.givenOn != null }.thenByDescending { it.givenOn }),
        )
    }

    private fun save(person: Person): Person {
        val done = computed(person)
        people.replaceAll { if (it.person.id == done.person.id) done else it }
        return done
    }

    private fun owner(predicate: (Person) -> Boolean, id: String) = people.firstOrNull(predicate) ?: throw notFound(id)

    override suspend fun summary(): PeopleSummary = call("summary", null) {
        val all = people.map(::computed)
        val upcoming = all.mapNotNull { p ->
            val next = peopleDay(p.person.nextBirthday) ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(today, next).toInt()
            if (days > 30) return@mapNotNull null
            UpcomingBirthday(p.person.id, p.person.name, next.toString(), days, p.person.birthday?.year?.let { next.year - it },
                p.giftIdeas.count { !it.isGiven })
        }.sortedBy { it.date }
        val loans = all.flatMap { it.loans }.filter { it.isOpen }
        PeopleSummary(today.toString(), all.size, all.map { it.person }.filter { it.contactOverdue }.sortedBy { it.contactDueOn },
            upcoming, loans.size, loans.count { it.overdue })
    }

    override suspend fun groups(): List<GroupCount> = call("groups", null) {
        people.flatMap { it.person.groups }.groupingBy { it }.eachCount().toSortedMap().map { (name, count) -> GroupCount(name, count) }
    }

    override suspend fun people(group: String?, q: String?, sort: String?, page: Int, pageSize: Int): PersonPage =
        call("people", listOf(group, q, sort)) {
            val found = people.map { computed(it).person }
                .filter { group == null || group.lowercase() in it.groups }
                .filter { p -> q.isNullOrBlank() || q.lowercase().split(' ').all { word -> p.name.lowercase().contains(word) } }
                .sortedBy { it.name }
            PersonPage(found.drop((page - 1) * pageSize).take(pageSize), page, pageSize, found.size, found.size > page * pageSize)
        }

    override suspend fun person(id: String): Person = call("person", id) { computed(find(id)) }

    override suspend fun create(person: PersonDetails): Person = call("create", person) {
        val created = Person(
            PersonSummary(id("p"), person.name, person.nickname, person.relationship, person.groups, person.birthday,
                email = person.email, phone = person.phone, contactEveryDays = person.contactEveryDays,
                createdAt = "${today}T12:00:00Z"),
            notes = person.notes,
        )
        people += created
        save(created)
    }

    override suspend fun update(id: String, details: PersonDetails): Person = call("update", id to details) {
        val old = find(id)
        save(old.copy(
            person = old.person.copy(name = details.name, nickname = details.nickname, relationship = details.relationship,
                groups = details.groups, birthday = details.birthday, email = details.email, phone = details.phone,
                contactEveryDays = details.contactEveryDays),
            notes = details.notes,
        ))
    }

    override suspend fun delete(id: String) = call("delete", id) {
        find(id)
        people.removeAll { it.person.id == id }
        Unit
    }

    override suspend fun logInteraction(personId: String, interaction: InteractionDetails): Interaction =
        call("logInteraction", personId to interaction) {
            val person = find(personId)
            val created = Interaction(id("c"), personId, interaction.occurredOn ?: today.toString(), interaction.kind, interaction.note)
            save(person.copy(interactions = person.interactions + created))
            created
        }

    override suspend fun deleteInteraction(id: String) = call("deleteInteraction", id) {
        val person = owner({ p -> p.interactions.any { it.id == id } }, id)
        save(person.copy(interactions = person.interactions.filter { it.id != id }))
        Unit
    }

    override suspend fun addGift(personId: String, gift: GiftDetails): GiftIdea = call("addGift", personId to gift) {
        val person = find(personId)
        val created = GiftIdea(id("g"), personId, gift.title, gift.notes, gift.price, gift.occasion, gift.givenOn)
        save(person.copy(giftIdeas = person.giftIdeas + created))
        created
    }

    override suspend fun updateGift(id: String, gift: GiftDetails): GiftIdea = call("updateGift", id to gift) {
        val person = owner({ p -> p.giftIdeas.any { it.id == id } }, id)
        val changed = person.giftIdeas.first { it.id == id }
            .copy(title = gift.title, notes = gift.notes, price = gift.price, occasion = gift.occasion, givenOn = gift.givenOn)
        save(person.copy(giftIdeas = person.giftIdeas.map { if (it.id == id) changed else it }))
        changed
    }

    override suspend fun deleteGift(id: String) = call("deleteGift", id) {
        val person = owner({ p -> p.giftIdeas.any { it.id == id } }, id)
        save(person.copy(giftIdeas = person.giftIdeas.filter { it.id != id }))
        Unit
    }

    override suspend fun loans(status: String): List<Loan> = call("loans", status) {
        people.map(::computed).flatMap { it.loans }.filter {
            when (status) {
                LoanStatus.OPEN -> it.isOpen
                LoanStatus.RETURNED -> !it.isOpen
                else -> true
            }
        }
    }

    override suspend fun addLoan(personId: String, loan: LoanDetails): Loan = call("addLoan", personId to loan) {
        val person = find(personId)
        val created = Loan(id("loan"), personId, person.person.name, loan.itemName, loan.direction, loan.lentOn ?: today.toString(),
            loan.dueOn, loan.returnedOn, notes = loan.notes)
        save(person.copy(loans = person.loans + created)).loans.first { it.id == created.id }
    }

    override suspend fun updateLoan(id: String, loan: LoanDetails): Loan = call("updateLoan", id to loan) {
        val person = owner({ p -> p.loans.any { it.id == id } }, id)
        val changed = person.loans.first { it.id == id }.copy(itemName = loan.itemName, direction = loan.direction,
            lentOn = loan.lentOn ?: today.toString(), dueOn = loan.dueOn, returnedOn = loan.returnedOn, notes = loan.notes)
        save(person.copy(loans = person.loans.map { if (it.id == id) changed else it })).loans.first { it.id == id }
    }

    override suspend fun deleteLoan(id: String) = call("deleteLoan", id) {
        val person = owner({ p -> p.loans.any { it.id == id } }, id)
        save(person.copy(loans = person.loans.filter { it.id != id }))
        Unit
    }

    override suspend fun addLink(id: String, link: NewPersonLink): PersonLink = call("addLink", id to link) {
        val person = find(id)
        val created = PersonLink(id("l"), link.kind, link.targetId)
        save(person.copy(links = person.links + created))
        created
    }

    override suspend fun removeLink(id: String, linkId: String) = call("removeLink", id to linkId) {
        val person = find(id)
        save(person.copy(links = person.links.filter { it.id != linkId }))
        Unit
    }

    override suspend fun byLink(kind: String, id: String): List<PersonSummary> = call("byLink", kind to id) {
        people.filter { p -> p.links.any { it.kind == kind && it.targetId == id } }.map { computed(it).person }
    }
}
