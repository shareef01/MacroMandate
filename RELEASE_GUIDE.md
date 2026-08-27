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

MacroMandate dynamically resolves release signing credentials from multiple sources in priority order:

1. **Environment Variables** (Recommended for CI/CD)
2. **`local.properties`** (Recommended for local developer machines)
3. **`gradle.properties`**

### Option A: Local Configuration (`local.properties`)
Add the following keys to your root `local.properties`:

```properties
RELEASE_STORE_FILE=C:/path/to/macromandate-release.jks
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=macromandate-key
RELEASE_KEY_PASSWORD=your_key_password
```

### Option B: CI/CD Environment Variables
Set the following environment variables in your CI workflow:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

*Note: If no keystore is provided, `assembleRelease` will still succeed and output an unsigned release artifact.*

---

## 3. Production Build Commands

### Build Release APK (with R8 ProGuard shrinking enabled):
```bash
./gradlew assembleRelease
```
Artifact location: `app/build/outputs/apk/release/app-release.apk`

### Build Production Android App Bundle (AAB for Google Play):
```bash
./gradlew bundleRelease
```
Artifact location: `app/build/outputs/bundle/release/app-release.aab`

---

## 4. R8 & ProGuard Verification

MacroMandate uses full code shrinking and resource optimization (`isMinifyEnabled = true`, `isShrinkResources = true`). Rules are specified in `app/proguard-rules.pro`:

- Room entities and DAOs preserved.
- Network models and Gson serialization tokens preserved.
- Retrofit & OkHttp interface reflection preserved.

---

## 5. Release Pre-Flight Checklist

- [x] All 39 automated unit tests passing (`./gradlew test`).
- [x] R8 release build compilation succeeds (`./gradlew assembleRelease`).
- [x] Hugging Face API keys masked and zero credential leaks in logs or exports.
- [x] JSON backup schema compatibility maintained.
- [x] Location tracking opt-in confirmed (off by default).
