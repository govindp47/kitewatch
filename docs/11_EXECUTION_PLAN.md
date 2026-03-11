# 11 — Execution Plan

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-11

---

## Overview

This document defines the phased engineering execution plan for KiteWatch across three product phases aligned to the PRD roadmap. Each engineering phase maps to a product phase and is broken down into discrete sprints with defined scope, dependencies, complexity ratings, effort estimates, risks, and exit criteria. Estimates assume a team of **1–2 engineers** (one senior Android engineer as the primary implementor, with optional second engineer joining in Phase 2). All estimates include buffer for reviews, testing, and iteration.

**Total estimated calendar duration:** 34–42 weeks from project initiation to Phase 3 complete.

---

## Effort Estimation Conventions

| Complexity Rating | Definition | Typical Duration (1 engineer) |
|---|---|---|
| XS | Trivial: single class, no external dependency | 0.5 – 1 day |
| S | Small: 2–5 classes, unit-testable in isolation | 1 – 3 days |
| M | Medium: full feature slice (UI → Domain → Data), integration points | 3 – 7 days |
| L | Large: multi-module work, complex business logic, or integration with external API | 7 – 14 days |
| XL | Extra-large: foundational subsystem, affects multiple modules, high testing burden | 14 – 21 days |

---

## Phase 0 — Project Foundation

**Objective:** Establish the complete project skeleton, toolchain, CI/CD, and build infrastructure before any product feature work begins. No skipping this phase. Time spent here is recovered multiple times over in downstream development velocity.

**Duration:** 2 weeks  
**Prerequisite:** None

---

### Sprint 0.1 — Repository and Toolchain (Week 1)

#### Scope

1. **Repository initialization**
   - Create private GitHub repository.
   - Configure `.gitignore` (Android standard + custom: `secrets.properties`, keystore files, `/local.properties`).
   - Set branch protection rules on `main`: require PR review, require CI pass, no force push.
   - Create `develop` branch as the integration branch.

2. **Multi-module Gradle scaffold**
   - Create all module stubs per the module graph in `05_APPLICATION_STRUCTURE.md`:
     - `:app`, `:feature-portfolio`, `:feature-holdings`, `:feature-orders`, `:feature-transactions`, `:feature-gtt`, `:feature-settings`, `:feature-onboarding`, `:feature-auth`
     - `:core-domain`, `:core-data`, `:core-network`, `:core-database`, `:core-ui`
     - `:infra-worker`, `:infra-backup`, `:infra-auth`, `:infra-csv`
   - Configure convention plugins for `com.android.library`, `com.android.application`, and `java-library` module types.
   - Wire `VERSION_CATALOG` (`libs.versions.toml`) for all dependency declarations.
   - Configure `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`.
   - Establish three build variants: `debug`, `staging`, `release`.
   - Configure R8/ProGuard rules skeleton in `proguard-rules.pro`.

3. **Code quality toolchain**
   - Integrate Ktlint via `jlleitschuh/ktlint-gradle` plugin.
   - Integrate Detekt with a `detekt.yml` configuration tailored to the codebase (disable rules incompatible with Compose).
   - Configure `allWarningsAsErrors = true` in Kotlin compiler options.
   - Set up pre-commit hooks script (`git-hooks/pre-commit`) and a Gradle `setup` task to install hooks.

4. **Secrets infrastructure**
   - Configure `secrets-gradle-plugin` for `secrets.properties`.
   - Document `secrets.properties.template` with placeholder values.
   - Add CI secrets to GitHub repository settings (keystore, API keys).

**Complexity:** M  
**Effort:** 4–5 days

---

### Sprint 0.2 — CI/CD Pipeline and Base App Shell (Week 2)

#### Scope

1. **CI/CD workflows**
   - Implement `ci-pr.yml`, `ci-staging.yml`, `ci-release.yml` per `10_DEPLOYMENT_WORKFLOW.md`.
   - Validate all three workflows fire correctly on the respective trigger events.
   - Confirm staging build produces a signed APK artifact downloadable from GitHub Actions.

2. **Application class and DI root**
   - Implement `KiteWatchApplication : Application()` annotated with `@HiltAndroidApp`.
   - Configure Hilt application-level modules: `NetworkModule`, `DatabaseModule`, `PreferencesModule`.
   - Configure Timber: `DebugTree` for debug, `CrashReportingTree` (no-op initially) for release.

3. **Navigation host scaffold**
   - Implement `MainActivity` with `NavHostComposable`.
   - Wire all feature module navigation graphs (empty screens, correct back-stack behavior).
   - Verify navigation between all five main tabs compiles and navigates correctly.

4. **Theme system**
   - Implement `KiteWatchTheme` with Material 3 `ColorScheme` for Dark and Light modes.
   - Implement `ThemePreferenceRepository` persisting to `DataStore<Preferences>`.
   - Verify theme switch applies immediately without restart.

