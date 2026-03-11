# 10 — Deployment Workflow

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-11

---

## Overview

KiteWatch is distributed as a sideloaded APK — not via Google Play Store. This fundamental distribution constraint shapes the entire deployment pipeline: there is no automated over-the-air update mechanism provided by a store, no automatic review gate, and no centralized crash reporting tied to a store dashboard. All release infrastructure must be self-managed. The deployment workflow must therefore compensate for these gaps with disciplined versioning, a robust CI/CD pipeline, a manual but process-driven release process, and an in-app update notification mechanism.

---

## 1. Development Environment Setup

### 1.1 Required Toolchain

| Tool | Version Constraint | Purpose |
|---|---|---|
| Android Studio | Hedgehog (2023.1.1) or later | Primary IDE |
| JDK | 17 (Temurin distribution recommended) | Build JVM |
| Kotlin | 2.0+ | Language compiler |
| Android Gradle Plugin (AGP) | 8.3+ | Build system |
| Gradle | 8.6+ (via wrapper) | Build orchestration |
| Android SDK | `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35` | Platform targeting |
| Kotlin Symbol Processing (KSP) | Latest stable matching Kotlin version | Annotation processing (Room, Hilt) |
| ADB | Bundled with Android SDK | Device communication |

All developers must use the **Gradle wrapper** (`./gradlew`) exclusively. Never use a system-installed Gradle version. The `gradle-wrapper.properties` pins the exact Gradle version.

### 1.2 Repository Setup

```bash
# Clone repository
git clone git@github.com:org/kitewatch.git
cd kitewatch

# Verify Java version
java -version  # Must be 17

# Run initial sync (downloads all dependencies)
./gradlew dependencies --configuration releaseRuntimeClasspath

# Verify build
./gradlew assembleDebug
```

### 1.3 Local Secrets Configuration

Sensitive credentials (Kite Connect API Key, OAuth client IDs) are **never committed** to version control. They are injected via a local `secrets.properties` file at the project root (gitignored) and consumed via the `secrets-gradle-plugin`:

```properties
# secrets.properties (gitignored)
KITE_API_KEY=xxxxxxxxxxxxxxxx
GOOGLE_OAUTH_CLIENT_ID=xxxx.apps.googleusercontent.com
KEYSTORE_PATH=/absolute/path/to/kitewatch-release.jks
KEYSTORE_PASSWORD=xxx
KEY_ALIAS=kitewatch
KEY_PASSWORD=xxx
```

In `build.gradle.kts` (app module):

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "KITE_API_KEY", "\"${secretsProperties["KITE_API_KEY"]}\"")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"${secretsProperties["GOOGLE_OAUTH_CLIENT_ID"]}\"")
    }
}
```

### 1.4 Keystore Management

A single release keystore (`kitewatch-release.jks`) is used for all release builds. This keystore is:

- Stored in an encrypted secrets vault (e.g., Bitwarden, 1Password, or equivalent) accessible only to release engineers.
- **Never committed to the repository.**
- Its SHA-1 and SHA-256 fingerprints are registered in the Google Cloud Console for OAuth 2.0 credentials.
- Backed up to at least two physically separate secure locations.

**Keystore loss is unrecoverable** — users would need to uninstall and reinstall a re-keyed APK (losing all local data unless they restored from backup). Keystore continuity is a critical operational concern.

### 1.5 Pre-Commit Hooks

Configured via `git-hooks/pre-commit` (symlinked during `./gradlew setup`):

```bash
#!/bin/bash
# 1. Ktlint formatting check
./gradlew ktlintCheck --daemon
if [ $? -ne 0 ]; then
  echo "Ktlint check failed. Run ./gradlew ktlintFormat"
  exit 1
fi

# 2. Detekt static analysis
./gradlew detekt --daemon
if [ $? -ne 0 ]; then
  echo "Detekt analysis failed."
  exit 1
