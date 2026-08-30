# MacroMandate 👁️📟

> **Sovereign Dietary Intelligence & Calorie Surveillance System**

**MacroMandate** is a local-first Android nutrition tracker with a retro tactical
terminal aesthetic. Photograph a meal and a multimodal model estimates its
calories and macros; log by hand when you'd rather; keep every record on your own
device.

The persona is deliberate. The numbers are not part of the persona:

> **Character in the chrome. Clarity in the data.**

The interface is dramatic. What it tells you about your food is plain, hedged
where it should be hedged, and always yours to correct.

---

## ⚡ What it does

- 📸 **Photo analysis** — capture or pick an image; a vision model estimates
  calories, protein, carbohydrates and fat. **Every result is shown for review
  and correction before anything is written to your log.**
- ✍️ **Manual entry** — a complete alternative path. Works offline, with no API
  key, and when the model gets it wrong.
- ✏️ **In-place correction** — edit any record's name, calories, macros and
  liquid status; totals recompute immediately.
- 📊 **Today** — calories against target, remaining delta, macro breakdown.
- 📈 **Trends** — seven-day intake against your target, macro distribution, and a
  weekly summary you can copy or export.
- 🗺️ **Optional geotagging** — off by default. When on, coordinates are saved
  with the meal *and rendered into the image that gets uploaded*; the setting
  says so.
- 🎨 **Four terminal themes** — Cyber Cyan, Phosphor Green, Amber CRT, Stark Mono.
- 💾 **Your data, portable** — JSON backup and restore, CSV export for
  spreadsheets, and a one-tap erase of everything. No account, no sync, no
  lock-in.
- 🔔 **Reminders** — an optional nudge when nothing has been logged for a while.
- 📟 **Home-screen widget** — today's total at a glance.

### What it deliberately does not do

- It does not lock you out of your own records, whatever your intake looks like.
- It does not let a language model decide to delete your history.
- It does not present an AI estimate as a measurement.
- It does not diagnose, treat, or advise. It is not medical software.

> An earlier version did the first two of those. See
> [`docs/AUDIT_2026.md`](docs/AUDIT_2026.md) §1.1–§1.2 for what happened and why
> it was removed.

---

## 🔒 Privacy

Everything lives on your device. There is no server operated by this project, no
account, no analytics, and no crash reporter.

**Two things leave the device, both only when you ask for them,** both to the AI
provider you configure and authenticated with your own key:

- a **meal photograph**, when you use photo analysis. The image can incidentally
  contain faces, your home, or documents on the table, so the app says so at the
  point of capture rather than burying it in Settings.
- **today's meal names and totals as text**, if you tap *Trends → Daily summary*.

Nothing else — not your history, not your preferences, not the activity log.

The API key is stored in app-private storage. **It is not encrypted** — the
protection is the Android sandbox, and this README will not claim otherwise. It
is never written to logs, exports, or backups.

Full data inventory, network boundary and threat model:
[`docs/PRIVACY_THREAT_MODEL.md`](docs/PRIVACY_THREAT_MODEL.md).

---

## 🛠️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    JETPACK COMPOSE UI                       │
│  MainScreen │ AnalyticsScreen │ ControlPanelScreen │ Detail │
├─────────────────────────────────────────────────────────────┤
│                      VIEWMODEL LAYER                        │
│           MainViewModel · StateFlow · UiState               │
├─────────────────────────────────────────────────────────────┤
│                      REPOSITORY LAYER                       │
│    MealRepository │ AuditRepository │ MandatePreferences    │
├─────────────────────────────────────────────────────────────┤
│                      LOCAL DATA LAYER                       │
│  Room (MealEntity, AuditEntity) │ DataStore Preferences     │
│           ── plaintext, sandbox-protected ──                │
├─────────────────────────────────────────────────────────────┤
│                      NETWORK & SYSTEM                       │
│  Retrofit → chat completions │ CameraX │ WorkManager        │
│  NutritionBounds ── one validation gate for every writer    │
└─────────────────────────────────────────────────────────────┘
```

- **UI**: Jetpack Compose + Material 3, with custom HUD framing and a CRT
  scanline overlay drawn in Compose (`drawWithCache`; no shaders).
- **Architecture**: MVVM + repository, coroutines and `StateFlow`.
  `MainViewModel` currently serves every screen — see the audit's follow-ups.
- **Persistence**: Room with exported schemas and explicit migrations.
  Destructive fallback is **debug-only**.
- **Networking**: Retrofit 2 + OkHttp, OpenAI-compatible chat completions, with
  explicit timeouts and no automatic retries.
- **Validation**: every path that writes nutrition — model output, manual entry,
  the edit dialog, JSON restore — goes through `NutritionBounds`, so no route
  bypasses another's checks.
- **Background**: a single WorkManager periodic worker. No foreground service.
- **Localization**: all user-visible copy is in `strings.xml`; errors are carried
  as `@StringRes` ids from the domain layer and resolved at display time.
- **Widget**: Jetpack Glance.

---

## 🚀 Getting started

### Prerequisites

- Android Studio Ladybug or newer
- Android SDK 37 (`minSdk` 29)
- JDK 17+

### Build

```bash
git clone https://github.com/shareef01/MacroMandate.git
cd MacroMandate