5. **Base design system components (stubs)**
   - Stub implementations for: `AlertBanner`, `SkeletonLoader`, `EmptyStateWidget`, `ConfirmationDialog`, `FilterChipGroup`.
   - These are non-functional stubs; full implementations delivered per feature sprint.

6. **Baseline instrumented test configuration**
   - Configure Hilt testing rules for instrumented tests.
   - Configure `ComposeTestRule` and verify a trivial Compose test passes on CI.

**Complexity:** M  
**Effort:** 4–5 days

**Phase 0 Exit Criteria:**

- All modules compile clean with zero warnings.
- Ktlint and Detekt pass on CI.
- All three CI workflows fire and complete successfully.
- Staging APK is produced and installable on a physical device.
- Navigation between all tabs works (with placeholder screens).
- Dark/Light theme toggle works.
- No secrets in version control.

---

## Phase 1 — MVP (Core Utility)

**Objective:** Deliver a functional, secure, end-to-end app covering: Zerodha authentication, order sync, GTT automation, P&L calculation, Holdings view, Portfolio dashboard, Transactions log, biometric lock, and basic fund balance entry.

**Duration:** 12–14 weeks  
**Prerequisite:** Phase 0 complete

---

### Sprint 1.1 — Database Schema and Core Domain Models (Weeks 3–4)

#### Scope

1. **Room database implementation** (`:core-database`)
   - Implement all entity classes per `03_DATABASE_SCHEMA.md`:
     `OrderEntity`, `HoldingEntity`, `TransactionEntity`, `FundBalanceEntity`, `FundTransactionEntity`, `GttOrderEntity`, `ChargeRateEntity`, `AppSettingsEntity`, `BackgroundJobAuditEntity`.
   - Implement all DAOs with complete query set.
   - Implement `TypeConverters` for `Instant` ↔ `Long`, `BigDecimal` ↔ `String`.
   - Set `DATABASE_VERSION = 1`. Export schema JSON to `core-database/schemas/1.json`.
   - Implement `AppDatabase` singleton with Hilt injection.

2. **Core domain models** (`:core-domain`)
   - Implement all domain model data classes: `Order`, `Holding`, `Transaction`, `FundBalance`, `GttOrder`, `ChargeRate`, `AppSettings`.
   - Implement `DomainError` sealed class hierarchy.
   - Implement repository interfaces: `OrderRepository`, `HoldingRepository`, `TransactionRepository`, `FundRepository`, `GttRepository`, `ChargeRateRepository`, `SettingsRepository`.

3. **Mapper layer** (`:core-data`)
   - Implement entity-to-domain and domain-to-entity mappers for all entities.

**Dependencies:** Phase 0 complete  
**Complexity:** L  
**Effort:** 8–10 days  
**Risk:** Schema design revisions after implementation begin are costly. Finalize schema review before coding starts.

**Exit Criteria:**

- All Room entities, DAOs, and TypeConverters compile with zero errors.
- Schema JSON exported and committed.
- Unit tests cover all DAO operations against an in-memory Room database.
- All domain models instantiable; repository interfaces defined.

---

### Sprint 1.2 — Zerodha API Integration and Authentication (Weeks 5–6)

#### Scope

1. **Kite Connect HTTP client** (`:core-network`)
   - Implement `KiteConnectApiService` (Retrofit interface) for:
     - `GET /user/profile` — profile validation
     - `GET /user/margins` — fund balances
     - `GET /orders` — today's order history
     - `GET /portfolio/holdings` — current holdings
     - `GET /gtt` — active GTTs
     - `POST /gtt` — create GTT
     - `PUT /gtt/{id}` — update GTT
     - `DELETE /gtt/{id}` — delete GTT
     - `GET /charges/orders` — charge rates (if available; fallback to hardcoded rates)
   - Implement `KiteAuthInterceptor` that injects `Authorization: token {api_key}:{access_token}` header.
   - Implement `TokenExpiredInterceptor` that detects 403 responses and emits a `SessionExpired` event.
   - Configure OkHttp with 30-second connect timeout, 60-second read timeout, retry-on-connection-failure disabled (explicit retry in use case layer).

2. **Zerodha OAuth login flow** (`:feature-onboarding`, `:infra-auth`)
   - Implement `KiteAuthActivity` that opens Zerodha login URL in a `CustomTabsIntent`.
   - Implement `DeepLinkHandlerActivity` with intent filter for `kitewatch://callback` to capture the `request_token` from redirect URI.
   - Implement `KiteSessionUseCase.exchangeRequestToken(requestToken)` → calls `/session/token` → stores `access_token` in `EncryptedSharedPreferences`.
   - Implement session expiry detection and recovery flow: on 403 response, clear stored token and navigate to re-auth screen.

