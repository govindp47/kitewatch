# 02 — Technology Decisions

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-10

---

## Overview

This document records every major technology decision for KiteWatch. Each decision follows a structured format: selected option, alternatives considered, trade-offs, and justification. Decisions are grouped by category.

---

## 1. Programming Language

### Decision: **Kotlin** (JVM, targeting Android)

| Criterion | Assessment |
|---|---|
| **Selected** | Kotlin 2.0+ |
| **Alternatives** | Java, Kotlin Multiplatform (KMP) |
| **Trade-offs** | Kotlin has a marginally larger APK overhead vs Java (Kotlin stdlib: ~1.5MB). KMP was considered for future iOS/Desktop but rejected since the product scope is Android-exclusive with no planned cross-platform targets. |
| **Justification** | Kotlin is Google's recommended language for Android development. Coroutines provide first-class structured concurrency. Sealed interfaces/classes model the MVI pattern and error hierarchy exhaustively. Null safety eliminates an entire class of runtime crashes. The Android ecosystem (Jetpack, Compose, Room, Hilt) is Kotlin-first. |

### Kotlin Compiler Configuration

| Setting | Value | Reason |
|---|---|---|
| `jvmTarget` | `17` | Minimum for latest AGP; consistent with Android API 26+ deployment |
| `allWarningsAsErrors` | `true` | Enforce code quality |
| `optIn` | `ExperimentalCoroutinesApi`, `ExperimentalMaterial3Api` | Required for some Compose and Flow APIs |

---

## 2. UI Framework

### Decision: **Jetpack Compose** (Material 3)

| Criterion | Assessment |
|---|---|
| **Selected** | Jetpack Compose 1.7+ with Material 3 (Material Design 3) |
| **Alternatives** | XML Views + ViewBinding, XML Views + DataBinding |
| **Trade-offs** | Compose has a steeper learning curve and slightly higher initial compile times due to the Compose compiler plugin. XML Views have broader community resources and older device compatibility. Compose's declarative model introduces recomposition performance considerations that require awareness. |
| **Justification** | Compose is the modern Android UI standard with active Google investment. The declarative paradigm maps directly to the MVI architecture: immutable state → composable function → UI render. Material 3 provides built-in Dark/Light theme switching, dynamic color support, and accessibility primitives (touch target sizing, semantic descriptions). The app has no legacy XML code to migrate. Starting with Compose eliminates the future migration cost. Compose's testability (ComposeTestRule, semantic nodes) is superior to XML-based alternatives. |

### Compose Dependencies

| Library | Purpose |
|---|---|
| `androidx.compose.material3` | Core Material 3 components |
| `androidx.compose.ui` | Foundation compose UI |
| `androidx.navigation:navigation-compose` | Type-safe navigation |
| `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle()` |
| `androidx.paging:paging-compose` | Integration with Paging 3 for infinite scroll |

---

## 3. Architecture Pattern

### Decision: **MVI (Model-View-Intent) with Unidirectional Data Flow**

| Criterion | Assessment |
|---|---|
| **Selected** | MVI with `StateFlow<State>` + `Channel<SideEffect>` |
| **Alternatives** | MVVM (ViewModel + LiveData), MVI with Orbit-MVI library, MVI with custom Redux-like store |
| **Trade-offs** | MVI adds boilerplate (Intent/State sealed classes per screen) compared to MVVM. Orbit-MVI reduces this boilerplate but adds a third-party dependency. A custom Redux-like store is over-engineered for a single-platform app. |
| **Justification** | MVI's unidirectional data flow guarantees that the UI is always a pure function of a single immutable state object. This makes state management predictable, debuggable (state can be logged/replayed), and testable (assert on state snapshots rather than UI output). The financial-data nature of the app demands this level of state correctness — a user must never see stale or inconsistent P&L figures due to a race condition in state management. `StateFlow` provides conflated, lifecycle-aware state propagation without a third-party library. `Channel<SideEffect>` provides exactly-once delivery for navigation and toast events. |

### MVI Implementation Rules

1. **One ViewModel per screen.** No shared ViewModels across screens (use UseCases for shared logic).
2. **State is an immutable `data class`.** Mutations produce new instances via `copy()`.
3. **Intents are `sealed interface`.** Exhaustive `when` in the reducer ensures every intent is handled.
4. **Side effects for one-shot events only.** Navigation, snackbars, and dialogs that must not survive configuration change are emitted via `Channel`.
5. **No business logic in ViewModels.** ViewModels delegate to UseCases. The ViewModel only maps UseCase results to State/SideEffect.

---

## 4. Dependency Injection

### Decision: **Hilt** (Dagger-based, compile-time DI)

