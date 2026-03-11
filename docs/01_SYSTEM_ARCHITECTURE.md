# 01 — System Architecture

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-10

---

## 1. High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            ANDROID DEVICE                                   │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      PRESENTATION LAYER                               │  │
│  │                                                                       │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │  │
│  │  │Portfolio  │ │Holdings  │ │ Orders   │ │Transact. │ │Settings  │   │  │
│  │  │  Screen   │ │  Screen  │ │  Screen  │ │  Screen  │ │  Screen  │   │  │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘   │  │
│  │       │             │            │             │            │          │  │
│  │  ┌────▼─────────────▼────────────▼─────────────▼────────────▼───────┐ │  │
│  │  │                    ViewModels (MVI Pattern)                       │ │  │
│  │  │  PortfolioVM │ HoldingsVM │ OrdersVM │ TransactionsVM │ etc.     │ │  │
│  │  └──────────────────────────┬───────────────────────────────────────┘ │  │
│  └─────────────────────────────┼─────────────────────────────────────────┘  │
│                                │                                             │
│  ┌─────────────────────────────▼─────────────────────────────────────────┐  │
│  │                        DOMAIN LAYER                                    │  │
│  │                                                                       │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌─────────────┐  │  │
│  │  │ Order Sync   │ │ Fund Mgmt    │ │ GTT Engine   │ │ P&L Engine  │  │  │
│  │  │  UseCase     │ │  UseCase     │ │  UseCase     │ │  UseCase    │  │  │
│  │  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬──────┘  │  │
│  │         │                │                │                │          │  │
│  │  ┌──────▼────────────────▼────────────────▼────────────────▼──────┐   │  │
│  │  │              Domain Models & Business Rules                     │   │  │
│  │  │  Order │ Holding │ Transaction │ Fund │ GTT │ ChargeRate │ etc │   │  │
│  │  └───────────────────────────────────────────────────────────────┘   │  │
│  └─────────────────────────────┬─────────────────────────────────────────┘  │
│                                │                                             │
│  ┌─────────────────────────────▼─────────────────────────────────────────┐  │
│  │                         DATA LAYER                                     │  │
│  │                                                                       │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌─────────────┐  │  │
│  │  │  Repository   │ │  Repository  │ │  Repository  │ │ Repository  │  │  │
│  │  │  (Orders)     │ │  (Funds)     │ │  (GTT)       │ │ (Holdings)  │  │  │
│  │  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬──────┘  │  │
│  │         │                │                │                │          │  │
│  │  ┌──────▼────────────────▼────────────────▼────────────────▼──────┐   │  │
│  │  │                    Room Database (SQLite)                       │   │  │
│  │  │  DAOs │ Entities │ TypeConverters │ Migrations                  │   │  │
│  │  └───────────────────────────────────────────────────────────────┘   │  │
│  │                                                                       │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                  │  │
│  │  │ Kite Connect │ │  Gmail API   │ │ Google Drive │                  │  │
│  │  │ Remote Source │ │ Remote Source│ │ Remote Source│                  │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘                  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      INFRASTRUCTURE LAYER                              │  │
│  │                                                                       │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌─────────────┐  │  │
│  │  │  WorkManager  │ │  Biometric   │ │   Backup     │ │  CSV/Excel  │  │  │
│  │  │  (Scheduler)  │ │  Auth Mgr    │ │   Engine     │ │  Engine     │  │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └─────────────┘  │  │
│  │                                                                       │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                  │  │
│  │  │  DataStore    │ │  Crypto /    │ │  Logger      │                  │  │
│  │  │ (Preferences) │ │  EncryptedSP │ │  (Timber)    │                  │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘                  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────┐
          ▼                         ▼                         ▼
   ┌──────────────┐         ┌──────────────┐         ┌──────────────┐
   │ Zerodha Kite │         │  Gmail API   │         │ Google Drive │
   │ Connect API  │         │  (Google)    │         │  API (User)  │
   └──────────────┘         └──────────────┘         └──────────────┘
