package com.guibilar.financeinsights.ui.settings

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.ReadableColumn

/** Where the rest of the app lives: insights (subscriptions, reviews), setup (accounts, categories, tags, rules, settings), and the signed-in account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    username: String,
    version: String,
    contentPadding: PaddingValues,
    openRules: () -> Unit,
    openCategories: () -> Unit,
    openAccounts: () -> Unit,
    openAccount: () -> Unit,
    openSettings: () -> Unit = {},
    openSubscriptions: () -> Unit = {},
    openReview: () -> Unit = {},
    openTags: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(title = { FitText("More", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) })
        // On a tablet, a centred column rather than rows stretched across the screen.
        ReadableColumn {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Group("Insights")
                Entry(Icons.Outlined.Autorenew, "Subscriptions", "Payments that come again and again, and what they cost", openSubscriptions)
                Entry(Icons.Outlined.CalendarMonth, "Review", "A month or a year against the one before", openReview)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Group("Setup")
                Entry(Icons.Outlined.AccountBalance, "Accounts", "The bank accounts statements are imported into", openAccounts)
                Entry(Icons.Outlined.Palette, "Categories", "Names and colours, everywhere they show", openCategories)
                Entry(Icons.Outlined.Tag, "Tags", "Your own labels across categories", openTags)
                Entry(Icons.AutoMirrored.Outlined.Rule, "Rules", "What sets each transaction's category, and what it counts as", openRules)
                Entry(Icons.Outlined.Settings, "Settings", "Theme, app lock, budget alerts", openSettings)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Entry(Icons.Outlined.ManageAccounts, username, "Password, sessions, signing out", openAccount)
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

/** A group's name over its entries. */
@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Entry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
