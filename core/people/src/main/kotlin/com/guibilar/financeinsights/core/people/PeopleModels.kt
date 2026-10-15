package com.guibilar.financeinsights.core.people

import kotlinx.serialization.Serializable

// The models of the People API of Hestia.Api (module People, T20; contracts in
// Hestia.Modules.People.Contracts/PeopleDtos.cs). Ids are GUIDs, as text. Days are "yyyy-MM-dd". Times are ISO 8601.
// Amounts are numbers in the one app currency (the app shows CAD).

/** A birthday: the month and the day, and the year when it is known. */
@Serializable
data class Birthday(val month: Int, val day: Int, val year: Int? = null)

/** A person in a list. */
@Serializable
data class PersonSummary(
    val id: String,
    val name: String = "",
    val nickname: String? = null,
    /** Free text, for example "Friend". */
    val relationship: String? = null,
    /** In lower case, for example "gaming group". */
    val groups: List<String> = emptyList(),
    val birthday: Birthday? = null,
    /** Today or later; February 29 is February 28 in other years. */
    val nextBirthday: String? = null,
    val email: String? = null,
    val phone: String? = null,
    /** The reach-out reminder in days, or null for none. */
    val contactEveryDays: Int? = null,
    val lastContactOn: String? = null,
    /** With a reminder: the day to reach out (the last contact, or the add day, plus the reminder). */
    val contactDueOn: String? = null,
    val contactOverdue: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** A contact with a person: a call, a coffee, a visit. */
@Serializable
data class Interaction(
    val id: String,
    val personId: String = "",
    val occurredOn: String = "",
    val kind: String? = null,
    val note: String? = null,
    val createdAt: String? = null,
)

/** A gift idea; with [givenOn], a given gift. */
@Serializable
data class GiftIdea(
    val id: String,
    val personId: String = "",
    val title: String = "",
    val notes: String? = null,
    val price: Double? = null,
    val occasion: String? = null,
    val givenOn: String? = null,
    val createdAt: String? = null,
) {
    val isGiven get() = givenOn != null
}

/** Who has the thing of a loan. */
@Serializable
enum class LoanDirection { Lent, Borrowed }

/** A thing lent to the person, or borrowed from the person. Open while [returnedOn] is null. */
@Serializable
data class Loan(
    val id: String,
    val personId: String = "",
    val personName: String = "",
    val itemName: String = "",
    val direction: LoanDirection = LoanDirection.Lent,
    val lentOn: String = "",
    val dueOn: String? = null,
    val returnedOn: String? = null,
    val overdue: Boolean = false,
    val notes: String? = null,
    val createdAt: String? = null,
) {
    val isOpen get() = returnedOn == null
}

/** A link from a person to a document, a task, a home item, or a transaction. */
@Serializable
data class PersonLink(val id: String, val kind: String, val targetId: String, val createdAt: String? = null)

/** The kinds of link that the People module knows. */
object PersonLinkKinds {
    const val DOCUMENT = "Documents.Document"
    const val TRANSACTION = "Money.Transaction"
    const val TASK = "Tasks.Task"
    const val HOME_ITEM = "Home.Item"
}

/** One person, with the latest 20 contacts, the gift ideas, the loans, and the links. */
@Serializable
data class Person(
    val person: PersonSummary,
    val notes: String? = null,
    /** Newest first. */
    val interactions: List<Interaction> = emptyList(),
    val interactionCount: Int = 0,
    /** Ideas first, then the given gifts (newest first). */
    val giftIdeas: List<GiftIdea> = emptyList(),
    /** Open loans first, then the returned ones. */
    val loans: List<Loan> = emptyList(),
    val links: List<PersonLink> = emptyList(),
    /** The documents and the tasks that link to the person (T93): documents first, each group by title. */
    val linkedFrom: List<PersonLinkedFrom> = emptyList(),
)

/** A document or a task that links to the person (T93). The link belongs to it: it is removed there. */
@Serializable
data class PersonLinkedFrom(
    /** [PersonLinkKinds.DOCUMENT] or [PersonLinkKinds.TASK]. */
    val kind: String,
    val targetId: String,
    val title: String,
)

/** A page of people. */
@Serializable
data class PersonPage(
    val items: List<PersonSummary> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalCount: Int = 0,
    val hasNextPage: Boolean = false,
)

@Serializable
data class GroupCount(val name: String, val count: Int = 0)

/** A birthday in the next days. */
@Serializable
data class UpcomingBirthday(
    val personId: String,
    val name: String = "",
    val date: String = "",
    /** 0 = today. */
    val days: Int = 0,
    val age: Int? = null,
    /** How many open gift ideas the person has. */
    val giftIdeas: Int = 0,
)

/** The numbers of the People screen (`GET /api/people/summary`). */
@Serializable
data class PeopleSummary(
    /** Today on the server ("yyyy-MM-dd"), in the time zone of the People module. */
    val date: String? = null,
    val peopleCount: Int = 0,
    /** Overdue reminders, most overdue first. */
    val reachOut: List<PersonSummary> = emptyList(),
    /** The birthdays in the next 30 days, soonest first. */
    val upcoming: List<UpcomingBirthday> = emptyList(),
    val openLoans: Int = 0,
    val overdueLoans: Int = 0,
)

/** The details of a person (`POST /api/people`, `PUT /api/people/{id}`). A PUT replaces all of them. */
@Serializable
data class PersonDetails(
    val name: String,
    val nickname: String? = null,
    val relationship: String? = null,
    val groups: List<String> = emptyList(),
    val birthday: Birthday? = null,
    val email: String? = null,
    val phone: String? = null,
    val notes: String? = null,
    val contactEveryDays: Int? = null,
)

/** A contact to log. Without [occurredOn]: today on the server. */
@Serializable
data class InteractionDetails(val occurredOn: String? = null, val kind: String? = null, val note: String? = null)

/** A gift idea (all values; a PUT replaces them). */
@Serializable
data class GiftDetails(
    val title: String,
    val notes: String? = null,
    val price: Double? = null,
    val occasion: String? = null,
    val givenOn: String? = null,
)

/** A loan (all values; a PUT replaces them). Without [lentOn]: today on the server. */
@Serializable
data class LoanDetails(
    val itemName: String,
    val direction: LoanDirection = LoanDirection.Lent,
    val lentOn: String? = null,
    val dueOn: String? = null,
    val returnedOn: String? = null,
    val notes: String? = null,
)

@Serializable
data class NewPersonLink(val kind: String, val targetId: String)