3. **Account binding** (`:feature-onboarding`)
   - Implement onboarding screen sequence: T&C acceptance → biometric setup → Zerodha login → success.
   - Store `user_id`, `user_name`, `api_key` in `EncryptedSharedPreferences` after successful auth.
   - Implement "already onboarded" check on app cold start → navigate to biometric lock or main app.

**Dependencies:** Sprint 1.1  
**Complexity:** L  
**Effort:** 8–10 days  
**Risks:**

- Kite Connect OAuth redirect URI handling is device-specific (Custom Tabs vs WebView fallback). Test on multiple devices.
- `request_token` lifetime is very short (~2 minutes). The exchange must happen immediately after redirect.
- Free-tier API endpoint availability must be confirmed against the live Kite Connect documentation before implementing — if an endpoint is unavailable at free tier, use-case fallback logic must be in place.

**Exit Criteria:**

- Full OAuth flow completes end-to-end on a physical device with a real Zerodha account.
- `access_token` stored securely in `EncryptedSharedPreferences`.
- Session expiry correctly surfaces the re-auth screen.
- Retrofit service compiles; all API calls return typed results.

---

### Sprint 1.3 — Biometric App Lock (Weeks 6–7, parallel with 1.2)

#### Scope

1. **BiometricAuthManager** (`:infra-auth`)
   - Implement `BiometricAuthManager` using `BiometricPrompt` API.
   - Support: fingerprint, face unlock, device PIN/pattern fallback.
   - Implement `AppLockStateManager` tracking foreground/background transitions via `ProcessLifecycleOwner`.
   - Implement 5-minute background threshold: if app has been backgrounded for > 5 minutes, require re-auth on next foreground.
   - Store lock state in-memory only — never persisted.

2. **Lock screen UI** (`:feature-auth`)
   - Implement `AppLockScreen` Composable — prompt biometric dialog immediately on composition.
   - Implement `AppLockViewModel` managing biometric prompt result, retry logic, and app exit on repeated failure.

3. **Lock gate in NavHost**
   - Wrap entire `NavHostComposable` with an `AppLockGate` composable that blocks navigation until `AppLockStateManager.isUnlocked = true`.

**Dependencies:** Phase 0 complete  
**Complexity:** M  
**Effort:** 4–5 days  
**Risks:** Biometric API behavior varies across Android OEM implementations. Test on Samsung, Xiaomi, and stock Android.

**Exit Criteria:**

- Biometric prompt appears immediately on app launch and after 5-minute background.
- Correct fallback to PIN/pattern.
- App lock gates all screens correctly.

---

### Sprint 1.4 — Order Sync and Holdings Verification (Weeks 7–9)

#### Scope

1. **OrderSyncUseCase** (`:core-domain`)
   - Implement the full order sync algorithm per `04_DOMAIN_ENGINE_DESIGN.md`:
     - Fetch today's orders from Kite Connect.
     - Deduplicate against locally stored orders (by `order_id`).
     - For new COMPLETE/TRADED orders: classify as BUY or SELL, calculate charges, derive transaction record, upsert into `orders` and `transactions` tables.
     - Return `OrderSyncResult` containing: new order count, updated order count, new transaction count, validation errors.
   - Implement full charge calculation for equity delivery:
     - Brokerage: ₹0 for delivery (Zerodha specific).
     - STT: 0.1% of trade value on both buy and sell.
     - Exchange Transaction Charges: NSE 0.00297%, BSE 0.003%.
     - SEBI Charges: ₹10 per crore.
     - GST: 18% on (brokerage + exchange charges + SEBI charges).
     - Stamp Duty: 0.015% on buy, capped at ₹1,500 per order.
   - Charge rates loaded from `charge_rates` table; fallback to hardcoded defaults if table is empty.

2. **HoldingsVerificationUseCase** (`:core-domain`)
   - After each order sync: fetch live holdings from Kite Connect.
   - Compare against locally-computed holdings (derived from all order history).
   - Identify discrepancies: missing local holding, quantity mismatch, instrument present in API but not local.
   - Return `HoldingsVerificationResult` with a list of `HoldingDiscrepancy` items.
   - Surface discrepancies as in-app alerts (non-blocking, dismissible, but logged permanently in audit table).

3. **Holdings computation engine** (`:core-domain`)
   - Implement `HoldingsComputationEngine` that derives current holdings from order history:
     - Group orders by instrument token.
     - Apply FIFO lot matching for partial sells.
     - Compute average buy price per lot still held.
     - Compute quantity held, total cost basis.
   - This engine runs in-memory from the full order history — never truncated.

4. **Manual sync trigger** (`:feature-orders`)
   - Implement "Refresh" button on Orders screen → triggers `OrderSyncUseCase` → updates UI via `StateFlow`.
   - Implement loading state, error state, and success state on the Orders screen.

