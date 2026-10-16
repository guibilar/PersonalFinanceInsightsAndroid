package com.guibilar.financeinsights.people.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.people.Loan
import com.guibilar.financeinsights.core.people.PeopleSummary
import com.guibilar.financeinsights.core.people.agoText
import com.guibilar.financeinsights.core.people.groupLabel
import com.guibilar.financeinsights.core.people.lastContactText
import com.guibilar.financeinsights.core.people.loanText
import com.guibilar.financeinsights.core.people.peopleDay
import com.guibilar.financeinsights.core.people.upcomingText
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.Skeleton

/**
 * The People screen (design canvas, "People"): a search, "Reach out" (people with an overdue reminder), "Upcoming"
 * (birthdays in the next 30 days), "Lent and borrowed", and all people with group chips. [canWrite] false (a read-only
 * user) hides "Add person".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    model: PeopleViewModel,
    contentPadding: PaddingValues,
    openPerson: (String) -> Unit,
    addPerson: () -> Unit,
    /** Import contacts (T105). */
    importPeople: () -> Unit = {},
    canWrite: Boolean = true,
    /** Back to where the screen was opened from; none in previews and tests. */
    onBack: (() -> Unit)? = null,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("People", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (canWrite) {
                        IconButton(onClick = importPeople) { Icon(Icons.Outlined.Contacts, "Import contacts") }
                        IconButton(onClick = addPerson) { Icon(Icons.Outlined.PersonAdd, "Add person") }
                    }
                },
            )
            ReadableColumn {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "search") { SearchField(state.search, model::search) }
                        if (state.search.isBlank() && state.group == null) {
                            when (val summary = state.summary) {
                                LoadState.Loading -> item { Skeleton(lines = 4, lineHeight = 32.dp) }
                                is LoadState.Failed -> item { ErrorState(summary.message, model::refresh) }
                                is LoadState.Loaded -> overview(summary.value, state, openPerson)
                            }
                        }
                        everybody(state, openPerson, model::pickGroup, canWrite)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Reach out, upcoming birthdays, and the open loans. */
private fun LazyListScope.overview(summary: PeopleSummary, state: PeopleState, openPerson: (String) -> Unit) {
    if (summary.reachOut.isNotEmpty()) {
        item(key = "reach-title") { SectionTitle("Reach out") }
        item(key = "reach") {
            RowsCard {
                summary.reachOut.forEachIndexed { index, person ->
                    CardRow({ openPerson(person.id) }, last = index == summary.reachOut.lastIndex) {
                        PersonAvatar(person.name, 42.dp)
                        TwoLines(person.name, lastContactText(person.lastContactOn, state.today), MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
    if (summary.upcoming.isNotEmpty()) {
        item(key = "upcoming-title") { SectionTitle("Upcoming") }
        item(key = "upcoming") {
            RowsCard {
                summary.upcoming.forEachIndexed { index, birthday ->
                    CardRow({ openPerson(birthday.personId) }, last = index == summary.upcoming.lastIndex) {
                        peopleDay(birthday.date)?.let { DateBadge(it) }
                        TwoLines("${birthday.name}’s birthday", upcomingText(birthday))
                    }
                }
            }
        }
    }
    if (state.loans.isNotEmpty()) {
        item(key = "loans-title") { SectionTitle("Lent and borrowed") }
        item(key = "loans") { LoansCard(state.loans, openPerson) }
    }
}

@Composable
private fun LoansCard(loans: List<Loan>, openPerson: (String) -> Unit) {
    RowsCard {
        loans.forEachIndexed { index, loan ->
            CardRow({ openPerson(loan.personId) }, last = index == loans.lastIndex) {
                TwoLines(loan.itemName, loanText(loan))
                if (loan.overdue) Text("Overdue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/** "All people · 48", the group chips, and the list. */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.everybody(state: PeopleState, openPerson: (String) -> Unit, pickGroup: (String?) -> Unit, canWrite: Boolean) {
    item(key = "all-title") {
        SectionTitle(if (state.people is LoadState.Loaded) "All people · ${state.totalCount}" else "All people")
    }
    if (state.groups.isNotEmpty()) {
        item(key = "groups") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.group == null, onClick = { pickGroup(null) }, label = { Text("All") })
                state.groups.forEach { group ->
                    FilterChip(
                        selected = state.group == group.name,
                        onClick = { pickGroup(if (state.group == group.name) null else group.name) },
                        label = { Text(groupLabel(group.name)) },
                    )
                }
            }
        }
    }
    when (val people = state.people) {
        LoadState.Loading -> item { Skeleton(lines = 4, lineHeight = 32.dp) }
        is LoadState.Failed -> item { ErrorState(people.message, { pickGroup(state.group) }) }
        is LoadState.Loaded -> if (people.value.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    when {
                        state.search.isNotBlank() || state.group != null -> "Nobody found."
                        canWrite -> "No people yet. Add the people you care about, with a birthday or a reach-out reminder."
                        else -> "No people yet."
                    },
                )
            }
        } else {
            item(key = "people") {
                RowsCard {
                    people.value.forEachIndexed { index, person ->
                        CardRow({ openPerson(person.id) }, last = index == people.value.lastIndex) {
                            PersonAvatar(person.name, 36.dp)
                            TwoLines(person.name, person.relationship)
                            peopleDay(person.lastContactOn)?.let {
                                Text(agoText(it, state.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(text: String, search: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = search,
        placeholder = { Placeholder("Find a person") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { search("") }) { Icon(Icons.Outlined.Close, "Clear the search") } },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
