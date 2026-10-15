package com.guibilar.financeinsights.core.people

import com.guibilar.financeinsights.core.logic.formatDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// The rules of the People screens, without Android: the same rules as the server (T20) and the web app (T91).

const val MAX_PERSON_NAME = 200
const val MAX_GROUPS = 20
const val MAX_GROUP = 50
const val MAX_CONTACT_EVERY_DAYS = 3650
const val MAX_GIFT_TITLE = 200
const val MAX_ITEM_NAME = 200

/** The kinds of contact that "Log contact" offers. The server takes any text. */
val CONTACT_KINDS = listOf("Call", "Message", "Coffee", "Visit", "Meal", "Other")

/** The reminders that the form offers: days to a label. */
val REMINDER_CHOICES = listOf(
    7 to "Every week",
    14 to "Every 2 weeks",
    30 to "Every month",
    42 to "Every 6 weeks",
    90 to "Every 3 months",
    182 to "Every 6 months",
    365 to "Every year",
)

/** A day from the API ("2026-10-14"); null for none or a value that is not a day. */
fun peopleDay(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

/** Up to two capital letters: "Marco Silva" → "MS", "Ana" → "A". */
fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val last = if (words.size > 1) words.last().first().toString() else ""
    return (words.first().first() + last).uppercase(Locale.ROOT)
}

/** A group as the screens show it: "gaming group" → "Gaming group". */
fun groupLabel(group: String): String = group.replaceFirstChar { it.titlecase(Locale.ROOT) }

/** Groups typed as "Family, gaming group": trimmed, lower case, without repeats. */
fun parseGroups(text: String): List<String> =
    text.split(',').map { it.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()

/** "today", "yesterday", "5 days ago", "6 weeks ago", "3 months ago", "2 years ago". */
fun agoText(day: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        days < 14 -> "$days days ago"
        days < 60 -> "${days / 7} weeks ago"
        days < 730 -> "${days / 30} months ago"
        else -> "${days / 365} years ago"
    }
}

/** "Last contact 6 weeks ago", or "No contact yet". */
fun lastContactText(lastContactOn: String?, today: LocalDate): String =
    peopleDay(lastContactOn)?.let { "Last contact ${agoText(it, today)}" } ?: "No contact yet"

/** A reminder in words: "Every 6 weeks", "Every 3 weeks", "Every 10 days", or "None". */
fun reminderText(days: Int?): String {
    if (days == null || days <= 0) return "None"
    REMINDER_CHOICES.firstOrNull { it.first == days }?.let { return it.second }
    return if (days % 7 == 0) "Every ${days / 7} weeks" else "Every $days days"
}

/** A birthday as "Nov 2", or "Nov 2, 1990" with the year. */
fun birthdayText(birthday: Birthday?): String {
    if (birthday == null) return ""
    val short = formatDay(LocalDate.of(2000, birthday.month, birthday.day), year = false)
    return birthday.year?.let { "$short, $it" } ?: short
}

/** "Today", "Tomorrow", "In 23 days". */
fun inDaysText(days: Int): String = when {
    days <= 0 -> "Today"
    days == 1 -> "Tomorrow"
    else -> "In $days days"
}

/** The second line of an upcoming birthday: "In 19 days · turns 36 · 2 gift ideas". */
fun upcomingText(birthday: UpcomingBirthday): String = listOfNotNull(
    inDaysText(birthday.days),
    birthday.age?.let { "turns $it" },
    birthday.giftIdeas.takeIf { it > 0 }?.let { if (it == 1) "1 gift idea" else "$it gift ideas" },
).joinToString(" · ")

/** The three capitals of a month ("NOV"), for the date badge. */
fun monthShort(day: LocalDate): String = day.month.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US)

/** "Lent to Marco · due Aug 1, 2026", "Borrowed from Ana". */
fun loanText(loan: Loan): String {
    val who = if (loan.direction == LoanDirection.Lent) "Lent to ${loan.personName}" else "Borrowed from ${loan.personName}"
    return peopleDay(loan.dueOn)?.let { "$who · due ${formatDay(it)}" } ?: who
}

/** The relationship and the groups: "Friend · Gaming group". */
fun groupsLine(person: PersonSummary): String =
    (listOfNotNull(person.relationship?.takeIf { it.isNotBlank() }) + person.groups.map(::groupLabel)).joinToString(" · ")

/**
 * The person form, as typed. [check] gives the reasons it can't be sent (by field), and [details] the values for the
 * API, cleaned as the server cleans them.
 */