**Dependencies:** Sprints 1.1, 1.2  
**Complexity:** XL  
**Effort:** 12–14 days  
**Risks:**

- Charge calculation precision: use `BigDecimal` for all monetary arithmetic. Round only at the final display/storage step. A single rounding error propagated across many orders compounds into significant discrepancy.
- FIFO lot matching edge cases: partial buy fills, split orders, same-day buy-sell (though only delivery is in scope, same-day full exit is possible).
- Holdings API may return instruments not in local history (e.g., stocks transferred in via CDSL). These must be flagged as "externally held" without crashing.

**Exit Criteria:**

- Order sync correctly deduplicates, calculates charges, and creates transaction records.
- Charge calculations match Zerodha contract note values within ₹1 rounding tolerance on a set of at least 10 real order fixtures.
- Holdings verification detects injected mismatches in integration tests.
- Manual refresh flow completes end-to-end on device with live Zerodha account.

---

### Sprint 1.5 — GTT Automation Engine (Weeks 9–11)

#### Scope

1. **GttAutomationEngine** (`:core-domain`)
   - Implement the GTT automation algorithm per `04_DOMAIN_ENGINE_DESIGN.md`:
     - For each new BUY order in current sync results: compute target sell price = `average_buy_price × (1 + profit_target_pct / 100)`.
     - Check if a GTT already exists locally for this instrument token.
     - If no GTT exists: create a new GTT via Kite Connect API → store `gtt_id` locally.
     - If GTT exists and target price has changed (e.g., averaging down): update existing GTT via API.
     - If GTT was manually modified in Zerodha (detected by comparing API-returned `last_price` with locally-stored target): flag as `ManualOverrideDetected`, do not auto-update.
   - Implement idempotency: if automation runs twice for the same buy order, the second run detects GTT already exists and skips creation.
   - Implement GTT API retry with exponential backoff (3 attempts, 2s/4s/8s delays) for transient failures.

2. **Profit target configuration** (`:feature-holdings`, `:core-domain`)
   - Implement `ProfitTargetConfigUseCase` allowing per-instrument override of the default 5% target.
   - Store per-instrument profit target percentages in `app_settings` table.
   - When GTT is recalculated: use per-instrument target if set, else global default.

3. **GTT list screen** (`:feature-gtt`)
   - Implement read-only GTT list screen fetching from local `gtt_orders` table.
   - Display: instrument symbol, trigger price, quantity, status, last synced timestamp.
   - Surface `ManualOverrideDetected` items with a distinct visual indicator.
   - No create/edit actions on this screen — GTTs are managed by the automation engine.

**Dependencies:** Sprints 1.1, 1.2, 1.4  
**Complexity:** L  
**Effort:** 10–12 days  
**Risks:**

- Kite Connect GTT API rate limits: creating GTTs for large numbers of holdings in a single sync could exhaust rate limits. Implement batch throttling (100ms delay between consecutive GTT API calls).
- GTT delete/recreate vs update: Kite Connect may not support updating an existing GTT's trigger price; it may require delete + recreate. Confirm from API docs and handle accordingly with atomic local state update.
- GTT manual override detection is heuristic: the app cannot know with certainty that a user manually changed a GTT vs the price happening to match. Flag conservatively and let the user decide.

**Exit Criteria:**

- GTT is auto-created within the same sync cycle as a new BUY order.
- GTT is auto-updated when averaging down produces a new lower average buy price.
- Manual override detection flags correctly in integration tests.
- GTT screen displays live state from local database.
- Idempotency verified: running sync twice with same buy orders does not create duplicate GTTs.

---

### Sprint 1.6 — P&L Engine and Portfolio Screen (Weeks 11–12)

#### Scope

1. **PnLCalculationEngine** (`:core-domain`)
   - Implement the realized P&L formula per `04_DOMAIN_ENGINE_DESIGN.md`:

     ```
     Realized P&L = Σ(sell_value) − Σ(matched_buy_cost) − Σ(all_charges_on_matched_orders)
     ```

   - FIFO lot matching for partial sells across multiple buy lots.
   - Support date range filtering: calculate P&L for any user-specified date range.
   - Output: `PnLResult(realizedPnL, totalBuyValue, totalSellValue, totalCharges, chargeBreakdown)`.
   - Implement `PnLCalculationEngine.calculate(dateRange, instrumentFilter)` as a pure function operating on order history — no side effects, fully testable.

2. **Portfolio screen** (`:feature-portfolio`)
   - Implement `PortfolioViewModel` backed by `PnLCalculationEngine`.
   - Display: realized P&L (net), total charges, gross profit, date range selector.
   - Implement horizontal line chart of cumulative P&L over time using MPAndroidChart or Compose Canvas.
   - Implement pie chart for charge type breakdown.
   - Implement empty state: "Setup checklist" guide when no orders are present.
   - Date range presets: Today, This Week, This Month, This Year, All Time, Custom.

