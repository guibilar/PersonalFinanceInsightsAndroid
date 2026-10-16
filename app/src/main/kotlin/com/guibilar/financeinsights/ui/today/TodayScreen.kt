package com.guibilar.financeinsights.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.today.TodayAgendaItem
import com.guibilar.financeinsights.core.today.TodayCard
import com.guibilar.financeinsights.core.today.TodayMemory
import com.guibilar.financeinsights.core.today.agendaMeta
import com.guibilar.financeinsights.core.today.agendaTime
import com.guibilar.financeinsights.core.today.daySummary
import com.guibilar.financeinsights.core.today.memoryWhen
import com.guibilar.financeinsights.core.today.upcomingDay
import com.guibilar.financeinsights.ui.common.ShowMessages
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import com.guibilar.financeinsights.core.today.TodayCardType
import com.guibilar.financeinsights.core.today.TodayDestination
import com.guibilar.financeinsights.core.today.TodayDto
import com.guibilar.financeinsights.core.today.TodayModuleStatus
import com.guibilar.financeinsights.core.today.TodaySeverity
import com.guibilar.financeinsights.core.today.TodayTrend
import com.guibilar.financeinsights.core.today.filledText
import com.guibilar.financeinsights.core.today.todayDestination
import com.guibilar.financeinsights.core.today.valueText
import com.guibilar.financeinsights.ui.common.AttentionItem
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.NeedsAttentionCard
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.StatTile
import com.guibilar.financeinsights.ui.common.isLargeText
import com.guibilar.financeinsights.ui.modules.HestiaModule
import com.guibilar.financeinsights.ui.modules.ModuleStatus
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.LocalModuleAccent
import com.guibilar.financeinsights.ui.theme.accentOf
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One module on Today, with its tiles. */
data class TodaySection(
    /** The server's module name, for example "Money". */
    val name: String,
    /** The registry entry, when the app knows the module. */
    val module: HestiaModule?,
    val status: TodayModuleStatus,
    val tiles: List<TodayCard>,
)

/** The registry module that a server module name means ("Money" is "money"). */
fun List<HestiaModule>.forServer(name: String): HestiaModule? = firstOrNull { it.id == name.lowercase(Locale.ROOT) }

/**
 * The modules with cards, in the server's order (a module that has cards but no status comes after), each with its
 * tiles. A module that the app does not know yet gets its own section too, with no app change.
 */
fun TodayDto.sections(registry: List<HestiaModule>): List<TodaySection> {
    val names = (modules.map { it.module } + cards.map { it.module }).distinct()
    return names.map { name ->
        TodaySection(
            name = registry.forServer(name)?.name ?: name,
            module = registry.forServer(name),
            status = modules.firstOrNull { it.module == name }?.status ?: TodayModuleStatus.Complete,
            tiles = cards.filter { it.module == name && it.type == TodayCardType.Tile },
        )
    }
}

/** The server module of its own alerts (T80): no tiles, and last on Today. */
const val SYSTEM_MODULE = "System"

/**
 * The sections that Today shows (T81): a module with tiles or a load problem. A module with only "Needs attention" items
 * has no section (its items are in the one list). System goes last.
 */
fun List<TodaySection>.shown(): List<TodaySection> =
    filter { it.tiles.isNotEmpty() || it.status != TodayModuleStatus.Complete }
        .sortedBy { it.name == SYSTEM_MODULE }

/** All "Needs attention" items, most pressing first (the server's order). */
val TodayDto.attention: List<TodayCard> get() = cards.filter { it.type == TodayCardType.Attention }

/** Registry modules that are still to come and that the server has no cards for. */
fun List<HestiaModule>.comingSoon(sections: List<TodaySection>): List<HestiaModule> =
    filter { module -> module.status == ModuleStatus.ComingSoon && sections.none { it.module?.id == module.id } }

