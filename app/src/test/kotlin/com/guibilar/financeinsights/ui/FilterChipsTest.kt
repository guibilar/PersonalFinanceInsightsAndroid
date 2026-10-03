package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.model.Category
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class FilterChipsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a category and an account with the same name both show as filters`() {
        // A "Savings" category and the "Savings" account: their chips once shared a list key, which crashed the screen.
        val api = FakeFinanceApi().apply { extraCategories += Category("c-savings", "Savings", "#1F76B5") }
        val route = TransactionsRoute(categoryId = "c-savings")
        val model = TransactionsViewModel(api, route) { LocalDate.of(2026, 10, 3) }
        model.setAccount("5b0c4b8e-3f5e-4c1a-9d53-0c2f7a1e8a02")
        compose.setContent { FinanceTheme(dynamicColor = false) { Surface { TransactionsScreen(model, AccountInfo("dev", "s"), {}, PaddingValues()) } } }
        compose.waitForIdle()
        compose.onAllNodesWithText("Savings").assertCountEquals(2)
    }
}