fi
```

---

## 2. Build Pipeline

### 2.1 Build Variants

| Variant | Signing | Debuggable | Minification | BuildConfig | Purpose |
|---|---|---|---|---|---|
| `debug` | Debug keystore | Yes | No | `BuildConfig.DEBUG = true` | Development; enables network inspection, strict mode |
| `staging` | Release keystore | No | Yes (R8) | `BuildConfig.DEBUG = false`, `STAGING = true` | Pre-release functional testing with prod signing |
| `release` | Release keystore | No | Yes (R8, full obfuscation) | `BuildConfig.DEBUG = false` | Distribution APK |

```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isDebuggable = true
            isMinifyEnabled = false
        }
        create("staging") {
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-STAGING"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 2.2 R8 / ProGuard Configuration

`proguard-rules.pro` must protect all classes that are:

- Serialized/deserialized via Moshi (keep all `@JsonClass` annotated classes)
- Reflected upon by Room (keep all `@Entity`, `@Dao` annotated classes)
- Used by Hilt (keep `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject`)
- Part of the Kite Connect API DTO models
- WorkManager `Worker` subclasses

```proguard
# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Moshi
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Retrofit + OkHttp
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kite Connect DTOs
-keep class com.kitewatch.core.network.dto.** { *; }
```

**Mandatory post-obfuscation validation:** After every release build, run the staging APK against all integration test scenarios to confirm no runtime `ClassNotFoundException` or reflection failures.

### 2.3 APK vs AAB Decision

KiteWatch distributes a **universal APK**, not an AAB. Rationale: AAB requires Play Store delivery to perform APK splitting by ABI/screen density. Sideloaded distribution requires a self-contained APK. The `release` build produces:

- `kitewatch-release-{versionCode}.apk` — universal APK, all ABI and screen density resources included.

This results in a slightly larger APK (~5–8 MB additional for multi-ABI libraries) but eliminates the device-specific split complexity.

### 2.4 Build Performance Configuration

`gradle.properties`:

```properties
# Daemon and memory
org.gradle.daemon=true
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true

# Kotlin
kotlin.incremental=true
kotlin.daemon.jvm.options=-Xmx2g

# Android
android.useAndroidX=true
android.nonTransitiveRClass=true
android.enableR8.fullMode=true
```

Multi-module design enables Gradle to compile independent modules in parallel. `:core-domain` (pure Kotlin) compiles fastest and is the most-depended-upon module — any change to it invalidates downstream module caches.

---

## 3. CI/CD Architecture

### 3.1 CI Platform

**Selected:** GitHub Actions (self-hosted runner option available if build times exceed 20 minutes on free tier).

Rationale: Free tier provides 2,000 minutes/month for private repositories. KiteWatch is a single-developer or small-team project with infrequent commits. GitHub Actions integrates natively with the repository, requires no external CI service setup, and supports secret injection for keystore credentials.

### 3.2 CI Workflow Files

#### 3.2.1 `ci-pr.yml` — Pull Request Validation

Triggers on: `pull_request` targeting `main` or `develop`.

```yaml
name: PR Validation

on:
  pull_request:
    branches: [main, develop]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Validate Gradle wrapper
        uses: gradle/wrapper-validation-action@v2

      - name: Ktlint check
        run: ./gradlew ktlintCheck

      - name: Detekt analysis
        run: ./gradlew detekt

      - name: Unit tests
        run: ./gradlew testDebugUnitTest

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: '**/build/reports/tests/'

      - name: Assemble debug APK (build verification)
        run: ./gradlew assembleDebug
```

Mandatory gate: All jobs must pass before PR can be merged. No bypasses permitted on `main`.

#### 3.2.2 `ci-staging.yml` — Staging Build

Triggers on: Push to `develop` branch.

```yaml
name: Staging Build

on:
  push:
    branches: [develop]

jobs:
  staging-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Decode keystore
        run: |
          echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 --decode > kitewatch-release.jks

      - name: Write secrets.properties
        run: |
          echo "KITE_API_KEY=${{ secrets.KITE_API_KEY }}" >> secrets.properties
          echo "GOOGLE_OAUTH_CLIENT_ID=${{ secrets.GOOGLE_OAUTH_CLIENT_ID }}" >> secrets.properties
          echo "KEYSTORE_PATH=kitewatch-release.jks" >> secrets.properties
          echo "KEYSTORE_PASSWORD=${{ secrets.KEYSTORE_PASSWORD }}" >> secrets.properties
          echo "KEY_ALIAS=${{ secrets.KEY_ALIAS }}" >> secrets.properties
          echo "KEY_PASSWORD=${{ secrets.KEY_PASSWORD }}" >> secrets.properties

      - name: Run all unit tests
        run: ./gradlew testStagingUnitTest

      - name: Assemble staging APK
        run: ./gradlew assembleStagingRelease

      - name: Upload staging APK
        uses: actions/upload-artifact@v4
        with:
          name: kitewatch-staging-${{ github.run_number }}
          path: app/build/outputs/apk/staging/release/*.apk
          retention-days: 14
```

#### 3.2.3 `ci-release.yml` — Release Build

Triggers on: Push of a version tag matching `v*.*.*` to `main`.

```yaml
name: Release Build

on:
  push:
    tags:
      - 'v*.*.*'

jobs:
  release-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Full history for changelog generation

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Decode keystore
        run: |
          echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 --decode > kitewatch-release.jks

      - name: Write secrets.properties
        run: |
          echo "KITE_API_KEY=${{ secrets.KITE_API_KEY }}" >> secrets.properties
          echo "GOOGLE_OAUTH_CLIENT_ID=${{ secrets.GOOGLE_OAUTH_CLIENT_ID }}" >> secrets.properties
          echo "KEYSTORE_PATH=kitewatch-release.jks" >> secrets.properties
          echo "KEYSTORE_PASSWORD=${{ secrets.KEYSTORE_PASSWORD }}" >> secrets.properties
          echo "KEY_ALIAS=${{ secrets.KEY_ALIAS }}" >> secrets.properties
          echo "KEY_PASSWORD=${{ secrets.KEY_PASSWORD }}" >> secrets.properties

      - name: Run full test suite
        run: ./gradlew testReleaseUnitTest

      - name: Assemble release APK
        run: ./gradlew assembleRelease

      - name: Verify APK signing
        run: |
          $ANDROID_HOME/build-tools/34.0.0/apksigner verify \
            --print-certs \
            app/build/outputs/apk/release/*.apk

      - name: Compute APK SHA-256
        run: sha256sum app/build/outputs/apk/release/*.apk > apk-sha256.txt

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            app/build/outputs/apk/release/*.apk
            apk-sha256.txt
          generate_release_notes: true
          draft: true  # Require manual publish
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

The release is created as a **draft** — a human must inspect it, attach the changelog, and publish it. This prevents accidental automated releases.

### 3.3 GitHub Actions Secrets Configuration

| Secret Name | Value | Usage |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `.jks` file | Injected as file during release/staging builds |
| `KEYSTORE_PASSWORD` | Keystore password | Signing |
| `KEY_ALIAS` | Key alias in keystore | Signing |
| `KEY_PASSWORD` | Key password | Signing |
| `KITE_API_KEY` | Zerodha Kite Connect API key | `BuildConfig` field |
| `GOOGLE_OAUTH_CLIENT_ID` | Google OAuth Client ID | `BuildConfig` field |

Secrets must be rotated whenever a team member who had access to them leaves.

---

## 4. Environment Configurations

KiteWatch operates in three environments distinguished by `BuildConfig` fields:

| Environment | App ID Suffix | API Base URL | Logging | Strict Mode | Crash Reporting |
|---|---|---|---|---|---|
| Debug | `.debug` | Kite Connect prod (no staging) | Verbose (Timber debug tree) | `StrictMode.enableDefaults()` | None (logcat only) |
| Staging | `.staging` | Kite Connect prod | Warn+ (Timber) | Off | Crashlytics (if added later) |
| Release | (none) | Kite Connect prod | Error only | Off | Crashlytics (if added later) |

**Note:** Kite Connect API has no sandbox/staging environment for the free tier. All environments use the production Kite Connect API. Integration tests that hit the live API are **gated by a manual-run flag** and never run automatically on CI to avoid exhausting API rate limits.

---

## 5. Versioning Strategy

### 5.1 Version Code and Version Name

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        versionCode = 10 // Integer, monotonically increasing, never recycled
        versionName = "1.0.0" // SemVer: MAJOR.MINOR.PATCH
    }
}
```

**Version Code Rules:**

- Incremented by 1 for every released build (including patches).
- Never decremented. Android's package installer rejects downgrades.
- The version code is the canonical ordering signal; the version name is human-readable.

**Version Name Rules (SemVer):**

- `MAJOR`: Incremented for breaking changes to backup/restore schema (requires explicit user migration).
- `MINOR`: Incremented for new features that do not break existing data.
- `PATCH`: Incremented for bug fixes and performance improvements.
- Pre-release: `1.2.0-beta.1`, `1.2.0-rc.1` for staging distributions.

### 5.2 Git Tagging Convention

```
v1.0.0         # Production release
v1.0.1         # Patch release
v1.1.0-beta.1  # Beta (distributed as staging APK)
v1.1.0-rc.1    # Release candidate
```

Git tags are the **single source of truth** for what constitutes a release. The CI `release-build` workflow is triggered exclusively by `v*.*.*` tags on `main`.

### 5.3 Changelog Management

Maintained in `CHANGELOG.md` at the repository root. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

```markdown
## [1.1.0] - 2026-06-01
### Added
- CSV import for historical orders (Phase 2)
- Google Drive backup and restore

### Changed
- Background sync now configurable (multiple times per day)

### Fixed
- GTT update incorrectly skipped when buy order split across two lots
```

---

## 6. Release Process

### 6.1 Pre-Release Checklist

Prior to tagging a release, the release engineer must complete and record completion of every item:

**Code Readiness**

- [ ] All Phase N features merged to `develop` and `develop` merged to `main`
- [ ] Zero open `P0`/`P1` bugs in the milestone
- [ ] `versionCode` incremented and `versionName` updated in `app/build.gradle.kts`
- [ ] `CHANGELOG.md` updated with all changes since last release

**Build Verification**

- [ ] Full unit test suite passes on CI (`testReleaseUnitTest`)
- [ ] Staging APK built from the same commit as the release tag target
- [ ] Staging APK installed on a physical device and smoke-tested (checklist in §6.2)
- [ ] ProGuard/R8 obfuscation validated: no runtime crashes in staging from missing classes
- [ ] APK size within acceptable threshold (< 30 MB for Phase 1; document size trend per release)

**Database Migration**

- [ ] If `DATABASE_VERSION` incremented: migration SQL reviewed and integration-tested against the prior schema version
- [ ] Fallback destructive migration is disabled for release; a `MigrationTestHelper` test exists

**Security**

- [ ] No `BuildConfig.DEBUG = true` code paths active in release build
- [ ] No API keys or sensitive strings visible in `strings.xml` or resources
- [ ] APK signature verified via `apksigner verify --print-certs`
- [ ] SHA-256 hash computed and published alongside the APK

**Distribution**

- [ ] GitHub Release draft created with APK attachment and SHA-256 file
- [ ] Release notes reviewed and written in user-facing language
- [ ] In-app version manifest updated (see §6.4)

### 6.2 Staging Smoke Test Protocol

Execute on a physical Android device (API 26 minimum, API 34 recommended) with a real Zerodha account in test posture:

1. Fresh install of staging APK → complete onboarding → verify Zerodha auth succeeds.
2. Trigger manual order sync → verify orders appear in Orders screen.
3. Verify GTT auto-created or updated for each new buy order.
4. Open Holdings screen → verify all holdings present, profit target prices correct.
5. Navigate to Portfolio screen → verify P&L calculated, charts render.
6. Open Transactions screen → verify all entries present and filterable.
7. Open Settings → verify all preference toggles persist across app kill/restart.
8. Lock screen → verify biometric authentication gates the app.
9. (Phase 2+) Perform a Drive backup → restore on same device → verify data integrity.
10. Kill and relaunch app → verify no data loss, no crash on relaunch.

All findings during smoke testing are recorded. Any `P0`/`P1` finding blocks the release.

### 6.3 APK Distribution Mechanism

KiteWatch is distributed via GitHub Releases. The release page includes:

- `kitewatch-{versionCode}.apk` — the signed release APK.
- `kitewatch-{versionCode}-sha256.txt` — SHA-256 hash for integrity verification.
- Human-readable release notes.

Users download the APK directly from the GitHub Releases URL. The Guidebook within the app contains instructions for sideloading (enabling "Install from unknown sources" for the browser/file manager).

### 6.4 In-App Update Notification

Since there is no Play Store to deliver update notifications, KiteWatch implements a lightweight **version manifest check**:

- A static JSON file is hosted at a stable URL (e.g., GitHub Pages or a gist):

  ```json
  {
    "latest_version_code": 12,
    "latest_version_name": "1.1.0",
    "release_url": "https://github.com/org/kitewatch/releases/tag/v1.1.0",
    "release_notes": "Added CSV import and Drive backup.",
    "min_supported_version_code": 8
  }
  ```

- On app foreground (at most once per 24 hours), the app fetches this manifest.
- If `latest_version_code > current versionCode`: display a non-blocking banner — "Update available: v1.1.0. Tap to download."
- If `current versionCode < min_supported_version_code`: display a blocking alert — "This version is no longer supported. Please update." (graceful degradation: user can dismiss but cannot sync).
- Manifest fetch failure is silently ignored — no error state is surfaced to the user for this check.

---

## 7. Database Migration Rollout

### 7.1 Room Migration Strategy

Each `DATABASE_VERSION` increment requires a corresponding `Migration` object:

```kotlin
// core-database/src/main/kotlin/com/kitewatch/database/Migrations.kt

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE orders ADD COLUMN settlement_id TEXT
        """)
        database.execSQL("""
            CREATE INDEX idx_orders_settlement_id ON orders(settlement_id)
        """)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS gmail_filters (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                filter_query TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )
        """)
    }
}
```

### 7.2 Migration Chain Maintenance

All migration objects are added to `AppDatabase.buildDatabase()`:

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "kitewatch.db")
    .addMigrations(
        MIGRATION_1_2,
        MIGRATION_2_3,
        // New migrations appended here
    )
    .build()
