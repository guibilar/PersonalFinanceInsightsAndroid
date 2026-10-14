package com.guibilar.financeinsights.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.StatTile
import com.guibilar.financeinsights.ui.common.isLargeText
import com.guibilar.financeinsights.ui.modules.HestiaModule
import com.guibilar.financeinsights.ui.modules.ModuleStatus
import com.guibilar.financeinsights.ui.theme.LocalChartColors
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
) {
    val state by model.state.collectAsState()
    // Back from another tab or the background: the cards reload (new ones when the data changed in the app).
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    val day = state.day(model.phoneDay()).format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(
            title = {
                Column {
                    Text("Today", fontWeight = FontWeight.SemiBold)
                    Text(day, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
            ReadableColumn {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val today = state.today
                    val sections = today?.sections(modules).orEmpty()
                    when {
                        today != null -> {
                            state.error?.let { Note(it) }
                            sections.forEach { section -> Section(section, open, openModule, model::retry) }
                            val items = today.attention
                            if (items.isNotEmpty()) NeedsAttention(items, open)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                section.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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

/** "Needs attention" icons by card kind, as on the Money dashboard. */
private fun attentionIcon(kind: String): ImageVector = when (kind) {
    "budget-over" -> Icons.Outlined.AccountBalanceWallet
    "statements-not-imported", "statements-behind" -> Icons.Outlined.UploadFile
    "price-changed" -> Icons.Outlined.Autorenew
    "unsorted" -> Icons.Outlined.Sell
    // Documents (T11): a document that expires soon, or that expired.
    "expiring", "expired" -> Icons.Outlined.Description
    // Tasks (T19): one overdue task, or many.
    "overdue", "overdue-many" -> Icons.Outlined.CheckBox
    else -> Icons.Outlined.ErrorOutline
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

/** What needs a look, from all modules, each opening where it is dealt with. Only shown when something does. */
@Composable
private fun NeedsAttention(items: List<TodayCard>, open: (TodayDestination) -> Unit) {
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Needs attention",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { heading() },
            )
            items.forEach { item ->
                val destination = todayDestination(item.link)
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (destination != null) Modifier.clickable(role = Role.Button) { open(destination) } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        attentionIcon(item.kind),
                        null,
                        Modifier.size(18.dp),
                        tint = if (item.severity == TodaySeverity.Bad) LocalChartColors.current.negative else onContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(item.filledText, style = MaterialTheme.typography.bodyMedium, color = onContainer, modifier = Modifier.weight(1f))
                    if (destination != null) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = onContainer)
                    }
                }
            }
        }
    }
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