```

---

## 2. System Component Breakdown

### 2.1 Presentation Layer

| Component | Responsibility |
|---|---|
| Composable Screens | Stateless UI rendering: Portfolio, Holdings, Orders, Transactions, GTT, Settings, Onboarding. Each screen observes a single ViewModel. |
| ViewModels (MVI) | Receives user Intents, delegates to UseCases, reduces State. Emits immutable UI state and one-shot Side Effects. |
| Navigation Host | Jetpack Navigation Compose manages bottom-bar tabs and nested routes (e.g., Holdings → Stock Detail). |
| Theme Engine | Material 3 `DynamicColorScheme` with manual Dark/Light toggle stored in DataStore. |
| Shared UI Components | Design-system components: AlertBanner, SkeletonLoader, FilterChipGroup, EmptyStateWidget, ConfirmationDialog. |

### 2.2 Domain Layer

| Component | Responsibility |
|---|---|
| Use Cases | Orchestrate multi-repository business logic. Each UseCase is single-responsibility (e.g., `SyncOrdersUseCase`, `ReconcileFundUseCase`, `PlaceGttUseCase`, `CalculatePnlUseCase`). |
| Domain Models | Pure Kotlin data classes with no framework dependencies. Encapsulate business invariants (e.g., a `Holding` cannot have negative quantity). |
| Business Rule Validators | Stateless functions validating domain invariants: CSV format compliance, account-ID binding match, duplicate detection, tolerance checks. |
| Charge Calculator | Deterministic, stateless engine accepting `ChargeRateSnapshot` + `OrderDetails` → `ChargeBreakdown`. |

### 2.3 Data Layer

| Component | Responsibility |
|---|---|
| Repositories | Aggregate data from local (Room DAOs) and remote (API clients) sources. Expose `Flow<T>` for reactive reads, `suspend fun` for writes. |
| Room Database | Single SQLite database with DAOs per entity group. Manages migrations, type converters, and FTS indexes. |
| Remote Data Sources | Retrofit-based HTTP clients for Kite Connect. Google API client wrappers for Gmail and Drive. Isolated behind interface contracts so they can be faked in tests. |
| DataStore / EncryptedSP | Preferences DataStore for non-sensitive config (theme, schedules, tolerance). EncryptedSharedPreferences for sensitive tokens (Kite access token, bound account ID). |

### 2.4 Infrastructure Layer

| Component | Responsibility |
|---|---|
| WorkManager Scheduler | Schedules and dispatches background Workers (OrderSyncWorker, ReconciliationWorker, ChargeRateRefreshWorker, BackupWorker). Enforces Mon–Fri constraint and handles retry policies. |
| Biometric Auth Manager | Wraps AndroidX Biometric API. Manages BiometricPrompt lifecycle, fallback to device credential, and lock-timeout logic. |
| Backup Engine | Serializes database subsets into a versioned protobuf/JSON file. Streams upload to Google Drive or local file system. Handles restore with merge-only semantics. |
| CSV/Excel Engine | Parses CSV imports (Apache Commons CSV), validates row schemas, performs duplicate detection. Generates Excel exports (Apache POI or equivalent lightweight library). |
| Logger | Timber-based structured logging with local log file rotation for post-mortem debugging. No remote logging. |

---

## 3. Clean Architecture Layers

```
┌─────────────────────────────────────────┐
│          Presentation (UI / VM)         │  ← Depends on Domain
├─────────────────────────────────────────┤
│          Domain (UseCases / Models)     │  ← Pure Kotlin, no framework deps
├─────────────────────────────────────────┤
│          Data (Repos / DAOs / API)      │  ← Implements Domain interfaces
├─────────────────────────────────────────┤
│     Infrastructure (Workers / Auth)     │  ← Platform-specific services
└─────────────────────────────────────────┘
```

**Dependency Rule:** Dependencies point inward only. Domain has zero knowledge of Data, Presentation, or Infrastructure. Data and Infrastructure implement Domain-defined interfaces (Repository contracts, Engine contracts).

**Module Boundary Enforcement:** Gradle multi-module project structure physically prevents illegal imports via module visibility rules. Details in `05_APPLICATION_STRUCTURE.md`.

---

## 4. Module Boundaries

| Gradle Module | Layer | Depends On |
|---|---|---|
| `:app` | Entry point | `:feature-*`, `:core-*`, `:infra-*` |
| `:feature-portfolio` | Presentation | `:core-domain`, `:core-ui` |
| `:feature-holdings` | Presentation | `:core-domain`, `:core-ui` |
| `:feature-orders` | Presentation | `:core-domain`, `:core-ui` |
| `:feature-transactions` | Presentation | `:core-domain`, `:core-ui` |
| `:feature-gtt` | Presentation | `:core-domain`, `:core-ui` |
| `:feature-settings` | Presentation | `:core-domain`, `:core-ui` |
| `:feature-onboarding` | Presentation | `:core-domain`, `:core-ui` |
| `:core-domain` | Domain | None (pure Kotlin) |
| `:core-data` | Data | `:core-domain` |
| `:core-network` | Data/Remote | `:core-domain` |
| `:core-database` | Data/Local | `:core-domain` |
| `:core-ui` | Shared UI | `:core-domain` (for model display) |
| `:infra-worker` | Infrastructure | `:core-domain`, `:core-data` |
| `:infra-auth` | Infrastructure | `:core-domain` |
| `:infra-backup` | Infrastructure | `:core-domain`, `:core-data` |
| `:infra-csv` | Infrastructure | `:core-domain` |

---

## 5. Data Flow Lifecycle

### 5.1 Order Sync Data Flow (Primary Critical Path)

```
[WorkManager Trigger or Manual Refresh]
         │
         ▼
  OrderSyncWorker
         │
         ▼
  SyncOrdersUseCase.execute()
         │
         ├── 1. KiteConnectRepository.fetchTodaysOrders()
         │       └── HTTP GET /orders → filter equity delivery, executed only
         │
         ├── 2. KiteConnectRepository.fetchHoldings()
         │       └── HTTP GET /holdings
         │
         ├── 3. HoldingsVerifier.verify(remoteHoldings, localHoldings)
         │       ├── MATCH → continue
         │       └── MISMATCH → abort, emit MismatchError with per-stock diff
         │
         ├── 4. [MATCH path] OrderRepository.insertNewOrders(orders)
         │       └── Inside DB transaction
         │
         ├── 5. HoldingsRepository.updateHoldings(newHoldings)
         │       └── Recalculate average buy price, update quantities
         │
         ├── 6. ChargeCalculator.calculate(orders, chargeRates)
         │       └── Returns ChargeBreakdown per order
         │
         ├── 7. TransactionRepository.insertChargeTransactions(charges)
         │       └── One debit entry per charge type per order
         │
         ├── 8. TransactionRepository.insertOrderValueTransactions(orders)
         │       └── Buy → credit equity value; Sell → debit equity value
         │
         ├── 9. GttEngine.processNewBuys(affectedHoldings)
         │       ├── KiteConnectRepository.fetchGttList()
         │       ├── For each holding: create or update GTT
         │       │    └── KiteConnectRepository.placeGtt() / modifyGtt()
         │       ├── KiteConnectRepository.fetchGttList()  [Verification]
         │       └── GttRepository.updateLocalRecords()
         │
         └── 10. ReconcileFundUseCase.execute()
                 ├── KiteConnectRepository.fetchFundBalance()
                 ├── Compare local vs remote
                 ├── Within tolerance → auto-adjust + log
                 └── Exceeds tolerance → emit FundMismatchAlert
