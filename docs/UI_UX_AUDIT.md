# MacroMandate — UI/UX & Usability Audit

> **Implementation status (2026-09-03):** Top 10 items #1–#9 and most of the
> P1/P2 backlog below are implemented on branch `ui-ux-audit-fixes` (commit
> `1caf3c2`). Verified by `./gradlew testDebugUnitTest` (152 tests, 0
> failures), `lintDebug` (0 errors, 22 warnings — unchanged baseline), and
> `assembleDebug` — **not** by running the app; no emulator/device was
> available for implementation either. A self code-review pass caught one
> regression (the widget-launched manual-entry dialog re-opening on every
> return to the Today tab) before it shipped; it's fixed in the same commit.
> Findings this pass did not touch: the "reduce visual effects" toggle (F.6),
> the audit-log nested-scroll question (D.8), and the larger structural items
> in section I. Status is also marked inline on the Top 10 table and the P1/P2
> backlog.

**Audit date:** 2026-09-03
**Scope:** Product/UI/UX usability. Not a security or code-quality review — see [`AUDIT_REPORT.md`](AUDIT_REPORT.md) and [`PRIVACY_THREAT_MODEL.md`](PRIVACY_THREAT_MODEL.md) for those.
**Method:** Full read of every `@Composable` file, the ViewModel, string resources, theme system, manifest, and both prior audit docs; visual inspection of the four physical-device screenshots in `docs/screenshots/` (captured on a Pixel 7, per `PLAY_RELEASE_CHECKLIST.md` §7). **No emulator or device was available to this audit** — nothing below is claimed as live interaction beyond what the four static screenshots actually show. Every finding is labelled:

- **Observed at runtime** — visible in one of the four `docs/screenshots/*.png` files.
- **Verified in source** — read directly in the `.kt` file cited, with line numbers.
- **Likely / requires runtime verification** — a strong inference from source that needs a device to confirm (timing, TalkBack traversal order, keyboard-overlap behavior).

A note on starting position: this codebase has already been through several audit passes (`AUDIT_REPORT.md` documents ~30 closed findings). A lot of what this brief asked me to go check for — shaming language, lockout-on-diet-deviation, hardcoded-zero silent failures, missing network disclosure, truncated macros, un-labelled UTC times — has already been fixed, and fixed well, with the reasoning left in code comments. I've verified each of those rather than re-reporting them; where the old fix is holding, it's named explicitly below so a future pass doesn't re-open it.

---

## A. Executive Summary

MacroMandate is in noticeably better shape than a typical "audit this app" engagement finds. The core loop — take/choose a photo, review the AI's numbers before anything is saved, log manually with no key required — is honest, reversible, and well-disclosed. Destructive actions (erase all data, delete a meal) already use two-step confirmation with the safe choice visually plainer than the dangerous one. Error copy is plain-English and actionable. The dystopian voice has mostly already been pushed out of the paths where it could cause real confusion (the AI review sheet, the manual-entry field labels, the error strings) and kept in the chrome (status banner, screen titles, button verbs like "LOG RECORD"). That's the right instinct and it's largely already executed.

**What's still wrong is narrower and more specific than "the theme gets in the way."** It clusters into five patterns:

1. **A meal can silently save with 0 kcal.** Both manual-entry dialogs default an unparseable/blank calorie field to `0` with no warning and no Save-button validation — confirmed in source (`ManualMealDialog`, `MainScreen.kt:767`) and then **observed in the wild**: `device_app.png` shows a real logged entry, "Salmon Bowl550", 45g protein, **0 kcal**, with "550" apparently mistyped into the name field instead of calories. This is the single clearest way the app currently produces wrong data without telling the user.
2. **The meal map plots noise, not location.** `SurveillanceMap` positions pins using `longitude % 1f` and `latitude % 1f` — the fractional part of the coordinate only, with the whole degree discarded (`AnalyticsScreen.kt:722-723`). Two meals eaten a block apart and two eaten on different continents can land in the same place, or the reverse. The map looks precise (crosshairs, grid, "Where you ate") and is not.
3. **The theme system doesn't quite cover the whole app.** ~101 hardcoded `Color.Gray/White/Black/Red/Cyan/DarkGray` call sites exist outside the theme; most are harmless (black backgrounds are correct in every theme), but several are not — a fixed cyan (`0xFF00E5FF`) drives the Daily Briefing and its loading spinner regardless of which of the four themes is active, and the CSV export button is the only one of three data buttons styled in flat gray instead of the theme's accent. Separately, two of the four themes' primary accent color is **pixel-identical** to one of the app's three "consistent across themes" macro colors (Cyber Cyan primary = Carbs color; Phosphor Green primary = Protein color), which undercuts the one piece of color-coding the app relies on for macro identification.
4. **A few controls are less legible than the rest of the app.** The sort control cycles through three states on tap with no way to see the other two without tapping through them; six filter chips plus the sort button sit in a horizontally-scrolling row with no scroll affordance, so "LIQUID" and "FLAGGED" are off-screen by default; haptic feedback uses `LongPress` semantics for ordinary single taps in most of the app but `ContextClick` for visually identical taps elsewhere (two Settings switches, right next to each other, use different haptic types for the same gesture).
5. **One filter and one visual warning are dead.** `isRestricted` is hardcoded `false` at every construction site in `MainViewModel.kt` now (the "forbidden sector" feature it powered was intentionally emptied in a prior audit pass) — so the "FLAGGED" filter chip and the red border/banner on the meal-detail photo can never fire from normal use. They still cost a chip slot and a moment of "wait, what does this mean" for anyone who taps it.

**Accessibility status:** better than average for an indie app, not finished. Typography is genuinely `.sp`-based throughout (scales with system font size — this is not a given), most `contentDescription`s are meaningful and several are hand-written to read naturally to TalkBack (`macroContentDescription()`), and touch targets are mostly ≥44-48dp. But three macro-entry field labels are pinned to `fontSize = 10.sp` with `maxLines = 1, softWrap = false` inside `weight(1f)` columns — a large-font-scale clipping risk that is easy to reproduce by reasoning alone — and two charts describe themselves to TalkBack with a non-merging `Modifier.semantics{}` that likely causes the chart's child numbers to be announced a second time right after the summary sentence.

**Is the tactical identity helping or hurting?** Net helping, and the app is not far from where it should end up. The team already found the right dividing line in most places ("character in the chrome, clarity in the data" — their words, `strings.xml:11`) and just hasn't finished applying it: a few thematic labels leak into data (a blank manual entry silently gets *named* "MANUAL REFUELING" — that's chrome pretending to be a food name), and a few visual treatments (the red "FLAGGED" overlay, the yellow "Late-night meal" highlight directly on the food photo) still read as a warning/penalty even though the audit trail says the underlying scoring penalty was removed. The fix in every one of these cases is smaller than a redesign.

---

## B. Product & Journey Map

### Information architecture (as built, not as assumed)

```
App (bottom nav: Today / Trends / Settings)
├── Today  (MainScreen.kt)
│   ├── Status banner (thematic, cosmetic — StateStatusBanner)
│   ├── Summary card — today's kcal vs target, P/C/F
│   ├── Action row — Take photo | Choose photo | Log manually
│   │   └── Network disclosure line (always visible, not a modal)
│   ├── Recent meals
│   │   ├── Search (text filter over name/id/assessment)
│   │   ├── Filter chips: ALL · TODAY · HIGH PROTEIN · HIGH CAL · LIQUID · FLAGGED*
│   │   ├── Sort (cycles NEWEST → CALORIES ↓ → PROTEIN ↓)
│   │   └── History list → tap → Meal Detail
│   ├── Manual entry dialog (AlertDialog)
│   ├── Camera capture screen (full-screen, own back stack via BackHandler)
│   └── Analysis Review Sheet (modal, blocks outside-tap/back dismiss)
├── Trends  (AnalyticsScreen.kt)
│   ├── Today's progress — donut chart, kcal vs target
│   ├── Meal map button (disabled if nothing geotagged) → SurveillanceMap*
│   ├── Daily summary button (network call #2 — disclosed inline)
│   ├── Macros today — 3 bars, %-of-calories
│   ├── Last 7 days — bar chart with target line
│   └── Generate weekly dossier debrief → export/copy dialog
├── Settings  (ControlPanelScreen.kt — single scroll, no section anchors)
│   ├── Analysis API key
│   ├── Terminal theme (4 full-width cards)
│   ├── Daily calorie target (text + stepper + slider, all three at once)
│   ├── Reminders (switch, contextual permission request)
│   ├── Location (switch, inline privacy copy)
│   ├── "Your data" — Export JSON / Restore JSON / Export CSV
│   ├── Erase everything (two-step confirm)
│   └── Activity log (nested-scroll list + Clear)
├── Meal Detail  (MealDetailScreen.kt, no bottom nav — pushed screen)
│   └── Edit / Delete (each with its own confirm dialog)
└── Home-screen widget  (Glance) — today's kcal/target + "Log a meal" → opens app to Today
```

