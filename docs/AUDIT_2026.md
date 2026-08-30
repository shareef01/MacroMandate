# MacroMandate — Full Application Audit, August 2026

An independent audit of the whole application: architecture, correctness,
privacy, security, accessibility, performance and product design. Findings are
recorded with the evidence that produced them, and each has a stated confidence
level. Where a claim could not be verified in this environment, it says so.

---

## 0. Baseline

### Environment

| | |
|---|---|
| Audit date | 2026-08-28 |
| Repository state at start | `8bbc4d2`, working tree clean |
| Gradle | 9.5.0 (wrapper, SHA-256 pinned) |
| Android Gradle Plugin | 9.3.1 |
| Kotlin | 2.1.0 |
| JDK (Gradle daemon toolchain) | Temurin 25 |
| JDK (`java -version` on PATH) | Microsoft OpenJDK 21.0.11 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 29 |
| versionCode / versionName | 1 / 1.0 |
| Kotlin source | 38 files, 6,222 lines |

### Commands executed

| Task | Result | Notes |
|---|---|---|
| `testDebugUnitTest` | **PASS** — 44 tests, 0 failures | First run reported `UP-TO-DATE`; re-run with `cleanTest` to get real execution |
| `lintDebug` | **PASS** — 32 results, 0 errors | 14 `GradleDependency`, 7 `UnusedResources`, 5 `NewerVersionAvailable`, 6 misc |
| `assembleDebug` | **PASS** | 79.45 MB |
| `assembleRelease` | **PASS** | 5.86 MB, **unsigned** (no keystore on this machine) |
| `connectedDebugAndroidTest` | **NOT RUN** | `adb devices` returned an empty list |

### Hard limitation on this audit

**No physical device or emulator was attached.** `adb devices` listed nothing.
Nothing below that depends on observing the app run is marked CONFIRMED on
runtime grounds. Specifically **not** verified: TalkBack announcement order,
real touch-target bounds, font-scale reflow, frame timings, cold-start time,
battery draw, camera behaviour on real hardware, WorkManager scheduling in Doze,
widget rendering on a launcher, and Room migration execution.

The instrumentation suite consists of one file, `ExampleInstrumentedTest.kt`,
which asserts the package name. There is **no instrumented coverage of any
screen, flow, or migration**, so the "not verified" set could not be narrowed
from the repository either.

### Documentation is not evidence — demonstrated

The audit brief warns against trusting documentation. Three of this repo's own
claims failed on inspection:

1. **The README's screenshots are not images.** `docs/screenshots/*.png` begin
   with the bytes `ff fe eb 00 50 00 4e 00` — a UTF-16LE byte-order mark
   followed by PNG magic widened to two bytes per character. They were captured
   with a PowerShell redirect (`adb exec-out screencap -p > file.png`), which
   passes binary through a text encoder. All three are irrecoverably corrupt and
   render as broken images on GitHub. Committed as "screenshots captured from
   physical hardware device" (`8bbc4d2`).
2. **`RELEASE_GUIDE.md` and the Room comment describe destructive migration as a
   "pre-release safeguard"** while the database sat at version 6 with only
   schema 6 exported — i.e. no upgrade path existed at all (§7.1).
3. **`MandatePreferences` documented the API key as living in "app-private
   storage"**, which is accurate, but the README's "privacy-conscious" framing
   sat next to a capture flow that uploaded photographs with no disclosure at
   the point of capture (§9.2).

---

## 1. Architecture map

| Responsibility | Owner | Notes |
|---|---|---|
| Application startup | `MandateApplication` | Creates notification channels, schedules the reminder worker |
| Activity / navigation | `MainActivity`, `MacroMandateApp` | Single activity, Compose `NavHost`, 3 tabs + detail route |
| All screen state | `MainViewModel` (804 lines) | Single `AndroidViewModel` shared by every screen |
| Dashboard UI | `MainScreen.kt` (1,079 lines) | Summary card, actions, history list, manual-entry and delete dialogs, camera swap |
| Trends UI | `AnalyticsScreen.kt` (705 lines) | Daily ring, macro bars, weekly bar chart, map, weekly dossier |
| Settings UI | `ControlPanelScreen.kt` (700 lines) | API key, theme, target, reminders, location, export/restore, audit log |
| Detail / edit UI | `MealDetailScreen.kt` (477 lines) | Photo, assessment, macros, location, edit + delete dialogs |
| Camera | `CameraCaptureScreen.kt` | CameraX preview + `ImageCapture` |
| Persistence | `AppDatabase`, `MealDao`, `AuditDao` | Room, 2 entities |
| Domain access | `MealRepository`, `AuditRepository` | Entity↔domain mapping, day-boundary flows, audit pruning |
| Preferences | `MandatePreferences` | DataStore Preferences, 6 keys |
| Networking | `HuggingFaceApi`, `ApiConfig` | Retrofit + OkHttp + Gson, OpenAI-compatible chat completions |
| AI parsing | `NutritionSanitizer` | JSON→`ParsedNutrition` |
| Image pipeline | `ImageForensics`, `EvidenceStore` | Downsample, watermark, durable storage |
| Export / backup | `DossierExporter` | CSV + JSON, restore parsing |
| Reporting | `DossierReportGenerator` | Weekly markdown dossier |
| Scoring | `ComplianceEngine` | Deviation-from-target score |
| Background work | `EnforcementScheduler`, `MandateEnforcementWorker` | One periodic worker, 6h |
| Foreground service | `MandateSurveillanceService` | Ongoing calorie notification |
| Boot | `MandateBootReceiver` | Restarted the service |
| Widget | `MandateWidget`, `MandateWidgetReceiver` | Glance |
| Theme | `Theme.kt`, `Color.kt`, `Type.kt`, `ModifierUtils.kt` | 4 terminal themes, HUD framing, CRT overlay |

### Structural observations

- **`MainViewModel` is a god object.** It owns meals, totals, the analysis
  pipeline, HTTP client construction, API-key state, location acquisition, theme,
  target, reminders, exports, restore, reporting, widget refresh, the audit log,
  and the compliance state machine. Every screen receives the same instance, so
  Settings holds subscriptions to the full meal history and the dashboard holds
  subscriptions to the audit buffer.
- **No dependency injection, and it is not obviously needed.** Wiring is a
  handful of constructor calls. `AppDatabase.getDatabase()` is a correct
  double-checked singleton. The genuine cost of manual wiring here is
  *testability*: `MainViewModel` builds its own Retrofit instance in a `by lazy`
  and reads `Application` directly, so none of its logic can be unit-tested. The
  proportionate fix is constructor-injecting the repositories and an API
  interface, **not** adopting Hilt.
- **`mealEntries` loads the entire meal history into memory** and the dashboard
  filters and sorts it during composition. Fine at hundreds of rows; not a
  structure that survives years of data.

---

## 2. Feature inventory

Traced UI → ViewModel → repository → persistence/network for each.

| Feature | Status | Note |
|---|---|---|
| Dashboard totals | IMPLEMENTED | Sums `todayMeals` correctly |
| Calorie goal | PARTIAL | Slider only; 1200–4000 in 100-kcal steps (§17.3) |
| Macro totals | IMPLEMENTED | Displayed at the smallest type size in the app (§17.2) |
| Meal history | IMPLEMENTED | Full history, no paging |
| Filter / sort / search | IMPLEMENTED | 6 filters, 3 sorts, substring search |
| Manual meal | IMPLEMENTED | Input defects — §2.4, §17.5 |
| Camera capture | IMPLEMENTED | Camera never released (§3.5) |
| Gallery selection | **WAS GATED** | Disabled at `SUBVERSIVE` (§1.1) |
| AI analysis | IMPLEMENTED | Auto-saved with no review step (§3.1) |
| AI result validation | PARTIAL | No upper bounds (§2.1) |
| Edit meal | **WAS UNREACHABLE** | Detail screen covered at `SUBVERSIVE` (§1.1) |
| Delete meal | IMPLEMENTED | Photo was never deleted (§6.1) |
| Liquid marking | IMPLEMENTED | |
| Location recording | IMPLEMENTED | Opt-in, correctly defaulted off |
| Map | IMPLEMENTED | Renders a full-screen grid to say "no data" (§17.6) |
| Analytics | IMPLEMENTED | Chart scale defect (§8.2) |
| Weekly dossier | IMPLEMENTED | Averaging defect (§8.1) |
| Copy / export report | IMPLEMENTED | |
| CSV export | IMPLEMENTED | No BOM, partial quoting (§5.2) |
| JSON backup | IMPLEMENTED | |
| JSON restore | IMPLEMENTED | Unvalidated hostile boundary (§5.1) |
| Deduplication | **ABSENT** | Duplicate ids silently collapsed by Room, not by the parser (§5.3) |
| Reminders | IMPLEMENTED | Nagged brand-new users (§11.2) |
| Background surveillance | IMPLEMENTED | Unjustified foreground service (§11.1) |
| Notifications | IMPLEMENTED | Reminder channel set to interrupt (§11.3) |
| Leniency plea | IMPLEMENTED | **Model-driven data deletion** (§1.2) |
| Permanent lockdown | IMPLEMENTED | **Model-driven app lockout** (§1.2) |
| Audit history | IMPLEMENTED | Capped at 1,000, pruned in batches |
| Themes | IMPLEMENTED | Banner ignored the active theme (§18.1) |
| API configuration | IMPLEMENTED | Build-time key reached release (§4.1) |
| Widget | PARTIAL | Went stale at midnight (§12.1) |
| Restart persistence | IMPLEMENTED | |
| Boot behaviour | DEAD CODE | Returned immediately on API ≥ 35 (§11.4) |

