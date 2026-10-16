package com.guibilar.financeinsights.people.ui

import android.content.ContentResolver
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import com.guibilar.financeinsights.core.people.PersonDetails
import com.guibilar.financeinsights.core.people.phoneBirthday

/** The most contacts in one import (the server takes 2000). */
const val MAX_PHONE_CONTACTS = 2000

/** One row of the phone's contact data, as read (tests build them by hand). */
data class ContactRow(val contactId: Long, val mimeType: String, val value: String?, val type: Int = 0, val primary: Boolean = false)

/**
 * The phone's contacts as people to import (T105): the name, the nickname, the first email and phone (the primary, then
 * the mobile one), and the birthday. Contacts without a name are left out. Sorted by name.
 */
fun peopleFrom(rows: List<ContactRow>): List<PersonDetails> =
    rows.groupBy { it.contactId }.values.mapNotNull { contact ->
        fun values(mime: String) = contact.filter { it.mimeType == mime && !it.value.isNullOrBlank() }
        val name = values(StructuredName.CONTENT_ITEM_TYPE).firstOrNull()?.value?.trim() ?: return@mapNotNull null
        val phone = values(Phone.CONTENT_ITEM_TYPE).sortedWith(compareBy({ !it.primary }, { it.type != Phone.TYPE_MOBILE })).firstOrNull()
        val email = values(Email.CONTENT_ITEM_TYPE).sortedBy { !it.primary }.firstOrNull()
        val birthday = values(Event.CONTENT_ITEM_TYPE).filter { it.type == Event.TYPE_BIRTHDAY }.firstNotNullOfOrNull { phoneBirthday(it.value) }
        PersonDetails(
            name = name.take(200),
            nickname = values(Nickname.CONTENT_ITEM_TYPE).firstOrNull()?.value?.trim()?.take(100),
            email = email?.value?.trim()?.takeIf { '@' in it },
            phone = phone?.value?.trim()?.take(50),
            birthday = birthday,
        )
    }.sortedBy { it.name.lowercase() }.take(MAX_PHONE_CONTACTS)

/** Reads the phone's contacts (needs READ_CONTACTS). */
fun readPhoneContacts(resolver: ContentResolver): List<PersonDetails> {
    val mimes = listOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE)
    val rows = mutableListOf<ContactRow>()
    resolver.query(
        Data.CONTENT_URI,
        arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.IS_PRIMARY),
        "${Data.MIMETYPE} IN (${mimes.joinToString { "?" }})",
        mimes.toTypedArray(),
        null,
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val mime = cursor.getString(1)
            // A name's display name is DATA1; for the others DATA1 is the value and DATA2 the type.
            rows += ContactRow(cursor.getLong(0), mime, cursor.getString(2), cursor.getInt(3), cursor.getInt(4) != 0)
        }
    }
    return peopleFrom(rows)
}