| Criterion | Assessment |
|---|---|
| **Selected** | Hilt 2.51+ |
| **Alternatives** | Koin (runtime DI), manual DI (factory pattern), Dagger 2 (without Hilt) |
| **Trade-offs** | Hilt has code-generation overhead (increased build time ~5-15%) and requires kapt/KSP. Koin is simpler to set up but performs DI resolution at runtime, which means DI errors surface as crashes at runtime rather than compile-time failures. Manual DI becomes unmanageable beyond ~20 injectable classes. Raw Dagger 2 provides equivalent compile-time safety but with significantly more boilerplate (Components, SubComponents, manual provision). |
| **Justification** | Hilt is the recommended DI framework for Android applications by Google. It integrates natively with ViewModel, WorkManager, and Navigation Compose. Compile-time verification catches missing bindings before the app runs — critical for a financial app where runtime crashes are unacceptable. The KSP-based Hilt compiler (replacing kapt) mitigates build time concerns. |

### Hilt Scoping Strategy

| Scope | Lifecycle | Contents |
|---|---|---|
| `@Singleton` | Application | Database, Retrofit clients, EncryptedSharedPreferences, MutexRegistry |
| `@ViewModelScoped` | ViewModel | UseCases (injected via constructor — effectively ViewModel-scoped when used only within one ViewModel) |
| `@ActivityRetainedScoped` | Activity retained | Shared session state (e.g., BiometricAuthState) |
| No scope (unscoped) | Per-injection site | Utilities, mappers, validators (stateless, cheap to create) |

---

## 5. Database Technology

### Decision: **Room (SQLite)** with optional **SQLCipher** for at-rest encryption

| Criterion | Assessment |
|---|---|
| **Selected** | Room 2.7+ backed by SQLite, with SQLCipher via `net.zetetic:android-database-sqlcipher` |
| **Alternatives** | Realm, ObjectBox, raw SQLite (no ORM), Room without encryption |
| **Trade-offs** | Room + SQLCipher increases APK size by ~3MB (SQLCipher native libraries). Realm offers built-in encryption and offline sync but uses a proprietary data format, making backup/export harder. ObjectBox has simpler APIs but less community adoption and no SQL query flexibility. Raw SQLite without Room loses compile-time query validation and reactive `Flow` integration. |
| **Justification** | Room is the Jetpack-recommended persistence library with first-class Kotlin coroutine and Flow support. SQLite is the most battle-tested embedded database on Android. Room's compile-time SQL verification catches query errors before runtime. The `@Transaction` annotation provides declarative transaction boundaries. Paging 3 integration via `PagingSource` from Room is seamless. SQLCipher adds transparent AES-256-CBC encryption with negligible performance overhead (~5-10% on reads), satisfying the security requirement for financial data at rest. |

### Room Configuration

| Setting | Value | Reason |
|---|---|---|
| WAL mode | Enabled (default in Room 2.x) | Allows concurrent reads during writes; critical for UI reads during background sync |
| Schema export | `exportSchema = true` | Enables migration validation tests |
| Destructive migration | `fallbackToDestructiveMigration = false` | Financial data must never be lost; all migrations are manually authored |
| Type converters | `BigDecimal ↔ Long`, `Instant ↔ String (ISO-8601)`, `Enum ↔ String` | Consistent serialization |

---

## 6. Networking Library

### Decision: **Retrofit 2** with **OkHttp 4** and **kotlinx.serialization**

| Criterion | Assessment |
|---|---|
| **Selected** | Retrofit 2.11+ + OkHttp 4.12+ + kotlinx.serialization-json 1.7+ |
| **Alternatives** | Ktor Client, Volley, bare OkHttp |
| **Trade-offs** | Ktor provides a pure Kotlin client but lacks Retrofit's mature interceptor ecosystem and Hilt integration patterns. Volley is aging with limited coroutine support. Bare OkHttp requires manual response parsing and API interface definition. |
| **Justification** | Retrofit provides a declarative, interface-based API definition pattern that makes endpoint contracts explicit and testable. OkHttp's interceptor pipeline enables clean separation of concerns (auth header injection, logging, retry, certificate pinning). kotlinx.serialization is a Kotlin-native, compile-time serializer that avoids the reflection overhead of Gson/Moshi and produces smaller code output. |

### OkHttp Interceptor Stack (ordered)

```
1. AuthInterceptor         — Injects Kite Connect access/API tokens into headers
2. WeekdayGuardInterceptor — For automated calls: no-ops if weekend (defense-in-depth)
3. RateLimitInterceptor    — Tracks 429 responses, backs off, emits RateLimited error
4. HttpLoggingInterceptor  — Level.BODY in debug, Level.BASIC in release (redacting sensitive fields)
5. CertificatePinningInterceptor — Pins Kite Connect API TLS certificates
```

### Network Timeout Configuration