3. **Fund Balance screen (manual entry)** (`:feature-settings` or inline widget)
   - Implement manual fund balance entry: user inputs current Zerodha available balance.
   - Store in `fund_balances` table with `entry_type = MANUAL`.
   - Display current balance and last updated timestamp on Portfolio screen header.

**Dependencies:** Sprints 1.1, 1.4  
**Complexity:** L  
**Effort:** 10–12 days  
**Risks:**

- Chart library selection: MPAndroidChart is battle-tested but has a complex API. Compose Canvas alternative is fully custom but requires more implementation effort. Decide before sprint start.
- P&L formula correctness for edge cases: instruments with multiple buy lots, partial sells, full exit followed by re-buy. All must have automated tests with hand-verified fixture data.

**Exit Criteria:**

- P&L matches hand-calculated expected values for 20+ order fixture scenarios.
- Date range filters produce correct results in unit tests.
- Portfolio screen renders charts on device without performance issues.
- Empty state renders correctly with no orders.

---

### Sprint 1.7 — Holdings, Orders, and Transactions Screens (Weeks 12–13)

#### Scope

1. **Holdings screen** (`:feature-holdings`)
   - Display list of current holdings from `holdings` table.
   - Per holding: instrument symbol, quantity, average buy price, target sell price, current P&L indicator (unrealized, for display only — no live price feed), profit target %.
   - Tap to view stock detail: full lot breakdown, cost basis, linked GTT status.
   - Edit profit target percentage per holding.

2. **Orders screen** (`:feature-orders`)
   - Display list of all synced orders from `orders` table, ordered by `order_timestamp DESC`.
   - Columns: date, instrument, buy/sell, quantity, price, total value, charges.
   - Paging 3 integration for large datasets (> 500 rows).

3. **Transactions screen** (`:feature-transactions`)
   - Display list of all transaction records from `transactions` table.
   - Columns: date, type (BUY/SELL/FUND_CREDIT/FUND_DEBIT), instrument (if applicable), amount, charges.
   - Basic filter: by type.
   - Paging 3 integration.

4. **Settings screen** (`:feature-settings`)
   - Implement Settings screen with: app version display, theme toggle, API key display (masked), logout/re-auth option, about page, guidebook stub, Privacy & Security stub.

**Dependencies:** Sprints 1.1, 1.4, 1.6  
**Complexity:** M (per screen)  
**Effort:** 8–10 days  
**Risks:** Paging 3 with Compose has composability nuances (LazyColumn + PagingItems). Test pagination boundary conditions (last page, empty page, error page).

**Exit Criteria:**

- All three screens display correct live data from Room.
- Holdings list matches holdings computed from order history.
- Paging works correctly: next pages load on scroll, no duplicates, correct empty/error states.

---

### Sprint 1.8 — WorkManager Background Sync (Week 13–14)

#### Scope

1. **OrderSyncWorker** (`:infra-worker`)
   - Implement `OrderSyncWorker : CoroutineWorker` that executes `OrderSyncUseCase` + `GttAutomationEngine` + `HoldingsVerificationUseCase`.
   - Schedule daily at 16:30 IST using `PeriodicWorkRequest` with a `TimeConstraint`.
   - Implement exponential backoff retry policy: 3 retries, initial delay 5 minutes, max delay 30 minutes.
   - Write job result (success/failure/retry) to `background_job_audit` table.
   - Post a system notification on completion (if notifications enabled — opt-in).

2. **WorkManager schedule management** (`:core-data`, `:feature-settings`)
   - Implement `WorkSchedulerRepository` for enqueue/cancel of scheduled work.
   - Ensure `WorkManager` constraint: `NetworkType.CONNECTED`.
   - On device reboot: `WorkManager` automatically reschedules (uses Boot receiver internally).
   - Conflict resolution: if a manual sync is triggered while a background sync is running, the manual sync waits for the background sync's lock to release (Mutex in `OrderSyncUseCase`).

3. **Charge rate sync** (`:infra-worker`, if API available)
   - Implement `ChargeRateSyncWorker` to fetch current charge rates from Kite Connect and update `charge_rates` table.
   - Schedule: weekly, on Sunday at midnight.
   - If API unavailable: use hardcoded rates as default. Worker exits with `SUCCESS` even if rate fetch fails, to avoid endless retry on permanent unavailability.

**Dependencies:** Sprints 1.4, 1.5  
**Complexity:** M  
**Effort:** 5–7 days  
**Risks:** WorkManager on Android OEMs with aggressive battery optimization (Xiaomi MIUI, Realme, OnePlus) may not fire scheduled work reliably. Document this limitation in the in-app Guidebook and Settings screen ("Battery optimization: disable for KiteWatch").