---

## 3. Findings

Severity: **P0** release blocker / data loss / security · **P1** major defect ·
**P2** meaningful · **P3** polish.
Confidence: **CONFIRMED** (proved by execution or unambiguous code reading) ·
**HIGH** · **MEDIUM** · **HYPOTHESIS**.

---

### §1.1 — The compliance score took the user's own data hostage

| | |
|---|---|
| **ID** | MM-001 |
| **Severity** | **P0** |
| **Category** | Product correctness / user trust / data access |
| **Confidence** | **CONFIRMED** (code reading, unambiguous) |
| **Files** | `MainActivity.kt`, `MainScreen.kt`, `MealDetailScreen.kt`, `ControlPanelScreen.kt`, `ComplianceEngine.kt` |
| **Symbols** | `MacroMandateApp`, `ComplianceStatus`, `ActionRow(importEnabled)`, `MealDetailScreen`, `ComplianceEngine.calculateScore` |

**What was happening.** A single derived number gated access to the
application. `ComplianceEngine.calculateScore` returns `100 − averageDailyDeviationPercent`
across days that have entries. That score selected a `ComplianceStatus`, and the
status then controlled:

- `SUBVERSIVE` (score 40–69): the gallery picker was disabled and relabelled
  "Locked"; **JSON export, JSON restore and CSV export were all disabled** and
  relabelled "Export locked"; and `MealDetailScreen` drew a full-screen
  semi-opaque red `Surface` over the record reading "Meal details are locked
  while you're well off target." Material 3's non-clickable `Surface` installs a
  `pointerInput` that swallows touches, so the screen underneath — including the
  **Edit** button — was unreachable.
- `CRISIS` (score < 40): `MacroMandateApp` returned `LeniencyPleaScreen`
  *before* building the `NavHost`. The entire application — dashboard, history,
  trends, settings, export — was replaced by a plea form.

**How easily it triggered.** Trivially, through ordinary under-eating. The
scoring treats a past day's shortfall as deviation:

```
target 2500, and yesterday you logged one 400 kcal breakfast
deviation = |400 − 2500| / 2500 = 84%
score     = 100 − 84 = 16   → CRISIS → whole app replaced
```

Two further penalties stacked on top in `MainViewModel.complianceStatus`: −40
for a meal flagged `isRestricted`, and −15 for any meal logged between 23:00 and
04:59. Either alone was enough to move a user a full band.

**Why it matters.** Three compounding problems:

1. **A circular trap.** A wrong AI estimate inflates the day's calories → the
   score drops → `SUBVERSIVE` covers the detail screen → the user can no longer
   open the meal to correct the estimate that caused it.
2. **Data was withheld.** Export and restore were disabled. With
   `allowBackup="false"`, a user in `SUBVERSIVE` had no supported route to their
   own nutrition history.
3. **`CRISIS` was an unrecoverable dead end.** The plea screen's only action
   calls `submitLeniencyPlea`, which requires an API key and a network. With no
   key configured, or offline, it returns an error and there is no other control
   on the screen. The user cannot reach Settings to add a key. The only exit is
   clearing app data — which destroys the log.

**Expected behaviour.** Distance from a calorie target is information. It must
never determine whether someone can read, correct, or export their own records.

**Fix applied.** `ComplianceStatus` is now a label only. `LOCKED` is deleted; the
`CRISIS`/`SUBVERSIVE` gates are removed from all four screens; the late-night and
restricted-zone penalties are removed from the score. The dystopian voice is
retained in the banner copy — the brief's *character in the chrome, clarity in
the data* line — while nothing it says gates anything.

**Regression guard.** `ComplianceStatus` no longer has a `LOCKED` member, so any
reintroduction of a lockout state fails compilation of the exhaustive `when`
expressions in `StateStatusBanner` and `DossierReportGenerator`.

**Effort** M · **Risk of change** Low — removes gating, adds none.

---

### §1.2 — A language model could delete the meal log or lock the app permanently

| | |
|---|---|
| **ID** | MM-002 |
| **Severity** | **P0** |
| **Category** | Data loss / user trust |
| **Confidence** | **CONFIRMED** |
| **Files** | `MainViewModel.kt`, `LeniencyVerdict.kt`, `LeniencyPleaScreen.kt`, `MandatePreferences.kt` |
| **Symbols** | `submitLeniencyPlea`, `LeniencyVerdict.parse`, `setPermanentLockdown`, `PermanentLockdownScreen` |

**What was happening.** The `CRISIS` plea screen sent the user's free-text
justification to the configured chat-completions endpoint and branched on the
model's reply:

```kotlin
is LeniencyVerdict.Granted -> {
    repository.deleteAllMeals()          // every meal, permanently
    EvidenceStore.deleteAll(...)         // every photo, permanently
}
is LeniencyVerdict.Denied -> {
    preferences.setPermanentLockdown(true)   // app replaced by a lockout screen
}
```

`LeniencyVerdict.parse` takes the substring between the first `{` and the last
`}` of the model's output and reads a `decision` field.

**Why it matters.** A third-party model's token sampling decided whether to
irreversibly erase a person's entire nutrition history. There is no
confirmation, no undo, and no backup (`allowBackup="false"`). The word
"GRANTED" appearing in the response is the entire safety mechanism. The prompt
that produces it interpolates unescaped user text, so the user can also talk the
model into either outcome.

The code shows real awareness of the hazard — `LeniencyVerdict` carries a
careful KDoc about an unparsable verdict previously falling through into
lockdown, and `requestReinstatement` was added as an escape hatch. But the
hazard is the mechanism, not its parsing.

**Expected behaviour.** No model output should be able to destroy user data.
Destructive actions are the user's, taken deliberately, with confirmation.

**Fix applied.** The entire mechanism is removed: `submitLeniencyPlea`,
`requestReinstatement`, `LeniencyVerdict`, `LeniencyPleaScreen`,
`PermanentLockdownScreen`, and the `is_permanently_locked` preference. `deleteAllMeals()`
now has no caller.

**Regression guard.** `MealRepository.deleteAllMeals` retained deliberately with
no production caller; its reintroduction is visible in review. The 9 tests in
`LeniencyVerdictTest` are removed with the behaviour they guarded.

**Effort** S · **Risk of change** Low.

---

### §2.1 — No upper bound on any nutrition value

| | |
|---|---|
| **ID** | MM-003 |
| **Severity** | **P1** |
| **Category** | Data correctness |
| **Confidence** | **CONFIRMED** |
| **Files** | `NutritionSanitizer.kt`, `DossierExporter.kt`, `MainScreen.kt`, `MealDetailScreen.kt` |

**What was happening.** Values were floored at zero and never capped.
`parseSafeInt` returned any non-negative `Int`, so a model replying
`{"calories": 2000000000}` wrote two billion calories into Room, where it
propagated into the daily total, the weekly average, the compliance score, the
chart's y-scale (flattening every real bar to invisibility) and the widget.

Four separate ingestion paths applied four different rules:

| Path | Lower bound | Upper bound | Name length | NaN/Inf |
|---|---|---|---|---|
| `NutritionSanitizer` | 0 | none | unbounded | dropped |
| Manual entry dialog | `coerceAtLeast(0)` | none | unbounded | n/a |
| Edit dialog | `coerceAtLeast(0)` | none | unbounded | n/a |
| **JSON restore** | **none** | none | unbounded | passed through |

Restore applied no validation at all — a hand-edited backup could write negative
calories, `1e12` grams of fat, or a 100 KB meal name straight into the database.

**Fix applied.** One shared gate, `NutritionBounds`, used by all four paths:
calories `[0, 20 000]`, macros `[0, 2 000] g`, names truncated at 120 chars,
assessments at 500. Non-finite values collapse to 0. The bounds are documented
as parser limits, explicitly not dietary guidance.

**Regression guard.** `NutritionBoundsTest` (19 tests),
`HostileAnalysisResponseTest` (20 tests), `BackupRestoreHostileInputTest`
(16 tests).

**Effort** M · **Risk** Low.

---

### §2.2 — Calories that contradict the macros were accepted silently

| | |
|---|---|
| **ID** | MM-004 · **P1** · **CONFIRMED** · `NutritionSanitizer.kt` |

The Atwater fallback (`4/4/9`) fired **only** when `calories <= 0`. When the
model returned both, no reconciliation happened — `{"calories": 15,
"proteinGrams": 50, "carbsGrams": 50, "fatGrams": 30}` (macros implying ~670 kcal)
was stored as 15 kcal with no indication anything was wrong.

**Fix.** Stated calories are still preferred — the app has no basis for deciding
which half is wrong — but a >2.5× or <0.5× disagreement now sets
`caloriesContradictMacros`, surfaced in the review sheet as *"The calorie figure
doesn't match the macros given. Worth checking."* The derived-vs-stated
distinction is carried out of the parser as `caloriesDerivedFromMacros` so the UI
can say which number the model actually gave.

---

### §2.3 — Macro values were truncated, not rounded, at every display site

| | |
|---|---|
| **ID** | MM-005 · **P2** · **CONFIRMED** · `MainScreen.kt`, `MealDetailScreen.kt` |

`entry.proteinGrams.toInt()` at every display call site. 12.7 g rendered as
"12 g" on the meal card, the detail rows and the totals. Systematically low, and
the error accumulated across a day's rows. The edit dialog seeded its fields from
`Float.toString()`, showing "40.0" where "40" was meant.

**Fix.** `NutritionFormat.formatGramsValue` — rounds to one decimal, drops a
trailing `.0`, handles non-finite input. Used everywhere. **Guard:**
`NutritionFormatTest`.

---

### §2.4 — Overlong numeric input silently became zero

| | |
|---|---|
| **ID** | MM-006 · **P2** · **CONFIRMED** · `MainScreen.kt`, `MealDetailScreen.kt` |

`caloriesStr.toIntOrNull() ?: 0` with no length cap on the field. Typing 15
digits overflows `Int`, `toIntOrNull()` returns null, and the meal was logged
with **0 calories**. Similarly `"1.2.3"` → null → `0f` for macros.

**Fix.** Calorie fields capped at 6 characters; `parseCalories` returns null
rather than 0 so callers keep the previous value; `sanitizeDecimalInput` permits
at most one separator.

---

### §2.5 — Comma decimal separators were silently discarded

| | |
|---|---|
| **ID** | MM-007 · **P1** · **CONFIRMED** · both entry dialogs |

`it.filter { ch -> ch.isDigit() || ch == '.' }`. On a keyboard laid out for a
comma-decimal locale — most of Europe and South America — the separator key
produces `','`, which this filter dropped. **The user typed `12,5` and the field
showed `125`.** A tenfold error, entered with no indication anything was
discarded, in a nutrition app.

**Fix.** `sanitizeDecimalInput` accepts `,` and `.` and normalises to `.`.
**Guard:** `NutritionFormatTest.commaIsAcceptedAsADecimalSeparator`.

---

### §3.1 — AI estimates were written to the log without review

| | |
|---|---|
| **ID** | MM-008 |
| **Severity** | **P1** |
| **Category** | Product / data honesty |
| **Confidence** | **CONFIRMED** |
| **Files** | `MainViewModel.kt`, `MainScreen.kt` |

`processImageForMacros` parsed the response and called
`repository.insertMeal(newEntry)` directly. The user's first sight of the
estimate was a snackbar reading `"Logged: Toast"` — by which point it was already
counted in the day's totals, the weekly average and the compliance score. To see
what had been recorded they had to open the record; to fix it they had to edit a
row that was already affecting every aggregate.

Compounding it, `NutritionSanitizer` invented an assessment when the model gave
none (`"NOMINAL REFUELING REGISTERED."`) and parsed a `confidence` field that was
**never displayed anywhere** — a fake-confidence hazard sitting dormant in the
parser.

**Fix applied.** A `PendingAnalysis` state and an `AnalysisReviewSheet` between
the model and the database. Every field is editable in place, the heading says
*"Estimated from your photo by AI"*, caveats are shown when the app derived or
clamped a value, and **the image is only copied into durable storage on
confirm** — a discarded analysis leaves nothing behind. The invented assessment
and the unused `confidence` field are removed.

**Effort** M · **Risk** Low — additive.

---

### §3.2 — Every model response and provider error body was logged in release

| | |
|---|---|
| **ID** | MM-009 · **P1** · **CONFIRMED** · `MainViewModel.kt` |

```kotlin
Log.e("MainViewModel", "No JSON found in response: $responseText")
Log.e("MainViewModel", "API Error: $errorBody")
```

Neither was gated on `BuildConfig.DEBUG`. `Log.e` is emitted in release builds.
The response text describes the user's food; the provider's error body can echo
the request. The OkHttp interceptor was correctly set to `NONE` in release with
`redactHeader("Authorization")` — careful work undone by two unguarded `Log.e`
calls beside it.

**Fix.** Both moved to `Log.d` inside `if (BuildConfig.DEBUG)`. Failures reach
the user as `AnalysisError` values instead.

---

### §3.3 — Raw exception text was shown to the user

| | |
|---|---|
| **ID** | MM-010 · **P1** · **CONFIRMED** · `MainViewModel.kt` |

`e.localizedMessage?.uppercase()` in a snackbar, producing strings like
`FAILED TO CONNECT TO ROUTER.HUGGINGFACE.CO/2606:4700::6812:AD8:443`.
Infrastructure detail, unlocalizable, and no indication of what to do next.

**Fix.** `AnalysisError` — ten cases mapping HTTP status and transport
exceptions to one plain sentence each, with `isRetryable`. Offline and timeout
messages point at manual logging, which works without a network.
**Guard:** `AnalysisErrorTest` asserts no message contains `http`, none is
all-caps, and each ends in a full stop.

---

### §3.4 — No HTTP timeouts were configured

| | |
|---|---|
| **ID** | MM-011 |
| **Severity** | **P1** |
| **Category** | Reliability |
| **Confidence** | **CONFIRMED** (code) / **HIGH** (impact — not measured against a live endpoint) |

```kotlin
val client = OkHttpClient.Builder().addInterceptor(logging).build()
```

OkHttp defaults to a **10-second read timeout**. Vision inference on a cold
provider routinely exceeds that. The most likely everyday failure of the app's
headline feature was a `SocketTimeoutException` that had nothing to do with the
image — surfaced to the user as raw uppercase exception text (§3.3).

**Fix.** connect 15 s, write 30 s, read 60 s, call 90 s.
`retryOnConnectionFailure(false)` — an automatic retry of a vision call is a
second billable request, and a late reply arriving after a retry is how the same
meal gets logged twice. The loading overlay now offers **Cancel**, and
cancelling the coroutine cancels the call.

---

### §3.5 — The camera was never released

| | |
|---|---|
| **ID** | MM-012 · **P1** · **CONFIRMED** · `CameraCaptureScreen.kt` |

Use cases were bound with `LaunchedEffect(Unit)` and
`bindToLifecycle(lifecycleOwner, …)`, where `lifecycleOwner` is the **Activity**.
The capture screen is swapped in by a local `screenState` variable rather than a
nav destination, so leaving it disposed the composable but never unbound the
camera. After one visit the camera stayed open — and the OS privacy indicator
stayed lit — for as long as the app was foregrounded.

**Fix.** `DisposableEffect` with `onDispose { …unbindAll() }`.

---

### §3.6 — Continuous animations on the capture screen