```

**Atomicity Guarantee:** Steps 4–8 execute inside a single Room `@Transaction`. If any step fails, the entire local write is rolled back. Remote API calls (steps 1, 2, 9, 10) are idempotent reads or guarded writes; failure in step 9 or 10 does not roll back order/holdings persistence.

### 5.2 Fund Entry Data Flow

```
[User taps "Add Fund Entry"]
         │
         ▼
  FundManagementVM.processIntent(AddFundEntry(type, amount, date, note))
         │
         ▼
  AddFundEntryUseCase.execute()
         │
         ├── 1. DuplicateDetector.check(type, amount, date ±1 day)
         │       ├── No duplicate → continue
         │       └── Potential duplicate → emit DuplicateWarning (user confirms/cancels)
         │
         ├── 2. FundRepository.insertEntry(entry)
         │
         └── 3. ReconcileFundUseCase.execute()
                 └── (same as step 10 above)
```

### 5.3 Backup Data Flow

```
[User triggers "Back Up Now" → Google Drive]
         │
         ▼
  BackupEngine.createBackup()
         │
         ├── 1. Assemble metadata: accountId, appVersion, schemaVersion, timestamp
         ├── 2. Serialize: orders, transactions, holdings, fund entries, settings snapshot
         │       └── Protobuf serialization with schema version tag
         ├── 3. Compress: GZIP
         ├── 4. Upload: GoogleDriveRepository.upload(backupFile, appFolder)
         │       ├── Success → log event, emit BackupSuccess
         │       └── Failure → save to local fallback, emit BackupFailedWithLocalFallback
         └── 5. Record backup metadata locally (timestamp, destination, file size)
