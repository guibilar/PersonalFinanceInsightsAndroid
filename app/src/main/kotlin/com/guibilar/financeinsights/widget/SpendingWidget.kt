package com.guibilar.financeinsights.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
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
import com.guibilar.financeinsights.FinanceApp
import com.guibilar.financeinsights.MainActivity
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.spentThisMonth
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.Status
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the widget shows: this month's spending and the fullest budgets, or why it can't. */
sealed interface WidgetData {
    data class Loaded(val month: String, val spent: Double, val budgets: List<Budget>) : WidgetData
    data object SignedOut : WidgetData
    /** The app lock is on: the home screen shows no amounts, as anyone can see it. */
    data object Locked : WidgetData
    data object Failed : WidgetData
}

/** The home-screen widget: spent this month, the three fullest budgets, and a button to add a transaction. */
class SpendingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(data) } }
    }

    private suspend fun load(context: Context): WidgetData {
        val app = context.applicationContext as FinanceApp
        if (app.preferences.lock.value) return WidgetData.Locked
        val api = app.session.backgroundApi() ?: return WidgetData.SignedOut
        val month = apiCall { api.monthReport() }.getOrNull() ?: return WidgetData.Failed
        val budgets = apiCall { api.budgets(BudgetFilter(status = Status.Active, page = 1, pageSize = 1000)) }.getOrNull()
        return WidgetData.Loaded(
            month = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM", Locale.US)),
            spent = spentThisMonth(month),
            budgets = budgets?.items.orEmpty().sortedByDescending { it.percentageUsed }.take(3),
        )
    }

    companion object {
        /** Redraws every placed widget with fresh figures; nothing when none is placed. */
        suspend fun refresh(context: Context) = runCatching { SpendingWidget().updateAll(context) }
    }
}

class SpendingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SpendingWidget()
}

@Composable
internal fun Content(data: WidgetData) {
    val context = LocalContext.current
    val open = actionStartActivity(Intent(context, MainActivity::class.java))
    val add = actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_TRANSACTION))
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(14.dp).clickable(open),
    ) {
        when (data) {
            WidgetData.SignedOut -> Text("Open Finance Insights to sign in.", style = TextStyle(color = GlanceTheme.colors.onSurface))
            WidgetData.Locked -> {
                Text("Finance Insights", style = muted)
                Text("Locked. Tap to open.", style = TextStyle(color = GlanceTheme.colors.onSurface))
            }
            WidgetData.Failed -> {
                Text("Spent this month", style = muted)
                Text("Couldn't load. Tap to open the app.", style = TextStyle(color = GlanceTheme.colors.onSurface))
            }
            is WidgetData.Loaded -> {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text("Spent in ${data.month}", style = muted)
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
                data.budgets.forEach { budget ->
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
