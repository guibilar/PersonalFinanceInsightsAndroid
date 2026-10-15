# Personal Finance Insights for Android

A native Android app (Kotlin, Jetpack Compose, Material 3) for the Personal Finance Insights backend: the same
account and data as the web app, on your phone.

## What it does

The app is the shell of Hestia, a personal "everything app". Finance is its first module, **Money**.

The bar at the bottom has five items: **Today**, **Timeline**, **Capture** (the round + in the middle), **Modules** and
**Search**. Each keeps its own back stack, and Back from any of them returns to Today.

- **Today** shows the cards that the server gives for today (`GET /api/today` on Hestia.Api): each module's tiles
  (Money first, with its four headline tiles), then one "Needs attention" list. A tile or an item opens its page. Pull down to get new cards. If a module could not load all its figures,
  Today says so; if a module failed, it has its own "Try again".
- **Modules** lists the modules. Money opens its home, Documents its list, Tasks, Home, People and Journal their
  screens. Under them are the app's settings and your account.
- **Money** (its home) has the four tiles, the Money items of "Needs attention" (the same as on Today), and a row for each finance page: Dashboard, Net worth and Review; Transactions,
  Budgets and Subscriptions; and Accounts, Categories, Tags and Rules. Back from a finance page returns to the Money
  home, and then to where you opened it from.
- **Capture** has eight buttons. "Task" opens the quick add of Tasks; "Item" opens a new item of Home; "Person" opens a
  new person; "Journal" opens today's journal entry; "Expense" opens
  the "Add transaction" form;
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
- A task shows its items of the home (a tap opens one); "Link to an item" picks one from a search.

**Home** (on Hestia.Api, `/api/home`) keeps the things in the home: where they are, what they cost, their serial
numbers, warranties, receipts and maintenance:

- The Home screen has a search over all items, "Maintenance" (the open repeated tasks that link to an item, the next
  due first, with the days left; a tap opens the task), "Inventory" with the item count and the total value, the
  rooms (each with its count and value, and "No room" for the items without one), "Add room", and "Share list for
  insurance": the server's CSV file goes to the share sheet (email, Drive, a messenger). A read-only user can share it
  too. "1 warranty ends in the next 30 days" lists those items.
- A room lists its items by name, with the value and the warranty when it ends soon or ended. Rename the room, or
  delete it when it has no items.
