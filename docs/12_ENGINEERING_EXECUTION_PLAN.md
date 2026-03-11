# 12 — Engineering Execution Plan

**Product:** KiteWatch — Android Local-First Portfolio Management  
**Version:** 1.0  
**Last Updated:** 2026-03-11  
**Status:** Authoritative task-level implementation roadmap  
**Scope:** Zero code → production-ready APK across Phases 0–3  
**Estimated Duration:** 34–43 engineering weeks (1 senior Android engineer)  
**Task Count:** ~90 discrete tasks across 8 engineering phases

---

## Table of Contents

1. [Execution Principles](#1-execution-principles)
2. [Model Usage Strategy](#2-model-usage-strategy)
3. [Context Optimization Strategy](#3-context-optimization-strategy)
4. [Dependency Graph Overview](#4-dependency-graph-overview)
5. [Phased Engineering Plan](#5-phased-engineering-plan)
6. [Detailed Task Breakdown — Phase 0](#detailed-tasks-phase-0)
7. [Detailed Task Breakdown — Phase 1](#detailed-tasks-phase-1)
8. [Detailed Task Breakdown — Phase 2](#detailed-tasks-phase-2)
9. [Detailed Task Breakdown — Phase 3](#detailed-tasks-phase-3)
10. [Detailed Task Breakdown — Phase 4](#detailed-tasks-phase-4)
11. [Detailed Task Breakdown — Phase 5](#detailed-tasks-phase-5)
12. [Detailed Task Breakdown — Phase 6](#detailed-tasks-phase-6)
13. [Detailed Task Breakdown — Phase 7](#detailed-tasks-phase-7)
14. [Final Validation Milestones](#final-validation-milestones)

---

## 1. Execution Principles

### 1.1 Task Sizing Rules

- Every task must represent **1–3 focused engineering sessions** (approx. 2–8 hours of focused work).
- A task must affect **2–8 files** at most. Tasks that touch more than 8 files must be split.
- A task must not cross more than **two architectural layers** simultaneously (e.g., Domain + Data is acceptable; Database + Domain + UI in one task is not).
- Tasks that produce more than approximately **1,500–2,000 lines of new or modified code** must be split.
- Every task must have a discrete, verifiable acceptance criterion. "It works" is not an acceptance criterion.

### 1.2 Layer Isolation Rules

- **Database migrations** are never combined with domain logic changes in the same task.
- **Repository interface changes** and their implementations are separate tasks (interface in `:core-domain`, implementation in `:core-data`).
- **ViewModel/UI wiring** tasks may not also introduce new domain use cases. The use case must exist and be tested before the ViewModel task begins.
- **Security-sensitive code** (token storage, encryption, auth flows) is always in its own task, never bundled with feature work.
- Infrastructure modules (`:infra-*`) are built and tested before feature modules (`:feature-*`) consume them.

### 1.3 Incremental Commit Safety

- Every task must result in a **compilable, non-crashing state** after completion. Broken builds must not be committed to `develop`.
- If a task introduces a new database entity before the full data path is wired, use a placeholder DAO with no UI binding — the app must still launch.
- Schema version increments are committed **only** when the corresponding `Migration` object and `MigrationTestHelper` test are both present in the same commit.
- Feature-flag dead code is acceptable temporarily; incomplete UI paths must be gated by a `BuildConfig.FEATURE_X_ENABLED` flag if they are visible to the user.

### 1.4 Domain Logic Safety

- All business logic that involves monetary arithmetic must use `BigDecimal` exclusively. The use of `Double` or `Float` for any financial value is a blocking defect.
- Pure domain functions (no Android dependencies) must have 100% branch coverage in unit tests before the task is marked complete.
- Any state transition in a domain engine must have an **explicit failure path** — no silent swallowing of errors.
- Idempotency requirements (order deduplication, GTT creation) must be enforced at the domain layer, not the UI layer.

### 1.5 Schema Migration Safety

- The Room database schema JSON for version N must be committed to `core-database/schemas/{N}.json` **before** the `DATABASE_VERSION` is incremented to N+1.
- `fallbackToDestructiveMigration()` is never called in release builds. Violation is a blocking defect.
- Each migration is tested independently against the prior exported schema using `MigrationTestHelper` before the migration is considered complete.
- If a migration cannot be cleanly expressed as `ALTER TABLE` or `CREATE TABLE` statements, escalate to a schema redesign review before proceeding.

### 1.6 Test Completeness Gates

- A task is not complete if its unit tests are not written. Tests are part of the task, not a follow-up task.
- Integration tests for external API interactions must use a `MockWebServer` or equivalent — no live API calls in automated tests.
- UI tests for onboarding and auth flows must be written as Compose UI tests with fake ViewModels, not real UseCases.

---

## 2. Model Usage Strategy

### 2.1 Claude Opus (Thinking Mode)

**Reserved for the most complex, consequence-bearing reasoning tasks.** Use when an incorrect design decision would cascade into multiple subsystems or require expensive rework.

Use Claude Opus for:

- Multi-subsystem architecture design decisions (e.g., charge calculation engine design, FIFO lot-matching algorithm)
- Domain engine design where correctness is financially critical (P&L engine, GTT automation logic)
- Concurrency model design (WorkManager + Mutex + coroutine scope architecture)
- Data consistency invariant definitions across the full schema
- Security model design (token storage architecture, encryption key lifecycle)
- Any task where the task description includes the label **[Opus]**

**Do not use Opus for:** boilerplate generation, file edits, UI scaffolding, test generation, or routine refactoring. Cost discipline is essential across a 40-week project.

### 2.2 Claude Sonnet (Thinking Mode)

**Use for complex feature implementation where correctness matters but the domain is bounded.** This model balances reasoning depth with cost.

Use Claude Sonnet (Thinking) for:

- Room schema design and DAO query implementation
- Repository implementation that aggregates multiple data sources
- OAuth token exchange implementation and session management
- WorkManager Worker implementation with retry and backoff logic
- CSV import validation engine with structured error reporting
- Backup/restore serialization and integrity verification
- Any task labelled **[Sonnet-T]**

### 2.3 Claude Sonnet (Fast Mode)

**Default model for all code generation, scaffolding, editing, and test writing tasks.** The majority of tasks in this plan use this model.

Use Claude Sonnet (Fast) for:

- Module scaffold generation (empty modules, build files, stub classes)
- Jetpack Compose UI screen implementations (stateless composables driven by ViewModel state)
- ViewModel implementation (pure state mapping from UseCase results)
- Hilt module wiring
- Mapper class generation (Entity ↔ Domain)
- ProGuard/R8 rule additions
- GitHub Actions workflow file writing
- Test file generation (given a clear specification of what to test)
- Refactoring and code cleanup tasks
- Any task labelled **[Sonnet-F]**

---

## 3. Context Optimization Strategy

### 3.1 When to Start a New Chat

Start a fresh chat session at the following boundaries:

- Beginning any new engineering phase (Phase 0 → 1 → 2, etc.)
- After completing any database schema task that changes `DATABASE_VERSION`
- After completing the full domain engine (P&L, GTT, charge calculation)
- Before beginning any security-related task (auth, encryption, token storage)
- Before beginning backup/restore implementation
- Before beginning any CI/CD or deployment workflow task
- After approximately 8–10 task executions within the same subsystem
- Whenever the model begins producing contextually confused output (hallucinating file paths, inventing APIs)

### 3.2 Context Minimization Rules

- Load only the **architecture document most relevant to the current subsystem** into context. Do not load all 11 documents simultaneously.
- For database tasks: load `03_DATABASE_SCHEMA.md` only.
- For domain engine tasks: load `04_DOMAIN_ENGINE_DESIGN.md` + `03_DATABASE_SCHEMA.md` (for entity reference only).
- For UI tasks: load `05_APPLICATION_STRUCTURE.md` only.
- For security tasks: load `07_SECURITY_MODEL.md` only.
- For backup tasks: load `08_BACKUP_AND_RECOVERY.md` only.
- For deployment tasks: load `10_DEPLOYMENT_WORKFLOW.md` only.
- Never load `00_PRODUCT_SPECIFICATION.md` during implementation tasks — it is a product document, not an implementation reference.
- Load existing **source files** being modified rather than architecture docs describing them, once those files exist.

### 3.3 File Context Strategy Per Task

- Provide the model with the **current state of files being modified**, not outdated versions.
- When a task modifies an existing file, include the full current file content in context.
- When a task creates new files, provide only the interface/contract the new file must satisfy (the relevant repository interface, the relevant DAO interface).
- For test-writing tasks: include only the class under test and its direct dependencies (interfaces, not implementations).

### 3.4 Avoiding Context Explosion

- Do not accumulate output from previous tasks in the same chat session indefinitely. After 5–6 task completions, start a fresh session even within the same phase.
- Do not paste the entire module structure tree for tasks that affect only 1–2 modules.
- Use the task's "Required files to include as context" list as a strict upper bound — do not add files out of habit.

---

## 4. Dependency Graph Overview

### 4.1 Module Build Order (Strict)

The following order reflects compilation dependencies. A module may only be implemented after all modules it depends on are stable (compilable, core interfaces defined).

```
Tier 0 (no dependencies):
  :core-domain

Tier 1 (depends on :core-domain only):
  :core-database
  :core-network

Tier 2 (depends on Tier 0–1):
  :core-data
  :core-ui

Tier 3 (depends on Tier 0–2):
  :infra-auth
  :infra-worker
  :infra-backup
  :infra-csv

Tier 4 (depends on all tiers):
  :feature-onboarding
  :feature-auth
  :feature-portfolio
  :feature-holdings
  :feature-orders
  :feature-transactions
  :feature-gtt
  :feature-settings

Tier 5:
  :app  (wires everything together)
```

### 4.2 Critical Path

The following task chain represents the minimum path to a testable end-to-end flow. No shortcuts can be taken on this path without incurring blocking rework:

```
Gradle scaffold
  → :core-domain models + repository interfaces
    → :core-database entities + DAOs
      → :core-network Retrofit service
        → Kite OAuth flow (:infra-auth)
          → OrderSyncUseCase (:core-domain)
            → OrderSyncWorker (:infra-worker)
              → HoldingsComputationEngine (:core-domain)
                → PnLCalculationEngine (:core-domain)
                  → GttAutomationEngine (:core-domain)
                    → Portfolio screen (first end-to-end testable UI)
```

### 4.3 Foundational Modules

These modules must be **complete and stable before any feature work begins**. They are the highest leverage investment in the project:

| Module | Why Foundational |
|---|---|
| `:core-domain` | All business logic, all repository interfaces, all error types live here. Unstable interfaces here cascade into every other module. |
| `:core-database` | Every feature reads/writes through Room. Schema errors discovered late require migrations. |
| `:core-network` | API contract defines data shape for all remote data sources. |
| `:core-ui` | Shared design system components used by all 8 feature modules. |

### 4.4 Parallel Workstreams (Phase 3+)

Once the critical path is complete through the domain engine, the following can proceed in parallel if a second engineer is available:

- Stream A: Portfolio screen + P&L visualization
- Stream B: GTT automation + GTT screen
- Stream C: Biometric auth + app lock gate

---

## 5. Phased Engineering Plan

### Phase 0 — Project Setup and Infrastructure

**Objective:** Establish a compilable, multi-module Gradle project with CI/CD, code quality toolchain, secrets management, build variants, and base navigation shell. Zero product features. All infrastructure.

**Duration:** ~2 weeks  
**Risk Level:** Low — failure here is immediately visible and correctable  
**Depends On:** Nothing

**Completion Criteria:**

- All modules defined and compile clean with zero Ktlint/Detekt warnings.
- Three build variants produce signed APKs without error.
- All three GitHub Actions workflows fire correctly on their respective triggers.
- Base navigation shell launches on a physical device and navigates between all tab stubs.
- Theme toggle (Dark/Light) persists across app kill/restart.
- No secrets committed to the repository.

---

### Phase 1 — Database Layer

**Objective:** Implement the complete Room database schema, all entities, DAOs, type converters, and the initial schema export. Establish the migration infrastructure. All database tests pass against an in-memory database.

**Duration:** ~2 weeks  
**Risk Level:** Medium — schema design decisions made here are expensive to reverse post-migration  
**Depends On:** Phase 0

**Completion Criteria:**

- All entities defined with correct column types, constraints, and indexes per `03_DATABASE_SCHEMA.md`.
- All DAOs implemented with the complete query set.
- `DATABASE_VERSION = 1` schema JSON exported and committed.
- 100% of DAO operations covered by unit tests against in-memory Room database.
- `MigrationTestHelper` infrastructure in place and validated on schema v1.
- Hilt `DatabaseModule` provides `AppDatabase` singleton.

---

### Phase 2 — Domain Engine

**Objective:** Implement the complete domain layer: all domain models, repository interfaces, use cases, and the three core engines (charge calculation, holdings computation, P&L calculation). All engines verified by fixture-driven unit tests before any UI work begins.

**Duration:** ~3 weeks  
**Risk Level:** High — monetary calculation errors discovered post-release require a hotfix and damage user trust  
**Depends On:** Phase 1 (entity shapes inform domain model design)

**Completion Criteria:**

- All domain models defined as immutable `data class` with no Android imports.
- All repository interfaces defined.
- `ChargeCalculationEngine` produces results matching 15+ real Zerodha contract note fixtures within ₹1 tolerance.
- `HoldingsComputationEngine` produces correct lot-matching output for all FIFO edge cases (partial sell, full exit, re-buy, split fills).
- `PnLCalculationEngine` produces correct realized P&L for 20+ order sequence fixtures.
- `GttAutomationEngine` correctly identifies create/update/skip/flag-override scenarios.
- All engines have 100% branch coverage.
- Zero `Double` or `Float` usage in any monetary calculation path.

---

### Phase 3 — Core Feature Flows

**Objective:** Implement all data sources (Retrofit, Room DAO wiring), repository implementations, Kite Connect OAuth authentication, order sync use case, WorkManager background sync, and the biometric app lock. This phase produces the first functionally testable end-to-end flow.

**Duration:** ~4 weeks  
**Risk Level:** High — OAuth and API integration involve external systems; failures here block all dependent feature work  
**Depends On:** Phase 2

**Completion Criteria:**

- Full Kite Connect OAuth flow completes end-to-end on a physical device with a real account.
- `access_token` stored in `EncryptedSharedPreferences` and correctly injected into API requests.
- `OrderSyncUseCase` fetches, deduplicates, calculates charges, and persists orders from the live API.
- `GttAutomationEngine` creates/updates GTTs on Kite Connect after a sync containing new BUY orders.
- `HoldingsVerificationUseCase` detects injected discrepancies in integration tests.
- `OrderSyncWorker` schedules, fires, retries, and records job results in the audit table.
- Biometric lock gates all screens; 5-minute background threshold triggers re-auth.

---

### Phase 4 — UI Screens

**Objective:** Implement all five main screens (Portfolio, Holdings, Orders, Transactions, GTT), the onboarding flow, fund balance entry, and all Settings screens. All screens are driven by ViewModel state from real UseCases backed by real Room data.

**Duration:** ~4 weeks  
**Risk Level:** Medium — UI bugs are visible but not data-corruptive; most risks are cosmetic or UX  
**Depends On:** Phase 3

**Completion Criteria:**

- All five main screens render correct data from Room in both Dark and Light themes.
- Onboarding flow completes and persists account binding state.
- Empty states, error states, and loading states render correctly on all screens.
- Portfolio screen P&L values match the domain engine output for loaded fixture data.
- Holdings screen displays FIFO lot breakdown correctly.
- All Paging 3 scroll boundaries function correctly on Orders and Transactions screens.
- GTT screen correctly surfaces `ManualOverrideDetected` items.
- Zero Compose recomposition loops or `StateFlow` collection memory leaks detectable via LeakCanary.

---

### Phase 5 — Backup, Export, and Data Ingestion

**Objective:** Implement local and Google Drive backup/restore, CSV import engine, Excel export, and Gmail fund detection (all Phase 2 product features).

**Duration:** ~3 weeks  
**Risk Level:** Medium — data integrity on restore is critical; correctness issues here risk data loss  
**Depends On:** Phase 4 (stable data model required before serialization)

**Completion Criteria:**

- Drive backup/restore round-trips without data loss for a database with 500+ orders.
- Local backup/restore works correctly from the device `Downloads` directory.
- CSV import handles all three formats, reports per-row errors, and rolls back atomically on failure.
- Excel export opens and renders correctly in Google Sheets and Microsoft Excel.
- Gmail fund detection creates pending candidates; user confirmation flow persists confirmed transactions.
- Backup file integrity (SHA-256 verification) rejects tampered files.
- Schema version mismatch on restore surfaces an actionable error, not a crash.

---

### Phase 6 — Security Hardening

**Objective:** Implement and audit all security controls: token encryption, EncryptedSharedPreferences audit, network security config, ProGuard configuration validation, no-screenshot enforcement, data scrubbing in logs, and secure deletion.

**Duration:** ~1 week  
**Risk Level:** High — security vulnerabilities in a financial app are critical and may be non-obvious  
**Depends On:** Phase 3 (auth infrastructure must exist before it can be hardened)

**Completion Criteria:**

- `EncryptedSharedPreferences` used for all sensitive preferences; plaintext `SharedPreferences` contains no sensitive fields.
- `network_security_config.xml` disables cleartext traffic and pins certificates where applicable.
- ProGuard/R8 obfuscation validated: staging APK passes full smoke test with no `ClassNotFoundException`.
- `FLAG_SECURE` set on `MainActivity` window.
- All log statements scrubbed of financial values, instrument symbols, and user-identifiable fields.
- Secure data deletion path (account reset) wipes `EncryptedSharedPreferences` and Room database.
- APK signature verified via `apksigner verify` in CI.

---

### Phase 7 — Testing, Performance, and Release Preparation

**Objective:** Complete integration test suite, Compose UI tests for critical flows, performance profiling, memory leak audit, final ProGuard validation, release APK production, and version manifest publication.

**Duration:** ~2 weeks  
**Risk Level:** Low (systemic) — issues found here are fixed before release, not after  
**Depends On:** All previous phases

**Completion Criteria:**

- All unit tests pass (target: > 85% line coverage on `:core-domain`).
- Integration tests cover: order sync end-to-end, GTT automation, CSV import, backup/restore round-trip.
- Compose UI tests cover: onboarding flow, biometric lock gate, portfolio screen data rendering.
- No memory leaks detected by LeakCanary on any main user journey.
- No janky frames (> 16ms) on Portfolio, Holdings, and Orders screens on a mid-range device (Snapdragon 680 or equivalent).
- Release APK < 25 MB.
- APK signed and SHA-256 hash committed to GitHub Release draft.
- Version manifest JSON published and in-app update check returns correct result.
- All P0 and P1 bugs resolved.

---

 —*

*Phases 0–7 detailed task breakdown to follow in subsequent generation phases.*

---

## Detailed Tasks — Phase 0: Project Setup and Infrastructure {#detailed-tasks-phase-0}

---

### T-001 — Repository Initialization and Branch Strategy

**Phase:** 0 — Project Setup
**Subsystem:** Version Control

**Description:**
Create the GitHub repository, configure `.gitignore`, establish branch protection rules on `main` and `develop`, and create the integration branch. This is the first task — all subsequent work flows through this structure.

**Scope Boundaries**

- Files affected: `.gitignore`, `README.md`, GitHub repository settings
- Modules affected: None
- Explicitly NOT touching: Any Gradle files, source code, CI workflows

**Implementation Steps**

1. Create private GitHub repository named `kitewatch`.
2. Write `.gitignore`: Android standard entries plus `secrets.properties`, `*.jks`, `*.keystore`, `/local.properties`, `google-services.json`.
3. Write minimal `README.md`: project name, one-sentence description, sideloaded APK distribution notice, placeholder setup/build sections.
4. Push initial commit to `main` with only `.gitignore` and `README.md`.
5. Create `develop` branch from `main`.
6. Configure branch protection on `main`: require 1 PR reviewer, require CI pass, disallow force push, disallow deletion.
7. Configure branch protection on `develop`: require CI pass, disallow force push.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual verification: Attempt a direct push to `main` — confirm it is rejected. Create `secrets.properties` locally — confirm `git status` does not track it.

**Acceptance Criteria**

- `main` rejects direct pushes.
- `develop` branch exists.
- `secrets.properties` and `*.jks` files are gitignored.
- `README.md` is committed.

**Rollback Strategy:**
Delete repository and recreate. Zero code exists; rollback has no cost.

**Estimated Complexity:** XS

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Pure configuration; no algorithmic reasoning required.

**Context Strategy**

- Start new chat? No (first task in project)
- Required files: None
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §1.1–§1.2
- Documents NOT required: All others

---

### T-002 — Gradle Multi-Module Project Scaffold and Version Catalog

**Phase:** 0 — Project Setup
**Subsystem:** Build System

**Description:**
Create the complete Gradle multi-module skeleton: all 18 module stubs, `libs.versions.toml` version catalog, root build file, and Gradle wrapper pinned to 8.6+. Every module compiles as an empty stub.

**Scope Boundaries**

- Files affected: `settings.gradle.kts`, `build.gradle.kts` (root), `gradle/libs.versions.toml`, per-module `build.gradle.kts` (18 files), `gradle.properties`, `gradle-wrapper.properties`
- Modules affected: All 18 modules created as stubs
- Explicitly NOT touching: Convention plugins, CI, secrets, source code

**Implementation Steps**

1. Bootstrap `:app` module via Android Studio new project wizard: `compileSdk=35`, `minSdk=26`, `targetSdk=35`, `applicationId="com.kitewatch.app"`.
2. Pin Gradle 8.6+ in `gradle-wrapper.properties`.
3. Create `gradle/libs.versions.toml` with version entries for: Kotlin 2.0+, AGP 8.3+, KSP, Compose BOM, AndroidX lifecycle, Room, Hilt, Retrofit, OkHttp, Moshi, Coroutines, WorkManager, DataStore, Paging 3, Timber, JUnit, MockK, Turbine, Robolectric.
4. Add 17 library module stubs to `settings.gradle.kts` via `include()`. Each stub has a minimal `build.gradle.kts` with appropriate plugin (`com.android.library` or `java-library` for `:core-domain`).
5. Apply `compileSdk`, `minSdk`, `targetSdk` to all Android library modules.
6. Set `gradle.properties`: `org.gradle.daemon=true`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `kotlin.incremental=true`, `org.gradle.jvmargs=-Xmx4g`.
7. Run `./gradlew assembleDebug` — confirm success.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: `./gradlew assembleDebug` succeeds. `./gradlew dependencies` resolves without conflict. All 18 modules visible in Android Studio.

**Acceptance Criteria**

- All 18 modules defined in `settings.gradle.kts`.
- `./gradlew assembleDebug` succeeds with zero errors.
- `:core-domain` uses `java-library` plugin (no Android dependency).
- Gradle wrapper pinned at 8.6+.

**Rollback Strategy:** Delete Gradle structure and recreate.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: High-volume boilerplate scaffold generation.

**Context Strategy**

- Start new chat? No
- Required files: `settings.gradle.kts`, `build.gradle.kts` (root)
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §1.1 (Module Dependency Graph)
- Documents NOT required: Domain, schema, security documents

---

### T-003 — Convention Plugins, Build Variants, and Signing Configuration

**Phase:** 0 — Project Setup
**Subsystem:** Build System

**Description:**
Implement Gradle convention plugins to eliminate per-module boilerplate. Configure three build variants (`debug`, `staging`, `release`) with signing config and `BuildConfig` field injection sourced from `secrets.properties`.

**Scope Boundaries**

- Files affected: `build-logic/` directory (new), 3–4 convention plugin files, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Modules affected: `:app`, `build-logic`
- Explicitly NOT touching: Feature modules (consume convention plugins), source code

**Implementation Steps**

1. Create `build-logic/` as an included Gradle build.
2. Write `AndroidLibraryConventionPlugin`, `AndroidApplicationConventionPlugin`, `AndroidComposeConventionPlugin` — apply SDK versions, Kotlin plugin, Compose BOM.
3. Apply convention plugins to `:app` and all `:feature-*` / `:core-ui` modules.
4. Configure `app/build.gradle.kts` build types: `debug` (debuggable, no minify, `.debug` suffix), `staging` (R8 enabled, `.staging` suffix, release signing), `release` (R8 full mode, release signing, no suffix).
5. Configure `signingConfigs.release` reading keystore from `secrets.properties` via `gradle-secrets-plugin`.
6. Add `BuildConfig` fields: `KITE_API_KEY`, `GOOGLE_OAUTH_CLIENT_ID`, `BUILD_VARIANT`.
7. Create skeleton `proguard-rules.pro` with comment placeholders for each library category.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: `./gradlew assembleStagingRelease` produces a signed APK. `apksigner verify --print-certs` confirms release keystore. `BuildConfig.KITE_API_KEY` is non-empty in debug build.

**Acceptance Criteria**

- All three variants produce APKs without error.
- Release and staging are signed with the release keystore.
- `BuildConfig` fields populated from `secrets.properties`, never hardcoded.
- Convention plugins eliminate SDK version duplication.

**Rollback Strategy:** Revert to per-module explicit configuration.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Convention plugin patterns are well-established structured boilerplate.

**Context Strategy**

- Start new chat? No
- Required files: `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §2.1–§2.2
- Documents NOT required: All domain, schema, security documents

---

### T-004 — Secrets Management and R8/ProGuard Validation

**Phase:** 0 — Project Setup
**Subsystem:** Build System / Security Infrastructure

**Description:**
Write complete R8/ProGuard rules for all reflection-dependent libraries. Establish `secrets.properties.template`. Confirm the release build survives R8 full mode on the empty scaffold without `ClassNotFoundException`.

**Scope Boundaries**

- Files affected: `app/proguard-rules.pro`, `secrets.properties.template` (new)
- Modules affected: `:app`
- Explicitly NOT touching: Source classes (rules written as forward-compatible patterns)

**Implementation Steps**

1. Write ProGuard rules for: Room (`@Entity`, `@Dao`, `RoomDatabase` subclasses), Moshi (`@JsonClass`, `@Json` fields), Hilt (all annotation types), Retrofit (interfaces, `Call` wrappers), OkHttp (`-dontwarn` for known false positives), WorkManager (`Worker` and `CoroutineWorker` subclasses), Kite DTO wildcard keep (`-keep class com.kitewatch.core.network.dto.** { *; }`).
2. Create `secrets.properties.template` with all keys set to placeholder values and comments explaining where each is obtained.
3. Run `./gradlew assembleRelease` — zero R8 errors, zero unintended warnings.
4. Install release APK on device — confirm launch without crash.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: Install release APK on device; app must launch without `ClassNotFoundException`.

**Acceptance Criteria**

- `./gradlew assembleRelease` produces APK with zero R8 errors.
- `secrets.properties.template` committed; `secrets.properties` gitignored.
- Release APK launches on device.

**Rollback Strategy:** Disable `isMinifyEnabled` temporarily; track as debt.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: ProGuard rules follow library-specific documented patterns.

**Context Strategy**

- Start new chat? No
- Required files: `app/proguard-rules.pro`
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §2.2
- Documents NOT required: All others

---

### T-005 — Code Quality Toolchain: Ktlint and Detekt

**Phase:** 0 — Project Setup
**Subsystem:** Developer Tooling

**Description:**
Integrate Ktlint and Detekt. Configure project-appropriate rule sets (disabling Compose-incompatible Detekt rules). Establish pre-commit hooks that block non-compliant commits.

**Scope Boundaries**

- Files affected: Root `build.gradle.kts`, `.editorconfig`, `detekt.yml` (new), `git-hooks/pre-commit` (new)
- Modules affected: All (project-wide)

**Implementation Steps**

1. Apply `jlleitschuh.gradle.ktlint` at root project level.
2. Create `.editorconfig`: `indent_size=4`, `max_line_length=120`, `insert_final_newline=true`.
3. Apply `io.gitlab.arturbosch.detekt`; generate `detekt.yml` via `./gradlew detektGenerateConfig`.
4. Disable in `detekt.yml`: `MagicNumber`, `LongMethod`, `FunctionNaming` (Compose incompatibilities).
5. Write `git-hooks/pre-commit`: runs `./gradlew ktlintCheck` and `./gradlew detekt`; exits non-zero on failure.
6. Add Gradle `setup` task: symlinks `git-hooks/pre-commit` to `.git/hooks/pre-commit`, marks executable.
7. Run both tools — fix any issues on empty scaffold.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: Introduce a deliberate Ktlint violation (trailing whitespace), attempt commit — hook blocks it. Remove violation, attempt again — commit succeeds.

**Acceptance Criteria**

- `./gradlew ktlintCheck` passes on clean scaffold.
- `./gradlew detekt` passes on clean scaffold.
- Pre-commit hook blocks non-compliant commits.
- `./gradlew setup` installs hook correctly on fresh clone.

**Rollback Strategy:** Remove plugin applications and delete config files.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Standard tooling configuration.

**Context Strategy**

- Start new chat? No
- Required files: Root `build.gradle.kts`, `gradle/libs.versions.toml`
- Architecture docs to reference: None required
- Documents NOT required: All

---

### T-006 — GitHub Actions CI/CD Workflows

**Phase:** 0 — Project Setup
**Subsystem:** CI/CD

**Description:**
Implement all three GitHub Actions workflow files and configure repository secrets. Validate all three workflows fire correctly.

**Scope Boundaries**

- Files affected: `.github/workflows/ci-pr.yml`, `.github/workflows/ci-staging.yml`, `.github/workflows/ci-release.yml`
- Modules affected: None

**Implementation Steps**

1. Write `ci-pr.yml`: triggers on PR to `main`/`develop`; steps: checkout, JDK 17 Temurin + Gradle cache, wrapper validation, Ktlint, Detekt, `testDebugUnitTest`, upload test results, assemble debug APK.
2. Write `ci-staging.yml`: triggers on push to `develop`; steps: checkout, JDK 17, decode keystore from base64 secret, write `secrets.properties` from secrets, run unit tests, assemble staging APK, upload artifact (14-day retention).
3. Write `ci-release.yml`: triggers on `v*.*.*` tag push to `main`; steps: checkout (`fetch-depth:0`), JDK 17, decode keystore, write secrets, run release unit tests, assemble release APK, `apksigner verify`, compute SHA-256, create **draft** GitHub Release with APK + SHA-256 attachments.
4. Add all 6 required secrets to GitHub repository settings.
5. Open test PR — confirm `ci-pr.yml` passes. Push to `develop` — confirm `ci-staging.yml` produces downloadable APK. Push `v0.0.1-test` tag — confirm draft release created.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Inject a deliberate Ktlint failure in a PR — confirm CI blocks the merge.
- Download staging APK artifact and install on device.

**Acceptance Criteria**

- All three workflows complete on their triggers.
- PR workflow fails on injected Ktlint violation.
- Staging APK artifact is downloadable.
- Release draft contains APK and SHA-256 file.
- No secrets appear in workflow log output.

**Rollback Strategy:** Delete or disable workflow files.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: GitHub Actions YAML configuration following `10_DEPLOYMENT_WORKFLOW.md` §3.2 directly.

**Context Strategy**

- Start new chat? No
- Required files: None (generating new files)
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §3.2
- Documents NOT required: All domain, schema documents

---

### T-007 — Application Class, Hilt Root, and Timber

**Phase:** 0 — Project Setup
**Subsystem:** Application Infrastructure

**Description:**
Implement `KiteWatchApplication` with `@HiltAndroidApp`, establish root Hilt DI modules (stubs for network, database, preferences), and configure Timber with a `DebugTree` for debug builds and a no-op `ReleaseTree` for release.

**Scope Boundaries**

- Files affected: `KiteWatchApplication.kt`, `AndroidManifest.xml`, `NetworkModule.kt` (stub), `DatabaseModule.kt` (stub), `PreferencesModule.kt` (stub), `AppModule.kt`
- Modules affected: `:app`, `:core-network`, `:core-database`, `:core-data`

**Implementation Steps**

1. Add Hilt dependencies to `:app`, `:core-network`, `:core-database`, `:core-data`.
2. Implement `KiteWatchApplication : Application()` annotated `@HiltAndroidApp`. Register in `AndroidManifest.xml`.
3. Implement `AppModule.kt` in `:app`: provides `@ApplicationContext Context` and application-scope `CoroutineScope`.
4. Implement stub `NetworkModule`, `DatabaseModule`, `PreferencesModule` — empty `@Module @InstallIn(SingletonComponent::class)` objects to be populated in later phases.
5. Add Timber to `:app`.
6. In `KiteWatchApplication.onCreate()`: plant `Timber.DebugTree()` if `BuildConfig.DEBUG`; plant no-op `ReleaseTree` otherwise. `ReleaseTree.log()` does nothing.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test: Verify Timber trees planted correctly per build type using `Timber.forest().size`.
- Manual: In debug build, `Timber.d("App started")` appears in logcat. In release build, it does not.

**Acceptance Criteria**

- App launches without crash on device.
- Hilt injection compiles: a `@HiltAndroidTest` test with an injected empty component passes.
- Timber logs appear in debug only.
- `ReleaseTree` does not forward log calls.

**Rollback Strategy:** Revert to manual dependency instantiation. Represents a full architecture reset — should not be needed.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Established Hilt application setup pattern.

**Context Strategy**

- Start new chat? Yes (new subsystem: application infrastructure)
- Required files: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §2, `01_SYSTEM_ARCHITECTURE.md` §2.4
- Documents NOT required: Schema, domain engine, security, backup documents

---

### T-008 — Navigation Host, Theme System, and DataStore Theme Persistence

**Phase:** 0 — Project Setup
**Subsystem:** Presentation Infrastructure

**Description:**
Implement `MainActivity` with a Compose `NavHost`, wire all feature module navigation graphs as placeholder screens, implement `KiteWatchTheme` with Material 3 Dark/Light support, and persist the theme preference via `DataStore<Preferences>`.

**Scope Boundaries**

- Files affected: `MainActivity.kt`, `AppNavGraph.kt`, `KiteWatchTheme.kt`, `Color.kt`, `Type.kt`, `ThemePreferenceRepository.kt`
- Modules affected: `:app`, `:core-ui`, `:core-data`

**Implementation Steps**

1. Add `navigation-compose`, `datastore-preferences`, Compose dependencies to appropriate modules.
2. Implement `KiteWatchTheme` in `:core-ui`: `@Composable fun KiteWatchTheme(darkTheme: Boolean, content: @Composable () -> Unit)` using `MaterialTheme` with custom `ColorScheme` for dark and light.
3. Implement `ThemePreferenceRepository`: `Flow<Boolean> isDarkTheme`, `suspend fun setDarkTheme(dark: Boolean)` via `DataStore<Preferences>`.
4. Implement `MainActivity` (`@AndroidEntryPoint`): collects `isDarkTheme` via `collectAsStateWithLifecycle()`, wraps `AppNavGraph` in `KiteWatchTheme`.
5. Implement `AppNavGraph.kt`: `NavHostComposable` with five top-level routes (Portfolio, Holdings, Orders, Transactions, Settings), each rendering placeholder `Text("Screen")`. Add `BottomNavigationBar` with five tabs.
6. Stub non-tab routes: `OnboardingGraph`, `AuthLockScreen`, `GttScreen`.
7. Wire `ThemePreferenceRepository` into `PreferencesModule` Hilt binding.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test: `ThemePreferenceRepositoryTest` — write Dark preference, read via `Flow`, confirm updated emission using in-memory `DataStore`.
- Manual: Navigate all five tabs — no crash. Toggle Dark/Light — changes immediately. Kill/relaunch — preference persists.

**Acceptance Criteria**

- All five tabs navigate to placeholder screens without error.
- Theme toggle changes immediately without restart.
- Theme persists across kill/relaunch.
- Back-stack does not exit to OS when pressing back within tab.

**Rollback Strategy:** Revert to single-screen implementation if navigation causes issues.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Navigation and theme setup are well-specified Compose/Jetpack boilerplate.

**Context Strategy**

- Start new chat? No
- Required files: `KiteWatchApplication.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §3 (Navigation Architecture), §5 (Design System)
- Documents NOT required: Domain, schema, security, backup documents

---

### T-009 — Base Design System Component Stubs

**Phase:** 0 — Project Setup
**Subsystem:** Design System (`:core-ui`)

**Description:**
Implement functional (not placeholder) stub versions of the five shared design system components: `AlertBanner`, `SkeletonLoader`, `EmptyStateWidget`, `ConfirmationDialog`, and `FilterChipGroup`. These must accept the correct API signatures so feature modules wire them without future changes.

**Scope Boundaries**

- Files affected: 5 new Kotlin files in `core-ui/src/main/kotlin/.../components/`, 1 re-export file
- Modules affected: `:core-ui`

**Implementation Steps**

1. `AlertBanner(message: String, type: AlertType, onDismiss: (() -> Unit)? = null)`: `AlertType` sealed class (`Error`, `Warning`, `Info`, `Success`). `Card` with icon row and message. Dismissible if `onDismiss` provided.
2. `SkeletonLoader(modifier: Modifier, lines: Int = 3)`: animated shimmer rows via `InfiniteTransition` alpha animation.
3. `EmptyStateWidget(title: String, description: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)`: centered layout, icon placeholder, title, description, optional action button.
4. `ConfirmationDialog(title, message, confirmLabel, cancelLabel, onConfirm, onCancel)`: Material3 `AlertDialog`.
5. `FilterChipGroup<T>(options: List<T>, selectedOption: T, onSelect: (T) -> Unit, labelFor: (T) -> String)`: horizontal lazy row of `FilterChip`. Generic type `T`.
6. Create `Components.kt` re-export file for clean import surface.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Compose UI tests: `AlertBanner` renders correct message; `ConfirmationDialog` calls `onConfirm` when confirm button tapped; `FilterChipGroup` calls `onSelect` with correct item.

**Acceptance Criteria**

- All five components compile and render without error.
- `FilterChipGroup` is generic (`<T>`).
- `SkeletonLoader` animates on device.
- All components accept `Modifier` parameter.
- Compose UI tests pass.

**Rollback Strategy:** Replace with inline `Text()` placeholders in consuming modules.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Compose component implementation; well-defined API contracts.

**Context Strategy**

- Start new chat? No
- Required files: `KiteWatchTheme.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §6 (Reusable Component Strategy)
- Documents NOT required: All domain, schema, CI documents

---

### T-010 — Phase 0 Validation Milestone

**Phase:** 0 — Project Setup
**Subsystem:** All

**Description:**
Execute the complete Phase 0 exit criteria checklist. Verification task only — no new features. All items confirmed on both a development machine and CI before Phase 1 begins.

**Implementation Steps**

1. `./gradlew ktlintCheck` — zero violations.
2. `./gradlew detekt` — zero violations.
3. `./gradlew testDebugUnitTest` — all tests pass.
4. `./gradlew assembleDebug assembleRelease assembleStagingRelease` — all succeed.
5. Install release APK on API 26 device — launches without crash.
6. Install debug APK on API 34 device — navigate all tabs, toggle theme, confirm persistence.
7. Open test PR — `ci-pr.yml` passes on GitHub Actions.
8. Push to `develop` — `ci-staging.yml` produces downloadable staging APK artifact.
9. Confirm `secrets.properties` absent from repository.
10. Confirm `./gradlew setup` installs pre-commit hook on fresh clone.

**Acceptance Criteria**

- Every item in Phase 0 Completion Criteria (§5) verified and checked off.
- Zero open blocking issues before Phase 1 begins.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (clean context before database work)
- Required files: None
- Architecture docs to reference: None
- Documents NOT required: All

---

## Detailed Tasks — Phase 1: Database Layer {#detailed-tasks-phase-1}

---

### T-011 — Core Domain Value Objects: Paisa, ProfitTarget, AppError

**Phase:** 1 — Database Layer
**Subsystem:** `:core-domain`

**Description:**
Implement the three foundational value types that the entire domain depends on: the `Paisa` monetary value class, the `ProfitTarget` sealed interface, and the `AppError` sealed hierarchy. Pure Kotlin, zero Android dependencies.

**Scope Boundaries**

- Files affected: `model/Paisa.kt`, `model/ProfitTarget.kt`, `error/AppError.kt`
- Modules affected: `:core-domain`
- Explicitly NOT touching: Room entities, repositories, Android modules

**Implementation Steps**

1. Implement `Paisa` as `@JvmInline value class` wrapping `Long`: arithmetic operators (`+`, `-`, `*`, `/`), `applyBasisPoints(basisPoints: Int): Paisa` (formula: `(value * bps + 5000) / 10_000`), `toRupees(): BigDecimal` (display boundary only), `fromRupees(BigDecimal)` factory, `ZERO` constant. Division guards against divide-by-zero via `require()`.
2. Implement `ProfitTarget` sealed interface: `Percentage(basisPoints: Int)` and `Absolute(amount: Paisa)` variants. `computeTargetProfit(investedAmount: Paisa): Paisa` default method. `init` blocks enforce non-negative constraints.
3. Implement `AppError` sealed hierarchy: top-level categories `NetworkError`, `DatabaseError`, `AuthError`, `DomainError`, `ValidationError`, `BackupError`, each with typed subtypes covering all defined failure modes.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- `PaisaTest`: `applyBasisPoints(500)` on `Paisa(1_000_000L)` returns `Paisa(50_000L)`. `toRupees()` round-trip. Division by zero throws. Negative value subtraction produces negative result.
- `ProfitTargetTest`: 5% on ₹1,00,000 produces ₹5,000. Negative `basisPoints` constructor throws.
- `AppErrorTest`: `when` expression on `AppError` compiles only if all branches covered (sealed exhaustiveness).

**Acceptance Criteria**

- Zero `Double` or `Float` in any arithmetic path.
- `applyBasisPoints(500)` on `Paisa(1_000_000L)` returns exactly `Paisa(50_000L)`.
- `ProfitTarget.Percentage(-1)` throws `IllegalArgumentException`.
- All unit tests pass with 100% branch coverage on `Paisa` arithmetic.

**Rollback Strategy:** Pure Kotlin value types; revert files with no side effects.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Opus
- Recommended Mode: Thinking
- Reason: `Paisa.applyBasisPoints` rounding formula and `BigDecimal` display-boundary conversion are financially critical. Errors here propagate into every charge calculation in the system.

**Context Strategy**

- Start new chat? Yes (new phase: domain foundation)
- Required files: None (new files)
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §2.1, §2.2 exclusively
- Documents NOT required: All UI, database, CI, security documents

---

### T-012 — Core Domain Entity Models

**Phase:** 1 — Database Layer
**Subsystem:** `:core-domain`

**Description:**
Implement all domain entity data classes: `Order`, `Holding`, `Transaction`, `FundEntry`, `GttRecord`, `ChargeRateSnapshot`, `ChargeBreakdown`, `PnlSummary`, `AccountBinding`, and `SyncResult`. Immutable `data class` objects. No Room or serialization annotations. Constructor `init` blocks enforce BR-03, BR-04, BR-05.

**Scope Boundaries**

- Files affected: 10 new files in `core-domain/src/main/kotlin/.../model/`
- Modules affected: `:core-domain`
- Explicitly NOT touching: Room entities, repositories, use cases

**Implementation Steps**

1. `Order`: `orderId`, `zerodhaOrderId`, `stockCode`, `stockName`, `orderType: OrderType` (BUY/SELL), `quantity`, `price: Paisa`, `totalValue: Paisa`, `tradeDate: LocalDate`, `exchange: Exchange` (NSE/BSE), `settlementId: String?`, `source: OrderSource`. `init`: require `quantity > 0`, `price.value > 0`.
2. `Holding`: `holdingId`, `stockCode`, `stockName`, `quantity`, `avgBuyPrice: Paisa`, `investedAmount: Paisa`, `totalBuyCharges: Paisa`, `profitTarget: ProfitTarget`, `targetSellPrice: Paisa`, timestamps. `init`: require `quantity >= 0`.
3. `Transaction`: `transactionId`, `type: TransactionType`, `referenceId: String?`, `stockCode: String?`, `amount: Paisa`, `transactionDate: LocalDate`, `description: String`, `source: TransactionSource`.
4. `FundEntry`: `entryId`, `entryType: FundEntryType`, `amount: Paisa`, `entryDate: LocalDate`, `note: String?`, `gmailMessageId: String?`. `init`: require `amount.value > 0`.
5. `GttRecord`: `gttId`, `zerodhaGttId: String?`, `stockCode`, `triggerPrice: Paisa`, `quantity`, `status: GttStatus`, `isAppManaged: Boolean`, `lastSyncedAt: Instant?`.
6. `ChargeRateSnapshot`: all rate fields as typed Ints (bps) and `Paisa` (per-crore charges), `fetchedAt: Instant`.
7. `ChargeBreakdown`: `stt`, `exchangeTxn`, `sebiCharges`, `stampDuty`, `gst` — all `Paisa`. `fun total(): Paisa`.
8. `PnlSummary`: `realizedPnl`, `totalSellValue`, `totalBuyCostOfSoldLots`, `totalCharges`, `chargeBreakdown`, `dateRange: ClosedRange<LocalDate>`.
9. `AccountBinding`: `userId`, `userName`, `apiKey`, `boundAt: Instant`.
10. `SyncResult` sealed: `Success(newOrderCount, updatedGttCount)`, `NoNewOrders`, `Skipped(reason)`, `Partial(succeeded, failed)`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Constructor validation tests for `Order`, `Holding`, `FundEntry`. `ChargeBreakdown.total()` sum. `SyncResult` sealed exhaustiveness.

**Acceptance Criteria**

- All 10 models compile in `:core-domain` with zero Android imports.
- `Order`, `Holding`, `FundEntry` constructor validation tests pass.
- `ChargeBreakdown.total()` returns correct sum.
- No `Double` or `Float` monetary fields.

**Rollback Strategy:** Pure Kotlin data classes; revert files.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Data class generation from specification; structural correctness verified by tests.

**Context Strategy**

- Start new chat? No
- Required files: `Paisa.kt`, `ProfitTarget.kt`, `AppError.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §1.2 (domain model hierarchy list)
- Documents NOT required: All UI, CI, backup documents

---

### T-013 — Repository Interfaces

**Phase:** 1 — Database Layer
**Subsystem:** `:core-domain`

**Description:**
Define all repository interfaces. Pure contracts between domain and data layers — no Room types, no Retrofit, no Android imports. Implementations provided in `:core-data` (Phase 3).

**Scope Boundaries**

- Files affected: 8 new files in `core-domain/src/main/kotlin/.../repository/`
- Modules affected: `:core-domain`

**Implementation Steps**

1. `OrderRepository`: insert, insertAll, existsByZerodhaId, observeAll (Flow), observeByDateRange (Flow), getAll, getByStockCode.
2. `HoldingRepository`: upsert, updateQuantityAndPrice, observeAll (Flow), getByStockCode, getAll.
3. `TransactionRepository` (append-only — no update/delete): insert, insertAll, observeAll (Flow), observeByType (Flow).
4. `FundRepository`: insertEntry, observeEntries (Flow), getRunningBalance.
5. `GttRepository`: upsert, updateStatus, archive, observeActive (Flow), getByStockCode.
6. `ChargeRateRepository`: saveRates, getCurrentRates.
7. `AlertRepository`: insert, observeUnacknowledged (Flow), acknowledge.
8. `AccountBindingRepository`: bind, getBinding, isBound, clear.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Compile-time: confirm `:core-domain` has zero Android imports. Confirm `TransactionRepository` has no `update()`/`delete()` method.

**Acceptance Criteria**

- All 8 interfaces compile with zero Android imports.
- `TransactionRepository` has no update or delete methods (BR-10 enforced at compile time).
- Observable queries use `Flow<List<T>>`; one-shot reads use `suspend fun`.
- `AccountBindingRepository.clear()` exists.

**Rollback Strategy:** Pure interfaces; revert with no side effects.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Interface definition from specification.

**Context Strategy**

- Start new chat? No
- Required files: All T-011 and T-012 files
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §1.2 (repository interface list)
- Documents NOT required: All UI, CI, schema DDL documents

---

### T-014 — Room Entities: Orders, Holdings, and Junction Table

**Phase:** 1 — Database Layer
**Subsystem:** `:core-database`

**Description:**
Implement the Room `@Entity` classes for `orders`, `holdings`, and `order_holdings` junction table — the three most critical entities with complex index and constraint requirements.

**Scope Boundaries**

- Files affected: `OrderEntity.kt`, `HoldingEntity.kt`, `OrderHoldingEntity.kt`
- Modules affected: `:core-database`
- Explicitly NOT touching: DAOs, migrations, domain models

**Implementation Steps**

1. `OrderEntity`: `@Entity(tableName="orders", indices=[Index("zerodha_order_id", unique=true), Index("stock_code"), Index("trade_date"), Index("order_type")])`. Columns: `id` (Long PK autoincrement), `zerodha_order_id` (TEXT NOT NULL UNIQUE), `stock_code`, `stock_name`, `order_type`, `quantity` (INTEGER), `price_paisa` (INTEGER), `total_value_paisa` (INTEGER), `trade_date` (TEXT ISO-8601), `exchange` (TEXT), `settlement_id` (TEXT nullable), `source` (TEXT DEFAULT 'SYNC'), `created_at` (INTEGER epoch millis).
2. `HoldingEntity`: `@Entity(tableName="holdings", indices=[Index("stock_code", unique=true)])`. Columns: `id` (Long PK autoincrement), `stock_code` (TEXT UNIQUE), `stock_name`, `quantity` (INTEGER), `avg_buy_price_paisa`, `invested_amount_paisa`, `total_buy_charges_paisa` (DEFAULT 0), `profit_target_type` (DEFAULT 'PERCENTAGE'), `profit_target_value` (DEFAULT 500), `target_sell_price_paisa`, `created_at`, `updated_at`.
3. `OrderHoldingEntity`: `@Entity(tableName="order_holdings", primaryKeys=["order_id","holding_id"], foreignKeys=[FK(OrderEntity→order_id, CASCADE), FK(HoldingEntity→holding_id, CASCADE)], indices=[Index("order_id"), Index("holding_id")])`. Columns: `order_id` (Long), `holding_id` (Long), `quantity` (INTEGER).

**Data Impact**

- Schema changes: Introduces three tables into `DATABASE_VERSION = 1`
- Migration required: No (establishing baseline)

**Test Plan**

- In-memory Room: uniqueness constraint rejects duplicate `zerodha_order_id`. Cascade delete removes `order_holdings` row when parent `OrderEntity` deleted. `stock_code` uniqueness in `holdings`.

**Acceptance Criteria**

- All three entities compile.
- `zerodha_order_id` has UNIQUE constraint.
- `stock_code` in `holdings` has UNIQUE constraint.
- `order_holdings` has composite PK and two CASCADE foreign keys.
- All constraint tests pass.

**Rollback Strategy:** Remove from `AppDatabase.entities`; schema not yet exported.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Foreign key cascade semantics and composite primary key correctness in Room require careful validation.

**Context Strategy**

- Start new chat? Yes (new subsystem: database layer)
- Required files: None (new files)
- Architecture docs to reference: `03_DATABASE_SCHEMA.md` §2 (ER Diagram), §3 (DDL for orders, holdings)
- Documents NOT required: Domain engine, UI, CI, security documents

---

### T-015 — Room Entities: Transactions, Fund Entries, and GTT Records

**Phase:** 1 — Database Layer
**Subsystem:** `:core-database`

**Description:**
Implement `TransactionEntity`, `FundEntryEntity`, and `GttRecordEntity`. The transactions entity enforces append-only design by omitting `updated_at`.

**Scope Boundaries**

- Files affected: `TransactionEntity.kt`, `FundEntryEntity.kt`, `GttRecordEntity.kt`
- Modules affected: `:core-database`

**Implementation Steps**

1. `TransactionEntity`: `@Entity(tableName="transactions", indices=[Index("type"), Index("transaction_date"), Index("stock_code"), Index("reference_id")])`. Columns: `id` (Long PK), `type`, `sub_type` (nullable), `reference_id` (nullable), `stock_code` (nullable), `amount_paisa` (INTEGER), `transaction_date` (TEXT), `description` (DEFAULT ''), `source` (DEFAULT 'SYNC'), `created_at` (INTEGER). No `updated_at` column.
2. `FundEntryEntity`: `@Entity(tableName="fund_entries", indices=[Index("entry_date"), Index("entry_type"), Index("gmail_message_id")])`. Columns: `id` (Long PK), `entry_type`, `amount_paisa`, `entry_date`, `note` (nullable), `is_confirmed` (INTEGER DEFAULT 1), `gmail_message_id` (nullable), `created_at`.
3. `GttRecordEntity`: `@Entity(tableName="gtt_records", indices=[Index("stock_code"), Index("zerodha_gtt_id"), Index("status"), Index("is_archived")])`. Columns: `id` (Long PK), `zerodha_gtt_id` (nullable), `stock_code`, `trigger_price_paisa`, `quantity`, `status` (DEFAULT 'PENDING_CREATION'), `is_app_managed` (DEFAULT 1), `last_synced_at` (nullable INTEGER), `is_archived` (DEFAULT 0), `created_at`, `updated_at`.

**Data Impact**

- Schema changes: Three more tables into `DATABASE_VERSION = 1`
- Migration required: No

**Test Plan**

- In-memory Room: insert/read-back for all three. `FundEntryEntity` with `is_confirmed=0` correctly filtered. `GttRecordEntity` `is_archived=1` excluded from active queries.

**Acceptance Criteria**

- All three compile and register in `AppDatabase`.
- `TransactionEntity` has no `updated_at` column.
- `GttRecordEntity` has `is_archived` flag (soft delete).

**Rollback Strategy:** Remove from `AppDatabase.entities`.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Entity definitions following schema spec; structurally similar to T-014.

**Context Strategy**

- Start new chat? No
- Required files: `OrderEntity.kt`, `HoldingEntity.kt`
- Architecture docs to reference: `03_DATABASE_SCHEMA.md` §3 (DDL for transactions, fund_entries, gtt_records)
- Documents NOT required: All others

---

### T-016 — Room Entities: Supporting Tables

**Phase:** 1 — Database Layer
**Subsystem:** `:core-database`

**Description:**
Implement the 8 remaining supporting entity classes: `AccountBindingEntity`, `ChargeRateEntity`, `PersistentAlertEntity`, `SyncEventEntity`, `PnlMonthlyCacheEntity`, `WorkerHandoffEntity`, `GmailScanCacheEntity`, `GmailFilterEntity`.

**Scope Boundaries**

- Files affected: 8 new `*Entity.kt` files
- Modules affected: `:core-database`

**Implementation Steps**

1. `AccountBindingEntity`: `@Entity(tableName="account_binding")`. Single-row: `id=1L` (PK fixed). Columns: `zerodha_user_id`, `user_name`, `api_key`, `bound_at` (INTEGER).
2. `ChargeRateEntity`: indices on `rate_type`, `effective_from`. Columns: `id` (PK), `rate_type`, `rate_value` (INTEGER — bps or paisa/crore), `effective_from` (TEXT), `fetched_at` (INTEGER).
3. `PersistentAlertEntity`: indices on `acknowledged`, `alert_type`. Columns: `id`, `alert_type`, `payload` (TEXT JSON), `acknowledged` (INTEGER DEFAULT 0), `created_at`, `resolved_at` (nullable).
4. `SyncEventEntity`: indices on `event_type`, `status`, `started_at`. Columns: `id`, `event_type`, `started_at`, `completed_at` (nullable), `status` (DEFAULT 'RUNNING'), `details` (nullable TEXT), `error_message` (nullable TEXT).
5. `PnlMonthlyCacheEntity`: index on `year_month` (unique). Columns: `id`, `year_month` (TEXT UNIQUE 'YYYY-MM'), all P&L fields as INTEGER (paisa), `last_updated_at`.
6. `WorkerHandoffEntity`: columns: `id`, `worker_tag`, `payload` (TEXT), `created_at`, `consumed` (INTEGER DEFAULT 0).
7. `GmailScanCacheEntity`: index on `gmail_message_id` (unique), `status`. Columns: `id`, `gmail_message_id` (UNIQUE), `detected_type`, `detected_amount_paisa`, `email_date`, `email_subject` (nullable), `status` (DEFAULT 'PENDING'), `linked_fund_entry_id` (nullable Long), `scanned_at`.
8. `GmailFilterEntity`: columns: `id`, `filter_type`, `filter_value`, `is_active` (DEFAULT 1), `created_at`.

**Data Impact**

- Schema changes: Completes all tables for `DATABASE_VERSION = 1`
- Migration required: No

**Test Plan**

- In-memory Room: `AccountBindingEntity` single-row (INSERT OR REPLACE). `PnlMonthlyCacheEntity` unique `year_month` rejects duplicate. `GmailScanCacheEntity` unique `gmail_message_id` rejects duplicate.

**Acceptance Criteria**

- All 8 entities compile and registered in `AppDatabase` (total 14 entities).
- `AccountBindingEntity` uses fixed PK strategy.
- `PnlMonthlyCacheEntity.year_month` has UNIQUE constraint.
- All uniqueness tests pass.

**Rollback Strategy:** Remove from `AppDatabase.entities`.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Entity definition boilerplate.

**Context Strategy**

- Start new chat? No
- Required files: `OrderEntity.kt`, `GttRecordEntity.kt`
- Architecture docs to reference: `03_DATABASE_SCHEMA.md` §3 (supporting table DDL)
- Documents NOT required: All others

---

### T-017 — TypeConverters, AppDatabase Assembly, and Schema Export

**Phase:** 1 — Database Layer
**Subsystem:** `:core-database`

**Description:**
Implement `RoomTypeConverters` for `LocalDate` and `Instant`. Assemble `AppDatabase` with all 14 entities. Set `DATABASE_VERSION = 1`. Configure KSP schema export. Commit generated `1.json`.

**Scope Boundaries**

- Files affected: `AppDatabase.kt`, `RoomTypeConverters.kt`, `core-database/schemas/1.json` (generated), `core-database/build.gradle.kts`
- Modules affected: `:core-database`

**Implementation Steps**

1. Implement `RoomTypeConverters`: `LocalDate ↔ String` (ISO-8601 via `DateTimeFormatter.ISO_LOCAL_DATE`), `Instant ↔ Long` (epoch millis via `Instant.toEpochMilli()`). Annotate `@ProvidedTypeConverter`.
2. Assemble `AppDatabase.kt`: `@Database(entities=[all 14], version=1, exportSchema=true)`, `@TypeConverters(RoomTypeConverters::class)`. Abstract DAO functions (stubs referencing DAO interfaces from T-018–T-020). Companion `buildDatabase(context)` via `Room.databaseBuilder` with empty migration list.
3. Add to `core-database/build.gradle.kts`: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.
4. Run `./gradlew :core-database:kspDebugKotlin` — generates `schemas/1.json`.
5. Verify `1.json` contains all 14 table definitions. Commit to version control.

**Data Impact**

- Schema changes: Finalizes `DATABASE_VERSION = 1`
- Migration required: No

**Test Plan**

- `TypeConverterTest`: `LocalDate.of(2024,3,15)` converts to `"2024-03-15"` and back. `Instant.ofEpochMilli(1_700_000_000_000L)` converts to `Long` and back.
- In-memory Room opens with all 14 entities without `IllegalStateException`.

**Acceptance Criteria**

- `AppDatabase` compiles with all 14 entities.
- `DATABASE_VERSION = 1`.
- `schemas/1.json` committed; contains all 14 tables.
- TypeConverter tests pass.
- `fallbackToDestructiveMigration()` is NOT called.

**Rollback Strategy:** Remove entities from `AppDatabase`; schema export regenerated.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Database assembly and TypeConverter are standard Room boilerplate.

**Context Strategy**

- Start new chat? No
- Required files: All entity files from T-014–T-016
- Architecture docs to reference: `03_DATABASE_SCHEMA.md` §1 (monetary as Long, timestamps as epoch)
- Documents NOT required: All others

---

### T-018 — DAOs: Orders, Holdings, and Order-Holdings Junction

**Phase:** 1 — Database Layer
**Subsystem:** `:core-database`

**Description:**
Implement `OrderDao`, `HoldingDao`, and `OrderHoldingDao`. All observing queries use `Flow<List<T>>`; one-shot reads use `suspend fun`. `@Transaction` annotations where multi-table consistency is required.

**Scope Boundaries**

- Files affected: `OrderDao.kt`, `HoldingDao.kt`, `OrderHoldingDao.kt`
- Modules affected: `:core-database`

**Implementation Steps**

1. `OrderDao`: `@Insert(onConflict=IGNORE) insert/insertAll`, `existsByZerodhaId(id): Boolean`, `observeAll(): Flow` (DESC), `observeByDateRange(from, to): Flow`, `getAll(): List`, `getByStockCode(code): List`, `getBuyOrdersByStockCode(code): List`.
2. `HoldingDao`: `@Insert(onConflict=REPLACE) upsert`, `updateQuantityAndPrice(code, qty, avgPrice, invested, updatedAt)`, `observeActive(): Flow` (WHERE quantity>0), `getByStockCode(code): HoldingEntity?`, `getAll(): List`.
3. `OrderHoldingDao`: insert junction entries, query by `holding_id` for all associated order IDs.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- In-memory Room: `insert` returns `-1L` on duplicate `zerodha_order_id` (IGNORE). `observeActive()` excludes `quantity=0`. Date range returns only orders in range. `updateQuantityAndPrice` updates correct row.

**Acceptance Criteria**

- `OrderDao.insert()` returns `-1L` on duplicate (IGNORE behavior).
- `HoldingDao.observeActive()` filters `quantity=0` holdings.
- Date range query test passes.
- All in-memory tests pass.

**Rollback Strategy:** Remove DAO abstract declarations from `AppDatabase`.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: SQL query correctness for date range, conflict strategies, and Flow semantics require careful validation.

**Context Strategy**

- Start new chat? No
- Required files: `OrderEntity.kt`, `HoldingEntity.kt`, `OrderHoldingEntity.kt`, `AppDatabase.kt`
- Architecture docs to reference: `03_DATABASE_SCHEMA.md` §5 (Critical Queries)
- Documents NOT required: UI, CI, domain engine documents

---

### T-019 — DAOs: Transactions, Fund Entries, and GTT Records

**Phase:** 1 — Database Layer
**Subsystem:** `:core-database`

**Description:**
Implement `TransactionDao` (append-only — zero `@Update`/`@Delete` methods), `FundEntryDao`, and `GttRecordDao`.

**Scope Boundaries**

- Files affected: `TransactionDao.kt`, `FundEntryDao.kt`, `GttRecordDao.kt`
- Modules affected: `:core-database`

**Implementation Steps**

1. `TransactionDao`: `@Insert(onConflict=IGNORE) insert/insertAll`, `observeAll(): Flow` (DESC), `observeByType(type): Flow`, `getByStockCode(code): List`, `getTotalCredits(): Long?`, `getTotalDebits(): Long?`. Zero `@Update` or `@Delete` methods.
2. `FundEntryDao`: `@Insert(onConflict=REPLACE) insert`, `observeConfirmed(): Flow` (WHERE is_confirmed=1), `getPendingGmailEntries(): List` (WHERE is_confirmed=0), `confirm(id)` (UPDATE SET is_confirmed=1), `getTotalConfirmedFunds(): Long?`.
3. `GttRecordDao`: `@Insert(onConflict=REPLACE) upsert`, `updateStatus(id, status, updatedAt)`, `archive(id, updatedAt)` (SET is_archived=1), `observeActive(): Flow` (WHERE is_archived=0), `getActiveByStockCode(code): GttRecordEntity?`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- `TransactionDao`: insert three, `observeAll()` emits all in DESC order. Confirm no `@Update` or `@Delete` annotation present (reflection assertion).
- `FundEntryDao.getPendingGmailEntries()`: returns only `is_confirmed=0` rows.
- `GttRecordDao.observeActive()`: excludes `is_archived=1` rows.

**Acceptance Criteria**

- `TransactionDao` has zero `@Update` and zero `@Delete` methods.
- `FundEntryDao.confirm()` sets `is_confirmed=1` on the correct row only.
- `GttRecordDao.observeActive()` filters archived records.
- All unit tests pass.

**Rollback Strategy:** Remove from `AppDatabase` DAO declarations.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Consistent DAO pattern; structurally similar to T-018.

**Context Strategy**

- Start new chat? No
- Required files: `TransactionEntity.kt`, `FundEntryEntity.kt`, `GttRecordEntity.kt`
- Architecture docs to reference: `03_DATABASE_SCHEMA.md` §5
- Documents NOT required: All others

---

### T-020 — DAOs: Supporting Tables

**Phase:** 1 — Database Layer
**Subsystem:** `:core-database`

**Description:**
Implement all 8 remaining DAOs: `AccountBindingDao`, `ChargeRateDao`, `AlertDao`, `SyncEventDao`, `PnlMonthlyCacheDao`, `WorkerHandoffDao`, `GmailScanCacheDao`, `GmailFilterDao`.

**Scope Boundaries**

- Files affected: 8 new `*Dao.kt` files
- Modules affected: `:core-database`

**Implementation Steps**

1. `AccountBindingDao`: `@Insert(onConflict=REPLACE) insert`, `get(): AccountBindingEntity?`, `clear()` (DELETE all).
2. `ChargeRateDao`: `insertAll(List)`, `getLatest(): List` (WHERE effective_from = MAX), `pruneOlderThan(cutoff)`.
3. `AlertDao`: `insert: Long`, `observeUnacknowledged(): Flow`, `acknowledge(id, resolvedAt)`.
4. `SyncEventDao`: `insert: Long`, `update(id, completedAt, status, details, error)`, `getRecent(): List` (LIMIT 20).
5. `PnlMonthlyCacheDao`: `@Insert(onConflict=REPLACE) upsert`, `observeAll(): Flow` (DESC), `pruneOlderThan(cutoff)`.
6. `WorkerHandoffDao`: `insert: Long`, `getPending(tag): WorkerHandoffEntity?` (WHERE consumed=0 LIMIT 1), `markConsumed(id)`.
7. `GmailScanCacheDao`: `@Insert(onConflict=IGNORE) insert: Long`, `exists(messageId): Boolean`, `observePending(): Flow` (WHERE status='PENDING').
8. `GmailFilterDao`: `insert: Long`, `observeActive(): Flow` (WHERE is_active=1), `@Delete delete`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- `AccountBindingDao`: insert, retrieve, clear, confirm null after clear. `SyncEventDao`: insert RUNNING, update to SUCCESS, confirm status updated. `GmailScanCacheDao.exists()`: false before insert, true after.

**Acceptance Criteria**

- All 8 DAOs compile and registered as abstract functions in `AppDatabase`.
- `AccountBindingDao.clear()` deletes all rows.
- `SyncEventDao` two-phase pattern (INSERT + UPDATE) works correctly.
- All tests pass.

**Rollback Strategy:** Remove from `AppDatabase` abstract declarations.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: High volume, low complexity; consistent DAO pattern.

**Context Strategy**

- Start new chat? No
- Required files: All supporting entity files from T-016; `AppDatabase.kt`
- Architecture docs to reference: `03_DATABASE_SCHEMA.md` §5
- Documents NOT required: All others

---

### T-021 — Hilt DatabaseModule and MigrationTestHelper Infrastructure

**Phase:** 1 — Database Layer
**Subsystem:** `:core-database`

**Description:**
Complete the `DatabaseModule` Hilt binding providing `AppDatabase` and all DAOs as singletons. Establish `MigrationTestHelper` infrastructure with a baseline v1 schema test.

**Scope Boundaries**

- Files affected: `DatabaseModule.kt`, `MigrationTest.kt` (instrumented test)
- Modules affected: `:core-database`

**Implementation Steps**

1. Implement `DatabaseModule.kt` `@Module @InstallIn(SingletonComponent::class)`:
   - `@Singleton @Provides fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase` — calls `AppDatabase.buildDatabase(ctx)`.
   - One `@Provides` per DAO (all 16 DAOs bound as singletons from `AppDatabase`).
2. Replace stub `DatabaseModule` from T-007 with this implementation.
3. Write `MigrationTest.kt` (instrumented): `MigrationTestHelper` for `AppDatabase`. Test: `helper.createDatabase(TEST_DB, 1)` opens without error; raw SQL insert and read on `orders` table succeeds. This serves as the template for all future migration tests.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Hilt test: `AppDatabase` injected in `@HiltAndroidTest` test without error.
- Instrumented: `testOpenV1Database()` passes on connected device/emulator.

**Acceptance Criteria**

- `DatabaseModule` provides all 16 DAOs as singletons.
- `MigrationTestHelper` test passes on device.
- `AppDatabase.buildDatabase()` does not call `fallbackToDestructiveMigration()`.
- Database file created at `databases/kitewatch.db` on device.

**Rollback Strategy:** Revert to stub `DatabaseModule`; remove instrumented test.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Hilt module wiring and test scaffolding; established patterns.

**Context Strategy**

- Start new chat? No
- Required files: `AppDatabase.kt`, all DAO files
- Architecture docs to reference: `03_DATABASE_SCHEMA.md` §6 (Migration Strategy)
- Documents NOT required: All others

---

### T-022 — Phase 1 Validation Milestone

**Phase:** 1 — Database Layer
**Subsystem:** All

**Description:**
Execute Phase 1 exit criteria checklist. Verify DAO tests, schema export, Hilt injection, migration infrastructure, and domain model invariants.

**Implementation Steps**

1. `./gradlew :core-domain:test` — all domain model tests pass.
2. `./gradlew :core-database:testDebugUnitTest` — all in-memory DAO tests pass.
3. `./gradlew :core-database:connectedAndroidTest` — `MigrationTest` passes on device.
4. Verify `schemas/1.json` committed; contains all 14 table definitions.
5. Verify `:core-domain` has zero Android imports.
6. Verify `TransactionDao` has no `@Update`/`@Delete` methods.
7. Verify `AppDatabase` has no `fallbackToDestructiveMigration()`.
8. Verify `HoldingDao.observeActive()` excludes `quantity=0` in unit test.

**Acceptance Criteria**

- All Phase 1 Completion Criteria verified and checked off.
- Zero open blocking issues before Phase 2 begins.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (clean context before domain engine work)
- Required files: None
- Architecture docs to reference: None
- Documents NOT required: All

---

## Detailed Tasks — Phase 2: Domain Engine {#detailed-tasks-phase-2}

---

### T-023 — Charge Calculation Engine: Implementation

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Implement `ChargeCalculator` — a stateless, pure Kotlin object computing a complete `ChargeBreakdown` for a single equity delivery order given trade value, exchange, order type, and a `ChargeRateSnapshot`.

**Scope Boundaries**

- Files affected: `core-domain/src/main/kotlin/.../engine/ChargeCalculator.kt`
- Modules affected: `:core-domain`
- Explicitly NOT touching: DAOs, repositories, UI, WorkManager

**Implementation Steps**

1. Implement `ChargeCalculator.calculate(tradeValue: Paisa, orderType: OrderType, exchange: Exchange, rates: ChargeRateSnapshot): ChargeBreakdown`.
2. STT: `tradeValue.applyBasisPoints(if BUY rates.sttBuyBps else rates.sttSellBps)`. Delivery: 10 bps on both buy and sell.
3. Exchange Txn Charge: `tradeValue.applyBasisPoints(if NSE rates.nseTxnBps else rates.bseTxnBps)`. NSE≈2.97 bps, BSE=3 bps.
4. SEBI charges: `Paisa((tradeValue.value * rates.sebiChargePerCrorePaisa.value) / 10_000_000_000L)`. Rate: ₹10/crore = 1,000 paisa/crore.
5. Stamp duty (BUY only, capped ₹1,500 = 150,000 paisa): `val uncapped = tradeValue.applyBasisPoints(rates.stampDutyBuyBps); if BUY minOf(uncapped, Paisa(150_000L)) else Paisa.ZERO`. Rate: 1.5 bps.
6. GST: `(exchangeTxn + sebiCharges).applyBasisPoints(rates.gstBps)`. Brokerage = ₹0 for Zerodha delivery; GST base excludes brokerage.
7. Return `ChargeBreakdown(stt, exchangeTxn, sebiCharges, stampDuty, gst)`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Smoke: ₹1,00,000 NSE BUY delivery → STT≈₹100, NSE txn≈₹2.97, SEBI≈₹0.10, Stamp≈₹15, GST≈₹0.55. Full fixture suite in T-024.

**Acceptance Criteria**

- Pure function, no I/O or state.
- Zero `Double`/`Float` in any arithmetic path.
- Stamp duty cap of ₹1,500 applied correctly.
- Stamp duty = `Paisa.ZERO` for SELL orders.
- GST excludes brokerage (Zerodha delivery = ₹0 brokerage).

**Rollback Strategy:** Pure function; revert file.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Opus
- Recommended Mode: Thinking
- Reason: Financial arithmetic correctness is irreversible. Stamp duty cap, SEBI per-crore formula, and GST base composition must be reasoned precisely. Errors propagate into every P&L calculation.

**Context Strategy**

- Start new chat? Yes (new phase: domain engine)
- Required files: `Paisa.kt`, `ChargeRateSnapshot.kt`, `ChargeBreakdown.kt`, `OrderType.kt`, `Exchange.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §6 (Charge Calculation)
- Documents NOT required: All UI, CI, backup, database DDL documents

---

### T-024 — Charge Calculation Engine: Fixture Tests

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Write a fixture-driven test suite for `ChargeCalculator` covering 15+ real equity delivery scenarios. Each fixture has manually verified expected charge values. Covers NSE/BSE, BUY/SELL, small/large trade values, and stamp duty cap boundary.

**Scope Boundaries**

- Files affected: `ChargeCalculatorTest.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

1. Required fixture scenarios:
   - NSE BUY ₹10,000: verify each component individually.
   - NSE SELL ₹10,000: verify stamp duty = ₹0.
   - BSE BUY ₹10,000: verify BSE txn rate vs NSE.
   - NSE BUY ₹1 crore: verify stamp duty capped at exactly ₹1,500.
   - NSE BUY ₹50 (very small): verify SEBI rounds to ₹0 paisa.
   - NSE BUY ₹99,99,900 (just at stamp cap): verify stamp = ₹1,500.
   - NSE BUY ₹99,98,900 (just below stamp cap): verify stamp < ₹1,500.
   - 8+ additional fixtures from real Zerodha contract notes.
2. Each fixture asserts `stt`, `exchangeTxn`, `sebiCharges`, `stampDuty`, `gst`, and `total()` as exact `Paisa` equality.

**Acceptance Criteria**

- All 15+ fixture tests pass with exact `Paisa` equality.
- Stamp duty cap boundary tests pass.
- NSE vs BSE differentiation tests pass.
- SELL stamp duty = `Paisa.ZERO` test passes.
- 100% branch coverage on `ChargeCalculator.calculate()`.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Fixture values computed from first principles; Sonnet (Thinking) validates arithmetic per scenario.

**Context Strategy**

- Start new chat? No
- Required files: `ChargeCalculator.kt`, `Paisa.kt`, `ChargeBreakdown.kt`, `ChargeRateSnapshot.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §6
- Documents NOT required: All UI, CI, backup documents

---

### T-025 — FIFO Lot Matching Algorithm

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Implement `FifoLotMatcher` — a pure function taking an ordered list of buy lots and a sell quantity; returns matched lots with cost basis, remaining lots, and over-sell detection. Foundational algorithm for both `HoldingsComputationEngine` and `PnlCalculator`.

**Scope Boundaries**

- Files affected: `core-domain/src/main/kotlin/.../engine/FifoLotMatcher.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

1. Define `BuyLot(orderId, quantity, pricePerUnit: Paisa, totalValue: Paisa, tradeDate: LocalDate)`.
2. Define `FifoMatchResult(matchedLots: List<MatchedLot>, matchedCostBasis: Paisa, remainingLots: List<BuyLot>, overSellQuantity: Int)`. `MatchedLot(orderId, matchedQty, costBasisForMatchedQty: Paisa)`.
3. Implement `FifoLotMatcher.match(buyLots: List<BuyLot>, sellQuantity: Int): FifoMatchResult`:
   - Sort `buyLots` by `tradeDate ASC` (FIFO).
   - Iterate; consume from each lot until `sellQuantity` exhausted or lots depleted.
   - Partial lot: proportional cost basis = `(lot.totalValue * matchedQty) / lot.quantity` using `Paisa` integer arithmetic.
   - If `sellQuantity > totalBuyQty`: record `overSellQuantity`, remaining = empty.
4. Implement `computeHoldings(allBuyLots, allSellOrders)` convenience wrapper applying all sells sequentially.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Smoke: 2 lots × 10 shares, sell 15 → lot 1 fully consumed, lot 2: 5 consumed 5 remaining.
- Full edge case suite in T-026.

**Acceptance Criteria**

- Pure function, no mutable state.
- Partial lot cost basis split proportionally.
- `overSellQuantity > 0` when sells exceed available buys.
- Lots matched in strict chronological order.

**Rollback Strategy:** Revert file; no side effects.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Opus
- Recommended Mode: Thinking
- Reason: Proportional cost basis calculation for partial lot splits uses integer arithmetic with rounding decisions. Off-by-one errors in lot boundary conditions produce incorrect P&L for every partial sell trade.

**Context Strategy**

- Start new chat? No
- Required files: `Paisa.kt`, `Order.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §7 (FIFO lot matching pseudocode)
- Documents NOT required: All UI, CI, database documents

---

### T-026 — FIFO Lot Matcher: Edge Case Tests

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Write exhaustive tests for `FifoLotMatcher` covering all edge cases including partial sells, full exit, re-buy, and over-sell.

**Scope Boundaries**

- Files affected: `FifoLotMatcherTest.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

Test cases required:

1. Single lot, full sell: `matchedCostBasis = lot.totalValue`, remaining empty.
2. Single lot, partial sell: proportional cost basis, correct remaining quantity.
3. Two lots same price, sell spanning both: lot 1 fully consumed, lot 2 partially consumed.
4. Two lots different prices, sell spanning both: oldest lot price used first.
5. Three lots, sell all: remaining empty, `overSellQuantity = 0`.
6. Sell > total buy quantity: `overSellQuantity > 0`, remaining empty.
7. `sellQuantity = 0`: no matches, original lots unchanged.
8. Zero buy lots, any sell: `overSellQuantity = sellQuantity`.
9. Multiple rounds: sell → buy more → sell again → correct state.
10. Re-buy after full exit: sell all 10, buy 5 new, holdings = 5 at new price (no contamination from prior lots).

**Acceptance Criteria**

- All 10 edge case tests pass.
- 100% branch coverage on `FifoLotMatcher.match()`.
- Re-buy contamination test passes.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Edge case identification and expected value calculation.

**Context Strategy**

- Start new chat? No
- Required files: `FifoLotMatcher.kt`, `Paisa.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §7
- Documents NOT required: All others

---

### T-027 — Holdings Computation Engine

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Implement `HoldingsComputationEngine` — a pure function deriving current holdings state from a complete order history. Produces `ComputedHolding` per instrument with correct average buy price, invested amount, remaining quantity, and total buy charges.

**Scope Boundaries**

- Files affected: `HoldingsComputationEngine.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

1. Define `ComputedHolding(stockCode, quantity, avgBuyPrice: Paisa, investedAmount: Paisa, remainingLots: List<BuyLot>, totalBuyCharges: Paisa)`.
2. Implement `HoldingsComputationEngine.compute(orders: List<Order>, chargesByOrderId: Map<String, ChargeBreakdown>): List<ComputedHolding>`:
   - Group by `stockCode`.
   - For each: sort by `tradeDate ASC`, build `BuyLot` list from BUY orders, apply SELL orders via `FifoLotMatcher` sequentially.
   - `quantity` = sum of `remainingLots` quantities.
   - `investedAmount` = sum of `remainingLots` cost bases.
   - `avgBuyPrice` = `investedAmount / quantity` (0 if quantity = 0).
   - `totalBuyCharges` = sum of charges for BUY `orderId`s in `remainingLots`.
3. Include `quantity=0` holdings in output (UI filters them; engine does not).

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- 5+ multi-order sequences: 3 buys + 2 partial sells → verify remaining quantity and `avgBuyPrice`. Full exit → `quantity=0`. Re-buy after full exit → new `avgBuyPrice` from new lot only.

**Acceptance Criteria**

- Pure function.
- `avgBuyPrice` computed from remaining lots only.
- `quantity=0` holdings included in output.
- All unit tests pass.

**Rollback Strategy:** Revert file.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Opus
- Recommended Mode: Thinking
- Reason: Average buy price from remaining lots (not all historical lots) after partial sells is precision-critical. Composition with `FifoLotMatcher` must be verified under all lot-split scenarios.

**Context Strategy**

- Start new chat? No
- Required files: `FifoLotMatcher.kt`, `Order.kt`, `Paisa.kt`, `ChargeBreakdown.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §4.1 (avg price recalculation pseudocode)
- Documents NOT required: All others

---

### T-028 — P&L Calculation Engine

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Implement `PnlCalculator` — a pure function computing realized P&L for a date range and optional stock filter. Uses `FifoLotMatcher` for cost basis matching. Produces `PnlSummary`.

**Scope Boundaries**

- Files affected: `PnlCalculator.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

1. Implement `PnlCalculator.calculate(allOrders: List<Order>, chargesByOrderId: Map<String, ChargeBreakdown>, dateRange: ClosedRange<LocalDate>, stockCodeFilter: String? = null): PnlSummary`.
2. Filter by `stockCodeFilter` if provided.
3. Identify SELL orders within `dateRange` — these define realized P&L events.
4. For each SELL: use `FifoLotMatcher` on the **complete historical BUY lot list** (not date-filtered) to match cost basis.
5. `totalSellValue` = sum of sell order `totalValue` within `dateRange`.
6. `totalBuyCostOfSoldLots` = sum of `matchedCostBasis` from FIFO matching.
7. `totalCharges` = sum of `ChargeBreakdown.total()` for all orders within `dateRange`.
8. `realizedPnl = totalSellValue - totalBuyCostOfSoldLots - totalCharges`.
9. `chargeBreakdown` = aggregate sum of all component charges.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Smoke: 1 buy ₹10,000 + 1 sell ₹12,000 → P&L = ₹2,000 − charges ≈ ₹1,870. Full suite in T-029.

**Acceptance Criteria**

- Cost basis lookup uses full history, not date-filtered subset.
- No sells in range → `realizedPnl = Paisa.ZERO`.
- Loss scenario → negative `realizedPnl`.
- Pure function, no state or I/O.

**Rollback Strategy:** Revert file.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Opus
- Recommended Mode: Thinking
- Reason: The cost-basis lookback (buy orders outside the date range contributing to sells inside it) is a subtle correctness requirement that must be explicitly verified.

**Context Strategy**

- Start new chat? No
- Required files: `FifoLotMatcher.kt`, `ChargeCalculator.kt`, `Order.kt`, `PnlSummary.kt`, `ChargeBreakdown.kt`, `Paisa.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §8 (P&L formula and pseudocode)
- Documents NOT required: All others

---

### T-029 — P&L Calculation Engine: Fixture Tests

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Write 20+ fixture-driven tests for `PnlCalculator` with manually verified expected P&L values covering all order sequence edge cases.

**Scope Boundaries**

- Files affected: `PnlCalculatorTest.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

Required fixture scenarios:

1. Single BUY + full SELL in range: exact P&L.
2. BUY outside range + SELL inside range: cost basis from out-of-range buy.
3. Partial sell: only proportional cost basis deducted.
4. Multiple stocks in same range: correct cross-stock aggregation.
5. Full exit + re-buy + second sell: second sell uses re-buy cost only.
6. Only BUYs in range (no sells): `realizedPnl = Paisa.ZERO`.
7. Empty order list: `realizedPnl = Paisa.ZERO`.
8. Date boundary: sell on day 5, range is days 1–4 → excluded.
9. Stock code filter: two stocks, filter to one → only filtered stock's P&L.
10. Loss scenario: sell below buy price → negative `realizedPnl`.
11. 10+ additional real-trade fixtures from Zerodha contract notes.

**Acceptance Criteria**

- All 20+ fixture tests pass with exact `Paisa` equality.
- Loss scenario produces negative `realizedPnl`.
- Date range boundary tests pass (inclusive on both ends).
- 100% branch coverage on `PnlCalculator.calculate()`.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Fixture value computation and edge case identification.

**Context Strategy**

- Start new chat? No
- Required files: `PnlCalculator.kt`, `FifoLotMatcher.kt`, `ChargeCalculator.kt`, `Paisa.kt`, `PnlSummary.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §8
- Documents NOT required: All others

---

### T-030 — Target Price Calculator

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Implement `TargetPriceCalculator` — a pure function computing the GTT trigger price given a holding's cost basis, profit target, and charge rates. Accounts for sell-side charges so the net profit at the target price meets or exceeds the configured target.

**Scope Boundaries**

- Files affected: `TargetPriceCalculator.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

1. Implement `TargetPriceCalculator.compute(avgBuyPrice: Paisa, quantity: Int, profitTarget: ProfitTarget, investedAmount: Paisa, buyCharges: Paisa, chargeRates: ChargeRateSnapshot): Paisa`.
2. `targetNetProfit = profitTarget.computeTargetProfit(investedAmount)`.
3. Required gross sell value (closed-form approximation to avoid circular dependency): `requiredSellValue = (investedAmount + buyCharges + targetNetProfit) / (1 − sellChargeRate)` where `sellChargeRate` is the combined sell-side charge rate expressed as a fraction. Use `Paisa` integer arithmetic with rounding up.
4. `targetSellPricePerShare = requiredSellValue / quantity` (rounded up to nearest paisa).
5. Return `targetSellPricePerShare`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- 5% on ₹1,00,000 invested, 100 shares → target ≈ ₹1,052/share (accounting for buy + sell charges). Verify within ₹1.
- Zero-profit target: target price covers all charges exactly.
- Absolute profit target: fixed amount added correctly.

**Acceptance Criteria**

- Target sell price, when applied to a full sell, produces net profit ≥ configured target.
- Zero-profit target covers all charges.
- Returns `Paisa` — no `Double` arithmetic.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Sell-charge circular dependency requires the correct closed-form approximation formula.

**Context Strategy**

- Start new chat? No
- Required files: `ProfitTarget.kt`, `ChargeCalculator.kt`, `ChargeRateSnapshot.kt`, `Paisa.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §9
- Documents NOT required: All others

---

### T-031 — GTT Automation Engine

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Implement `GttAutomationEngine` — classifies each holding into a GTT action: `CreateGtt`, `UpdateGtt`, `NoAction`, `FlagManualOverride`, or `ArchiveGtt`. Produces a list of `GttAction` instructions; does not call any external API.

**Scope Boundaries**

- Files affected: `GttAutomationEngine.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

1. Define sealed class `GttAction`: `CreateGtt(stockCode, quantity, targetPrice)`, `UpdateGtt(gttId, newQuantity, newTargetPrice)`, `NoAction(stockCode)`, `FlagManualOverride(gttId, appTargetPrice, zerodhaActualPrice)`, `ArchiveGtt(gttId)`.
2. Implement `GttAutomationEngine.evaluate(holdings: List<ComputedHolding>, existingGtts: Map<String, GttRecord>, chargeRates: ChargeRateSnapshot, profitTargets: Map<String, ProfitTarget>): List<GttAction>`.
3. Per active holding (`quantity > 0`): compute `appTargetPrice` via `TargetPriceCalculator`. If no GTT: `CreateGtt`. If GTT exists and `isAppManaged=true` and price within 1 paisa tolerance: `NoAction`. If price differs: `UpdateGtt`. If `isAppManaged=false`: `FlagManualOverride`.
4. Per holding with `quantity=0` and active GTT: `ArchiveGtt`.
5. Return complete action list.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Full state transition tests in T-032.

**Acceptance Criteria**

- Pure function.
- `FlagManualOverride` emitted when `isAppManaged=false`.
- `NoAction` when price within 1 paisa.
- `ArchiveGtt` for `quantity=0` with active GTT.

**Rollback Strategy:** Revert file.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: GTT state transition and manual-override detection heuristic require careful reasoning against all lifecycle states.

**Context Strategy**

- Start new chat? No
- Required files: `TargetPriceCalculator.kt`, `GttRecord.kt`, `GttStatus.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §4.2 (GTT Lifecycle), §10
- Documents NOT required: All others

---

### T-032 — GTT Automation Engine: State Transition Tests

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Comprehensive tests for `GttAutomationEngine.evaluate()` covering all six action outcomes and all GTT lifecycle states.

**Scope Boundaries**

- Files affected: `GttAutomationEngineTest.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

Test cases:

1. `CreateGtt`: holding `quantity>0`, no existing GTT.
2. `NoAction`: existing app-managed GTT, price within 1 paisa tolerance.
3. `UpdateGtt`: existing app-managed GTT, price differs by >1 paisa.
4. `FlagManualOverride`: existing GTT with `isAppManaged=false`.
5. `ArchiveGtt`: holding `quantity=0` + active GTT.
6. No action: `quantity=0`, no GTT.
7. Idempotency: evaluate twice with same state → same actions.
8. Multiple stocks: mix of all action types in one call.
9. Tolerance boundary: differs by exactly 1 paisa → `NoAction`; 2 paisa → `UpdateGtt`.

**Acceptance Criteria**

- All 9 tests pass.
- 100% branch coverage on `GttAutomationEngine.evaluate()`.
- Idempotency test passes.
- Tolerance boundary tests pass.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Test case generation from a well-specified state machine.

**Context Strategy**

- Start new chat? No
- Required files: `GttAutomationEngine.kt`, `GttRecord.kt`, `GttStatus.kt`, `GttAction.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §4.2
- Documents NOT required: All others

---

### T-033 — SyncOrdersUseCase: Domain Logic

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Implement `SyncOrdersUseCase` — the sync flow orchestrator. All I/O via injected repository interfaces (mocked in tests). Implements: weekday guard, Mutex acquisition, order fetch, equity delivery filter, deduplication, holdings verification, charge calculation, atomic DB write, GTT evaluation, and result reporting.

**Scope Boundaries**

- Files affected: `SyncOrdersUseCase.kt`
- Modules affected: `:core-domain`
- Explicitly NOT touching: Repository implementations, WorkManager, Retrofit

**Implementation Steps**

1. Constructor-inject all repository interfaces.
2. Implement `suspend fun execute(): Result<SyncResult>` per `04_DOMAIN_ENGINE_DESIGN.md §5.1` pseudocode.
3. Weekday guard: return `SyncResult.Skipped` immediately — zero further operations.
4. Acquire `Mutex("order_sync")` — release in `finally` block unconditionally.
5. Log `SyncEvent(ORDER_SYNC, RUNNING)` via `SyncEventDao`.
6. Fetch remote orders; on failure: log FAILED event, release mutex, return `Failure(NetworkError)`.
7. Filter equity delivery; deduplicate against local store.
8. Fetch remote holdings; on failure: log FAILED, return `Failure(NetworkError)`.
9. Verify holdings; on mismatch: insert `PersistentAlert(HOLDINGS_MISMATCH)`, log FAILED, return `Failure(HoldingsMismatch)`.
10. Fetch charge rates; if null: log warning, proceed with zero charges marked as estimated.
11. Begin atomic Room transaction: insert orders, compute holdings via `HoldingsComputationEngine`, upsert holdings, insert transactions.
12. Evaluate GTT actions via `GttAutomationEngine`. Return `SyncResult.Success`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- MockK unit tests: happy path (3 new orders → `Success`), weekend guard (no repo calls made), network failure on order fetch (`Failure(NetworkError)`), holdings mismatch (`Failure(HoldingsMismatch)` + alert inserted), no new orders (`NoNewOrders`). Mutex release on failure verified (second `execute()` call not blocked after first failure).

**Acceptance Criteria**

- Mutex released in `finally` — always, including on failure.
- Holdings mismatch creates `PersistentAlert` via `alertRepo`.
- Weekday guard fires before any network call.
- All unit tests pass.

**Rollback Strategy:** Revert file; no database side effects.

**Estimated Complexity:** L

---

**LLM Execution Assignment**

- Recommended Model: Claude Opus
- Recommended Mode: Thinking
- Reason: Correct Mutex lifecycle, transactional atomicity semantics, and multi-step async error propagation in a use case with 10+ steps require precise reasoning.

**Context Strategy**

- Start new chat? No
- Required files: All domain engine files T-023–T-032, all repository interfaces from T-013
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §5.1 (pseudocode), §3.1 (Business Rule Catalog)
- Documents NOT required: UI, CI, backup documents

---

### T-034 — HoldingsVerificationUseCase and WeekdayGuard

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Implement `HoldingsVerifier` (stateless comparison engine), `HoldingsVerificationUseCase` (orchestrator), and `WeekdayGuard` (trading day check used by all scheduled use cases).

**Scope Boundaries**

- Files affected: `HoldingsVerifier.kt`, `HoldingsVerificationUseCase.kt`, `WeekdayGuard.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

1. `HoldingsVerifier.verify(remoteHoldings, localHoldings): HoldingsVerificationResult`. Sealed: `Verified`, `Mismatch(diffs: List<HoldingDiff>)`. `HoldingDiff` covers: `MISSING_LOCAL`, `MISSING_REMOTE`, `QUANTITY_MISMATCH(stockCode, remoteQty, localQty)`.
2. `HoldingsVerificationUseCase.execute(): Result<HoldingsVerificationResult>` — fetches remote + local holdings, delegates to `HoldingsVerifier`.
3. `WeekdayGuard.isTradingDay(date: LocalDate = LocalDate.now()): Boolean` — returns `false` for Saturday and Sunday. Public holidays not in scope for Phase 1.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- `HoldingsVerifier`: `Verified` (identical lists), `QUANTITY_MISMATCH`, `MISSING_LOCAL`, `MISSING_REMOTE`.
- `WeekdayGuard`: Mon–Fri → true; Sat → false; Sun → false.

**Acceptance Criteria**

- All `HoldingsVerifier` tests pass.
- `WeekdayGuard` correctly identifies weekends.
- `HoldingDiff` covers all three mismatch types.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Straightforward comparison and classification logic.

**Context Strategy**

- Start new chat? No
- Required files: `ComputedHolding` from `HoldingsComputationEngine.kt`, `AppError.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §5.2
- Documents NOT required: All others

---

### T-035 — Phase 2 Validation Milestone

**Phase:** 2 — Domain Engine
**Subsystem:** `:core-domain`

**Description:**
Execute all Phase 2 exit criteria. Verify complete domain engine passes all fixture tests, has 100% branch coverage on critical calculation paths, and has zero `Double`/`Float` in any monetary path.

**Implementation Steps**

1. `./gradlew :core-domain:test` — all 50+ tests pass.
2. Jacoco coverage report — ≥100% branch on `ChargeCalculator`, `FifoLotMatcher`, `PnlCalculator`, `GttAutomationEngine`.
3. Grep `:core-domain` for `Double`, `Float` monetary usage — zero results.
4. Grep `:core-domain` for `import android.` — zero results.
5. Manually verify 3 P&L fixture results against real Zerodha contract notes.
6. Verify `SyncOrdersUseCase` mutex-release-on-failure test passes.

**Acceptance Criteria**

- All Phase 2 Completion Criteria verified and checked off.
- Zero open blocking issues before Phase 3 begins.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (clean context before core feature flows)
- Required files: None
- Architecture docs to reference: None
- Documents NOT required: All

---

*— End of Phase 2 content (Tasks T-001 through T-035) —*

*Phase 3 content (Engineering Phases 3 and 4 detailed tasks) to follow upon confirmation.*

---

## Detailed Tasks — Phase 3: Core Feature Flows {#detailed-tasks-phase-3}

---

### T-036 — Kite Connect API DTOs and Retrofit Service Interface

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:core-network`

**Description:**
Define all Kite Connect API response DTOs as `@JsonClass`-annotated Moshi data classes, and implement the `KiteConnectApiService` Retrofit interface covering all required endpoints. No HTTP client wiring yet — interface and models only.

**Scope Boundaries**

- Files affected: `dto/OrderDto.kt`, `dto/HoldingDto.kt`, `dto/FundBalanceDto.kt`, `dto/GttDto.kt`, `dto/ChargeRateDto.kt`, `dto/AuthResponseDto.kt`, `KiteConnectApiService.kt`
- Modules affected: `:core-network`
- Explicitly NOT touching: OkHttp client, interceptors, repository implementations

**Implementation Steps**

1. Implement `OrderDto`: fields matching Kite Connect `/orders` response — `order_id`, `tradingsymbol`, `transaction_type` (BUY/SELL), `quantity`, `average_price`, `status`, `product` (CNC for delivery), `exchange`, `fill_timestamp`. All fields nullable where the API may omit them.
2. Implement `HoldingDto`: `tradingsymbol`, `quantity`, `average_price`, `t1_quantity`, `exchange`, `isin`.
3. Implement `FundBalanceDto`: `available.live_balance`, `available.opening_balance`, `net` — match the Kite Connect fund margin response structure.
4. Implement `GttDto`: `id`, `type`, `status`, `condition.tradingsymbol`, `condition.trigger_values[]`, `orders[].quantity`, `orders[].transaction_type`.
5. Implement `ChargeRateDto`: match the Kite charges API response structure (if available); include a fallback-ready design via nullable fields for free-tier incompatibility.
6. Implement `AuthResponseDto`: `access_token`, `public_token`, `user_id`, `user_name`.
7. Implement `KiteConnectApiService` Retrofit interface: `@GET("/orders")`, `@GET("/portfolio/holdings")`, `@GET("/user/margins")`, `@GET("/gtt")`, `@POST("/gtt")`, `@PUT("/gtt/{id}")`, `@DELETE("/gtt/{id}")`, `@POST("/session/token")`. All methods return `Response<KiteApiResponse<T>>` where `KiteApiResponse` is the standard Kite wrapper `{ status, data, message }`.
8. Implement `KiteApiResponse<T>` wrapper class.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit tests: Moshi deserialization tests — parse a hardcoded JSON fixture for each DTO; confirm all fields populated correctly. Test nullable field handling — missing `fill_timestamp` produces `null`, not a crash.

**Acceptance Criteria**

- All DTOs annotated `@JsonClass(generateAdapter = true)`.
- `KiteConnectApiService` compiles with all 8 endpoint methods.
- Moshi deserialization tests pass for all 6 DTO types.
- Nullable API fields do not crash deserialization.
- No Android imports in DTO classes (pure data classes).

**Rollback Strategy:** Delete DTO and service files; no schema or state impact.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: DTO generation from a known API spec; Moshi annotation pattern is boilerplate.

**Context Strategy**

- Start new chat? Yes (new phase: network and data layer)
- Required files: None (new files)
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §2.3 (`:core-network` package), `02_TECH_DECISIONS.md` §8 (Moshi)
- Documents NOT required: Domain engine, database, UI documents

---

### T-037 — OkHttp Client, Interceptors, and Retrofit Wiring

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:core-network`

**Description:**
Implement the OkHttp client with all interceptors (`KiteAuthInterceptor`, `TokenExpiredInterceptor`, `KiteConnectRateLimitInterceptor`), wire Retrofit with Moshi, and populate the `NetworkModule` Hilt binding. Implement `ApiResultAdapter` for `Result<T>` wrapping.

**Scope Boundaries**

- Files affected: `KiteConnectAuthInterceptor.kt`, `KiteConnectRateLimitInterceptor.kt`, `ApiResultAdapter.kt`, `NetworkModule.kt`, `util/NetworkMonitor.kt`
- Modules affected: `:core-network`
- Explicitly NOT touching: Repository implementations, OAuth flow

**Implementation Steps**

1. Implement `KiteConnectAuthInterceptor`: reads `access_token` from `EncryptedSharedPreferences` on every request; injects `Authorization: token {api_key}:{access_token}` header. If `access_token` is null, allows the request through (the `TokenExpiredInterceptor` handles the response).
2. Implement `TokenExpiredInterceptor`: intercepts HTTP 403 responses; emits a `SessionExpiredEvent` via a `SharedFlow` (provided via Hilt); does not retry the request. The consumer (auth ViewModel) observes this event and navigates to the re-auth screen.
3. Implement `KiteConnectRateLimitInterceptor`: enforces minimum 100ms delay between consecutive API calls to the same host using a `AtomicLong` timestamp tracker. No retry on 429 — surface as `AppError.NetworkError.RateLimited`.
4. Implement `ApiResultAdapter` as a `CallAdapter.Factory`: converts `Call<T>` to `Result<T>`, mapping HTTP errors and `IOException` to the appropriate `AppError` subtype.
5. Populate `NetworkModule.kt`:
   - `@Singleton @Provides OkHttpClient` with 30s connect/60s read timeouts, retry-on-connection-failure disabled, all three interceptors added.
   - `@Singleton @Provides Moshi` with `KotlinJsonAdapterFactory`.
   - `@Singleton @Provides KiteConnectApiService` via `Retrofit.Builder` with base URL from `BuildConfig`, Moshi converter, `ApiResultAdapter`.
6. Implement `NetworkMonitor` using `ConnectivityManager.NetworkCallback` to emit `Flow<Boolean>` of connectivity state.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit tests with `MockWebServer`:
  - Request has correct `Authorization` header after `access_token` written to mock `EncryptedSharedPreferences`.
  - HTTP 403 response emits `SessionExpiredEvent` (test via `SharedFlow` collector).
  - HTTP 200 with valid JSON → `Result.Success(dto)`.
  - HTTP 500 → `Result.Failure(AppError.NetworkError.ServerError)`.
  - Network timeout → `Result.Failure(AppError.NetworkError.Timeout)`.

**Acceptance Criteria**

- All `MockWebServer` tests pass.
- `Authorization` header format is `token {api_key}:{access_token}` exactly.
- HTTP 403 fires `SessionExpiredEvent` — does not throw an exception.
- Rate limit interceptor delays consecutive calls by ≥ 100ms.
- `retry-on-connection-failure` is disabled on OkHttp client.

**Rollback Strategy:** Revert `NetworkModule` to stub; remove interceptors.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: `CallAdapter.Factory` implementation and the `TokenExpiredInterceptor` `SharedFlow` pattern require careful reasoning around threading and lifecycle to avoid emission on the wrong dispatcher.

**Context Strategy**

- Start new chat? No
- Required files: `KiteConnectApiService.kt`, `ApiResultAdapter.kt` skeleton
- Architecture docs to reference: `07_SECURITY_MODEL.md` §3.2 (API auth header format), `01_SYSTEM_ARCHITECTURE.md` §4.2 (network failure recovery)
- Documents NOT required: Database, domain engine, UI documents

---

### T-038 — EncryptedSharedPreferences and Token Credential Store

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:infra-auth`

**Description:**
Implement `CredentialStore` — the single, security-hardened store for all secrets that must persist beyond the process lifecycle: `access_token`, `api_key`, `user_id`, Google OAuth tokens. Uses `EncryptedSharedPreferences` backed by Android Keystore. No plaintext storage of any credential.

**Scope Boundaries**

- Files affected: `infra-auth/src/main/kotlin/.../CredentialStore.kt`, `infra-auth/src/main/kotlin/.../MasterKeyProvider.kt`, `infra-auth/src/main/kotlin/.../di/AuthInfraModule.kt`
- Modules affected: `:infra-auth`
- Explicitly NOT touching: Biometric auth (T-043), SQLCipher key derivation (Phase 6)

**Implementation Steps**

1. Implement `MasterKeyProvider`: creates or retrieves an `AES256_GCM` `MasterKey` from Android Keystore using `MasterKey.Builder`. `MasterKey` is `UserPresenceRequired = false` (it would block background WorkManager operations). Store the `MasterKey` instance in memory only — never serialized.
2. Implement `CredentialStore` wrapping `EncryptedSharedPreferences.create(context, "kitewatch_secrets", masterKey, AES256_SIV, AES256_GCM)`:
   - `fun saveAccessToken(token: String)`
   - `fun getAccessToken(): String?`
   - `fun clearAccessToken()`
   - `fun saveApiKey(key: String)`
   - `fun getApiKey(): String?`
   - `fun saveUserId(id: String)`
   - `fun getUserId(): String?`
   - `fun saveGoogleOAuthToken(token: String)`
   - `fun getGoogleOAuthToken(): String?`
   - `fun clearAll()` — wipes all entries (used on account reset)
3. Implement `AuthInfraModule` Hilt binding: `@Singleton @Provides CredentialStore`.
4. Confirm `CredentialStore` is the **only** write path for any secret — no other class may call `SharedPreferences.edit()` on any preferences file containing sensitive data.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Instrumented tests (requires device — `EncryptedSharedPreferences` needs Keystore):
  - Save and retrieve `access_token` — correct value returned.
  - `clearAll()` — all keys return null after call.
  - Verify plaintext `SharedPreferences` files contain only encrypted ciphertext (no raw token strings visible in the raw file).

**Acceptance Criteria**

- `CredentialStore` uses `EncryptedSharedPreferences` exclusively.
- `clearAll()` removes all entries.
- No plaintext `SharedPreferences` file contains any secret field.
- `MasterKey` uses `AES256_GCM` scheme backed by Android Keystore.
- Instrumented tests pass on API 26+ device.

**Rollback Strategy:** Revert to stub `CredentialStore` returning hardcoded empty values for development only.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: `MasterKey` configuration and `EncryptedSharedPreferences` lifecycle must be correct. `UserPresenceRequired = false` decision has security implications that must be consciously documented.

**Context Strategy**

- Start new chat? No
- Required files: None (new files)
- Architecture docs to reference: `07_SECURITY_MODEL.md` §2.1 (EncryptedSharedPreferences section), §3 (Key Management)
- Documents NOT required: Database, domain engine, UI documents

---

### T-039 — Kite Connect OAuth Flow: Token Exchange and Session Management

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:feature-onboarding`, `:infra-auth`

**Description:**
Implement the full Zerodha OAuth authentication flow: open the Kite login URL in Chrome Custom Tabs, handle the `request_token` redirect via a `DeepLinkHandlerActivity`, exchange the token for an `access_token`, and persist it via `CredentialStore`. Implement session expiry detection and re-authentication navigation.

**Scope Boundaries**

- Files affected: `feature-onboarding/src/main/kotlin/.../KiteAuthLauncher.kt`, `feature-onboarding/src/main/kotlin/.../DeepLinkHandlerActivity.kt`, `core-domain/src/main/kotlin/.../usecase/auth/BindAccountUseCase.kt`, `feature-onboarding/src/main/AndroidManifest.xml`
- Modules affected: `:feature-onboarding`, `:core-domain`, `:infra-auth`
- Explicitly NOT touching: Biometric lock, onboarding UI screens (T-063)

**Implementation Steps**

1. Implement `DeepLinkHandlerActivity`: registered in `AndroidManifest.xml` with `intent-filter` for `android:scheme="kitewatch"` and `android:host="callback"`. On `onCreate`: extract `request_token` from `intent.data`, pass to `KiteAuthViewModel`, finish immediately.
2. Implement `KiteAuthLauncher`: wraps `CustomTabsIntent.Builder().build().launchUrl(context, kiteLoginUri)` where the login URI is `https://kite.zerodha.com/connect/login?api_key={api_key}&v=3`. The redirect URI registered with Zerodha must be `kitewatch://callback`.
3. Implement `BindAccountUseCase.execute(requestToken: String): Result<AccountBinding>`:
   - Calls `KiteConnectApiService.exchangeRequestToken(apiKey, requestToken, checksum)` — checksum is `SHA256("{api_key}{request_token}{api_secret}")`.
   - On success: saves `access_token` to `CredentialStore`, fetches user profile, inserts `AccountBinding` to `AccountBindingRepository`.
   - Returns `Result.Success(AccountBinding)`.
4. Implement `SessionManager`: observes `SessionExpiredEvent` from `TokenExpiredInterceptor`; clears `access_token` in `CredentialStore`; emits a `ReAuthRequired` app-level event consumed by `MainActivity` to navigate to the re-auth screen.
5. Add `api_secret` to `BuildConfig` fields (sourced from `secrets.properties`) — required for checksum computation. **Never log the API secret.**

**Data Impact**

- Schema changes: `account_binding` table written on first successful auth
- Migration required: No

**Test Plan**

- Unit tests: `BindAccountUseCase` with `MockWebServer` returning a valid `AuthResponseDto` → `AccountBinding` inserted in mock repository, `CredentialStore.saveAccessToken()` called. Invalid response → `Result.Failure(AuthError.TokenExchangeFailed)`.
- Manual: Full flow on a device with a real Zerodha account — Custom Tab opens, user logs in, app returns to foreground with active session.

**Acceptance Criteria**

- Custom Tab opens the correct Kite login URL with `api_key` query parameter.
- `DeepLinkHandlerActivity` extracts `request_token` and finishes itself.
- `access_token` is stored in `CredentialStore` — never in plaintext `SharedPreferences`.
- `api_secret` is never written to any log output (verify by grepping `Timber.` calls in `BindAccountUseCase`).
- Session expiry (HTTP 403) triggers `ReAuthRequired` event.

**Rollback Strategy:** Remove `DeepLinkHandlerActivity` intent filter; revert `SessionManager`. No data is corrupted.

**Estimated Complexity:** L

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: OAuth checksum computation correctness, `CustomTabsIntent` lifecycle edge cases, and the `SessionExpiredEvent` propagation path through the app require careful design.

**Context Strategy**

- Start new chat? No
- Required files: `CredentialStore.kt`, `KiteConnectApiService.kt`, `AccountBinding.kt`, `AppError.kt`
- Architecture docs to reference: `07_SECURITY_MODEL.md` §3.1 (OAuth flow), `06_AUTOMATION_AND_INTEGRATION.md` §5 (Kite Connect auth)
- Documents NOT required: UI screens, backup, database documents

---

### T-040 — Entity-to-Domain Mappers

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:core-data`

**Description:**
Implement all entity-to-domain and domain-to-entity mapper functions. These are pure extension functions that convert between Room entity types (`:core-database`) and domain model types (`:core-domain`). No business logic — structural mapping only.

**Scope Boundaries**

- Files affected: `OrderMapper.kt`, `HoldingMapper.kt`, `TransactionMapper.kt`, `GttRecordMapper.kt`, `ChargeRateMapper.kt`, `AlertMapper.kt`, `ApiDtoMapper.kt`
- Modules affected: `:core-data`
- Explicitly NOT touching: Repository implementations, DAO logic

**Implementation Steps**

1. `OrderMapper.kt`: `OrderEntity.toDomain(): Order`, `Order.toEntity(): OrderEntity`. Handle `trade_date` String ↔ `LocalDate`, `*_paisa` Long ↔ `Paisa`, enum String ↔ domain enum.
2. `HoldingMapper.kt`: `HoldingEntity.toDomain(): Holding`, `Holding.toEntity(): HoldingEntity`. Handle `profit_target_type` + `profit_target_value` columns → `ProfitTarget` sealed type (and reverse).
3. `TransactionMapper.kt`: `TransactionEntity.toDomain(): Transaction`. No `toEntity()` needed — transactions are built from business logic, not round-tripped.
4. `GttRecordMapper.kt`: `GttRecordEntity.toDomain(): GttRecord`, `GttRecord.toEntity(): GttRecordEntity`. Handle `GttStatus` enum String mapping.
5. `ChargeRateMapper.kt`: `List<ChargeRateEntity>.toDomain(): ChargeRateSnapshot`. Reads the latest rate set and assembles a `ChargeRateSnapshot`. `ChargeRateSnapshot.toEntities(): List<ChargeRateEntity>`.
6. `AlertMapper.kt`: `PersistentAlertEntity.toDomain(): PersistentAlert`. `payload` TEXT column is a JSON string — parse to `PersistentAlert.payload: Map<String, String>` via Moshi.
7. `ApiDtoMapper.kt`: `OrderDto.toDomain(): Order?` (nullable — returns null if `product != "CNC"` or `status != "COMPLETE"`), `HoldingDto.toDomain(): RemoteHolding`, `FundBalanceDto.toDomain(): FundBalance`, `GttDto.toDomain(): GttRecord`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit tests: round-trip test for each bidirectional mapper — create domain model, convert to entity, convert back, assert equality. `ProfitTarget.Percentage(500)` → entity columns `profit_target_type="PERCENTAGE"`, `profit_target_value=500` → back to `ProfitTarget.Percentage(500)`. `ApiDtoMapper.OrderDto.toDomain()` returns `null` for non-CNC products.

**Acceptance Criteria**

- All bidirectional mappers pass round-trip tests.
- `OrderDto.toDomain()` returns `null` for non-delivery (`product != "CNC"`) orders.
- `ProfitTarget` sealed type is correctly round-tripped via two columns.
- No business logic in mappers — structural transformation only.

**Rollback Strategy:** Revert mapper files; no database or domain impact.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Structural mapping generation from known source and target types; high volume, low complexity.

**Context Strategy**

- Start new chat? No
- Required files: All domain model files from T-012, all entity files from T-014–T-016, `KiteConnectApiService.kt` DTOs from T-036
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §2.2 (`:core-data` mapper package)
- Documents NOT required: All others

---

### T-041 — Repository Implementations: Local Persistence (Orders, Holdings, Transactions, GTT)

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:core-data`

**Description:**
Implement the four core repository implementations that wrap Room DAOs: `OrderRepositoryImpl`, `HoldingRepositoryImpl`, `TransactionRepositoryImpl`, and `GttRepositoryImpl`. Each delegates to its DAO and uses the mapper layer to convert between entity and domain types.

**Scope Boundaries**

- Files affected: `OrderRepositoryImpl.kt`, `HoldingRepositoryImpl.kt`, `TransactionRepositoryImpl.kt`, `GttRepositoryImpl.kt`
- Modules affected: `:core-data`
- Explicitly NOT touching: Network repositories, fund repository, `RepositoryModule`

**Implementation Steps**

1. `OrderRepositoryImpl` implements `OrderRepository`: all methods delegate to `OrderDao` with mapper transformations. `observeAll()` uses `.map { entities -> entities.map { it.toDomain() } }` on the `Flow`. `insertAll()` wraps `orderDao.insertAll(orders.map { it.toEntity() })`.
2. `HoldingRepositoryImpl` implements `HoldingRepository`: `upsert()` calls `holdingDao.upsert(holding.toEntity())`. `observeAll()` maps entity Flow to domain Flow.
3. `TransactionRepositoryImpl` implements `TransactionRepository`: `insert()` and `insertAll()` map to entity and call DAO. No update or delete methods — confirmed by interface contract.
4. `GttRepositoryImpl` implements `GttRepository`: `upsert()` maps to entity. `updateStatus()` delegates to DAO's raw status update. `archive()` delegates to DAO. `observeActive()` maps filtered Flow.
5. All four implementations are annotated `@Singleton` and injected via constructor.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Integration tests with real Room in-memory database (not mocks): `OrderRepositoryImpl.insertAll()` inserts three orders; `observeAll()` emits all three in correct order. `HoldingRepositoryImpl.upsert()` inserts then updates the same stock code — second upsert replaces first. `GttRepositoryImpl.archive()` sets `is_archived=1` and `observeActive()` stops emitting the record.

**Acceptance Criteria**

- All four implementations compile against their respective interfaces.
- `Flow`-emitting methods emit updated values when the underlying DAO data changes.
- `TransactionRepositoryImpl` has no update or delete methods — confirmed by interface.
- In-memory Room integration tests pass.

**Rollback Strategy:** Revert implementations; domain layer uses interface mocks.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Repository implementations are structured DAO delegation with mapper calls; well-defined pattern.

**Context Strategy**

- Start new chat? No
- Required files: All mapper files from T-040, all DAO files from T-018–T-020, all repository interfaces from T-013
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §2.2 (`:core-data` repository package)
- Documents NOT required: All network, UI, CI documents

---

### T-042 — Repository Implementations: Remote and Supporting (KiteConnect, Fund, ChargeRate, Alert)

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:core-data`

**Description:**
Implement the remaining repository implementations: `KiteConnectRepositoryImpl` (wraps the Retrofit service), `FundRepositoryImpl`, `ChargeRateRepositoryImpl`, `AlertRepositoryImpl`, and `SyncEventRepositoryImpl`. Wire all implementations into `RepositoryModule`.

**Scope Boundaries**

- Files affected: `KiteConnectRepositoryImpl.kt`, `FundRepositoryImpl.kt`, `ChargeRateRepositoryImpl.kt`, `AlertRepositoryImpl.kt`, `SyncEventRepositoryImpl.kt`, `AccountBindingRepositoryImpl.kt`, `di/RepositoryModule.kt`
- Modules affected: `:core-data`

**Implementation Steps**

1. `KiteConnectRepositoryImpl` implements `KiteConnectRepository` (add this interface to `:core-domain` if not yet present): `fetchTodaysOrders(): Result<List<Order>>` calls `apiService.getOrders()`, maps `List<OrderDto>` via `ApiDtoMapper`, filters to delivery only. Similar wrappers for holdings, fund margins, GTT list, create GTT, update GTT, delete GTT.
2. `FundRepositoryImpl` implements `FundRepository`: `insertEntry()` wraps `FundEntryDao.insert()`. `getRunningBalance()` computes balance from `FundEntryDao.getTotalConfirmedFunds()`.
3. `ChargeRateRepositoryImpl` implements `ChargeRateRepository`: `saveRates()` maps `ChargeRateSnapshot` to `List<ChargeRateEntity>` and calls `ChargeRateDao.insertAll()`. `getCurrentRates()` calls `ChargeRateDao.getLatest()` and maps to `ChargeRateSnapshot`.
4. `AlertRepositoryImpl`, `SyncEventRepositoryImpl`, `AccountBindingRepositoryImpl`: straightforward DAO delegation with mapping.
5. Implement `RepositoryModule.kt` `@Module @InstallIn(SingletonComponent::class)`: bind all 9 repository interfaces to their implementations using `@Binds` (for constructor-injectable impls) or `@Provides`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test `KiteConnectRepositoryImpl` with `MockWebServer`: `fetchTodaysOrders()` returns only `product="CNC"` and `status="COMPLETE"` orders after filtering. Non-delivery orders are excluded.
- Unit test `ChargeRateRepositoryImpl`: save a `ChargeRateSnapshot`, retrieve it, confirm round-trip equality.

**Acceptance Criteria**

- `KiteConnectRepositoryImpl` filters non-delivery orders before returning them to the domain layer.
- `RepositoryModule` binds all 9 interfaces without error.
- `ChargeRateRepositoryImpl` round-trip test passes.

**Rollback Strategy:** Revert `RepositoryModule` to bind mock implementations.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Repository delegation pattern; straightforward wiring with established mapping layer.

**Context Strategy**

- Start new chat? No
- Required files: All mapper files, all DAO files, `KiteConnectApiService.kt`, all repository interfaces
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §2.2
- Documents NOT required: UI, CI, backup documents

---

### T-043 — Biometric App Lock: BiometricAuthManager and AppLockStateManager

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:infra-auth`

**Description:**
Implement `BiometricAuthManager` using the `BiometricPrompt` API, and `AppLockStateManager` that tracks foreground/background transitions and enforces the configurable lock timeout. Implement the `AppLockScreen` composable in `:feature-auth`.

**Scope Boundaries**

- Files affected: `infra-auth/src/main/kotlin/.../BiometricAuthManager.kt`, `infra-auth/src/main/kotlin/.../AppLockStateManager.kt`, `feature-auth/src/main/kotlin/.../AppLockScreen.kt`, `feature-auth/src/main/kotlin/.../AppLockViewModel.kt`
- Modules affected: `:infra-auth`, `:feature-auth`

**Implementation Steps**

1. Implement `BiometricAuthManager`: `fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailed: () -> Unit, onError: (Int, CharSequence) -> Unit)`. Builds `BiometricPrompt` with `Executor` on the main thread. `BiometricPrompt.PromptInfo` configured with: title "Unlock KiteWatch", subtitle "Confirm identity to continue", `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`. No `setNegativeButtonText` (device credential fallback is enabled instead).
2. Implement `AppLockStateManager` using `ProcessLifecycleOwner`: tracks last-backgrounded timestamp in memory. On foreground event: if `currentTime - lastBackgrounded > lockTimeoutMs` → emit `LockRequired`. Default timeout: 5 minutes (300,000 ms). Timeout value sourced from `PreferencesRepository`.
3. Implement `AppLockScreen` composable: on initial composition, immediately triggers `BiometricAuthManager.authenticate()`. Shows a locked padlock icon with the app name while prompt is visible. On auth success: calls `onAuthenticated()` callback. On permanent failure (too many attempts): shows "Use device PIN" fallback button.
4. Implement `AppLockViewModel` (`@HiltViewModel`): manages `LockState` (LOCKED, AUTHENTICATING, UNLOCKED). On `LockRequired` event from `AppLockStateManager`: transitions to LOCKED. On auth success: transitions to UNLOCKED.
5. Integrate `AppLockStateManager` into `KiteWatchApplication.onCreate()` via Hilt injection and `ProcessLifecycleOwner.get().lifecycle.addObserver(appLockStateManager)`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test `AppLockStateManager`: simulate foreground after 6-minute background → `LockRequired` emitted. Simulate foreground after 2-minute background → no event.
- Manual on device: background app for > 5 minutes, return to foreground — biometric prompt appears. Background for < 5 minutes — no prompt.

**Acceptance Criteria**

- Biometric prompt appears on app launch (first time, or after lock timeout).
- 5-minute background threshold triggers re-auth.
- Device PIN fallback works when biometric fails.
- `AppLockStateManager` emits `LockRequired` only after threshold exceeded.
- All screens are visually blocked while `LockState == LOCKED`.

**Rollback Strategy:** Disable `AppLockStateManager` observer; remove `AppLockScreen` from NavHost gating.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: `BiometricPrompt` OEM behaviour variations and `ProcessLifecycleOwner` edge cases (process kill vs background) require careful reasoning.

**Context Strategy**

- Start new chat? No
- Required files: `CredentialStore.kt`, `AppError.kt`
- Architecture docs to reference: `07_SECURITY_MODEL.md` §4 (Biometric Auth), `05_APPLICATION_STRUCTURE.md` §4.4 (Navigation Flow)
- Documents NOT required: Domain engine, database, backup documents

---

### T-044 — PlaceGttUseCase: GTT API Orchestration

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:core-domain`

**Description:**
Implement `PlaceGttUseCase` — the orchestrator that takes a list of `GttAction` items from `GttAutomationEngine` and executes them against the Kite Connect GTT API. Handles per-action retry, rate limiting, partial failure, and local state updates.

**Scope Boundaries**

- Files affected: `core-domain/src/main/kotlin/.../usecase/gtt/PlaceGttUseCase.kt`
- Modules affected: `:core-domain`
- Explicitly NOT touching: `GttAutomationEngine` (already implemented), UI

**Implementation Steps**

1. Implement `PlaceGttUseCase.execute(actions: List<GttAction>): GttPlacementResult`.
2. For each `GttAction`:
   - `CreateGtt`: call `kiteConnectRepo.createGtt(stockCode, quantity, triggerPrice)`. On success: insert `GttRecord(status=ACTIVE, zerodhaGttId=returned_id, isAppManaged=true)` into `GttRepository`. On failure: insert `GttRecord(status=PENDING_CREATION)` for retry.
   - `UpdateGtt`: call `kiteConnectRepo.updateGtt(zerodhaGttId, quantity, triggerPrice)`. On success: update local record. On API 404 (GTT no longer exists in Zerodha): delete local record, insert `PersistentAlert(GTT_NOT_FOUND)`.
   - `ArchiveGtt`: call `kiteConnectRepo.deleteGtt(zerodhaGttId)`. On success: call `gttRepo.archive(gttId)`.
   - `FlagManualOverride`: no API call — update local `GttRecord.status = MANUAL_OVERRIDE_DETECTED`, insert `PersistentAlert(GTT_MANUAL_OVERRIDE, stockCode)`.
3. Between consecutive Kite Connect API calls: enforce 100ms minimum delay (delegated to `KiteConnectRateLimitInterceptor` — no additional delay needed at this layer).
4. Implement retry for `CreateGtt` and `UpdateGtt`: max 3 attempts with `delay(2000L * attempt)` exponential backoff. After 3 failures: record `status=PENDING_CREATION` / `PENDING_UPDATE` for the next sync cycle.
5. Return `GttPlacementResult(succeeded, failed, flagged)` with per-action outcome.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit tests with mocked `KiteConnectRepository` and `GttRepository`: `CreateGtt` success path — GTT record inserted with `ACTIVE` status and `zerodhaGttId`. `UpdateGtt` 404 — local record deleted, alert inserted. `FlagManualOverride` — no API call made, alert inserted. Retry exhaustion — record set to `PENDING_CREATION` after 3 failures.

**Acceptance Criteria**

- `FlagManualOverride` makes zero API calls.
- `UpdateGtt` with API 404 deletes local record and creates alert (does not crash).
- Retry logic gives up after 3 attempts; local record status reflects pending state.
- All unit tests pass.

**Rollback Strategy:** Revert file; no schema impact.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: API 404 handling during `UpdateGtt`, partial failure semantics, and retry backoff require careful state management.

**Context Strategy**

- Start new chat? No
- Required files: `GttAutomationEngine.kt`, `GttAction.kt`, `GttRecord.kt`, `GttRepository.kt`, `KiteConnectRepository.kt`
- Architecture docs to reference: `06_AUTOMATION_AND_INTEGRATION.md` §2.2 (GTT Automation Pipeline)
- Documents NOT required: UI, database DDL, backup documents

---

### T-045 — OrderSyncWorker: WorkManager Integration

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:infra-worker`

**Description:**
Implement `OrderSyncWorker` as a `CoroutineWorker`, schedule it as a daily periodic task at 16:30 IST Mon–Fri, implement retry policy and job audit logging, and wire it into WorkManager via `WorkSchedulerRepository`.

**Scope Boundaries**

- Files affected: `infra-worker/src/main/kotlin/.../OrderSyncWorker.kt`, `infra-worker/src/main/kotlin/.../WorkSchedulerRepository.kt`, `infra-worker/src/main/kotlin/.../di/WorkerModule.kt`
- Modules affected: `:infra-worker`
- Explicitly NOT touching: `SyncOrdersUseCase` (already implemented), GTT UI

**Implementation Steps**

1. Implement `OrderSyncWorker : CoroutineWorker(context, params)`:
   - In `doWork()`: call `syncOrdersUseCase.execute()`. On `Result.Success`: call `placeGttUseCase.execute(actions)`, update `SyncEventRepository`, post notification if enabled. Return `Result.success()`.
   - On `Result.Failure(NetworkError)`: return `Result.retry()` (triggers exponential backoff).
   - On `Result.Failure(HoldingsMismatch)` or other domain errors: return `Result.failure()` (no retry — requires user intervention).
   - Inject `SyncOrdersUseCase` and `PlaceGttUseCase` via Hilt `@AssistedInject`.
2. Configure `PeriodicWorkRequest`: period 24 hours, `flex` interval 30 minutes, initial delay calculated to align with 16:30 IST from current time, constraints: `NetworkType.CONNECTED`, `requiresBatteryNotLow = false`.
3. Implement `WorkSchedulerRepository`: `fun scheduleOrderSync(timeOfDay: LocalTime)`, `fun cancelOrderSync()`, `fun isOrderSyncScheduled(): Boolean`. Uses `WorkManager.enqueueUniquePeriodicWork` with `KEEP` policy on conflict.
4. Implement `ChargeRateSyncWorker`: weekly `PeriodicWorkRequest` (7-day period); calls `refreshChargeRatesUseCase.execute()`; returns `Result.success()` even on API failure (charge rates have a hardcoded fallback).
5. Implement `WorkerModule`: Hilt `@HiltWorker` factory bindings for both workers.
6. Schedule both workers from `KiteWatchApplication.onCreate()` only if onboarding is complete (check `AccountBindingRepository.isBound()`).

**Data Impact**

- Schema changes: None (`sync_event_log` table already exists)
- Migration required: No

**Test Plan**

- Unit tests: `OrderSyncWorker` — mock `SyncOrdersUseCase` returning `Failure(NetworkError)` → worker returns `Result.retry()`. Mock returning `Failure(HoldingsMismatch)` → worker returns `Result.failure()`.
- Integration test with `TestListenableWorkerBuilder` (WorkManager test API): confirm worker completes `Result.success()` with mocked use cases returning success.
- Manual: verify `background_job_audit` (or `sync_event_log`) records the attempt after a triggered background sync.

**Acceptance Criteria**

- `NetworkError` → `Result.retry()`.
- `HoldingsMismatch` → `Result.failure()` (no retry).
- `WorkManager.enqueueUniquePeriodicWork` uses `KEEP` conflict policy.
- Workers are not scheduled before onboarding is complete.
- `TestListenableWorkerBuilder` integration test passes.

**Rollback Strategy:** Cancel all WorkManager tasks via `WorkManager.cancelAllWork()` in tests; revert worker implementations.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: `PeriodicWorkRequest` initial delay calculation for a specific time-of-day requires correct IST offset handling. `@AssistedInject` with Hilt `@HiltWorker` pattern requires careful setup.

**Context Strategy**

- Start new chat? No
- Required files: `SyncOrdersUseCase.kt`, `PlaceGttUseCase.kt`, `SyncEventRepositoryImpl.kt`
- Architecture docs to reference: `06_AUTOMATION_AND_INTEGRATION.md` §3 (WorkManager scheduling), `01_SYSTEM_ARCHITECTURE.md` §9 (Background Job Architecture)
- Documents NOT required: UI, database DDL, backup documents

---

### T-046 — AddFundEntryUseCase and GetFundBalanceUseCase

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:core-domain`

**Description:**
Implement `AddFundEntryUseCase` (manual fund balance recording) and `GetFundBalanceUseCase` (returns the computed running balance from all confirmed fund entries). These are the minimal fund tracking use cases required for Phase 1 MVP.

**Scope Boundaries**

- Files affected: `core-domain/src/main/kotlin/.../usecase/fund/AddFundEntryUseCase.kt`, `core-domain/src/main/kotlin/.../usecase/fund/GetFundBalanceUseCase.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

1. `AddFundEntryUseCase.execute(amount: Paisa, date: LocalDate, note: String?, entryType: FundEntryType): Result<FundEntry>`:
   - Validate: `amount.value > 0` (BR-05) — return `ValidationError` if not.
   - Create `FundEntry(entryId=UUID, amount, entryDate=date, entryType, note, isConfirmed=true)`.
   - Call `fundRepo.insertEntry(entry)`.
   - Return `Result.Success(entry)`.
2. `GetFundBalanceUseCase.execute(): Flow<Paisa>`:
   - Observe `fundRepo.observeEntries()`.
   - Map to running sum of all confirmed entries.
   - Return `Flow<Paisa>`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- `AddFundEntryUseCase`: zero amount → `ValidationError`. Valid amount → `FundEntry` inserted in mock repository.
- `GetFundBalanceUseCase`: emit three fund entries; confirm `Flow` emits the correct running sum.

**Acceptance Criteria**

- Zero-amount entry returns `ValidationError`.
- `GetFundBalanceUseCase` returns the correct sum of all confirmed entries.
- Unconfirmed (`is_confirmed=0`) entries are excluded from the balance.

**Rollback Strategy:** Revert files; no schema impact.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Simple use case delegation with straightforward validation.

**Context Strategy**

- Start new chat? No
- Required files: `FundEntry.kt`, `FundRepository.kt`, `Paisa.kt`, `AppError.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §3.1 (BR-05)
- Documents NOT required: All others

---

### T-047 — CalculatePnlUseCase and GetHoldingsUseCase

**Phase:** 3 — Core Feature Flows
**Subsystem:** `:core-domain`

**Description:**
Implement `CalculatePnlUseCase` (wires the `PnlCalculator` engine with live repository data) and `GetHoldingsUseCase` (wires `HoldingsComputationEngine` with live repository data). These are the primary data-producing use cases for the Portfolio and Holdings screens.

**Scope Boundaries**

- Files affected: `core-domain/src/main/kotlin/.../usecase/portfolio/CalculatePnlUseCase.kt`, `core-domain/src/main/kotlin/.../usecase/holdings/GetHoldingsUseCase.kt`
- Modules affected: `:core-domain`

**Implementation Steps**

1. `CalculatePnlUseCase.execute(dateRange: ClosedRange<LocalDate>, stockCodeFilter: String? = null): Flow<PnlSummary>`:
   - Observe `orderRepo.observeAll()`.
   - On each emission: fetch `chargesByOrderId` from `chargeRateRepo.getCurrentRates()`.
   - Call `PnlCalculator.calculate(allOrders, chargesByOrderId, dateRange, stockCodeFilter)`.
   - Return `Flow<PnlSummary>` that re-emits whenever the order list changes.
2. `GetHoldingsUseCase.execute(): Flow<List<Holding>>`:
   - Observe `orderRepo.observeAll()`.
   - On each emission: call `HoldingsComputationEngine.compute(allOrders, chargesByOrderId)`.
   - Map `List<ComputedHolding>` to `List<Holding>` domain models.
   - Filter to `quantity > 0` holdings for the active display list.
   - Return `Flow<List<Holding>>`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit tests: `CalculatePnlUseCase` — mock repository emitting 5 orders → `Flow` emits a `PnlSummary`. A second emission (additional order inserted) triggers a new `PnlSummary` emission. `GetHoldingsUseCase` — mock repo emitting orders including a fully-exited stock → that stock is excluded from the `Flow<List<Holding>>`.

**Acceptance Criteria**

- Both use cases return `Flow` — reactive, not one-shot.
- `GetHoldingsUseCase` excludes `quantity=0` holdings.
- `CalculatePnlUseCase` correctly passes the full order history to `PnlCalculator` regardless of date filter.

**Rollback Strategy:** Revert files; no schema impact.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Use case wiring is straightforward composition of existing engines and repositories.

**Context Strategy**

- Start new chat? No
- Required files: `PnlCalculator.kt`, `HoldingsComputationEngine.kt`, `OrderRepository.kt`, `ChargeRateRepository.kt`, `PnlSummary.kt`, `Holding.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §8, §4.1
- Documents NOT required: All others

---

### T-048 — Phase 3 Validation Milestone

**Phase:** 3 — Core Feature Flows
**Subsystem:** All

**Description:**
Execute all Phase 3 exit criteria. Verify the full OAuth flow, WorkManager scheduling, all repository implementations, and the complete domain use case chain from API call to domain model.

**Implementation Steps**

1. Complete OAuth flow on a physical device with a real Zerodha account — `access_token` in `CredentialStore`, `AccountBinding` in Room.
2. `./gradlew :core-data:testDebugUnitTest` — all repository implementation tests pass.
3. `./gradlew :core-network:testDebugUnitTest` — all `MockWebServer` tests pass.
4. `./gradlew :infra-worker:testDebugUnitTest` — all worker unit tests pass.
5. Manual: trigger a manual order sync — orders appear in Room, GTT created in Zerodha, holdings updated.
6. Verify `sync_event_log` records the sync attempt with correct status.
7. Verify biometric lock gates all screens after 5-minute background.
8. Verify `EncryptedSharedPreferences` contains no plaintext `access_token` (inspect `secrets.xml` via `adb shell` on a debug build — confirm ciphertext only).

**Acceptance Criteria**

- All Phase 3 Completion Criteria verified.
- Zero open blocking issues before Phase 4 begins.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (clean context before UI work)
- Required files: None
- Architecture docs to reference: None
- Documents NOT required: All

---

## Detailed Tasks — Phase 4: UI Screens {#detailed-tasks-phase-4}

---

### T-049 — Core UI Formatters and Chart Infrastructure

**Phase:** 4 — UI Screens
**Subsystem:** `:core-ui`

**Description:**
Implement the three display formatters (`CurrencyFormatter`, `DateFormatter`, `PercentageFormatter`) and all four chart composables (`PnlLineChart`, `MonthlyBarChart`, `ChargesBreakdownChart`, `PnlPieChart`). These are consumed by every financial screen and must be production-quality before screen work begins.

**Scope Boundaries**

- Files affected: `formatter/CurrencyFormatter.kt`, `formatter/DateFormatter.kt`, `formatter/PercentageFormatter.kt`, `chart/PnlLineChart.kt`, `chart/MonthlyBarChart.kt`, `chart/ChargesBreakdownChart.kt`, `chart/PnlPieChart.kt`
- Modules affected: `:core-ui`
- Explicitly NOT touching: Feature screens, ViewModels

**Implementation Steps**

1. `CurrencyFormatter.format(paisa: Paisa): String`: converts to Indian numbering system with ₹ prefix — `₹1,00,000.00`. Handles negative values: `-₹1,000.00`. Handles zero: `₹0.00`. Uses `NumberFormat` with `Locale("en", "IN")` for the Indian grouping separator.
2. `DateFormatter.formatDisplay(date: LocalDate): String` → `"15 Mar 2024"`. `formatShort(date): String` → `"15/03/24"`. `formatMonthYear(date): String` → `"Mar 2024"`.
3. `PercentageFormatter.format(basisPoints: Int): String` → `"5.00%"`. `formatWithSign(basisPoints: Int): String` → `"+5.00%"` or `"-2.50%"`.
4. `PnlLineChart`: Compose Canvas-based cumulative P&L line chart. Accepts `List<Pair<LocalDate, Paisa>>` as data points. Draws: date axis (bottom), value axis (left), line path, positive area fill (green-tinted), negative area fill (red-tinted), a zero baseline. Minimum 30dp chart height; expands to fill available space.
5. `MonthlyBarChart`: Canvas-based vertical bar chart. Accepts `List<Pair<String, Paisa>>` (month label, P&L value). Positive bars green, negative bars red.
6. `ChargesBreakdownChart`: Canvas-based stacked horizontal bar or pie chart showing charge type proportions.
7. `PnlPieChart`: Canvas-based pie chart. Two segments: realized P&L and total charges. Click on segment highlights it. Shows centre label with total value.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- `CurrencyFormatterTest`: `Paisa(10_000_00L)` → `"₹1,00,000.00"`. Negative → `-₹500.00`. Zero → `₹0.00`.
- `PercentageFormatterTest`: `500` bps → `"5.00%"`. `formatWithSign(-250)` → `"-2.50%"`.
- Chart Compose UI tests: render each chart with sample data in a `ComposeTestRule`; confirm no crash and chart bounds are non-zero.

**Acceptance Criteria**

- `CurrencyFormatter` uses Indian number grouping (`1,00,000` not `100,000`).
- Negative `Paisa` values render with minus prefix.
- All four charts render without crash on sample data.
- `PnlLineChart` draws positive/negative area fills correctly (visual inspection in preview).

**Rollback Strategy:** Replace charts with placeholder `Box` composables; formatter tests are purely unit-level.

**Estimated Complexity:** L

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Formatter logic is deterministic with known expected outputs. Canvas chart implementation is code generation following established Android Canvas drawing patterns.

**Context Strategy**

- Start new chat? Yes (new phase: UI)
- Required files: `Paisa.kt`, `KiteWatchTheme.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §2.6 (`:core-ui` package), `00_PRODUCT_SPECIFICATION.md` §9 (chart specifications)
- Documents NOT required: Domain engine, database, CI, security documents

---

### T-050 — Additional Core UI Components

**Phase:** 4 — UI Screens
**Subsystem:** `:core-ui`

**Description:**
Implement the remaining shared UI components beyond the stubs from T-009: `CurrencyText`, `PercentageText`, `DateRangeSelector`, `PaginatedLazyColumn`, `StatusIndicator`, `ErrorStateWidget`, and `SetupChecklist`. These fill out the full design system component library used across all screens.

**Scope Boundaries**

- Files affected: 7 new component files in `core-ui/src/main/kotlin/.../component/`
- Modules affected: `:core-ui`

**Implementation Steps**

1. `CurrencyText(paisa: Paisa, textStyle: TextStyle, modifier: Modifier)`: renders `CurrencyFormatter.format(paisa)`. Positive values in default text color; negative in `MaterialTheme.colorScheme.error`.
2. `PercentageText(basisPoints: Int, showSign: Boolean = false, modifier: Modifier)`: renders via `PercentageFormatter`. Colors as `CurrencyText`.
3. `DateRangeSelector(selectedRange: DateRangePreset, onRangeSelected: (DateRangePreset) -> Unit)`: `DateRangePreset` enum: TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, ALL_TIME, CUSTOM. Renders as a horizontal `FilterChipGroup`. CUSTOM opens a date picker dialog (Material3 `DateRangePicker`).
4. `PaginatedLazyColumn<T>(pagingItems: LazyPagingItems<T>, itemContent: @Composable (T) -> Unit, emptyState: @Composable () -> Unit, errorState: @Composable (Throwable) -> Unit)`: wraps Paging 3 `LazyPagingItems` handling LOADING, ERROR, EMPTY states.
5. `StatusIndicator(status: SyncStatus, lastSyncTime: Instant?)`: small coloured dot + text. GREEN (synced within 24h), AMBER (> 24h since sync), RED (last sync failed). `SyncStatus` enum in `:core-ui`.
6. `ErrorStateWidget(message: String, onRetry: (() -> Unit)? = null)`: centred error icon, message text, optional retry button.
7. `SetupChecklist(items: List<ChecklistItem>)`: vertical list of `ChecklistItem(label, isComplete, actionLabel, onAction)`. Used on the Portfolio empty state.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Compose UI tests: `CurrencyText` with negative `Paisa` renders in error color. `DateRangeSelector` calls `onRangeSelected` with correct preset when chip tapped. `PaginatedLazyColumn` renders `emptyState` when `pagingItems` is empty.

**Acceptance Criteria**

- All 7 components compile and render without error.
- `CurrencyText` negative value uses error color.
- `PaginatedLazyColumn` handles all three paging states.
- `DateRangeSelector` CUSTOM preset opens date picker.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Component implementation following specified API contracts.

**Context Strategy**

- Start new chat? No
- Required files: `CurrencyFormatter.kt`, `PercentageFormatter.kt`, `FilterChipGroup.kt`, `KiteWatchTheme.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §2.6
- Documents NOT required: All domain, CI, security, backup documents

---

### T-051 — Portfolio Screen: ViewModel and State

**Phase:** 4 — UI Screens
**Subsystem:** `:feature-portfolio`

**Description:**
Implement `PortfolioViewModel` with full MVI pattern for the Portfolio/Home screen. The ViewModel orchestrates `CalculatePnlUseCase`, `GetFundBalanceUseCase`, and alert observation. Produces a single immutable `PortfolioState`.

**Scope Boundaries**

- Files affected: `PortfolioContract.kt`, `PortfolioViewModel.kt`, `model/PortfolioUiModel.kt`, `mapper/PortfolioUiMapper.kt`
- Modules affected: `:feature-portfolio`

**Implementation Steps**

1. Define `PortfolioContract.kt`:
   - `sealed interface PortfolioIntent`: `LoadData`, `SelectDateRange(range: DateRangePreset)`, `SelectCustomRange(from: LocalDate, to: LocalDate)`, `DismissAlert(alertId: String)`, `RefreshSync`.
   - `data class PortfolioState`: `isLoading: Boolean`, `pnlSummary: PnlUiModel?`, `fundBalance: String`, `selectedRange: DateRangePreset`, `chartData: List<ChartDataPoint>`, `unacknowledgedAlerts: List<AlertUiModel>`, `lastSyncStatus: SyncStatusUiModel`, `showSetupChecklist: Boolean`, `error: String?`.
   - `sealed interface PortfolioSideEffect`: `ShowSnackbar(message: String)`, `NavigateToSync`.
2. Define `PortfolioUiModel`: formatted strings for `realizedPnl`, `totalCharges`, `grossProfit`, `chargeBreakdownFormatted`. `isProfit: Boolean`.
3. Implement `PortfolioUiMapper`: `PnlSummary.toUiModel(): PortfolioUiModel` using `CurrencyFormatter`.
4. Implement `PortfolioViewModel` (`@HiltViewModel`): observes `CalculatePnlUseCase.execute(selectedRange)` and `GetFundBalanceUseCase.execute()` in `viewModelScope`. On `SelectDateRange` intent: cancel prior collection, relaunch with new range. On `RefreshSync` intent: emit `NavigateToSync` side effect (manual sync is triggered from the Orders screen or a dedicated sync button). On alert dismiss: call `alertRepo.acknowledge()`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit tests: Initial state has `isLoading=true`. After `CalculatePnlUseCase` emits: `isLoading=false`, `pnlSummary` populated. `SelectDateRange(THIS_MONTH)` intent updates `selectedRange` in state.

**Acceptance Criteria**

- `PortfolioState` is an immutable `data class` — no `var` fields.
- `SelectDateRange` intent cancels and re-launches the P&L Flow collection.
- `showSetupChecklist=true` when no orders exist in the repository.
- All ViewModel unit tests pass.

**Rollback Strategy:** Revert ViewModel to a stub returning a loading state.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: MVI ViewModel pattern with defined state shape; high-volume code generation.

**Context Strategy**

- Start new chat? No
- Required files: `CalculatePnlUseCase.kt`, `GetFundBalanceUseCase.kt`, `PnlSummary.kt`, `CurrencyFormatter.kt`, `AlertRepository.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §3 (Feature module layout), §5 (State management)
- Documents NOT required: All database, CI, security, backup documents

---

### T-052 — Portfolio Screen: Composable and Chart Wiring

**Phase:** 4 — UI Screens
**Subsystem:** `:feature-portfolio`

**Description:**
Implement the `PortfolioScreen` composable — the primary dashboard screen. Renders P&L summary cards, fund balance, the cumulative P&L line chart, date range selector, charges pie chart, alerts banner, and setup checklist empty state.

**Scope Boundaries**

- Files affected: `PortfolioScreen.kt`, `component/PnlSummaryCard.kt`, `component/FundBalanceCard.kt`, `component/PortfolioAlertRow.kt`, `navigation/PortfolioNavigation.kt`
- Modules affected: `:feature-portfolio`

**Implementation Steps**

1. `PortfolioScreen.kt` is a stateless composable receiving `state: PortfolioState` and `onIntent: (PortfolioIntent) -> Unit`. No business logic.
2. Layout: `Scaffold` with top bar showing "KiteWatch" title and sync status `StatusIndicator`. `LazyColumn` content:
   - `DateRangeSelector` chip row at top.
   - `AlertBanner` for each unacknowledged alert (dismissible).
   - `PnlSummaryCard`: realized P&L (large, colored), total charges, gross profit.
   - `PnlLineChart` with data from `state.chartData`.
   - `FundBalanceCard`: current fund balance, last reconciled timestamp.
   - `PnlPieChart` (charges breakdown) if data is available.
   - `SetupChecklist` when `state.showSetupChecklist = true`.
3. `SkeletonLoader` rendered while `state.isLoading = true`.
4. `PortfolioNavigation.kt`: `fun NavGraphBuilder.portfolioScreen()` extension contributing the Portfolio route.
5. Wire `PortfolioScreen` to `PortfolioViewModel` in the navigation host using `hiltViewModel()` and `collectAsStateWithLifecycle()`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Compose UI test with a fakeViewModel emitting a known `PortfolioState`: P&L value renders as `"₹5,000.00"`. `DateRangeSelector` chip press emits `SelectDateRange` intent. `SkeletonLoader` visible when `isLoading=true`.

**Acceptance Criteria**

- Screen renders correctly in Dark and Light themes (visual inspection).
- `SkeletonLoader` shown during loading; replaced by content on data arrival.
- `SetupChecklist` shown when `showSetupChecklist=true`.
- Date range chip press triggers correct intent.
- Compose UI tests pass.

**Rollback Strategy:** Replace with placeholder `Text("Portfolio")`.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Composable screen assembly using the defined state and pre-built components.

**Context Strategy**

- Start new chat? No
- Required files: `PortfolioViewModel.kt`, `PortfolioContract.kt`, `PnlLineChart.kt`, `PnlPieChart.kt`, `DateRangeSelector.kt`, `AlertBanner.kt`, `SetupChecklist.kt`, `CurrencyText.kt`
- Architecture docs to reference: `00_PRODUCT_SPECIFICATION.md` §6.1 (Portfolio screen specification)
- Documents NOT required: Domain engine, database, CI, security documents

---

### T-053 — Holdings Screen: ViewModel, State, and Detail Navigation

**Phase:** 4 — UI Screens
**Subsystem:** `:feature-holdings`

**Description:**
Implement `HoldingsViewModel` and `HoldingsScreen`. The screen displays the list of active holdings, each with expandable detail showing lot breakdown, cost basis, GTT status, and an editable profit target. Navigates to the GTT screen.

**Scope Boundaries**

- Files affected: `HoldingsContract.kt`, `HoldingsViewModel.kt`, `model/HoldingUiModel.kt`, `mapper/HoldingUiMapper.kt`, `HoldingsScreen.kt`, `component/HoldingCard.kt`, `component/ProfitTargetEditSheet.kt`, `navigation/HoldingsNavigation.kt`
- Modules affected: `:feature-holdings`

**Implementation Steps**

1. `HoldingUiModel`: `stockCode`, `stockName`, `quantity: String`, `avgBuyPrice: String`, `targetSellPrice: String`, `profitTargetDisplay: String`, `investedAmount: String`, `estimatedCurrentValue: String?` (null — no live price feed), `isExpanded: Boolean`, `linkedGttStatus: GttStatusUiModel?`.
2. `HoldingsContract`: intents — `ToggleExpand(stockCode)`, `EditProfitTarget(stockCode)`, `SaveProfitTarget(stockCode, newTarget: ProfitTarget)`, `NavigateToGtt`. State: `isLoading`, `holdings: List<HoldingUiModel>`, `editingStockCode: String?`, `error: String?`.
3. `HoldingsViewModel`: observes `GetHoldingsUseCase.execute()`. On `SaveProfitTarget`: calls `UpdateProfitTargetUseCase.execute(stockCode, newTarget)`. On `NavigateToGtt`: emits `SideEffect.NavigateToGtt`.
4. `HoldingCard` composable: collapsed shows symbol, qty, avg price, target price. Expanded adds: cost basis, buy charges, profit target with an "Edit" icon button. GTT status badge.
5. `ProfitTargetEditSheet` (Modal Bottom Sheet): text field for percentage value, saves on confirm. Validates input (must be ≥ 0).
6. `HoldingsNavigation.kt`: contributes `holdingsScreen` and `stockDetailScreen` routes to `NavGraphBuilder`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Compose UI test: render `HoldingsScreen` with a fake state containing 3 holdings. Tap a `HoldingCard` — `ToggleExpand` intent emitted. Tap "Edit" on expanded card — `EditProfitTarget` intent emitted, `ProfitTargetEditSheet` visible.

**Acceptance Criteria**

- `HoldingsViewModel` unit test: `GetHoldingsUseCase` emitting 3 holdings → `PortfolioState.holdings` has 3 items after mapping.
- `ProfitTargetEditSheet` validates negative values and shows an error.
- Compose UI tests pass.

**Rollback Strategy:** Replace with `Text("Holdings")`.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Composable screen and ViewModel following the defined MVI contract; established pattern from T-051–T-052.

**Context Strategy**

- Start new chat? No
- Required files: `GetHoldingsUseCase.kt`, `Holding.kt`, `CurrencyFormatter.kt`, `ConfirmationDialog.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §2.5 (Holdings feature structure), `00_PRODUCT_SPECIFICATION.md` §6.2 (Holdings screen spec)
- Documents NOT required: Domain engine, database, CI documents

---

### T-054 — Orders Screen: ViewModel, Paging 3, and Manual Sync Trigger

**Phase:** 4 — UI Screens
**Subsystem:** `:feature-orders`

**Description:**
Implement `OrdersViewModel` and `OrdersScreen` with Paging 3 for the orders list, a manual sync trigger button, and date range / stock code filtering. This screen is the primary entry point for triggering a manual sync.

**Scope Boundaries**

- Files affected: `OrdersContract.kt`, `OrdersViewModel.kt`, `model/OrderUiModel.kt`, `mapper/OrderUiMapper.kt`, `OrdersScreen.kt`, `component/OrderRow.kt`, `navigation/OrdersNavigation.kt`
- Modules affected: `:feature-orders`

**Implementation Steps**

1. `OrdersViewModel`: uses `GetOrdersPagedUseCase` (add to `:core-domain` if not yet present) returning `Flow<PagingData<Order>>` via Room Paging 3 DAO query. Collects paging data via `cachedIn(viewModelScope)`. On `SyncNow` intent: calls `syncOrdersUseCase.execute()` in a coroutine; emits `ShowSnackbar("Syncing…")`. On sync completion: emits `ShowSnackbar("Sync complete — {N} new orders")`.
2. `OrderUiModel`: `date: String`, `stockCode`, `type: String` (BUY/SELL), `quantity: String`, `price: String`, `totalValue: String`, `charges: String`.
3. `OrdersScreen`: `Scaffold` with top bar title "Orders" and a sync `IconButton` (refresh icon). `FilterChipGroup` for date range. `PaginatedLazyColumn<OrderUiModel>` bound to `pagingItems`.
4. `OrderRow` composable: single order list item with date, symbol, type badge (BUY=green, SELL=red), quantity, price, value.
5. Implement `GetOrdersPagedUseCase`: returns `Flow<PagingData<Order>>` using `Pager(PagingConfig(pageSize=50))` backed by a Room `PagingSource`.
6. `OrdersNavigation.kt`: contributes `ordersScreen` route.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test `OrdersViewModel`: `SyncNow` intent triggers `syncOrdersUseCase.execute()`. Snackbar side effect emitted after sync completion.
- Paging boundary test: verify last page loads without duplication or crash using `TestPager`.

**Acceptance Criteria**

- `PagingData` scrolls through > 200 orders without duplicate rows.
- Sync button triggers `SyncNow` intent and shows loading state.
- Date range filter re-queries the paged data source.
- BUY rows have green type badge; SELL rows have red type badge.

**Rollback Strategy:** Replace with static `LazyColumn` (no Paging 3) for initial integration if Paging issues arise.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Paging 3 with Room is a well-documented pattern; ViewModel and screen follow the established MVI template.

**Context Strategy**

- Start new chat? No
- Required files: `OrdersViewModel.kt` skeleton, `Order.kt`, `SyncOrdersUseCase.kt`, `CurrencyFormatter.kt`, `PaginatedLazyColumn.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §2.5 (Orders feature structure), `00_PRODUCT_SPECIFICATION.md` §6.3 (Orders screen spec)
- Documents NOT required: Domain engine, security, backup documents

---

### T-055 — Transactions Screen: ViewModel and Paging 3

**Phase:** 4 — UI Screens
**Subsystem:** `:feature-transactions`

**Description:**
Implement `TransactionsViewModel` and `TransactionsScreen` with Paging 3 and filter by transaction type. Structurally similar to the Orders screen; separate feature module.

**Scope Boundaries**

- Files affected: `TransactionsContract.kt`, `TransactionsViewModel.kt`, `model/TransactionUiModel.kt`, `mapper/TransactionUiMapper.kt`, `TransactionsScreen.kt`, `component/TransactionRow.kt`, `navigation/TransactionsNavigation.kt`
- Modules affected: `:feature-transactions`

**Implementation Steps**

1. `TransactionsViewModel`: `GetTransactionsPagedUseCase` provides `Flow<PagingData<Transaction>>`. Intents: `FilterByType(type: TransactionType?)`.
2. `TransactionUiModel`: `date: String`, `type: String`, `stockCode: String?`, `amount: String`, `description: String`. Amount in green for credits, red for debits.
3. `TransactionsScreen`: top bar "Transactions", `FilterChipGroup` for type (ALL, BUY, SELL, FUND_CREDIT, FUND_DEBIT), `PaginatedLazyColumn<TransactionUiModel>`.
4. `TransactionRow`: date, type icon, description, amount (colored).
5. `TransactionsNavigation.kt`: contributes `transactionsScreen` route.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Compose UI test: render with fake paging data; verify FUND_CREDIT amount renders in green, FUND_DEBIT in red. Type filter chip press emits `FilterByType` intent.

**Acceptance Criteria**

- Paging 3 integration test passes (no duplicate rows).
- Type filter reduces displayed items to the selected type.
- Credit/debit color coding correct.

**Rollback Strategy:** Replace with static `LazyColumn`.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Structurally identical to T-054; high reuse of established patterns.

**Context Strategy**

- Start new chat? No
- Required files: `Transaction.kt`, `CurrencyFormatter.kt`, `PaginatedLazyColumn.kt`, `FilterChipGroup.kt`
- Architecture docs to reference: `00_PRODUCT_SPECIFICATION.md` §6.4 (Transactions screen spec)
- Documents NOT required: All others

---

### T-056 — GTT Screen: ViewModel and Screen

**Phase:** 4 — UI Screens
**Subsystem:** `:feature-gtt`

**Description:**
Implement `GttViewModel` and `GttScreen` — a read-only list of active GTT records with visual distinction for `MANUAL_OVERRIDE_DETECTED` items. No create/edit actions (GTTs are automation-managed).

**Scope Boundaries**

- Files affected: `GttContract.kt`, `GttViewModel.kt`, `model/GttUiModel.kt`, `mapper/GttUiMapper.kt`, `GttScreen.kt`, `component/GttRecordRow.kt`, `navigation/GttNavigation.kt`
- Modules affected: `:feature-gtt`

**Implementation Steps**

1. `GttViewModel`: observes `gttRepo.observeActive()` (exposed via a use case wrapper). Emits `GttState(isLoading, records: List<GttUiModel>, unacknowledgedOverrides: Int)`.
2. `GttUiModel`: `stockCode`, `triggerPrice: String`, `quantity: String`, `status: GttStatusDisplay`, `isManualOverride: Boolean`, `lastSynced: String`.
3. `GttScreen`: top bar "GTT Orders" with override count badge. `LazyColumn` of `GttRecordRow`. Empty state if no active GTTs.
4. `GttRecordRow`: standard card; `MANUAL_OVERRIDE_DETECTED` rows rendered with an amber `StatusIndicator` and a warning note.
5. `GttNavigation.kt`: contributes `gttScreen` route; accessible from Holdings tab via the `NavigateToGtt` side effect.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Compose UI test: render with a fake state containing one ACTIVE and one MANUAL_OVERRIDE_DETECTED record. Confirm override row has amber indicator. Confirm non-override row does not.

**Acceptance Criteria**

- `MANUAL_OVERRIDE_DETECTED` rows visually distinct from ACTIVE rows.
- Empty state renders when no active GTTs.
- No create/edit controls visible on this screen.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Read-only display screen with a simple list; structurally simpler than Holdings or Orders.

**Context Strategy**

- Start new chat? No
- Required files: `GttRecord.kt`, `GttStatus.kt`, `GttRepository.kt`
- Architecture docs to reference: `00_PRODUCT_SPECIFICATION.md` §6.5 (GTT screen spec)
- Documents NOT required: All others

---

### T-057 — Settings Screen and Fund Balance Entry

**Phase:** 4 — UI Screens
**Subsystem:** `:feature-settings`

**Description:**
Implement the multi-page Settings screen hierarchy: the root settings list, fund balance entry section, theme toggle, sync schedule display, app version info, stub About/Guidebook/Privacy pages, and the account binding display.

**Scope Boundaries**

- Files affected: `SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsContract.kt`, `component/FundBalanceEntrySheet.kt`, `component/SettingsRow.kt`, `navigation/SettingsNavigation.kt`
- Modules affected: `:feature-settings`

**Implementation Steps**

1. `SettingsViewModel`: provides current `SettingsState` — theme preference, fund balance, last sync time, app version, account user ID. Handles intents: `ToggleTheme`, `AddFundEntry(amount, date, note)`, `NavigateTo(route)`.
2. `SettingsScreen`: grouped `LazyColumn` with sections: "Fund Balance" (current balance + add entry button), "Appearance" (Dark/Light toggle), "Sync" (last sync time, schedule display — read-only in Phase 1), "About" (version, Zerodha user ID masked, links to About/Guidebook/Privacy).
3. `FundBalanceEntrySheet` (Modal Bottom Sheet): currency amount input field, date picker (defaults to today), optional note field, Save button. Validates non-zero amount.
4. Stub `AboutScreen`, `GuidebookScreen`, `PrivacyScreen` pages as `Text("Coming soon")` — to be populated in Phase 3.
5. `SettingsNavigation.kt`: full sub-graph including routes for all settings sub-pages.

**Data Impact**

- Schema changes: None (`fund_entries` table already exists)
- Migration required: No

**Test Plan**

- Compose UI test: `ToggleTheme` intent changes `isDarkTheme` in `ThemePreferenceRepository`. `AddFundEntry` with zero amount shows validation error. Valid `AddFundEntry` calls `AddFundEntryUseCase`.

**Acceptance Criteria**

- Theme toggle persists across kill/relaunch (DataStore-backed).
- Fund balance entry validates non-zero amount.
- Fund entry appears in the running balance after save.
- Stub About/Guidebook/Privacy pages display without crash.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Settings screen is straightforward list composition; fund entry is a standard modal bottom sheet form.

**Context Strategy**

- Start new chat? No
- Required files: `AddFundEntryUseCase.kt`, `ThemePreferenceRepository.kt`, `CurrencyFormatter.kt`, `ConfirmationDialog.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §4 (Navigation), `00_PRODUCT_SPECIFICATION.md` §6.6 (Settings spec)
- Documents NOT required: Domain engine, database, security documents

---

### T-058 — Onboarding Flow: Full Screen Implementation

**Phase:** 4 — UI Screens
**Subsystem:** `:feature-onboarding`

**Description:**
Implement the complete onboarding screen sequence: T&C acceptance, biometric setup, Zerodha login initiation, and account binding confirmation. The onboarding flow is entered only once; subsequent launches gate at the biometric lock screen.

**Scope Boundaries**

- Files affected: `OnboardingScreen.kt`, `OnboardingViewModel.kt`, `OnboardingContract.kt`, `component/TermsAcceptanceStep.kt`, `component/BiometricSetupStep.kt`, `component/ZerodhaLoginStep.kt`, `component/AccountConfirmationStep.kt`, `navigation/OnboardingNavigation.kt`
- Modules affected: `:feature-onboarding`

**Implementation Steps**

1. `OnboardingViewModel`: tracks `OnboardingStep` (TERMS, BIOMETRIC_SETUP, ZERODHA_LOGIN, ACCOUNT_CONFIRMATION, COMPLETE). Intents: `AcceptTerms`, `SetupBiometric`, `LaunchKiteLogin`, `OnLoginSuccess(requestToken)`, `OnLoginFailure(error)`.
2. `TermsAcceptanceStep`: scrollable T&C text (from `strings.xml`), checkbox "I agree", Continue button (disabled until checkbox checked).
3. `BiometricSetupStep`: explains biometric requirement; "Set up now" button launches `BiometricManager.authenticate()` in enrollment context. On success: advance to next step.
4. `ZerodhaLoginStep`: displays API key field (pre-populated from `BuildConfig.KITE_API_KEY` or editable), "Connect to Zerodha" button launches `KiteAuthLauncher`. Shows loading indicator while awaiting `request_token` callback.
5. `AccountConfirmationStep`: displays bound account name and user ID. "Start KiteWatch" button navigates to main graph and schedules WorkManager tasks.
6. Persist onboarding completion state in `DataStore` (non-sensitive: not in `EncryptedSharedPreferences`).
7. `OnboardingNavigation.kt`: multi-step `AnimatedContent`-based onboarding graph; each step slides in from the right.

**Data Impact**

- Schema changes: `account_binding` table written on completion
- Migration required: No

**Test Plan**

- Unit test: skipping biometric (not supported on emulator) — `BiometricSetupStep` must show a graceful fallback (device PIN setup instead of biometric). `OnboardingViewModel` advances through all steps in order with correct mocked use case responses.

**Acceptance Criteria**

- T&C Continue button is disabled until checkbox checked.
- Biometric setup step handles `ERROR_HW_UNAVAILABLE` gracefully (shows PIN setup alternative).
- After onboarding completion, `AccountBindingRepository.isBound()` returns `true`.
- Subsequent app launches skip onboarding and go directly to the lock screen.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Multi-step wizard pattern with defined transitions; established MVI approach.

**Context Strategy**

- Start new chat? No
- Required files: `KiteAuthLauncher.kt`, `BindAccountUseCase.kt`, `BiometricAuthManager.kt`, `AccountBindingRepository.kt`
- Architecture docs to reference: `00_PRODUCT_SPECIFICATION.md` §8.1 (Onboarding flow), `07_SECURITY_MODEL.md` §4
- Documents NOT required: Domain engine, database DDL documents

---

### T-059 — Full Navigation Wiring and App Launch Logic

**Phase:** 4 — UI Screens
**Subsystem:** `:app`

**Description:**
Replace the placeholder navigation scaffold from T-008 with the full production `KiteWatchNavHost`. Wire all feature modules' `NavGraph` extensions into the host. Implement the startup routing logic: app lock → onboarding check → main graph.

**Scope Boundaries**

- Files affected: `app/src/main/kotlin/.../navigation/KiteWatchNavHost.kt`, `app/src/main/kotlin/.../MainActivity.kt`
- Modules affected: `:app`
- Explicitly NOT touching: Feature screen implementations (already complete)

**Implementation Steps**

1. Implement production `KiteWatchNavHost` per `05_APPLICATION_STRUCTURE.md §4.1`:
   - `startDestination` logic: if not authenticated → `AuthRoute`, if not onboarded → `OnboardingRoute`, else → `MainRoute`.
   - Contribute all feature navigation graphs via their `NavGraph` extension functions.
2. Implement `MainActivity`: observes `AppLockStateManager.isUnlocked` and `AccountBindingRepository.isBound()`. Wraps `KiteWatchNavHost` in `AppLockGate` composable that blocks navigation until unlocked.
3. `AppLockGate`: if `isUnlocked == false`, render `AppLockScreen` filling the full window. If `isUnlocked == true`, render `content`.
4. Implement `SessionExpiredEvent` observer in `MainActivity`: on receipt of `ReAuthRequired` event from `SessionManager`, navigate to `AuthRoute` (not `OnboardingRoute` — re-auth only, not full re-onboarding).
5. Verify deep link handling for `kitewatch://callback` routes to `DeepLinkHandlerActivity` and not the main navigation graph.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: fresh install → lands on onboarding. Complete onboarding → lands on Portfolio. Background for > 5 min, return → biometric screen. Complete auth → Portfolio. Simulate session expiry (HTTP 403 from Kite) → re-auth screen, not onboarding.

**Acceptance Criteria**

- Fresh install starts at onboarding.
- Already-onboarded app starts at biometric lock.
- Session expiry navigates to biometric re-auth, not full onboarding.
- All five bottom navigation tabs are accessible after authentication.
- `kitewatch://callback` deep link does not appear in the app's back stack after handling.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Navigation wiring is configuration code; the routing logic is clearly specified.

**Context Strategy**

- Start new chat? No
- Required files: All `*Navigation.kt` files from T-051–T-058, `AppLockStateManager.kt`, `AccountBindingRepository.kt`, `SessionManager.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §4.1–§4.4 (Navigation architecture)
- Documents NOT required: Domain engine, database, security documents

---

### T-060 — Phase 4 Validation Milestone

**Phase:** 4 — UI Screens
**Subsystem:** All

**Description:**
Execute all Phase 4 exit criteria. End-to-end smoke test on a physical device with real Zerodha data. LeakCanary memory leak check across all main user journeys.

**Implementation Steps**

1. Fresh install on physical device — complete onboarding, confirm account binding.
2. Manual sync from Orders screen — confirm orders, holdings, and GTT appear correctly across all screens.
3. Navigate all five tabs — confirm correct data rendering in Dark and Light themes.
4. P&L values on Portfolio screen match hand-calculated values from real order data.
5. Holdings screen shows correct average buy prices from FIFO computation.
6. GTT screen shows any `MANUAL_OVERRIDE_DETECTED` items correctly badged.
7. Fund balance entry saved and reflected in Portfolio screen balance.
8. Biometric lock triggers after 5-minute background.
9. Run LeakCanary via debug build — navigate all screens, kill and restart — confirm zero leaks reported.
10. `./gradlew :feature-portfolio:testDebugUnitTest :feature-holdings:testDebugUnitTest :feature-orders:testDebugUnitTest` — all pass.

**Acceptance Criteria**

- All Phase 4 Completion Criteria verified.
- Zero LeakCanary leaks across all main user journeys.
- P&L values match expected hand-calculated results.
- Zero `P0` crashes in 2-hour smoke test session.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (clean context before backup/export work)
- Required files: None
- Architecture docs to reference: None
- Documents NOT required: All

---

*— End of Phase 3 content (Tasks T-036 through T-060) —*

*Phase 4 content (Engineering Phases 5, 6, 7, and Final Validation Milestones) to follow upon confirmation.*

---

## Detailed Tasks — Phase 5: Backup, CSV Import, and Gmail Detection {#detailed-tasks-phase-5}

---

### T-061 — Protobuf Schema Definition and Code Generation

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:infra-backup`

**Description:**
Define the complete `.proto` schema for the `.kwbackup` file format, configure the Protobuf Gradle plugin for code generation, and verify generated Kotlin data classes compile cleanly. This task has no runtime logic — it establishes the serialization contract that all backup/restore code depends on.

**Scope Boundaries**

- Files affected: `infra-backup/src/main/proto/kitewatch_backup.proto`, `infra-backup/build.gradle.kts` (protobuf plugin config)
- Modules affected: `:infra-backup`
- Explicitly NOT touching: Backup use cases, Drive upload, restore logic

**Implementation Steps**

1. Add `com.google.protobuf` Gradle plugin and `protobuf-kotlin-lite` dependency to `:infra-backup` via `libs.versions.toml`.
2. Write `kitewatch_backup.proto` with `syntax = "proto3"` per the schema in `08_BACKUP_AND_RECOVERY.md §2.3`, covering all 13 messages: `KiteWatchBackup`, `BackupHeader`, `Order`, `Holding`, `Transaction`, `FundEntry`, `ChargeRate`, `GttRecord`, `OrderHoldingsLink`, `PnlMonthlyCache`, `GmailScanCache`, `GmailFilter`, `PersistentAlert`, `UserPreferences`.
3. Configure protobuf plugin in `build.gradle.kts`: `generateProtoTasks { all().forEach { task -> task.builtins { id("kotlin") { option("lite") } } } }`.
4. Run `./gradlew :infra-backup:generateDebugProto` — confirm generated `.kt` files in `build/generated/source/proto/`.
5. Verify generated classes are accessible from `:infra-backup` Kotlin source.

**Data Impact**

- Schema changes: None (no Room changes)
- Migration required: No

**Test Plan**

- Unit test: create a `KiteWatchBackup` proto instance, serialize to `ByteArray` via `.toByteArray()`, deserialize via `KiteWatchBackup.parseFrom()`, confirm round-trip equality on all fields.

**Acceptance Criteria**

- `./gradlew :infra-backup:generateDebugProto` completes without error.
- Generated Kotlin lite classes are importable from `:infra-backup` source.
- Proto round-trip test passes for all 13 message types.
- Proto file uses `proto3` syntax (not `proto2`).

**Rollback Strategy:** Remove protobuf plugin from `build.gradle.kts`; delete `.proto` file.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Proto schema transcription from spec and Gradle plugin configuration; no algorithmic reasoning required.

**Context Strategy**

- Start new chat? Yes (new phase: backup/restore)
- Required files: None (new files)
- Architecture docs to reference: `08_BACKUP_AND_RECOVERY.md` §2.3 (Protobuf Schema)
- Documents NOT required: Domain engine, UI, CI documents

---

### T-062 — Backup Serialization: Entity-to-Proto Mappers

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:infra-backup`

**Description:**
Implement bidirectional mapper functions between Room entity types and Protobuf message types. These are pure structural transformations — `Entity.toProto()` and `Proto.toEntity()` extension functions covering all 12 data tables included in the backup.

**Scope Boundaries**

- Files affected: `infra-backup/src/main/kotlin/.../mapper/BackupProtoMappers.kt`
- Modules affected: `:infra-backup`
- Explicitly NOT touching: Backup use cases, Drive upload, restore orchestration

**Implementation Steps**

1. Implement `OrderEntity.toProto(): OrderProto` and `OrderProto.toEntity(): OrderEntity` — map all fields including `price_paisa` (Long ↔ Long, no conversion needed), `trade_date` (String ↔ String), `exchange` (String enum).
2. Implement `HoldingEntity.toProto(): HoldingProto` and `HoldingProto.toEntity(): HoldingEntity` — include `profit_target_type` and `profit_target_value` columns.
3. Repeat for all remaining entity types: `TransactionEntity`, `FundEntryEntity`, `ChargeRateEntity`, `GttRecordEntity`, `OrderHoldingEntity`, `PnlMonthlyCacheEntity`, `GmailScanCacheEntity`, `GmailFilterEntity`, `PersistentAlertEntity`.
4. Implement `UserPreferences.toProto(): UserPreferencesProto` and `UserPreferencesProto.toDomain(): UserPreferences` — preferences exported from `PreferencesRepository.exportAll()`.
5. Null safety: all nullable entity fields map to proto default values (empty string / 0 / false) on `toProto()`; proto defaults map back to `null` on `toEntity()`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Round-trip unit tests for each entity type: create an entity with all fields populated, convert `toProto()`, convert `toEntity()`, assert field-by-field equality. Null field handling: entity with `null` optional field → proto default → back to `null`.

**Acceptance Criteria**

- All 12 entity types have bidirectional mappers.
- Round-trip tests pass for all 12 entity types.
- Null optional fields survive the round-trip as `null` (not empty string or 0).
- No business logic in mapper functions.

**Rollback Strategy:** Delete mapper file; no database impact.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: High-volume structural mapping from known source and target types.

**Context Strategy**

- Start new chat? No
- Required files: All entity files from T-014–T-016, generated proto classes from T-061
- Architecture docs to reference: `08_BACKUP_AND_RECOVERY.md` §2.3 (proto message field definitions)
- Documents NOT required: All others

---

### T-063 — CreateBackupUseCase: Serialization, Compression, and Integrity

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:infra-backup`

**Description:**
Implement `CreateBackupUseCase` covering data assembly from all 12 tables, Protobuf serialization, GZIP compression, SHA-256 checksum computation, `.kwbackup` binary header construction, and the local file fallback on Drive failure.

**Scope Boundaries**

- Files affected: `infra-backup/src/main/kotlin/.../usecase/CreateBackupUseCase.kt`, `infra-backup/src/main/kotlin/.../BackupFileWriter.kt`, `infra-backup/src/main/kotlin/.../BackupIntegrityUtil.kt`
- Modules affected: `:infra-backup`
- Explicitly NOT touching: Drive upload (T-064), restore (T-065)

**Implementation Steps**

1. Implement `BackupIntegrityUtil`: `fun sha256(data: ByteArray): ByteArray` using `MessageDigest.getInstance("SHA-256")`, `fun verifyChecksum(payload: ByteArray, expected: ByteArray): Boolean` using `MessageDigest.isEqual()` (constant-time comparison — not `==`), `fun gzipCompress(data: ByteArray): ByteArray`, `fun gzipDecompress(data: ByteArray): ByteArray`.
2. Implement `BackupFileWriter.buildFile(header: BackupHeader, compressedPayload: ByteArray): ByteArray`: writes magic bytes `"KWBK"` as 4-byte prefix, format version (uint32 big-endian), schema version (uint32), created-at ISO string (64 bytes, space-padded), account ID (32 bytes, space-padded), payload size (uint64), SHA-256 checksum (32 bytes), then the compressed payload.
3. Implement `CreateBackupUseCase.execute(destination: BackupDestination): Result<BackupResult>` per pseudocode in `08_BACKUP_AND_RECOVERY.md §3.1`:
   - Assemble all table data inside `database.withTransaction {}`.
   - Serialize to proto, compress, compute checksum, build file bytes.
   - If `destination = LOCAL`: write to `getFilesDir()/backups/` and enforce 5-backup retention.
   - If `destination = GOOGLE_DRIVE`: delegate to `GoogleDriveRemoteDataSource.upload()` (T-064). On Drive failure: fall back to local and record `status = "LOCAL_FALLBACK"` in `backup_history`.
   - Record backup history in `BackupHistoryDao`.

**Data Impact**

- Schema changes: None (backup data is read-only from Room's perspective)
- Migration required: No

**Test Plan**

- Unit test: mock all DAOs returning sample data; call `execute(LOCAL)` → confirm file written to correct path, correct magic bytes prefix, SHA-256 checksum verifiable by `BackupIntegrityUtil.verifyChecksum()`.
- Test: Drive upload mock throws `IOException` → local fallback triggered, `backup_history` status = `"LOCAL_FALLBACK"`.
- Test: round-trip — create backup bytes, parse them in `RestoreBackupUseCase` test (T-065) — record counts match.

**Acceptance Criteria**

- SHA-256 verification of the produced file passes using `BackupIntegrityUtil.verifyChecksum()`.
- Magic bytes `"KWBK"` present at offset 0.
- Drive failure triggers local fallback without throwing to caller.
- Backup file written to `getFilesDir()/backups/` with `.kwbackup` extension.
- Local retention: only 5 newest files kept (test with 6 file creation).

**Rollback Strategy:** Revert file; no database writes are made by this use case.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Binary header layout correctness (byte offsets, big-endian uint32/uint64, space-padding) and the SHA-256 constant-time comparison requirement must be explicitly verified.

**Context Strategy**

- Start new chat? No
- Required files: `BackupProtoMappers.kt`, `AppDatabase.kt`, `BackupIntegrityUtil.kt` skeleton
- Architecture docs to reference: `08_BACKUP_AND_RECOVERY.md` §2.1 (file layout), §3.1 (CreateBackupUseCase pseudocode), §3.2–§3.3
- Documents NOT required: Domain engine, UI, CI documents

---

### T-064 — Google Drive API Integration

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:core-network`

**Description:**
Implement `GoogleDriveRemoteDataSource` using the Google Drive REST API v3 (via Retrofit, not the Google Drive Android SDK). Covers upload, list, download, and delete operations for `.kwbackup` files stored in the app's Drive appdata folder.

**Scope Boundaries**

- Files affected: `core-network/src/main/kotlin/.../drive/GoogleDriveApiClient.kt`, `core-network/src/main/kotlin/.../drive/GoogleDriveRemoteDataSource.kt`, `core-network/src/main/kotlin/.../di/GoogleApiModule.kt`
- Modules affected: `:core-network`
- Explicitly NOT touching: CreateBackupUseCase, RestoreBackupUseCase, Gmail

**Implementation Steps**

1. Implement `GoogleDriveApiClient` (Retrofit interface): `@POST("upload/drive/v3/files?uploadType=multipart") suspend fun uploadFile(@Header("Authorization") auth: String, @Body body: MultipartBody): DriveFileResponse`, `@GET("drive/v3/files") suspend fun listFiles(@Header("Authorization") auth: String, @Query("spaces") spaces: String = "appDataFolder", @Query("fields") fields: String): DriveFileListResponse`, `@GET("drive/v3/files/{fileId}?alt=media") suspend fun downloadFile(@Header("Authorization") auth: String, @Path("fileId") fileId: String): ResponseBody`, `@DELETE("drive/v3/files/{fileId}") suspend fun deleteFile(@Header("Authorization") auth: String, @Path("fileId") fileId: String): Response<Unit>`.
2. Implement `GoogleDriveRemoteDataSource`: `suspend fun uploadBackup(fileName: String, fileBytes: ByteArray): DriveFileId`, `suspend fun listBackups(): List<DriveBackupEntry>`, `suspend fun downloadBackup(fileId: String): ByteArray`, `suspend fun deleteBackup(fileId: String)`. OAuth token read from `CredentialStore`. Enforce `spaces=appDataFolder` — app files are hidden from the user's Drive file list.
3. Populate `GoogleApiModule.kt` Hilt binding for `GoogleDriveRemoteDataSource`.
4. Add Drive scopes to Google Cloud Console OAuth config: `https://www.googleapis.com/auth/drive.appdata`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test with `MockWebServer`: upload returns a valid `DriveFileId`. List returns a sorted list of backup file entries. Download returns the correct byte array. Delete returns HTTP 204.
- Integration test (optional, skipped in CI): real Drive upload + list + download + delete cycle on a test account.

**Acceptance Criteria**

- All file operations use `appDataFolder` scope (files invisible in user's Drive).
- OAuth token injected from `CredentialStore`, not hardcoded.
- `MockWebServer` upload test produces a `DriveFileId`.
- `downloadBackup()` returns the complete file bytes as a `ByteArray`.

**Rollback Strategy:** Replace with local-only backup destination; Drive data source is called conditionally.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Drive API multipart upload construction and `appDataFolder` scope configuration require careful implementation. Errors leave orphaned files on Drive.

**Context Strategy**

- Start new chat? No
- Required files: `CredentialStore.kt`, `KiteConnectApiService.kt` (for Retrofit pattern reference)
- Architecture docs to reference: `08_BACKUP_AND_RECOVERY.md` §3.1 (Drive upload call site)
- Documents NOT required: Domain engine, database, UI documents

---

### T-065 — RestoreBackupUseCase: Validation, Decompression, and Atomic Write

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:infra-backup`

**Description:**
Implement `RestoreBackupUseCase` — the full restore pipeline: header validation, magic byte check, account ID mismatch detection, checksum verification, decompression, Protobuf deserialization, backup schema migration, and atomic all-or-nothing database restore.

**Scope Boundaries**

- Files affected: `infra-backup/src/main/kotlin/.../usecase/RestoreBackupUseCase.kt`, `infra-backup/src/main/kotlin/.../BackupMigrationEngine.kt`
- Modules affected: `:infra-backup`
- Explicitly NOT touching: Drive download (already in T-064), backup creation

**Implementation Steps**

1. Implement `RestoreBackupUseCase.execute(source: RestoreSource): Result<RestoreResult>` per pseudocode in `08_BACKUP_AND_RECOVERY.md §4.1`:
   - Read file bytes from `Drive` or `Local` source.
   - Parse fixed-size header; validate magic bytes `"KWBK"`.
   - Check account ID: if bound account exists and ID mismatches → return `Result.Failure(AccountMismatch)` — **do not proceed**.
   - Verify SHA-256 checksum of compressed payload using `BackupIntegrityUtil.verifyChecksum()`.
   - Decompress and deserialize Protobuf.
   - Apply `BackupMigrationEngine.migrate()` if `header.schemaVersion < currentSchemaVersion`.
   - Inside `database.withTransaction {}`: call `clearAllTables()` (in reverse FK order, preserving `account_binding`), then insert all restored records table by table.
   - Restore preferences via `PreferencesRepository.importAll()`.
   - Return `RestoreResult.Success(recordCount, backupDate, schemaVersion)`.
2. Implement `BackupMigrationEngine.migrate(data, fromVersion, toVersion): KiteWatchBackupProto` applying sequential version migrations. For Phase 1 (v1 only): the method is a no-op passthrough. The framework is present for future use.
3. Implement `clearAllTables()` clearing all 12 data tables in reverse FK dependency order, **never touching `account_binding`**.

**Data Impact**

- Schema changes: None
- Migration required: No (restore handles its own schema migration internally)

**Test Plan**

- Unit test: create a valid backup file in memory (via `CreateBackupUseCase` output), restore it, confirm all record counts match.
- Test: backup from a different account ID → `AccountMismatch` error; database not modified.
- Test: corrupted checksum → `ChecksumMismatch` error; database not modified.
- Test: `clearAllTables()` order — insert records with FK dependencies, call `clearAllTables()`, confirm no FK constraint violation.

**Acceptance Criteria**

- Account ID mismatch check fires before any table is cleared.
- Checksum mismatch check fires before any table is cleared.
- `database.withTransaction {}` ensures atomicity — if any insert fails, the database is rolled back to its pre-restore state.
- `account_binding` is never cleared by `clearAllTables()`.
- All unit tests pass.

**Rollback Strategy:** No rollback needed — the restore itself is the rollback mechanism (restores previous state). If the restore fails mid-transaction, Room auto-rollbacks.

**Estimated Complexity:** L

---

**LLM Execution Assignment**

- Recommended Model: Claude Opus
- Recommended Mode: Thinking
- Reason: The table clearing order, account ID validation sequencing, and the atomic transactional restore under failure conditions must be precisely correct. An error here can destroy all user data.

**Context Strategy**

- Start new chat? No
- Required files: `CreateBackupUseCase.kt`, `BackupIntegrityUtil.kt`, `BackupProtoMappers.kt`, `AppDatabase.kt`
- Architecture docs to reference: `08_BACKUP_AND_RECOVERY.md` §4.1 (RestoreBackupUseCase pseudocode), §4.2 (BackupMigrationEngine)
- Documents NOT required: UI, CI, domain engine documents

---

### T-066 — Backup and Restore UI: Settings Integration

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:feature-settings`

**Description:**
Add the Backup & Restore settings sub-page: manual backup trigger, backup history list, restore from Drive (file picker), restore from local file, and a pre-restore confirmation dialog that displays the backup's account ID and date.

**Scope Boundaries**

- Files affected: `feature-settings/src/main/kotlin/.../BackupRestoreScreen.kt`, `feature-settings/src/main/kotlin/.../BackupRestoreViewModel.kt`, `feature-settings/src/main/kotlin/.../component/BackupHistoryRow.kt`
- Modules affected: `:feature-settings`

**Implementation Steps**

1. `BackupRestoreViewModel`: intents — `CreateBackup(destination)`, `ListDriveBackups`, `RestoreFromDrive(fileId)`, `RestoreFromLocal(uri)`, `ConfirmRestore(source)`, `CancelRestore`. State: `isBackingUp`, `isRestoring`, `driveBackups: List<DriveBackupEntry>`, `pendingRestoreSource: RestoreSource?` (shown in confirmation dialog), `lastBackupHistory: List<BackupHistoryEntry>`, `error: String?`.
2. `BackupRestoreScreen`: two sections — "Create Backup" (Drive button, Local button, status from last backup), "Restore" (list of Drive backups with dates, "Restore from local file" button). Each restore action opens a `ConfirmationDialog` showing the backup's date and account ID before proceeding.
3. `BackupHistoryRow`: filename, timestamp, size, status badge (SUCCESS / LOCAL_FALLBACK / FAILED).
4. Wire to `BackupRestoreRoute` in `SettingsNavigation`.
5. Pre-restore automatic backup: before calling `RestoreBackupUseCase`, `BackupRestoreViewModel` first calls `CreateBackupUseCase(LOCAL)` as a safety measure. If the pre-restore backup fails, show a warning but allow the user to proceed.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test: `CreateBackup` intent → `CreateBackupUseCase.execute()` called. `ConfirmRestore` intent with correct source → `RestoreBackupUseCase.execute()` called. Pre-restore backup failure → warning state emitted, restore not blocked.

**Acceptance Criteria**

- Restore confirmation dialog shows the backup's account ID and date before any destructive action.
- Pre-restore automatic local backup completes before restore begins (or warning shown if it fails).
- Restore progress indicator blocks all user interaction during restore.
- Error state displayed if restore fails.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Settings sub-page composition following the established MVI pattern.

**Context Strategy**

- Start new chat? No
- Required files: `CreateBackupUseCase.kt`, `RestoreBackupUseCase.kt`, `ConfirmationDialog.kt`
- Architecture docs to reference: `00_PRODUCT_SPECIFICATION.md` §7 (Backup & Recovery spec)
- Documents NOT required: All others

---

### T-067 — CSV Import: Parser and Validator

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:infra-csv`

**Description:**
Implement the CSV parser supporting three Kite/Zerodha export formats. The parser produces a list of candidate `Order` domain objects or a typed error list — it never partially imports data. All-or-nothing semantics are enforced at the use case level.

**Scope Boundaries**

- Files affected: `infra-csv/src/main/kotlin/.../CsvParser.kt`, `infra-csv/src/main/kotlin/.../CsvValidator.kt`, `infra-csv/src/main/kotlin/.../model/CsvParseResult.kt`
- Modules affected: `:infra-csv`
- Explicitly NOT touching: `ImportCsvUseCase`, database writes

**Implementation Steps**

1. Define `CsvParseResult`: sealed class — `Success(orders: List<Order>)`, `ValidationFailure(errors: List<CsvRowError>)`. `CsvRowError(rowNumber: Int, field: String, message: String)`.
2. Implement `CsvParser.detect(firstLine: String): CsvFormat` — auto-detects format from column headers. Supported formats:
   - **Kite Trade Book** (`tradebook-*.csv`): columns `trade_date, tradingsymbol, exchange, segment, series, trade_type, quantity, price, order_id, trade_id, order_execution_time`.
   - **Kite Orders** (from Kite web console): columns `Date, Type, Instrument, Product, Qty, Avg. price, Status`.
   - **Custom KiteWatch format**: columns `date, stock_code, exchange, type, quantity, price, zerodha_order_id`.
3. Implement `CsvParser.parse(inputStream: InputStream, format: CsvFormat): CsvParseResult`:
   - Read all rows (Apache Commons CSV or manual split).
   - For each row: validate required fields non-empty, `quantity` is positive integer, `price` is positive decimal, `trade_date` parseable as `LocalDate`.
   - Filter: only `product = "CNC"` or `series = "EQ"` rows (delivery equity only).
   - Collect all errors — do not fail fast. Return all errors at once.
   - If any errors: return `ValidationFailure(allErrors)`. If zero errors: return `Success(orders)`.
4. Implement `CsvValidator`: separate class validating business-level rules — no future dates, no duplicate `zerodha_order_id` vs existing orders in database (duplicate check provided as a `Set<String>` parameter — no direct DB access in the validator).

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit tests with fixture CSV files (one per format): parse 10-row valid file → `Success(10 orders)`. Parse file with 2 invalid rows (one negative quantity, one malformed date) → `ValidationFailure(errors)` with exactly 2 errors. Mixed CNC/non-CNC rows → only CNC rows in output. Future date → validator error.

**Acceptance Criteria**

- All three CSV formats parsed correctly.
- All errors collected and returned together (not fail-fast).
- Non-delivery rows (`product != "CNC"`) excluded without generating errors.
- `CsvParser` has zero database dependencies.
- All unit tests pass.

**Rollback Strategy:** Revert files; no database impact.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Multi-format CSV detection heuristic and comprehensive per-row error collection without fail-fast require careful design. An overly strict parser rejects valid files; a lenient one admits corrupted data.

**Context Strategy**

- Start new chat? No
- Required files: `Order.kt`, `Paisa.kt`, `AppError.kt`
- Architecture docs to reference: `00_PRODUCT_SPECIFICATION.md` §5.2 (CSV Import spec), `04_DOMAIN_ENGINE_DESIGN.md` §3.1 (BR-11, BR-12)
- Documents NOT required: Backup, UI, CI documents

---

### T-068 — ImportCsvUseCase: Atomic Import with Pre-Import Backup

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:core-domain`

**Description:**
Implement `ImportCsvUseCase` — orchestrates the CSV import flow: pre-import local backup, parse and validate the file, duplicate detection, and atomic all-or-nothing database write including charge calculation for all imported orders.

**Scope Boundaries**

- Files affected: `core-domain/src/main/kotlin/.../usecase/orders/ImportCsvUseCase.kt`
- Modules affected: `:core-domain`
- Explicitly NOT touching: CSV parser internals (T-067), backup engine (T-063)

**Implementation Steps**

1. Implement `ImportCsvUseCase.execute(fileUri: Uri): Result<ImportResult>`.
2. **Pre-import backup**: call `createBackupUseCase.execute(LOCAL)`. If backup fails: log warning, do not block — proceed with import (matches spec: `08_BACKUP_AND_RECOVERY.md §5.3`).
3. **Parse**: call `csvParser.parse(contentResolver.openInputStream(fileUri))` → if `ValidationFailure`: return `Result.Failure(ValidationError(errors))` — do not write any data (BR-11).
4. **Duplicate detection**: call `orderRepo.getExistingZerodhaIds()` (returns `Set<String>`); pass to `CsvValidator.checkDuplicates()`. Duplicate orders are silently skipped (not errors) — matches BR-12.
5. **Charge calculation**: for each new non-duplicate order, call `ChargeCalculator.calculate()` using current `ChargeRateSnapshot`. If no charge rates available: use hardcoded fallback rates and flag the orders as `charges_estimated = true`.
6. **Atomic write**: inside a single `database.withTransaction {}`: insert all new orders, upsert holdings via `HoldingsComputationEngine`, insert transaction ledger entries, update P&L cache.
7. Return `ImportResult(newOrderCount, skippedDuplicateCount, estimatedChargesCount)`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit tests: valid CSV with 5 orders (2 duplicates) → `ImportResult(newOrderCount=3, skippedDuplicateCount=2)`. Invalid CSV → `Result.Failure(ValidationError)`, zero DB writes. Pre-import backup failure → warning logged, import proceeds.

**Acceptance Criteria**

- Invalid CSV returns failure with all row errors — no partial writes (BR-11).
- Duplicate orders silently skipped, not errors (BR-12).
- Pre-import backup failure does not block import.
- Atomic transaction rolls back entirely on any write failure.
- `ImportResult` reports accurate new/skipped/estimated counts.

**Rollback Strategy:** Revert file; the pre-import local backup provides data recovery.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: The interaction between pre-import backup, validation failure paths, duplicate skipping, and the atomic transaction boundary requires careful orchestration to ensure BR-11 (no partial imports) is never violated.

**Context Strategy**

- Start new chat? No
- Required files: `CsvParser.kt`, `ChargeCalculator.kt`, `HoldingsComputationEngine.kt`, `CreateBackupUseCase.kt`, `OrderRepository.kt`
- Architecture docs to reference: `04_DOMAIN_ENGINE_DESIGN.md` §3.1 (BR-11, BR-12), `08_BACKUP_AND_RECOVERY.md` §5.3
- Documents NOT required: UI, CI, backup format documents

---

### T-069 — CSV Import UI: File Picker and Validation Result Screen

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:feature-settings`

**Description:**
Add the CSV import flow to Settings: file picker integration, progress indicator during import, and a detailed result screen showing counts and any per-row validation errors if the import was rejected.

**Scope Boundaries**

- Files affected: `feature-settings/src/main/kotlin/.../CsvImportScreen.kt`, `feature-settings/src/main/kotlin/.../CsvImportViewModel.kt`, `feature-settings/src/main/kotlin/.../component/CsvErrorRow.kt`
- Modules affected: `:feature-settings`

**Implementation Steps**

1. `CsvImportViewModel`: intent `SelectFile(uri: Uri)` triggers `ImportCsvUseCase.execute(uri)`. State: `isImporting`, `result: CsvImportUiResult?`, `error: String?`. `CsvImportUiResult` shows new/skipped/estimated counts on success, or a list of `CsvErrorUiModel(row, field, message)` on failure.
2. `CsvImportScreen`: "Import CSV" button launches `ActivityResultContracts.GetContent("text/csv")` file picker. During import: full-screen `CircularProgressIndicator` blocking interaction. On success: green summary card with counts. On failure: red header + scrollable `LazyColumn` of `CsvErrorRow` items.
3. `CsvErrorRow`: row number, field name, error description.
4. Wire to the Settings navigation graph.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Compose UI test with mocked ViewModel: `isImporting=true` → progress shown. `result=Success(3, 2, 0)` → success card shows `3 new orders`. `result=Failure(errors=[...])` → error list visible.

**Acceptance Criteria**

- File picker opens the correct MIME type filter.
- Validation errors displayed per-row with row number and field.
- Success card shows all three counts (new, skipped, estimated).
- Import button disabled during `isImporting=true`.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Standard file picker integration and result display UI.

**Context Strategy**

- Start new chat? No
- Required files: `ImportCsvUseCase.kt`, `ConfirmationDialog.kt`
- Architecture docs to reference: `00_PRODUCT_SPECIFICATION.md` §5.2 (CSV Import UI)
- Documents NOT required: All others

---

### T-070 — Excel Export

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:infra-csv`

**Description:**
Implement `ExcelExportUseCase` producing a multi-sheet `.xlsx` file from the Orders, Holdings, and Transactions tables using the Apache POI library (or the lightweight `dxl` library). Export is written to a temporary file, then shared via Android `FileProvider`.

**Scope Boundaries**

- Files affected: `infra-csv/src/main/kotlin/.../ExcelExporter.kt`, `core-domain/src/main/kotlin/.../usecase/backup/ExportExcelUseCase.kt`
- Modules affected: `:infra-csv`, `:core-domain`

**Implementation Steps**

1. Add `poi-ooxml` (or `dxl`) dependency to `:infra-csv` via `libs.versions.toml`. Use the smallest viable library — POI is large; evaluate `dxl` first.
2. Implement `ExcelExporter.export(orders, holdings, transactions): ByteArray`:
   - Sheet 1 "Orders": headers + one row per order with formatted columns (`Date`, `Stock`, `Type`, `Qty`, `Price ₹`, `Total ₹`, `Charges ₹`).
   - Sheet 2 "Holdings": headers + one row per holding with `Stock`, `Qty`, `Avg Buy Price ₹`, `Target Sell Price ₹`, `Invested ₹`, `GTT Status`.
   - Sheet 3 "Transactions": headers + one row per transaction with `Date`, `Type`, `Stock`, `Amount ₹`, `Description`.
   - Column widths auto-sized. Header row bold.
   - Returns `ByteArray` of the `.xlsx` file.
3. Implement `ExportExcelUseCase.execute(): Result<Uri>`: fetch all orders, active holdings, and all transactions from repositories. Call `ExcelExporter.export()`. Write `ByteArray` to `getCacheDir()/exports/kitewatch_export_{date}.xlsx`. Return `FileProvider.getUriForFile()` URI suitable for `Intent.ACTION_SEND`.

**Data Impact**

- Schema changes: None (read-only)
- Migration required: No

**Test Plan**

- Unit test: call `ExcelExporter.export()` with sample data; parse the output `ByteArray` as an `XSSFWorkbook`; confirm three sheets exist with correct column headers and row counts matching input.

**Acceptance Criteria**

- Exported `.xlsx` has three sheets with correct names.
- Row count in each sheet matches the number of records passed to the exporter.
- Headers are bold.
- Output `ByteArray` is a valid `.xlsx` file (parseable by Apache POI / LibreOffice).

**Rollback Strategy:** Disable export button in Settings; no data is modified.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Excel generation is established library API usage; no domain logic.

**Context Strategy**

- Start new chat? No
- Required files: `Order.kt`, `Holding.kt`, `Transaction.kt`, `CurrencyFormatter.kt`
- Architecture docs to reference: `00_PRODUCT_SPECIFICATION.md` §5.3 (Excel Export spec)
- Documents NOT required: All others

---

### T-071 — Gmail Fund Detection: GmailApiClient and Message Parser

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:core-network`

**Description:**
Implement `GmailApiClient` (Retrofit interface for Gmail REST API), `GmailMessageParser` (extracts fund amount from Zerodha fund-credit email bodies), and `GmailRemoteDataSource` (orchestrates search + parse).

**Scope Boundaries**

- Files affected: `core-network/src/main/kotlin/.../gmail/GmailApiClient.kt`, `core-network/src/main/kotlin/.../gmail/GmailMessageParser.kt`, `core-network/src/main/kotlin/.../gmail/GmailRemoteDataSource.kt`
- Modules affected: `:core-network`
- Explicitly NOT touching: `ScanGmailUseCase`, `ConfirmGmailEntryUseCase`, fund repository

**Implementation Steps**

1. Implement `GmailApiClient` (Retrofit): `@GET("gmail/v1/users/me/messages") suspend fun listMessages(@Query("q") query: String, @Query("maxResults") max: Int = 50): GmailMessageListResponse`, `@GET("gmail/v1/users/me/messages/{id}") suspend fun getMessage(@Path("id") id: String, @Query("format") format: String = "full"): GmailMessageResponse`.
2. Implement `GmailMessageParser.parse(messageBody: String): FundDetectionResult?`:
   - Match Zerodha fund credit email patterns: subject `"Funds added to Kite"`, body contains amount pattern `"Rs. [\d,]+\.?\d*"` or `"₹[\d,]+\.?\d*"`.
   - Extract amount, parse to `Paisa`.
   - Extract date from email headers.
   - Return `FundDetectionResult(amount: Paisa, date: LocalDate, messageId: String, subject: String)` or `null` if pattern not found.
3. Implement `GmailRemoteDataSource.scanForFundCredits(since: LocalDate): List<FundDetectionResult>`:
   - Build Gmail search query: `from:no-reply@zerodha.com subject:"Funds added" after:{since}`.
   - Fetch message list, then fetch each message body.
   - Parse each message; collect non-null results.
   - Filter: skip `gmail_message_id` values already in `GmailScanCacheDao.exists()`.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test `GmailMessageParser` with 3 fixture email bodies: valid Zerodha fund credit email → `FundDetectionResult` with correct amount. Non-Zerodha email → `null`. Zerodha email with non-fund subject → `null`.
- `MockWebServer` test for `GmailRemoteDataSource`: list returns 2 message IDs, fetch returns 2 message bodies, 1 parsed successfully → `List<FundDetectionResult>` of size 1.

**Acceptance Criteria**

- `GmailMessageParser` correctly extracts `Paisa` from `"Rs. 50,000.00"` and `"₹50,000"` formats.
- Already-processed `gmail_message_id` values are skipped.
- Parser returns `null` for non-matching emails (does not throw).

**Rollback Strategy:** Remove Gmail feature flag; `ScanGmailUseCase` is guarded by a feature flag in settings.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Regex pattern for Indian currency amounts (`Rs. 1,00,000.00` vs `₹1,00,000`) must cover all format variants without false positives.

**Context Strategy**

- Start new chat? No
- Required files: `Paisa.kt`, `GmailScanCacheDao.kt`, `CredentialStore.kt`
- Architecture docs to reference: `06_AUTOMATION_AND_INTEGRATION.md` §4 (Gmail Detection pipeline)
- Documents NOT required: Backup, CSV, database DDL documents

---

### T-072 — ScanGmailUseCase, ConfirmGmailEntryUseCase, and Pending Detection UI

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** `:core-domain`, `:feature-settings`

**Description:**
Implement `ScanGmailUseCase` (orchestrates Gmail scan + stores pending detections), `ConfirmGmailEntryUseCase` (user-confirms a detected entry and promotes it to a real `FundEntry`), and the Pending Detections UI in Settings.

**Scope Boundaries**

- Files affected: `core-domain/src/main/kotlin/.../usecase/gmail/ScanGmailUseCase.kt`, `core-domain/src/main/kotlin/.../usecase/gmail/ConfirmGmailEntryUseCase.kt`, `feature-settings/src/main/kotlin/.../GmailDetectionsScreen.kt`, `feature-settings/src/main/kotlin/.../GmailDetectionsViewModel.kt`
- Modules affected: `:core-domain`, `:feature-settings`

**Implementation Steps**

1. `ScanGmailUseCase.execute(): Result<Int>`:
   - Calls `gmailDataSource.scanForFundCredits(since = 90 days ago)`.
   - For each result: insert `GmailScanCacheEntity(status=PENDING)` if `gmail_message_id` not already in cache (idempotent).
   - Returns count of new pending detections.
2. `ConfirmGmailEntryUseCase.execute(gmailMessageId: String): Result<FundEntry>`:
   - Fetch `GmailScanCacheEntity` by `gmail_message_id`.
   - Call `AddFundEntryUseCase(amount, date, entryType=GMAIL_DETECTED)`.
   - Update `GmailScanCacheEntity.status = CONFIRMED`, `linked_fund_entry_id = newEntryId`.
   - Return `Result.Success(fundEntry)`.
3. `GmailDetectionsViewModel`: observes `GmailScanCacheDao.observePending()`. Intents: `ScanNow`, `Confirm(messageId)`, `Dismiss(messageId)`.
4. `GmailDetectionsScreen` in Settings: list of pending detections with amount, date, email subject. Each row has "Confirm" and "Dismiss" buttons. "Scan Now" action in top bar.

**Data Impact**

- Schema changes: None (`gmail_scan_cache` already exists)
- Migration required: No

**Test Plan**

- `ScanGmailUseCase`: mock `GmailRemoteDataSource` returning 3 results; confirm 3 `GmailScanCacheEntity` rows inserted with `status=PENDING`. Second call with same IDs → 0 new insertions (idempotent).
- `ConfirmGmailEntryUseCase`: confirm a pending entry → `FundEntry` inserted, cache entity status = `CONFIRMED`.

**Acceptance Criteria**

- `ScanGmailUseCase` is idempotent — scanning twice does not create duplicate entries.
- Confirmed entries appear in the fund balance calculation immediately.
- Dismissed entries set `status=DISMISSED` and disappear from the pending list.
- BR-14 enforced: Gmail-detected entries require explicit user confirmation before becoming a `FundEntry`.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Use case orchestration with clear pre-specified inputs/outputs; UI is a standard list with action buttons.

**Context Strategy**

- Start new chat? No
- Required files: `GmailRemoteDataSource.kt`, `AddFundEntryUseCase.kt`, `GmailScanCacheDao.kt`, `FundEntry.kt`
- Architecture docs to reference: `06_AUTOMATION_AND_INTEGRATION.md` §4, `04_DOMAIN_ENGINE_DESIGN.md` §3.1 (BR-14)
- Documents NOT required: Backup, CI, database DDL documents

---

### T-073 — Phase 5 Validation Milestone

**Phase:** 5 — Backup, CSV Import, and Gmail Detection
**Subsystem:** All

**Description:**
Execute all Phase 5 exit criteria: verify backup round-trip integrity, CSV import all-or-nothing semantics, Excel export contents, and Gmail detection idempotency.

**Implementation Steps**

1. Create a backup from a device with real data. Install the app fresh on a second device. Restore from the Drive backup. Confirm all order counts, holding values, and fund balance match the original.
2. Import a 50-row CSV trade book file. Verify order count increases by the expected amount. Verify holdings recompute correctly.
3. Import a CSV with 3 invalid rows. Confirm zero orders imported and all 3 errors displayed.
4. Export to Excel. Open the `.xlsx` file in a spreadsheet application. Confirm three sheets with correct headers and row counts.
5. Run `ScanGmailUseCase` twice with the same inbox state. Confirm pending detection count does not double.
6. `./gradlew :infra-backup:testDebugUnitTest :infra-csv:testDebugUnitTest :core-network:testDebugUnitTest` — all pass.

**Acceptance Criteria**

- Backup round-trip: restored device has identical record counts to original.
- CSV invalid-row import returns zero writes.
- Gmail scan is idempotent across two consecutive calls.
- Excel export opens cleanly in LibreOffice Calc.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (clean context before security hardening)
- Required files: None
- Architecture docs to reference: None
- Documents NOT required: All

---

## Detailed Tasks — Phase 6: Security Hardening {#detailed-tasks-phase-6}

---

### T-074 — SQLCipher Encryption Integration

**Phase:** 6 — Security Hardening
**Subsystem:** `:core-database`, `:infra-auth`

**Description:**
Migrate `AppDatabase` from standard Room to SQLCipher-backed encrypted Room. Implement `MasterKeyProvider` (deriving the database passphrase from Android Keystore), replace the `Room.databaseBuilder()` call with a `SupportOpenHelperFactory(passphrase)` variant, and verify that the database file at `databases/kitewatch.db` is no longer readable as plaintext SQLite.

**Scope Boundaries**

- Files affected: `core-database/src/main/kotlin/.../AppDatabase.kt`, `infra-auth/src/main/kotlin/.../MasterKeyProvider.kt` (update), `core-database/src/main/kotlin/.../di/DatabaseModule.kt`, `app/proguard-rules.pro` (SQLCipher keep rules)
- Modules affected: `:core-database`, `:infra-auth`
- Explicitly NOT touching: DAOs, entities, schema, repository implementations

**Implementation Steps**

1. Add `net.zetetic:android-database-sqlcipher` and `androidx.sqlite:sqlite-ktx` dependencies to `:core-database` via `libs.versions.toml`. SQLCipher replaces the standard SQLite.
2. Update `MasterKeyProvider` to also derive a `databasePassphrase: ByteArray`: generate a 256-bit random passphrase on first launch via `SecureRandom`, store it in `EncryptedSharedPreferences` under key `db_passphrase_b64` (base64-encoded). Retrieve on subsequent launches. The passphrase is protected by the Android Keystore-backed `EncryptedSharedPreferences` master key.
3. Update `AppDatabase.buildDatabase()`: pass `SupportOpenHelperFactory(passphrase)` to `Room.databaseBuilder(... ).openHelperFactory(factory)`. Remove all `fallbackToDestructiveMigration()` calls (they should already be absent per earlier tasks).
4. Add SQLCipher ProGuard keep rules to `proguard-rules.pro`: `-keep class net.sqlcipher.** { *; }`.
5. Verify: on a fresh install, `databases/kitewatch.db` is not openable by standard `sqlite3` CLI (returns `file is not a database`). On the device, the app operates normally.

**Data Impact**

- Schema changes: None
- Migration required: No (fresh install only — no migration from unencrypted to encrypted for Phase 1. Existing development devices must wipe app data and reinstall.)

**Test Plan**

- Instrumented test: open the database, insert an order, read it back — confirm round-trip works. Confirm the database file fails to open with the standard `SQLiteDatabase.openDatabase()` (throws `SQLiteException`).
- Manual: `adb pull /data/data/com.kitewatch.app/databases/kitewatch.db` → attempt to open with `sqlite3 kitewatch.db` → must fail with "not an encrypted database" or similar error.

**Acceptance Criteria**

- Database file cannot be opened by standard `sqlite3` or unencrypted Room.
- App operates normally after SQLCipher integration.
- Passphrase stored in `EncryptedSharedPreferences`, never in plaintext.
- Instrumented round-trip test passes.

**Rollback Strategy:** Revert `buildDatabase()` to use standard `Room.databaseBuilder()` without the `openHelperFactory`. Development convenience only — never ship without SQLCipher.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: SQLCipher integration changes the fundamental database opening mechanism. The passphrase derivation and storage chain (SecureRandom → EncryptedSharedPreferences → SQLCipher) must be correct on first install and on every subsequent launch.

**Context Strategy**

- Start new chat? Yes (new phase: security hardening)
- Required files: `AppDatabase.kt`, `MasterKeyProvider.kt`, `CredentialStore.kt`
- Architecture docs to reference: `07_SECURITY_MODEL.md` §2.1 (SQLCipher Configuration), §2.2 (EncryptedSharedPreferences), §3 (Key Management)
- Documents NOT required: Domain engine, UI, CI, backup documents

---

### T-075 — FLAG_SECURE, Log Scrubbing, and Screen Security

**Phase:** 6 — Security Hardening
**Subsystem:** `:app`, `:feature-auth`

**Description:**
Apply `FLAG_SECURE` to all financial screens to prevent task-switcher screenshots and screen recording. Implement log scrubbing to ensure no sensitive field values appear in Timber log output. Implement `android:allowBackup="false"` in the manifest.

**Scope Boundaries**

- Files affected: `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/.../MainActivity.kt`, `core-network/src/main/kotlin/.../KiteConnectAuthInterceptor.kt` (log audit), all `Timber.` call sites in `:core-domain` use cases
- Modules affected: `:app`, `:core-network`, `:core-domain`

**Implementation Steps**

1. In `AndroidManifest.xml`: set `android:allowBackup="false"` on the `<application>` element. This prevents `adb backup` extraction.
2. In `MainActivity.onCreate()`: call `window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)`. This applies to the entire activity, blocking screenshots and screen recording from system recents.
3. Audit all `Timber.d()`, `Timber.w()`, `Timber.e()` calls across all modules:
   - Search for any call that logs: `access_token`, `api_key`, `api_secret`, `request_token`, email addresses, order IDs, monetary amounts. Flag any such call.
   - Replace flagged calls with sanitized versions — log category and error type only, never field values. Example: `Timber.e("Order sync failed: NetworkError")` instead of `Timber.e("Token expired: $accessToken")`.
4. Implement `SensitiveDataFilter` — a simple utility that redacts a `Set<String>` of known-sensitive keys from structured log messages if used elsewhere.
5. Verify that the `ReleaseTree` no-op (from T-007) is still in place and active.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Instrumented: from task switcher, attempt to screenshot the app — confirm the screenshot is blank (FLAG_SECURE active).
- Code review pass: grep all Timber calls in `:core-network` and `:core-domain` for `access_token`, `api_key`, `api_secret`, `password` — confirm zero results.
- Manual: `adb backup -apk -f backup.ab com.kitewatch.app` → confirm backup file is empty (allowBackup=false).

**Acceptance Criteria**

- `FLAG_SECURE` prevents task-switcher screenshot.
- `android:allowBackup="false"` in manifest.
- Zero Timber calls log any sensitive credential field value.
- `ReleaseTree` no-op remains active in release build.

**Rollback Strategy:** Remove `FLAG_SECURE` call for development if it interferes with debugging. It must be present in all release builds.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Manifest changes and `FLAG_SECURE` are one-liner calls. Log audit is a systematic search-and-replace task.

**Context Strategy**

- Start new chat? No
- Required files: `AndroidManifest.xml`, `MainActivity.kt`, `KiteConnectAuthInterceptor.kt`
- Architecture docs to reference: `07_SECURITY_MODEL.md` §1.2 (T-02, T-03, T-08, T-09 threat mitigations)
- Documents NOT required: Domain engine, database, backup documents

---

### T-076 — Certificate Pinning for Kite Connect API

**Phase:** 6 — Security Hardening
**Subsystem:** `:core-network`

**Description:**
Implement certificate pinning on the Kite Connect API hostname (`api.kite.trade`) using OkHttp's `CertificatePinner`. Pin both the leaf certificate SHA-256 hash and at least one intermediate CA hash as a backup pin to prevent lockout on certificate rotation.

**Scope Boundaries**

- Files affected: `core-network/src/main/kotlin/.../kiteconnect/KiteConnectCertificatePinner.kt`, `NetworkModule.kt` (amend OkHttp builder)
- Modules affected: `:core-network`

**Implementation Steps**

1. Extract the SHA-256 pin for `api.kite.trade`'s current leaf certificate using: `openssl s_client -connect api.kite.trade:443 | openssl x509 -pubkey | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64`.
2. Extract the SHA-256 pin for the intermediate CA certificate (Digicert or equivalent).
3. Implement `KiteConnectCertificatePinner.build(): CertificatePinner` returning `CertificatePinner.Builder().add("api.kite.trade", "sha256/{leaf_pin}", "sha256/{intermediate_pin}").build()`.
4. Add the `CertificatePinner` to the OkHttp client builder in `NetworkModule`.
5. Add a `network_security_config.xml` as a belt-and-suspenders backup: `<domain-config cleartextTrafficPermitted="false"><domain includeSubdomains="true">api.kite.trade</domain></domain-config>`.
6. Document pin rotation procedure in `10_DEPLOYMENT_WORKFLOW.md` (add a section): when Zerodha rotates their certificate, the new pin must be included in a new app release before the old certificate expires.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: connect to `api.kite.trade` from the app — confirms pinning does not block valid connections.
- `MockWebServer` test with a different certificate: `CertificatePinner` causes `SSLPeerUnverifiedException`. Confirm the app handles this as `AppError.NetworkError.CertificateMismatch` — not a crash.

**Acceptance Criteria**

- Certificate pinning active for `api.kite.trade`.
- At least one backup (intermediate CA) pin included.
- `SSLPeerUnverifiedException` from pin mismatch is caught and converted to `AppError`.
- `network_security_config.xml` disables cleartext traffic.
- Pin rotation procedure documented.

**Rollback Strategy:** Remove `CertificatePinner` from OkHttp builder. Do this only in an emergency (Zerodha certificate rotation before a new release is deployed).

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: `CertificatePinner` API is a well-documented OkHttp configuration.

**Context Strategy**

- Start new chat? No
- Required files: `NetworkModule.kt`, `ApiResultAdapter.kt`
- Architecture docs to reference: `07_SECURITY_MODEL.md` §5 (Certificate Pinning)
- Documents NOT required: All domain, database, UI documents

---

### T-077 — Security Audit and Phase 6 Validation

**Phase:** 6 — Security Hardening
**Subsystem:** All

**Description:**
Execute a systematic security audit against the threat model in `07_SECURITY_MODEL.md §1`. Verify all 10 threat mitigations are implemented. This is a verification task with any discovered gaps treated as bugs to fix before Phase 7.

**Implementation Steps**

1. T-01 (Physical access): verify SQLCipher, biometric lock, `EncryptedSharedPreferences`. Install the debug APK on a rooted emulator; pull the database file; confirm it cannot be decrypted without the passphrase.
2. T-02 (Shoulder surfing): verify `FLAG_SECURE` in task switcher. Verify no financial amounts appear in notifications.
3. T-03 (Screen overlay / accessibility): verify `FLAG_SECURE` on all screens.
4. T-04 (Root access / file system read): verify SQLCipher encrypts the database file; `EncryptedSharedPreferences` encrypts secrets.xml; confirm `allowBackup=false`.
5. T-05/T-06 (MITM / DNS spoofing): verify certificate pinning active; confirm `cleartext=false`; confirm `api.kite.trade` pin validated.
6. T-07 (Backup interception): verify `.kwbackup` files are transferred via TLS to Drive (inspect with a debug proxy); confirm SHA-256 integrity check is applied on restore.
7. T-08 (Task switcher screenshot): attempt a screenshot via the recent apps screen — confirm blank capture.
8. T-09 (ADB extraction): `adb backup` attempt → confirm empty or rejected.
9. T-10 (Supply chain): audit `libs.versions.toml` — confirm no analytics, tracking, or crash reporting SDKs are included that send data off-device without disclosure.
10. Run the full test suite; confirm zero regression from security changes.

**Acceptance Criteria**

- All 10 threat mitigations verified with passing tests or confirmed manual checks.
- Zero new crashes introduced by security changes.
- No financial data visible in any logcat output on a release build.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? No
- Required files: `07_SECURITY_MODEL.md`
- Architecture docs to reference: `07_SECURITY_MODEL.md` §1 (threat model)
- Documents NOT required: All others

---

## Detailed Tasks — Phase 7: Integration Tests, Performance, and Release {#detailed-tasks-phase-7}

---

### T-078 — End-to-End Integration Test Suite

**Phase:** 7 — Integration Tests, Performance, and Release
**Subsystem:** All

**Description:**
Write a comprehensive instrumented end-to-end test suite covering the five most critical user journeys. Tests run on a connected device or emulator using fake network responses from `MockWebServer`, injected via Hilt test modules.

**Scope Boundaries**

- Files affected: `app/src/androidTest/kotlin/.../e2e/OrderSyncE2ETest.kt`, `CsvImportE2ETest.kt`, `BackupRestoreE2ETest.kt`, `BiometricLockE2ETest.kt`, `GttAutomationE2ETest.kt`
- Modules affected: `:app` (instrumented test source set)

**Implementation Steps**

1. Establish Hilt test infrastructure: `@HiltAndroidTest` base class, `@UninstallModules` to replace `NetworkModule` with `FakeNetworkModule` pointing to `MockWebServer`, `@UninstallModules` to replace `DatabaseModule` with an in-memory Room variant.
2. `OrderSyncE2ETest`: enqueue mock Kite API responses (orders + holdings); call `SyncOrdersUseCase.execute()`; assert Room database contains the expected orders; assert holdings are correctly computed; assert GTT actions are queued.
3. `CsvImportE2ETest`: load a fixture `.csv` file from `androidTest/assets/`; call `ImportCsvUseCase.execute(uri)`; assert order count in Room; assert holding average buy price correct; assert pre-import backup file created in `getFilesDir()/backups/`.
4. `BackupRestoreE2ETest`: insert known data into Room; call `CreateBackupUseCase.execute(LOCAL)`; clear all tables; call `RestoreBackupUseCase.execute(LOCAL)`; assert all table record counts restored correctly; assert checksum validated.
5. `BiometricLockE2ETest`: (Robolectric unit test — biometric cannot be triggered on emulator): use `BiometricManager` test stubs; verify `AppLockStateManager` emits `LockRequired` after 5-minute simulated background; verify it does NOT emit for 3-minute background.
6. `GttAutomationE2ETest`: insert holdings; enqueue mock GTT API; call `PlaceGttUseCase.execute()`; assert `GttRecord` status in Room matches expected (`ACTIVE` for new GTTs, `MANUAL_OVERRIDE_DETECTED` for user-modified).

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- The tests themselves are the test plan.

**Acceptance Criteria**

- All 5 E2E tests pass on an emulator running API 26 (minimum).
- All 5 E2E tests pass on a physical device running API 34.
- Tests are hermetic — no real network calls; no persistent state between tests.
- Tests complete in < 3 minutes total (emulator).

**Rollback Strategy:** Disable E2E tests from CI if they become flaky; run manually before each release instead.

**Estimated Complexity:** L

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Hilt test module replacement, `MockWebServer` + Room in-memory integration, and asserting multi-layer state transitions require careful test design to remain hermetic and non-flaky.

**Context Strategy**

- Start new chat? Yes (new phase: testing and release)
- Required files: `SyncOrdersUseCase.kt`, `ImportCsvUseCase.kt`, `CreateBackupUseCase.kt`, `RestoreBackupUseCase.kt`, `PlaceGttUseCase.kt`, `AppLockStateManager.kt`
- Architecture docs to reference: `11_EXECUTION_PLAN.md` §5 (Testing Strategy)
- Documents NOT required: All UI, CI workflow, domain engine detailed documents

---

### T-079 — Performance Profiling and Baseline Verification

**Phase:** 7 — Integration Tests, Performance, and Release
**Subsystem:** All

**Description:**
Measure app performance against the 6 baselines defined in `10_DEPLOYMENT_WORKFLOW.md §4.3`. Use Android Studio Profiler for cold start and query timing. Fix any measurements that exceed the baseline before marking Phase 7 complete.

**Scope Boundaries**

- Files affected: Any code identified as exceeding performance baselines (bug fixes only)
- Modules affected: Any

**Baselines to Verify**

| Metric | Target | Measurement Method |
|---|---|---|
| APK size | < 25 MB | `./gradlew assembleRelease` → APK file size |
| Cold start time | < 2,500 ms | Android Studio Profiler, 3 consecutive cold starts, take median |
| Room holdings query (1,000 rows) | < 50 ms | `measureTimeMillis {}` instrumented test, `HoldingDao.getAll()` |
| P&L calc (1,000 orders) | < 200 ms | `measureTimeMillis {}` unit test on `PnlCalculator.calculate()` with synthetic data |
| Background sync duration | < 30 s | `SyncOrdersUseCase.execute()` with mocked network; measure wall clock |
| Backup serialize (full DB) | < 5 s | `CreateBackupUseCase.execute(LOCAL)` with synthetic data; measure wall clock |

**Implementation Steps**

1. Build the release APK: `./gradlew assembleRelease`. Measure file size. If > 25 MB: enable R8 aggressive mode, audit dependencies, remove unused resources.
2. Measure cold start on a physical device (low-end device preferred — Pixel 4a or equivalent). If > 2,500 ms: profile `KiteWatchApplication.onCreate()` — identify and defer any heavy synchronous initialization.
3. Write instrumented test `HoldingsDaoPerformanceTest`: insert 1,000 `HoldingEntity` rows in an in-memory database; measure `holdingDao.getAll()` via `measureTimeMillis {}`. Fail the test if > 50 ms.
4. Write unit test `PnlCalculatorPerformanceTest`: generate 1,000 synthetic `Order` objects; measure `PnlCalculator.calculate()`. Fail if > 200 ms.
5. Write instrumented test `BackupPerformanceTest`: populate all 12 tables with realistic row counts (orders: 2,000, transactions: 3,000, etc.); measure `CreateBackupUseCase.execute(LOCAL)`. Fail if > 5,000 ms.
6. Fix any failing baselines. Common fixes: add database indices, switch to lazy initialization for heavy singletons, use `Dispatchers.IO` for any sync call accidentally on the main dispatcher.

**Data Impact**

- Schema changes: Possibly (adding a missing index if a query baseline fails)
- Migration required: Only if a schema index is added (requires a new Room `Migration` object)

**Test Plan**

- All 6 performance tests pass with measurements under baseline.

**Acceptance Criteria**

- All 6 performance baselines met on the target device class.
- If any schema change is required: a `Migration` object and `MigrationTestHelper` test are added and `DATABASE_VERSION` incremented.
- Performance tests are part of the CI suite (`ci-pr.yml`) as of this milestone.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Performance benchmarking is instrumented test writing and metric collection; fixes are targeted bug repairs.

**Context Strategy**

- Start new chat? No
- Required files: `HoldingDao.kt`, `PnlCalculator.kt`, `CreateBackupUseCase.kt`, `AppDatabase.kt`
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §4.3 (Performance Baselines)
- Documents NOT required: All others

---

### T-080 — In-App Update Notification

**Phase:** 7 — Integration Tests, Performance, and Release
**Subsystem:** `:core-network`, `:feature-settings`

**Description:**
Implement the lightweight in-app update check that fetches a static JSON manifest from a stable URL once per 24 hours, compares `latest_version_code` against `BuildConfig.VERSION_CODE`, and shows a non-blocking banner or a blocking force-upgrade dialog when `min_supported_version_code` is exceeded.

**Scope Boundaries**

- Files affected: `core-network/src/main/kotlin/.../UpdateCheckService.kt`, `core-domain/src/main/kotlin/.../usecase/UpdateCheckUseCase.kt`, `app/src/main/kotlin/.../update/UpdateCheckManager.kt`
- Modules affected: `:core-network`, `:core-domain`, `:app`

**Implementation Steps**

1. Define the update manifest JSON schema: `{ "latest_version_code": 5, "latest_version_name": "1.2.0", "min_supported_version_code": 2, "download_url": "https://github.com/…/releases/latest", "release_notes": "Bug fixes" }`.
2. Implement `UpdateCheckService` (Retrofit or `OkHttpClient.newCall()`): `suspend fun fetchManifest(): UpdateManifest?`. URL is stored in `BuildConfig`. Returns `null` on any network failure — update check never throws.
3. Implement `UpdateCheckUseCase.execute(): UpdateCheckResult` (sealed: `UpToDate`, `UpdateAvailable(versionName, downloadUrl, releaseNotes)`, `ForceUpgradeRequired(downloadUrl)`). Compares `BuildConfig.VERSION_CODE` against manifest values.
4. Implement `UpdateCheckManager`: called from `MainActivity.onStart()` if the last check was > 24 hours ago (timestamp in `DataStore`). On `UpdateAvailable`: emits an app-level event; `MainActivity` shows a dismissible `AlertBanner`. On `ForceUpgradeRequired`: shows a non-dismissible dialog with a "Download Update" button opening the download URL in a browser. Update check failure: silently ignored, no user-visible error.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test `UpdateCheckUseCase`: manifest with `latest_version_code = BuildConfig.VERSION_CODE + 1` → `UpdateAvailable`. `min_supported_version_code > BuildConfig.VERSION_CODE` → `ForceUpgradeRequired`. Network failure (null manifest) → `UpToDate` (fail safe).
- Unit test `UpdateCheckManager`: last check > 24 hours → `UpdateCheckUseCase` called. Last check < 24 hours → `UpdateCheckUseCase` not called.

**Acceptance Criteria**

- Update check failure is silently ignored — no user-visible error.
- Force-upgrade dialog is non-dismissible (no cancel/dismiss button).
- Update check throttled to once per 24 hours per app launch.
- `ForceUpgradeRequired` renders a dialog that cannot be dismissed.

**Rollback Strategy:** Remove `UpdateCheckManager` call from `MainActivity.onStart()`. No data impact.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Straightforward version comparison logic and simple HTTP fetch.

**Context Strategy**

- Start new chat? No
- Required files: `MainActivity.kt`, `AlertBanner.kt`
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §5 (In-App Update Notification)
- Documents NOT required: All others

---

### T-081 — Release Readiness: Pre-Release Checklist and Signed APK

**Phase:** 7 — Integration Tests, Performance, and Release
**Subsystem:** All

**Description:**
Execute the 20-item pre-release checklist from `10_DEPLOYMENT_WORKFLOW.md §4.1`, produce the signed release APK, verify its signature, compute and attach the SHA-256 hash, and create the GitHub Release draft.

**Scope Boundaries**

- Files affected: None (verification and release artifact production only)
- Modules affected: All (full build)

**Implementation Steps**

1. Complete all 20 pre-release checklist items:
   - All unit tests pass: `./gradlew testReleaseUnitTest`.
   - All E2E instrumented tests pass on API 26 device.
   - All 6 performance baselines met.
   - `./gradlew ktlintCheck detekt` — zero violations.
   - Version code incremented from last release; version name follows SemVer.
   - `CHANGELOG.md` updated with new version section.
   - All `FIXME` and `TODO` comments reviewed — none are release-blocking.
   - `secrets.properties` not committed to the repository.
   - Release build type uses R8 full mode.
   - SQLCipher active in release build (confirmed by database file inspection).
   - Certificate pinning active (confirmed by intercepting a request with an invalid certificate).
   - `FLAG_SECURE` active (confirmed by task-switcher screenshot attempt).
   - `android:allowBackup="false"` in manifest.
   - No plaintext credentials in any log output.
   - Update manifest JSON updated at the stable URL.
   - Release APK signed with production keystore.
   - `apksigner verify --print-certs` shows production certificate SHA-256.
   - SHA-256 of the APK computed and stored in `kitewatch_v{version}.apk.sha256`.
   - GitHub Release draft created with APK and SHA-256 attachment.
   - At least 2 hours of smoke testing on a physical device with real Zerodha account.
2. Push the version tag `v{major}.{minor}.{patch}` to `main` — triggers `ci-release.yml` workflow.
3. Verify CI produces the draft release with APK and SHA-256.
4. Publish the GitHub Release draft.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- All items in the checklist serve as the test plan.

**Acceptance Criteria**

- All 20 pre-release checklist items checked off.
- `apksigner verify` confirms release keystore signature.
- SHA-256 file is correct (verify by independently hashing the APK).
- GitHub Release published with both attachments.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Checklist execution and release artifact production.

**Context Strategy**

- Start new chat? No
- Required files: `10_DEPLOYMENT_WORKFLOW.md` §4.1 (pre-release checklist)
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §4 (Release Process)
- Documents NOT required: Domain engine, database, UI documents

---

### T-082 — Final Project Validation Milestone

**Phase:** 7 — Integration Tests, Performance, and Release
**Subsystem:** All

**Description:**
Execute the complete system-level acceptance test against every product requirement in `00_PRODUCT_SPECIFICATION.md §6` (functional requirements). This is the terminal milestone — a signed-off pass here means KiteWatch is production-ready for Phase 1 MVP release.

**Implementation Steps**

1. Install the production-signed release APK on a physical device via sideload.
2. Complete the full onboarding flow.
3. Execute the 10-step smoke test protocol from `10_DEPLOYMENT_WORKFLOW.md §4.2`.
4. Verify every functional requirement in `00_PRODUCT_SPECIFICATION.md §6` against the production APK:
   - FR-01: OAuth authentication with real Zerodha account.
   - FR-02: Order sync fetches and stores today's equity delivery orders.
   - FR-03: Holdings computed correctly from order history (verify against Kite app).
   - FR-04: GTT created in Zerodha for a new holding (verify in Kite app).
   - FR-05: P&L calculated correctly (spot-check 5 trades against manually computed values).
   - FR-06: Biometric lock engages after 5-minute background.
   - FR-07: CSV import works with a real Zerodha tradebook export.
   - FR-08: Backup to Google Drive succeeds; restore from Drive succeeds.
   - FR-09: Fund balance entry persists and appears in Portfolio.
   - FR-10: Excel export opens correctly in a spreadsheet application.
5. Confirm no `P0` or `P1` bugs open.
6. Merge all feature branches to `main`. Tag `v1.0.0`. Publish GitHub Release.

**Acceptance Criteria**

- All 10 functional requirements verified against the production APK.
- Zero `P0` (crash / data loss) bugs.
- Production release APK published on GitHub Releases.
- SHA-256 hash published alongside the APK.
- All team members (if any) have reviewed the release checklist.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Verification execution against a fixed checklist.

**Context Strategy**

- Start new chat? Yes (final verification pass — fresh context)
- Required files: None
- Architecture docs to reference: `00_PRODUCT_SPECIFICATION.md` §6 (functional requirements), `10_DEPLOYMENT_WORKFLOW.md` §4.2 (smoke test protocol)
- Documents NOT required: All

---

## Summary: Complete Task Index {#task-index}

| Task | Title | Phase | Subsystem | Model | Complexity |
|---|---|---|---|---|---|
| T-001 | Repository Initialization | 0 | Version Control | Sonnet Fast | XS |
| T-002 | Gradle Multi-Module Scaffold | 0 | Build System | Sonnet Fast | M |
| T-003 | Convention Plugins and Build Variants | 0 | Build System | Sonnet Fast | M |
| T-004 | Secrets Management and R8 Validation | 0 | Build System | Sonnet Fast | S |
| T-005 | Ktlint and Detekt | 0 | Developer Tooling | Sonnet Fast | S |
| T-006 | GitHub Actions CI/CD | 0 | CI/CD | Sonnet Fast | M |
| T-007 | Application Class, Hilt, Timber | 0 | App Infrastructure | Sonnet Fast | S |
| T-008 | NavHost, Theme, DataStore | 0 | Presentation | Sonnet Fast | M |
| T-009 | Design System Component Stubs | 0 | Design System | Sonnet Fast | M |
| T-010 | Phase 0 Validation | 0 | All | Sonnet Fast | S |
| T-011 | Paisa, ProfitTarget, AppError | 1 | core-domain | **Opus Thinking** | S |
| T-012 | Domain Entity Models | 1 | core-domain | Sonnet Fast | M |
| T-013 | Repository Interfaces | 1 | core-domain | Sonnet Fast | S |
| T-014 | Room Entities: Orders, Holdings, Junction | 1 | core-database | Sonnet Thinking | S |
| T-015 | Room Entities: Transactions, FundEntries, GTT | 1 | core-database | Sonnet Fast | S |
| T-016 | Room Entities: Supporting Tables | 1 | core-database | Sonnet Fast | M |
| T-017 | TypeConverters, AppDatabase, Schema Export | 1 | core-database | Sonnet Fast | S |
| T-018 | DAOs: Orders, Holdings, Junction | 1 | core-database | Sonnet Thinking | M |
| T-019 | DAOs: Transactions, FundEntries, GTT | 1 | core-database | Sonnet Fast | M |
| T-020 | DAOs: Supporting Tables | 1 | core-database | Sonnet Fast | M |
| T-021 | DatabaseModule and MigrationTestHelper | 1 | core-database | Sonnet Fast | S |
| T-022 | Phase 1 Validation | 1 | All | Sonnet Fast | S |
| T-023 | ChargeCalculator: Implementation | 2 | core-domain | **Opus Thinking** | M |
| T-024 | ChargeCalculator: Fixture Tests | 2 | core-domain | Sonnet Thinking | S |
| T-025 | FIFO Lot Matching Algorithm | 2 | core-domain | **Opus Thinking** | M |
| T-026 | FIFO Lot Matcher: Edge Case Tests | 2 | core-domain | Sonnet Thinking | S |
| T-027 | Holdings Computation Engine | 2 | core-domain | **Opus Thinking** | M |
| T-028 | P&L Calculation Engine | 2 | core-domain | **Opus Thinking** | M |
| T-029 | P&L Calculation Engine: Fixture Tests | 2 | core-domain | Sonnet Thinking | M |
| T-030 | Target Price Calculator | 2 | core-domain | Sonnet Thinking | S |
| T-031 | GTT Automation Engine | 2 | core-domain | Sonnet Thinking | M |
| T-032 | GTT Automation Engine: State Tests | 2 | core-domain | Sonnet Fast | S |
| T-033 | SyncOrdersUseCase | 2 | core-domain | **Opus Thinking** | L |
| T-034 | HoldingsVerifier and WeekdayGuard | 2 | core-domain | Sonnet Fast | S |
| T-035 | Phase 2 Validation | 2 | All | Sonnet Fast | S |
| T-036 | Kite Connect DTOs and Retrofit Interface | 3 | core-network | Sonnet Fast | M |
| T-037 | OkHttp Client, Interceptors, Retrofit Wiring | 3 | core-network | Sonnet Thinking | M |
| T-038 | EncryptedSharedPreferences and CredentialStore | 3 | infra-auth | Sonnet Thinking | S |
| T-039 | OAuth Flow and Session Management | 3 | feature-onboarding | Sonnet Thinking | L |
| T-040 | Entity-to-Domain Mappers | 3 | core-data | Sonnet Fast | M |
| T-041 | Repository Impls: Local Persistence | 3 | core-data | Sonnet Fast | M |
| T-042 | Repository Impls: Remote and Supporting | 3 | core-data | Sonnet Fast | M |
| T-043 | Biometric App Lock | 3 | infra-auth | Sonnet Thinking | M |
| T-044 | PlaceGttUseCase | 3 | core-domain | Sonnet Thinking | M |
| T-045 | OrderSyncWorker: WorkManager Integration | 3 | infra-worker | Sonnet Thinking | M |
| T-046 | AddFundEntryUseCase and GetFundBalanceUseCase | 3 | core-domain | Sonnet Fast | S |
| T-047 | CalculatePnlUseCase and GetHoldingsUseCase | 3 | core-domain | Sonnet Fast | S |
| T-048 | Phase 3 Validation | 3 | All | Sonnet Fast | S |
| T-049 | Core UI Formatters and Charts | 4 | core-ui | Sonnet Fast | L |
| T-050 | Additional Core UI Components | 4 | core-ui | Sonnet Fast | M |
| T-051 | Portfolio Screen: ViewModel and State | 4 | feature-portfolio | Sonnet Fast | M |
| T-052 | Portfolio Screen: Composable | 4 | feature-portfolio | Sonnet Fast | M |
| T-053 | Holdings Screen: ViewModel and Screen | 4 | feature-holdings | Sonnet Fast | M |
| T-054 | Orders Screen: ViewModel and Paging 3 | 4 | feature-orders | Sonnet Fast | M |
| T-055 | Transactions Screen: ViewModel and Paging 3 | 4 | feature-transactions | Sonnet Fast | M |
| T-056 | GTT Screen: ViewModel and Screen | 4 | feature-gtt | Sonnet Fast | S |
| T-057 | Settings Screen and Fund Balance Entry | 4 | feature-settings | Sonnet Fast | M |
| T-058 | Onboarding Flow: Full Screen Implementation | 4 | feature-onboarding | Sonnet Fast | M |
| T-059 | Full Navigation Wiring and App Launch Logic | 4 | app | Sonnet Fast | M |
| T-060 | Phase 4 Validation | 4 | All | Sonnet Fast | S |
| T-061 | Protobuf Schema and Code Generation | 5 | infra-backup | Sonnet Fast | S |
| T-062 | Backup Serialization: Entity-to-Proto Mappers | 5 | infra-backup | Sonnet Fast | M |
| T-063 | CreateBackupUseCase | 5 | infra-backup | Sonnet Thinking | M |
| T-064 | Google Drive API Integration | 5 | core-network | Sonnet Thinking | M |
| T-065 | RestoreBackupUseCase | 5 | infra-backup | **Opus Thinking** | L |
| T-066 | Backup and Restore UI | 5 | feature-settings | Sonnet Fast | M |
| T-067 | CSV Parser and Validator | 5 | infra-csv | Sonnet Thinking | M |
| T-068 | ImportCsvUseCase | 5 | core-domain | Sonnet Thinking | M |
| T-069 | CSV Import UI | 5 | feature-settings | Sonnet Fast | S |
| T-070 | Excel Export | 5 | infra-csv | Sonnet Fast | M |
| T-071 | Gmail Fund Detection: API and Parser | 5 | core-network | Sonnet Thinking | M |
| T-072 | ScanGmailUseCase and Pending Detection UI | 5 | core-domain | Sonnet Fast | M |
| T-073 | Phase 5 Validation | 5 | All | Sonnet Fast | S |
| T-074 | SQLCipher Encryption Integration | 6 | core-database | Sonnet Thinking | M |
| T-075 | FLAG_SECURE, Log Scrubbing, Screen Security | 6 | app | Sonnet Fast | S |
| T-076 | Certificate Pinning | 6 | core-network | Sonnet Fast | S |
| T-077 | Security Audit and Phase 6 Validation | 6 | All | Sonnet Fast | M |
| T-078 | End-to-End Integration Test Suite | 7 | app | Sonnet Thinking | L |
| T-079 | Performance Profiling and Baseline Verification | 7 | All | Sonnet Fast | M |
| T-080 | In-App Update Notification | 7 | core-network | Sonnet Fast | S |
| T-081 | Release Readiness: Pre-Release Checklist | 7 | All | Sonnet Fast | M |
| T-082 | Final Project Validation Milestone | 7 | All | Sonnet Fast | M |

---

**Total Tasks:** 82  
**Opus Thinking assignments:** T-011, T-023, T-025, T-027, T-028, T-033, T-065 (7 tasks — financially and architecturally critical paths only)  
**Sonnet Thinking assignments:** T-014, T-018, T-024, T-026, T-029, T-030, T-031, T-037, T-038, T-039, T-043, T-044, T-045, T-063, T-064, T-067, T-068, T-071, T-074, T-078 (20 tasks)  
**Sonnet Fast assignments:** All remaining tasks (55 tasks)

---
