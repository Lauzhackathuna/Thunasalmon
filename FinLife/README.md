# FinLife — interactive financial digital twin

FinLife is a fictional financial journey for the UBS × LauzHack and JetBrains challenges.
It follows a student in Lausanne. Configure their starting circumstances, follow completed months, explore
alternative futures, advance one month to commit selected actions, and finish or resume the journey.
All displayed financial results come from the shared monthly V3 engine. No UBS customer data is used.

## Run and validate

Requirements: JDK 21, Android SDK platform 37, and the versions pinned in `gradle/libs.versions.toml`.
From this directory:

```sh
bash gradlew :desktopApp:run
bash gradlew :androidApp:assembleDebug
bash gradlew :shared:jvmTest :shared:testAndroidHostTest :desktopApp:classes :androidApp:assembleDebug
python3 tools/check_resources.py
```

Open the project in Android Studio/IntelliJ to run Android on an emulator or device.
Android host tests are not device tests. JVM rendering tests produce reviewable PNGs in
`shared/build/qa` for wide/narrow layouts, dark mode, enlarged fonts, the future preview and summary.
The CI workflow runs the same test/build targets and checks English/French resource parity.
Use `--offline` only when dependencies and SDK components are already cached.

## User experience

- Main card: **available bank balance**, actual monthly change, separately labelled net worth and shortfall.
- History chart: cash, net worth or theoretical balance; 3/6/12 months or full history. Hover, touch and
  keyboard readings remain available. Dotted historical comparisons are labelled as historical alternatives.
- Outlook: next 3/6/12-month cash and unpaid bills, starting at the current state. No future random draws.
- What If: reversible internship/shared-flat toggles immediately update a full twelve-month projection.
  All admissible combinations are shown, with actual differences, rent savings, temporary income,
  moving costs, unpaid bills avoided and resilience. Advancing the month commits the choices once.
- Details: regular/occasional costs, actual payments, outstanding bills, optional envelope allocations,
  payments and transfers. Editable assumptions replay prior months while preserving decisions.
- Summary: opening/final finances, income, accrued expenses, actual payments, unpaid bills, internship
  income, goal progress and a recorded-event recap. Continue or start over. No past decision rewriting is exposed.
- Assumptions: every value the engine uses for the run (profile, configuration, random-problem rules, goal),
  read from the engine and labelled synthetic.
- Goal: progress toward the engine's goal (350,000 CHF of net worth by month 120 by default).
- Sensitivity: rent of the current flat and income from parents, −50% to +50%; the engine reruns the
  12-month outlook with the changed inputs and the chart compares it with the unchanged values.
- Dotted-line breakdown: the net-worth gap between the player's path and the dotted line, split by flow
  item (salary, rent, taxes…). Each item is a sum of engine amounts, so the items add up exactly to the gap.

The journey starts by asking the player's first name; no fixed persona is shown. Legacy personas are
retained for compatibility and tests; they are not presented as functioning V3 journeys.

## Financial definitions and assumptions

Money is stored in integer cents. Display formatting consumes cents directly; no floating-point balance
animation can alter the visible amount.

| Metric | Definition |
|---|---|
| Available bank balance | Actual cash, always nonnegative |
| Unpaid obligations | Due amounts not yet paid, including separately identified unpaid bank principal |
| Theoretical balance | Actual cash minus all unpaid obligations; not an authorized overdraft |
| Net worth | Cash + investments − bank debt − unpaid non-bank obligations |
| Expenses | Charges accrued this month |
| Actual payments | Cash actually spent, potentially settling older bills |

Unpaid principal remains in bank debt and is not counted twice in net worth. Internal envelope transfers
are never income or expense. Paying an old bill reduces cash and its liability without recognizing the
expense again. See the [engine specification](shared/src/commonMain/kotlin/com/tuna/finlife/simulation/README.md).

Reference inputs: cash 8,000 CHF; student income 1,200/month; rent 1,000; insurance 250; groceries 250;
other costs 300. Tuition is 800 per academic semester, at progress 0, 6, 12 and 18, not every month.
After 24 study months the reference salary is 6,000/month.

The adventure uses **2% annual effective inflation on applicable costs and zero salary growth**.
Incomes stay nominally fixed. Rent, regular expenses and tuition use the monthly effective price index.
Shared rent is 800 at month-zero prices, indexed once at signing and then relative to that contract.
Moving costs are a configurable month-zero reference, indexed once at the move (zero by default).
Known one-off bills are nominal at their payment date. Demo repairs are indexed when generated, not again.

**Short internship: 300 CHF additional income/month for exactly three months, 900 total.** It keeps student
income, academic progress and tuition deadlines. The previous app-level 1,500 CHF default was inconsistent
with V3 and is deliberately replaced; edited values still propagate to previews, replay and summaries.
S005 remains a separate twelve-month internship that can suspend studies.

Initial envelopes fill, in priority order: two remaining tuition deadlines, three rents, four insurance
payments, three grocery budgets, one OTHER budget, then RESERVE, each capped by the remaining cash.
At reference inputs this gives STUDIES 1,600, HOUSING 3,000, INSURANCE 1,000, GROCERIES 750, OTHER 300,
RESERVE 1,350. Customized cash no longer goes entirely to reserve. This buffering policy is a FinLife
assumption, not a universal budget rule.

