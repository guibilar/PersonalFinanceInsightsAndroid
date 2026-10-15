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
import com.guibilar.financeinsights.core.today.TodayCard
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
) {
    val state by model.state.collectAsState()
    // Back from another tab or the background: the cards reload (new ones when the data changed in the app).
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    val day = state.day(model.phoneDay()).format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
    // T69: the bell goes to the "Needs attention" card.
    val attentionView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
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
                            "Here is how things are at home today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val today = state.today
                    val sections = today?.sections(modules).orEmpty()
                    when {
                        today != null -> {
                            state.error?.let { Note(it) }
                            sections.shown().forEach { section -> Section(section, open, openModule, model::retry) }
                            val items = today.attention
                            if (items.isNotEmpty()) NeedsAttention(items, open, Modifier.bringIntoViewRequester(attentionView))
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
}

/** A module's heading, its status, and its tiles. */
@Composable
private fun Section(section: TodaySection, open: (TodayDestination) -> Unit, openModule: (HestiaModule) -> Unit, retry: () -> Unit) {
    // Each module in its own warm colour: the icon here and the tiles' icons (LocalModuleAccent).
    val accent = accentOf(section.module?.id)
    CompositionLocalProvider(LocalModuleAccent provides accent) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            section.module?.let { module ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(accent?.container ?: MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(module.icon, null, Modifier.size(16.dp), tint = accent?.color ?: MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                section.name,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            val module = section.module
            if (module != null && module.status == ModuleStatus.Active && module.route != null) {
                TextButton(onClick = { openModule(module) }) {
                    Text("Open ${module.name}")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                }
            }
        }
        when (section.status) {
            TodayModuleStatus.Failed -> ErrorState("${section.name} could not load.", onRetry = retry)
            else -> {
                if (section.status == TodayModuleStatus.Partial) Note("Some ${section.name} figures could not load.")
                Tiles(section.tiles.map<TodayCard, @Composable (Modifier) -> Unit> { card -> { m -> Tile(card, section.module, open, m) } })
            }
        }
    }
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