| Parameter | Value | Reason |
|---|---|---|
| Connect timeout | 15 seconds | Kite Connect servers are generally responsive; 15s accommodates slow networks |
| Read timeout | 30 seconds | Some endpoints (GTT list, order history) may return larger payloads |
| Write timeout | 15 seconds | POST/PUT bodies are small (GTT creation) |
| Retry on connection failure | `true` (OkHttp default) | Handles transient TCP issues |

---

## 7. Serialization Strategy

### Decision: **kotlinx.serialization** for network; **Protocol Buffers** for backup files

| Criterion | Assessment |
|---|---|
| **Selected** | kotlinx.serialization-json for API responses; Protocol Buffers (protobuf-lite) for backup |
| **Alternatives** | Gson, Moshi, JSON for everything, CBOR/MessagePack |
| **Trade-offs** | kotlinx.serialization requires `@Serializable` annotations on every model class (minor boilerplate). Protobuf requires `.proto` file maintenance and code generation. JSON-for-everything is simpler but bloats backup file size and lacks schema evolution guarantees. |
| **Justification** | **API Layer:** kotlinx.serialization is Kotlin-native with compile-time safety. No reflection. Polymorphic serialization handles sealed class hierarchies natively. **Backup Layer:** Protocol Buffers provide compact binary serialization (~40% smaller than JSON), built-in schema versioning (field numbers), and forward/backward compatibility. This is critical for backup files that may be restored on a newer app version with schema changes. The protobuf-lite runtime is ~300KB — acceptable for the backup use case. |

### Backup File Format Specification

```
backup_file = HEADER + GZIP(PROTOBUF_PAYLOAD)

HEADER:
  magic_bytes: 4 bytes ("KTWB" — KiteWatch Backup)
  format_version: uint16                                  // backup format version
  schema_version: uint16                                  // Room DB schema version at time of backup
  account_id: length-prefixed UTF-8 string               // bound Zerodha account identifier
  created_at: int64 (Unix epoch millis)
  checksum: SHA-256 of the GZIP payload

PROTOBUF_PAYLOAD:
  message KiteWatchBackup {
    repeated Order orders = 1;
    repeated Transaction transactions = 2;
    repeated Holding holdings = 3;
    repeated FundEntry fund_entries = 4;
    repeated GttRecord gtt_records = 5;
    ChargeRateSnapshot charge_rates = 6;
    AppSettings settings = 7;
  }
```

---

## 8. Background Processing

### Decision: **WorkManager** (Jetpack)

| Criterion | Assessment |
|---|---|
| **Selected** | WorkManager 2.10+ with `CoroutineWorker` |
| **Alternatives** | AlarmManager + IntentService, JobScheduler, custom foreground service, Firebase JobDispatcher |
| **Trade-offs** | WorkManager is not precise to the second (system optimization may defer execution by minutes). AlarmManager provides exact timing but is not battery-friendly and is restricted on API 31+. Custom foreground services require persistent notification and are overkill for background data sync. |
| **Justification** | WorkManager is the Jetpack-standard for deferrable, guaranteed background work. It survives app kills and device reboots. It respects battery optimization (Doze mode) while guaranteeing eventual execution. Hilt integration via `@HiltWorker` provides DI in workers. Constraint-based scheduling (network required, charging) reduces failed execution attempts. The "4 PM order sync" is a best-effort schedule; delays of a few minutes are acceptable since executed orders are finalized by EOD. |

### Scheduling Implementation Detail

```kotlin
// Order Sync: Daily at user-configured time, approx
val syncRequest = PeriodicWorkRequestBuilder<OrderSyncWorker>(
    repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS
)
    .setInitialDelay(calculateDelayUntilNextExecution(userConfiguredTime), TimeUnit.MILLISECONDS)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    )
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .addTag("order_sync")
    .build()

workManager.enqueueUniquePeriodicWork(
    "order_sync_daily",
    ExistingPeriodicWorkPolicy.UPDATE,
    syncRequest
)
```

> **Multiple daily times:** For tasks with multiple user-configured times per day, each time slot is registered as a separate `OneTimeWorkRequest` chained with a `PeriodicWorkRequest` that re-schedules the next day's batch. This avoids WorkManager's limitation of one interval per periodic request.

---

## 9. Messaging / Event System

### Decision: **SharedFlow-based in-process event bus** (no external messaging)

| Criterion | Assessment |
|---|---|
| **Selected** | `MutableSharedFlow<AppEvent>` + Kotlin coroutines `Channel` |
| **Alternatives** | EventBus (greenrobot), LocalBroadcastManager, LiveData-based event wrapper, Kotlin Flow with custom bus library |
| **Trade-offs** | SharedFlow requires manual lifecycle management (collection must be scoped correctly). EventBus is simpler but introduces implicit coupling and is considered deprecated practice. LocalBroadcastManager is deprecated. |
| **Justification** | The app has no external messaging requirement (no push notifications, no server-sent events). All inter-component communication is in-process. A `SharedFlow<AppEvent>` event bus with `replay = 0` and `extraBufferCapacity = 64` provides: (a) decoupled communication between Workers and ViewModels, (b) no event loss when the buffer is consumed, (c) coroutine-native with no additional library. Events like `SyncCompleted`, `GttUpdated`, `FundMismatchDetected` are emitted by Workers/UseCases and consumed by active ViewModels. |

