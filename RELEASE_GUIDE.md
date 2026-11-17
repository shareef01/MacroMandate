# MacroMandate release guide

GitHub Releases distributes MacroMandate as a signed APK with a SHA-256 checksum. The **Signed Android Release** workflow builds the APK with the existing release key, verifies its signature and certificate, then checks both uploaded asset digests before making the release available. The signed Android App Bundle is stored as a workflow artifact.

## Prepare and publish

1. Update `versionName` and increment `versionCode` in [app/build.gradle.kts](app/build.gradle.kts). Merge the release commit into `master` and wait for [Android CI](https://github.com/shareef01/MacroMandate/actions/workflows/android.yml) to pass.
2. Open [Signed Android Release](https://github.com/shareef01/MacroMandate/actions/workflows/release.yml), choose **Run workflow** on `master`, and select `draft`.
3. Review the release notes and the two assets, `MacroMandate-<version>.apk` and `MacroMandate-<version>.apk.sha256`. Install the draft APK over the previous release on a test device for changes that affect upgrades, storage, or signing.
4. Publish the draft from [GitHub Releases](https://github.com/shareef01/MacroMandate/releases). Use `publish` at dispatch to create and publish a new release in one run. The workflow refuses to replace an existing tag or release, so publish an existing draft from its release page.

The workflow derives the `v<version>` tag from the signed APK and targets the exact build commit. Use `rehearse` to verify signing and uploads in a temporary draft that the workflow removes after checking both assets.

## Signing credentials

The workflow reads these GitHub Actions secrets from the `release-signing` environment, which is restricted to `master`.

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded production PKCS12 keystore |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Alias of the existing release key |
| `RELEASE_KEY_PASSWORD` | Signing-key password |
| `RELEASE_CERT_SHA256` | SHA-256 fingerprint of the expected production certificate |

Keep a secure backup of the existing keystore and passwords. Android checks the signing identity when installing an update over a GitHub release. Keep signing secrets and keystores out of version control and out of project Gradle properties.

For local production builds, provide `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` through environment variables or user-level Gradle properties.

```bash
./gradlew assembleRelease bundleRelease -PproductionRelease=true
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Normal CI can compile unsigned release builds. For distribution, use the production signing guard and leave `HUGGINGFACE_API_KEY` unset in `local.properties`; the release build rejects embedded API credentials by default.

## Verify a downloaded APK

Download the APK and its checksum into the same folder. Run the following with the actual release version in the filename.

```bash
sha256sum --check MacroMandate-1.0.3.apk.sha256
```

On Windows PowerShell:

```powershell
$apk = 'MacroMandate-1.0.3.apk'
$expected = (Get-Content "$apk.sha256" -Raw).Trim() -split '\s+' | Select-Object -First 1
(Get-FileHash $apk -Algorithm SHA256).Hash -eq $expected
```

PowerShell returns `True` when the hashes match.

## Database compatibility

Room schema exports start at version 6. Fresh installs use schema 7 and the instrumented tests verify the 6-to-7 migration. Upgrades from unsupported pre-v6 builds can fail to open the database. Future schema changes need an explicit migration and an instrumented migration test.
