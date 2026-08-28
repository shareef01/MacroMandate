# MacroMandate — Privacy Threat Model

What the app collects, where it goes, and what protects it. Written against the
source as of the August 2026 audit (`docs/AUDIT_2026.md`).

Claims here describe **verified implementation**, not intent. Where something is
unverified, it says so.

---

## 1. Posture in one paragraph

MacroMandate is local-first. There are no accounts, no analytics SDK, no crash
reporter, no advertising identifier, and no server operated by this project.
Every meal, photograph, coordinate and preference lives in the app's private
storage on the device. Two things leave it, both only when the user asks for
them, both to the same third-party AI provider the user configures with their own
credential: a **meal photograph** sent for nutrition estimation, and — if the
Daily summary button is tapped — **today's meal names and macro totals as text**.
The app is fully usable with both switched off.

---

## 2. Data inventory

### 2.1 Meal records

| | |
|---|---|
| **Collected** | Manual entry, or confirmed AI analysis |
| **Why** | The product |
| **Stored** | Room, `macro_mandate_db`, app-private (`/data/data/…/databases/`) |
| **Encrypted at rest** | **No.** Plain SQLite. Protection is the app sandbox and device encryption |
| **Retention** | Until the user deletes the meal or uninstalls |
| **Exported** | Yes — CSV and JSON, user-initiated, to a location the user picks |
| **Leaves device** | Only in an export the user creates |
| **Deletion** | Per-meal from the detail screen or the history list |

Fields: id, timestamp, food name, calories, protein/carbs/fat, liquid flag,
optional image path, optional coordinates, optional model assessment, a
late-night flag.

### 2.2 Meal photographs

| | |
|---|---|
| **Collected** | Camera capture or gallery selection, per meal |
| **Why** | AI nutrition estimation, and a visual record on the detail screen |
| **Stored** | `filesDir/evidence/<mealId>.jpg`, app-private |
| **Encrypted at rest** | **No.** Sandbox + device encryption |
| **Retention** | Until the meal is deleted |
| **Leaves device** | **Yes** — see §3 |
| **Deletion** | With the meal (**fixed in this audit — see below**) |

> **Previously broken.** Deleting a meal did not delete its photograph. The call
> passed a meal id where a URI was expected, so the ownership check failed on the
> first line and returned without deleting. Every photo survived the deletion of
> its meal, indefinitely, while the UI promised the record would be "permanently
> expunged". Fixed (`AUDIT_2026.md` §6.1). Photos orphaned by an
> earlier build are **not** cleaned up retroactively.

The image is only copied into durable storage **after** the user confirms an
analysis. A discarded result leaves nothing behind.

### 2.3 Location coordinates

| | |
|---|---|
| **Collected** | Only when "Tag meals with location" is on **and** the user takes/picks a photo |
| **Default** | **Off** |
| **Why** | Optional meal geotagging and the map view |
| **Stored** | On the meal row, and drawn into the uploaded image as a watermark |
| **Precision** | Full device precision; displayed at 6 decimal places (~0.1 m) |
| **Retention** | With the meal |
| **Exported** | JSON backup **yes**; CSV **no** (deliberate — see §5) |
| **Leaves device** | **Yes, when the setting is on** — burned into the uploaded photo |

Requested at capture time, never at launch. Meal logging is never gated on it.
`lastLocation` only — no continuous tracking, no background location, no
geofencing.

> **Known gap.** There is no way to strip coordinates from an existing meal
> without deleting the whole record.

### 2.4 Analysis API credential

| | |
|---|---|
| **Collected** | Typed by the user in Settings |
| **Why** | Authenticates their own requests to their own provider account |
| **Stored** | DataStore Preferences, `mandate_prefs`, app-private |
| **Encrypted at rest** | **No — and deliberately not claimed to be** (§4) |
| **Retention** | Until cleared in Settings |
| **Exported** | **Never.** Not in CSV, JSON, the weekly report, or the audit log |
| **Displayed** | Masked (`••••••••` + last 4). Entry field uses `PasswordVisualTransformation` |
| **Leaves device** | Only as an `Authorization: Bearer` header to the configured endpoint |

Release builds cannot contain a developer credential: the build fails if one is
present (`AUDIT_2026.md` §4.1).

### 2.5 Preferences

Daily calorie target, terminal theme, reminders on/off, location on/off. All in
the same DataStore file. Not exported, never transmitted.

### 2.5b Model-generated prose