### Event Categories

```kotlin
sealed interface AppEvent {
    // Sync events
    data class OrderSyncCompleted(val newOrderCount: Int) : AppEvent
    data class OrderSyncFailed(val error: AppError) : AppEvent

    // Fund events
    data class FundReconciliationCompleted(val adjustment: BigDecimal?) : AppEvent
    data class FundMismatchDetected(val localBalance: BigDecimal, val remoteBalance: BigDecimal) : AppEvent

    // GTT events
    data class GttUpdated(val stockId: String) : AppEvent
    data class GttManualOverrideDetected(val stockId: String) : AppEvent

    // Backup events
    data class BackupCompleted(val destination: BackupDestination) : AppEvent
    data class BackupFailed(val error: AppError) : AppEvent

    // System events
    data object ChargeRatesRefreshed : AppEvent
    data object SessionExpired : AppEvent
}
```

---

## 10. Logging Framework

### Decision: **Timber** with custom `FileLoggingTree`

| Criterion | Assessment |
|---|---|
| **Selected** | Timber 5.0+ |
| **Alternatives** | SLF4J + Logback (Android), raw `android.util.Log`, custom Logger |
| **Trade-offs** | Timber adds a lightweight dependency (~50KB). SLF4J + Logback is heavier and designed for server-side. Raw `Log` lacks tree-based extensibility and auto-tag generation. |
| **Justification** | Timber's `Tree` abstraction allows plugging different logging targets per build type: `DebugTree` for debug builds (logcat), `FileLoggingTree` for release builds (local file). Auto-generates TAG from calling class. The API mirrors `android.util.Log` so migration cost is zero. No SDKs are required (no Crashlytics, no remote logging) — consistent with the no-third-party-analytics requirement. |

### FileLoggingTree Configuration

| Parameter | Value |
|---|---|
| Log file location | `context.filesDir/logs/` |
| Max file size | 5 MB |
| Max file count | 2 (rotating) |
| Format | `[LEVEL] YYYY-MM-DD HH:mm:ss.SSS [TAG] message` |
| Sensitive data | Redacted: API tokens, account IDs truncated (`****XY12`) |
| Flush policy | On every `ERROR`; periodic 30-second flush for lower levels |

---

## 11. Monitoring Strategy

### Decision: **Local-only monitoring** via `SyncEventLog` + in-app diagnostics

| Criterion | Assessment |
|---|---|
| **Selected** | Room-based `sync_event_log` table + in-app diagnostic screen (developer mode) |
| **Alternatives** | Firebase Crashlytics + Performance, Sentry, custom analytics backend |
| **Trade-offs** | No remote crash reporting means crash diagnosis depends on user-reported log files. This is acceptable for a sideloaded app with a technically-capable user base. The alternative (any remote SDK) violates the product's strict no-third-party-data requirement. |
| **Justification** | The product specification explicitly prohibits third-party analytics SDKs, crash reporters, and advertising SDKs. All monitoring is therefore local-only. The `sync_event_log` table (defined in `01_SYSTEM_ARCHITECTURE.md`) captures every background operation with status, duration, and error details. An in-app diagnostic screen (accessible via a hidden gesture or developer toggle in Settings) can display sync history, last N log file entries, and database statistics. Users can export log files for manual sharing if they need support. |

### Crash Handling (No Remote Reporting)

```kotlin
class KiteWatchCrashHandler : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // 1. Write crash details to a crash log file
        CrashLogWriter.write(throwable)
        // 2. Delegate to default handler (system crash dialog)
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
```

Crash log files are written to `context.filesDir/crashes/` and surfaced in the diagnostic screen on next app launch.

---

## 12. CI/CD Tools

### Decision: **GitHub Actions** for CI; **Manual release** for APK distribution

| Criterion | Assessment |
|---|---|
| **Selected** | GitHub Actions (CI), Gradle signing (release APK), manual distribution (APK file sharing) |
| **Alternatives** | Bitrise, CircleCI, GitLab CI, Firebase App Distribution |
| **Trade-offs** | GitHub Actions requires YAML workflow authoring and has limited free tier minutes for private repos (2,000 min/month). Firebase App Distribution provides OTA updates but requires a Firebase project (adding a third-party dependency). |
| **Justification** | GitHub Actions integrates directly with the GitHub repository. No external CI service configuration needed. The app is sideloaded, not published to Play Store, so there is no need for a Play Console integration. APK signing is done locally via Gradle's `signingConfigs` with keystore stored securely (not in version control). Release APKs are generated as GitHub Actions artifacts or via a local `./gradlew assembleRelease` command. No OTA update mechanism is implemented (users sideload new versions manually). |