| | |
|---|---|
| **ID** | MM-013 · **P2** · **CONFIRMED** · `CameraCaptureScreen.kt`, `MainScreen.kt` |

Three unbounded loops ran whenever their screen was visible:

- a marquee rotating an instruction string by one character every 100 ms —
  unreadable as text, permanently recomposing, and re-announced by a screen
  reader ten times a second;
- two `infiniteTransition` animations (a scan line and a colour pulse) over the
  camera preview;
- the dashboard banner's `while (true) { cursorVisible = !cursorVisible; delay(500) }`,
  recomposing the top of the dashboard twice a second forever.

**Fix.** Marquee replaced with static text; typewriter and blinking cursor
removed. The camera scan-line animation is retained — it is on a transient
screen and reads as instrumentation.

---

### §4.1 — The developer's API key was compiled into release builds

| | |
|---|---|
| **ID** | MM-014 |
| **Severity** | **P0** (as a release blocker) |
| **Category** | Security |
| **Confidence** | **CONFIRMED** (built an APK and recovered the key from its dex) |
| **Files** | `app/build.gradle.kts`, `ApiConfig.kt` |

```kotlin
release {
    buildConfigField("String", "HUGGINGFACE_API_KEY", "\"$hfKey\"")
}
```

`HUGGINGFACE_API_KEY` from `local.properties` was emitted into **both** debug
and release `BuildConfig`. A string constant in `BuildConfig` is a plain UTF-8
string in `classes.dex`, recoverable with `strings` or `apktool` in seconds.
Any release built on a machine with a key in `local.properties` would ship that
key to every installer, who could then spend the developer's inference quota.

`ApiConfig`'s KDoc documents this risk accurately — but nothing enforced it.

**Search performed.** `hf_`, `Bearer`, `api key`, `token`, `secret`, `password`,
`BuildConfig`, `Authorization` across the working tree; `git log --all -S 'hf_'`
across all history; regex scan for `hf_[A-Za-z0-9]{10,}`, `sk-…`, `AIza…` in
tracked files.
**Result: no credential is present in this checkout or in any commit.**
`local.properties` contains only `sdk.dir` and was never committed. The exposure
was latent, not realised.

**Verification performed during this audit.** A fake key
(`hf_FAKEKEYFORAUDIT…`) was temporarily written to `local.properties`, a debug
APK built, and the APK's dex entries byte-searched:

```
dex entries containing the literal key: ['classes11.dex']
```

**CONFIRMED by execution:** a `BuildConfig` string constant is stored verbatim in
`classes.dex` and is recoverable from any installed copy with a plain byte
search — no reverse-engineering tooling required. `local.properties` was then
restored to its original contents (`sdk.dir` only) and the APK rebuilt.

**Fix applied.** The release build now **fails** if `HUGGINGFACE_API_KEY` is set,
with a message pointing at the two safe architectures (bring-your-own-key, or a
backend proxy via `MANDATE_API_BASE_URL`). `-PallowEmbeddedKey=true` overrides it
deliberately for a private build. When not overridden, release `BuildConfig` gets
`""` regardless of `local.properties`.

**Guard verified by execution.** With the fake key present,
`./gradlew assembleRelease` failed with the intended message, while
`./gradlew assembleDebug` succeeded — the developer convenience is preserved and
the distribution hazard is closed.

BYOK is retained as the shipping architecture — it needs no backend, and the
key belongs to the person who typed it.

---

### §5.1 — Restore was an unvalidated hostile-input boundary

| | |
|---|---|
| **ID** | MM-015 · **P1** · **CONFIRMED** · `DossierExporter.parseJsonBackup` |

The file comes from a document picker: arbitrary bytes. Every field was taken
verbatim.

| Input | Old behaviour | Now |
|---|---|---|
| `"version": 99` | Accepted, unknown fields dropped, reported success | Refused as `UnsupportedVersion` |
| 500 MB file | `readText()` → OOM | Refused above 16 M chars |
| `"calories": -5` / `1e12` | Written to Room | Clamped |
| `"timestamp": 4102444800000` (2100) | Written to Room | Clamped to now |
| `"latitude": 999.0` | Written to Room | Dropped |
| 100 KB `foodName` | Written to Room | Truncated |
| Duplicate ids | Both returned; Room silently kept one | First kept, count is honest |
| Arbitrary `imageUri` | Stored verbatim | Only evidence-directory `file://` URIs |
| Non-object array entry | `getJSONObject` threw, whole restore lost | Entry skipped |

The far-future timestamp is the sharpest of these: `getTodayMeals` is
`WHERE timestamp >= :startOfDay` with **no upper bound**, so a single row dated
2100 would be counted in every daily total from then on.

**Guard:** `BackupRestoreHostileInputTest`, 16 tests.

---

### §5.2 — CSV export defects

| | |
|---|---|
| **ID** | MM-016 · **P2** · **CONFIRMED** · `DossierExporter.generateCsv` |

Formula-injection defence was already correct and tested — `escapeCsvField`
prefixes `=+-@\t` with an apostrophe, which is the right call given meal names
are model-generated from a user-supplied photo.

Remaining defects: only `FoodName` was quoted (id and timestamp bare); no UTF-8
BOM, so Excel on Windows mangles any non-ASCII dish name; `LF` line endings
rather than RFC-4180 `CRLF`; and the timestamp carried no timezone, making rows
unplaceable in time.

**Fix.** All fields quoted, BOM prepended, `CRLF`, ISO-8601 with offset.
Coordinates and the model's assessment are deliberately **excluded** — a CSV is
the artefact people mail to themselves and open on a shared machine. The JSON
backup remains the complete archive.

---

### §5.3 — The round-trip test asserted a count the database could not hold

| | |
|---|---|
| **ID** | MM-017 · **P2** · **CONFIRMED** (test execution) · `DossierExporterTest` |

The fixture gave every meal `id = "abc123"`. `jsonBackupRoundTripRestoresIdenticalMeals`
asserted 2 meals survived a round trip — but `insertMeals` uses
`OnConflictStrategy.REPLACE`, so Room would have stored **one**. The test gave
false confidence about restore fidelity. Adding parser-level deduplication made
it fail, which is how it was found.

**Fix.** Distinct ids in the fixture; deduplication now makes the parser's
reported count match what Room stores.

---

### §6.1 — Deleting a meal never deleted its photograph

| | |
|---|---|
| **ID** | MM-018 |
| **Severity** | **P1** |
| **Category** | Privacy / correctness |
| **Confidence** | **CONFIRMED** |
| **Files** | `MainViewModel.kt`, `EvidenceStore.kt`, `CameraCaptureScreen.kt` |

Two independent bugs, either alone sufficient:

1. `deleteMealEntry(id)` called `EvidenceStore.delete(getApplication(), id)`,
   but that function's parameter is `imageUri: String?`. It received a bare UUID,
   `Uri.parse` produced a URI with a null scheme, `isStored` returned false on
   the first line, and the function **returned without deleting anything**.
2. Even with a URI, the naming was wrong: `CameraCaptureScreen.takePhoto` names
   the file after a *fresh* `UUID.randomUUID()`, while `processImageForMacros`
   generated a *different* `mealId`. The file was never named after the meal.

So every photograph survived the deletion of its meal, indefinitely, while the
audit log recorded `"RECORD EXPUNGED."` and the confirmation dialog promised the
entry "will be permanently expunged". A user deleting a meal to remove a photo
containing something private did not remove it.

**Fix.** The image URI is read from the meal row *before* the row is deleted, and
that URI is passed to `EvidenceStore.delete`.

---

### §6.2 — Path traversal in the evidence-store ownership check

| | |
|---|---|
| **ID** | MM-019 · **P2** · **CONFIRMED** (code) / **HYPOTHESIS** (exploitability) · `EvidenceStore.isStored` |

```kotlin
return File(path).absolutePath.startsWith(directory(context).absolutePath)
```

`getAbsolutePath()` does **not** resolve `..`. The path
`…/files/evidence/../../databases/macro_mandate_db` passes this prefix check.
Chained with §5.1 (arbitrary `imageUri` accepted on restore) and §6.1 fixed, a
crafted backup could have made "delete this meal" unlink the meal database.

Marked HYPOTHESIS on exploitability because §6.1 meant the delete path was inert
in the shipped code — the traversal was latent, and would have become live the
moment §6.1 was fixed in isolation. Both are fixed together.

**Fix.** Canonical-path comparison via `File.canonicalFile` and
`Path.startsWith`, plus URI filtering at the restore boundary.

