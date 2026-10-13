# Personal Finance Insights for Android

A native Android app (Kotlin, Jetpack Compose, Material 3) for the Personal Finance Insights backend: the same
account and data as the web app, on your phone.

## What it does

The app is the shell of Hestia, a personal "everything app". Finance is its first module, **Money**.

The bar at the bottom has five items: **Today**, **Timeline**, **Capture** (the round + in the middle), **Modules** and
**Search**. Each keeps its own back stack, and Back from any of them returns to Today.

- **Today** shows the Money module's four headline tiles and its "Needs attention" list, then a card for the modules
  that are coming. A tile or an item opens its Money page.
- **Modules** lists the modules. Money opens its home; Documents, Home, People, Journal and Tasks are "Coming soon".
  Under them are the app's settings and your account.
- **Money** (its home) has the four tiles and a row for each finance page: Dashboard, Net worth and Review; Transactions,
  Budgets and Subscriptions; and Accounts, Categories, Tags and Rules. Back from a finance page returns to the Money
  home, and then to where you opened it from.
- **Capture** has four buttons. "Expense" opens the "Add transaction" form; Photo, Scan and Voice are coming soon.
- **Timeline** and **Search** are placeholders for now.

**Sign in** with the server's address (where you open the web app, e.g. `https://finance.example.ts.net`) and your
username and password. The session is kept between launches, for 14 days of inactivity like the web's
"Remember me".

The finance pages:

- **Dashboard** has headline tiles: spent this month against a usual month at the same point, left over this month, net
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
  it counts as, its note and tags, or to link it to a budget. Swipe a row:
  - left to delete it, with Undo; deleted ones can be restored from the ⋮ menu;
  - right to change its category.

  A long press ticks a row, to change many at once.

  You can also add transactions by hand. Import bank statements (CSV or PDF; several at once) from the phone, or
  share a CSV or PDF to the app from another app, then re-apply the rules.
- **Budgets:** search and filter by status; add, edit and delete budgets: total, monthly, yearly or custom dates,
  counting linked transactions, some categories, or a merchant.
- **Net worth:** what the accounts and investments hold, less what the cards owe, over the last twelve months, with
  each account's latest balance.
- **Subscriptions** (payments that come again and again, what they cost and when the next is due) and **Review** (a
  month or a year against the one before).
