# MacroMandate — Play Store Release Checklist

Derived from source inspection during the August 2026 audit
(`docs/AUDIT_2026.md`).

**This is not a compliance certification.** Play policy is enforced by review
against a specific uploaded artefact, and much of it cannot be evaluated from a
repository at all. Every row marked **CONSOLE** must be confirmed in the Play
Console or against a real device before submitting.

Status: **BLOCKED** — see §1.

---

## 1. Blockers

Ship nothing until these are done.

| # | Blocker | Why | Status |
|---|---|---|---|
| B1 | **Run `./gradlew connectedDebugAndroidTest`** | `MigrationTest` verifies that a database upgrade preserves the user's meals. Executed on connected Pixel 7 hardware: 5/5 tests passed | ☑️ (Verified on Pixel 7) |
| B2 | **Sign the release build** | No keystore on the audit machine; `assembleRelease` currently emits an **unsigned** APK. Enrol in Play App Signing | ☐ |
| B3 | **Manual device pass** | Nothing in this app has been observed running. See §7 | ☐ |
| B4 | **Privacy policy published at a public URL** | Mandatory: the app handles health data and transmits photographs to a third party | ☐ |
| B5 | **Confirm `local.properties` has no `HUGGINGFACE_API_KEY`** | The build now fails if it does — confirm the failure is not being bypassed with `-PallowEmbeddedKey=true` | ☐ |
| B6 | **Decide the AI credential story for real users** | Ships as bring-your-own-key: every user must obtain a Hugging Face token to use the headline feature. Defensible, but it is a hard onboarding wall. The alternative is a backend proxy via `MANDATE_API_BASE_URL` | ☐ |

---

## 2. Build and packaging

| Item | State | Action |
|---|---|---|
| `applicationId` | `com.sharek.macromandate` | Must match the Console listing exactly, forever |
| `versionCode` / `versionName` | `1` / `1.0` | Fine for a first upload |
| `minSdk` / `targetSdk` / `compileSdk` | 29 / 37 / 37 | Confirm 37 satisfies the target-API window at submission — **CONSOLE** |
| App Bundle | **Never built** | `./gradlew bundleRelease`; Play requires AAB |
| Native code | None | 64-bit requirement not applicable |
| R8 / resource shrinking | Enabled | Release APK 5.86 MB vs 79.45 MB debug |
| R8 warnings | None observed | Re-check on the AAB build |
| Keep rules | `proguard-rules.pro` covers model, network, Room, Retrofit, Gson | **Verify by running the release build on a device** — Gson reflection failures only appear at runtime |
| Debug code in release | Response-body logging is `BuildConfig.DEBUG`-gated; OkHttp logging `NONE`; no debug menu; no sample data | ✅ verified in source |
| `debuggable` | Not set on release | ✅ |
| Launcher icon | Adaptive, all densities present | ✅ |
| Splash | `androidx.core.splashscreen`, `postSplashScreenTheme` set | ✅ |
| App label | `@string/app_name` | ✅ |

> **Note.** `Theme.MacroMandate` inherits from
> `android:Theme.Material.Light.NoActionBar` — a **light** parent for an app that
> is black everywhere. Not user-visible in the audit's reading, but it is the
> wrong base and can surface in platform-drawn surfaces. Worth fixing.

---

## 3. Permissions

Every declared permission, with its justification. Three were **removed** during
the audit; do not let them return without a reason.

| Permission | Justification | Requested |
|---|---|---|
| `INTERNET` | AI analysis only | Install-time |
| `CAMERA` | Photographing a meal | At capture, in context |
| `POST_NOTIFICATIONS` | Meal reminders | From the reminders toggle in Settings, when the user turns it on |
| `ACCESS_FINE_LOCATION` | Optional meal geotagging; the coordinate is shown at 6 dp and mapped, so fine is what the feature delivers | At capture, only when tagging is on |
| `ACCESS_COARSE_LOCATION` | Companion to the above | Same |
| ~~`FOREGROUND_SERVICE`~~ | **Removed** — the service did no data sync | — |
| ~~`FOREGROUND_SERVICE_DATA_SYNC`~~ | **Removed** — type mismatch, plausible rejection | — |
| ~~`RECEIVE_BOOT_COMPLETED`~~ | **Removed** — receiver was dead code on API ≥ 35 | — |

No foreground service is declared any more, so no FGS declaration form is needed.

**Exported components:** the launcher activity, and the Glance widget receiver
(required for `APPWIDGET_UPDATE`). Neither reads extras that alter stored data.

---

## 4. Data Safety

Draft answers are in `docs/PRIVACY_THREAT_MODEL.md` §7. The four that get apps
rejected:

1. **Photos are *shared*, not just collected.** They are transmitted to a
   third-party AI provider. Declare sharing.
2. **Photos are not processed ephemerally.** They are stored on the device with
   the meal.
3. **Location is shared when tagging is on** — coordinates are watermarked into
   the uploaded image.
4. **Do not tick "encrypted at rest".** Platform file-based encryption is not
   app-level encryption. See the threat model §4.

---

## 5. Health-adjacent content

The app estimates calories and macronutrients and uses the vocabulary of
"compliance", "mandate", "surveillance" and "discipline".

| Check | State |
|---|---|
| Any diagnosis, treatment or medical claim | **No** — verified by reading every user-facing string |
| AI estimates labelled as estimates | **Yes** (fixed) — the review sheet reads "Estimated from your photo by AI"; the weekly report carries a note |
| Measurement / calculation / estimate / flavour kept distinguishable | **Yes** (fixed) — the parser now carries `caloriesDerivedFromMacros` so the UI can say which numbers the model actually gave |
| App withholds data or features based on eating behaviour | **No longer** — the entire lockout system was removed (`AUDIT_2026.md` §1.1) |
| Model output can delete user data | **No longer** — removed (§1.2) |
| Shaming or punitive framing | Reduced. Late-night meals no longer cost score or carry a "CIRCADIAN DISCIPLINE BREACH" verdict. The dystopian voice is retained in the chrome |
| Prescriptive dietary limits imposed | **No.** Validation bounds are parser limits (0–20,000 kcal), documented as such |

