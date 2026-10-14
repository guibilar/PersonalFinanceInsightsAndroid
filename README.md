# Personal Finance Insights for Android

A native Android app (Kotlin, Jetpack Compose, Material 3) for the Personal Finance Insights backend: the same
account and data as the web app, on your phone.

## What it does

The app is the shell of Hestia, a personal "everything app". Finance is its first module, **Money**.

The bar at the bottom has five items: **Today**, **Timeline**, **Capture** (the round + in the middle), **Modules** and
**Search**. Each keeps its own back stack, and Back from any of them returns to Today.

- **Today** shows the cards that the server gives for today (`GET /api/today` on Hestia.Api): each module's tiles
  (Money first, with its four headline tiles), then one "Needs attention" list, then a card for the modules that are
  coming. A tile or an item opens its page. Pull down to get new cards. If a module could not load all its figures,
  Today says so; if a module failed, it has its own "Try again".
- **Modules** lists the modules. Money opens its home and Documents its list; Home, People, Journal and Tasks are
  "Coming soon". Under them are the app's settings and your account.
- **Money** (its home) has the four tiles and a row for each finance page: Dashboard, Net worth and Review; Transactions,
  Budgets and Subscriptions; and Accounts, Categories, Tags and Rules. Back from a finance page returns to the Money
  home, and then to where you opened it from.
- **Capture** has four buttons. "Scan" opens the document scanner and "Photo" the photo picker, for a new document;
  "Expense" opens the "Add transaction" form; Voice is coming soon.
- **Timeline** and **Search** are placeholders for now.

**Documents** (on Hestia.Api, `/api/documents`) keeps receipts, warranties, leases, tax slips, insurance policies and
manuals:

- The list has a search (in the titles, tags, notes and the text that the server read), a chip for each kind, the
  documents that expire in the next 30 days, then all documents, newest first.
- "Add document" (and the scan button at the top, and Capture) takes the files from the document scanner (ML Kit: the
  camera finds the page edges and makes one PDF; it needs Google Play services, and no camera permission), the photo
  picker, or the file picker (PDFs and images). Photos go up as JPEGs of at most 2500 pixels on their long side,
  turned upright; a scanned PDF goes as it is. The app checks the size (25 MB per file, 100 MB in all) and the type
  (PDF, JPEG, PNG, HEIC, WebP) before the upload.
- A document shows its details (edit them, or delete the document), its files (an image opens in the app, a PDF in
  the phone's PDF viewer, from the app's cache), the text that the server read (OCR; while it runs, the page asks
  again every 3 seconds for 2 minutes; "Run OCR again" when it failed), and the transactions it is linked to. A tap
  on a linked transaction opens it in Money. "Link to a transaction" picks one from the latest, or by a search.
