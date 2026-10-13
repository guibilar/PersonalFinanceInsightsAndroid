package com.guibilar.financeinsights.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.ui.MoneyLink
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.modules.HestiaModule
import com.guibilar.financeinsights.ui.modules.ModuleStatus
import com.guibilar.financeinsights.ui.money.MoneyGlance
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The first page: the Money module's headline tiles and "Needs attention" first, then a card for the modules that
 * are not built yet. For now the figures come from the dashboard's reports; the Today API replaces them (T09).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    model: DashboardViewModel,
    modules: List<HestiaModule>,
    contentPadding: PaddingValues,
    open: (MoneyLink) -> Unit,
    openMoney: () -> Unit,
) {
    val state by model.state.collectAsState()
    // Back from another tab or the background: the figures reload, and "today" moves on.
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    val day = state.today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Money this month",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        TextButton(onClick = openMoney) {
                            Text("Open Money")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                        }
                    }
                    MoneyGlance(state, open)
                    val later = modules.filter { it.status == ModuleStatus.ComingSoon }
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
