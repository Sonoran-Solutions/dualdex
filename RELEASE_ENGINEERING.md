# DualDex Release Engineering

This document records the permanent Android application identity, the versioning
policy, the release-signing workflow, and the artifact-verification procedure for
the first public DualDex beta. It is the authoritative reference for maintainers
preparing a release; `RELEASE_CHECKLIST.md` remains the broader tracking list.

## Public application identity

The public DualDex Android application ID is **`com.dualdex`**.

```text
applicationId: com.dualdex
```

Changing it after public release would create a separate Android application
rather than an upgrade path: Android keys application identity, saved data, and
upgrade eligibility on this ID. `com.dualdex` has been used consistently since
early development (`namespace` and `applicationId` both match) and there is no
concrete reason it is unsuitable, so it is preserved as the permanent identity.

Do **not** casually rename the package. Any future rename must be treated as a
new application, not an upgrade.

## Version

```text
versionName = "0.9.0-beta.1"
versionCode = 1
```

These live in `app/build.gradle.kts` under `defaultConfig` and are the single
source of truth. They are consumed by the app through Android-generated
`BuildConfig` metadata (`VERSION_NAME`, `VERSION_CODE`, `BUILD_TYPE`,
`APPLICATION_ID`), never through a duplicated hard-coded UI string.

### versionCode policy

The policy is deliberately simple:

> Every distributed Android build increments `versionCode` by 1.
> `versionName` carries the human semantic version.
> `versionCode` must never decrease and must never be reused.

A distributed build is any APK given to another person (beta, RC, or stable).
The planned sequence is:

| versionName     | versionCode |
|-----------------|-------------|
| 0.9.0-beta.1    | 1           |
| 0.9.0-beta.2    | 2           |
| 0.9.0-rc.1      | 3           |
| 0.9.0           | 4           |
| 0.9.1           | 5           |
| 1.0.0           | 6           |

Rules:

- **Beta/RC** prerelease versions still increment `versionCode` by 1. Android
  cannot order prerelease semantics, so the monotonic integer is what guarantees
  upgrades install.
- **Stable** versions follow the same rule: increment by 1, never re-derive from
  the semantic version.
- **Never decrease or reuse** a `versionCode` that was ever distributed.
- Internal/CI debug builds reuse the current `versionCode`/`versionName` and are
  not counted as distributed builds.

There is no mathematical encoding of the semantic version into `versionCode`;
clarity is preferred over cleverness.

## Build / version diagnostics

The About & Diagnostics section of Settings sources its values from
`BuildInfo`, which reads Android-generated `BuildConfig`:

- `BuildConfig.VERSION_NAME` → `DualDex 0.9.0-beta.1`
- `BuildConfig.VERSION_CODE` → `versionCode 1`
- `BuildConfig.BUILD_TYPE` → `build type debug` or `build type release`

`BuildInfoFormatter` (pure, unit-tested) renders the strings so the UI cannot
drift from the Gradle configuration. No commit/build ID is surfaced yet; adding
one would require a safe, reproducible source-revision mechanism that does not
depend on Git being present on end-user devices. Version + build type are
sufficient for the first beta.

## Release signing (fail-closed)

Production signing secrets are **never** stored in this repository. They are
loaded only from external sources at build time:

- Environment variables, or
- a gitignored local `signing.properties` file at the repository root
  (environment variables take precedence).

| Secret | Meaning |
|--------|---------|
| `DUALDEX_KEYSTORE_PATH`     | Absolute path to the release keystore |
| `DUALDEX_KEYSTORE_PASSWORD` | Keystore password |
| `DUALDEX_KEY_ALIAS`         | Key alias inside the keystore |
| `DUALDEX_KEY_PASSWORD`      | Key password |

`signing.properties.example` documents the variable names with placeholders
only; it contains no real credentials.

### Fail-closed behavior

The `release` build type has no debug-signing fallback. If any required
credential is missing, `assembleRelease` (and `bundleRelease`/`packageRelease`)
fails with:

```text
Production signing credentials are not configured.
Set DUALDEX_KEYSTORE_PATH, DUALDEX_KEYSTORE_PASSWORD,
DUALDEX_KEY_ALIAS, and DUALDEX_KEY_PASSWORD.
See RELEASE_ENGINEERING.md for the release-signing workflow.
```

This is intentional: a command intended to produce the public release artifact
must never silently fall back to a debug-signed or unsigned APK. Normal debug
builds and canonical CI (`./ci.sh test|build|all`) do **not** require these
credentials and continue to work.

## Release build command

```bash
# Fail-closed production release build (requires signing credentials):
./ci.sh release

# Equivalent underlying Gradle task:
./gradlew assembleRelease
```

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`.

`./ci.sh build` continues to mean the **debug** APK (`app-debug.apk`), as
documented in the canonical CI contract.

## Maintainer signing-key setup (manual, outside this repository)

The permanent production signing key is created and stored by a human. The
following steps are documented but **not** automated by this repository, and no
key is generated here.

1. Generate a long-lived Android signing key using Android Studio (Build →
   Generate Signed Bundle / APK) or `keytool`:

   ```bash
   # Example only — choose your own strong password and safe output path.
   keytool -genkeypair -v \
     -keystore /secure/outside/repo/dualdex-release.keystore \
     -alias dualdex \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Back the keystore up securely in **more than one** safe location.
3. Store the keystore password, key password, and alias in a password manager.
4. Configure the required variables, for example via `signing.properties`
   (copy `signing.properties.example`) or environment variables.
5. Run `./ci.sh release` and verify the artifact (see below).

> **Losing this key can prevent future builds from upgrading the installed
> public app.** Android only permits an upgrade when the new APK is signed by
> the same key as the installed app. If the key is lost, the only path is a
> brand-new application ID — a fresh install with no upgrade path and no access
> to the previous app's data.

The same key identity must be used for every future public build.

## Verifying a release artifact

Use standard Android tooling already present in the development environment.

```bash
APK=app/build/outputs/apk/release/app-release.apk

# Package name, versionName, and versionCode:
aapt dump badging "$APK" | grep -E "package:|versionName|versionCode"

# Signing certificate (must be the production key, NOT "Android Debug"):
apksigner verify --print-certs "$APK"

# Overall signature verification:
apksigner verify "$APK"
```

Confirm:

- `package: name='com.dualdex'` — package identity is correct.
- `versionName='0.9.0-beta.1'` and the expected `versionCode`.
- The signing certificate subject/DN matches the production key, not
  `CN=Android Debug`.
- The APK is not `debuggable` (production release is not debuggable unless
  explicitly intended).

## Signed upgrade validation

A same-key beta-to-beta upgrade test is a manual release step and requires the
real production key.

**Procedure (release-signed builds A → B, same key):**

1. Install release-signed beta build A on the target device (AYN Thor).
2. Play, save, and configure settings so there is SHA-scoped save data and
   settings state to preserve.
3. Build release-signed beta build B with the **same production key** and a
   higher `versionCode`.
4. Install build B over build A without uninstalling.
5. Verify the app upgrades successfully (no signature conflict).
6. Verify SHA-scoped saves and settings remain present and load correctly.

> This must use two **release-signed** builds with the **same** key. A
> debug-signed → release-signed test is invalid: the signatures differ and
> Android correctly treats them as incompatible applications.

To validate only the Gradle upgrade mechanics without the production key, a
maintainer may build two release APKs using a **developer-owned temporary test
key** (never committed). This exercises the build plumbing but does **not**
validate the production key; final same-key validation with the real key is
still required.

## Automated coverage

- `BuildInfoTest` asserts `versionName == "0.9.0-beta.1"`, `versionCode == 1`,
  `applicationId == "com.dualdex"`, and that `BuildInfo` is sourced from
  `BuildConfig` (no duplicated hard-coded string).
- `BuildInfoFormatterTest` unit-tests the About/diagnostics formatting.
- Fail-closed signing is exercised by running `./ci.sh release` (or
  `./gradlew assembleRelease`) without credentials and confirming it fails with
  the explicit signing error, while `./ci.sh build` succeeds without credentials.
