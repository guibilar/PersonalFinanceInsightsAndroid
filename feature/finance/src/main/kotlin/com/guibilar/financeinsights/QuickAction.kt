package com.guibilar.financeinsights

/**
 * Something asked of the app from outside it: the launcher shortcut, the widget, a notification. [intentAction] is the
 * action of the intent that opens the app for it; pinned shortcuts and posted notifications keep it, so it never changes.
 */
enum class QuickAction(val intentAction: String) {
    AddTransaction("com.guibilar.financeinsights.ADD_TRANSACTION"),
    Budgets("com.guibilar.financeinsights.BUDGETS"),
    Subscriptions("com.guibilar.financeinsights.SUBSCRIPTIONS"),
    Review("com.guibilar.financeinsights.REVIEW"),
    Import("com.guibilar.financeinsights.IMPORT"),
    ;

    companion object {
        /** The quick action an intent's action asks for; null for any other intent. */
        fun of(intentAction: String?): QuickAction? = entries.firstOrNull { it.intentAction == intentAction }
    }
}
