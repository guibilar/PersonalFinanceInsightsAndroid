package com.guibilar.financeinsights.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * False for a read-only user (T60, T75): the Money pages hide every write action (add, import, edit, delete, restore,
 * bulk changes, "apply rules", categories, tags, rules, budgets, accounts), and quick actions open the page without its
 * form. The app provides it around its navigation host; the server refuses the writes anyway.
 */
val LocalFinanceCanWrite = staticCompositionLocalOf { true }

/** What a read-only user is told when a quick action (shortcut, widget, notification, link) wanted a form. */
const val READ_ONLY_NOTE = "This account can only read. It cannot add or change anything."