### CI Pipeline Stages

```yaml
# .github/workflows/ci.yml
jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
      - name: Set up JDK 17
      - name: Gradle cache
      - name: Lint (ktlint + Android Lint)
      - name: Unit tests (all modules)
      - name: Instrumentation tests (Room migrations, UI tests via emulator)
      - name: Build debug APK
      - name: Build release APK (on tagged commits only)
      - name: Upload APK artifact
```

---

## 13. Code Quality Enforcement Tools

### Decision: **ktlint** + **detekt** + **Android Lint** + **Compose Compiler Metrics**

| Tool | Purpose | Configuration |
|---|---|---|
| **ktlint** (0.51+) | Kotlin code style enforcement (official Kotlin coding conventions) | Integrated via `org.jlleitschuh.gradle.ktlint` plugin. Run on every PR. Formatting auto-fix available via `ktlintFormat`. |
| **detekt** (1.23+) | Static analysis for code smells, complexity metrics, and custom rules | Custom rule set: max function length (50 lines), max file length (400 lines), max cyclomatic complexity (10). Suppression requires a comment justification. |
| **Android Lint** | Android-specific checks (missing permissions, deprecated API usage, accessibility issues) | `lintOptions { abortOnError = true; warningsAsErrors = true }` for release builds. Custom lint rules for enforcing BigDecimal usage instead of Double for money. |
| **Compose Compiler Metrics** | Recomposition stability analysis | Generated via `-P composeCompilerReports=true`. Reviewed manually for stability regressions on each release. |

### Pre-Commit Hook

```bash
#!/bin/sh
# .githooks/pre-commit
./gradlew ktlintCheck detekt --daemon
```

---

## 14. Charting Library

### Decision: **Vico** (Jetpack Compose native charting)

| Criterion | Assessment |
|---|---|
| **Selected** | Vico 2.0+ (`com.patrykandpatrick.vico`) |
| **Alternatives** | MPAndroidChart (View-based), AnyChart, Chart.kt (YCharts), custom Canvas drawing |
| **Trade-offs** | Vico is a newer library with a smaller community than MPAndroidChart. MPAndroidChart requires `AndroidView` interop in Compose. AnyChart has licensing costs. Custom Canvas is fully flexible but extremely time-consuming to build, test, and maintain for four chart types (pie, line, bar, stacked). |
| **Justification** | Vico is built specifically for Jetpack Compose with a declarative API. It supports all required chart types: line charts (cumulative P&L), column/bar charts (monthly P&L), and compositions. Pie charts may require a custom Canvas composable (Vico focuses on cartesian charts) — this is a bounded effort. Vico respects Material 3 theming natively (Dark/Light mode chart colors). No View interop overhead. Active maintenance as of 2026. |

### Chart Types to Implementation Mapping

| Chart | Library | Notes |
|---|---|---|
| Cumulative P&L line graph | Vico `CartesianChart` with `LineCartesianLayer` | Date-keyed X-axis, INR Y-axis |
| Monthly P&L bar chart | Vico `CartesianChart` with `ColumnCartesianLayer` | Month labels on X-axis |
| Charges breakdown chart | Vico `CartesianChart` with stacked `ColumnCartesianLayer` | Brokerage vs other charges |
| P&L vs Charges pie chart | Custom `Canvas` composable | Vico lacks pie chart support; custom implementation with `drawArc` + animation |

---

## 15. CSV Parsing Library

### Decision: **Apache Commons CSV**

| Criterion | Assessment |
|---|---|
| **Selected** | Apache Commons CSV 1.11+ |
| **Alternatives** | OpenCSV, kotlin-csv, manual parsing |
| **Trade-offs** | Apache Commons CSV adds ~130KB to APK. kotlin-csv is lighter but has less robust edge-case handling (quoted fields with newlines, BOM markers). Manual parsing is error-prone for edge cases. OpenCSV is roughly equivalent but has a thicker API surface. |
| **Justification** | Apache Commons CSV handles RFC 4180 CSV parsing robustly: quoted fields, escaped quotes, custom delimiters, headers with whitespace. KiteWatch's CSV import is a critical data path (historical order import) — parsing errors here mean financial data loss or corruption. Apache Commons CSV provides `CSVParser.iterator()` for streaming (memory-efficient for large files) and `CSVRecord` for type-safe field access by header name. |

---

## 16. Excel Export Library

### Decision: **Apache POI (poi-ooxml-lite)**

