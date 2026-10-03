# Personal Finance Insights for Android

A native Android app (Kotlin, Jetpack Compose, Material 3) for the Personal Finance Insights backend: the same
account and data as the web app, on your phone.

## What it does

- **Sign in** with the server's address (where you open the web app, e.g. `https://finance.example.ts.net`) and your
  username and password. The session is kept between launches, for 14 days of inactivity like the web's "Remember me".
- **Dashboard:** spending this month against last month to date, the monthly average, the top category, budgets over
  their limit; monthly spending by category (tap a month for its categories, tap a category to list its transactions
  week by week); this month by category; each category's typical month; income and spending (chart or table);
  budgets at a glance; recent transactions.
- **Transactions:** search, filters (date presets or a custom range, category, account, what it counts as), sorting,
  and a list that loads as you scroll. The totals of whatever the filters pick, and "Over time": per week, month,
  quarter or year, with the running total; tap a bar to list that period. "Where it went" lists the merchants,
  more on request.

  Tap a transaction to correct its description, category (optionally adding a rule so similar ones follow) or what
  it counts as, link it to a budget, or delete it (with Undo; deleted ones can be restored from the ⋮ menu). Add
  transactions by hand, import a bank statement (CSV) from the phone, or share one to the app from another app, and
  re-apply the rules.
- **Budgets:** search and filter by status; add, edit and delete budgets: total, monthly, yearly or custom dates,
  counting linked transactions, some categories, or a merchant.

- **More:** the rules (category rules in order, with search and moving up and down; flow rules), categories
  (names and colours; deleting one moves what used it), accounts (type, statement format; deleting empty ones), and
  your account (change the password, sign out everywhere else, sign out).

## Reaching the server

The backend is only on your tailnet, so the phone needs the [Tailscale app](https://tailscale.com/download/android)
on. When it is off, the app says it can't reach the server.

## Installing

Every push builds a debug APK: open the workflow run in the repository's **Actions** tab and download the
`finance-insights-debug-apk` artifact, then open the APK on the phone (allow installing from that app when asked).

## Building

- JDK 21 and the Android SDK (platform 37.2; Gradle downloads what is missing when the SDK's licences are accepted).
- `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

`core` is plain Kotlin: the API's models and client (Retrofit, OkHttp with a cookie jar kept in the app's private
storage) and the logic the screens share (date presets, dashboard figures), tested on the JVM. `app` is the Android
app; its tests include screenshot tests (Robolectric and Roborazzi) that render the screens with responses recorded
from a real backend, written to `app/build/shots` and uploaded by CI.