```

---

## 6. Request / Command Processing Pipeline (MVI)

```
┌─────────┐      ┌───────────┐      ┌──────────┐      ┌──────────┐
│  User   │─────▶│  Intent   │─────▶│ Reducer  │─────▶│  State   │
│ Action  │      │  (sealed) │      │(ViewModel│      │(data     │
│         │      │           │      │  logic)  │      │ class)   │
└─────────┘      └───────────┘      └──────────┘      └──────┬───┘
                                          │                    │
                                          │                    ▼
                                          │              ┌──────────┐
                                          └─────────────▶│  Side    │
                                           (one-shot)    │  Effect  │
                                                         │ (Channel)│
                                                         └──────────┘
```

**MVI Contract per Screen:**

```kotlin
// Example: HoldingsScreen
sealed interface HoldingsIntent {
    data object LoadHoldings : HoldingsIntent
    data class EditProfitTarget(val stockId: String, val target: ProfitTarget) : HoldingsIntent
    data class ExpandCard(val stockId: String) : HoldingsIntent
}

data class HoldingsState(
    val holdings: List<HoldingUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val expandedStockId: String? = null,
    val chargeRatesMissing: Boolean = false,
    val error: UiError? = null,
)

sealed interface HoldingsSideEffect {
    data class ShowSnackbar(val message: String) : HoldingsSideEffect
    data class NavigateToGtt(val stockId: String) : HoldingsSideEffect
}
```

**Reducer Execution:**

1. Intent arrives via `Channel<Intent>` (conflated for UI state reads, buffered for commands).
2. ViewModel maps Intent to a suspend function calling the appropriate UseCase.
3. UseCase returns a `Result<T>` (sealed success/failure).
4. Reducer function produces a new immutable State copy via `copy()`.
5. State is emitted via `StateFlow<State>`.
6. One-shot effects (navigation, snackbar) are emitted via `Channel<SideEffect>` consumed as `receiveAsFlow()` in the Composable.

**Thread Safety:** `StateFlow` ensures atomic state emissions. All state mutations funnel through a single coroutine (`viewModelScope.launch`) with sequential processing. No concurrent state reduction.

---

## 7. Concurrency Model

### 7.1 Coroutine Dispatchers

| Context | Dispatcher | Rationale |
|---|---|---|
| ViewModel coroutines | `Dispatchers.Main.immediate` | State emission must happen on Main. |
| UseCase orchestration | `Dispatchers.Default` | CPU-bound business logic (charge calculation, P&L aggregation). |
| Database operations | Room's internal dispatcher (`Dispatchers.IO` underneath) | Room manages its own thread pool for query execution. |
| Network calls | `Dispatchers.IO` | Blocking HTTP I/O. |
| WorkManager Workers | `CoroutineWorker` on `Dispatchers.Default` | Workers are background; no Main thread affinity needed. |
| CSV/Excel parsing | `Dispatchers.Default` | CPU-bound row validation. |
| Backup serialization | `Dispatchers.IO` | File I/O + compression. |

### 7.2 Concurrency Safety Rules

1. **Single-writer per entity group.** Order sync, fund reconciliation, and GTT operations each acquire a `Mutex` from a named `MutexRegistry` before writing. This prevents two scheduled workers from concurrently modifying holdings.

2. **No parallel API calls to the same Kite Connect endpoint.** Kite Connect free tier has rate limits. A `Semaphore(1)` guards all Kite API calls, serializing them. Different API providers (Gmail, Drive) have independent semaphores.

3. **Room transactions for multi-table writes.** Any operation that modifies more than one table (e.g., inserting orders + updating holdings + inserting transactions) is wrapped in a Room `@Transaction`.

4. **StateFlow conflation.** UI state uses `StateFlow`, which conflates intermediate emissions. This is safe because each emission is a complete snapshot.

5. **WorkManager uniqueness.** Each periodic worker uses `ExistingPeriodicWorkPolicy.KEEP` to prevent duplicate scheduling. One-time workers use `ExistingWorkPolicy.REPLACE`.

### 7.3 Concurrency Hazard Matrix

| Scenario | Hazard | Mitigation |
|---|---|---|
| Manual sync pressed during scheduled sync | Double order write | `Mutex("order_sync")` — second caller suspends until first completes |
| User edits profit target while GTT update in progress | Stale target sent to Kite | Mutex on GTT operations; target change queues behind in-flight GTT call |
| Backup triggered during active write | Inconsistent snapshot | Backup reads from a Room WAL snapshot; WAL mode provides MVCC isolation |
| Two CSV imports triggered rapidly | Duplicate records | Mutex on import operations + duplicate detection within DB transaction |
| Network timeout during GTT create, followed by retry | Double GTT creation | Idempotency: check GTT list before creating; if GTT exists for stock, update instead |

---

## 8. Failure Recovery Model

### 8.1 Failure Classification

| Severity | Examples | Recovery Strategy |
|---|---|---|
| **Transient** | API timeout, rate limit, no connectivity | Automatic retry at next scheduled time. Amber warning banner. No data modification. |
| **Recoverable** | Holdings quantity mismatch, GTT verification mismatch | Red alert banner. User must acknowledge. Data writes blocked until resolution. |
| **Critical** | Database corruption, schema migration failure | App enters degraded read-only mode. Prompts user to restore from backup. Logs diagnostic info. |
| **Permanent** | API endpoint deprecated, account credentials revoked | Feature-specific disablement. Critical alert with instruction to update app or re-authorize. |

### 8.2 Recovery Strategies by Subsystem

**Order Sync Failure:**

- API unreachable → Worker returns `Result.retry()` with exponential backoff (30s, 60s, 120s, max 3 retries). If all retries fail, emits `SyncFailedTransient` event. Worker schedules re-run at next periodic window.
- Holdings mismatch → Worker returns `Result.success()` (work completed) but emits `HoldingsMismatchAlert` to a persistent alert store. User must navigate to a resolution screen. No automatic re-sync until user acknowledges.
- Partial failure (orders fetched, holdings fetch fails) → Entire sync rolled back. No local state modified.

**GTT Failure:**

- GTT API call fails → GTT state marked as `PENDING` locally. Retry on next order sync that touches the affected holding.
- GTT verification mismatch → `GttVerificationAlert` emitted. Local GTT record not updated until user resolves.

**Fund Reconciliation Failure:**

- API unreachable → Warning logged. Retry at next scheduled reconciliation. Local balance unchanged.
- Tolerance exceeded → `FundMismatchAlert` persisted. Alert shown on Portfolio screen until next reconciliation resolves it or user manually adjusts.

**Backup Failure:**

- Drive upload fails → Local backup saved automatically. User notified of fallback.
- Restore of corrupted file → Abort before any writes. Existing data untouched.

### 8.3 Data Integrity Guarantees

1. **No silent failures.** Every API call failure, every mismatch, every retry is logged to the local audit log and surfaced via UI alerts.
2. **No partial writes.** All multi-step local writes are transactional. Failure at any step rolls back the entire operation.
3. **Append-only transaction log.** The transaction log is never modified or deleted programmatically. This provides a complete audit trail.
4. **Bound-account validation.** Every external data ingestion (API response, CSV import, backup restore) validates the embedded account identifier against the locally bound account before processing.

---

## 9. Observability Strategy

### 9.1 Local Logging

| Log Level | Usage |
|---|---|
| `ERROR` | Unrecoverable failures, unexpected exceptions, data integrity violations |
| `WARN` | Transient API failures, tolerance mismatches, retries triggered |
| `INFO` | Sync completions, GTT actions, backup events, user-initiated actions |
| `DEBUG` | API request/response payloads (sensitive fields redacted), SQL query timing |

**Implementation:**

- Timber with a custom `FileLoggingTree` that writes to a ring-buffer log file (`max 5MB`, rotated into 2 files).
- Log files stored in app-internal directory. Exportable via debug menu (disabled in release builds — or gated behind a developer toggle in Settings).
- **No remote logging.** Consistent with the local-only data residency requirement.

### 9.2 Structured Event Tracking

A local `SyncEventLog` table records every background operation:

```
sync_event_log(
  id INTEGER PRIMARY KEY,
  event_type TEXT NOT NULL,       -- 'ORDER_SYNC', 'FUND_RECON', 'GTT_UPDATE', 'CHARGE_REFRESH', 'BACKUP'
  started_at TEXT NOT NULL,       -- ISO-8601
  completed_at TEXT,
  status TEXT NOT NULL,           -- 'SUCCESS', 'FAILED', 'PARTIAL', 'SKIPPED'
  details TEXT,                   -- JSON blob with event-specific data
  error_message TEXT
)
```

This table powers:

- The "Last Sync" status indicator on each screen.
- Settings → Automation section showing per-task last-run status.
- Diagnostic data for debugging user-reported issues.

### 9.3 Performance Monitoring (Local Only)

- Room query timing via `SupportSQLiteOpenHelper.Factory` instrumentation (debug builds).
- WorkManager execution duration captured in `sync_event_log`.
- Composable recomposition count tracking via Jetpack Compose compiler metrics (debug builds).
- No third-party APM SDK (Crashlytics, Firebase Performance) to honor the no-analytics-SDK constraint.

---

## 10. Performance Strategy

### 10.1 Performance Budgets

| Operation | Target | Strategy |
|---|---|---|
| Portfolio screen render (3 years of data) | < 2 seconds | Pre-aggregated P&L summary table; chart data computed on IO dispatcher |
| List screen first page (50 items) | < 1 second | Indexed queries; Room paging via Paging 3 library |
| Order sync (typical day, ≤20 orders) | < 10 seconds total | Sequential API calls; batch DB insert |
| Charge calculation (per order) | < 1ms | Pure arithmetic, no I/O |
| CSV import (5,000 rows) | < 15 seconds | Chunked parsing (500 rows/batch); batch insert in transaction |
| Backup creation (full DB, ~50MB) | < 30 seconds | Streaming protobuf serialization + GZIP |

### 10.2 Query Optimization

- **P&L Aggregation:** Maintain a `pnl_summary` materialized table (or Room `@DatabaseView`) that pre-aggregates realized P&L by month. Updated incrementally when new orders are synced. Portfolio screen queries this view instead of computing from raw order and transaction rows.
- **Pagination:** Orders and Transactions screens use `PagingSource` from Room's Paging 3 integration. Keyset pagination keyed on `(date DESC, id DESC)`.
- **Indexes:** All foreign keys indexed. Composite indexes on `(stock_code, date)` for order lookups, `(type, date)` for transaction filtering. Details in `03_DATABASE_SCHEMA.md`.

### 10.3 Memory Management

- **Lazy loading:** Chart bitmap rendering deferred until the chart Composable is visible (via `LaunchedEffect` keyed on visibility state).
- **Image-free UI:** No stock logos or images to load. Charts rendered via Canvas composables or Vico library (lightweight).
- **Flow collection scoping:** All `Flow` collections in ViewModels use `viewModelScope`. All collections in Composables use `collectAsStateWithLifecycle()` to respect lifecycle.
- **Room cursor windowing:** Large query results processed in windowed cursors by default (Room's built-in behavior).

---

## 11. Scaling Considerations

### 11.1 Dataset Growth Projections

| Entity | Assumed Growth | 3-Year Estimate |
|---|---|---|
| Orders | ~5/day average (active trader) | ~3,750 rows |
| Transactions | ~15/day (buy/sell + charges per order) | ~11,250 rows |
| Holdings | ≤50 concurrent stocks | 50 rows (stable) |
| Fund entries | ~5/month | ~180 rows |
| GTT records | ≤50 active | 50 rows + archived |
| Sync event log | ~3/day | ~2,250 rows |

**Conclusion:** Total dataset fits comfortably within SQLite's capabilities on any modern Android device. No sharding, partitioning, or secondary storage needed for the foreseeable future.

### 11.2 Large Dataset Handling

Even though datasets are modest, defensive strategies are applied:

1. **Archival:** `sync_event_log` entries older than 6 months are auto-archived (moved to a `sync_event_log_archive` table or pruned). Configurable retention period.
2. **Vacuum scheduling:** SQLite `VACUUM` runs opportunistically (e.g., after a large CSV import) using WorkManager to reclaim space.
3. **Pre-aggregation:** P&L summaries are incrementally maintained, not recomputed from raw data on every screen load. Even with 50,000 transactions, the Portfolio screen reads from a summary view.

---

## 12. Background Job Architecture

### 12.1 Worker Definitions

| Worker | Type | Schedule | Constraints | Retry Policy |
|---|---|---|---|---|
| `OrderSyncWorker` | Periodic | User-configured time(s), Mon–Fri | Network required | Exponential backoff, max 3 retries |
| `ReconciliationWorker` | Periodic | User-configured time, Mon–Fri | Network required | Exponential backoff, max 3 retries |
| `ChargeRateRefreshWorker` | Periodic | Every N days (default 15) | Network required | Linear backoff, max 2 retries |
| `BackupWorker` | Periodic (optional) | User-configured | Network for Drive; none for local | Exponential backoff, max 2 retries |
| `GttUpdateWorker` | OneTime | Triggered by OrderSyncWorker on new buys | Network required | Exponential backoff, max 3 retries |
| `GmailScanWorker` | Periodic | Aligned with ReconciliationWorker | Network required | Linear backoff, max 2 retries |

### 12.2 Worker Scheduling — Mon–Fri Enforcement

```kotlin
class WeekdayConstraintHelper {
    /**
     * Returns true if today is Monday–Friday in the user's local timezone.
     * Called at the START of every Worker.doWork(). If false, the worker
     * returns Result.success() immediately (no-op), effectively skipping
     * weekend execution.
     */
    fun isTradingDay(now: LocalDate = LocalDate.now()): Boolean {
        val dow = now.dayOfWeek
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
    }
}
```

WorkManager's `PeriodicWorkRequest` does not natively support "weekdays only." The enforcement is in-worker: if triggered on a weekend (e.g., due to deferred execution), the worker completes as a no-op.

### 12.3 Worker Chaining (Order Sync Pipeline)

```
OrderSyncWorker
    │
    ├── on SUCCESS with new orders:
    │      enqueue OneTimeWorkRequest(GttUpdateWorker)
    │      enqueue OneTimeWorkRequest(ReconciliationWorker)  [if not already run today]
    │
    └── on SUCCESS with no new orders:
           no follow-up work