---

### §7.1 — Destructive migration in a shippable build

| | |
|---|---|
| **ID** | MM-020 |
| **Severity** | **P0** (once distributed) |
| **Category** | Data loss |
| **Confidence** | **CONFIRMED** |
| **Files** | `AppDatabase.kt`, `app/schemas/` |

`fallbackToDestructiveMigration()` was unconditional at database version 6, with
only `6.json` exported and **no `Migration` objects at all**. Any future schema
change would silently drop and recreate every table — total, unannounced loss of
the user's entire nutrition history on an app update.

The in-code comment is honest about this ("PRE-RELEASE SAFEGUARD (versionCode 1,
no users in the wild yet)… must be replaced before the first public release").
The evidence supports the premise: `versionCode = 1`, no tags, no release
artefacts. But an unconditional destructive fallback is one forgotten checklist
item away from shipping.

**Fix applied.**
- Destructive fallback is now **debug-only**. A release build that meets a
  database it cannot migrate fails loudly rather than emptying it.
- `MIGRATION_6_7` added, carrying the missing indices (§25.1). Data-preserving
  by construction — `CREATE INDEX` rewrites no rows.
- Schema `7.json` exported and committed.
- `MigrationTest` added (4 tests: meal rows preserved, audit rows preserved,
  indices created, 500-row bulk).

**Verification status.** `MigrationTest` **compiles but has not been executed** —
it requires a device. This is the single most important unverified item in this
audit; run `./gradlew connectedDebugAndroidTest` before any release.

---

### §8.1 — The weekly dossier divided by 7 regardless of available data

| | |
|---|---|
| **ID** | MM-021 · **P1** · **CONFIRMED** · `DossierReportGenerator` |

```kotlin
val avgDailyCalories = (totalCalories / 7.0).roundToInt()
```

Always seven, even on a two-day-old install. A user who logged 2,000 kcal on
their only day of use was told:

```
AVG DAILY CONSUMED   : 286 kcal (-2214 kcal, 89% deviation)
```

Stated with the authority of a generated report, in a health-adjacent app.

A second defect: the report's stated window was `now − 7×24 h`, while the meals
it summarised came from `MealRepository`'s `startOfToday − 6 days` window. **The
header described a period that did not match the data underneath it.**

**Fix.** Average over days that actually have entries; print
`DAYS WITH ENTRIES : n of 7` so the denominator is visible; compute the window
from `MealRepository.WEEK_LENGTH_DAYS` so the two cannot drift. The report now
carries one line stating that photo-derived values are estimates.

---

### §8.2 — The weekly chart drew its bars and its target line on different scales

| | |
|---|---|
| **ID** | MM-022 · **P1** · **CONFIRMED** (arithmetic) · `AnalyticsScreen.WeeklyBarChart` |

```kotlin
// bar:         (calories / maxVal) * 140          .dp, from the Row's bottom
// target line: height − (target / maxVal) * (height − 40.dp)   , from the Canvas
```

Two different scales and two different baselines on one chart. A bar equal to the
target did not reach the target line. **Reading a day as over or under target off
the graphic gave the wrong answer** — the chart's only job.

**Fix.** Both use a shared `BAR_MAX_HEIGHT` from a shared baseline, extracted as
named constants precisely because they were implicit and disagreed.

