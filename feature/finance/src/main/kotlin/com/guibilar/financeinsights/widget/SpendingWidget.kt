package com.guibilar.financeinsights.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.guibilar.financeinsights.QuickAction
import com.guibilar.financeinsights.financeHost
import com.guibilar.financeinsights.core.api.ApiJson
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.spentThisMonth
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.Status
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the widget shows: this month's spending and the fullest budgets, or why it can't. */
sealed interface WidgetData {
    @Serializable
    data class Loaded(
        val month: String,
        val spent: Double,
        val budgets: List<Budget>,
        /** When these figures were fetched, "14:05", shown when they couldn't be refreshed since. */
        val asOf: String? = null,
        /** The month they are for, "2026-10": the last figures stay only within it. */
        val yearMonth: String? = null,
    ) : WidgetData
    data object SignedOut : WidgetData
    /** Amounts hidden by choice (Settings): the home screen shows none, as anyone can see it. */
    data object Hidden : WidgetData
    data object Failed : WidgetData
}

/** The home-screen widget: spent this month, the three fullest budgets, and a button to add a transaction. */
class SpendingWidget : GlanceAppWidget() {
    // Told its real size, so it shows as many budgets as fit instead of cutting the last off.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(data) } }
    }

    private suspend fun load(context: Context): WidgetData {
        val host = context.financeHost
        // Hidden only when asked: the app lock alone used to hide them, which looked like the widget had broken.
        if (host.financePreferences.hideWidgetAmounts.value || host.amountsHidden) return WidgetData.Hidden
        val api = host.backgroundApi() ?: return WidgetData.SignedOut
        val monthResult = apiCall { api.monthReport() }
        // The session ended (expired, or signed out elsewhere): the figures belong to no one until the app signs in again.
        if (monthResult.apiError?.isUnauthorized == true) {
            forget(context)
            return WidgetData.SignedOut
        }
        val month = monthResult.getOrNull()
            // Unreachable (Tailscale off), the last figures stay, with when they are from; better than nothing.
            ?: return lastLoaded(context)?.takeIf { it.yearMonth == YearMonth.now().toString() } ?: WidgetData.Failed
        val budgets = apiCall { api.budgets(BudgetFilter(status = Status.Active, page = 1, pageSize = 1000)) }.getOrNull()
            ?.items?.sortedByDescending { it.percentageUsed }?.take(3)
            // The budgets failed alone: the last ones this month stay rather than vanishing as if there were none.
            ?: lastLoaded(context)?.takeIf { it.yearMonth == YearMonth.now().toString() }?.budgets.orEmpty()
        val loaded = WidgetData.Loaded(
            month = thisMonth(),
            yearMonth = YearMonth.now().toString(),
            spent = spentThisMonth(month),
            budgets = budgets,
        )
        remember(context, loaded)
        return loaded
    }

    private fun thisMonth() = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM", Locale.US))

    private fun store(context: Context) = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    private fun remember(context: Context, loaded: WidgetData.Loaded) {
        val asOf = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        store(context).edit { putString(KEY_LAST, ApiJson.encodeToString(loaded.copy(asOf = asOf))) }
    }

    private fun lastLoaded(context: Context): WidgetData.Loaded? =
        store(context).getString(KEY_LAST, null)?.let { runCatching { ApiJson.decodeFromString<WidgetData.Loaded>(it) }.getOrNull() }

    companion object {
        private const val KEY_LAST = "last"

        /** Forgets the last figures: signed out, they belong to no one now. */
        fun forget(context: Context) {
            context.getSharedPreferences("widget", Context.MODE_PRIVATE).edit { clear() }
        }

        /** Redraws every placed widget with fresh figures; nothing when none is placed. */
        suspend fun refresh(context: Context) = runCatching { SpendingWidget().updateAll(context) }
    }
}

class SpendingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SpendingWidget()
}

private val WIDGET_PADDING = 14.dp
private val TOTAL_HEIGHT = 56.dp
private val BUDGET_HEIGHT = 34.dp

@Composable
internal fun Content(data: WidgetData) {
    val context = LocalContext.current
    val activity = context.financeHost.mainActivity
    val open = actionStartActivity(Intent(context, activity))
    val add = actionStartActivity(Intent(context, activity).setAction(QuickAction.AddTransaction.intentAction))
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(WIDGET_PADDING).clickable(open),
    ) {
        when (data) {
            WidgetData.SignedOut -> Text("Open Finance Insights to sign in.", style = TextStyle(color = GlanceTheme.colors.onSurface))
            WidgetData.Hidden -> {
                Text("Finance Insights", style = muted)
                Text("Amounts hidden. Tap to open.", style = TextStyle(color = GlanceTheme.colors.onSurface))
            }
            WidgetData.Failed -> {
                Text("Spent this month", style = muted)
                Text("Couldn't load. Tap to open the app.", style = TextStyle(color = GlanceTheme.colors.onSurface))
            }
            is WidgetData.Loaded -> {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(if (data.asOf != null) "Spent in ${data.month} · as of ${data.asOf}" else "Spent in ${data.month}", style = muted)
                        Text(
                            Money.format(data.spent),
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Text(
                        "+ Add",
                        style = TextStyle(color = GlanceTheme.colors.onPrimary, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(16.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp).clickable(add),
                    )
                }
                // The total takes about 56dp of the height; each budget about 34dp.
                val room = ((LocalSize.current.height - WIDGET_PADDING * 2 - TOTAL_HEIGHT) / BUDGET_HEIGHT).toInt().coerceAtLeast(0)
                data.budgets.take(room).forEach { budget ->
                    Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        Text(budget.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                        Spacer(GlanceModifier.width(8.dp))
                        Text(Money.percentUsed(budget.percentageUsed), style = muted)
                    }
                    Spacer(GlanceModifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = (budget.percentageUsed / 100).toFloat().coerceIn(0f, 1f),
                        modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                        color = if (budget.percentageUsed >= 100) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                        backgroundColor = GlanceTheme.colors.surfaceVariant,
                    )
                }
            }
        }
    }
}