The daily briefing and the per-meal `assessment` are text produced by the
provider's model. The assessment is stored on the meal row and included in the
JSON backup; the briefing is held in memory only and discarded on dismissal.

The briefing prompt was previously escalated by how far the user was from their
target — at the extreme it instructed the model to produce *"TERMINAL WARNING.
ABSOLUTE CONDEMNATION."* about what the person had eaten. That escalation is
removed; the prompt now asks for a factual summary in the terminal register and
explicitly forbids evaluating the person or giving dietary advice.

### 2.6 Audit log

A local activity trail (max 1,000 rows, pruned in batches of 100). Contains
**meal names** and target values, e.g. `RECORD LOGGED: PORRIDGE`.

Verified: it records `"API key saved."` / `"API key cleared."` — **never the key
itself**. It is not exported and not transmitted. Clearable from Settings.

---

## 3. The network boundary

**Exactly one outbound destination exists.** Two features send data to it.

```
Camera / gallery
  → decode, downsample to ≤800 px, JPEG q80          [on device]
  → optional coordinate watermark drawn into pixels   [on device, opt-in]
  → base64
  → POST {MANDATE_API_BASE_URL}v1/chat/completions
        Authorization: Bearer <user's key>
        body: prompt + inline data:image/jpeg;base64
  → response parsed, sanitized, bounded
  → shown to the user for review                      [not yet stored]
  → user confirms → written to Room
```

Default endpoint: `https://router.huggingface.co/`. Overridable at build time via
`MANDATE_API_BASE_URL`, which is the supported route for pointing the app at a
self-hosted proxy.

**What is sent:** the downsampled photograph, a fixed prompt, the model id, and
the user's bearer token.

### 3b. The second path — "Daily summary"

The **Trends → Daily summary** button sends **today's meal names and macro
totals, as text**, to the same endpoint, and shows the model's prose reply.

```
POST {MANDATE_API_BASE_URL}v1/chat/completions
  "Summarize these meals … Data: Total: 1850 kcal, 90P, 210C, 60F.
   Items: Porridge, Chicken salad, Pasta"
```

> **Correction.** An earlier draft of this document stated that the meal log and
> totals never leave the device. That was wrong — it was written from the photo
> path alone, before this feature was traced. It is recorded here rather than
> quietly edited, because the same mistake is what produces inaccurate Data
> Safety declarations.

The button now carries a disclosure line naming what it transmits. It is
opt-in per use — nothing is sent unless it is tapped.

**What is never sent, on either path:** the full meal history, coordinates as
structured data (only as pixels in a watermarked image), preferences, the audit
log, or any device or advertising identifier.

**What the provider receives:** an image of the user's food — which may
incidentally contain faces, a home interior, documents on the table, or location
clues; and, if location tagging is on, their **exact coordinates rendered as
legible text in the image**. Retention and training use are governed by the
provider's terms, not by this app.

### Disclosure

Three places, all verified present:

1. Under the capture buttons, where the decision is made: *"Photos are sent to
   your configured analysis provider. Manual entry stays on this device."*
2. On the location toggle: *"When on, your precise coordinates are saved with
   each meal, printed onto the photo, and that photo is sent to the analysis
   service. Off by default."*
3. Beside the Daily summary button: *"'Daily summary' sends today's meal names
   and totals to your analysis provider."*

> **Previously missing.** Before this audit, nothing anywhere told the user that
> the photograph itself was uploaded (§9.2).

### Logging

- OkHttp logging is `HEADERS` in debug, `NONE` in release, with
  `redactHeader("Authorization")`.
- Model responses and provider error bodies are logged **only** under
  `if (BuildConfig.DEBUG)`. They were previously logged unconditionally via
  `Log.e`, which is emitted in release (§3.2).
- Coordinates are never logged.

---

## 4. On the word "encrypted"

**MacroMandate does not encrypt anything itself, and this document will not say
that it does.**

The API token sits in a plaintext DataStore file. What actually protects it:

| Protection | Holds against | Does not hold against |
|---|---|---|
| App sandbox (Linux UID isolation) | Other installed apps | Root, an unlocked bootloader, a custom recovery |
| File-based encryption (platform) | Physical access to a **locked, powered-off** device | An unlocked device |
| `allowBackup="false"` + explicit `data_extraction_rules.xml` | Cloud backup, device-to-device transfer | Local access |

**Why no Keystore wrapper was added.** The app must send the token in a header,
so the same process must be able to decrypt it. A Keystore-wrapped value raises
the bar for an attacker with code execution as this app only marginally, while
making it tempting to describe the token as "encrypted" — which would be a
stronger claim than the design supports. Accurate documentation was judged worth
more than the appearance of hardening.