```

Workers communicate via WorkManager's `Data` output (limited to simple key-value pairs). Complex payloads (e.g., list of affected stock IDs) are written to a transient `worker_handoff` table in Room and read by the chained worker.

### 12.4 Worker Lifecycle & Cancellation

- All periodic workers are registered during onboarding completion.
- Schedule changes in Settings cancel existing work and re-register with new parameters.
- App uninstall automatically clears WorkManager state.
- If a worker is killed mid-execution by the OS, WorkManager's built-in retry handles re-execution.

---

## 13. Caching Strategy

### 13.1 No HTTP Cache Layer

External API responses (Kite Connect, Gmail, Drive) are **not cached at the HTTP level** because:

- Kite Connect data (orders, holdings, fund balance) must always be fresh when fetched.
- The app already stores all API data locally in Room. Room IS the cache.

### 13.2 In-Memory Caches

| Cache | Contents | Scope | Eviction |
|---|---|---|---|
| `ChargeRateCache` | Latest charge rate snapshot | Singleton (Application scope) | Replaced on every charge rate refresh |
| `HoldingsSummaryCache` | Computed holdings list with derived fields (target prices) | ViewModel scope | Cleared when ViewModel is destroyed or when holdings are updated |
| `PnlAggregationCache` | Pre-computed P&L data per date range | ViewModel scope | Invalidated on new order sync |

### 13.3 Disk Cache (Room as Cache)

Room serves as the persistent cache for all API data. The "fetch → store → read from store" pattern ensures:

- Offline access to all previously fetched data.
- Deterministic UI rendering (always from local source of truth).
- No stale API response confusion (local data is the canonical state).

---

## 14. Error Propagation Architecture

### 14.1 Error Model

```kotlin
sealed interface AppError {
    // Transient — will resolve on retry
    sealed interface Transient : AppError {
        data class NetworkUnavailable(val cause: Throwable) : Transient
        data class ApiRateLimited(val retryAfterSec: Int?) : Transient
        data class ApiTimeout(val endpoint: String) : Transient
    }