**Exit Criteria:**

- Background sync fires at scheduled time on an unoptimized Android device.
- Job audit table records all sync attempts.
- Retry fires correctly on simulated network failure.
- Manual and background sync do not conflict (Mutex test).

---

**Phase 1 Exit Criteria (Overall):**

- Full onboarding flow completes on a fresh device.
- Order sync, GTT automation, holdings verification work end-to-end with a live Zerodha account.
- P&L calculations pass all 20+ fixture tests.
- Biometric lock gates the app correctly.
- Background sync fires daily at 16:30.
- All screens render correctly in Dark and Light themes.
- Zero `P0` crashes on a 2-hour real-usage smoke test.
- APK size < 25 MB.
- Release APK produced by CI, signed, and installable via sideload.

---

## Phase 2 — Expansion (Data Completeness and Backup)

**Objective:** Add historical data ingestion (CSV import), Google Drive and local backup/restore, Gmail-based fund detection, fund reconciliation, configurable scheduling, Excel export, notifications, and GTT screen enhancements.

**Duration:** 12–14 weeks  
**Prerequisite:** Phase 1 complete and stable  
**Team:** 1–2 engineers

---

### Sprint 2.1 — CSV Import Engine (Weeks 1–3 of Phase 2)

#### Scope

- Implement `CsvImportEngine` (`:infra-csv`) for three CSV formats:
  - Historical orders (Zerodha order history CSV export format).
  - Fund transactions (Zerodha ledger CSV format).
  - Manual adjustments (custom KiteWatch format).
- Implement per-row validation with structured error reporting: `List<CsvImportError(rowNumber, field, reason)>`.
- Implement transactional import: all rows succeed or the entire import is rolled back.
- Implement conflict detection: duplicate `order_id` → skip with warning (not error).
- Implement progress reporting via `Flow<CsvImportProgress>` for large files (> 1,000 rows).
- Add CSV import trigger to Orders screen and Transactions screen.

**Complexity:** L  
**Effort:** 10–12 days

---

### Sprint 2.2 — Google Drive Backup and Restore (Weeks 3–5 of Phase 2)

#### Scope

- Implement Google Sign-In OAuth flow for Drive scope (`drive.appdata` or `drive.file`).
- Implement `DriveBackupEngine` (`:infra-backup`): serialize all DB tables to JSON, write to Drive `appDataFolder`.
- Implement `DriveRestoreEngine`: list backup files from Drive, download, deserialize, restore to Room.
- Implement backup file naming: `kitewatch-backup-{versionCode}-{epoch}.json`.
- Implement backup integrity check: SHA-256 hash stored in backup file header, verified on restore.
- Implement restore conflict resolution: existing data + restored data → prompt user for strategy (Replace All / Merge / Cancel).
- Add Drive backup controls to Settings screen.

**Complexity:** L  
**Effort:** 10–12 days

---

### Sprint 2.3 — Local Backup and Excel Export (Weeks 5–6 of Phase 2)

#### Scope

- Implement local backup: write backup JSON to device `Downloads` directory via `MediaStore` API.
- Implement local restore: open file picker, load backup file, validate, restore.
- Implement Excel export using Apache POI (or `xlsx4j`):
  - Export: orders, transactions, holdings, fund transactions to multi-sheet `.xlsx` file.
  - Each sheet has frozen header row, auto-sized columns, currency formatting for monetary fields.
- Add local backup and export controls to Settings screen.

**Complexity:** M  
**Effort:** 6–8 days

---

### Sprint 2.4 — Gmail Fund Detection (Weeks 7–8 of Phase 2)

#### Scope

- Implement Gmail OAuth sign-in for `gmail.readonly` scope with narrowed query filter.
- Implement `GmailFundDetectionEngine` (`:infra-worker`):
  - Execute Gmail API `messages.list` with user-defined filter query (e.g., `from:noreply@zerodha.com subject:funds`).
  - Parse email body for credit/debit amount patterns using regex.
  - Create candidate `FundTransaction` records with `source = GMAIL_DETECTED`, `status = PENDING_CONFIRMATION`.
  - Surface candidates to user for confirmation before writing to database.
- Implement Gmail filter configuration UI in Settings.
- Implement confirmed-transaction write flow: user approves → write to `fund_transactions` table.

**Complexity:** L  
**Effort:** 8–10 days

---

### Sprint 2.5 — Fund Reconciliation (Weeks 8–9 of Phase 2)

#### Scope

