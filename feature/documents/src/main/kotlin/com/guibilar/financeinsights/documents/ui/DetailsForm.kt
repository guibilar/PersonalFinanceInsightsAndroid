package com.guibilar.financeinsights.documents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentDetails
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.parseTags
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.common.OptionalDateField
import java.time.LocalDate

/** The details of a document as typed in a form: for a new document, and for an edit. */
data class DetailsDraft(
    val title: String = "",
    val kind: DocumentKind = DocumentKind.Other,
    /** As typed: "kitchen, warranty". */
    val tags: String = "",
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val notes: String = "",
) {
    /** Why the expiry can't be saved; null when it can. */
    val expiryError: String?
        get() = if (issuedOn != null && expiresOn != null && expiresOn < issuedOn) "The expiry can't be before the issue day." else null

    /** The details to send: all of them, empty ones as nothing. */
    fun toDetails() = DocumentDetails(
        title = title.trim().ifEmpty { null },
        kind = kind,
        tags = parseTags(tags),
        issuedOn = issuedOn?.toString(),
        expiresOn = expiresOn?.toString(),
        notes = notes.trim().ifEmpty { null },
    )

    companion object {
        fun of(document: Document) = DetailsDraft(
            title = document.title,
            kind = document.kind,
            tags = document.tags.joinToString(", "),
            issuedOn = document.issuedOn?.let(::day),
            expiresOn = document.expiresOn?.let(::day),
            notes = document.notes.orEmpty(),
        )

        private fun day(text: String) = runCatching { LocalDate.parse(text.take(10)) }.getOrNull()

        /** Keeps what was typed when the phone rotates. */
        val Saver: Saver<DetailsDraft, Any> = listSaver(
            save = { listOf(it.title, it.kind.name, it.tags, it.issuedOn?.toString(), it.expiresOn?.toString(), it.notes) },
            restore = {
                DetailsDraft(
                    title = it[0] as String,
                    kind = DocumentKind.valueOf(it[1] as String),
                    tags = it[2] as String,
                    issuedOn = (it[3] as String?)?.let(::day),
                    expiresOn = (it[4] as String?)?.let(::day),
                    notes = it[5] as String,
                )
            },
        )
    }
}

/**
 * The fields of a document's details. [titleHint] is what the title becomes when it is left empty (a new document:
 * the name of the first file); without it, the title is needed.
 */
@Composable
fun DetailsForm(draft: DetailsDraft, onChange: (DetailsDraft) -> Unit, titleHint: String? = null, titleError: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onChange(draft.copy(title = it)) },
            label = { Text("Title") },
            placeholder = titleHint?.let { { Text(it, maxLines = 1) } },
            supportingText = titleError?.let { { Text(it) } },
            isError = titleError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownPicker(
            label = "Kind",
            options = DocumentKind.entries,
            selected = draft.kind,
            text = { it?.label.orEmpty() },
            onPick = { onChange(draft.copy(kind = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.tags,
            onValueChange = { onChange(draft.copy(tags = it)) },
            label = { Text("Tags") },
            placeholder = { Text("kitchen, car", maxLines = 1) },
            supportingText = { Text("Separate tags with commas.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ClearableDate("Issued on", draft.issuedOn) { onChange(draft.copy(issuedOn = it)) }
        ClearableDate("Expires on", draft.expiresOn, error = draft.expiryError) { onChange(draft.copy(expiresOn = it)) }
        OutlinedTextField(
            value = draft.notes,
            onValueChange = { onChange(draft.copy(notes = it)) },
            label = { Text("Notes") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A day that can also be taken away: an issue day or an expiry is often not known. */
@Composable
private fun ClearableDate(label: String, value: LocalDate?, error: String? = null, onChange: (LocalDate?) -> Unit) {
    Column {
        OptionalDateField(label, value, onChange, isError = error != null)
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