- A Today item for a document that expires soon opens the document.
- A transaction in Money shows its documents, and can add or link one (below, "Transactions").

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
  it counts as, its note and tags, or to link it to a budget. Its "Documents" section lists the documents linked to
  it (a tap opens one). "Add document" opens the Documents upload: the new document starts as a receipt, and after the
  upload it is linked to the transaction and the page goes back (if the link fails, the document opens, with a
  message). "Link a document" links one from the newest documents or a search. Swipe a row:
  - left to delete it, with Undo; deleted ones can be restored from the ⋮ menu;
  - right to change its category.

  A long press ticks a row, to change many at once.

  **A link to one transaction** (from a document, a Today item `/money/transactions?id=<id>`, or "View" in the
  message after an add) opens the list on the day and account of the transaction, with no other filter. The row comes
  into view and is highlighted for 3 seconds (with "Remove animations" on, it does not fade). If the day has more rows
  than one page, the next pages load until the row is there. If the list cannot show it, a short message is over the
  normal list: "The linked transaction does not exist." (also for an id that is not a GUID, with no call to the
  server), "The linked transaction is deleted." (with "Show deleted transactions"), or "Could not load the linked
  transaction.". The message goes when you close it, change the filters, or open the deleted list.

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
- `./gradlew :core:common:test :core:network:test :core:finance:test :core:today:test :core:documents:test :core:ui:lintDebug :feature:finance:testDebugUnitTest :feature:finance:lintDebug :feature:documents:testDebugUnitTest :feature:documents:lintDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

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
| `:core:common` | Plain Kotlin | Amounts and days as the app shows them (`Money`, `formatDay`), privacy mode, and `DataChanges` (the data changed in the app). |
| `:core:network` | Plain Kotlin | The HTTP client (Retrofit, OkHttp) with the cookie jar kept in the app's private storage, the server's address, and API errors. Sign-in (`AuthApi`, the `/api/Auth/*` endpoints) and the signed-in client (`ApiClient`) that all modules share. Its test fixtures hold the fake `AuthApi` and `fakeApiClient(...)`. |
| `:core:finance` | Plain Kotlin | The finance API's models and client, and the logic that the finance screens share (date presets, dashboard figures, alerts, attention items). Its test fixtures hold the fake API with responses recorded from a real backend. |
| `:core:today` | Plain Kotlin | The Today API (`TodayApi`, `GET /api/today`) and its models, and the filler that puts the app's money, percent and date formats into the card texts (it hides amounts in privacy mode). Its test fixtures hold the fake `TodayApi` and sample answers. |
| `:core:documents` | Plain Kotlin | The Documents API (`DocumentsApi`, `/api/documents`) and its models, the upload form (`DocumentUpload`, multipart) with the checks of size and type, and the expiry and photo-size rules. Its test fixtures hold the fake `DocumentsApi` and sample documents. |
| `:core:ui` | Android library | The theme, the common components, the charts, and the frame of the settings pages. |
| `:feature:finance` | Android library | The Money module: its home, the finance screens, their navigation graph, the finance notifications, the widget, and the finance settings (`FinancePreferences`). |
| `:feature:documents` | Android library | The Documents module: the list, a document, the upload with the scanner and the pickers, the photo scaling, and its navigation graph (`documentsGraph`). It depends on the core modules only; the transactions of its links come from `:core:finance`. |
| `:app` | Android app | `MainActivity`, the application class, the module registry, the navigation host with the bar (Today, Timeline, Capture, Modules, Search), sign-in, the lock, the account page and the app's settings. |

How they connect:

- `:feature:finance` does not depend on `:app`. The app calls `financeGraph(...)` in its navigation host. The bar shows
  the finance pages under Modules (`isFinancePage()`). `rememberFinanceRequests(...)` sends shared statements and quick
  actions (shortcut, widget, notifications, Capture) to the finance pages, over the Money home (`openFinancePage(...)`).
  The Money home shows its tiles with `MoneyGlance(...)`.
- **Today** (`:app`, `ui/today`) reads only the Today API. The server owns the card rules, so Today and the web show
  the same cards. A card's link is a web route (for example `/money/budgets`); `todayDestination(...)` in
  `TodayLinks.kt` is the one place that turns it into a page of the app. A link that the app does not know opens the
  Money home. A module with cards gets its own section on Today, with no app change; a registry module with no cards
  stays under "Coming soon". After an import or a new transaction (`DataChanges.changed()`), the next Today load asks
  the server for new cards (`refresh=true`), and not its 30-second cache. `todayDestination(...)` gives a
  `TodayDestination`: a Money page, one transaction (`/money/transactions?id=<id>`), the Documents list
  (`/documents`), or one document (`/documents/<id>`).
- **One transaction:** `openTransaction(id)` in `:feature:finance` is the one way to open a transaction from another
  page. Other modules get it through the app (Documents takes an `openTransaction` function), never by a dependency
  on `:feature:finance`. The look-up (`findTransaction(id)` in `:core:finance`: the live transactions, then the deleted
  ones) serves the Transactions page and the document page.
- `:feature:documents` gives `documentsGraph(...)` and `openDocuments()`, `openDocument(id)` and `addDocument(source)`
  for the other pages (Today, Modules, Capture). It makes its APIs from the signed-in client. A linked transaction
  opens that transaction through the app (`openTransaction(id)`).
- The module registry (`MODULES` in `:app`, `ui/modules/ModuleRegistry.kt`) lists each module with its name, icon,
  home route and status. The Modules page and Today read it.
- Sign-in is in `:core:network`, not in a module. When you sign in, the app gets one `ApiClient` for the server. It has
  one HTTP client and one cookie jar. Each module makes its Retrofit interface from it in one line:
  `client.api<FinanceApi>()`. The client makes each interface one time and keeps it. `MainScreen` gets the client.
- **A transaction's documents:** `:feature:finance` reads them with the Documents API from `:core:documents`. The
  pages (open a document, the upload for one transaction) come from the app through the `FinanceDocuments`
  interface (`AppFinanceDocuments` in `:app`), which `financeGraph(documents = …)` takes. Without it, the section
  does not show. `addDocumentTo(transactionId)` in `:feature:documents` opens the upload that links the new document.
- **A read-only user (T60):** `GET /api/Auth/me` and the sign-in give a `scope` (`full` or `read-only`;
  `CurrentUser.canWrite`). A missing scope (an older server) counts as `full`. `AuthState.SignedIn.canWrite` goes to
  `MainScreen(canWrite = …)`, which passes it to Capture (the buttons are disabled), `documentsGraph(canWrite = …)`,
  and `AppFinanceDocuments.canWrite` (the "Documents" section of a transaction). In tests, `FakeAuthApi.scope` sets it.
- The widget and the notifications need the session and the app's settings. They get them through the `FinanceHost`
  interface, which the application class implements.
- The plain Kotlin modules are tested on the JVM. The Android modules' tests include screenshot tests (Robolectric and
  Roborazzi) that render the screens with the recorded responses. They write to `<module>/build/shots`. CI uploads
  them on pull requests and when a run fails. The finance module's tests run in `TestFinanceApp` (see `feature/finance/src/test/resources/robolectric.properties`).
- To add a module (for example `:feature:documents`), make an Android library that depends on `:core:ui` and
  `:core:network`, give it a graph function like `financeGraph` with a home route, and call it from the app's
  navigation host. Make the module's API with `client.api<YourApi>()`. In tests, use `fakeApiClient(yourFake)`. In `MODULES`, give the module its route and the status `Active`, and add its pages to the Modules
  item of the bar. Nothing in `:feature:finance` changes.