- Implement `FundReconciliationUseCase`:
  - Fetch live Zerodha fund balance via API.
  - Compare with locally-computed balance: `opening_balance + Σ(credits) − Σ(debits) − Σ(trade_settlements)`.
  - Classify discrepancy: within tolerance (configurable, default ₹1) → RECONCILED; outside tolerance → DISCREPANCY.
  - Store reconciliation result in `fund_balances` table with `reconciliation_status`.
- Implement reconciliation trigger: daily at 09:30 IST via WorkManager.
- Implement manual reconciliation via Fund Balance screen refresh button.
- Display discrepancy as an alert banner on Portfolio screen when unresolved.

**Complexity:** M  
**Effort:** 5–7 days

---

### Sprint 2.6 — Configurable Scheduling and Notifications (Weeks 10–11 of Phase 2)

#### Scope

- Implement schedule configuration UI: per-task enable/disable, time-of-day picker.
- Implement `WorkSchedulerRepository.reschedule(task, config)` — cancels existing and re-enqueues with new parameters.
- Implement notification system:
  - `NotificationManager` wrapper for posting and cancelling notifications.
  - Notification channels: Sync Complete, Discrepancy Alert, GTT Action Required.
  - User opt-in per channel via Settings.
  - Notification content: sync summary (order count, GTT actions taken).

**Complexity:** M  
**Effort:** 5–7 days

---

**Phase 2 Exit Criteria:**

- CSV import handles all three formats, reports per-row errors, rolls back on failure.
- Drive backup/restore verified on at least two different Google accounts.
- Local backup/restore verified with schema version matching and version-mismatch error handling.
- Gmail fund detection creates pending candidates; user confirmation flow works.
- Fund reconciliation computes correct discrepancy for injected test cases.
- Scheduling config persists across app restart and WorkManager correctly re-enqueues.
- Notifications appear for sync completion (on a device without battery optimization).
- Excel export opens correctly in Microsoft Excel and Google Sheets.

---

## Phase 3 — Polish and Advanced Configuration

**Objective:** Enhance visualizations, harden edge cases, add advanced configuration options, accessibility improvements, comprehensive Guidebook, and scheduled Drive backup.

**Duration:** 8–10 weeks  
**Prerequisite:** Phase 2 complete

---

### Sprint 3.1 — Enhanced Portfolio Visualizations (Weeks 1–2 of Phase 3)

#### Scope

- Monthly P&L percentage bar chart (month-by-month for last 12 months).
- Charges breakdown stacked bar chart.
- Holdings allocation pie chart (by current value weight).
- Smooth animated chart transitions.
- All charts accessible: content descriptions for screen readers; data table view alternative.

**Complexity:** M  
**Effort:** 6–8 days

---

### Sprint 3.2 — Advanced Configuration and Scheduled Drive Backup (Weeks 3–4 of Phase 3)

#### Scope

- App lock timeout configuration: user-selectable from [1, 2, 5, 10, 30] minutes.
- Scheduled Google Drive backup: recurring WorkManager job, user-configurable frequency (daily/weekly).
- Charge rate refresh interval configuration.
- Reconciliation tolerance configuration (default ₹1, adjustable ₹0.50 – ₹100).

**Complexity:** S-M  
**Effort:** 4–5 days

---

### Sprint 3.3 — Advanced CSV Import and Excel Restore (Weeks 4–5 of Phase 3)

#### Scope

- Advanced CSV validation: per-row error table displayed in a scrollable dialog before confirming import.
- Excel restore (merge mode): import from `.xlsx` backup file with conflict resolution per-row.
- Post-import summary: records imported, records skipped, errors encountered.

**Complexity:** M  
**Effort:** 5–7 days

---

### Sprint 3.4 — Accessibility, Guidebook, and Final Polish (Weeks 6–8 of Phase 3)

#### Scope

- Accessibility audit: font scaling support, minimum touch target size (48×48dp), contrast ratio audit against WCAG AA.
- Comprehensive in-app Guidebook with: CSV format documentation, feature step-by-step guides, troubleshooting section.
- Final edge case hardening from accumulated bug backlog.
- Performance profiling: run Android Profiler on all main screens, resolve any frame drops or memory leaks.
- Final security review: confirm no sensitive data in logs, in SharedPreferences, or in crash reports.

**Complexity:** M  
**Effort:** 8–10 days

---

**Phase 3 Exit Criteria:**

- All accessibility checks pass (font scaling, touch targets, contrast).
- Guidebook content complete and reviewed.
- No memory leaks detected by LeakCanary across all main user journeys.
- No frame drops > 16ms on main thread during standard interactions on a mid-range device.
- All Phase 3 feature tests pass.
- Zero `P0`/`P1` open bugs.
- Final release APK < 28 MB.

---

## Risk Register