**Not present in the current codebase** (the brief's checklist names it; flagging the gap in the ground truth): there is no `LeniencyPleaScreen.kt` and no compliance-gated screen of any kind. That's confirmed by `MainActivity.kt` — the nav graph has exactly four unconditional destinations, and `MainViewModel.kt:77-90`'s own comment explains why: the CRISIS-lockout system was removed. Good — nothing to preserve there, and nothing to warn about either.

### Jobs, by frequency

| Job | Frequency | Where |
|---|---|---|
| Check today's calories/macros at a glance | Very high | Today summary card, Trends donut |
| Log a meal (photo or manual) | Very high | Today action row |
| Correct/edit a just-logged meal | Medium | Meal Detail → Edit |
| Find an older meal | Medium | Today search/filter/sort |
| Check weekly trend | Low-medium | Trends |
| Change calorie target | Low | Settings |
| Configure API key | Setup-only (once, or after rotation) | Settings |
| Change theme | Rare, one-time-ish | Settings |
| Toggle reminders/location | Setup-only | Settings |
| Export/restore/erase | Rare, high-stakes | Settings |

The Today screen's layout roughly matches this ranking (log-meal actions above the fold, search/filter below, edit/delete reachable in one tap from a card). Settings does **not** rank by frequency — API key and Theme (setup-once) sit above Calorie Target (the number the whole app measures against, plausibly adjusted more than either) with no visual grouping to say why. See D.8/E.13 below.

---

## C. Top 10 Highest-Priority Improvements

| # | Finding | Screen/Flow | Severity | Impact | Effort | Why now | Status |
|---|---|---|---|---|---|---|---|
| 1 | Manual/Edit meal dialogs silently save blank or unparseable calories as **0 kcal**; Save is never disabled | Manual entry, Edit meal | **P1** | Wrong daily totals, no warning — reproduced in a real screenshot | XS-S | Directly corrupts the number the whole app is built around | ✅ Fixed — all three entry forms (manual, edit, AI review) now require an explicit name and calorie value before Save enables |
| 2 | `SurveillanceMap` plots the fractional part of lat/long only — pins bear no reliable relation to real position | Trends → Meal map | **P1** | A "privacy-forward" app ships a location feature that looks precise and isn't; undermines trust once discovered | M | Core promise of the feature is currently false | ✅ Fixed — real centroid-relative meters projection with an on-screen scale legend |
| 3 | Macro field labels (`Protein`/`Carbs`/`Fat`) hardcoded `10.sp`, `maxLines=1`, `softWrap=false` in three-column rows | Manual entry, Edit meal | **P1** | Clips/truncates at 1.3×–2× system font scale | XS | Direct, easily-reproduced accessibility failure in the highest-frequency form in the app | ✅ Fixed — constraints removed from all six sites |
| 4 | Sort control cycles 3 states on tap with no visible menu of options | Today → filter row | **P2** | User must tap-and-read repeatedly to find "PROTEIN ↓"; state is invisible until landed on | S | Touches every session that searches history | ✅ Fixed — `DropdownMenu` listing all three, active one checked |
| 5 | 6 filter chips + sort button in one horizontally-scrolling row, no scroll affordance | Today → filter row | **P2** | LIQUID and FLAGGED filters are undiscoverable without an accidental scroll | S | Same row as #4; fix together | ✅ Fixed — `FlowRow` wraps instead of scrolling |
| 6 | Hardcoded colors bypass the theme system in specific, visible spots (Daily Briefing cyan, CSV export button gray, audit-log red/cyan, weekly-chart grid/target line) | Trends, Settings | **P2** | Breaks visual coherence specifically in the 3 non-Cyan themes the app advertises | XS (per site) | Cheap, and it's the exact promise "4 terminal themes" makes | ✅ Fixed — all four sites swapped to `MaterialTheme.colorScheme.*` |
| 7 | `NutritionColors.Carbs` (#00E5FF) = `CYBER_CYAN.primaryColor` exactly; `NutritionColors.Protein` (#00FF66) = `MATRIX_GREEN.primaryColor` exactly | All screens showing macros, in 2 of 4 themes | **P2** | In the *default* theme, "this is cyan because it's carbs" and "this is cyan because it's a UI accent" collapse into one signal | S | Undercuts the one color-coding system the app has, in its default theme | ✅ Fixed — macro palette shifted off every theme's primary/secondary |
| 8 | Haptic feedback type is inconsistent for identical gestures (`LongPress` vs `ContextClick` on two adjacent Settings switches; `LongPress` fired on ordinary single taps almost everywhere) | App-wide | **P2/P3** | Minor per-tap, but touches literally every interaction in the core logging loop | XS | Cheapest fix on this list relative to how many taps it touches | ✅ Fixed — light feedback for selection/navigation, heavy reserved for commit/destructive, applied at every call site; several destructive confirm buttons that had *no* haptic also fixed |
| 9 | "Daily summary" loading overlay has no Cancel; "Take photo" analysis loading does | Trends vs Today | **P2** | Inconsistent recovery from a slow/stuck network call | XS | Same underlying network call class, different guarantees | ✅ Fixed — Cancel button added, backed by a cancellable ViewModel job |
| 10 | `FLAGGED` filter and the red "FLAGGED" photo overlay can never trigger (`isRestricted` is hardcoded `false` everywhere it's constructed) | Today filter row, Meal Detail | **P2/P3** | Dead control costing a chip slot and a moment of "what does this mean" | XS | One-line-of-reasoning fix: remove or repurpose | ◐ Partial — filter chip kept (a restored backup can still carry `isRestricted`), but the full-photo red/yellow warning washes for both `isRestricted` and `isNightRefueling` are now small corner badges, not a color wash over the photo |

---

## D. Screen-by-Screen Audit

### D.1 Today / Main (`ui/MainScreen.kt`)

**Working well**
- Action hierarchy is real, not just visual noise: "Take photo" is a filled primary button, "Choose photo" and "Log manually" are outlined/secondary. The network-boundary sentence ("Photos are sent to your configured analysis provider. Manual entry stays on this device.") sits directly under the buttons, at the decision point — not buried in Settings. **Verified in source**, `MainScreen.kt:628-633`; **observed at runtime** in all four screenshots.
- Delete-from-list confirmation names the meal and calorie value and states the consequence ("This will update daily and weekly totals") rather than a bare "Are you sure?" (`MainScreen.kt:346-354`).
- Empty state correctly distinguishes "no API key" from "no meals" and tells the user manual entry needs neither (`MainScreen.kt:288-296`, string `empty_no_meals_no_key`) — **observed at runtime** in `device_dashboard.png`.
- Double-submission on a second photo capture is handled: a new capture explicitly cancels/supersedes an in-flight one rather than racing it (`MainViewModel.kt:486-490`).
- A loading overlay during analysis states what's happening, gives a time expectation, and offers a way out (`MainScreen.kt:1143-1186`, `AnalysisLoadingOverlay`).

**Friction**
- **Manual entry silently accepts and saves invalid data.** `caloriesStr` is filtered to digits only, then `parseCalories(caloriesStr) ?: 0` (`MainScreen.kt:767`) — a blank or cleared field becomes a real `0` in the database with no dialog, no inline error, and the Save button (`manual_entry_save` / "LOG RECORD") is never disabled. A blank food name is silently replaced with the literal string `"MANUAL REFUELING"` (`MainScreen.kt:771`) — thematic chrome leaking into what will display as the food's actual name in history. **This is exactly what `device_app.png` shows**: an entry named "Salmon Bowl550" (protein 45g) logged at **0 kcal**.
- **Cyclic sort, no menu.** `MealSortOrder.next()` (`MainScreen.kt:75-79`) cycles NEWEST → CALORIES ↓ → PROTEIN ↓ → NEWEST on every tap of one `Surface` (`MainScreen.kt:994-1024`). There is no dropdown, no list of the three options — a user learns what's available only by tapping through all of them once. Predictability cost: to get from CALORIES ↓ back to NEWEST you must pass through PROTEIN ↓.
- **Filter row overflow.** `MealFilterChipRow` (`MainScreen.kt:977-1054`) is a single `horizontalScroll` `Row` holding the sort control + 6 filter chips. `device_app.png` shows NEWEST / ALL / TODAY fully visible and HIGH PROTEIN cut off mid-word at the screen edge — LIQUID and FLAGGED are entirely off-screen with no fade/arrow/count hinting more exist.
- **Haptic inconsistency**, concretely: `onCaptureMeal` (primary action) fires `LongPress` (`MainScreen.kt:214`); `onImportImage`/`onManualEntry` (secondary actions, same row) fire `ContextClick` (`MainScreen.kt:232, 238`). Meal-card tap, sort tap, filter-chip tap, search-clear tap, and "clear filters" tap all fire `LongPress` for an ordinary single tap (`MainScreen.kt:831, 951, 999, 1033, 1092`). None of these are long-presses or context menus.
- **`FLAGGED` filter is unreachable from normal use.** `MealFilter.FLAGGED` matches `meal.isRestricted || meal.isNightRefueling` (`MainScreen.kt:170`). `isRestricted` is hardcoded `false` at both meal-construction sites in `MainViewModel.kt:583, 768`, and the `forbiddenSectors` list that used to set it is now `emptyList()` (`MainViewModel.kt:218`) — a prior, correct audit fix (`AUDIT_REPORT.md` A-12). `isNightRefueling` can still be true, so the chip isn't *completely* dead, but its name and its pairing with a photo's red "FLAGGED" overlay (see D.5) both describe a condition (`isRestricted`) that cannot occur.
- Filter chips use `defaultMinSize(minHeight = 44.dp)` (`MainScreen.kt:997, 1031`) — 4dp under Android's commonly-cited 48dp touch-target guidance. Minor.

**Accessibility**
- The macro row on the summary card correctly uses `clearAndSetSemantics` to replace three separate "P / C / F" reads with one spoken sentence via `macroContentDescription()` (`MainScreen.kt:552-557`) — this is the right pattern and is not present everywhere it should be (see D.5, D.6).
- Delete icon buttons announce which meal, not a bare "Delete" (`MainScreen.kt:876-880`, string `content_description_delete_meal`).
- Bottom nav icons deliberately carry `contentDescription = null` because the label beneath already names the destination (`MainActivity.kt:104-106`) — correct, avoids TalkBack double-announcement, and is explicitly reasoned in-code.

**Recommendation:** see Top 10 #1, #3, #4, #5, #8, #10.

---

### D.2 Camera (`ui/CameraCaptureScreen.kt`)

**Working well**
- Camera use-cases are unbound in `onDispose` via `DisposableEffect`, not `LaunchedEffect` — the code comment explains this was previously leaving the camera (and its hardware indicator) open for the app's whole foreground lifetime after a single visit (`CameraCaptureScreen.kt:91-95`). Correct fix, worth protecting.
- The "Point the camera..." instruction ticker is now static; a comment records it used to be a per-100ms marquee that a screen reader would have re-announced ten times a second (`CameraCaptureScreen.kt:179-190`). Don't reintroduce motion here.
- Capture failure shows a generic, actionable snackbar; the CameraX exception detail goes to `Log.w`, not the user (`CameraCaptureScreen.kt:245-251`).
- `BackHandler` in `MainScreen.kt:386-388` returns cleanly to Dashboard; nothing was captured yet, so no confirmation is needed and none is shown. Correct — do not add one.

**Friction**
- No visible affordance for "no camera permission ever granted, denied permanently" — the permission request in `MainScreen.kt:111-119` only branches on grant/deny for that call; a user in the Android 13+ "denied twice" state gets silently returned to Dashboard with `screenState` never changing and no explanation of why the camera didn't open. **Likely, requires runtime verification.**
- The animated scanning-line/grid overlay (`CameraCaptureScreen.kt:64-84, 131-145`) is continuous while the camera is open. It's decorative, not informational (it doesn't track anything real), and it's the one piece of motion left in the app after the typewriter/marquee effects were removed elsewhere. Low priority given it's contained to one full-screen moment, but worth the same "does this survive reduced-motion" question the audit brief asks generally — see F.6.

**Accessibility**
- Shutter button has a real `contentDescription` (`content_description_shutter`) and is 80dp — well above target size (`CameraCaptureScreen.kt:216`).

---

### D.3 AI Analysis Review (`ui/AnalysisReviewSheet.kt`)

This is the best-designed screen in the app and should be the template for the other two entry forms.

**Working well**
- Not dismissible by outside tap or back press (`onDismissRequest = {}`, `AnalysisReviewSheet.kt:64`) — deliberate, and explained in a doc comment: the result isn't saved anywhere yet, so a stray touch shouldn't silently discard an estimate the user "just paid for" (a network call). Correct call; do not add swipe-to-dismiss or outside-tap-to-cancel here.
- Every field is editable in place before anything is written to Room (`AnalysisReviewSheet.kt:126-174`); a caveat banner appears only when something is actually worth flagging (contradiction, derived-not-measured calories, or clamped values) and only the single most relevant one, not all three stacked (`PendingAnalysis.kt:43-57`) — a genuinely well-reasoned "don't cry wolf" design.
- The photo is `contentDescription = null` on purpose — it's context for the numbers beside it, not information TalkBack needs to read on its own (`AnalysisReviewSheet.kt:92`).
- `MacroField`'s label is announced with its unit ("Protein in grams", not just "Protein") via `field_grams_description` (`AnalysisReviewSheet.kt:219,230`) — better screen-reader behavior than the two dialogs below, which don't do this.
- Blank/invalid calories on confirm fall back to **the AI's original estimate**, not zero (`AnalysisReviewSheet.kt:183`) — the safer of the app's two different blank-calorie behaviors (contrast D.4).

**Friction**
- Same missing-Next/Done-chaining issue as the other two forms (see D.4) — no `imeAction`/focus advance between Item name → Calories → Protein/Carbs/Fat.
- Confirm ("Save to log") is never disabled regardless of field validity, same as the other two forms.

**Recommendation:** Once D.4's validation/IME fixes exist, backport them here rather than the reverse — this file's error-recovery default (fall back to the known-good AI value, not zero) is the one worth generalizing.

---

### D.4 Manual Entry (`ManualMealDialog`, `MainScreen.kt:658-790`) & Meal Edit (`EditMealDialog`, `MealDetailScreen.kt:349-489`)

Two near-duplicate `AlertDialog` forms with the same fields, and — because they were written separately — subtly different behavior for the same situations.

**Findings**
- **Blank/invalid calories → silent 0**, confirmed above (Top 10 #1). `EditMealDialog` has the same line: `parseCalories(caloriesStr) ?: meal.calories` (`MealDetailScreen.kt:457`) — wait, on inspection this one is actually safer (falls back to the *existing* value, matching the Review Sheet's pattern) — **only `ManualMealDialog` defaults to a hard `0`** (`MainScreen.kt:767`). That's the one to fix first, and the three forms should agree on one policy afterward.
- **The liquid/drink checkbox has two different labels for the same field.** `ManualMealDialog` and `EditMealDialog` both use `field_is_liquid` — "Liquid consumption (beverage or shake)" (`MainScreen.kt:756`, `MealDetailScreen.kt:446`). `AnalysisReviewSheet` uses `field_is_drink` — "Drink rather than food" (`AnalysisReviewSheet.kt:170`). Same boolean (`isLiquid`), two different mental models, three screens apart. The former wording is more precise (it resolves the "is a protein shake a drink or food?" ambiguity the latter doesn't) — standardize on it and delete `field_is_drink`.
- **Macro field labels clip at large font scale** (Top 10 #3): `label = { Text(stringResource(...), maxLines = 1, softWrap = false, fontSize = 10.sp) }` appears six times across the two dialogs (`MainScreen.kt:715, 724, 733`; `MealDetailScreen.kt:405, 414, 423`) inside `Modifier.weight(1f)` fields in a 3-across row. At 1.3×–2× system font scale this is a near-certain truncation. `AnalysisReviewSheet`'s equivalent field doesn't hardcode size or forbid wrapping — same three labels, no clipping risk there.
- **No IME action chaining.** None of the six `OutlinedTextField`s in either dialog sets `imeAction`/`KeyboardActions` to advance focus. Contrast `CalorieTargetControl` in Settings, which does exactly this correctly (`ControlPanelScreen.kt:874-880`) — the pattern exists in the codebase, it just wasn't applied to the two highest-frequency forms in the app. Default IME behavior on each of these singleLine fields is "Done," which dismisses the keyboard rather than moving to the next field — a five-field form where every field-to-field move requires re-tapping the next field.
- **Save is never gated on validity** in either dialog — contrast `ApiKeyCard`'s Save button, which is correctly `enabled = draft.isNotBlank()` (`ControlPanelScreen.kt:646`). The pattern exists elsewhere in the codebase and wasn't applied here.
- Both dialogs correctly use `rememberSaveable` so a rotation mid-entry doesn't discard what's typed (`MainScreen.kt:665-670`, comment explains the prior bug) — good, protect this.
- Both dialogs wrap their field column in `verticalScroll(rememberScrollState())` (`MainScreen.kt:686-688`) — the right mitigation for keyboard-covers-field on a small screen, assuming Compose's automatic bring-into-view-on-focus behavior functions inside an `AlertDialog`'s own window. **Likely correct; requires runtime verification at 1.5×+ font scale with the keyboard open**, since `AlertDialog` renders in its own `Dialog` window and the Activity's `windowSoftInputMode="adjustResize"` (`AndroidManifest.xml`) does not automatically apply to a separate dialog window the same way.

**Is `AlertDialog` still the right container?** For the *current* five-field content, yes — it's short enough that a full screen would be overkill, and the brief's own guidance ("don't recommend changing the pattern unless there's a clear usability benefit") applies. The friction here isn't the container, it's validation and IME behavior inside it. Fix those first; only reconsider the container if a future field addition makes the form genuinely long.

---

### D.5 Meal Detail / Edit (`ui/MealDetailScreen.kt`)

**Working well**
- Edit (filled primary) vs Delete (outlined, error-colored) has real visual hierarchy, not two equal-weight buttons (`MealDetailScreen.kt:254-283`).
- Coordinates are read from `LocalConfiguration.current.locales[0]`, not `Locale.getDefault()`, specifically so a locale change is recomposed correctly — a comment records this was a real prior bug class (`MealDetailScreen.kt:232-236`).
- `.toInt()` truncation of macro grams was fixed app-wide via a shared `formatGramsValue()` (rounds, doesn't truncate) — verified present and used consistently (`NutritionFormat.kt:25-33`, used at `MealDetailScreen.kt:210-212`).

**Friction**
- **The red "FLAGGED" overlay can never appear** (see D.1) — `if (meal.isRestricted)` gates both a red `hudFraming` border and a red banner reading "FLAGGED" directly over the user's food photo (`MealDetailScreen.kt:99, 109-124`), and `isRestricted` cannot be `true` from any current app flow.
- **The "Late-night meal" banner still reads as a warning despite the copy being neutral.** `AUDIT_REPORT.md` A-14 correctly removed the scoring penalty and the "CIRCADIAN DISCIPLINE BREACH" language for `isNightRefueling`. But the *visual* treatment wasn't updated to match: it's still a yellow-tinted overlay with black-on-yellow caution-colored text stamped directly on the food photo (`MealDetailScreen.kt:126-141`). Yellow-on-photo reads as "warning" to most users regardless of what the label says, which risks re-introducing exactly the "ordinary dietary variance feels like punishment" problem the copy fix was meant to close. This is a case where the thematic visual language hasn't caught up with the (already-fixed) thematic copy.
- Two different delete-confirmation bodies for the same action depending on entry point: the list-view dialog names the meal and calorie count (`delete_meal_body`, `MainScreen.kt:347-351`); the detail-view dialog just says "This meal and its photo will be permanently deleted" (`delete_meal_body_detail`, `MealDetailScreen.kt:313`) with no name/calorie repetition. Minor, but it's the same destructive action described two different ways depending on where you started.
- Manual/Edit form issues (D.4) apply here too, since `EditMealDialog` lives in this file.

---

### D.6 Trends / Analytics (`ui/AnalyticsScreen.kt`)

**Working well**
- The map button's enabled state and label are computed from the exact same list the map will plot (`geotaggedCount`, `AnalyticsScreen.kt:63-65`) — a comment explains this replaced a version where the button could promise pins the map wouldn't draw. Good discipline; the "No geotagged" label change (rather than a generic disabled gray) is a nice touch (`AnalyticsScreen.kt:177-181`).
- The weekly chart's bar geometry and its target line are computed from one shared baseline constant (`BAR_MAX_HEIGHT`) specifically because a prior version used two different scales and a bar could visually sit on the wrong side of the target line (`AnalyticsScreen.kt:625-631`) — correct fix, protect it.
- The daily-summary and weekly-dossier network calls are disclosed inline, at the button, not only in Settings (`trends_summary_notice`, `AnalyticsScreen.kt:214-219`).
- Weekly average is computed over days-with-entries, not a hardcoded 7 — a two-day-old install won't see its real average reported as 2/7ths of itself (`AnalyticsScreen.kt:591-600`, comment explains the prior bug and that a screen reader reads the wrong number as fact).

**Friction**
- **`SurveillanceMap` does not plot real relative position** (Top 10 #2). `AnalyticsScreen.kt:722-723`:
  ```kotlin
  val x = (size.width / 2) + (meal.longitude.toFloat() % 1f) * size.width * 2
  val y = (size.height / 2) - (meal.latitude.toFloat() % 1f) * size.height * 2
  ```
  `% 1f` discards the integer degree of latitude/longitude entirely, keeping only the fractional part. Two meals eaten in different cities can produce the same fractional coordinates and land on top of each other; two meals eaten in the same room, on either side of a whole-degree boundary crossing, can land far apart. There is no scale, no legend, no basemap reference, and no on-screen indication that this is anything other than a literal spatial plot. The label — "Where you ate," the string used for the screen title — and the surrounding tactical-radar visual language both actively signal precision. **This is the single largest gap between what the interface implies and what the code actually does anywhere in the app.**
- **Hardcoded cyan bypasses the theme in the Daily Briefing.** `Color(0xFF00E5FF)` appears four times for the briefing panel border, its heading text, and its loading spinner (`AnalyticsScreen.kt:387, 393, 427, 431`) — regardless of whether Phosphor Green, Amber CRT, or Stark Mono is the active theme. This is the app's own default-theme primary color hardcoded in, so it happens to match when Cyber Cyan is selected and clashes in the other three.
- **The weekly chart's grid lines and target-reference line are also hardcoded**, not theme-derived: `Color.White.copy(alpha = 0.08f)` for the grid (`AnalyticsScreen.kt:638`) and `Color.Red.copy(alpha = 0.35f)` for the target line (`AnalyticsScreen.kt:646`), while the bars themselves correctly use `MaterialTheme.colorScheme.primary`/`error`. Partial theme coverage on one chart is more confusing than none, because the bars visibly respond to a theme switch and the reference lines around them don't.
- **No on-canvas legend for the target line.** The weekly chart's `contentDescription` correctly states the daily target in words for TalkBack (`AnalyticsScreen.kt:601-616`), but a sighted user gets a faint red horizontal line with no visible label saying what it is — it must be inferred.
- **Daily-summary loading has no Cancel**, unlike the photo-analysis loading overlay on Today (`AnalyticsScreen.kt:420-438` vs `MainScreen.kt:1143-1186`) — the same class of "network call that can take up to a minute" gets a different recovery guarantee depending on which screen triggered it.
- **The Daily Briefing overlay has no copy action**; the weekly dossier dialog does (`ContentCopy` button, `AnalyticsScreen.kt:341-367`). Tapping *anywhere* on the briefing overlay — including directly on the summary text — dismisses it (`AnalyticsScreen.kt:376-382`), so a user who wants to reference or copy what the AI said about their day has no way to.
- **Two chart `contentDescription`s use non-merging semantics.** `DailyComplianceChart` (`AnalyticsScreen.kt:462`) and the weekly chart (`AnalyticsScreen.kt:622`) both apply `Modifier.semantics { contentDescription = ... }` on the outer `Box` without `mergeDescendants = true`. Contrast the correct pattern used for the summary-card macro row on Today, `Modifier.clearAndSetSemantics { ... }` (`MainScreen.kt:552-557`), which *replaces* the subtree's semantics. Without `mergeDescendants`/`clearAndSetSemantics`, TalkBack is **likely** to announce the chart's full sentence description and then separately re-announce the individual numbers inside it (the "350" / "of 2500 kcal" / "150 kcal left" texts on the donut; each day/value pair on the bar chart). **Requires runtime verification with TalkBack**, but the source pattern is a well-known cause of duplicate announcements in Compose.
- Two user-facing strings are Kotlin literals, not resources — `"Weekly dossier debrief exported."` / `"Export failed."` (`AnalyticsScreen.kt:83`) and `"Dossier copied to clipboard."` (`AnalyticsScreen.kt:347`) — breaking the localization discipline the rest of the app follows (`strings.xml`'s own header comment states this was fixed everywhere else for exactly this reason).
- `WeeklyBarChart`'s over-target color coding is color-plus-number (the calorie figure is always printed above the bar, and turns error-red when over), so it degrades gracefully without color — but there's no non-color marker (icon, pattern) distinguishing an over-target day at a glance beyond hue.

---

### D.7 Map / Geotag Experience

Covered fully in D.6. The map is currently the audit's single highest-priority correctness finding, separate from its UX polish: it is not a smaller version of a real map, it's mathematically incapable of being one in its current form regardless of screen size or zoom.

---

### D.8 Settings (`ui/ControlPanelScreen.kt`)

**Working well**
- The whole screen is built from one reused `SettingsCard` component (icon + title + content) — real component consistency, not six different card implementations (`ControlPanelScreen.kt:670-698`, used 6+ times).
- Notification permission for reminders is requested contextually, at the toggle, not at first launch — and the toggle re-checks the *actual* OS notification-enabled state on every resume (not a cached value), with a plain-language recovery instruction if it's blocked (`ControlPanelScreen.kt:95-116, 363-372`, string `settings_reminders_blocked`). This is exactly right and should not be touched.
- Erase-everything confirmation puts Cancel first and visually plain (`OutlinedButton`), Delete second and colored — "the safe choice should be the easy one," per the code comment (`ControlPanelScreen.kt:264-269`). Correct pattern; the meal-delete dialogs already match it.
- Calorie target's text field commits on focus-loss rather than per-keystroke specifically so typing "2" on the way to "2500" doesn't briefly persist a 2-kcal target (`ControlPanelScreen.kt:847-868`) — thoughtful, protect it.
- Theme selection state is communicated by more than color: border width (1dp → 2dp) *and* a checkmark icon *and* the accent text color (`ControlPanelScreen.kt:774-812`) — not color-alone.

**Friction**
- **No section grouping beyond one heading.** API key, Theme, Calorie Target, Reminders, and Location sit as five consecutive cards with no visual grouping; only "Your data" (export/restore/erase) gets a section heading (`ControlPanelScreen.kt:400-404`). This isn't "too long" in an absolute sense — task frequency genuinely varies here (see B) — but nothing currently signals *why* the order is what it is, and Calorie Target (plausibly the most frequently revisited value) sits below API key and Theme (both setup-once).
- **Three redundant controls for one number.** Calorie target exposes a typed field, a ± stepper, and a slider simultaneously (`ControlPanelScreen.kt:854-926`) — the code comment correctly explains *why* the slider-only precursor was a problem (29 reachable values, 100 kcal apart), but the fix added two more controls rather than replacing the constrained one. All three are always visible and always the same visual weight for a value most users set once and revisit rarely. This is a defensible trade shown to have real reasoning behind it — the recommendation is to de-emphasize the slider (e.g., a smaller/quieter "fine-tune" affordance) rather than remove it.
- **The audit-log panel hardcodes category colors outside the theme**: `Color.Red` for SECURITY, `Color.Cyan` for MANDATE_SHIFT/CONFIG/PRIVACY, `Color.Gray` otherwise (`ControlPanelScreen.kt:732-736`) — in Stark Mono or Amber CRT this log will show fixed red/cyan lines that belong to no palette on screen.
- **The CSV export button is the only one of three data-portability buttons not styled from the theme.** JSON export (filled, `MaterialTheme.colorScheme.primary`) and JSON restore (outlined, primary border) both respect the active theme; CSV export uses `border = BorderStroke(1.dp, Color.Gray)` and `contentColor = Color.White` (`ControlPanelScreen.kt:459-460`) — in any of the three non-Cyan themes it will visibly not match its two siblings.
- **The audit-log `LazyColumn` is nested inside the screen's outer `verticalScroll` Column** (`ControlPanelScreen.kt:701-741`, height-constrained `120-220dp`) — a known Compose pattern that can produce a "sticky" scroll handoff where a fast scroll gesture that lands inside the log's bounds gets captured by the inner list instead of continuing the outer scroll. **Likely, requires runtime verification.**
- **API key "Clear" has no confirmation and sits directly beside "Save."** A single mistap wipes a working key immediately (`ControlPanelScreen.kt:652-664`); recoverable (re-enter the key) but inconsistent with how carefully the Erase-everything flow guards against the equivalent mistake.

**Accessibility**
- Every `SettingRow` label is given `Modifier.weight(1f)` specifically so a long/translated label doesn't push the `Switch` off-screen (`ControlPanelScreen.kt:560-565`, commented as deliberate) — good defensive layout, worth preserving as translations are added.
- Step buttons for the calorie target are explicitly 48dp with a comment explaining a stepper is "the last place to be stingy with the touch target" (`ControlPanelScreen.kt:930-948`) — correct, and a good internal example to point at when fixing the 44dp filter chips on Today.

---

### D.9 API Key Configuration

Covered in D.8. Additional note: the field correctly uses `PasswordVisualTransformation` with a visibility toggle (`content_description_show_key`/`hide_key`) and the "present" state shows a hint (last 4 chars) rather than the full key (`ControlPanelScreen.kt:588-596, 607-630`) — matches the privacy threat model's stated behavior and is good practice; nothing to change here beyond the Clear-button confirmation noted above.

### D.10 Target Configuration

Covered in D.8.

### D.11 Reminders

Covered in D.8 — this is one of the strongest flows in the app (contextual permission, live re-check, plain recovery copy). No changes recommended.

### D.12 Location

**Working well:** the toggle's description states the real consequence in one sentence — coordinates saved, printed on the photo, and that photo sent to the analysis service — directly under the switch, not in a separate policy screen (`settings_location_description`, `ControlPanelScreen.kt:390-394`). This is the right place and the right amount of disclosure.

**Gap (documented, not newly found):** `PRIVACY_THREAT_MODEL.md` §2.3 already names this — there's no way to strip coordinates from an already-saved meal without deleting the whole record. Worth a small Settings-adjacent affordance ("remove location from this meal") on the Meal Detail screen if this becomes a real user request; not urgent enough to be in the Top 10.

### D.13 Export/Restore

Covered in D.6/D.8. `settings_data_description` already explains the JSON-vs-CSV distinction in plain terms ("Export structured JSON archives for full database restoration, or CSV dossiers for external spreadsheet analysis") — reasonably clear, if "dossiers" is doing a little unnecessary work in an otherwise plain sentence. Minor: exported filenames use a raw millisecond timestamp (`MacroMandate_Backup_${System.currentTimeMillis()}.json`, `ControlPanelScreen.kt:419`) rather than a human-readable date — mildly unhelpful when managing several exports in a file browser later. Quick win.

### D.14 Erase Data

Covered in D.8 — this is the strongest destructive-action pattern in the app (two-step, Cancel-first-and-plain, explicit description of what's kept vs. deleted, explicit "cannot be undone, export first if you want a copy"). Do not weaken it.

### D.15 Home-Screen Widget (`widget/MandateWidget.kt`)

**Working well:** the `target > 0` guard preventing `Infinity`/`NaN` in the progress bar is present and correctly reasoned in a comment (`MandateWidget.kt:41-46`) — matches `AUDIT_REPORT.md` A-20.

**Friction:** the widget's "Log a meal" button starts `MainActivity` with no extras (`MandateWidget.kt:87-90`) — it always opens to the Today dashboard, one more tap away from the action the button's own label promises. For a widget whose entire reason to exist is fast access, this is a real, low-effort miss: deep-link the tap into the manual-entry dialog (or at minimum the capture flow) rather than the dashboard.

---

## E. Cross-Cutting UX Findings

**Navigation.** Three unconditional bottom-nav destinations plus one pushed detail screen; `launchSingleTop`/`restoreState` are set on nav (`MainActivity.kt:124-131`), so tab state should survive switching — **requires runtime verification** for scroll-position retention specifically. Nav icons correctly avoid double-announcement (D.1).

**Hierarchy.** Real primary/secondary distinction exists on Today (D.1) and Meal Detail (D.5); it's absent inside the Settings list (D.8) and inside the filter-chip row, where six filters and one sort control all receive identical visual weight regardless of how often each is used.

**Typography.** Deliberate two-face system (monospace for "instrumentation," system sans for prose the user has to actually read), documented and reasoned in `Type.kt:9-33` — this is good, non-obvious design work and should be preserved as-is. The one place it's overridden inconsistently is the six macro-label fields flagged in D.4.

**Color.** ~101 hardcoded `Color.*` literals exist outside the theme system across 8 UI files (grep count). Most are inert (black backgrounds, which are correct in every theme, since all four themes share `background = TerminalBlack`, `Theme.kt:19`). The ones that aren't are named individually in D.6/D.8/C. Separately: `NutritionColors` are deliberately fixed "across themes" (`Color.kt:69-75`) but two of the four themes' `primaryColor` collide exactly with a macro color (Carbs=Cyber Cyan primary; Protein=Phosphor Green primary) — see C #7.

**Terminology.** Two labels for one field (`field_is_liquid` vs `field_is_drink`, D.4); a placeholder value that reads as user data ("MANUAL REFUELING" as a literal food name, D.1); a filter/visual state that can never fire (`FLAGGED`, D.1/D.5). Everywhere else — error copy, empty states, disclosure lines, field labels — the "chrome vs. data" split from `strings.xml`'s own header comment is followed correctly and should be the standard applied to fix the exceptions above, not a new standard introduced for them.

**Forms.** See D.4 in full — this is the single most concrete, fixable cluster in the audit.

**Feedback/Loading/Errors.** Error strings are uniformly plain, actionable, and free of hostnames/status codes (`strings.xml` Errors section, cross-checked against `AnalysisError.kt`'s taxonomy) — this is genuinely well done and covered by its own test class (`ErrorCopyTest`, confirms no URLs/hostnames, sentence structure). The one inconsistency is loading-state recovery: photo analysis gets a Cancel button, daily-summary generation doesn't (D.6).

**Destructive actions.** Erase-everything and both delete-meal confirmations are well-built (Cancel-first, plain-vs-colored, explicit consequences). API-key Clear is the one destructive-ish action without a confirmation step, sitting directly beside Save (D.8).

**Haptics.** No coherent system currently exists. `HapticFeedbackType.LongPress` is used for ordinary single taps in the large majority of call sites app-wide (list item tap, filter chip tap, sort tap, search-clear, manual-save, most Settings actions); `ContextClick` appears for a handful of others (photo import, manual-entry entry point, one Settings switch, the calorie stepper) with no discernible rule distinguishing which taps get which. Concrete adjacent-inconsistency example: the Reminders switch fires `ContextClick` (`ControlPanelScreen.kt:345`) and the Location switch — same component, same screen, two cards apart — fires `LongPress` (`ControlPanelScreen.kt:384`).

**Privacy UX.** Already strong at the moments that matter: the capture-network notice sits under the buttons (not in a settings-only policy page), the location toggle states the real consequence inline, and the Daily-summary button carries its own network disclosure. Nothing to add here beyond what D.6/D.12 already note.

**Responsive layout.** No fixed-`dp` text sizes were found (typography is `.sp`-based throughout, which is the harder and more important thing to get right); the specific clipping risk is the six macro-entry labels in D.4 (`maxLines=1, softWrap=false` at a fixed 10sp). No evidence of layouts assuming a single fixed screen width beyond that.

**Localization.** Two literal (non-resource) strings found in `AnalyticsScreen.kt` (D.6) — the only regression against an otherwise consistently externalized string set (confirmed via `strings.xml`'s own stated convention and spot-checks across all six UI files).

---

## F. Accessibility Report

### F.1 TalkBack
- **Good:** `macroContentDescription()` produces natural sentences ("Protein 40 grams. Carbohydrates 12 grams. Fat 8 grams.") instead of a literal reading of "P: 40g C: 12g F: 8g" (`NutritionFormat.kt:43-46`), applied via `clearAndSetSemantics` on the Today summary card (`MainScreen.kt:552-557`). Delete buttons announce the specific meal name (D.1). Bottom-nav icons are deliberately `contentDescription = null` to avoid double-announcing the destination the label already states (`MainActivity.kt:104-106`).
- **Gap:** The two charts on Trends use non-merging `Modifier.semantics{contentDescription=...}` rather than `clearAndSetSemantics`/`mergeDescendants=true` (D.6) — likely double-announcement of the chart's numbers. This is the one place the "one correct pattern, inconsistently applied" issue shows up in accessibility rather than just visuals.
- **Requires runtime verification:** traversal order through the Settings screen's mixed card/switch/text layout, and through the manual-entry dialog's 3-across macro row.

### F.2 Touch targets
- Camera shutter (80dp), calorie-target step buttons (48dp, explicitly reasoned in-code), and standard `IconButton`s (48dp Material3 default, unmodified) all clear the bar.
- Filter chips and the sort control are `defaultMinSize(minHeight = 44dp)` — 4dp under the commonly-cited 48dp guidance (`MainScreen.kt:997, 1031`). Low severity, easy fix, listed as a quick win.

### F.3 Font scaling
- Typography is `.sp`-based throughout (`Type.kt`), which is the foundational requirement and is met.
- **Confirmed clipping risk:** `fontSize = 10.sp, maxLines = 1, softWrap = false` on the Protein/Carbs/Fat field labels in both Manual Entry and Edit dialogs (D.4) — six call sites, `MainScreen.kt:715,724,733` and `MealDetailScreen.kt:405,414,423`. This is the one accessibility finding in this audit confirmed by direct code inspection alone (no runtime needed) to produce truncation at large font scale, because `softWrap = false` explicitly forbids the wrap that would otherwise save it.
- The widget's fixed `10.sp` title (`MandateWidget.kt:59`) is a much lower-severity version of the same pattern, in a much more space-constrained context (a home-screen widget) — not flagged as an action item, noted for completeness.

### F.4 Color and contrast
- All four themes share the same near-black background and a light `onSurface`/`onBackground` (`Theme.kt:19-22`), so base text contrast should hold across themes — **not independently contrast-measured against WCAG 2.2 AA ratios in this audit; recommend a device-based contrast check per theme before shipping**, specifically for `Color.Gray` text (used extensively for secondary copy — labels, timestamps, descriptions) against each theme's actual surface color, since `Color.Gray` is a fixed literal rather than a theme-derived token and its contrast will vary by which `surfaceColor` it happens to sit on.
- Macro-color collisions with theme primaries (C #7) are a comprehension issue more than a contrast one — both colors are legible, they're just no longer distinguishable *from each other* in two of four themes.
- Over/under-target signal is never color-only: the Today summary card, the Trends donut, and the weekly bar chart all pair color with an explicit printed number or delta (kcal over/remaining) every place they use red-vs-primary.

### F.5 Color independence
Covered above — the app consistently pairs color with text/numbers for status meaning; the one place color carries slightly more of the semantic load alone is the weekly bar chart's over-target red vs. on-target primary (still backed by the printed number, just no additional icon/pattern).

### F.6 Motion/visual effects
- The CRT scanline overlay (`terminalOverlay()`, `ModifierUtils.kt:65-89`) is a fixed, very low alpha (0.03) static gradient with **no animation** — a prior version's data-driven red "alarm wash" (proportional to how far over target the user was) was removed, and a prior GPU-wasting per-frame `drawRect` loop was replaced with one cached brush; both are documented fixes worth protecting. It is applied globally, with **no user-facing toggle to disable it**. Given how low the current alpha already is, this is not an urgent fix, but a "Reduce visual effects" preference in Settings would be a cheap, complete answer to the brief's accessibility question about CRT effects and is worth adding opportunistically rather than urgently.
- The camera screen's animated scan-line/pulse (D.2) is the one remaining continuous animation in the app; it's contained to a single full-screen moment and isn't informational. No action required unless a future reduced-motion setting is added, in which case this is the other thing it should gate.
- The typewriter-reveal effect on the Daily Briefing was removed specifically because a screen reader would re-announce it per character (`AnalyticsScreen.kt:398-402`, comment) — correct fix, don't reintroduce.

### F.7 Keyboard/focus
- `CalorieTargetControl` demonstrates the correct pattern (explicit `ImeAction.Done` + `KeyboardActions`, commit-on-blur) that the three meal-entry forms lack (D.4).
- No field-to-field `Next` chaining exists in any of the three meal-entry forms — the single most consistent, fixable accessibility/efficiency gap in the app.

### F.8 Charts (semantic state)
Covered in D.6/F.1 — both charts have a `contentDescription`, which is good and not universal in apps this size; the merge-semantics fix is the remaining gap.

### F.9 Semantic state
Switches, theme-selection cards, and filter chips all communicate selected/checked state through more than color (Switch has its own built-in state semantics; theme cards use border width + icon + text color; filter chips use fill + border + text color together) — no pure color-only selected-state indicator was found.

---

## G. Microcopy Audit

| Current wording | Location | Problem | Recommended wording/principle |
|---|---|---|---|
| `"MANUAL REFUELING"` (silent default when the name field is left blank) | `MainScreen.kt:771` | Thematic placeholder becomes the meal's permanent displayed *name* — reads as real data, not chrome, the first time it shows up in history | Require a name (disable Save until non-blank) instead of silently substituting one — same fix as the calorie default |
| `field_is_liquid`: "Liquid consumption (beverage or shake)" **and** `field_is_drink`: "Drink rather than food" | Manual/Edit dialogs vs. AI Review sheet, for the identical `isLiquid` field | Two different mental models for one checkbox, three screens apart | Standardize on `field_is_liquid`'s wording everywhere (it correctly resolves the "is a shake a drink?" ambiguity the other doesn't); delete `field_is_drink` |
| `"FLAGGED"` filter chip; red "FLAGGED" banner on a meal photo | `MainScreen.kt` (filter), `MealDetailScreen.kt:109-124` | Describes a condition (`isRestricted`) that is hardcoded `false` everywhere in the current app — the label promises a real classification that can't occur | Remove the filter and the banner, or repurpose both around a condition that can actually be true today |
| Yellow "Late-night meal" banner stamped on the food photo, black-on-yellow | `MealDetailScreen.kt:126-141` | Copy was correctly de-shamed in a prior pass (no scoring penalty, neutral language), but the caution-yellow visual treatment still reads as a warning regardless of what the text says | Keep the fact ("Late-night meal") but present it the way `MealEntryItem`'s geotag marker is presented elsewhere in the app — a small neutral-colored label, not a colored wash over the photo itself |
| `"On target. The State is pleased."` / `"Well off target."` / `"Far from target."` (status banner) | `strings.xml:53-56`, `StateStatusBanner` | Flavor with essentially no usability cost — it's a thin accent strip, not a gate, and the literal meaning ("on/close/off/far from target") is present in the same sentence | Fine as-is; explicitly **not** a finding — keep it |
| `"MANDATE_SHIFT"`, `"CONFIG"`, `"PRIVACY"`, `"SECURITY"` audit-log category labels | `ControlPanelScreen.kt:732-736` | Internal/diagnostic register in a screen a real user can open; not confusing (it's clearly a log), but worth noting as flavor-with-no-supporting-text for a feature few users will ever read closely | Low priority; leave unless user testing shows confusion — this is exactly the kind of label the brief says not to reflexively sanitize |
| "Analysis API key" / "hf\_…" hint | `strings.xml:195,198` | Correctly literal already — states what it enables, uses the provider's own token prefix as a hint | No change; cite as a working example of "literal primary label" |
| "Export structured JSON archives for full database restoration, or CSV dossiers for external spreadsheet analysis." | `strings.xml:234` | "Dossiers" is thematic flavor inside an otherwise plain, correctly-scoped sentence that already does the real job (explains JSON=restore vs CSV=spreadsheet) | Optional: swap "CSV dossiers" → "CSV files" for a fully plain read; low priority, the sentence already succeeds at its job either way |

---

## H. Quick Wins

**<2 hours each**
- Disable `ManualMealDialog`'s Save button until `foodName` is non-blank and `caloriesStr` parses to a positive number (mirrors the existing `enabled = draft.isNotBlank()` pattern already used on `ApiKeyCard`, `ControlPanelScreen.kt:646`).
- Remove `maxLines = 1, softWrap = false, fontSize = 10.sp` from the six macro-field labels in `ManualMealDialog`/`EditMealDialog` (`MainScreen.kt:715,724,733`; `MealDetailScreen.kt:405,414,423`); let them wrap or size like `AnalysisReviewSheet`'s equivalent field does.
- Swap the four hardcoded `Color(0xFF00E5FF)` sites in the Daily Briefing (`AnalyticsScreen.kt:387,393,427,431`) for `MaterialTheme.colorScheme.primary`.
- Swap the CSV export button's `Color.Gray`/`Color.White` (`ControlPanelScreen.kt:459-460`) for the same theme tokens its JSON siblings already use.
- Standardize the liquid/drink field label on one string (`field_is_liquid`) across all three forms; delete `field_is_drink`.
- Move the two literal strings in `AnalyticsScreen.kt:83,347` into `strings.xml`.
- Add a Cancel button to the daily-summary loading overlay (`AnalyticsScreen.kt:420-438`), matching `AnalysisLoadingOverlay`'s existing pattern (`MainScreen.kt:1143-1186`).
- Bump filter-chip/sort-control `minHeight` from 44dp to 48dp (`MainScreen.kt:997,1031`).
- Add a lightweight confirmation (or an undo snackbar) to the API key "Clear" button (`ControlPanelScreen.kt:652-664`).
- Replace the raw-millisecond export filenames with a human-readable date (`ControlPanelScreen.kt:419,437,455`).

**Half-day**
- Remove (or repurpose) the `FLAGGED` filter chip and the red photo banner tied to `isRestricted`, given it can no longer be `true` from normal use (`MainScreen.kt:67,170`; `MealDetailScreen.kt:99,109-124`).
- Restyle the "Late-night meal" banner as a neutral small label instead of a yellow photo overlay (`MealDetailScreen.kt:126-141`).
- Define one haptic policy (e.g., light "tick" for selection/navigation, medium for confirm/save, a distinct pattern reserved for destructive actions) and apply it consistently across `MainScreen.kt`, `ControlPanelScreen.kt`, `AnalyticsScreen.kt`, `MealDetailScreen.kt` — mostly a find-and-normalize pass, not new code.
- Add `imeAction = ImeAction.Next` + `FocusRequester` chaining across the three meal-entry forms' fields (Item name → Calories → Protein → Carbs → Fat → Done), matching the pattern already used correctly in `CalorieTargetControl`.
- Add `mergeDescendants = true` (or switch to `clearAndSetSemantics`) on both chart containers in `AnalyticsScreen.kt:462,622` and verify with TalkBack that the numbers aren't announced twice.
- Add a visible on-canvas legend line for the weekly chart's target line (a small "— target" key near the chart, matching what the donut chart already states in text).

**One day**
- Replace the cyclic sort `Surface` with a `DropdownMenu`/bottom-sheet listing all three options with the active one marked, so state is visible without tapping through it (`MainScreen.kt:994-1024`).
- Give the filter-chip row a scroll affordance (edge fade, or wrap to a second row, or move rarely-used filters like `FLAGGED`/`LIQUID` behind a "More filters" entry) so `LIQUID` isn't undiscoverable by default (`MainScreen.kt:987-1054`).
- Deep-link the widget's "Log a meal" button into the manual-entry dialog (or the capture flow) instead of the bare dashboard (`MandateWidget.kt:87-90`).
- Add lightweight section headers inside Settings (e.g., "Analysis," "Appearance," "Reminders & location," alongside the existing "Your data") so the screen's grouping is visible, not just implicit in card order (`ControlPanelScreen.kt`).

---

## I. Larger UX Improvements

### I.1 Rebuild `SurveillanceMap` on real relative geometry
**Rationale:** the current implementation cannot represent position correctly at any zoom level or screen size — it's not a precision problem, it's a wrong-formula problem (Top 10 #2, D.6/D.7).
**Affected screens:** Trends → Meal map.
**Scope:** Replace the `% 1f` fractional-coordinate placement with a proper local projection — pick a reference point (e.g., the centroid of the plotted meals, or the most recent one), convert each meal's lat/long delta from that reference into meters using an equirectangular approximation (cheap, accurate enough at city scale), and scale meters-to-pixels with a real, stated scale bar. This stays entirely within the existing tactical-radar visual language (crosshairs, grid, monochrome) — no map SDK or new dependency is required unless real-world street/basemap context becomes a stated goal later, which is a separate, larger decision.
**Expected benefit:** the one feature in the app that currently actively misinforms becomes trustworthy; directly serves the brief's "more trustworthy" objective.
**Migration risk:** low — it's a self-contained drawing function with no schema or data-model change; existing stored coordinates are already full-precision (the bug is purely in the chart, not the data).

### I.2 One shared meal-entry form component
**Rationale:** three independent implementations of the same five-field form (`ManualMealDialog`, `EditMealDialog`, `AnalysisReviewSheet`'s field block) have already drifted into three different blank-calorie behaviors and two different liquid-field labels (D.4, G). Every future fix (validation, IME chaining, error copy) currently has to be applied three times and can silently miss one.
**Affected screens:** Today (manual entry), Meal Detail (edit), Analysis Review Sheet.
**Scope:** Extract one composable taking the shared fields (name, calories, P/C/F, liquid) plus screen-specific slots (the AI photo + caveat banner only appears in the review sheet; the "existing value on blank" vs. "zero on blank" policy should become one shared, explicit decision rather than three implicit ones).
**Expected benefit:** the highest-frequency interaction in the app becomes consistent by construction, and D.4's whole finding cluster becomes a single fix instead of three.
**Migration risk:** low-medium — this is the one recommendation in the audit where the reason to abstract is the stated bar in the brief ("only when it prevents visible inconsistency") — it already has, twice, in the current three-copy state.
**Status:** Not done. The 2026-09-03 implementation pass fixed the specific drift this item warns about (all three forms now require the same explicit name+calories, and share one liquid-field label) by applying the same fix three times rather than extracting the shared component — a deliberate smaller-footprint choice for that pass, not an oversight. The underlying duplication (three copies of the field-and-focus-chain layout) is still there and this item's rationale still applies to future changes.

### I.3 A visible, non-cyclic sort control
**Rationale:** D.1/C #4 — state invisibility is the core problem, not the three sort options themselves.
**Affected screens:** Today.
**Scope:** Swap the single cycling `Surface` for a small `DropdownMenu` (or a 3-item bottom sheet, consistent with the app's rectangular/terminal aesthetic — a monospace list with a checkmark on the active row reads entirely in-theme) anchored to the same control.
**Expected benefit:** removes the "tap and read" loop for anyone who wants a sort order other than the current one; no change to the underlying three sort orders.
**Migration risk:** low — pure presentation change, `MealSortOrder` enum and its ordering logic are untouched.

---

## J. File-Level Implementation Map

1. **Manual/edit meal validation & IME chaining**
   Files: `ui/MainScreen.kt` (`ManualMealDialog`), `ui/MealDetailScreen.kt` (`EditMealDialog`), `ui/AnalysisReviewSheet.kt`, `ui/NutritionFormat.kt` (shared parse helpers), `res/values/strings.xml` (if a new inline-error string is added)

2. **Fix `SurveillanceMap` projection**
   Files: `ui/AnalyticsScreen.kt` (`SurveillanceMap`)

3. **Sort control as a menu, filter-row overflow**
   Files: `ui/MainScreen.kt` (`MealFilterChipRow`, `MealSortOrder`)

4. **Theme-color sweep (Daily Briefing, CSV button, audit log, weekly-chart grid/target line)**
   Files: `ui/AnalyticsScreen.kt`, `ui/ControlPanelScreen.kt`

5. **Macro color / theme-primary collision**
   Files: `ui/theme/Color.kt` (`TerminalTheme`, `NutritionColors`)

6. **Haptic policy normalization**
   Files: `ui/MainScreen.kt`, `ui/ControlPanelScreen.kt`, `ui/AnalyticsScreen.kt`, `ui/MealDetailScreen.kt`, `ui/CameraCaptureScreen.kt` (every `HapticFeedbackType.*` call site)

7. **Remove/repurpose `FLAGGED` + restyle "Late-night meal" banner**
   Files: `ui/MainScreen.kt` (`MealFilter.FLAGGED`), `ui/MealDetailScreen.kt` (photo overlay block), `res/values/strings.xml`

8. **Chart semantics merge fix**
   Files: `ui/AnalyticsScreen.kt` (`DailyComplianceChart`, `WeeklyBarChart`)

9. **Widget deep link**
   Files: `widget/MandateWidget.kt`

10. **Settings section headers**
    Files: `ui/ControlPanelScreen.kt`, `res/values/strings.xml`

---

## K. Issue-Ready Backlog

### P1

**1. Meal-entry forms can silently save invalid data (blank name/zero calories) with no validation, and clip macro labels at large font scale**
- *Problem:* `ManualMealDialog` defaults blank/unparseable calories to `0` and blank name to `"MANUAL REFUELING"` with the Save button always enabled; the same dialog's Protein/Carbs/Fat labels are `10.sp, maxLines=1, softWrap=false` and will clip at 1.3×+ system font scale.
- *User impact:* Wrong daily totals with zero warning (reproduced in `docs/screenshots/device_app.png`); a real accessibility failure in the app's highest-frequency form.
- *Proposed change:* Gate Save on non-blank name + a parseable positive calorie value; on blank/invalid, show an inline hint rather than silently substituting a value. Remove the label size/wrap constraints.
- *Relevant files:* `ui/MainScreen.kt`, `ui/MealDetailScreen.kt`, `ui/NutritionFormat.kt`
- *Acceptance criteria:* Save is disabled until name is non-blank and calories parses to a positive integer; at 2.0× system font scale, no macro field label clips or truncates.
- *Accessibility considerations:* Verify with TalkBack that the disabled-state reason is announced, not just visually implied.
- *Estimated effort:* S
- *Status:* ✅ Fixed, extended to all three entry forms (Manual/Edit/AI review) for consistency, not just Manual. TalkBack announcement of the disabled state — **requires runtime verification**.

**2. `SurveillanceMap` does not plot real geographic position**
- *Problem:* Pin placement uses only the fractional part of latitude/longitude (`% 1f`), discarding the whole degree.
- *User impact:* The map can visually misrepresent how close or far apart meals actually were, while presenting itself (crosshairs, grid, "Where you ate") as precise.
- *Proposed change:* Compute pin position from a real local projection relative to a reference point (see I.1), with a stated scale.
- *Relevant files:* `ui/AnalyticsScreen.kt`
- *Acceptance criteria:* Two meals with known, distinct real-world coordinates render at visually distinguishable, correctly-relative positions on the map; two meals at nearly the same coordinates render close together regardless of whether they cross a whole-degree boundary.
- *Accessibility considerations:* None beyond existing (map has no current TalkBack description — consider adding one describing relative distances once positions are real).
- *Estimated effort:* M
- *Status:* ✅ Fixed. Also added the TalkBack description this item flagged as a nice-to-have.

### P2

**3. Sort control and filter row are hard to operate**
- *Problem:* Sort cycles blind through 3 states on tap; 6 filter chips + sort share one unindicated horizontally-scrolling row.
- *User impact:* Users can't see or predict sort state without repeated tapping; `LIQUID`/`FLAGGED` filters are undiscoverable off-screen by default.
- *Proposed change:* Replace the cycling sort button with a menu/sheet listing all options; add a scroll affordance or wrap the filter row.
- *Relevant files:* `ui/MainScreen.kt`
- *Acceptance criteria:* All 3 sort options and all 6 filters are visible or discoverable without trial-and-error tapping; current sort/filter state is always legible without interaction.
- *Accessibility considerations:* Menu/sheet items must be individually focusable with the active item announced as selected.
- *Estimated effort:* S
- *Status:* ✅ Fixed (`DropdownMenu` + `FlowRow`). Individual-focus/selected-announcement of menu items — **requires runtime verification**.

**4. Hardcoded colors break the 4-theme promise in specific, named spots**
- *Problem:* Daily Briefing (+ its spinner), the CSV export button, the audit-log category colors, and the weekly chart's grid/target line all use fixed `Color.*` literals instead of theme tokens.
- *User impact:* Visually inconsistent app in 3 of the 4 advertised themes, in exactly the features meant to showcase the theme system.
- *Proposed change:* Replace each literal with the equivalent `MaterialTheme.colorScheme.*` token.
- *Relevant files:* `ui/AnalyticsScreen.kt`, `ui/ControlPanelScreen.kt`
- *Acceptance criteria:* Switching theme visibly re-colors every element named above, in all 4 themes.
- *Accessibility considerations:* Re-verify contrast of the new theme-derived colors against each theme's background.
- *Estimated effort:* XS
- *Status:* ✅ Fixed. Contrast re-check across all 4 themes — **requires runtime verification**.

**5. Macro colors collide with theme primary in 2 of 4 themes**
- *Problem:* `NutritionColors.Carbs` == `CYBER_CYAN.primaryColor`; `NutritionColors.Protein` == `MATRIX_GREEN.primaryColor`, exactly.
- *User impact:* In the app's default theme, "this is colored because it's carbs" and "this is colored because it's a UI accent" are visually indistinguishable.
- *Proposed change:* Either shift the macro palette slightly off each theme's primary hue, or add a non-color differentiator (a fixed macro icon/glyph) so identification doesn't depend on hue alone in any theme.
- *Relevant files:* `ui/theme/Color.kt`
- *Acceptance criteria:* In every theme, each macro's color is visually distinguishable from that theme's primary accent color at a glance.
- *Accessibility considerations:* Re-run a color-blindness simulation across the revised palette per theme.
- *Estimated effort:* S
- *Status:* ✅ Fixed via the palette-shift approach (Protein → deeper emerald, Carbs → cool blue, Fat → warm orange-gold). Color-blindness simulation — **requires runtime/tooling verification**.

**6. Inconsistent haptic feedback semantics**
- *Problem:* `LongPress`-type haptics fire on ordinary single taps almost everywhere; `ContextClick` fires on visually identical taps elsewhere, including two adjacent Settings switches using different types for the same gesture.
- *User impact:* Minor per-tap, but touches nearly every interaction in the core logging loop; inconsistency is more noticeable the more an app is used daily.
- *Proposed change:* Define one small haptic policy (selection/navigation vs. confirm/save vs. destructive-warning vs. none) and apply it uniformly.
- *Relevant files:* `ui/MainScreen.kt`, `ui/ControlPanelScreen.kt`, `ui/AnalyticsScreen.kt`, `ui/MealDetailScreen.kt`, `ui/CameraCaptureScreen.kt`
- *Acceptance criteria:* Every haptic call site maps to one of a small, named set of interaction categories, with no two visually-identical gestures using different types.
- *Estimated effort:* XS
- *Status:* ✅ Fixed across all ~35 call sites in `MainScreen.kt`, `ControlPanelScreen.kt`, `AnalyticsScreen.kt`, `MealDetailScreen.kt`, `CameraCaptureScreen.kt`. Also found and fixed 3 destructive confirm buttons (delete, restore, erase) that had no haptic at all. The policy is documented inline at each call site rather than as a shared helper — a small follow-up worth doing if a new destructive action is ever added.

**7. `FLAGGED` filter and its red photo banner are dead UI**
- *Problem:* `isRestricted` is hardcoded `false` everywhere a meal is constructed; the filter/banner tied to it can't fire from normal use.
- *User impact:* Wasted chip slot, a visual promise ("FLAGGED") that never resolves to anything a normal user can trigger, and a moment of confusion for anyone who taps it.
- *Proposed change:* Remove the filter/banner, or repurpose them around a condition that can actually occur.
- *Relevant files:* `ui/MainScreen.kt`, `ui/MealDetailScreen.kt`, `res/values/strings.xml`
- *Acceptance criteria:* No filter or visual state in the shipped app describes a condition that cannot occur from any current data-entry path.
- *Estimated effort:* XS
- *Status:* ◐ Partial. Kept the filter/field (a restored backup can still legitimately carry `isRestricted = true`, so it isn't strictly unreachable) but replaced the full-photo red/yellow warning washes for both `isRestricted` and `isNightRefueling` with small corner badges — the actively punitive-looking part of this finding.

**8. Daily-summary loading has no Cancel; two charts risk duplicate TalkBack announcements**
- *Problem:* `AnalyticsScreen`'s loading overlay lacks the Cancel button `MainScreen`'s equivalent has; `DailyComplianceChart`/`WeeklyBarChart` use non-merging semantics.
- *User impact:* Inconsistent recovery from a stuck network call; likely redundant/confusing chart readouts for TalkBack users.
- *Proposed change:* Add Cancel to the summary-loading overlay; add `mergeDescendants = true`/`clearAndSetSemantics` to both charts.
- *Relevant files:* `ui/AnalyticsScreen.kt`
- *Acceptance criteria:* Summary generation can be cancelled the same way photo analysis can; TalkBack announces each chart's description exactly once per interaction, verified on-device.
- *Estimated effort:* S
- *Status:* ✅ Fixed (Cancel button + `cancelDailyBriefing()`; both charts switched to `clearAndSetSemantics`). Single-announcement claim — **requires runtime verification with TalkBack**.

### P3

**9. Field-label terminology and small-scope inconsistencies**
- *Problem:* `field_is_liquid` vs. `field_is_drink` for one field; two different delete-confirmation bodies for the same action; raw-millisecond export filenames; "Late-night meal" still visually reads as a warning despite neutral copy.
- *User impact:* Small comprehension/polish friction, not blocking.
- *Proposed change:* Standardize the liquid-field wording; align the two delete-confirmation bodies; use a human-readable export filename date; restyle the late-night banner as a neutral label.
- *Relevant files:* `ui/MainScreen.kt`, `ui/MealDetailScreen.kt`, `ui/ControlPanelScreen.kt`, `res/values/strings.xml`
- *Acceptance criteria:* One label per field concept; consistent destructive-confirmation copy for the same action regardless of entry point.
- *Estimated effort:* S (bundle)
- *Status:* ✅ All four fixed.

**10. Settings screen has no visible section grouping; widget doesn't deep-link its own CTA; API-key Clear has no confirmation**
- *Problem:* See D.8, D.15.
- *Proposed change:* Add lightweight section headers to Settings; deep-link the widget's "Log a meal" button into manual entry; add a confirmation or undo to API-key Clear.
- *Relevant files:* `ui/ControlPanelScreen.kt`, `widget/MandateWidget.kt`
- *Acceptance criteria:* Settings sections are visually distinguishable; tapping the widget's CTA opens directly into a logging action; clearing the API key requires a deliberate second step or offers undo.
- *Estimated effort:* S (bundle)
- *Status:* ✅ All three fixed. The widget deep-link required more than the "S" estimate suggested — `MainActivity` needed `launchMode="singleTop"` plus an `onNewIntent` override so a widget tap while the app is already running updates the same Activity instance instead of stacking a second one, and the "open manual entry" flag needed a proper consume-once pattern (a first attempt re-opened the dialog on every return to the Today tab — caught by self code-review, fixed before landing). API-key Clear got a tap-to-arm/tap-to-confirm pattern rather than a full modal, given the key is recoverable by re-entering it.

---

## L. Recommended Implementation Order

**Phase 1 — Correctness & accessibility (no design decisions required)**
P1 backlog items (#1, #2). These are bugs with UX consequences, not design debates — fix first because everything downstream (trust, comprehension) assumes the data and the map are accurate.

**Phase 2 — Core logging friction**
Meal-entry form consolidation (I.2) and its validation/IME fixes, once Phase 1's per-form patches exist — do the one-component extraction *after* the immediate validation fix ships, so the fix isn't blocked on a refactor. Sort-control and filter-row fixes (#3).

**Phase 3 — Visual/theme coherence**
Hardcoded-color sweep (#4) and macro/theme-primary collision (#5) — independent of Phase 1/2, can run in parallel with either.

**Phase 4 — Consistency polish**
Haptics (#6), dead FLAGGED UI (#7), loading/semantics fixes (#8) — low-risk, mechanical, good candidates for a single cleanup pass.

**Phase 5 — Settings/administrative UX**
Section headers, API-key Clear confirmation, widget deep link, export filenames (#9, #10) — lowest frequency of use, lowest urgency.

**Phase 6 — Polish**
Microcopy alignment (liquid/drink label, delete-confirmation parity, late-night banner restyle) not already covered above; optional "reduce visual effects" preference (F.6) if pursued.

**Dependencies:** Phase 2's component consolidation should follow Phase 1's validation fix (fix the bug once, in place, then extract — don't extract first and fix three times mid-refactor). Everything else is independently orderable.

---

## M. "Do Not Change" List

These are working, some of them are genuinely good design, and a future pass should not re-open them without new evidence:

- **The Analysis Review Sheet's non-dismissible modal behavior** (`AnalysisReviewSheet.kt:64`) — an unconfirmed AI result is not casually discardable by a stray tap or back press, and the reasoning is sound.
- **The single-caveat-at-a-time policy on AI estimates** (`PendingAnalysis.kt:38-57`) — stacking three warnings on one estimate teaches users to ignore warnings; showing the most relevant one doesn't.
- **The network-boundary disclosure lines placed at the decision point** (capture buttons, location toggle, daily-summary button) rather than only in a settings/policy page.
- **The two-step, Cancel-first, plain-vs-colored destructive-confirmation pattern** used for Erase-everything and both meal-delete flows.
- **The "chrome vs. data" copy split** as a stated principle (`strings.xml:11-17`) — the thematic voice belongs in status lines, screen titles, and button verbs; plain language belongs in anything the user needs to act on correctly. The fixes in this audit apply this existing rule to the few places it wasn't yet applied — they are not a new rule.
- **The deliberate two-typeface system** (monospace for instrumentation, system sans for prose) — a genuinely considered piece of identity work, not decoration.
- **The rectangular, borderless-card, corner-bracket (`hudFraming`) visual language** — reserved for containers that actually carry data, not applied indiscriminately as decoration. Keep that discipline as new screens are added.
- **The low-alpha, static (non-data-driven) CRT scanline overlay** — a prior, more aggressive, target-deviation-tinted version was correctly removed; the current version is subtle enough not to need urgent gating, only an optional toggle if pursued.
- **The contextual, re-checked-on-resume notification-permission flow for Reminders** — a model other apps should copy, not the reverse.
- **The plain, hostname/status-code-free error copy taxonomy** (`AnalysisError.kt`, `strings.xml` Errors section, covered by `ErrorCopyTest`) — don't let thematic language creep back into failure states.
- **The compliance-status-as-label-not-gate architecture** (`MainViewModel.kt:77-90`) — nothing in the app should ever again be reachable-or-not based on how someone is eating.

---

## N. Optional Compose Examples

Five sketches for the five highest-value findings — architecture/interaction direction only, not production patches.

**1. Gate Save on valid input, without silently substituting a value**
```kotlin
val caloriesValue = caloriesStr.toIntOrNull()
val isValid = foodName.isNotBlank() && caloriesValue != null && caloriesValue > 0

Button(
    onClick = { onSave(foodName, caloriesValue!!, /* ... */) },
    enabled = isValid,
    /* ... */
) { Text(stringResource(R.string.manual_entry_save)) }

if (caloriesStr.isNotBlank() && caloriesValue == null) {
    Text(
        stringResource(R.string.error_calories_unreadable), // new, plain string
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}
```

**2. Sort as a visible menu instead of a cycling tap target**
```kotlin
var sortMenuOpen by remember { mutableStateOf(false) }

Box {
    Surface(modifier = Modifier.clickable { sortMenuOpen = true } /* ... */) {
        Text(stringResource(sortOrder.labelRes))
    }
    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
        MealSortOrder.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes)) },
                leadingIcon = { if (option == sortOrder) Icon(Icons.Default.Check, null) },
                onClick = { onSelectSort(option); sortMenuOpen = false }
            )
        }
    }
}
```

**3. Local-projection map placement (concept, not the final math)**
```kotlin
// Reference point: centroid of the plotted meals, computed once per data set.
val refLat = geotaggedMeals.map { it.latitude!! }.average()
val refLon = geotaggedMeals.map { it.longitude!! }.average()
val metersPerDegLat = 111_320.0
fun metersPerDegLon(lat: Double) = 111_320.0 * cos(Math.toRadians(lat))

fun project(lat: Double, lon: Double): Offset {
    val dxMeters = (lon - refLon) * metersPerDegLon(refLat)
    val dyMeters = (lat - refLat) * metersPerDegLat
    // pxPerMeter chosen so the widest spread of points fits the canvas, with a
    // stated scale bar drawn from the same constant.
    return Offset(center.x + (dxMeters * pxPerMeter).toFloat(),
                   center.y - (dyMeters * pxPerMeter).toFloat())
}
```

**4. Theme-derived color instead of a literal**
```kotlin
// Before (AnalyticsScreen.kt:387,393,427,431)
Column(modifier = Modifier.hudFraming(Color(0xFF00E5FF), /* ... */)) { /* ... */ }

// After
Column(modifier = Modifier.hudFraming(MaterialTheme.colorScheme.primary, /* ... */)) { /* ... */ }
```

**5. Macro field label that survives large font scale**
```kotlin
// Before: fontSize = 10.sp, maxLines = 1, softWrap = false
OutlinedTextField(
    label = { Text(stringResource(R.string.field_protein)) }, // no size/wrap override
    // let the field's own compact width + default label styling handle wrapping;
    // if truncation must be prevented at all costs, use a shared short-form
    // string resource ("Prot.") rather than forcing a fixed small size.
)
```

---

## O. Final Judgment

**Core-task usability — 7/10.** The photo → review → confirm loop and the manual-entry loop both work, are honestly disclosed, and are hard to lose data in accidentally. The score isn't higher because the two manual-entry forms can currently produce silently-wrong data (0 kcal) — a core-task correctness failure, not a polish one — and because sort/filter discovery adds friction to the second most common task (finding an older meal).

**Accessibility — 6/10.** Real, considered work exists (`.sp`-based typography throughout, hand-written macro descriptions, contextual permission flows, deliberate `contentDescription = null` where merging would double-announce) — this is above the bar for an app this size. It's not higher because of one confirmed, easily-reproduced clipping bug in the highest-frequency form, and two charts whose semantics pattern likely produces duplicate TalkBack output.

**Information architecture — 7/10.** Three top-level destinations map cleanly to the app's actual jobs, and the compliance-status architecture correctly demoted itself to "label, never gate" everywhere. The gap is entirely inside Settings, where task-frequency ordering and grouping aren't yet visible to the user, and inside Today's filter row, where six options and one sort control compete for the same unindicated horizontal-scroll space.

**Visual identity vs. readability balance — 7/10.** The team already found the right dividing line (chrome carries character, data stays literal) and applied it almost everywhere — this is the least "generic Material calorie tracker" health app plausible while still being genuinely usable, and that balance is worth protecting. It loses points specifically where the visual language hasn't caught up with an already-fixed copy change (the yellow late-night banner still looks like a warning after the text stopped being one) and where the theme system doesn't quite reach every screen (four hardcoded-color spots, two exact macro/primary collisions).

---

### If only five changes could ship before the next release

1. **Fix silent zero-calorie/blank-name saves in the manual-entry forms** (#1) — this is actively producing wrong data today, in the app's single most common action, with a screenshot to prove it.
2. **Fix `SurveillanceMap`'s projection math** (#2) — it's the one place the app currently tells its user something false, in a feature whose entire pitch is trustworthy handling of sensitive location data.
3. **Fix the macro-label clipping at large font scale** (#3 in the P1 list) — smallest possible fix, highest-frequency form, direct accessibility failure.
4. **Make sort visible and fix filter-row overflow** (#3 in the Top 10) — the single biggest daily-use friction point that isn't a correctness bug.
5. **Sweep the four hardcoded-color spots and the two macro/theme-primary collisions** (#4/#5 combined) — cheapest fix on this list per unit of "does the app look and read the way it promises to," across all four advertised themes.

Together these are two correctness bugs, one accessibility bug, one frequent-friction fix, and one coherence sweep — no redesign, no new screens, nothing that touches the tactical identity. That's deliberate: the identity is working. What's left is finishing the job the last few audit passes already started.