data class PersonDraft(
    val name: String = "",
    val nickname: String = "",
    val relationship: String = "",
    val groups: String = "",
    /** 1 to 12, or null for no birthday. */
    val birthMonth: Int? = null,
    val birthDay: String = "",
    val birthYear: String = "",
    val email: String = "",
    val phone: String = "",
    val contactEveryDays: Int? = null,
    val notes: String = "",
) {
    /** The reasons by field ("name", "birthday", "email", "groups"); empty when the form can be sent. */
    fun check(today: LocalDate): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (name.isBlank()) errors["name"] = "Enter a name."
        if (name.trim().length > MAX_PERSON_NAME) errors["name"] = "At most $MAX_PERSON_NAME characters."
        val groupList = parseGroups(groups)
        if (groupList.size > MAX_GROUPS || groupList.any { it.length > MAX_GROUP || '|' in it }) {
            errors["groups"] = "At most $MAX_GROUPS groups of $MAX_GROUP characters, without “|”."
        }
        val email = email.trim()
        if (email.isNotEmpty() && (!email.contains('@') || email.contains(' '))) errors["email"] = "Enter an email address."
        birthdayError(today)?.let { errors["birthday"] = it }
        return errors
    }

    private fun birthdayError(today: LocalDate): String? {
        val day = birthDay.trim()
        val year = birthYear.trim()
        if (birthMonth == null && day.isEmpty()) return if (year.isEmpty()) null else "Choose the month and the day too."
        val month = birthMonth ?: return "Choose a month."
        val dayNumber = day.toIntOrNull() ?: return "Enter the day."
        val yearNumber = if (year.isEmpty()) null else year.toIntOrNull() ?: return "The year is a number."
        val max = YearMonth.of(yearNumber ?: 2000, month).lengthOfMonth()
        if (dayNumber !in 1..max) return "This month has no day $dayNumber."
        if (yearNumber != null && (yearNumber < 1900 || LocalDate.of(yearNumber, month, dayNumber).isAfter(today))) {
            return "The birthday cannot be in the future."
        }
        return null
    }

    fun details(): PersonDetails = PersonDetails(
        name = name.trim(),
        nickname = nickname.trim().ifEmpty { null },
        relationship = relationship.trim().ifEmpty { null },
        groups = parseGroups(groups),
        birthday = birthMonth?.let { month ->
            birthDay.trim().toIntOrNull()?.let { day -> Birthday(month, day, birthYear.trim().toIntOrNull()) }
        },
        email = email.trim().ifEmpty { null },
        phone = phone.trim().ifEmpty { null },
        notes = notes.trim().ifEmpty { null },
        contactEveryDays = contactEveryDays,
    )

    companion object {
        /** The form with the details of a person, for a change. */
        fun of(person: Person): PersonDraft = PersonDraft(
            name = person.person.name,
            nickname = person.person.nickname.orEmpty(),
            relationship = person.person.relationship.orEmpty(),
            groups = person.person.groups.joinToString(", ") { groupLabel(it) },
            birthMonth = person.person.birthday?.month,
            birthDay = person.person.birthday?.day?.toString().orEmpty(),
            birthYear = person.person.birthday?.year?.toString().orEmpty(),
            email = person.person.email.orEmpty(),
            phone = person.person.phone.orEmpty(),
            contactEveryDays = person.person.contactEveryDays,
            notes = person.notes.orEmpty(),
        )
    }
}

/** The new values of a gift idea that is now given on [day] (or, with null, an idea again). */
fun GiftIdea.given(day: LocalDate?): GiftDetails = GiftDetails(title, notes, price, occasion, day?.toString())

/** The new values of a loan whose thing came back on [today] (not before the day of the loan). */
fun Loan.returned(today: LocalDate): LoanDetails {
    val lent = peopleDay(lentOn)
    val back = if (lent != null && today.isBefore(lent)) lent else today
    return LoanDetails(itemName, direction, lentOn.ifEmpty { null }, dueOn, back.toString(), notes)
}

/** A history line: a contact or a loan, newest first. */
sealed interface HistoryEntry {
    val day: String
    val key: String

    data class Contact(val interaction: Interaction) : HistoryEntry {
        override val day get() = interaction.occurredOn
        override val key get() = "c-${interaction.id}"
    }

    data class Lending(val loan: Loan) : HistoryEntry {
        override val day get() = loan.lentOn
        override val key get() = "l-${loan.id}"
    }
}

/** The contacts and the loans of a person, newest first. */
fun historyOf(person: Person): List<HistoryEntry> =
    (person.interactions.map { HistoryEntry.Contact(it) } + person.loans.map { HistoryEntry.Lending(it) })
        .sortedWith(compareByDescending<HistoryEntry> { it.day }.thenBy { it.key })
