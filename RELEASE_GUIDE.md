# MacroMandate — Release & Signing Guide

This guide describes how to configure signing credentials, build signed release APKs / Android App Bundles (AAB), and verify production artifacts.

---

## 1. Release Keystore Setup

To sign production builds, generate a PKCS12 keystore using Java's `keytool`:

```bash
keytool -genkey -v -keystore macromandate-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias macromandate-key
```

Keep your `.jks` file secure and **never commit it to Git**.

---

## 2. Signing Configuration

MacroMandate resolves release signing credentials from these untracked sources:

1. **Environment Variables** (Recommended for CI/CD)
2. **User-level `~/.gradle/gradle.properties`** (local developer machines)

Never put signing passwords in the repository's `gradle.properties` or
`local.properties`. For a local build, put these in your user-level
`~/.gradle/gradle.properties`:

```properties
RELEASE_STORE_FILE=C:/path/to/macromandate-release.jks
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=macromandate-key
RELEASE_KEY_PASSWORD=your_key_password
```

### CI/CD Environment Variables
Set the following environment variables in your CI workflow:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Normal CI permits unsigned release compilation. Distribution uses the `Signed
Android Release` workflow, which requires all signing values plus
`RELEASE_KEYSTORE_BASE64` and `RELEASE_CERT_SHA256`, then verifies both the AAB
signature and the exact production certificate fingerprint.

---

## 3. Production Build Commands

### Build Release APK (with R8 ProGuard shrinking enabled):
```bash
./gradlew assembleRelease
```
Artifact location: `app/build/outputs/apk/release/app-release.apk`
(or `app-release-unsigned.apk` when no keystore is configured)

### Build Production Android App Bundle (AAB for Google Play):
```bash
./gradlew bundleRelease -PproductionRelease=true
```
Artifact location: `app/build/outputs/bundle/release/app-release.aab`

---

## 4. R8 & ProGuard Verification

MacroMandate uses full code shrinking and resource optimization (`isMinifyEnabled = true`, `isShrinkResources = true`). Rules are specified in `app/proguard-rules.pro`:

- Room entities and DAOs preserved.
- Network models and Gson serialization tokens preserved.
- Retrofit & OkHttp interface reflection preserved.

---

## 5. Build-Time Credential Guard

Release builds and `verifyReleaseConfiguration` **fail** if `HUGGINGFACE_API_KEY` is present in
`local.properties`:

```
MM_RELEASE_EMBEDDED_API_KEY_FORBIDDEN
```

This is not a precaution — it was verified by building an APK with a test key and
recovering that key verbatim from `classes.dex` with a plain byte search. A
`BuildConfig` string constant is not a secret.

Two supported architectures:

- **Bring-your-own-key** (current): the user pastes their own token in Settings.
  Leave `HUGGINGFACE_API_KEY` unset.
- **Backend proxy**: set `MANDATE_API_BASE_URL` to a service you run that holds
  the credential.

`-PallowEmbeddedKey=true` overrides the guard. Use it only for a private build
for yourself, accepting that the key ships inside it.

The debug build is unaffected.

---

## 6. Release Pre-Flight Checklist

**Verified by the August/September 2026 audit** (`docs/AUDIT_REPORT.md`):

- [x] Unit tests passing (`./gradlew test`)
- [x] `lintDebug` and `lintVitalRelease` clean (0 errors)
- [x] R8 release build succeeds (`./gradlew assembleRelease`)
- [x] Unsigned App Bundle compilation succeeds (`./gradlew bundleRelease`) -> `app-release.aab`
- [x] Instrumented `MigrationTest` passing on hardware (`./gradlew connectedDebugAndroidTest` / `am instrument`)
- [x] No credential in logs, exports or backups; release build refuses to embed one
- [x] Location tracking opt-in, off by default
- [x] Restore validates hostile input; every write path shares one validation gate

**Remaining before store submission:**

- [ ] Run the `Signed Android Release` workflow and verify its certificate check
- [ ] Manual device pass — see `docs/PLAY_RELEASE_CHECKLIST.md` §7
- [ ] Privacy policy published at public URL

> **Never ship a Room schema bump without a `Migration` and a passing
> `MigrationTest`.** Destructive fallback is now debug-only, so a missing
> migration fails the release build's database open rather than silently
> deleting the user's meals — but a failed open is still a broken app.
>
> **Note on legacy schemas (pre-v6):** Room schema exports exist from version 6
> onwards (`6.json`, `7.json`). Fresh installs start directly at v7, and upgrades
> from v6 to v7 are fully non-destructive and guarded by `MigrationTest`.
> Users upgrading from an unsupported legacy pre-v6 release build will encounter
> a database open failure rather than silent data erasure.
