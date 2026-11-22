# MacroMandate — Release & Signing Guide

MacroMandate is distributed through GitHub Releases. Users install the signed release APK from the release assets.

---

## 1. Release Keystore Setup

Use the existing signing key if you have already distributed an APK. For the first distribution, create a PKCS12 keystore with Java's `keytool`.

```bash
keytool -genkey -v -keystore macromandate-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias macromandate-key
```

Keep a secure backup of the keystore outside the repository. Use the same signing key and application ID for future APKs, and increase `versionCode` for updates.

The 1.0.4 candidate uses version code 5 and Room schema 8. The migration accepts both version-7 schemas, including the one shipped in v1.0.3, and preserves meal and activity records.

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

### Windows-generated release key

If the release key was generated in the Windows signing setup, the PKCS12 file and its DPAPI-protected password are under `%LOCALAPPDATA%/MacroMandate/release-signing`. Use the `macromandate-release` alias. The encrypted password file can be opened by the Windows account that created it. Keep a recoverable backup of the key and its password before relying on a different computer or Windows account.

To build from that setup, start a fresh PowerShell 7 session and run the following. The password is passed to Gradle through the child process environment.

```powershell
$signingDir = Join-Path $env:LOCALAPPDATA 'MacroMandate/release-signing'
$encryptedPassword = Join-Path $signingDir 'keystore-password.dpapi'
$securePassword = Get-Content -Raw -LiteralPath $encryptedPassword | ConvertTo-SecureString
try {
    $env:RELEASE_STORE_FILE = Join-Path $signingDir 'macromandate-release.p12'
    $env:RELEASE_KEY_ALIAS = 'macromandate-release'
    $env:RELEASE_STORE_PASSWORD = ConvertFrom-SecureString $securePassword -AsPlainText
    $env:RELEASE_KEY_PASSWORD = $env:RELEASE_STORE_PASSWORD
    .\gradlew.bat --no-daemon --no-configuration-cache :app:assembleRelease
} finally {
    Remove-Item Env:RELEASE_STORE_FILE, Env:RELEASE_KEY_ALIAS, Env:RELEASE_STORE_PASSWORD, Env:RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
```

### Option B: CI/CD Environment Variables
Set the following environment variables in your CI workflow:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Check that signing is configured before preparing release assets. Without a keystore, Gradle produces `app-release-unsigned.apk` for build verification.

---

## 3. Production Build Commands


On Windows, build the signed, minified release and verify its signature and package alignment.

```powershell
.\gradlew.bat :app:assembleRelease
& "$env:ANDROID_HOME/build-tools/36.0.0/apksigner.bat" verify --print-certs app/build/outputs/apk/release/app-release.apk
& "$env:ANDROID_HOME/build-tools/36.0.0/zipalign.exe" -c -P 16 4 app/build/outputs/apk/release/app-release.apk
Get-FileHash app/build/outputs/apk/release/app-release.apk -Algorithm SHA256
```

After preflight, draft a [GitHub release](https://github.com/shareef01/MacroMandate/releases) for the tested commit. Attach the signed APK and include its SHA-256 hash in the release notes. [GitHub release instructions](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

---

## 4. R8 & ProGuard Verification

MacroMandate uses full code shrinking and resource optimization (`isMinifyEnabled = true`, `isShrinkResources = true`). Rules are specified in `app/proguard-rules.pro`:

- Room entities, DAOs, and Migrations preserved.
- Network models and Gson serialization tokens preserved.
- Retrofit & OkHttp interface reflection preserved.

---

## 5. Release preflight

Run the checklist against the exact candidate version. See the [production audit](docs/PRODUCTION_AUDIT.md) for the current verdict and findings.

- [ ] Build debug and release from a clean checkout. Run host tests, Android lint, and instrumentation on a dedicated Android test device.
- [ ] Verify the signed APK, including the signer, version code, R8 behavior, and 16 KiB native and APK alignment.
- [ ] Test old-database upgrades, photo and metadata backup restore, rollback, permission denial, and storage-provider failure.
- [ ] Test authenticated photo analysis with OpenRouter's `openrouter/free` in the minified app, including free-tier errors. Check barcode lookup separately.
- [ ] Validate the notification and location behavior on supported Android versions and the disclosure for transmitting photos and optional coordinates.
- [ ] Check the [Android developer verification timeline](https://developer.android.com/developer-verification/guides/faq) for direct sideloading before each public release.
