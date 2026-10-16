package com.guibilar.financeinsights.people

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakePeopleApi
import com.guibilar.financeinsights.core.people.Birthday
import com.guibilar.financeinsights.core.people.PeopleImportRequest
import com.guibilar.financeinsights.core.people.PersonDetails
import com.guibilar.financeinsights.people.ui.ContactRow
import com.guibilar.financeinsights.people.ui.PeopleImportScreen
import com.guibilar.financeinsights.people.ui.PeopleImportViewModel
import com.guibilar.financeinsights.people.ui.peopleFrom
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** T105: the phone's contacts and a vCard file go through the server's preview, then the chosen ones are imported. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class PeopleImportTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakePeopleApi()
    private val done = mutableListOf<String>()
    private val phone = listOf(PersonDetails("Zoe Martin", phone = "+1 403 555 0101"), PersonDetails("Carla Diaz", email = "carla@example.com"))

    private fun show(shared: MutableStateFlow<String?> = MutableStateFlow(null), canWrite: Boolean = true): PeopleImportViewModel {
        val model = PeopleImportViewModel(api)
        compose.setContent {
            FinanceTheme(ripples = false, dynamicColor = false) {
                Surface {
                    PeopleImportScreen(model, PaddingValues(), canWrite, onDone = { done += it }, onBack = {}, sharedVCard = shared, readContacts = { phone })
                }
            }
        }
        compose.waitForIdle()
        return model
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    @Test
    fun `the phone's contacts are read into people`() {
        val rows = listOf(
            ContactRow(1, StructuredName.CONTENT_ITEM_TYPE, "Ana Souza"),
            ContactRow(1, Phone.CONTENT_ITEM_TYPE, "+1 403 555 0100", Phone.TYPE_WORK),
            ContactRow(1, Phone.CONTENT_ITEM_TYPE, "+1 403 555 0199", Phone.TYPE_MOBILE),
            ContactRow(1, Email.CONTENT_ITEM_TYPE, "ana@example.com", primary = true),
            ContactRow(1, Event.CONTENT_ITEM_TYPE, "--10-20", Event.TYPE_BIRTHDAY),
            ContactRow(1, Event.CONTENT_ITEM_TYPE, "2015-06-01", Event.TYPE_ANNIVERSARY),
            ContactRow(1, Nickname.CONTENT_ITEM_TYPE, "Aninha"),
            // No name: left out.
            ContactRow(2, Phone.CONTENT_ITEM_TYPE, "911"),
            ContactRow(3, StructuredName.CONTENT_ITEM_TYPE, "bruno"),
        )

        assertEquals(
            listOf(
                PersonDetails("Ana Souza", nickname = "Aninha", email = "ana@example.com", phone = "+1 403 555 0199", birthday = Birthday(10, 20)),
                PersonDetails("bruno"),
            ),
            peopleFrom(rows),
        )
    }

    @Test
    fun `the start offers the phone and a file`() {
        show()
        compose.onNodeWithText("From this phone's contacts").assertExists()
        compose.onNodeWithText("From a .vcf file").assertExists()
        shoot("people-import")
    }

    @Test
    fun `the phone's contacts are previewed, chosen, and imported`() {
        show()
        compose.onNodeWithText("From this phone's contacts").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("New person").onFirst().assertExists()
        compose.onNodeWithText("Import 2 contacts").assertExists()
        shoot("people-import-preview")

        compose.onNodeWithText("Zoe Martin").performClick()
        compose.onNodeWithText("Import 1 contact").performClick()
        compose.waitForIdle()

        val sent = api.sent.last { it.first == "importPeople" }.second as PeopleImportRequest
        assertEquals((false to listOf(1)), sent.dryRun to sent.only)
        assertEquals(listOf("1 person added."), done)
    }

    @Test
    fun `a shared vcf file shows its preview`() {
        show(shared = MutableStateFlow("BEGIN:VCARD\nFN:Dan Wu\nEND:VCARD"))
        compose.onNodeWithText("Dan Wu").assertExists()
    }

    @Test
    fun `a read-only user cannot import`() {
        show(canWrite = false)
        compose.onNodeWithText("You can only read people.").assertExists()
        compose.onNodeWithText("From this phone's contacts").assertDoesNotExist()
    }
}