| # | Risk | Probability | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Kite Connect free-tier endpoint unavailable (e.g., charge rates, GTT) | Medium | High | Implement fallback: hardcoded charge rates, graceful degradation on GTT API absence. Confirm endpoint availability in Phase 0 before writing dependent code. |
| R2 | Kite Connect session model requires more frequent re-auth than anticipated | Medium | Medium | Implement token refresh detection early. Design auth flow to be low-friction for the user (one-tap re-auth, not full OAuth restart). |
| R3 | Google OAuth scope approval delays or scope limitations restrict Gmail/Drive access | Low | High | Verify OAuth scope availability before Phase 2 sprint start. Design Gmail and Drive features as fully optional with graceful absence. |
| R4 | P&L calculation discrepancy with Zerodha contract notes | Medium | High | Extensive fixture testing (20+ real order scenarios) before Phase 1 release. Treat contract note as golden reference. |
| R5 | WorkManager background task killed by OEM battery optimization | High | Medium | Document workaround in Guidebook. Implement in-app prompt to disable battery optimization for KiteWatch on first background sync attempt. |
| R6 | Room database migration failure on user device | Low | Critical | No destructive migration. All migrations tested against prior schema via `MigrationTestHelper`. Backup before any migration by prompting user if backup hasn't run recently. |
| R7 | Keystore loss | Very Low | Critical | Keystore backup in multiple secure locations. Document recovery procedures (requires new app distribution, user data backup/restore). |
| R8 | Scope creep from open questions in PRD (Q1–Q10) | Medium | Medium | Resolve all open questions (Q1–Q10) before Phase 1 sprint planning. Unresolved questions default to the most conservative implementation. |
| R9 | FIFO lot matching complexity for edge cases (corporate actions, transfers) | Low | Medium | Scope strictly to standard buy/sell orders. Corporate actions (splits, bonuses) are out of Phase 1–3 scope; discrepancies surfaced as holdings verification alerts. |
| R10 | APK sideloading friction for users on MIUI/other restricted Android skins | Medium | Low | Document sideloading process in Guidebook. Not an engineering risk — a UX/distribution risk. |

---

## Technical Debt Mitigation Strategy

| Debt Item | Incurred In | Resolution Sprint | Priority |
|---|---|---|---|
| Hardcoded charge rate constants | Phase 1 Sprint 1.4 | Phase 2 Sprint 2.6 (charge rate auto-refresh) | High |
| Stub Guidebook and Privacy pages | Phase 1 Sprint 1.7 | Phase 3 Sprint 3.4 | Medium |
| No crash reporting (logcat only) | Phase 1 | Phase 3 (opt-in Crashlytics evaluation) | Low |
| No pagination on Orders/Transactions in Phase 1 (< 100 orders) | Phase 1 Sprint 1.7 | Phase 1 Sprint 1.7 (Paging 3 from day one) | Resolved |
| Single fixed background sync time | Phase 1 Sprint 1.8 | Phase 2 Sprint 2.6 | Medium |
| Gmail detection regex brittleness | Phase 2 Sprint 2.4 | Phase 3 (pattern versioning) | Medium |

---

## Long-Term Evolution Strategy

| Horizon | Capability | Engineering Prerequisite |
|---|---|---|
| 6 months post-Phase 3 | Sector/category tagging | Add `sector` column to `holdings` table (migration), tag UI in Holdings screen |
| 9 months | Capital gains estimation | Holding period tracking already present in order schema; pure domain logic addition |
| 12 months | Dividend tracking | New `dividend_income` table and Transaction type; no schema breaking change |
| 12 months | Home screen widget | `RemoteViews` widget bound to Room DAO; separate `:feature-widget` module |
| 18 months | Multi-language support (Indian regional) | All strings already in `strings.xml`; add language-specific `values-hi/`, `values-bn/`, etc. |
| 18 months | Backup passphrase encryption | `BackupEngine` encryption layer addition; key derivation via PBKDF2; backward-compatible version field in backup header |
| 24 months | Stepped profit targets (OCO GTT) | Contingent on Zerodha GTT API supporting OCO; domain model extension to `List<ProfitTarget>` |

---

## Summary Schedule

| Phase | Calendar Duration | Cumulative Week |
|---|---|---|
| Phase 0 — Foundation | 2 weeks | 0 → 2 |
| Phase 1 — MVP | 12–14 weeks | 2 → 16 |
| Phase 1 stabilization buffer | 1–2 weeks | 16 → 18 |
| Phase 2 — Expansion | 12–14 weeks | 18 → 32 |
| Phase 2 stabilization buffer | 1 week | 32 → 33 |
| Phase 3 — Polish | 8–10 weeks | 33 → 43 |
| **Total** | **34–43 weeks** | |

Timeline assumes a single senior Android engineer. Adding a second engineer to Phase 2 can reduce Phase 2 duration by 4–5 weeks through parallelizing CSV/backup and Gmail/reconciliation workstreams.
