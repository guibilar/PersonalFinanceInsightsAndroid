package com.guibilar.financeinsights.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guibilar.financeinsights.ui.AccountInfo
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.ReadableColumn

/**
 * All the modules, as cards: the built ones open their home, the others say "Coming soon". Below them, the app's own
 * pages (settings and the account), which were under More before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    modules: List<HestiaModule>,
    account: AccountInfo,
    version: String,
    contentPadding: PaddingValues,
    open: (HestiaModule) -> Unit,
    openSettings: () -> Unit,
    openAccount: () -> Unit,
    /** A short note on a module's card, by module id, for example "3 overdue" on Tasks (T86). */
    badges: Map<String, String> = emptyMap(),
) {
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(title = { FitText("Modules", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) })
        ReadableColumn {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(modules, key = { it.id }) { module -> ModuleCard(module, badges[module.id]) { open(module) } }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(
                            "App",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
                        )
                        ListItem(
                            headlineContent = { Text("Settings") },
                            supportingContent = { Text("Theme, app lock, notifications") },
                            leadingContent = { Icon(Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable(onClick = openSettings),
                        )
                        ListItem(
                            headlineContent = { Text(account.username) },
                            supportingContent = { Text("Password, sessions, signing out") },
                            leadingContent = { Icon(Icons.Outlined.ManageAccounts, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable(onClick = openAccount),
                        )
                        Text(
                            "Finance Insights $version",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A module's card. A module that is not built yet is outlined, cannot be opened, and says "Coming soon". [badge] is a
 * short note in the error colours, for example "3 overdue".
 */
@Composable
private fun ModuleCard(module: HestiaModule, badge: String?, onClick: () -> Unit) {
    val active = module.status == ModuleStatus.Active && module.route != null
    val content: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 132.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        module.icon,
                        null,
                        tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(module.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(module.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!active) {
                Text("Coming soon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (active) {
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) { content() }
    } else {
        OutlinedCard(Modifier.semantics(mergeDescendants = true) {}) { content() }
    }
}