```

**Rules:**

1. Migrations are **never removed** from the chain once published in a release.
2. Each migration must be tested via `MigrationTestHelper` against the exported schema JSON from the previous version.
3. Exported schema JSONs are committed to `core-database/schemas/` and version-controlled.

### 7.3 Schema Export Configuration

```kotlin
// core-database/build.gradle.kts
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}
```

Exported schemas serve as the migration test golden files and change-detection artifacts in PR diffs.

### 7.4 Migration Failure Handling

Room propagates migration failures as `IllegalStateException` during `build()`. The app catches this at the `Application` level:

```kotlin
// AppDatabase.kt
fun buildDatabase(context: Context): AppDatabase {
    return try {
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    } catch (e: IllegalStateException) {
        // Migration failed — database is in an unknown state
        // Log the error with full stack trace
        Timber.e(e, "Database migration failed — version mismatch")
        // Surface a fatal error screen to the user with options:
        // 1. Retry (cold restart)
        // 2. Contact support
        // DO NOT silently fallback to destructive migration in release
        throw DatabaseMigrationException("Migration failed: ${e.message}", e)
    }
}
```

A `fallbackToDestructiveMigration()` call is **explicitly forbidden** in release builds. A failed migration surfaces a fatal error screen with a support contact rather than silently deleting user data.

---

## 8. Rollback Strategy

KiteWatch is a local-first, sideloaded app. There is no server-side state to roll back. Rollback is entirely the user-side APK replacement operation.

### 8.1 APK Downgrade Constraint

Android's package manager **prohibits installing an APK with a lower `versionCode` over a higher one**. Therefore, true in-place APK rollback to a previous version is not possible without:

1. Uninstalling the current version (which destroys all app data unless backed up).
2. Installing the older APK fresh.
3. Restoring from a backup taken before the update.

This makes the backup system (Google Drive or local) the primary rollback mechanism.

### 8.2 Rollback Protocol

For a defective release where immediate rollback is required:

1. **Identify the defect:** Confirm whether it is a data-corruptive bug or a functional bug.
   - **Data-corruptive (migration bug, calculation error writing wrong values):** Requires a hotfix release with a corrective migration. APK rollback alone may not fix already-corrupted data.
   - **Functional (crash, UI failure, API regression):** A patch release (`PATCH` increment) is the fastest recovery path.

2. **For non-data-corruptive bugs:**
   - Ship a `PATCH` release ASAP.
   - Update the version manifest to point to the patch release.
   - Communicate via release notes.

3. **For data-corruptive bugs:**
   - Update the version manifest to mark the defective `versionCode` as unsupported (`min_supported_version_code` = defective + 1).
   - Ship a corrective release that includes a corrective data migration.
   - Users who have not yet been corrupted: the update fixes them before corruption.
   - Users already corrupted: the corrective migration attempts repair; if irreparable, guide them through backup restore flow.

4. **Communication:** GitHub Release notes for the defective version are updated with a prominent warning and link to the patch.

### 8.3 Hotfix Branch Process

```
main ──────────────── v1.0.0 ─────────────── v1.0.1 (hotfix)
                                                ▲