- Setup: **Accounts** (type, statement format, balances, and for a TFSA, RRSP or FHSA the year's contribution room),
  **Categories**, **Tags** and **Rules** (category rules in order, which you can drag; flow rules; and "Sort them" for
  the merchants no rule names).
- Settings and your account (change the password, sign out everywhere else, sign out) are on the Modules page.
- **Settings:**
  - colours from the wallpaper;
  - a lock on opening (fingerprint, face or the phone's PIN, again after a minute away);
  - hiding amounts on the widget.

  Four kinds of notification can be turned on: budget alerts (at 80% and when used up), subscription price
  changes, the monthly review, and a reminder when no statement has been imported for more than a week. A background
  check runs every few hours while any of them is on.
- **Widget and shortcut:** a home-screen widget shows this month's spending and the fullest budgets; a tap opens the
  Money dashboard. Long-pressing the app's icon offers "Add transaction". The widget, the shortcut, the notifications
  and shared statements open their page over the Money home.

## Tablets and foldables

From a small tablet's width (or a phone on its side) the five items are a rail at the side. On a tablet:

- Home's cards sit in two columns, under the four headline tiles in one row;
- Transactions are a centred list, a tapped one opening over it as on a phone;
- Budgets are cards side by side;
- Net worth's figures are tiles in a row, over cards for the months and the accounts;
- Today, Modules, the Money home, Settings and the setup pages are a centred column rather than rows across the whole
  screen.

## Reaching the server

The backend is only on your tailnet, so the phone needs the [Tailscale app](https://tailscale.com/download/android)
on. When it is off, the app says it can't reach the server.

## Installing

Every push builds a debug APK: open the workflow run in the repository's **Actions** tab and download the
`finance-insights-debug-apk` artifact, then open the APK on the phone (allow installing from that app when asked).
An APK from `main` stays for 30 days. If the last one has expired, open the **android** workflow in the **Actions**
tab and click **Run workflow** on `main` (see [CI artifacts](#ci-artifacts)).

## Building

- JDK 21 and the Android SDK (platform 37.2; Gradle downloads what is missing when the SDK's licences are accepted).
- `./gradlew :core:common:test :core:network:test :core:finance:test :core:ui:lintDebug :feature:finance:testDebugUnitTest :feature:finance:lintDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

This is the command that CI runs. Lint fails the build on any new warning. `app/lint-baseline.xml` holds the one left on
purpose: the target SDK, which is raised as a change of its own.

### CI artifacts

Artifacts count against the storage quota of the GitHub account. When the quota is full, every upload fails.
CI keeps the uploads small and short:

| Artifact | When CI uploads it | Kept for |
| --- | --- | --- |
| `finance-insights-debug-apk` | Every run that builds the APK | 30 days on `main` and manual runs, 7 days on pull requests |
| `screenshots` | Pull requests, and runs that fail | 7 days |
| `reports` | Runs that fail | 7 days |

A failed upload does not fail the run. The step **Warn about failed uploads** then writes a warning annotation
(`Artifact upload failed`). The usual cause is a full quota. To free space, delete old artifacts in the
**Actions** tab, or with `gh api -X DELETE repos/guibilar/PersonalFinanceInsightsAndroid/actions/artifacts/<id>`.
Each new upload step must set `retention-days` and `continue-on-error: true`, and the warning step must name it.

## Modules

The app is split into Gradle modules, so that more modules (documents, tasks, and so on) can be added next to finance.

| Module | Kind | Contents |
| --- | --- | --- |
| `:core:common` | Plain Kotlin | Amounts and days as the app shows them (`Money`, `formatDay`) and privacy mode. |
| `:core:network` | Plain Kotlin | The HTTP client (Retrofit, OkHttp) with the cookie jar kept in the app's private storage, the server's address, and API errors. |
| `:core:finance` | Plain Kotlin | The finance API's models and client, and the logic that the finance screens share (date presets, dashboard figures, alerts, attention items). Its test fixtures hold the fake API with responses recorded from a real backend. |
| `:core:ui` | Android library | The theme, the common components, the charts, and the frame of the settings pages. |
| `:feature:finance` | Android library | The Money module: its home, the finance screens, their navigation graph, the finance notifications, the widget, and the finance settings (`FinancePreferences`). |
| `:app` | Android app | `MainActivity`, the application class, the module registry, the navigation host with the bar (Today, Timeline, Capture, Modules, Search), sign-in, the lock, the account page and the app's settings. |

How they connect:

- `:feature:finance` does not depend on `:app`. The app calls `financeGraph(...)` in its navigation host. The bar shows
  the finance pages under Modules (`isFinancePage()`). `rememberFinanceRequests(...)` sends shared statements and quick
  actions (shortcut, widget, notifications, Capture) to the finance pages, over the Money home (`openFinancePage(...)`).
  Today shows the Money tiles and "Needs attention" with `MoneyGlance(...)`.
- The module registry (`MODULES` in `:app`, `ui/modules/ModuleRegistry.kt`) lists each module with its name, icon,
  home route and status. The Modules page and Today read it.
- The widget and the notifications need the session and the app's settings. They get them through the `FinanceHost`
  interface, which the application class implements.
- The plain Kotlin modules are tested on the JVM. The Android modules' tests include screenshot tests (Robolectric and
  Roborazzi) that render the screens with the recorded responses. They write to `<module>/build/shots`. CI uploads
  them on pull requests and when a run fails. The finance module's tests run in `TestFinanceApp` (see `feature/finance/src/test/resources/robolectric.properties`).
- To add a module (for example `:feature:documents`), make an Android library that depends on `:core:ui` and
  `:core:network`, give it a graph function like `financeGraph` with a home route, and call it from the app's
  navigation host. In `MODULES`, give the module its route and the status `Active`, and add its pages to the Modules
  item of the bar. Nothing in `:feature:finance` changes.