## Architecture and deterministic replay

`simulation/` is authoritative for arithmetic, obligations, events, projections and stability.
`game/AdventureRun` supplies product assumptions and seeded events. `WhatIfDraft` preserves the unmodified
current-month base; switching off rebuilds from that base. `AdventureOutlook` adapts full engine projections
for UI checkpoints and exact impact comparisons. The UI renders these values and localized explanations.

`AdventureRun.restore` is shared by assumption editing and saving. Events are revealed at their original
month rather than all being exposed at month zero; this avoids premature earmarking for unknown repairs.
Recorded responses and the completed month count are validated. Recalculation is atomic: a failed edit
leaves the existing run intact and reports an error. Financial changes invalidate historical alternatives.
Projection calculations are memoized by base session and internship amount, not rebuilt on every toggle.

Legacy annual APIs and `twin/FakeTwinEngine` remain tested compatibility code. Neither drives the current
adventure. `docs/modele.md` is explicitly historical annual-model documentation.

## Events, projections and stability

Seeded adventure events are illustrative pacing, not calibrated real-world probabilities. Rent announcements
and minor repairs each have a 1/12 monthly trigger, capped at one announcement and two repairs per twelve-month
block. A rent rule already active on a contract remains non-applicable to a second announcement.
The 400 CHF demo household repair is a `OneOffExpense`, **not S040 and not an oven replacement**.
Oven repair/replacement remain 450/800 in the engine's choice event; S040 remains major maintenance at 6,000.
Responsibility is explicit; events paid by another party do not charge the player's character.

Career: after studies the character earns the reference salary in a private job, taxed at a flat synthetic 20%.
Three months into the career, a fictional EPFL offer (4,500 CHF/month, stable) can replace the private job.
Keeping the private job adds an independent 10% chance each month that the company closes; the salary then
stops, savings remain. A voluntary six-month unpaid career break is available as a What If; bills continue.

Future previews share the same already-known external events, including a problem drawn for the current
month, and preserve active commitments. They never generate unseen future disasters. At the engine's
1,200-month limit a full twelve-month preview may be unavailable; the UI reports this instead of resetting
the character. Historical counterfactuals and future projections are distinct views, never an editor.

Resilience uses the existing V3 classification, not scenario spread: essential spending coverage, recurring
budget balance excluding temporary income, projected unpaid bills, reserve and ability to absorb a 500 CHF
surprise expense. Detailed explanations are available. Three to six months of essentials is an educational
guide; the result is neither an official UBS rating nor personal advice.

## Local persistence

The versioned save stores input amounts, seed, original event calendar, recorded answers, completed months,
current What If draft, summary mode, language and theme. It replays and validates balances before restoration.
An integrity checksum detects accidental corruption; it is not authentication or encryption.
Unknown versions/event types, invalid amounts and mismatched replays fail closed. Errors preserve the current
run and pause saving to protect the existing file; reset from Settings explicitly re-enables saving.

- Desktop: `~/.finlife/journey-v1.txt`, atomically replaced using a same-directory temporary file.
- Android: app-private `filesDir/journey-v1.txt`, using `AtomicFile`.
- QA can set `-Dfinlife.saveDirectory=/tmp/finlife-qa` to isolate Desktop saves.

I/O and reconstruction run off the UI thread; a mutex serializes writes. No server, account or database.
A session-free launch does not persist a standalone settings profile. A sudden process kill before an
asynchronous write completes can lose the very latest change. The save format (currently version 3, older
versions migrated) supports events exposed by the adventure, not every arbitrary custom event supported by the engine.

## Assistant (optional, explanation only)

A local Ollama model (`qwen3:4b` by default) answers questions about the run: a played month, the current
situation, the dotted line, a waiting decision, the coming 1–12 months or how the simulation works.
The model only classifies the question and words figures the app takes from the engine. Replies that quote
a number absent from those figures, or that give advice, are asked again once and otherwise replaced by an
app message. The assistant never changes the simulation; its chat is kept in memory and cleared with each run.

- Desktop: `http://localhost:11434`. `FINLIFE_OLLAMA_MODEL` selects another installed model.
- Android: through the host address `10.0.2.2:11434` (plain HTTP allowed to that address only) and the
  local-network permission requested at launch.
- Without Ollama, the panel says so and the rest of the app works unchanged.
- Opt-in check against the real model: `FINLIFE_LIVE_OLLAMA=1 ./gradlew :shared:jvmTest --tests '*AssistantLiveEvaluation*' --rerun`
  writes `shared/build/assistant-live-report.txt`. Small models vary; this reports a pass rate.

## Limitations and review

Synthetic deterministic inputs, monthly rather than daily timing, a flat synthetic salary tax instead of a
real tax schedule, no late-payment penalties,
no empirical event probabilities, no financial product recommendations. The comparison explores the current
two choices, not every possible future. Financial errors are recoverable; they do not silently end a run.

Native window automation is environment-dependent. Offscreen Compose rendering verifies real layouts, but
screen-reader behavior, device keyboards, process recreation and gestures still deserve a manual Android
and Desktop acceptance pass. CI configuration is provided; a local build is not evidence of a hosted CI run.