hotfix/crash-on-gtt-create ─────────────────────┘
```

Hotfixes are branched from the tagged release commit on `main`, not from `develop`. After the hotfix is tagged and released, it is merged back into both `main` and `develop`.

---

## 9. Monitoring and Alerting Setup

KiteWatch is a local-first Android app with no backend. Traditional APM (Application Performance Monitoring) tools that require a backend do not apply. The monitoring strategy relies on three mechanisms:

### 9.1 Crash Reporting (Optional, Phase 3)

Firebase Crashlytics can be integrated in the `release` build type. Key considerations given the privacy-first product:

- Crashlytics is **opt-in** and disclosed in the Privacy Policy.
- **No user-identifiable data is logged.** Stack traces, device model, and OS version only.
- Financial data, holdings, or order information must never appear in crash logs.
- Sensitive fields (instrument symbols, quantities, prices) must be scrubbed from any exception messages before they reach Crashlytics.
- Implementation gate: A `CrashReportingTree` subclass of `Timber.Tree` filters all log messages before forwarding to Crashlytics.

If the product owner rejects Crashlytics on privacy grounds (consistent with the privacy-first positioning), crash reporting is omitted entirely and replaced with:

- An in-app "Send Feedback" mechanism that attaches a filtered, user-reviewed local log file.
- The user exports the log file manually and emails it.

### 9.2 WorkManager Job Monitoring

Background task health is monitored by tracking `WorkInfo.State` in a local job audit table:

```sql
CREATE TABLE background_job_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_type TEXT NOT NULL,          -- 'ORDER_SYNC', 'GTT_AUTOMATION', etc.
    triggered_at INTEGER NOT NULL,
    completed_at INTEGER,
    result TEXT NOT NULL,            -- 'SUCCESS', 'FAILURE', 'RETRY'
    failure_reason TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 1
);
```

The Settings screen displays a "Last Sync Status" summary reading from this table. Persistent failures are surfaced as in-app alert banners.

### 9.3 API Error Rate Tracking

In-memory counters (reset per session) track Kite Connect API error rates:

```kotlin
object ApiHealthMonitor {
    private var consecutiveAuthFailures = 0
    private var consecutiveNetworkFailures = 0

    fun recordAuthFailure() {
        consecutiveAuthFailures++
        if (consecutiveAuthFailures >= 3) {
            // Emit event: force re-authentication flow
        }
    }
    fun recordSuccess() {
        consecutiveAuthFailures = 0
        consecutiveNetworkFailures = 0
    }
}
```

Three consecutive auth failures force a session expiry + re-auth screen rather than silently failing syncs.

---

## 10. Performance Baselines and Build Size Targets

| Metric | Target | Measurement Method |
|---|---|---|
| Release APK size | < 25 MB | `./gradlew assembleRelease` → APK Analyzer |
| Cold start time (API 28+) | < 2.5 seconds to main screen | Android Studio Profiler |
| Room query: holdings list | < 50ms for up to 100 holdings | JMH benchmark in `core-database` |
| P&L calculation: 1,000 orders | < 200ms on main thread equivalent | Domain layer unit benchmark |
| Background sync end-to-end | < 30 seconds for 50 new orders | WorkManager completion timestamp |
| Backup serialize + write | < 5 seconds for 10,000 records | `BackupEngine` benchmark test |

These baselines are checked quarterly and before each major release. Regressions > 20% over baseline trigger a performance investigation before shipping.