| Criterion | Assessment |
|---|---|
| **Selected** | Apache POI 5.3+ (OOXML-lite variant) |
| **Alternatives** | JExcelApi, FastExcel, custom CSV-as-Excel |
| **Trade-offs** | Apache POI is the heaviest option (~5MB AAR/JAR). The `poi-ooxml-lite` variant reduces this significantly. FastExcel is lighter but has less formatting control. CSV export avoids the library entirely but does not meet the "Excel export" requirement. |
| **Justification** | The product specification requires Excel format export (`.xlsx`). Apache POI is the de facto Java/Kotlin library for reading and writing Office documents. The `poi-ooxml-lite` variant strips unused features, keeping APK impact acceptable. POI supports: cell formatting (currency, dates), multiple worksheets (Orders, Transactions, Holdings as separate sheets), and streaming write mode (`SXSSFWorkbook`) for memory efficiency with large datasets. |

---

## 17. Biometric Authentication Library

### Decision: **AndroidX Biometric API** (`androidx.biometric`)

| Criterion | Assessment |
|---|---|
| **Selected** | `androidx.biometric:biometric:1.2+` (BiometricPrompt) |
| **Alternatives** | FingerprintManager (deprecated), custom biometric implementation, third-party biometric SDK |
| **Trade-offs** | AndroidX Biometric has no trade-offs for this use case. It wraps all biometric hardware types (fingerprint, face, iris) behind a unified API and provides automatic fallback to device credential (PIN/pattern/password). |
| **Justification** | AndroidX Biometric is the standard Android API for biometric authentication. It handles: hardware availability detection, enrollment status checks, `BiometricPrompt.PromptInfo` configuration with `BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL` fallback, and cancellation lifecycle. No third-party dependency needed. Integrates naturally with Hilt for injection into the auth manager. |

### BiometricPrompt Configuration

```kotlin
val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("KiteWatch")
    .setSubtitle("Authenticate to access your portfolio")
    .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG
        or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )
    .build()
```

> `setNegativeButtonText()` is omitted when `DEVICE_CREDENTIAL` is included as an allowed authenticator (AndroidX requirement).

---

## 18. Encrypted Storage

### Decision: **EncryptedSharedPreferences** (AndroidX Security Crypto) for tokens; **SQLCipher** for database

| Criterion | Assessment |
|---|---|
| **Selected** | `androidx.security:security-crypto:1.1+` for key-value secrets; SQLCipher for database encryption |
| **Alternatives** | Android Keystore alone (manual encryption), Tink library, Room without encryption |
| **Trade-offs** | SQLCipher adds ~3MB APK size and ~5-10% read performance overhead. EncryptedSharedPreferences has a slightly slower read/write than regular SharedPreferences (Keystore-backed key derivation). Not encrypting the database is simpler but leaves financial data vulnerable if device storage is extracted. |
| **Justification** | **Tokens (API key, access token, account ID):** These are small, infrequently accessed secrets. EncryptedSharedPreferences uses AES-256-SIV for key encryption and AES-256-GCM for value encryption, backed by Android Keystore. This is the recommended approach for sensitive key-value data. **Database:** SQLCipher provides transparent, page-level AES-256-CBC encryption of the entire SQLite database. Combined with Android's file-based encryption (FBE), this provides defense-in-depth: even if device encryption is bypassed, the database content remains encrypted. The 5-10% performance overhead is negligible for this app's query patterns. |

### Key Management

| Key | Storage | Algorithm | Rotation |
|---|---|---|---|
| EncryptedSharedPreferences master key | Android Keystore | AES-256 (MasterKey.KeyScheme.AES256_GCM) | Generated once, persisted in Keystore until app uninstall |
| SQLCipher passphrase | EncryptedSharedPreferences (encrypted at rest) | Generated randomly (32 bytes) at first DB creation | Never rotated (rotation requires re-encryption of entire DB) |

---

## 19. Preferences Storage

### Decision: **Jetpack DataStore (Preferences)** for non-sensitive settings

| Criterion | Assessment |
|---|---|
| **Selected** | `androidx.datastore:datastore-preferences:1.1+` |
| **Alternatives** | SharedPreferences, Room (settings table), Proto DataStore |
| **Trade-offs** | DataStore has a coroutine-based API which adds slight complexity vs SharedPreferences' synchronous API. Proto DataStore provides typed schemas but is heavier than Preferences DataStore for simple key-value settings. |
| **Justification** | DataStore is the Jetpack successor to SharedPreferences. It provides: (a) coroutine and Flow integration for reactive settings updates (e.g., theme change immediately reflects in Compose), (b) transactional writes (no partial updates), (c) exception handling (no runtime crashes from XML corruption). Non-sensitive settings stored: theme preference, schedule configurations, tolerance threshold, notification toggle, last sync timestamps, charge rate refresh interval. |

---

## 20. Google API Integration

### Decision: **Google API Client Libraries for Android** (Gmail API v1, Drive API v3)