./gradlew test            # unit tests
./gradlew assembleDebug   # debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Configuration (`local.properties`)

```properties
# Optional: pre-fill a token for local development ONLY.
# The release build will FAIL if this is set — a BuildConfig string constant
# is recoverable from any installed APK in seconds. Users supply their own
# key in Settings instead.
HUGGINGFACE_API_KEY=hf_your_token_here

# Optional: point at a backend proxy that holds the credential.
MANDATE_API_BASE_URL=https://router.huggingface.co/
MANDATE_MODEL_ID=google/gemma-4-31B-it
```

To use photo analysis, open **Settings → Analysis API key** and paste a Hugging
Face access token. The app is fully usable without one.

---

## 🧪 Tests

```bash
./gradlew test                        # 129 unit tests
./gradlew connectedDebugAndroidTest   # requires a device — includes MigrationTest
```

| Suite | Covers |
|---|---|
| `NutritionBoundsTest` | The shared validation gate: clamping, NaN/Infinity, Atwater fallback, macro/calorie contradiction |
| `HostileAnalysisResponseTest` | Model responses treated as hostile input — prose, markdown fences, string numbers, nulls, arrays, absurd values, truncated JSON, HTML error pages |
| `BackupRestoreHostileInputTest` | Restore as a hostile boundary — future versions, oversized files, impossible values, bad timestamps, foreign image URIs, duplicate ids |
| `NutritionFormatTest` | Rounding, screen-reader descriptions, comma decimal separators, numeric overflow |
| `AnalysisErrorTest` | HTTP status and transport exceptions → readable domain errors |
| `ErrorCopyTest` | The copy rules, asserted against `strings.xml` itself: no URLs, status codes or stack frames in user-facing text |
| `ImageForensicsTest` | Downsample sizing across 12 MP, 50 MP, panorama and degenerate frames |
| `NutritionSanitizerTest` | Model-response parsing |
| `DossierExporterTest` | CSV escaping, formula-injection mitigation, JSON round trip |
| `DossierReportGeneratorTest` | Weekly summary arithmetic |
| `ComplianceEngineTest` | Target-closeness scoring |
| `MigrationTest` *(instrumented)* | Database upgrades preserve meals and audit rows |

---

## 📋 Status

**Not production-ready.** The correctness, privacy and data-loss defects found in
the August 2026 audit are fixed and covered by tests, but **the application has
not been observed running** — the audit had no device available, so every UI,
accessibility, performance and background-execution finding rests on source
reading.

Before any release, see [`docs/PLAY_RELEASE_CHECKLIST.md`](docs/PLAY_RELEASE_CHECKLIST.md).
The blockers are: run the instrumented migration test, sign the build, complete a
manual device pass, and publish a privacy policy.

> Screenshots are absent on purpose. The three previously committed here were
> corrupt — captured through a PowerShell redirect that passed PNG bytes through
> a UTF-16 encoder — and rendered as broken images. They will return when they can
> be captured from a real device.

---

## 📚 Documentation

| Document | Contents |
|---|---|
| [`docs/AUDIT_2026.md`](docs/AUDIT_2026.md) | Full audit: baseline, architecture map, feature inventory, 39 findings with evidence and confidence levels |
| [`docs/PRIVACY_THREAT_MODEL.md`](docs/PRIVACY_THREAT_MODEL.md) | Data inventory, network boundary, threats, Data Safety draft |
| [`docs/PLAY_RELEASE_CHECKLIST.md`](docs/PLAY_RELEASE_CHECKLIST.md) | Release blockers and the device verification pass |
| [`RELEASE_GUIDE.md`](RELEASE_GUIDE.md) | Signing and release builds |

---

## 🛡️ License

All rights reserved.