Also fixed here: the chart grouped meals by `Calendar.DAY_OF_YEAR` alone, without
the year. `ComplianceEngine.dayKey` had been explicitly fixed for this ("Year-qualified
so days exactly a year apart cannot collide") and the chart was not; and the
weekday labels were hardcoded to `Locale.US`.

---

### §9.1 — Local time was labelled UTC in three places

| | |
|---|---|
| **ID** | MM-023 · **P1** · **CONFIRMED** · `MainScreen.kt`, `MealDetailScreen.kt`, `ImageForensics.kt` |

```kotlin
SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
```

The quoted `'UTC'` is a literal. The formatter used the device's default
timezone. **Every meal card, the detail screen, and the watermark burned into
every geotagged photograph displayed a local time explicitly labelled UTC.** For
a user in CEST, three hours wrong, stated with false precision — and in the
watermark's case, permanently, in an image that gets uploaded.

**Fix.** Real zone markers (`z` / `XXX`) and `Locale.getDefault()`.

---

### §9.2 — No disclosure that photographs leave the device

| | |
|---|---|
| **ID** | MM-024 · **P1** · **CONFIRMED** · `MainScreen.kt`, `ControlPanelScreen.kt` |

The location toggle carried a genuinely good prominent disclosure — coordinates
are saved, printed onto the photo, and the photo is sent for analysis. But
**nothing at any point told the user that the photograph itself is uploaded**.
The API-key card said only "Meal analysis needs a Hugging Face access token".

A meal photograph incidentally contains faces, home interiors, documents on the
table, and location clues. The README describes the app as "privacy-conscious".

**Fix.** A line under the capture actions, where the decision is made rather than
buried in Settings: *"Photos are sent to your configured analysis provider.
Manual entry stays on this device."* Full data-flow documentation in
`docs/PRIVACY_THREAT_MODEL.md`.

---

### §9.2b — A second, undisclosed network path

| | |
|---|---|
| **ID** | MM-040 · **P1** · **CONFIRMED** · `MainViewModel.generateDailyBriefing`, `AnalyticsScreen` |

Found late, while fact-checking a claim in the privacy threat model — the draft
asserted that the meal log and totals never leave the device, which was written
from the photo path alone and was **wrong**.

**Trends → Daily summary** sends today's meal names and macro totals to the same
provider as plain text, with no disclosure anywhere. It is a genuinely separate
data category from the photo path — a list of what the person ate today, in
words — and it was invisible in the UI and absent from the documentation.

Worse, the prompt escalated with the compliance status:

```kotlin
ComplianceStatus.SUBVERSIVE -> "EXTREME CORRECTION REQUIRED. AGGRESSIVE TONE."
ComplianceStatus.CRISIS     -> "TERMINAL WARNING. ABSOLUTE CONDEMNATION."
...
"Judge the subject's biological efficiency and mandate compliance for the day."
```

An open-ended instruction to a language model to condemn someone for what they
ate, with the intensity scaled by how far they were from a calorie target, in an
app that has no standing to make that judgement and no control over what the
model then says.

**Fix.** A disclosure line beside the button naming exactly what it transmits.
The tone escalation is removed; the prompt now asks for a factual summary in the
clipped terminal register and explicitly forbids evaluating the person or giving
health, dietary or medical advice. The persona survives; the condemnation does
not.

**Also found in the same sweep.** Two further `SUBVERSIVE` gates that the §1.1
pass had missed: the **Meal map** button, and a full-screen red overlay covering
**the entire Trends tab** reading "Trends are locked while you're well off
target." Both removed. The map button is now gated on whether there is anything
to plot, which is the condition that actually matters — and it shows the count,
so a user is not sent into a full-screen tactical grid to be told there is no
data (audit brief §17.9).

---

### §9.3 — "Encrypted" was not claimed, and is not implemented

| | |
|---|---|
| **ID** | MM-025 · **P2** · **CONFIRMED** · `MandatePreferences.kt` |

Verified: the API key lives in a **plaintext** DataStore Preferences file. There
is no Keystore use and no custom crypto anywhere in the repository. To the
existing code's credit, its KDoc said so plainly ("It is not encrypted at rest:
on a non-rooted device the app sandbox is the protection") — one of the few
places where documentation matched reality.

**No cryptography was added.** A Keystore-wrapped token is readable by the same
process that must send it in a header; the effort buys very little against a
device-access attacker while inviting the word "encrypted" into the docs. The
KDoc is expanded to state exactly what the sandbox does and does not defend
against, and `data_extraction_rules.xml` now explicitly excludes the DataStore
directory, the database and the evidence directory from cloud backup and device
transfer — so a future change flipping `allowBackup` cannot silently start
syncing the token.

---

### §10.1 — Permissions

Audited each declared permission. `INTERNET`, `CAMERA`, `POST_NOTIFICATIONS`,
`ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are justified and requested
in context. Three were removed as unnecessary after §11.1 and §11.4:
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`.

`ACCESS_FINE_LOCATION` was retained rather than narrowed to coarse: the recorded
coordinate is shown back to the user at six decimal places and plotted on the
map, so fine is what the feature actually delivers. Requesting coarse while
presenting the result as precise would be the worse choice. Documented inline in
the manifest.

`POST_NOTIFICATIONS` is still requested in `onCreate` with no rationale, on first
launch, before the user knows what the app does — **not fixed**, see §Remaining.

---

### §11.1 — The foreground service was not doing data sync

| | |
|---|---|
| **ID** | MM-026 |
| **Severity** | **P1** |
| **Category** | Platform correctness / Play policy / battery |
| **Confidence** | **CONFIRMED** (code) / **HIGH** (policy) |
| **Files** | `MandateSurveillanceService.kt`, `AndroidManifest.xml`, `MainViewModel.kt` |

`MandateSurveillanceService` was declared `foregroundServiceType="dataSync"`. It
synchronised nothing. Its entire function was to observe two local Flows and
post an ongoing notification showing today's calorie total. It was started from
`MainViewModel.init` — i.e. on every ViewModel construction, including after
process death — inside a `try/catch` swallowing the background-start exception.

Three problems:
1. **Policy.** Play requires the declared FGS type to match actual use.
   "dataSync" for "display a local number in a notification" is a mismatch and a
   plausible review rejection.
2. **It could not do its job anyway.** Android 15 caps `dataSync` services at
   six hours; the code correctly implements `onTimeout` and stops itself. So the
   "omnipresent surveillance" HUD died after six hours regardless.
3. **No foreground service was needed.** Posting an ongoing notification does not
   require one.

**Fix.** The service, its manifest entry, and both foreground-service permissions
are removed. Phase 11's guidance applied directly: do not keep a foreground
service because an earlier implementation used one.

---

### §11.2 — Brand-new users were told they were overdue

| | |
|---|---|
| **ID** | MM-027 · **P1** · **CONFIRMED** · `MandateEnforcementWorker` |

```kotlin
val lastLoggedTime = latestMeal?.timestamp ?: 0L
if (currentTime - lastLoggedTime > sixHoursInMillis) { notify() }
```

With no meals, `lastLoggedTime` is the Unix epoch, so the condition is
`now − 0 > 6h` → always true. On the worker's first daytime run, a user who had
just installed the app and logged nothing was notified: *"You haven't logged a
meal in a while."*

**Fix.** No latest meal → return `Result.success()` without notifying. Someone
who has never logged a meal is not overdue.

---

### §11.3 · §11.4 — Notification importance and dead boot receiver

- **MM-028 · P2** — The reminder channel was `IMPORTANCE_HIGH` with
  `PRIORITY_HIGH`: a heads-up card interrupting whatever the user is doing, for
  a meal reminder. Lowered to `DEFAULT`. An app that interrupts people about food
  several times a day gets its notifications disabled entirely.
- **MM-029 · P2** — `MandateBootReceiver` returned immediately on
  `SDK_INT >= VANILLA_ICE_CREAM` (35). With `targetSdk = 37`, it was dead code on
  the majority of target devices while still costing `RECEIVE_BOOT_COMPLETED`.
  WorkManager reschedules its own work across reboots. Removed.
- **MM-030 · P2** — `EnforcementScheduler` used `ExistingPeriodicWorkPolicy.UPDATE`
  and ran on every app start, resetting the period each time; an app opened more
  often than every six hours could push the next run indefinitely. Changed to
  `KEEP`, and a `requiresBatteryNotLow` constraint added.

---

### §12.1 — The widget went stale at midnight

| | |
|---|---|
| **ID** | MM-031 · **P1** · **CONFIRMED** · `MandateWidget.kt`, `mandate_widget_info.xml` |

The widget was only refreshed by explicit `updateAll()` calls from the ViewModel
on write. `updatePeriodMillis` was unset. After midnight it displayed
**yesterday's calorie total** until the app was next opened. A stale nutrition
figure on the home screen is worse than no widget.

Also: `current.toFloat() / target` with no zero guard produced `Infinity` (or
`NaN` at `0/0`), and `coerceIn` passes `NaN` straight through to the progress
indicator.

**Fix.** `updatePeriodMillis="1800000"` (the platform minimum), the reminder
worker also calls `updateAll`, zero-target guarded, and `targetCellWidth/Height`,
`description` and `previewLayout` added for correct presentation in the picker.

---

### §14.1 — Compose review

**Real defects found and fixed:**
- `LazyColumn { items(mealEntries) }` — **no key**. Recomposition and scroll
  position were tied to list index, so inserting or deleting a meal reused item
  state across different meals. Fixed with `key = { it.id }`.
- The CRT overlay was **invisible and paid for** (§19.1).
- Both entry dialogs used `remember`, not `rememberSaveable` — a rotation while
  typing discarded everything. Fixed.
- Haptics: `HapticFeedbackType.LongPress` fired for every ordinary tap, and on
  every `onValueChange` of the calorie slider — continuous vibration for the
  whole drag. Fixed.

**Considered and deliberately not changed:**
- **`MainViewModel` is not split.** It is a genuine god object (§1), but
  decomposing it touches every screen at once, and doing that in the same change
  set as the P0 data-access fixes would make both unreviewable. Recorded as the
  top structural follow-up.
- **No MVI framework introduced.** `UiState` is already a sealed class with the
  right four cases; the problem was never the shape of the state.

---

### §18.1 · §19.1 — Design system and CRT effects

**MM-032 · P2 — The status banner ignored the active theme.** Four hardcoded
literals (`0xFF00E5FF`, `0xFFFFEA00`, `0xFFFF1744`, `Color.Gray`), so switching
terminal theme left the loudest element on the dashboard in the old palette.
Now driven by `colorScheme`.

**MM-033 · P2 — The banner was a full-bleed alarm for routine information.**
Confirmed the audit brief's hypothesis: a saturated full-width yellow fill
carrying *"Close to target. Room to improve."* Reserving the strongest signal for
ordinary status leaves nothing to escalate to. Now a 4 dp accent rule against a
themed surface.

**MM-034 · P2 — The typography scale is incomplete.** `Typography` defines 7 of
~15 styles. `labelLarge`, `labelMedium`, `bodyMedium` and others fall through to
Material's **Roboto** default. Since `labelLarge` was the banner style and
`labelMedium` the navigation labels, the app was accidentally mixed-typeface —
not an authored decision. *Documented; not fixed* — completing the scale is a
visual change best made against a running device.

**MM-035 · P1 — The CRT scanline overlay was invisible and cost GPU time
anyway.** `Modifier.terminalOverlay(status)` was applied to `Scaffold`, and
`drawBehind` paints *behind* the node's own drawing — including `Scaffold`'s
opaque `containerColor`. Roughly 300 `drawRect` calls per frame, every one of them
completely occluded. The README's "custom CRT treatment" was not visible in the
running app.

It also tinted the entire screen red in proportion to compliance status, up to a
20% red wash over every pixel, degrading contrast on the data underneath.

**Fix.** `drawWithCache` + `onDrawWithContent` so it draws *over* content and is
actually visible; one repeating-gradient rect instead of a per-line loop; a fixed
3% alpha that never depends on how someone ate.

---

### §21.1 — Accessibility

**Fixed:**
- Macro rows announced as *"P colon zero g, C colon zero g, F colon zero g"*.
  Sighted users have colour and column position to decode the abbreviations; a
  screen-reader user has neither. Now one sentence via
  `macroContentDescription`: *"Protein 0 grams. Carbohydrates 0 grams. Fat 0
  grams."*
- Every row's delete button announced the bare word **"DELETE"** with no
  indication of which meal. Now *"Delete {name}"*.
- The liquid checkbox row had a `clickable` modifier *and* a `Checkbox` with its
  own `onCheckedChange` — two targets, announced twice. Now one target with
  `Role.Checkbox`.
- Meal names were rendered `.uppercase()`. All-caps is defensible for short
  system labels and slows reading of arbitrary content — and it mangles names the
  user typed. Now shown as entered.
- The camera marquee re-announced its text ten times a second.

**Not verified — requires a device:** actual TalkBack ordering and focus
traversal, real touch-target bounds, and font-scale reflow at 1.15/1.3/1.5/2.0.

**Measured statically — contrast.** `Color.DarkGray` (`#444444`) on
`TerminalBlack` is **2.0:1**, well below the 4.5:1 AA threshold; it was used for
borders and for disabled control labels. Most of those uses disappeared with the
lockout removal (§1.1). `Color.Gray` (`#888888`) on black is ~5.3:1 — passes for
body text, marginal for the 11 sp `labelSmall` it is usually paired with.
*Documented; the remaining `Color.Gray` metadata uses are not changed here.*

---

### §22.1 — Internationalization

**MM-036 · P2 · CONFIRMED.** `strings.xml` contained exactly **one** string
(`app_name`). Every other user-visible string in the application — every button,
dialog, error, notification and empty state — is a hardcoded Kotlin literal.

Date formatting used `Locale.US` throughout, and the decimal-separator bug
(§2.5) is the same root cause reaching data entry.

**Partially fixed.** Locale-correct date and number handling is done, and the
comma-separator bug is fixed. Full string extraction is **not** done — it touches
every file in the UI layer and would bury the correctness fixes in this change
set. Recorded as a follow-up.

---

### §23.1 — Date and time

`MealRepository.dayBoundaries()` is thoughtfully built: it re-emits at each
midnight rather than capturing a fixed cutoff, and adds a day via `Calendar`
rather than 24 h of millis so DST transitions are handled. Two residual issues:

- **MM-037 · P2 · HIGH** — the rollover uses `delay()`, which is driven by
  `CLOCK_MONOTONIC` and does not advance during deep sleep. A device asleep
  across midnight rolls over late, when it next wakes. Nothing listens for
  `ACTION_TIME_CHANGED` or `ACTION_TIMEZONE_CHANGED`, so travelling across
  timezones does not re-evaluate the boundary until the next scheduled emission.
  *Documented, not fixed — the correct fix is a broadcast receiver, best verified
  on a device.*
- **MM-038 · P2 · CONFIRMED** — `getTodayMeals` is `timestamp >= :startOfDay`
  with no upper bound, so a future-dated row is counted in "today" forever.
  Mitigated at the restore boundary (§5.1); the query itself is unchanged.

`SimpleDateFormat` is used throughout rather than `java.time`. With `minSdk 29`,
`java.time` is available without desugaring. Not migrated — a mechanical change
with real regression surface, and the actual bugs (§9.1, §8.2) were in the
format strings, not the API.

---

### §25.1 — Performance

**MM-039 · P1 · CONFIRMED — the meals table had no index.** Every read filters or
orders by `timestamp` — today's totals, the weekly window, the history list, the
widget — and SQLite answered each with a full table scan plus a sort. Fixed by
`MIGRATION_6_7`.

**Not measured.** Startup, jank, memory and GPU cost were **not** profiled: no
device. `am start -W`, `dumpsys gfxinfo` and `dumpsys meminfo` could not be run.
No Baseline Profile or Macrobenchmark was added — adding either without the
ability to measure the before/after would be cargo-culting the tooling.

**Image pipeline, read statically:** the analysis path decodes at ≤1600 px, then
`createScaledBitmap`s to 800 px, then JPEG-compresses at quality 80. Two full
bitmap allocations, and the intermediate is never `recycle()`d. The sizing is
sensible for a vision model. `ImageForensics.calculateInSampleSize` uses `||`
where the standard AOSP snippet uses `&&`, which can leave the decoded bitmap up
to ~2× `maxDimension` on one edge — its KDoc claims it stays within
`maxDimension`. *Documented, not fixed.*

**EXIF orientation is ignored** on both the analysis and watermark paths, so a
portrait photo from a sensor reporting rotation is sent to the model sideways.
`Coil` handles EXIF for display, so this is invisible in the app and only affects
model accuracy. *Documented, not fixed.*

---

## 3b. Post-change verification

Re-run clean after every change in this audit:

| Task | Result |
|---|---|
| `clean testDebugUnitTest lintDebug assembleDebug assembleRelease compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL** |
| Unit tests | **129 run, 0 failures, 0 errors, 0 skipped** (was 44) |
| `lintDebug` | **22 results, 0 code findings** (was 32). All 21 are dependency-version advisories (`GradleDependency` ×15, `NewerVersionAvailable` ×5, `AndroidGradlePluginVersion` ×1). Every code, resource and manifest finding is resolved — `UnusedResources`, `DataExtractionRules`, `RedundantLabel`, `UnusedAttribute`, `UseKtx` and `ObsoleteSdkInt` are all cleared. Dependency bumps were deliberately left alone: upgrading libraries inside an audit change set mixes unrelated regression risk into it |
| `lintVitalRelease` | passed |
| Debug APK | 79.15 MB |
| Release APK | 5.87 MB, R8 minified, **unsigned** (no keystore available) |
| Release-key guard | **verified by execution** — release fails with a key present, debug succeeds (§4.1) |
| `connectedDebugAndroidTest` | **STILL NOT RUN** — no device |

---

## 3c. Second pass — product and platform follow-ups

Addressed after the correctness and privacy work, in a separate commit.

### §17.3 — The calorie target was the least precise control in the app

| | |
|---|---|
| **ID** | MM-041 · **P1** · **CONFIRMED** · `ControlPanelScreen` |

`Slider(valueRange = 1200f..4000f, steps = 28)` — exactly **29 reachable
values**, 100 kcal apart, and no other way to set it. A target of 1850, 4200, or
900 under medical supervision was simply not expressible. This is the single
number every screen in the app measures against.

**Fix.** A numeric field (committing on IME-done and focus loss, so typing "2" en
route to "2500" does not briefly set a 2 kcal target), ±50 stepper buttons at
48 dp, and a slider over the common range. Storage bounds widened to
500–10,000 and documented as sanity limits, not dietary advice — the app has no
standing to tell someone their own target is wrong.

### §18.2 — Half the type scale was accidentally Roboto

| | |
|---|---|
| **ID** | MM-034 (from the first pass) · **P2** · **CONFIRMED** |

`Typography` defined 7 of ~15 styles. `bodyMedium` (11 call sites), `labelMedium`
(4) and `labelLarge` (2) — between them the navigation labels, the status banner,
the settings descriptions and most dialog copy — fell through to Material's
Roboto default. The app was already mixed-typeface; it was just not on purpose.

**Fix.** The scale is complete, and the split is now a stated rule:

- **Monospace** for anything that reads as instrumentation — every `label*`,
  `title*`, `headline*` and `display*` style. Monospaced digits keep columns of
  macros aligned down a list, which is the actual functional argument for the
  face.
- **The platform sans** for `body*` — settings descriptions, dialog copy,
  disclosures, empty states, error messages. This is the audit brief's own
  recommendation (§17.5): the terminal face strategically, a readable face for
  the paragraphs. The identity survives because monospace still carries every
  element the eye lands on first.

Call sites where `bodyMedium` was carrying *data* rather than prose (the meal
card's calorie figure, `DetailRow` values) were moved to `labelLarge` so the
numbers stay monospaced. `labelSmall` went from 11 sp to 12 sp: it is the most
used style in the app and it was carrying the macro readout at the smallest size
on screen.

### §25.2 — EXIF orientation was ignored on both image paths

| | |
|---|---|
| **ID** | MM-042 · **P1** · **CONFIRMED** (code) / **HYPOTHESIS** (accuracy impact) · `ImageForensics`, `MainViewModel` |

Phone cameras record the frame in the sensor's native orientation and describe
the correction in an EXIF tag rather than rotating pixels. Neither the analysis
path nor the watermark path read that tag, so **a photo taken in portrait was
sent to the vision model on its side** — and the coordinate watermark was drawn
sideways along with it.

Coil applies EXIF when rendering, so this was invisible inside the app. It showed
up only as worse estimates: the failure mode that is hardest to notice and most
expensive to have in the feature the product is built around.

Marked HYPOTHESIS on magnitude because the accuracy delta was not measured
against a live endpoint — that needs a device and a provider key.

**Fix.** `ImageForensics.decodeUpright` — one shared, downsampled, EXIF-corrected
decode used by both paths. Also fixed here: the analysis path allocated two
bitmaps and never recycled the intermediate, so every capture left a full-size
bitmap for the collector; and `calculateInSampleSize` had two `var`s that were
never reassigned. **Guard:** `ImageForensicsTest` (7 tests) covers power-of-two
sizing, 12 MP and 50 MP frames, panoramas, aspect ratio, and the degenerate
`0 × 0` case that an unreadable stream produces.

### §10.2 — POST_NOTIFICATIONS was requested on first launch

| | |
|---|---|
| **ID** | MM-043 · **P2** · **CONFIRMED** · `MainActivity`, `ControlPanelScreen` |

Requested unconditionally in `onCreate` — a system permission dialog as the very
first thing a new user saw, before they knew the app had reminders. A permission
asked for out of context is a permission that gets denied, and on Android 13+ a
second denial is permanent.

**Fix.** Moved to the reminders toggle in Settings, fired at the moment the user
turns the feature on. The toggle also now detects the blocked state — checking
both the runtime permission *and* `areNotificationsEnabled()`, since the
permission can be granted while notifications are off for the app and reminders
are silently discarded — re-checking on `ON_RESUME` because the user can revoke
it from system settings while the app is backgrounded, and saying plainly where
to turn it back on.

### §9.4 — There was no way to delete everything

| | |
|---|---|
| **ID** | MM-044 · **P2** · **CONFIRMED** · `MainViewModel`, `ControlPanelScreen` |

Meals deleted one at a time, the activity log cleared separately, and the
photographs stayed on disk regardless (§6.1). Someone who wanted their food and
location history gone had to uninstall and trust that it worked.

**Fix.** `deleteAllData` — meals, every stored photo, and the audit log, behind a
confirmation dialog that names what goes and states it cannot be undone.
Settings and the API key are deliberately left alone: this is a data erase, not a
factory reset, and silently clearing a pasted credential would be its own
surprise.

### §37.1 — Empty states that misinformed

| | |
|---|---|
| **ID** | MM-045 · **P2** · **CONFIRMED** · `MainScreen`, `AnalyticsScreen` |

The dashboard's no-key empty state read *"Add an API key in Settings to start
logging meals."* This is **untrue** — manual entry needs no key and no network.
In the one place a new user with no key had nothing else to read, the app told
them it did not work.

The map's empty state was two lines of all-caps (*"NO GEOTAGGED DOSSIER ENTRIES
(ENABLE LOCATION IN SETTINGS TO TRACK)"*) reached by opening a full-screen
tactical grid to announce there was nothing to draw — the audit brief's
hypothesis §17.9, confirmed.

**Fix.** Both rewritten in sentence case, saying what happened and what to do
next. The map button is now disabled when there is nothing to plot and shows the
pin count when there is, so the expensive empty screen is unreachable rather than
merely reworded.

---

## 3d. Third pass — localization and the last raw-error path

### §22.2 — Every user-visible string was a Kotlin literal

| | |
|---|---|
| **ID** | MM-036 (from the first pass) · **P2** · **CONFIRMED** |

`strings.xml` held exactly **one** entry, `app_name`. Every button, dialog,
error, notification, empty state and content description in the application was
a hardcoded literal. The app could not be translated at all, and there was no
single place to review its voice.

**Fix.** All ~170 user-visible strings extracted. Two pieces of that were
architectural rather than mechanical:

- **`AnalysisError` now carries `@StringRes` ids, not `String`s.** A domain class
  has no business holding English, and resolving text at throw time is wrong for
  a value that may be displayed after a configuration change. `UiState.Error` and
  the restore failures carry ids the same way.
- **Resource lookups were hoisted into composition.** A `LaunchedEffect` body is
  a suspend lambda, and an activity-result callback is not a composable, so
  resolving a string inside either means going through `LocalContext` — which
  Compose does not observe. Lint caught three of these; all are now resolved in
  composition and captured.

Internal diagnostics — log tags, audit-log categories — were deliberately left
as literals. They are never shown to a user, and translating them would only make
crash reports harder to read.

Also corrected in the same pass: `Locale.getDefault()` read inside a composable
(non-observable), four count-bearing strings converted to `<plurals>`, and the
kcal-bearing strings annotated with the reason they are *not* plurals rather than
silently suppressed.

### §38.2 — The daily briefing still showed raw exception text

| | |
|---|---|
| **ID** | MM-046 · **P1** · **CONFIRMED** · `MainViewModel.generateDailyBriefing` |

Found while converting `UiState` to resource ids. The analysis path was given the
`AnalysisError` taxonomy in the first pass (§3.3); the briefing path was missed
and still did:

```kotlin
UiState.Error("UPLINK FAILURE: ${response.code()}")
UiState.Error("SYNTHESIS ERROR: ${e.localizedMessage?.uppercase()}")
```

Same defect, same screen family, one function away — a good argument for the
taxonomy being a type rather than a convention. Now mapped through
`AnalysisError`, with `CancellationException` rethrown rather than swallowed as a
failure.

### §38.3 — Camera capture failures showed CameraX internals

| | |
|---|---|
| **ID** | MM-047 · **P2** · **CONFIRMED** · `CameraCaptureScreen` |

`onCaptureError(exception.message?.uppercase() ?: "UNKNOWN CAPTURE FAILURE")` —
the same uppercased-exception pattern, in the last place it survived. The detail
now goes to logcat and the user gets one sentence.

### Regression guard for the copy rules

`ErrorCopyTest` parses `strings.xml` directly and asserts the rules hold: no
error message contains a URL, hostname, status code, `Exception`, or stack frame;
none is all-caps; each is a complete sentence; failures that leave the app usable
name the manual fallback; failures the user can fix point at Settings; the review
sheet says "Estimated" and "AI"; both network-boundary notices name the provider;
and no status-line string mentions locking — which would mean the gating from
§1.1 had come back.

A unit test has no `Context`, so reading the resource file is what makes these
checkable at all rather than deferring them to a device pass that has not
happened.

---

## 4. What changed

| Area | Change |
|---|---|
| **Removed** | Leniency plea, permanent lockdown, `LeniencyVerdict`, all compliance gating, the foreground service, the boot receiver, 3 permissions, the 3 corrupt screenshots |
| **Added** | `NutritionBounds`, `AnalysisError`, `PendingAnalysis`, `AnalysisReviewSheet`, `NutritionFormat`, `MIGRATION_6_7`, `MigrationTest`, `data_extraction_rules.xml` |
| **Fixed** | 3 UTC mislabels, evidence deletion, path traversal, HTTP timeouts, release logging, restore validation, CSV format, weekly averaging, chart scale, list keys, camera release, widget staleness, worker nagging, notification importance, decimal separators, numeric overflow, macro truncation, CRT overlay |
| **Build** | Release fails on an embedded credential; destructive migration is debug-only |

### Test coverage

| | Before | After |
|---|---|---|
| Unit tests | 44 | **129** |
| Instrumentation tests | 1 (asserts the package name) | 5 (+4 migration, unexecuted) |

New suites: `NutritionBoundsTest` (19), `HostileAnalysisResponseTest` (20),
`BackupRestoreHostileInputTest` (16), `NutritionFormatTest` (12),
`AnalysisErrorTest` (11), `ImageForensicsTest` (7), `ErrorCopyTest` (10),
`MigrationTest` (4, **not executed**).

---

## 5. Remaining work, in priority order

1. **Run `connectedDebugAndroidTest` on a device.** `MigrationTest` is the
   highest-value unverified assertion in the repository. Nothing should ship
   before it passes.
2. **Device verification pass** — TalkBack traversal, font scale to 2.0, touch
   target bounds, rotation through every dialog, the widget on a real launcher,
   startup and jank measurement.
3. **Decompose `MainViewModel`** into screen-scoped ViewModels with injected
   dependencies. It remains the god object described in §1, and the reason it
   matters is testability: it builds its own Retrofit instance in a `by lazy` and
   reads `Application` directly, so none of the analysis pipeline can be
   unit-tested. This is now the largest outstanding item.
4. `java.time` migration; paging for the history list; a translation into at
   least one other language, which is the only real test of the extraction.

Done in the second pass: the typography scale (MM-034), EXIF orientation
(MM-042), the POST_NOTIFICATIONS rationale (MM-043), the calorie target control
(MM-041), erase-all (MM-044), the empty states (MM-045).

Done in the third pass: string extraction (MM-036), the briefing error path
(MM-046), camera capture errors (MM-047), the day-rollover robustness fix
(MM-037), and a `networkSecurityConfig` forbidding cleartext.

---

## 6. Release readiness

**MacroMandate is not production-ready, and this audit does not make it so.**

What changed is that the defects which would have caused irreversible harm — a
model deleting a user's data, the app locking people out of their own records, a
credential shipping inside the APK, a schema change silently wiping the
database — are fixed, with tests. What has not changed is that **no part of this
application has been observed running.** Every UI, accessibility, performance and
background-execution finding here rests on source reading, and Phase 0 of the
brief is explicit that this is the weakest tier of evidence.

The gap to a defensible release is a device, a systematic manual pass, and the
follow-ups above.