| Criterion | Assessment |
|---|---|
| **Selected** | `com.google.api-client:google-api-client-android`, `com.google.apis:google-api-services-gmail`, `com.google.apis:google-api-services-drive` |
| **Alternatives** | Raw REST calls via Retrofit, third-party Gmail/Drive wrappers |
| **Trade-offs** | Google's official client libraries are large (~2MB combined) and pull in transitive dependencies. Raw REST would be lighter but requires manual OAuth token management, request signing, and error code handling. |
| **Justification** | Official Google client libraries provide: (a) built-in OAuth2 token refresh handling via `GoogleAccountCredential`, (b) typed request/response models, (c) correct handling of Google API pagination, batch requests, and error codes, (d) tested compatibility with Android's account management framework. The APK size overhead is acceptable for a sideloaded app with no Play Store size constraints. |

### OAuth2 Scopes

| Integration | Scope | Justification |
|---|---|---|
| Gmail (fund detection) | `https://www.googleapis.com/auth/gmail.readonly` | Read-only access; the app never modifies, sends, or deletes emails |
| Google Drive (backup) | `https://www.googleapis.com/auth/drive.file` | Access only files created by the app; cannot see or modify other Drive files |

> **Privacy note:** `drive.file` scope is preferred over `drive` scope because it restricts access to only files the app has created. This minimizes the permission footprint.

---

## 21. Image / Asset Strategy

### Decision: **No image loading library** required

| Criterion | Assessment |
|---|---|
| **Selected** | No image library (Coil, Glide, Picasso) |
| **Alternatives** | Coil (Compose-native image loader) |
| **Justification** | KiteWatch does not display any remote images (no stock logos, no user avatars, no web content). All icons use Material Icons (bundled with Compose Material 3). Charts are rendered programmatically via Canvas / Vico. The app is text and data-centric. Adding an image loading library would be unnecessary dependency overhead with zero use case. If stock logos are added in a future enhancement, Coil would be the recommended choice (Compose-native, coroutine-based). |

---

## 22. Testing Frameworks

### Decision: **JUnit 5** + **Turbine** + **MockK** + **Compose Test** + **Room Testing**

| Framework | Purpose | Justification |
|---|---|---|
| JUnit 5 | Unit test framework | Parameterized tests for charge calculation, P&L edge cases. `@Nested` for organized test classes. |
| Turbine | Flow/StateFlow testing | Asserts on `StateFlow` emissions in ViewModels. `test {}` block with `awaitItem()` provides deterministic Flow testing. |
| MockK | Mocking | Kotlin-native mocking with coroutine support. Mocks suspend functions, verifies call order. |
| Compose UI Test | UI/integration tests | `ComposeTestRule` with semantic matchers. Validates screen rendering, navigation, and user interaction flows. |
| Room Testing | Migration tests | `MigrationTestHelper` validates every schema migration path. Ensures no data loss on app updates. |
| Robolectric | ViewModel + UseCase integration tests | Runs Android-dependent tests on JVM without emulator. Faster CI execution. |

> Full testing strategy details in `09_TESTING_STRATEGY.md`.

---

## 23. Minimum API Level & Target SDK

### Decision: **minSdk 26** (Android 8.0), **targetSdk 35** (Android 15)