**If a stronger guarantee is ever needed**, the correct move is to stop holding
the credential on the device at all: run a backend proxy and point
`MANDATE_API_BASE_URL` at it.

---

## 5. Exports

| | CSV | JSON backup |
|---|---|---|
| Purpose | Spreadsheets | Full restore |
| Meal records | Yes | Yes |
| **Coordinates** | **No** | **Yes** |
| Model assessment | No | Yes |
| Image paths | No | Yes |
| API key | **Never** | **Never** |
| Encoding | UTF-8 **with BOM**, CRLF | UTF-8 |
| Formula injection | Neutralized (`=+-@\t` prefixed with `'`) | n/a |

**Why coordinates are in one and not the other.** A CSV is the artefact people
mail to themselves and open on a shared machine; a location history should not
ride along by accident. The JSON backup is a complete archive whose purpose is
restoration, so it must round-trip everything — and it is documented here as
containing location data.

Both are written through the Storage Access Framework to a location the user
picks. No storage permission is requested, and no file is written to shared
storage without an explicit user choice.

### Restore is treated as hostile input

The file is arbitrary bytes from a picker. Verified defences: version checked
against a supported ceiling; 16 M-char size cap; all nutrition values clamped
through the same gate the UI uses; timestamps clamped to a plausible range;
out-of-range coordinates dropped; strings truncated; duplicate ids collapsed
deterministically; and **image URIs accepted only if they point inside this app's
own evidence directory** — a backup could otherwise have aimed a record's image
path at any file on the device, including the meal database, which the deletion
path would then act on (§6.2).

---

## 6. Threats considered

| Threat | Assessment |
|---|---|
| Another app reads the DB or token | Blocked by the sandbox on a non-rooted device |
| Credential recovered from the APK | **Was possible**; release builds now fail if a key would be embedded (§4.1) |
| Credential leaked via logs | OkHttp redacts the header; body logging is debug-only |
| Data leaked via cloud backup | `allowBackup="false"` plus explicit exclusion rules |
| Malicious backup file writes bad data | Clamped and filtered at the restore boundary |
| Malicious backup file causes arbitrary file deletion | Canonical-path check + URI filtering (§6.1, §6.2) |
| CSV formula injection into a spreadsheet | Neutralized, with tests |
| Photo leaks more than the user intended | **Real and inherent.** Mitigated by disclosure at the point of capture; not eliminable while the feature exists |
| Coordinates leak via the uploaded image | **Real.** Opt-in, off by default, disclosed on the toggle |
| Intent spoofing of exported components | Only the launcher activity and the Glance widget receiver are exported; neither accepts extras that alter data |
| Cleartext downgrade | Default endpoint is HTTPS. **Not enforced** — see below |

### Known unmitigated items

- **`MANDATE_API_BASE_URL` is not validated.** A build-time override could
  specify `http://`. It is build-time-only and not user-reachable, so it is not
  an attack surface for end users, but a `networkSecurityConfig` forbidding
  cleartext outside debug would make it structural.
- **No "delete all my data" control.** Meals delete individually; the audit log
  clears; there is no single erase-everything action.
- **Photos orphaned by pre-fix builds are not swept.**
- **No runtime verification.** Nothing in this document was confirmed by
  observing the app run — no device was available. It is derived from source.

---

## 7. Data Safety form — draft

For Play Console. **Verify against the shipping build before submitting.**

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **Collects** on-device; **shares** photos with a user-configured third-party provider when analysis is used |
| Photos — collected | Yes |
| Photos — shared | **Yes**, with the AI provider the user configures |
| Photos — processed ephemerally | **No** — stored on device with the meal |
| Photos — required | **No** — manual entry is a full alternative |
| Location — collected | Yes, **optional**, off by default |
| Location — shared | **Yes**, when tagging is on (watermarked into the uploaded photo) |
| Health & fitness — collected | Yes (meals, calories, macros) |
| Health & fitness — shared | No |
| Personal identifiers | None. No accounts, no ads id, no analytics |
| Data encrypted in transit | Yes (HTTPS to the default endpoint) |
| Data encrypted at rest | **Say no.** Platform file-based encryption is not app-level encryption |
| Users can request deletion | Yes — per-meal in-app, and uninstall removes everything |

A privacy policy URL is required because the app collects health data and shares
photos. It must name the AI provider and state that photographs are transmitted
for analysis.