/**
 * The first page: the cards of all modules from the Today API, the Money tiles first (D11), then "Needs attention",
 * then a card for the modules that are not built yet. Each tile and item opens its page through [open].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    model: TodayViewModel,
    modules: List<HestiaModule>,
    contentPadding: PaddingValues,
    open: (TodayDestination) -> Unit,
    openModule: (HestiaModule) -> Unit,
    /** After a pull to refresh: the phone asks for new alerts too (T81). */
    onRefresh: () -> Unit = {},
    /** The signed-in user's name, for the greeting; null for none (the read-only token). */
    userName: String? = null,
    /** False for a read-only user: no note field, no box to tick. */
    canWrite: Boolean = true,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    // Back from another tab or the background: the cards reload (new ones when the data changed in the app).
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    val day = state.day(model.phoneDay()).format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
    // T69: the bell goes to the "Needs attention" card.
    val attentionView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            actions = { AttentionBell(state.today?.attention?.size ?: 0) { scope.launch { attentionView.bringIntoView() } } },
        )
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = {
                model.refresh()
                onRefresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            ReadableColumn {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Hearth: a greeting, as when you come home.
                    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            greeting(localHour(state.today?.generatedAt), userName),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            daySummary(state.today?.agenda),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // T106: jot a thought down without leaving Today: it goes to the Inbox.
                    if (canWrite) JotField(state.jotting, model::jot)
                    val today = state.today
                    val sections = today?.sections(modules).orEmpty()
                    when {
                        today != null -> {
                            state.error?.let { Note(it) }
                            YourDay(today, state, canWrite, open, model::complete)
                            val items = today.attention
                            if (items.isNotEmpty()) NeedsAttention(items, open, Modifier.bringIntoViewRequester(attentionView))
                            Glance(sections.shown(), open, model::retry)
                            if (today.memories.isNotEmpty()) Memories(today.memories, open)
                        }
                        state.error != null && !state.loading -> ErrorState(state.error!!, onRetry = model::retry)
                        else -> LoadingTiles()
                    }
                    val later = modules.comingSoon(sections)
                    if (later.isNotEmpty()) {
                        SectionCard("Coming soon", subtitle = "What other modules will add here") {
                            later.forEach { module ->
                                ListItem(
                                    headlineContent = { Text(module.name) },
                                    supportingContent = { Text(module.todayPreview) },
                                    leadingContent = { Icon(module.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** A short line with a warning icon. */
@Composable
private fun Note(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = LocalChartColors.current.orange)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Tiles two a row, as tall as the taller one; one a row when the text is large, so amounts are not cut off. On a
 * tablet, four in one row (two when the text is large). The same layout as the Money home.
 */
@Composable
private fun Tiles(tiles: List<@Composable (Modifier) -> Unit>) {
    if (tiles.isEmpty()) return
    BoxWithConstraints {
        val wide = maxWidth >= ALL_IN_A_ROW_WIDTH
        val perRow = when {
            isLargeText -> if (wide) 2 else 1
            wide -> 4
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(perRow).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                    // A short last row keeps the tile width.
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** From this width on, four tiles fit in one row. */
private val ALL_IN_A_ROW_WIDTH = 700.dp

@Composable
private fun Tile(card: TodayCard, module: HestiaModule?, open: (TodayDestination) -> Unit, modifier: Modifier = Modifier) {
    val destination = todayDestination(card.link)
    StatTile(
        card.title,
        card.valueText,
        tileIcon(card.kind) ?: module?.icon ?: Icons.Outlined.Dashboard,
        modifier,
        hint = card.filledText.ifEmpty { null },
        hintColor = severityColor(card.severity),
        hintIcon = when (card.trend) {
            TodayTrend.Up -> Icons.AutoMirrored.Outlined.TrendingUp
            TodayTrend.Down -> Icons.AutoMirrored.Outlined.TrendingDown
            null -> null
        },
        onClick = destination?.let { { open(it) } },
    )
}

/** Tile icons by card kind, as on the Money home. */
private fun tileIcon(kind: String): ImageVector? = when (kind) {
    "spent" -> Icons.Outlined.CalendarMonth
    "left-over" -> Icons.Outlined.Savings
    "net-worth" -> Icons.AutoMirrored.Outlined.ShowChart
    "budgets" -> Icons.Outlined.AccountBalanceWallet
    // Tasks (T19): the tasks due today.
    "due-today" -> Icons.Outlined.CheckBox
    else -> null
}

@Composable
private fun severityColor(severity: TodaySeverity): Color {
    val colors = LocalChartColors.current
    return when (severity) {
        TodaySeverity.Good -> colors.positive
        TodaySeverity.Bad -> colors.negative
        TodaySeverity.Warning -> colors.orange
        TodaySeverity.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** What needs a look, from all modules, each opening where it is dealt with (the shared card, T77). */
@Composable
private fun NeedsAttention(items: List<TodayCard>, open: (TodayDestination) -> Unit, modifier: Modifier = Modifier) {
    NeedsAttentionCard(
        items.map { item ->
            val destination = todayDestination(item.link)
            AttentionItem(item.id, item.kind, item.filledText, item.severity == TodaySeverity.Bad, destination?.let { { open(it) } })
        },
        modifier = modifier,
    )
}

/** Four grey tiles while the first answer loads. */
@Composable
private fun LoadingTiles() {
    Tiles(
        List<@Composable (Modifier) -> Unit>(4) {
            { m ->
                Card(
                    modifier = m,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Skeleton(Modifier.padding(14.dp))
                }
            }
        },
    )
}

/**
 * The bell (T69, the design's notification bell): how many items need attention, as a badge; a tap goes to the
 * "Needs attention" card. Off when nothing needs a look.
 */
@Composable
private fun AttentionBell(count: Int, show: () -> Unit) {
    val label = when (count) {
        0 -> "Nothing needs attention"
        1 -> "1 item needs attention"
        else -> "$count items need attention"
    }
    IconButton(onClick = show, enabled = count > 0) {
        BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
            Icon(Icons.Outlined.Notifications, label)
        }
    }
}

/**
 * The hour on the phone when the server made the cards (`generatedAt`, so the greeting follows the data and the tests
 * can fix it); null when there is none yet.
 */
fun localHour(generatedAt: String?, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int? =
    generatedAt?.let { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(zone).hour }.getOrNull() }

/** "Good evening, Alex": by the hour, with the first name of [userName]. Without an hour: "Welcome home". */
fun greeting(hour: Int?, userName: String?): String {
    val part = when {
        hour == null -> "Welcome home"
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val name = userName?.let(::firstName).orEmpty()
    return if (name.isEmpty()) part else "$part, $name"
}

/** A user name as a first name: "alex.smith" or "alex" → "Alex". */
fun firstName(userName: String): String =
    userName.trim().split(Regex("[\\s._@-]+")).firstOrNull().orEmpty().replaceFirstChar { it.uppercase() }

// ---- T106: your day, jot, at a glance, on this day ----

/** One line for a note to the Inbox, with Save. */
@Composable
private fun JotField(busy: Boolean, jot: (String, () -> Unit) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(4000) },
        placeholder = { Text("Jot something down for later…") },
        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) },
        trailingIcon = {
            TextButton(onClick = { jot(text) { text = "" } }, enabled = text.isNotBlank() && !busy) { Text(if (busy) "Saving…" else "Save") }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The icon of an agenda item, by its kind (as on the Calendar). */
private fun agendaIcon(kind: String): ImageVector = when (kind) {
    "task-due" -> Icons.Outlined.TaskAlt
    "birthday" -> Icons.Outlined.Cake
    "reach-out" -> Icons.Outlined.Person
    "loan-due" -> Icons.Outlined.Handshake
    "document-expires" -> Icons.Outlined.Description
    "warranty-ends" -> Icons.Outlined.House
    else -> Icons.Outlined.Event
}

/**
 * "Your day" (T106): today's Calendar items (a box to tick a task due today), then what comes up this week. Each item
 * opens its page through the link map.
 */
@Composable
private fun YourDay(
    today: TodayDto,
    state: TodayState,
    canWrite: Boolean,
    open: (TodayDestination) -> Unit,
    complete: (TodayAgendaItem) -> Unit,
) {
    val accent = accentOf("calendar")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(accent?.container ?: MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp), tint = accent?.color ?: MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(10.dp))
                Text("Your day", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { open(TodayDestination.Calendar()) }) { Text("Calendar") }
            }
            val agenda = today.agenda
            when {
                agenda == null -> Text("The calendar could not load.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    if (!agenda.complete) Note("Some dates could not load.")
                    if (agenda.today.isEmpty()) {
                        Text("Nothing planned today. Enjoy the quiet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    agenda.today.forEach { item ->
                        AgendaRow(item, done = item.done == true || item.id in state.doneHere, busy = state.busyTask == item.id, canWrite = canWrite, open = open, complete = complete)
                    }
                    if (agenda.upcoming.isNotEmpty()) {
                        Text(
                            "COMING UP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        val day = state.day(LocalDate.now())
                        agenda.upcoming.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = item.link != null) { todayDestination(item.link)?.let(open) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(upcomingDay(item, day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(112.dp))
                                Box(Modifier.size(8.dp).clip(CircleShape).background(accentOf(item.module.lowercase(Locale.ROOT))?.color ?: MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.width(8.dp))
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(
    item: TodayAgendaItem,
    done: Boolean,
    busy: Boolean,
    canWrite: Boolean,
    open: (TodayDestination) -> Unit,
    complete: (TodayAgendaItem) -> Unit,
) {
    val accent = accentOf(item.module.lowercase(Locale.ROOT))
    val color = accent?.color ?: MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = item.link != null) { todayDestination(item.link)?.let(open) }
            .padding(end = 12.dp),
    ) {
        Box(Modifier.width(4.dp).height(52.dp).background(color))
        if (item.kind == "task-due" && canWrite) {
            Checkbox(checked = done, onCheckedChange = { if (!done) complete(item) }, enabled = !done && !busy, modifier = Modifier.semantics { contentDescription = "Done: ${item.title}" })
        } else {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(accent?.container ?: MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(agendaIcon(item.kind), null, Modifier.size(16.dp), tint = color) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                item.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val meta = agendaMeta(item)
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!item.allDay) agendaTime(item.startsAt)?.let { Text(it, fontWeight = FontWeight.SemiBold, color = color) }
    }
}

/** "At a glance" (T106): the tiles of every module, two a row, each in its module's colour. */
@Composable
private fun Glance(sections: List<TodaySection>, open: (TodayDestination) -> Unit, retry: () -> Unit) {
    if (sections.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("At a glance", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        sections.forEach { section ->
            when (section.status) {
                TodayModuleStatus.Failed -> ErrorState("${section.name} could not load.", onRetry = retry)
                TodayModuleStatus.Partial -> Note("Some ${section.name} figures could not load.")
                TodayModuleStatus.Complete -> Unit
            }
        }
        val tiles = sections.flatMap { section ->
            section.tiles.map<TodayCard, @Composable (Modifier) -> Unit> { card ->
                { m -> CompositionLocalProvider(LocalModuleAccent provides accentOf(section.module?.id)) { Tile(card, section.module, open, m) } }
            }
        }
        Tiles(tiles)
    }
}

/** "On this day" (T106): this day in earlier years. */
@Composable
private fun Memories(memories: List<TodayMemory>, open: (TodayDestination) -> Unit) {
    val accent = accentOf("journal")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("On this day", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        memories.forEach { memory ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent?.container ?: MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(enabled = memory.link != null) { todayDestination(memory.link)?.let(open) }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(memoryWhen(memory).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = accent?.color ?: MaterialTheme.colorScheme.secondary)
                Text(memory.text, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Serif)
            }
        }
    }
}
