# Personal Finance Insights for Android

A native Android app (Kotlin, Jetpack Compose, Material 3) for the Personal Finance Insights backend: the same
account and data as the web app, on your phone.

## What it does

Five tabs: Home, Transactions, Budgets, Net worth and More.

- **Sign in** with the server's address (where you open the web app, e.g. `https://finance.example.ts.net`) and your
  username and password. The session is kept between launches, for 14 days of inactivity like the web's
  "Remember me".
- **Home** has headline tiles: spent this month against a usual month at the same point, left over this month, net
  worth and budgets over their limit. Then **Needs attention**, where each item opens the place to deal with it:
  - budgets over their limit;
  - no statement imported for more than a week;
  - an account whose statements stop before last month;
  - a subscription whose price changed;
  - transactions left in Other because no rule names their merchant.

  The cards below cover:
  - where the month is heading (spent, payments still due, usual spending for the days left);
  - this month by category, or each category against a usual month;
  - monthly spending by category (tap a month for its categories, a category for its transactions week by week);
  - income and spending as a chart or a table;
  - investments with this year's room;
  - budgets at a glance;
  - recent transactions.
- **Transactions:** search (descriptions and notes), filters (date presets or a custom range, category, tag,
  account, what it counts as) and sorting, with a list that loads as you scroll. The page totals whatever the
  filters pick, and "Over time" splits that per week, month, quarter or year, with the running total; tap a bar to
  see that period. "Where it went" lists the merchants, more on request.

  Tap a transaction to correct its description, category (optionally adding a rule so similar ones follow), what
  it counts as, its note and tags, or to link it to a budget. On a tablet or an unfolded phone it opens beside the
  list. Swipe a row:
  - left to delete it, with Undo; deleted ones can be restored from the ⋮ menu;
  - right to change its category.

  A long press ticks a row, to change many at once.

  You can also add transactions by hand. Import bank statements (CSV or PDF; several at once) from the phone, or
  share a CSV or PDF to the app from another app, then re-apply the rules.
- **Budgets:** search and filter by status; add, edit and delete budgets: total, monthly, yearly or custom dates,
  counting linked transactions, some categories, or a merchant.
- **Net worth:** what the accounts and investments hold, less what the cards owe, over the last twelve months, with
  each account's latest balance.
- **More** has three sections:
  - Insights: **Subscriptions** (payments that come again and again, what they cost and when the next is due) and
    **Review** (a month or a year against the one before).
  - Setup: **Accounts** (type, statement format, balances, and for a TFSA, RRSP or FHSA the year's contribution
    room), **Categories**, **Tags** and **Rules** (category rules in order, which you can drag; flow rules; and
    "Sort them" for the merchants no rule names).
  - Settings and your account (change the password, sign out everywhere else, sign out).
- **Settings:**
  - colours from the wallpaper;
  - a lock on opening (fingerprint, face or the phone's PIN, again after a minute away);
  - hiding amounts on the widget.

  Four kinds of notification can be turned on: budget alerts (at 80% and when used up), subscription price
  changes, the monthly review, and a reminder when no statement has been imported for more than a week. A background
  check runs every few hours while any of them is on.
- **Widget and shortcut:** a home-screen widget shows this month's spending and the fullest budgets. Long-pressing the
  app's icon offers "Add transaction".

## Reaching the server

The backend is only on your tailnet, so the phone needs the [Tailscale app](https://tailscale.com/download/android)
on. When it is off, the app says it can't reach the server.

## Installing

Every push builds a debug APK: open the workflow run in the repository's **Actions** tab and download the
`finance-insights-debug-apk` artifact, then open the APK on the phone (allow installing from that app when asked).

## Building

- JDK 21 and the Android SDK (platform 37.2; Gradle downloads what is missing when the SDK's licences are accepted).
- `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

Lint fails the build on any new warning. `app/lint-baseline.xml` holds the one left on purpose: the target SDK, which
is raised as a change of its own.

`core` is plain Kotlin: the API's models and client (Retrofit, OkHttp with a cookie jar kept in the app's private
storage) and the logic the screens share (date presets, dashboard figures, alerts, attention items, day and money
formatting), tested on the JVM. `app` is the Android app; its tests include screenshot tests (Robolectric and
Roborazzi) that render the screens with responses recorded from a real backend, written to `app/build/shots` and
uploaded by CI.