| Criterion | Assessment |
|---|---|
| **Selected** | minSdk 26, targetSdk 35, compileSdk 35 |
| **Alternatives** | minSdk 24 (Android 7.0), minSdk 28 (Android 9.0) |
| **Trade-offs** | minSdk 26 excludes devices running Android 7.x and below (~2% of active Android devices in India as of 2026). minSdk 24 would require Java 8 desugaring for `java.time` APIs (still possible but adds build complexity). minSdk 28 provides file-based encryption (FBE) guarantee but excludes ~8% of devices. |
| **Justification** | minSdk 26 provides: (a) native `java.time` API support (critical for date/time handling without desugaring), (b) `autofill` framework support, (c) adaptive icons, (d) background execution limits (aligns with WorkManager's behavior). The target user base (technically-capable Zerodha investors willing to sideload APKs) overwhelmingly uses modern devices. targetSdk 35 ensures compatibility with the latest Android security and permission models. |

---

## 24. Build System

### Decision: **Gradle (Kotlin DSL)** with **Convention Plugins**

| Criterion | Assessment |
|---|---|
| **Selected** | Gradle 8.8+ with Kotlin DSL (`.gradle.kts`), Version Catalog (`libs.versions.toml`), convention plugins in `build-logic/` |
| **Alternatives** | Gradle Groovy DSL, Bazel |
| **Trade-offs** | Kotlin DSL has slower first-build configuration time than Groovy but provides IDE auto-completion and type safety. Bazel is faster for very large monorepos but has no Jetpack/AGP plugin ecosystem. |
| **Justification** | Kotlin DSL provides compile-time checking of build configuration — critical when managing 15+ Gradle modules. Convention plugins in `build-logic/` centralize shared build logic (Android library config, Compose config, testing config) and eliminate copy-paste across `build.gradle.kts` files. Version Catalog (`libs.versions.toml`) provides a single source of truth for all dependency versions, preventing version drift across modules. |

### Build Variants

| Variant | Config |
|---|---|
| `debug` | SQLCipher passphrase: hardcoded test value. Logging: verbose logcat. Compose recomposition highlights enabled. |
| `release` | SQLCipher passphrase: generated per-install. Logging: file-only, no logcat. ProGuard/R8 minification enabled. Debuggable: false. |

---

## 25. ProGuard / R8 Configuration

### Decision: **R8** (full mode) with manual keep rules for serialization and reflection

| Criterion | Assessment |
|---|---|
| **Selected** | R8 full mode (`android.enableR8.fullMode = true`) |
| **Justification** | R8 provides code shrinking (~30-40% APK size reduction), obfuscation (protecting business logic from reverse engineering), and optimization. Critical keep rules: kotlinx.serialization model classes (must retain `@Serializable` generated serializers), Retrofit API interfaces (reflection-based proxy), Room entity classes (reflection for schema generation is at compile-time — safe to obfuscate). |

### Key Keep Rules

```proguard
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.kitewatch.**$$serializer { *; }
-keepclassmembers class com.kitewatch.** {
    *** Companion;
}

# Retrofit
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface * { @retrofit2.http.* <methods>; }

# SQLCipher
-keep class net.sqlcipher.** { *; }

# Protobuf
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
```

---

## 26. Version Control Strategy

### Decision: **Git** with **trunk-based development**

| Criterion | Assessment |
|---|---|
| **Selected** | Git (GitHub), trunk-based development with short-lived feature branches |
| **Alternatives** | GitFlow, GitHub Flow |
| **Trade-offs** | Trunk-based development requires disciplined CI and feature flags for incomplete features. GitFlow provides more structure but is heavyweight for a small team. |
| **Justification** | Small team (1-3 engineers). Short-lived branches (< 2 days) merged via PR to `main`. Release branches cut from `main` for APK builds. No long-lived feature branches avoids merge conflicts and integration issues. Feature flags implemented via build variants or runtime toggles (stored in DataStore) for incomplete features. |

### Branch Naming Convention

```
feature/KW-{ticket-number}-{short-description}
bugfix/KW-{ticket-number}-{short-description}
release/{version}
```

### Commit Convention

```
type(scope): description

# Examples:
feat(orders): add CSV import validation for historical orders
fix(gtt): prevent duplicate GTT creation during concurrent sync
refactor(domain): extract ChargeCalculator into standalone UseCase
test(holdings): add verification flow edge case tests
```

---

## 27. Decision Summary Matrix

| Category | Decision | Key Rationale |
|---|---|---|
| Language | Kotlin 2.0+ | Google-recommended, coroutines, null safety |
| UI | Jetpack Compose + Material 3 | Declarative, MVI-native, theme support |
| Architecture | MVI | Unidirectional data flow, state predictability |
| DI | Hilt | Compile-time safety, Jetpack integration |
| Database | Room + SQLCipher | Compile-time SQL checks, encrypted at-rest |
| Network | Retrofit + OkHttp + kotlinx.serialization | Declarative API, interceptor pipeline, no reflection |
| Serialization | kotlinx.serialization (API) + Protobuf (backup) | Compile-time safety + compact versioned backups |
| Background | WorkManager | Guaranteed execution, battery-friendly |
| Events | SharedFlow | Coroutine-native, no external dependency |
| Logging | Timber | Extensible, lightweight, no remote dependency |
| Monitoring | Local-only (SyncEventLog) | No third-party analytics (privacy requirement) |
| CI/CD | GitHub Actions | Repository-integrated, free tier |
| Code Quality | ktlint + detekt + Android Lint | Multi-layered static analysis |
| Charts | Vico + custom Canvas | Compose-native, lightweight |
| CSV | Apache Commons CSV | RFC 4180 compliant, streaming |
| Excel | Apache POI (ooxml-lite) | Full .xlsx support, streaming writes |
| Biometric | AndroidX Biometric | Unified API, device credential fallback |
| Encrypted Storage | EncryptedSharedPreferences + SQLCipher | Defense-in-depth for financial data |
| Preferences | Jetpack DataStore | Reactive, coroutine-native, transactional |
| Google APIs | Official client libraries | OAuth handling, typed models |
| Min SDK | 26 (Android 8.0) | Native java.time, wide device coverage |
| Build | Gradle Kotlin DSL + Convention Plugins | Type-safe, centralized, version catalog |
| VCS | Git (trunk-based) | Simple for small team, fast iteration |
| Testing | JUnit 5 + Turbine + MockK + Compose Test | Comprehensive coverage across all layers |