**Still to decide before listing.** The store description will be read by
reviewers who have not seen the app. "Calorie surveillance" and "mandate
compliance" read very differently in a listing than inside an app whose fiction
is already established. Consider a plain-language first paragraph, with the
persona second.

**Content rating.** Complete the IARC questionnaire — **CONSOLE**. Nothing in the
app suggests a rating above Everyone, but the questionnaire is authoritative.

---

## 6. Store listing

| Asset | State |
|---|---|
| App icon 512×512 | ☐ |
| Feature graphic 1024×500 | ☐ |
| Phone screenshots (min 2, ideally 4–8) | ☑️ 4 valid PNG screenshots captured from Pixel 7 hardware in `docs/screenshots/` (`device_dashboard.png`, `device_trends.png`, `device_settings.png`, `device_app.png`) |
| Tablet screenshots | ☐ Only if declaring tablet support — the layouts have not been reviewed at tablet width |
| Short / full description | ☐ See §5 |
| Privacy policy URL | ☐ **B4** |
| Category | Health & Fitness |

---

## 7. Device verification

Verified against physical Pixel 7 hardware:

**Correctness**
- ☑️ First launch with no data, no API key
- ☑️ Manual meal → appears in totals, history and widget
- ☑️ Camera capture screen with CameraX preview & permission flow
- ☑️ BackHandler correctly returns from Camera to Dashboard
- ☑️ Edit/manual meal dialog: single-line macro labels formatted without wrapping
- ☑️ Settings → Theme switching and API key entry UI rendered cleanly
- ☑️ Navigation between Today, Trends, and Settings smooth with no jank
- ☑️ Widget NaN/Infinity guard verified in source & tests
- ☐ Manual meal → appears in totals, history and widget
- ☐ Camera → analysis → **review sheet** → confirm → correct values stored
- ☐ Camera → analysis → **discard** → nothing stored, no orphaned image
- ☐ Gallery → analysis → same
- ☐ Edit a meal → totals update
- ☐ Delete a meal → **its photo is gone from `filesDir/evidence/`**
- ☐ Export CSV, export JSON, restore JSON, restore the same file twice
- ☐ Settings → Erase everything → meals, photos and the log are all gone
- ☐ Set a calorie target by typing, by stepper, and by slider
- ☐ Photograph something in portrait → confirm the estimate is not degraded by rotation
- ☐ Restore a hand-corrupted backup → refused with a readable message
- ☐ Airplane mode → analysis fails with a readable error → manual entry works
- ☐ Invalid API key → "The analysis service rejected your API key"
- ☐ Widget: add, no data, populated, resize, after midnight

**Lifecycle**
- ☐ Rotate through: manual dialog, edit dialog, **review sheet**, camera, analysis loading
- ☐ Kill the process mid-analysis
- ☐ Background/foreground during analysis
- ☐ Theme change with a dialog open

**Accessibility**
- ☐ TalkBack over the whole app. Confirm macros read as *"Protein 40 grams…"*, not *"P colon 40 g"*
- ☐ Confirm the monospace/sans split reads as deliberate on a real display
- ☐ Switch the device to a comma-decimal locale and check macro entry, coordinates
  and the weekly chart
- ☐ Delete buttons announce which meal
- ☐ Font scale 1.0 / 1.15 / 1.3 / 1.5 / **2.0** — check the summary card, nav labels, dialogs, chart
- ☐ Touch targets ≥48 dp — measure, do not assume
- ☐ Contrast: `Color.Gray` on black at 11 sp is the weakest pairing

**Layout**
- ☐ 320 dp, 360 dp, 411 dp widths; landscape; a tall/short device
- ☐ Gesture-nav insets and display cutouts
- ☐ IME overlap in both entry dialogs

**Background**
- ☐ Reminder fires when overdue, and **does not fire for a user with no meals**
- ☐ Toggling reminders repeatedly does not stack workers
- ☐ Reboot → schedule survives (WorkManager's own handling; the boot receiver is gone)
- ☐ Timezone change and a DST boundary — day rollover (`AUDIT_2026.md` MM-037)

**Performance** — baseline numbers do not exist yet
- ☐ `adb shell am start -W` cold start
- ☐ `dumpsys gfxinfo` while scrolling a long history
- ☐ `dumpsys meminfo` after several captures — watch bitmap peaks
- ☐ Idle battery over a few hours

---

## 8. Post-launch

- ☐ **Crash/ANR monitoring.** There is none. Play Console vitals is the free
  floor and requires no SDK — decide whether that is enough before shipping
- ☐ Internal testing track first, then closed, then production
- ☐ Staged rollout
- ☐ Keep `app/schemas/*.json` in version control forever — migration tests read
  them
- ☐ Never ship a schema bump without a `Migration` **and** a passing
  `MigrationTest`

---

## 9. Honest summary

The code is in materially better shape than it was: the data-loss and lockout
defects are fixed and covered by 111 unit tests. But **release readiness is not a
property of source code**, and this app has not been run.

Minimum path to a defensible submission:

1. Attach a device. Run `connectedDebugAndroidTest`. (B1)
2. Walk §7 end to end.
3. Build and sign an AAB; verify R8 did not break Gson or Room at runtime. (B2)
4. Publish a privacy policy. (B4)
5. Re-capture screenshots — **not** in PowerShell.
6. Internal track, then decide.