    // Recoverable — requires user action
    sealed interface Recoverable : AppError {
        data class HoldingsMismatch(val diffs: List<StockDiff>) : Recoverable
        data class FundMismatchExceedsTolerance(
            val localBalance: BigDecimal,
            val remoteBalance: BigDecimal,
            val tolerance: BigDecimal,
        ) : Recoverable
        data class GttVerificationFailed(val stockId: String, val expected: GttState, val actual: GttState) : Recoverable
        data class ManualGttOverrideDetected(val stockId: String, val zerodhaValue: GttState, val appValue: GttState) : Recoverable
    }

    // Critical — app may need recovery
    sealed interface Critical : AppError {
        data class DatabaseCorruption(val cause: Throwable) : Critical
        data class SchemaMigrationFailed(val fromVersion: Int, val toVersion: Int) : Critical
        data class ApiEndpointDeprecated(val endpoint: String) : Critical
    }

    // Validation — user input errors
    sealed interface Validation : AppError {
        data class CsvFormatInvalid(val errors: List<CsvRowError>) : Validation
        data class AccountMismatch(val expected: String, val found: String) : Validation
        data class DuplicateEntry(val matchingEntryId: Long) : Validation
    }
}
```

### 14.2 Error Flow

```
[Data/Infra Layer] → throws Exception / returns Result.failure()
         │
         ▼
