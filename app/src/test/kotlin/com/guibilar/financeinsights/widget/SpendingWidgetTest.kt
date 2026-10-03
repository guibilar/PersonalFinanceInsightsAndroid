package com.guibilar.financeinsights.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.test.core.app.ApplicationProvider
import androidx.glance.testing.unit.hasText
import com.guibilar.financeinsights.core.model.Budget
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpendingWidgetTest {
    @Test
    fun `shows the month's spending and the fullest budgets`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        val budgets = listOf(
            Budget(id = "1", name = "Groceries", totalAmount = 150.0, totalSpent = 126.29, percentageUsed = 84.2),
            Budget(id = "2", name = "Eating out", totalAmount = 100.0, totalSpent = 6.45, percentageUsed = 6.45),
        )
        provideComposable { Content(WidgetData.Loaded("October", 186.89, budgets)) }
        onNode(hasText("Spent in October")).assertExists()
        onNode(hasText("$186.89")).assertExists()
        onNode(hasText("Groceries")).assertExists()
        onNode(hasText("84%")).assertExists()
        onNode(hasText("+ Add")).assertExists()
    }

    @Test
    fun `signed out, it says to sign in`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable { Content(WidgetData.SignedOut) }
        onNode(hasText("Open Finance Insights to sign in.")).assertExists()
    }

    @Test
    fun `with the app lock on, no amounts show`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable { Content(WidgetData.Locked) }
        onNode(hasText("Locked. Tap to open.")).assertExists()
    }
}