- An item shows its photo (the first image of its documents), the warranty ("Under warranty until …", "Warranty ends
  in 17 days", "Warranty ended …"), its details, its documents (a photo shows as a small picture), its purchase, and
  its maintenance tasks. "Add receipt or photo" takes a photo with the camera or picks one; it goes up to Documents
  (as Documents scales photos) and links to the item. "Link a document" and "Link the purchase" pick from a search;
  the purchase fills the price and the day when they are empty. "Add maintenance" adds a repeated task (after each
  done) linked to the item. Edit and delete are in the top bar.
- "Add item" (the + button, Capture, a room) can start with a photo: the camera or the photo picker. The photo goes
  up as a document (kind Other) linked to the new item.
- In Money, a transaction's sheet has "Add to inventory": a new item with the transaction's description, amount (as
  the price) and day, linked to the transaction.
- A document shows its items (a tap opens one), and "Link to item" links it to one.
- Today: a warranty alert (`/home/items/<id>`) opens the item; `/home` and `/home/rooms/<id>` open the Home screen and
  a room.

**People** (on Hestia.Api, `/api/people`) is a small personal CRM: the people you care about, when you last talked to
them, a reach-out reminder, birthdays, gift ideas, and things lent or borrowed.

- The People screen has a search, "Reach out" (people whose reminder is overdue: "Last contact 7 weeks ago"),
  "Upcoming" (birthdays in the next 30 days, with the new age and the open gift ideas), "Lent and borrowed" (the open
  loans of everybody), and all people with group chips and the last contact.
- A person shows the groups, "Log contact", "Gift idea", and "Lend item" (each a sheet), the strip (birthday, last
  contact, reminder), the gift ideas (a check marks one given today; uncheck makes it an idea again), the open loans
  ("Got it back" or "Gave it back"), the history (contacts and loans, newest first; delete with a tap), and the links
  (a tap opens the document, task, home item, or transaction). Edit and delete are in the top bar.
- The person form has the name, nickname, relationship, groups (with commas), birthday (month and day; the year is
  optional), email, phone, the reminder, and notes. It checks them as the server does.
- Today: a People alert (`/people/<id>`) opens the person; `/people` opens the People screen.
- A read-only user sees everything, but no add, edit, delete, log, gift, loan, or unlink.

**Journal** (on Hestia.Api, `/api/journal`, T21) keeps one entry per day:

- The screen has the streak at the top ("6-day streak"), the last 7 days (a filled circle for a day with an entry; a
  tap opens that day), the editor of one day (today, or the day that was tapped, with "Back to today"), "On this day"
  (for today: the same day in earlier years), and the newest 50 entries with a search (full-text on the server).
- "Save" writes the whole text of the day (it is off until the text changes); "Delete" asks first. After a change,
  Today gets new cards (`DataChanges`).
- A read-only user can read only: the editor does not change, and there is no Save or Delete.
- Today: the Journal tile (`/journal`, or `/journal?day=…`) opens the Journal.

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
    import, unsorted transactions, a document that expires, an overdue task, a warranty that ends. More than 3 new
    alerts give one summary. A notification goes away when its alert is resolved. A tap opens the alert's page, as on
    Today. A switch per module (Money, Documents, Tasks, Home, People). The first poll after install, sign-in, or turning it on only notes the open alerts and notifies
    nothing. "Hide amounts" hides the amounts in these notifications too, and with the app lock on the lock screen
    shows only the kind of alert.
  - Budgets nearly used up (80%) are a server alert since T71. The old local "Budget warnings" switch is gone: if it
    was on, the server alert notifications and Money turn on.
  - The **monthly review** is checked on the phone every few hours. The server has no alert for it yet (T82).
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
- `./gradlew :core:common:test :core:network:test :core:finance:test :core:today:test :core:documents:test :core:tasks:test :core:home:test :core:people:test :core:ui:lintDebug :feature:finance:testDebugUnitTest :feature:finance:lintDebug :feature:documents:testDebugUnitTest :feature:documents:lintDebug :feature:tasks:testDebugUnitTest :feature:tasks:lintDebug :feature:home:testDebugUnitTest :feature:home:lintDebug :feature:people:testDebugUnitTest :feature:people:lintDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

This is the command that CI runs. Lint fails the build on any new warning. `app/lint-baseline.xml` holds the one left on
purpose: the target SDK, which is raised as a change of its own.

### CI artifacts

Artifacts count against the storage quota of the GitHub account. When the quota is full, every upload fails.
CI keeps the uploads small and short:

| Artifact | When CI uploads it | Kept for |
| --- | --- | --- |
| `finance-insights-debug-apk` | Every run that builds the APK | 30 days on `main` and manual runs, 7 days on pull requests |
| `screenshots` | Runs that fail | 7 days |
| `reports` | Runs that fail | 7 days |

A failed upload does not fail the run. The step **Warn about failed uploads** then writes a warning annotation
(`Artifact upload failed`). The usual cause is a full quota. To free space, delete old artifacts in the
**Actions** tab, or with `gh api -X DELETE repos/guibilar/PersonalFinanceInsightsAndroid/actions/artifacts/<id>`.
Each new upload step must set `retention-days` and `continue-on-error: true`, and the warning step must name it.

### Screenshots

The screenshot tests (Robolectric and Roborazzi) write PNG files to `<module>/build/shots/`. The recorded images are in
git, in `<module>/screenshots/`. CI fails when an image is not the same as its recorded copy, byte for byte (T28).

- The images are the same on each run. Tests pass `ripples = false` to `FinanceTheme`: a ripple is drawn by an
  Android view on the system clock, not on the Compose test clock, so it was at a different point on each run.
- After an intended change of a screen, record the images and commit them with the change:

  ```bash
  ./gradlew :app:testDebugUnitTest :feature:finance:testDebugUnitTest :feature:documents:testDebugUnitTest \
    :feature:tasks:testDebugUnitTest :feature:home:testDebugUnitTest :feature:people:testDebugUnitTest
  python3 scripts/screenshots.py record
  git add app/screenshots feature/*/screenshots
  ```

  The pull request shows each changed image (before and after) in its file list.
- A new screenshot test needs its image recorded too; a removed test needs its image removed (`record` does both).
- `python3 scripts/screenshots.py check` compares locally, as CI does.

### CI annotations

CI writes its results as annotations, so you can see them on the run page (and with
`gh api repos/guibilar/PersonalFinanceInsightsAndroid/check-runs/<id>/annotations`) without the logs or artifacts.
The steps run also when the tests fail.

| Annotation | Step | Contents |
| --- | --- | --- |
| Notice `Tests :<module>` | Test results as annotations | Totals of one module: tests, passed, failed, skipped, and time |
| Error `Failed test :<module>` | Test results as annotations | The test name and the first lines of its message (at most 9; then one error with the number of the others) |
| Error `Compile error` | Compile errors as annotations | A Kotlin compile error, on its file and line (at most 10) |
| Error `Gradle: what went wrong` | Compile errors as annotations | Gradle's "What went wrong" block of each failed task (at most 10) |
| Error `Changed screenshot`, `New screenshot`, `Removed screenshot` | Screenshots match the recorded images | One image (module and name) that is not the same as its recorded copy (at most 10) |
| Notice `Screenshots` | Screenshots match the recorded images | The number of images, and the list of all changed, new, and removed images |

- The scripts are `scripts/test-annotations.py` (JUnit XML in `<module>/build/test-results/`),
  `scripts/gradle-annotations.py` (the Gradle output, kept in `gradle-output.log`), and `scripts/screenshots.py`
  (PNG files in `<module>/build/shots/`, compared with `<module>/screenshots/`).
- Gradle runs with `--continue`, so a failure in one module does not hide the results of the others.
- The screenshot tests (`:app` and the `:feature` modules) are not cacheable, because the images are
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
| `:core:home` | Plain Kotlin | The Home API (`HomeApi`, `/api/home`) and its models, and the rules of the Home screens: the warranty state and its words, the short labels, the amounts as typed, and the item form (`ItemDraft`). Its test fixtures hold the fake `HomeApi` (rooms, items, filters, links and the CSV in memory) and sample rooms and items. |
| `:core:people` | Plain Kotlin | The People API (`PeopleApi`, `/api/people`) and its models, and the rules of the People screens: the reminder and "last contact" words, birthdays, the history, and the person form (`PersonDraft`). Its test fixtures hold the fake `PeopleApi` (people, contacts, gifts, and loans in memory) and sample people. |
| `:core:ui` | Android library | The theme, the common components, the charts, and the frame of the settings pages. |
| `:feature:finance` | Android library | The Money module: its home, the finance screens, their navigation graph, the finance notifications, the widget, and the finance settings (`FinancePreferences`). |
| `:feature:documents` | Android library | The Documents module: the list, a document, the upload with the scanner and the pickers, the photo scaling, and its navigation graph (`documentsGraph`). It depends on the core modules only; the transactions of its links come from `:core:finance`. |
| `:feature:tasks` | Android library | The Tasks module: the Tasks screen (quick add, sections, done and undo), the task sheet (details, repeat, links, completions, delete), and its navigation graph (`tasksGraph`). It depends on the core modules only: linked documents come from `:core:documents`, linked transactions from `:core:finance`. |
| `:feature:home` | Android library | The Home module: the Home screen (maintenance, inventory, rooms, search, the export), a room, an item (details, warranty, photos, links, maintenance), the new item with a photo, and its navigation graph (`homeGraph`). It depends on the core modules only: documents come from `:core:documents`, transactions from `:core:finance`, maintenance tasks from `:core:tasks`. Its own file provider (`HomeFileProvider`, cache folder `home/`) serves the camera and the share sheet. |
| `:core:journal` | Plain Kotlin | The Journal API (`JournalApi`, `/api/journal`) and its models, and the rules of the Journal screen: the day labels, the streak, and "On this day". Its test fixtures hold the fake `JournalApi` (entries in memory) and sample entries. |
| `:feature:journal` | Android library | The Journal module: the Journal screen and its navigation graph (`journalGraph`). It depends on `:core:journal` and `:core:ui` only. |
| `:feature:people` | Android library | The People module: the People screen, a person (actions, gift ideas, loans, history, links), the person form, and its navigation graph (`peopleGraph`). It depends on `:core:people` and `:core:ui` only; linked pages open through `PeopleHost`. |
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
  stays under "Coming soon". After any successful write through the signed-in client (the `DataWrites` interceptor in `:core:network`,
  T58: every call that is not a GET, except sign-in and finance's read-only POST queries), the next Today load asks
  the server for new cards (`refresh=true`), and not its 30-second cache. `todayDestination(...)` gives a
  `TodayDestination`: a Money page, one transaction (`/money/transactions?id=<id>`), the Documents list
  (`/documents`), one document (`/documents/<id>`), the Tasks screen (`/tasks`, or `/tasks?due=overdue` for the
  overdue tasks only), one task (`/tasks/<id>`), the Home screen (`/home`), one room (`/home/rooms/<id>`), one
  item (`/home/items/<id>`), the People screen (`/people`), or one person (`/people/<id>`). "Statement to import" for one account
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
- `:feature:home` gives `homeGraph(...)` and `openHome()`, `openRoom(id)`, `openHomeItem(id)`, `addItem(photo)` and
  `addItemFromPurchase(...)` for the other pages. What it needs from the others comes through the `HomeHost` interface
  (`AppHomeHost` in `:app`): open a document, a transaction or a task, and read and decode photos with the Documents
  rules (T12).
- `:feature:people` gives `peopleGraph(...)` and `openPeople()`, `openPerson(id)`, and `addPerson()` for the other
  pages. A link of a person opens through the `PeopleHost` interface (`AppPeopleHost` in `:app`, `AppPeople.kt`).
- **Home and the other modules (T88):** `DocumentItems` (`:feature:documents`, `documentsGraph(items = …)`) gives a
  document its "Items" section; `TaskItems` (`:feature:tasks`, `tasksGraph(items = …)`) gives a task its items; and
  `FinanceHome` (`:feature:finance`, `financeGraph(home = …)`) gives a transaction "Add to inventory". The app
  implements each (`AppHome.kt`). Each module reads the Home API from `:core:home` only.
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
  (the "Documents" section of a transaction), `AppDocumentTasks.canWrite` (the "Tasks" section of a document),
  `homeGraph(canWrite = …)` (no add, edit, link or delete; the export stays), `AppDocumentItems.canWrite`, and
  `AppFinanceHome.canWrite`. In tests, `FakeAuthApi.scope` sets it.
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
- The shared build settings are in the convention plugins of `build-logic` (T30). A module build file names one plugin,
  its namespace (Android modules), and its dependencies:
  - `hestia.jvm.library`: a plain Kotlin module (`:core:*` without Android): JDK 21 toolchain, Java 17 bytecode;
  - `hestia.android.library`: an Android library (compile SDK, min SDK, Java and Kotlin versions, lint);
  - `hestia.android.compose`: the same with Compose (`:core:ui`);
  - `hestia.android.feature`: a feature module: Compose and the Robolectric screenshot test settings.

  Versions stay in `gradle/libs.versions.toml`. To change a shared setting, change the plugin in
  `build-logic/convention/src/main/kotlin/Conventions.kt`, not a module build file.
- To add a module (for example `:feature:documents`), make an Android library with `alias(libs.plugins.hestia.android.feature)` that depends on `:core:ui` and
  `:core:network`, give it a graph function like `financeGraph` with a home route, and call it from the app's
  navigation host. Make the module's API with `client.api<YourApi>()`. In tests, use `fakeApiClient(yourFake)`. In `MODULES`, give the module its route and the status `Active`, and add its pages to the Modules
  item of the bar. Nothing in `:feature:finance` changes.