[UseCase] → catches, maps to AppError, returns Result<T>
         │
         ▼
[ViewModel] → reduces error into UI State (inline banner, dialog)
              OR emits as SideEffect (snackbar, navigation)
         │
         ▼
[UI Composable] → renders error state from State or handles SideEffect
```

**Rule:** Raw exceptions never leak past the UseCase boundary. All errors surfaced to the ViewModel are typed `AppError` instances.

---

## 15. Persistent Alert System

Certain errors must persist across app restarts (e.g., holdings mismatch, fund mismatch exceeding tolerance). These are stored in a `persistent_alerts` table:

```
persistent_alerts(
  id INTEGER PRIMARY KEY,
  alert_type TEXT NOT NULL,       -- 'HOLDINGS_MISMATCH', 'FUND_MISMATCH', 'GTT_VERIFICATION'
  created_at TEXT NOT NULL,
  payload TEXT NOT NULL,          -- JSON blob with alert-specific data
  acknowledged INTEGER NOT NULL DEFAULT 0,
  resolved_at TEXT
)
```

- Alerts are inserted by Workers/UseCases when conditions are detected.
- Portfolio screen and relevant feature screens query active (unresolved) alerts on load.
- Resolution occurs when: (a) user acknowledges, (b) a subsequent sync resolves the mismatch automatically (e.g., next reconciliation is within tolerance), or (c) user manually adjusts.

---

## 16. Security Architecture Overview

> Full details in `07_SECURITY_MODEL.md`.

**Summary of security boundaries in the architecture:**

| Boundary | Mechanism |
|---|---|
| App entry | Mandatory BiometricPrompt / device credential |
| Token storage | EncryptedSharedPreferences (AES-256-SIV + AES-256-GCM via Android Keystore) |
| Database at rest | SQLCipher (AES-256-CBC) or Android file-based encryption (FBE), depending on tech decision |
| Network transport | HTTPS only (certificate pinning for Kite Connect) |
| Backup files | GZIP + optional AES-256 encryption with user passphrase (Phase 3) |
| Input validation | All CSV/import paths validated and sanitized before processing |
| Third-party SDK policy | Zero third-party analytics, crash reporting, or advertising SDKs |

---

## 17. Cross-Cutting Concerns

### 17.1 Dependency Injection

Hilt (Dagger-based) provides compile-time verified DI. Module-per-layer scoping:

| Hilt Module | Provides |
|---|---|
| `NetworkModule` | Retrofit instances, OkHttpClient, API service interfaces |
| `DatabaseModule` | Room database instance, DAOs |
| `RepositoryModule` | Repository implementations bound to interfaces |
| `UseCaseModule` | UseCase instances (or constructor-injected, no module needed) |
| `WorkerModule` | Worker factory, scheduler utilities |
| `SecurityModule` | EncryptedSharedPreferences, BiometricManager |

### 17.2 DateTime Handling

- All timestamps stored in UTC ISO-8601 in the database.
- Display-layer converts to user's local timezone for rendering.
- `java.time` API exclusively (no `java.util.Date`). Desugaring enabled for API < 26.
- Market-day calculations use `Asia/Kolkata` timezone with NSE holiday calendar for future phases.

### 17.3 Currency Handling

- All monetary values stored as `Long` representing **paisa** (1/100 of INR). This eliminates floating-point precision issues.
- Domain models expose `BigDecimal` via computed properties for business logic requiring decimal arithmetic (e.g., charge calculation with percentages).
- Display layer formats using `NumberFormat` with INR locale (`₹` symbol, comma grouping per Indian numbering: 1,00,000).
- No floating-point types (`Float`, `Double`) are used for monetary storage or intermediate calculations.

### 17.4 Internationalization Readiness

- All user-facing strings externalized to `strings.xml`.
- Number formatting uses locale-aware formatters.
- Date formatting uses `DateTimeFormatter` with user locale.
- RTL layout support enabled in manifest.
- Single language shipped in Phase 1 (English). Multi-language support is a documented future enhancement.
