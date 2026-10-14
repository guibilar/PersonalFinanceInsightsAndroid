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
- **Modules** lists the modules. Money opens its home, Documents its list, and Tasks its screen; Home, People and
  Journal are "Coming soon". Under them are the app's settings and your account.
- **Money** (its home) has the four tiles, the Money items of "Needs attention" (the same as on Today), and a row for each finance page: Dashboard, Net worth and Review; Transactions,
  Budgets and Subscriptions; and Accounts, Categories, Tags and Rules. Back from a finance page returns to the Money
  home, and then to where you opened it from.
- **Capture** has five buttons. "Task" opens the quick add of Tasks; "Expense" opens the "Add transaction" form;
  "Scan" opens the document scanner and "Photo" the photo picker, for a new document; Voice is coming soon.
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
- A document shows its tasks (a tap opens one in Tasks). On a document with an expiry date, "Add task" adds
  "Renew <title>", due 14 days before the expiry (or today, when that day is past), linked to the document.

**Tasks** (on Hestia.Api, `/api/tasks`) keeps to-dos and repeated home tasks:

- The quick add at the top: type a title and press Enter. The task is due today; the chip under the field picks
  tomorrow, next week, another day, or no date.
- The open tasks are in sections: "Overdue", "Today", "Upcoming" (the next 7 days), "Later and no date", and
  "Recurring" (each repeated task with its rule and next date). A repeated task that is due shows in "Overdue" or
  "Today" too. "Done" (the last 20 done tasks) is closed until you tap it. "Today" is the server's day
  (`GET /api/tasks/summary`), so the sections match Today and the alerts.
- A checkbox marks a task done; the message offers "Undo". A repeated task moves to its next date and stays open.
  Unchecking a done task opens it again.
- A tap on a task opens its sheet: title, notes, due date, tags, the repeat (every N days, weeks, months or years;
  "after each done" or "on a fixed schedule"), "Mark done" and "Undo the last done", the links (a tap opens the
  document or the transaction; "Link to a document" and "Link to a transaction" pick one from a search), the last
  times it was done, and delete.
- Today: the "Tasks" tile opens the screen. An overdue task (`/tasks/<id>`) opens its sheet; "N tasks are overdue"
  (`/tasks?due=overdue`) shows only the overdue tasks, with "Show all".

**Sign in** with the server's address (where you open the web app, e.g. `https://finance.example.ts.net`) and your
username and password. The session is kept between launches, for 14 days of inactivity like the web's
"Remember me".

The finance pages:

- **Dashboard** has headline tiles: spent this month against a usual month at the same point, left over this month, net
  worth and budgets over their limit. Then **Needs attention**, where each item opens the place to deal with it. The
  items come from the server (`GET /api/alerts`, the Money alerts of Hestia's Alerts module), so the Money home, the
  dashboard and Today show the same items; the app has no copy of the rules. Today the server finds:
  - budgets over their limit;
  - no statement imported for more than a week;
  - an account whose statements stop before last month;
  - a subscription whose price changed;
  - transactions left in Other because no rule names their merchant.

  If some data did not load in the server's last check, a short note says that the list can be out of date. If the
  list could not load, the card says so, with "Try again". Pull to refresh, "Try again", and the first load after an
  import or a new transaction ask the server to check its rules first (`refresh=true`).

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

  **Notifications:**
  - **Needs attention** (T68): the phone asks the server for its alerts (`GET /api/alerts`) every hour, over
    Tailscale, and shows a notification for each new alert: a budget over its limit, a price change, statements to
    import, unsorted transactions, a document that expires, an overdue task. More than 3 new alerts give one summary.
    A notification goes away when its alert is resolved. A tap opens the alert's page, as on Today. A switch per
    module (Money, Documents, Tasks). The first poll after install, sign-in, or turning it on only notes the open alerts and notifies
    nothing. "Hide amounts" hides the amounts in these notifications too, and with the app lock on the lock screen
    shows only the kind of alert.
  - **Budget warnings** (at 80%) and the **monthly review** are checked on the phone every few hours. The server has
    no alert for them yet.
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
- `./gradlew :core:common:test :core:network:test :core:finance:test :core:today:test :core:documents:test :core:tasks:test :core:ui:lintDebug :feature:finance:testDebugUnitTest :feature:finance:lintDebug :feature:documents:testDebugUnitTest :feature:documents:lintDebug :feature:tasks:testDebugUnitTest :feature:tasks:lintDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

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

### CI annotations

CI writes its results as annotations, so you can see them on the run page (and with
`gh api repos/guibilar/PersonalFinanceInsightsAndroid/check-runs/<id>/annotations`) without the logs or artifacts.
The steps run also when the tests fail.

| Annotation | Step | Contents |
| --- | --- | --- |
| Notice `Tests :<module>` | Test results as annotations | Totals of one module: tests, passed, failed, skipped, and time |
| Error `Failed test :<module>` | Test results as annotations | The test name and the first lines of its message (at most 9; then one error with the number of the others) |
| Notice `Screenshots` | Screenshot summary as an annotation | The number of images, and the short hash (SHA-256, 10 characters) of each new, changed, or removed image, compared with `main` |

- The scripts are `scripts/test-annotations.py` (JUnit XML in `<module>/build/test-results/`) and
  `scripts/screenshot-summary.py` (PNG files in `<module>/build/shots/`).
- Gradle runs with `--continue`, so a failure in one module does not hide the results of the others.
- A run on `main` saves its list of screenshot hashes in the Actions cache (`screenshots-<commit>`). A pull request
  restores the list of its base commit, or else the newest list of `main`. A hash that changes shows a changed
  image. Download the `screenshots` artifact to look at it.
- The screenshot tests (`:app`, `:feature:finance`, `:feature:documents`, `:feature:tasks`) are not cacheable, because the images are
  not a declared output. Thus CI always renders them.
- GitHub shows at most 10 notices and 10 errors per step. Keep this limit in mind when you add a module. With
  `:core:tasks` and `:feature:tasks`, the app has 10 modules with tests: one more puts the totals into one notice.

## Modules

The app is split into Gradle modules, so that more modules (documents, tasks, and so on) can be added next to finance.

| Module | Kind | Contents |
| --- | --- | --- |
| `:core:common` | Plain Kotlin | Amounts and days as the app shows them (`Money`, `formatDay`), privacy mode, and `DataChanges` (the data changed in the app). |
| `:core:network` | Plain Kotlin | The HTTP client (Retrofit, OkHttp) with the cookie jar kept in the app's private storage, the server's address, and API errors. Sign-in (`AuthApi`, the `/api/Auth/*` endpoints) and the signed-in client (`ApiClient`) that all modules share. Its test fixtures hold the fake `AuthApi` and `fakeApiClient(...)`. |
| `:core:finance` | Plain Kotlin | The finance API's models and client, and the logic that the finance screens share (date presets, dashboard figures, the rules of the phone's finance notifications). Its test fixtures hold the fake API with responses recorded from a real backend. |
| `:core:today` | Plain Kotlin | The Today API (`TodayApi`, `GET /api/today`) and the Alerts API (`AlertsApi`, `GET /api/alerts`) with their models, the filler that puts the app's money, percent and date formats into the card texts (it hides amounts in privacy mode), and the one map from a card's or an alert's link to a page of the app (`todayDestination`). Its test fixtures hold the fake `TodayApi` and `AlertsApi`, and sample answers. |
| `:core:documents` | Plain Kotlin | The Documents API (`DocumentsApi`, `/api/documents`) and its models, the upload form (`DocumentUpload`, multipart) with the checks of size and type, and the expiry and photo-size rules. Its test fixtures hold the fake `DocumentsApi` and sample documents. |
| `:core:tasks` | Plain Kotlin | The Tasks API (`TasksApi`, `/api/tasks`) and its models, and the rules of the Tasks screen: the sections, the short labels of due dates and repeats, the repeat form (`TaskDraft`), the quick add, and the "Renew" task of a document. Its test fixtures hold the fake `TasksApi` (done, undo and the repeat rules in memory) and sample tasks. |
| `:core:ui` | Android library | The theme, the common components, the charts, and the frame of the settings pages. |
| `:feature:finance` | Android library | The Money module: its home, the finance screens, their navigation graph, the finance notifications, the widget, and the finance settings (`FinancePreferences`). |
| `:feature:documents` | Android library | The Documents module: the list, a document, the upload with the scanner and the pickers, the photo scaling, and its navigation graph (`documentsGraph`). It depends on the core modules only; the transactions of its links come from `:core:finance`. |
| `:feature:tasks` | Android library | The Tasks module: the Tasks screen (quick add, sections, done and undo), the task sheet (details, repeat, links, completions, delete), and its navigation graph (`tasksGraph`). It depends on the core modules only: linked documents come from `:core:documents`, linked transactions from `:core:finance`. |
| `:app` | Android app | `MainActivity`, the application class, the module registry, the navigation host with the bar (Today, Timeline, Capture, Modules, Search), sign-in, the lock, the account page, the app's settings, and the alert notifications (`notifications/`). |

How they connect:

- `:feature:finance` does not depend on `:app`. The app calls `financeGraph(...)` in its navigation host. The bar shows
  the finance pages under Modules (`isFinancePage()`). `rememberFinanceRequests(...)` sends shared statements and quick
  actions (shortcut, widget, notifications, Capture) to the finance pages, over the Money home (`openFinancePage(...)`).
  The Money home shows its tiles and its "Needs attention" items with `MoneyGlance(...)`. The items are the server's Money
  alerts (`AlertsApi`); their links go through the same `todayDestination(...)` as Today, and `MoneyLink.route` gives
  each finance page's route.
- **Today** (`:app`, `ui/today`) reads only the Today API. The server owns the card rules, so Today and the web show
  the same cards. A card's link is a web route (for example `/money/budgets`); `todayDestination(...)` in
  `TodayLinks.kt` (in `:core:today`) is the one place that turns it into a page of the app. A link that the app does not know opens the
  Money home. A module with cards gets its own section on Today, with no app change; a registry module with no cards
  stays under "Coming soon". After an import or a new transaction (`DataChanges.changed()`), the next Today load asks
  the server for new cards (`refresh=true`), and not its 30-second cache. `todayDestination(...)` gives a
  `TodayDestination`: a Money page, one transaction (`/money/transactions?id=<id>`), the Documents list
  (`/documents`), one document (`/documents/<id>`), the Tasks screen (`/tasks`, or `/tasks?due=overdue` for the
  overdue tasks only), or one task (`/tasks/<id>`). "Statement to import" for one account
  (`/money/transactions?importAccount=<id>#import`, T63) opens the import sheet with that account picked
  (`TodayDestination.Money.importAccount`, then `FinanceRequests.importAccount`). An account that is not in the list
  picks nothing, as before.
- **One transaction:** `openTransaction(id)` in `:feature:finance` is the one way to open a transaction from another
  page. Other modules get it through the app (Documents takes an `openTransaction` function), never by a dependency
  on `:feature:finance`. The look-up (`findTransaction(id)` in `:core:finance`: the live transactions, then the deleted
  ones) serves the Transactions page and the document page.
- `:feature:documents` gives `documentsGraph(...)` and `openDocuments()`, `openDocument(id)` and `addDocument(source)`
  for the other pages (Today, Modules, Capture). It makes its APIs from the signed-in client. A linked transaction
  opens that transaction through the app (`openTransaction(id)`).
- `:feature:tasks` gives `tasksGraph(...)` and `openTasks(overdueOnly)`, `openTask(id)` and `addTask()` for the other
  pages (Today, Modules, Capture, a notification). One route, `TasksRoute`, holds the overdue filter, the task to open
  and the quick add. A linked document or transaction opens through the app (`openDocument`, `openTransaction`).
- **A document's tasks (T84):** `:feature:documents` reads them with the Tasks API from `:core:tasks`. Opening a task
  comes from the app through the `DocumentTasks` interface (`AppDocumentTasks` in `:app`), which
  `documentsGraph(tasks = …)` takes. Without it, the section does not show.
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
  `tasksGraph(canWrite = …)` (no quick add, no checkboxes, the task sheet as text), `AppFinanceDocuments.canWrite`
  (the "Documents" section of a transaction), and `AppDocumentTasks.canWrite` (the "Tasks" section of a document). In tests, `FakeAuthApi.scope` sets it.
- The widget and the local finance notifications need the session and the app's settings. They get them through the
  `FinanceHost` interface, which the application class implements.
- **Alert notifications (T68)** are in `:app` (`notifications/`), because they serve all modules.
  `AlertPollWorker` (WorkManager, every 60 minutes, when online) calls `GET /api/alerts`.
  `planAlertNotifications(...)` in `:core:today` decides what to show and what to remove. `SeenAlertStore`
  (DataStore) keeps the alert ids of the last poll and the account. `AlertNotificationSettings` holds the switches.
  A tap sends the alert's link to `MainActivity` (`ACTION_OPEN_ALERT`); `MainScreen` opens it with
  `todayDestination(...)`. A failed poll (Tailscale off, or the session ended) changes nothing.
- The plain Kotlin modules are tested on the JVM. The Android modules' tests include screenshot tests (Robolectric and
  Roborazzi) that render the screens with the recorded responses. They write to `<module>/build/shots`. CI uploads
  them on pull requests and when a run fails. The finance module's tests run in `TestFinanceApp` (see `feature/finance/src/test/resources/robolectric.properties`).
- To add a module (for example `:feature:documents`), make an Android library that depends on `:core:ui` and
  `:core:network`, give it a graph function like `financeGraph` with a home route, and call it from the app's
  navigation host. Make the module's API with `client.api<YourApi>()`. In tests, use `fakeApiClient(yourFake)`. In `MODULES`, give the module its route and the status `Active`, and add its pages to the Modules
  item of the bar. Nothing in `:feature:finance` changes.
