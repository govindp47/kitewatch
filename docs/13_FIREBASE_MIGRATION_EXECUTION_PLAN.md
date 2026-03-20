# Firebase Migration Execution Plan — Part 1 of 3

**Product:** KiteWatch — Android Local-First Portfolio Management
**Migration:** Secure Backend Layer via Firebase (Auth + Cloud Functions + Firestore)
**Version:** 1.0
**Last Updated:** 2026-03-18
**Status:** Authoritative migration task roadmap
**Scope:** Remove all secrets from APK, proxy all Kite API calls through Firebase Cloud Functions
**Part Coverage:** Sections 1–6 (Overview through Phase 1 tasks FM-001–FM-009)

---

## Table of Contents

1. [Migration Overview](#1-migration-overview)
2. [Architecture Changes](#2-architecture-changes)
3. [Security Corrections](#3-security-corrections)
4. [Migration Strategy](#4-migration-strategy)
5. [Migration Phases Overview](#5-migration-phases-overview)
6. [Detailed Tasks — Phase 0: Firebase Project Setup](#phase-0-tasks)
7. [Detailed Tasks — Phase 1: Cloud Functions Foundation](#phase-1-tasks)

---

## 1. Migration Overview

### 1.1 Current Architecture (As-Is)

The KiteWatch Android application communicates directly with the Kite Connect REST API (`api.kite.trade`) from the device. The OAuth token-exchange flow — which requires the developer's `KITE_API_SECRET` — executes on the device. The resulting `access_token` is stored in `EncryptedSharedPreferences` on the device. The `KITE_API_KEY` is baked into the APK as a `BuildConfig` field.

```
┌─────────────────────────────────────────┐
│              Android App                │
│                                         │
│  BuildConfig.KITE_API_KEY  (in APK)     │
│  KITE_API_SECRET   (entered by user,    │
│                     stored on device)   │
│  access_token      (EncryptedSharedPrefs│
│                     on device)          │
│                                         │
│  KiteConnectApiService  (Retrofit)      │
│  KiteConnectAuthInterceptor             │
│  BindAccountUseCase  (SHA-256 on device)│
│  OrderSyncWorker    (direct API calls)  │
└──────────────────┬──────────────────────┘
                   │ HTTPS direct
                   ▼
        ┌──────────────────┐
        │  Kite Connect    │
        │  api.kite.trade  │
        └──────────────────┘
```

**Key API endpoints called directly from device:**

| Endpoint | Method | Called From |
|---|---|---|
| `/session/token` | POST | `BindAccountUseCase` |
| `/orders` | GET | `KiteConnectRepositoryImpl` via `SyncOrdersUseCase` |
| `/portfolio/holdings` | GET | `KiteConnectRepositoryImpl` |
| `/user/margins` | GET | `KiteConnectRepositoryImpl` |
| `/gtt` | GET | `KiteConnectRepositoryImpl` |
| `/gtt` | POST | `KiteConnectRepositoryImpl` |
| `/gtt/{id}` | PUT | `KiteConnectRepositoryImpl` |
| `/gtt/{id}` | DELETE | `KiteConnectRepositoryImpl` |

### 1.2 Target Architecture (To-Be)

All Kite Connect API calls are removed from the Android app. A Firebase backend layer is introduced as the exclusive intermediary. The device never holds the `KITE_API_SECRET` or the Kite `access_token`. The app communicates only with Firebase services.

```
┌──────────────────────────────────────────┐
│              Android App                 │
│                                          │
│  Firebase Auth SDK  (identity)           │
│  Firebase ID token  (in-memory only)     │
│  KITE_API_KEY       (BuildConfig, public)│
│  FirebaseProxyApiService  (callable fns) │
│  FirebaseIdTokenInterceptor              │
│  No Kite tokens on device                │
└───────────┬──────────────────────────────┘
            │ Firebase SDK (HTTPS)
            ▼
┌───────────────────────────────────────────────────┐
│              Firebase Platform                    │
│                                                   │
│  Firebase Auth          (user identity)           │
│  Cloud Functions        (Kite API proxy)          │
│    - exchangeKiteToken  (OAuth exchange)          │
│    - getOrders / getHoldings / getFundBalance     │
│    - getGttOrders / createGtt / updateGtt / ...   │
│    - storeApiKey / invalidateSession              │
│  Firestore              (token storage)           │
│    users/{uid}/session/accessToken  (encrypted)  │
│    users/{uid}/profile/apiKey                    │
│  Secret Manager         (KITE_API_SECRET)         │
└───────────────────────────────┬───────────────────┘
                                │ HTTPS (server-side)
                                ▼
                     ┌──────────────────┐
                     │  Kite Connect    │
                     │  api.kite.trade  │
                     └──────────────────┘
```

### 1.3 Risks in the Current System

| Risk | Severity | Location |
|---|---|---|
| `KITE_API_SECRET` accessible to any user who decompiles the APK (via BuildConfig or EncryptedSharedPreferences extraction on rooted device) | **Critical** | `app/build.gradle.kts`, `infra/auth/CredentialStore.kt` |
| `KITE_API_KEY` baked into APK binary as `BuildConfig` field | **High** | `app/build.gradle.kts` |
| Kite `access_token` stored on device; extractable via ADB backup on rooted device | **High** | `infra/auth/CredentialStore.kt` |
| SHA-256 checksum for token exchange computed on device (requires `apiSecret` to be present) | **Critical** | `domain/usecase/auth/BindAccountUseCase.kt` |
| All 8 Kite API endpoints called directly from device; request interception possible if certificate pins are bypassed | **High** | `core/network/kiteconnect/KiteConnectApiService.kt` |
| User prompted to enter `KITE_API_SECRET` during onboarding, exposing it to clipboard and screenshot | **High** | `feature/onboarding` |
| `OrderSyncWorker` carries Kite credentials as implicit state; background execution without active auth review | **Medium** | `infra/worker/OrderSyncWorker.kt` |

### 1.4 Benefits of Migration

- `KITE_API_SECRET` is permanently removed from the device and the APK; it lives only in Firebase Secret Manager.
- Kite `access_token` is never transmitted to or stored on the device.
- All Kite API calls route through Firebase Cloud Functions, which are controlled server-side and auditable.
- Firebase Auth provides a proper identity layer — all Cloud Function calls require a valid Firebase ID token.
- Token expiry and re-auth logic centralised in the backend; device only reacts to Firebase Auth state.
- Per-user data isolation enforced by Firestore Security Rules without relying on device encryption alone.
- Background sync (`OrderSyncWorker`) continues to work using Firebase Auth's background token refresh.

---

## 2. Architecture Changes

### 2.1 Removal of Direct Kite API Calls

The `KiteConnectApiService` Retrofit interface (`core/network/kiteconnect/KiteConnectApiService.kt`) is deleted in full. All eight Kite API endpoints it defines are replaced by corresponding Firebase Callable Cloud Functions. The `KiteConnectAuthInterceptor`, `TokenExpiredInterceptor`, and `KiteConnectRateLimitInterceptor` are removed. The `NetworkModule` Kite-specific `OkHttpClient` and `Retrofit` instance is removed. Certificate pinning for `api.kite.trade` on the Android side is removed (server-side pinning is handled in Cloud Functions).

### 2.2 Introduction of Firebase Layers

Three Firebase services are introduced:

**Firebase Auth** — Provides the user's Firebase UID and signed ID tokens. Google Sign-In is configured as the identity provider. The app must be authenticated with Firebase before calling any Cloud Function. The Firebase Auth SDK handles token refresh transparently.

**Firebase Cloud Functions** — A Node.js/TypeScript project in `functions/` at the repository root. All functions are HTTPS Callable functions, which automatically validate the caller's Firebase ID token. Functions are grouped as: `auth/` (OAuth exchange, session management), `proxy/` (Kite API pass-through), and `middleware/` (shared auth validation). The `KITE_API_SECRET` is injected at runtime via Secret Manager; it is never in source code or environment variable files committed to the repository.

**Firestore** — Stores per-user state: the Kite `access_token` (in `users/{uid}/session`), the user's `apiKey` (in `users/{uid}/profile`), and binding metadata. Firestore Security Rules enforce that each user can only read and write their own subtree.

### 2.3 Auth Flow Redesign

**Current flow:**

```
App → Kite login page → deep link → App computes SHA-256 (needs apiSecret on device)
    → App calls POST /session/token → App stores access_token in EncryptedSharedPrefs
```

**New flow:**

```
App → Firebase Auth (Google Sign-In) → Firebase UID established
    → App opens Kite login page (needs only KITE_API_KEY, which is public)
    → Kite redirects to app via existing deep link → App gets request_token
    → App calls Cloud Function exchangeKiteToken(request_token)
        → Function verifies Firebase ID token
        → Function fetches apiKey from Firestore
        → Function fetches apiSecret from Secret Manager
        → Function computes SHA-256 checksum (server-side)
        → Function calls POST /session/token on Kite API
        → Function stores access_token in Firestore users/{uid}/session
        → Function returns { success: true } — access_token NOT returned to app
    → App updates binding state in local DB
```

### 2.4 Token Lifecycle Changes

| Concern | Current | Target |
|---|---|---|
| `KITE_API_SECRET` location | User enters during onboarding; stored in `EncryptedSharedPreferences` | Firebase Secret Manager only; never leaves Cloud Functions runtime |
| `KITE_API_KEY` location | `BuildConfig` in APK (injected from `secrets.properties`) | `BuildConfig` retained (public identifier; needed to construct Kite OAuth URL) |
| `access_token` location | `EncryptedSharedPreferences` on device (`kitewatch_secrets` → `kite_access_token`) | Firestore `users/{uid}/session/accessToken` only |
| Token injection into API requests | `KiteConnectAuthInterceptor` reads from device storage | Cloud Functions read from Firestore; device never sees token |
| Token expiry handling | `TokenExpiredInterceptor` detects 403 → `SessionExpiredEvent` → app shows re-auth | Cloud Function detects 403 from Kite → writes `tokenExpired: true` to Firestore → app observes and shows re-auth |
| Background sync auth | Worker reads `access_token` from `EncryptedSharedPreferences` | Worker obtains Firebase ID token from `FirebaseAuth.currentUser.getIdToken()` |

---

## 3. Security Corrections

### 3.1 Critical: `KITE_API_SECRET` Exposure

**Current state:** The `secrets.properties` file defines `KITE_API_SECRET`. `BindAccountUseCase` receives `apiSecret: String` as a parameter. During onboarding, the user types their API secret into a text field, which the app reads and passes to `BindAccountUseCase`. The use case computes `SHA256(apiKey + requestToken + apiSecret)` on device. Whether or not the secret is currently in `BuildConfig`, the secret is entered on device, processed on device, and after the token exchange, is no longer needed — but the pathway for it existing on device is present and the Firestore backup for `bound_api_key` in `AccountBindingStore` further persists the API key.

**Correction:** Remove the `apiSecret` parameter from `BindAccountUseCase` entirely. Remove the `apiSecret` text input field from the onboarding screen. Move the SHA-256 computation to the `exchangeKiteToken` Cloud Function. Store `KITE_API_SECRET` in Firebase Secret Manager, accessible only to Cloud Functions at runtime.

**Files requiring change:**

- `domain/usecase/auth/BindAccountUseCase.kt` — remove `apiSecret` parameter
- `infra/auth/CredentialStore.kt` — remove `saveApiSecret()` if present; remove `clearApiSecret()` call chain
- `feature/onboarding` — remove API secret input field
- `secrets.properties.template` — remove `KITE_API_SECRET` entry

### 3.2 High: Kite `access_token` on Device

**Current state:** After `BindAccountUseCase` calls `/session/token`, the returned `access_token` is stored via `CredentialStore.saveAccessToken()` into `EncryptedSharedPreferences` (`kitewatch_secrets`, key `kite_access_token`). On a rooted device or with ADB backup, this file can be extracted and the token used to impersonate the user against the Kite API.

**Correction:** The `exchangeKiteToken` Cloud Function stores the `access_token` in Firestore and returns only `{ success: true }` to the app. The `CredentialStore.saveAccessToken()` method is removed. The `kite_access_token` key is cleared from `EncryptedSharedPreferences` during the migration cleanup task. `KiteConnectAuthInterceptor` is deleted.

### 3.3 High: Direct Kite API Calls from Device

**Current state:** `KiteConnectApiService` makes 8 direct HTTPS calls to `api.kite.trade` from the device. Even with certificate pinning, a sufficiently privileged attacker (Frida hooks, root) can bypass the pin and intercept plaintext Kite API requests including the `access_token` in the `Authorization` header.

**Correction:** `KiteConnectApiService` is deleted. All 8 endpoints become Cloud Functions callable from the app. The `Authorization: token apiKey:accessToken` header is assembled server-side within Cloud Functions and is never present in any device-to-network traffic.

### 3.4 Medium: `KITE_API_KEY` in `BuildConfig`

**Current state:** `KITE_API_KEY` is injected as `BuildConfig.KITE_API_KEY` from `secrets.properties`. While the API key is a public identifier (required to construct the Kite OAuth URL and not considered a secret by Kite), its presence in `BuildConfig` means it is trivially extractable from the APK.

**Correction (acceptable risk):** `KITE_API_KEY` is retained in `BuildConfig` — it is a public OAuth client identifier equivalent to an OAuth2 `client_id`. Its exposure does not allow API calls to succeed (the `access_token` is now server-side). Document this explicitly in the migration. Remove `KITE_API_SECRET` from `secrets.properties.template` so no future developer mistakenly injects it into `BuildConfig`.

### 3.5 Medium: Onboarding Exposes Secret via UI

**Current state:** The onboarding flow prompts the user to enter `KITE_API_SECRET` into a text field. This input is visible to any app with accessibility service access or screenshot capability during onboarding.

**Correction:** Remove the `KITE_API_SECRET` input field from the onboarding UI entirely. Onboarding now only requires: Firebase Auth (Google Sign-In) and the Kite OAuth web flow (browser-based, no secret typed). The `apiKey` input field may remain if the developer wants to make the app configurable for multiple Kite developer accounts.

---

## 4. Migration Strategy

### 4.1 Incremental Migration Phases

The migration is structured so that after each phase, the app remains in a compilable and launchable state. Phases 0–2 are additive (Firebase is introduced without removing existing Kite code). Phase 3 switches the data path. Phases 4–5 remove the now-unused device-side secret infrastructure. Phases 6–7 harden and validate.

### 4.2 Backward Compatibility

There is no external API contract to maintain — the app is sideloaded and single-user. Backward compatibility requirements are:

- The local Room database schema must not change during migration (no new entities; this is an infrastructure change only).
- The domain engine (charge calculation, FIFO, P&L, GTT automation) is completely unaffected.
- All feature screens (Portfolio, Holdings, Orders, GTT, Transactions, Settings) are unaffected except for the removal of the API secret input from onboarding.
- The `BindAccountUseCase` interface changes (removes `apiSecret` parameter) but since this is an internal call site, it is a coordinated change across caller and callee in the same PR.

### 4.3 Safe Rollout Plan

```
Phase 0 — Firebase scaffold:  additive only, zero app behaviour change
Phase 1 — Cloud Functions:    deployed and testable in Firebase console, not yet called by app
Phase 2 — OAuth migration:    new Kite login path added; old path gated by feature flag FEATURE_FIREBASE_AUTH
Phase 3 — API proxy:          new proxy path added; old Retrofit path gated by FEATURE_FIREBASE_PROXY
Phase 4 — App refactor:       old code paths removed once new paths are validated on staging
Phase 5 — Token cleanup:      access_token removed from device; no fallback (Phase 3 must be stable)
Phase 6 — Security hardening: no behaviour changes, hardening only
Phase 7 — Cleanup/validation: deletion of dead code, full test suite, release gate
```

### 4.4 Feature Flags

Two `BuildConfig` flags gate the migration during Phases 2–3:

- `FEATURE_FIREBASE_AUTH` — controls whether onboarding uses the new Firebase-first OAuth path.
- `FEATURE_FIREBASE_PROXY` — controls whether `KiteConnectRepositoryImpl` routes through Firebase Cloud Functions or directly to Kite.

Both default to `false` in `debug` builds during development, `true` in `staging` builds once the Cloud Functions are deployed and validated.

### 4.5 Testing Checkpoints

| After Phase | Checkpoint |
|---|---|
| Phase 0 | `./gradlew assembleDebug` passes; Firebase SDK linked; google-services.json present |
| Phase 1 | Cloud Functions deploy successfully; `kiteOAuthCallback` testable via Firebase console |
| Phase 2 | End-to-end Kite OAuth completes with `FEATURE_FIREBASE_AUTH=true` on physical device |
| Phase 3 | `getOrders` Cloud Function returns real data; `OrderSyncWorker` completes via proxy |
| Phase 4 | `KITE_API_SECRET` is absent from all APK builds (verified with `apktool`) |
| Phase 5 | `kite_access_token` key absent from `EncryptedSharedPreferences` dump on rooted device |
| Phase 6 | Firestore Security Rules reject cross-user reads in automated tests |
| Phase 7 | Full CI pipeline passes; APK passes security audit |

---

## 5. Migration Phases Overview

| Phase | Title | Tasks | Duration Estimate | Risk |
|---|---|---|---|---|
| 0 | Firebase Project Setup | FM-001–FM-004 | ~3 days | Low |
| 1 | Cloud Functions Foundation | FM-005–FM-009 | ~4 days | Medium |
| 2 | OAuth Flow Migration | FM-010–FM-013 | ~3 days | High |
| 3 | API Proxy Implementation | FM-014–FM-019 | ~4 days | High |
| 4 | Android App Refactor | FM-020–FM-024 | ~3 days | Medium |
| 5 | Token Migration & Cleanup | FM-025–FM-028 | ~2 days | Medium |
| 6 | Security Hardening | FM-029–FM-033 | ~2 days | High |
| 7 | Cleanup & Validation | FM-034–FM-038 | ~3 days | Low |

**Total estimated duration:** 4–5 weeks (1 senior Android engineer + Cloud Functions familiarity)

---

## 6. Detailed Tasks — Phase 0: Firebase Project Setup {#phase-0-tasks}

**Objective:** Provision the Firebase project, add Firebase SDKs to the Android build, configure Firebase Auth with Google Sign-In, and integrate `google-services.json` into the CI pipeline. Zero product features changed. Zero Kite API call paths altered.

**Duration:** ~3 days
**Risk Level:** Low — additive infrastructure only; failure is immediately visible
**Depends On:** Nothing (can begin immediately)

**Completion Criteria:**

- Firebase project created and linked to Android `applicationId`.
- `google-services.json` present in `app/` and in `.gitignore`.
- `./gradlew assembleDebug` passes with Firebase SDK on the classpath.
- Firebase Auth initialises without crash on physical device.
- Google Sign-In completes end-to-end and returns a non-null `FirebaseUser`.
- CI pipeline correctly injects `google-services.json` from a base64-encoded repository secret.

---

### FM-001 — Firebase Project Creation and google-services.json Integration

**Phase:** 0 — Firebase Project Setup
**Subsystem:** Firebase / Build System

**Description:**
Create the Firebase project in the Firebase console, register the Android application using its `applicationId`, download `google-services.json`, and integrate it into the Gradle build. Configure `.gitignore` to exclude `google-services.json` from version control. Establish a CI secret for injecting the file in GitHub Actions.

**Scope Boundaries**

- Files affected: `app/google-services.json` (generated, gitignored), `.gitignore`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/` (new `AndroidFirebaseConventionPlugin` stub or direct `app` config), `.github/workflows/ci-pr.yml`, `.github/workflows/ci-staging.yml`, `.github/workflows/ci-release.yml`
- Modules affected: `:app`
- Explicitly NOT touching: Any Kite API code, domain logic, database schema, feature modules

**Implementation Steps**

1. In `gradle/libs.versions.toml`, add version entries: `firebase-bom = "33.x.x"` (latest stable), `google-services = "4.4.x"` (latest stable).
2. Add library aliases in `[libraries]`: `firebase-bom`, `firebase-auth-ktx`, `firebase-firestore-ktx`, `firebase-functions-ktx`.
3. Add plugin alias in `[plugins]`: `google-services`.
4. In root `build.gradle.kts`, add `google-services` to the `plugins` block with `apply false`.
5. In `app/build.gradle.kts`, apply `alias(libs.plugins.google.services)` and add `implementation(platform(libs.firebase.bom))`, `implementation(libs.firebase.auth.ktx)`, `implementation(libs.firebase.firestore.ktx)`, `implementation(libs.firebase.functions.ktx)` to `dependencies {}`.
6. Add `google-services.json` to `.gitignore` (it already lists it — confirm the entry is present from T-001).
7. Update all three GitHub Actions workflows: add a step before `assemble*` tasks that writes `GOOGLE_SERVICES_JSON` from a base64-encoded repository secret to `app/google-services.json` using `echo "${{ secrets.GOOGLE_SERVICES_JSON }}" | base64 --decode > app/google-services.json`.
8. Run `./gradlew assembleDebug` — confirm success with Firebase on classpath.

**User Action Steps (Manual Execution)**

1. Navigate to [https://console.firebase.google.com](https://console.firebase.google.com) → **Create a project** → name it `kitewatch` → disable Google Analytics (not required).
2. In the project overview, click **Add app** → select **Android** → enter `applicationId` = `com.kitewatch.app` → enter app nickname `KiteWatch Android` → skip SHA certificate for now (add in FM-002).
3. Download `google-services.json` → place at `app/google-services.json` in the repository.
4. In GitHub repository Settings → Secrets → add secret `GOOGLE_SERVICES_JSON` with the base64 value of `google-services.json`: run `base64 -i app/google-services.json | pbcopy` and paste.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: `./gradlew assembleDebug` succeeds. Install debug APK on device — no crash on launch. `FirebaseApp.initializeApp()` is called implicitly by the `google-services` plugin and does not throw.

**Acceptance Criteria**

- `./gradlew assembleDebug` passes with Firebase dependencies on classpath.
- `google-services.json` is present locally but absent from `git status`.
- GitHub Actions `ci-pr.yml` passes on a test PR (with `GOOGLE_SERVICES_JSON` secret set).
- No crash on cold app launch on a physical device.

**Rollback Strategy:** Remove `firebase-*` dependencies and `google-services` plugin from Gradle files. Remove the CI secret injection step. App returns to its pre-Firebase state.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Gradle dependency addition and CI secret injection are well-established boilerplate.

**Context Strategy**

- Start new chat? No
- Required files: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.gitignore`, all three `.github/workflows/*.yml`
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §3.2 (CI workflow structure)
- Documents NOT required: Domain, schema, security documents

---

### FM-002 — Firebase Auth Configuration (Google Sign-In Provider)

**Phase:** 0 — Firebase Project Setup
**Subsystem:** Firebase Auth / `:infra-auth`

**Description:**
Enable Google Sign-In as the authentication provider in the Firebase console. Register the app's debug and release SHA-1 certificate fingerprints so Google Sign-In tokens are accepted. Implement `FirebaseAuthManager` — a thin wrapper in a new `:infra-firebase` module that signs the user in with Google and exposes the Firebase Auth state as a `StateFlow`. This class replaces nothing yet — it coexists with the existing `CredentialStore` until Phase 4.

**Scope Boundaries**

- Files affected: `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseAuthManager.kt` (new file), `infra/firebase/build.gradle.kts` (new module), `settings.gradle.kts` (add `:infra-firebase`)
- Modules affected: `:infra-firebase` (new), `:app` (adds `:infra-firebase` dependency)
- Explicitly NOT touching: Existing `BiometricAuthManager`, `CredentialStore`, `SessionManager`, any Kite API code, feature screens

**Implementation Steps**

1. Add `:infra-firebase` to `settings.gradle.kts`.
2. Create `infra/firebase/build.gradle.kts`: apply `AndroidLibraryConventionPlugin`, add `firebase-auth-ktx`, `hilt-android`, `coroutines` dependencies.
3. Implement `FirebaseAuthManager` in `:infra-firebase`:
   - `val authState: StateFlow<FirebaseUser?>` — cold-started from `FirebaseAuth.getInstance().currentUser`, updated via `addAuthStateListener`.
   - `suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser>` — calls `FirebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))`.
   - `suspend fun getIdToken(): Result<String>` — calls `currentUser?.getIdToken(false)?.await()`; returns `AppError.AuthError.TokenExpired` if `currentUser` is null.
   - `fun signOut()` — calls `FirebaseAuth.getInstance().signOut()`.
4. Create `FirebaseModule.kt` in `:infra-firebase` (`@Module @InstallIn(SingletonComponent::class)`): provides `FirebaseAuth` singleton and `FirebaseFirestore` singleton.
5. Add `:infra-firebase` as a dependency in `app/build.gradle.kts`.

**User Action Steps (Manual Execution)**

1. In Firebase console → **Authentication** → **Sign-in method** → enable **Google** provider → enter project's public-facing name → save.
2. In Firebase console → **Project Settings** → **Your apps** → Android app → add SHA-1 fingerprints:
   - Debug: run `./gradlew signingReport` → copy the `SHA1` from the `debug` variant.
   - Release: copy from `secrets.properties` keystore or `keytool -list -v -keystore keystore.jks`.
3. Re-download `google-services.json` after adding SHA-1 (it must include the fingerprints) → replace `app/google-services.json`.
4. Update `GOOGLE_SERVICES_JSON` GitHub secret with the new base64 value.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit test: `FirebaseAuthManager` — mock `FirebaseAuth`; verify `authState` emits `null` when `currentUser` is null; verify `getIdToken()` returns `AppError.AuthError.TokenExpired` when unauthenticated.
- Manual: Install app → trigger Google Sign-In flow → verify `FirebaseAuth.currentUser` is non-null after sign-in. Check Firebase console → Authentication tab → confirm user appears.

**Acceptance Criteria**

- `:infra-firebase` module compiles and is included in `assembleDebug`.
- `FirebaseAuthManager.signInWithGoogle()` completes on a physical device with a real Google account.
- `FirebaseAuth.currentUser` is non-null and has a valid `uid` post sign-in.
- `getIdToken()` returns a non-empty string token.
- Unit tests pass.

**Rollback Strategy:** Remove `:infra-firebase` from `settings.gradle.kts` and `app/build.gradle.kts`. Module is entirely additive; no existing code is touched.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Firebase Auth SDK integration is well-documented boilerplate; module scaffold follows established convention.

**Context Strategy**

- Start new chat? No
- Required files: `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `infra/auth/src/main/java/com/kitewatch/infra/auth/di/AuthInfraModule.kt`
- Architecture docs to reference: None required
- Documents NOT required: All

---

### FM-003 — Firebase Feature Flags in BuildConfig

**Phase:** 0 — Firebase Project Setup
**Subsystem:** Build System / `:app`

**Description:**
Introduce the two migration feature flags (`FEATURE_FIREBASE_AUTH` and `FEATURE_FIREBASE_PROXY`) as `BuildConfig` boolean fields. These flags gate all new Firebase code paths during Phases 2–3. Both are `false` in `debug` builds initially, `true` in `staging` builds once Cloud Functions are deployed, and `true` in `release` builds only after Phase 7 validation is complete.

**Scope Boundaries**

- Files affected: `app/build.gradle.kts`, `secrets.properties.template` (comment addition only)
- Modules affected: `:app`
- Explicitly NOT touching: Any source files, domain logic, infra modules

**Implementation Steps**

1. In `app/build.gradle.kts`, `buildTypes {}` block:
   - `debug`: add `buildConfigField("Boolean", "FEATURE_FIREBASE_AUTH", "false")` and `buildConfigField("Boolean", "FEATURE_FIREBASE_PROXY", "false")`.
   - `staging`: add both fields as `"false"` initially; these will be updated to `"true"` in FM-013 (after OAuth migration is validated).
   - `release`: add both fields as `"false"` initially; updated in FM-038 (after final validation).
2. Add a comment block in `secrets.properties.template` above the existing entries:

   ```
   # Firebase migration feature flags are controlled via build.gradle.kts buildTypes.
   # Do NOT add KITE_API_SECRET here after migration. It lives in Firebase Secret Manager only.
   ```

3. Run `./gradlew assembleDebug` — confirm `BuildConfig.FEATURE_FIREBASE_AUTH == false`.

**User Action Steps (Manual Execution)**

None — fully automated Gradle configuration.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: Inspect `app/build/generated/source/buildConfig/debug/com/kitewatch/app/BuildConfig.java` — confirm `FEATURE_FIREBASE_AUTH = false` and `FEATURE_FIREBASE_PROXY = false` in the debug file.

**Acceptance Criteria**

- `BuildConfig.FEATURE_FIREBASE_AUTH` and `BuildConfig.FEATURE_FIREBASE_PROXY` exist and are `false` in `debug` builds.
- `./gradlew assembleDebug assembleStagingRelease assembleRelease` all pass.

**Rollback Strategy:** Remove the two `buildConfigField` lines from all three build types.

**Estimated Complexity:** XS

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Single-file build configuration change.

**Context Strategy**

- Start new chat? No
- Required files: `app/build.gradle.kts`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-004 — Cloud Functions Project Scaffold (TypeScript/Node.js)

**Phase:** 0 — Firebase Project Setup
**Subsystem:** Cloud Functions / Repository Infrastructure

**Description:**
Initialise the Firebase Cloud Functions project within the existing repository under a `functions/` directory. Configure TypeScript compilation, ESLint, and the Firebase emulator suite. Create the directory structure for all planned functions. No function logic is implemented in this task — only the scaffold, build system, and deployment configuration.

**Scope Boundaries**

- Files affected: `functions/package.json`, `functions/tsconfig.json`, `functions/.eslintrc.js`, `functions/src/index.ts` (empty exports), `functions/src/auth/` (empty directory stubs), `functions/src/proxy/` (empty directory stubs), `functions/src/middleware/` (empty directory stubs), `functions/src/utils/` (empty directory stubs), `firebase.json`, `.firebaserc`, `.gitignore` (add `functions/node_modules`, `functions/lib`)
- Modules affected: None (Android modules unaffected)
- Explicitly NOT touching: Any Android source code, Gradle files, existing infra modules

**Implementation Steps**

1. At the repository root, create `firebase.json` specifying `functions.source = "functions"`, `functions.runtime = "nodejs20"`, and `emulators` block for `auth` (port 9099), `firestore` (port 8080), `functions` (port 5001).
2. Create `.firebaserc` with the Firebase project ID from FM-001.
3. Create `functions/package.json`: `main: "lib/index.js"`, `scripts: { build, serve, deploy, lint }`. Dependencies: `firebase-admin`, `firebase-functions`. Dev dependencies: `typescript`, `@types/node`, `eslint`, `ts-node`.
4. Create `functions/tsconfig.json`: `target: "ES2020"`, `module: "commonjs"`, `strict: true`, `outDir: "lib"`, `rootDir: "src"`.
5. Create `functions/src/index.ts`: empty file that will export all functions. Add a single no-op `export const healthCheck = ...` placeholder for deployment validation.
6. Create empty stub directories: `functions/src/auth/`, `functions/src/proxy/`, `functions/src/middleware/`, `functions/src/utils/`.
7. Add `functions/node_modules/`, `functions/lib/` to root `.gitignore`.
8. Add `functions/` build to `ci-staging.yml` workflow: step to `npm ci && npm run build` in `functions/` directory, run before Android build.
9. Run `cd functions && npm ci && npm run build` — confirm compilation succeeds on the empty scaffold.

**User Action Steps (Manual Execution)**

1. Install Firebase CLI if not present: `npm install -g firebase-tools`.
2. Run `firebase login` to authenticate the CLI with the Firebase project owner's Google account.
3. Run `firebase use kitewatch` (or project ID from FM-001) to link the local repo to the Firebase project.
4. Run `firebase deploy --only functions` to deploy the `healthCheck` placeholder — confirm it appears in the Firebase console under Functions.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: `cd functions && npm run build` exits with code 0. `firebase deploy --only functions` completes without error. `healthCheck` function visible in Firebase console.
- CI: `ci-staging.yml` `npm run build` step passes on a test push to `develop`.

**Acceptance Criteria**

- `functions/` directory committed with scaffold (no `node_modules/` or `lib/`).
- `npm run build` succeeds on empty scaffold.
- `firebase deploy --only functions` succeeds (deploys `healthCheck` placeholder).
- CI pipeline includes `functions/` build step.
- `.gitignore` excludes `functions/node_modules` and `functions/lib`.

**Rollback Strategy:** Delete `functions/` directory and revert `.gitignore`, `firebase.json`, `.firebaserc`. Firebase project retains no deployed functions.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Standard Firebase Functions TypeScript scaffold; well-documented CLI workflow.

**Context Strategy**

- Start new chat? Yes (new subsystem: Cloud Functions infrastructure)
- Required files: `.gitignore`, `.github/workflows/ci-staging.yml`
- Architecture docs to reference: None
- Documents NOT required: All

---

## 7. Detailed Tasks — Phase 1: Cloud Functions Foundation {#phase-1-tasks}

**Objective:** Implement the Firestore data model and Security Rules, migrate `KITE_API_SECRET` to Firebase Secret Manager, build the authentication middleware shared by all Cloud Functions, and deploy the `exchangeKiteToken` function (the most security-critical function). No Android code changes in this phase — the app still calls Kite directly. These functions are tested via the Firebase emulator and console only.

**Duration:** ~4 days
**Risk Level:** Medium — secret management and token exchange correctness are critical
**Depends On:** Phase 0 complete; Firebase project exists; Cloud Functions scaffold deployed

**Completion Criteria:**

- Firestore Security Rules deployed and rejection of cross-user reads verified via emulator.
- `KITE_API_SECRET` stored in Firebase Secret Manager; Cloud Functions runtime can access it.
- `exchangeKiteToken` function deployed and successfully exchanges a real Kite `request_token` for an `access_token` via the Firebase emulator or production endpoint.
- All Cloud Functions return `UNAUTHENTICATED` when called without a valid Firebase ID token.
- `storeApiKey` function stores the user's API key in Firestore under the correct UID path.

---

### FM-005 — Firestore Data Model Design and Security Rules

**Phase:** 1 — Cloud Functions Foundation
**Subsystem:** Firestore / Security

**Description:**
Define the Firestore document schema for per-user Kite session data and user profile. Write and deploy Firestore Security Rules that enforce strict per-user data isolation — no user can read or write another user's subtree. Define the schema as TypeScript type definitions in `functions/src/utils/firestoreSchema.ts` for use by all Cloud Functions.

**Scope Boundaries**

- Files affected: `firestore.rules` (new, at repository root), `firestore.indexes.json` (new), `functions/src/utils/firestoreSchema.ts` (new), `firebase.json` (add `firestore.rules` reference)
- Modules affected: None (Android modules unaffected)
- Explicitly NOT touching: Any Android source code, Kite API code, existing `AccountBindingStore`

**Implementation Steps**

1. Define Firestore document schema (TypeScript interfaces in `firestoreSchema.ts`):
   - `UserProfile`: `{ kiteUserId: string; kiteUserName: string; apiKey: string; boundAt: FirebaseFirestore.Timestamp; }`
   - `UserSession`: `{ accessToken: string; tokenObtainedAt: FirebaseFirestore.Timestamp; tokenExpired: boolean; lastSyncedAt: FirebaseFirestore.Timestamp | null; }`
   - `SyncState`: `{ lastSyncStartedAt: FirebaseFirestore.Timestamp | null; lastSyncStatus: 'IDLE' | 'RUNNING' | 'SUCCESS' | 'FAILED'; lastErrorMessage: string | null; }`
   - Collection paths: `users/{uid}/profile` (single document), `users/{uid}/session` (single document), `users/{uid}/syncState` (single document).
2. Write `firestore.rules`:
   - Default deny all: `match /{document=**} { allow read, write: if false; }`
   - User subtree: `match /users/{userId}/{document=**} { allow read, write: if request.auth != null && request.auth.uid == userId; }`
   - No Cloud Functions bypass — Cloud Functions use Admin SDK which bypasses rules. Client SDK access (for `tokenExpired` observation) is covered by the user rule.
3. Write `firestore.indexes.json`: empty indexes array (no composite indexes needed for single-document collections).
4. Add `"firestore": { "rules": "firestore.rules", "indexes": "firestore.indexes.json" }` to `firebase.json`.
5. Deploy via `firebase deploy --only firestore:rules`.
6. Test via Firebase emulator: attempt to read `users/uid-a/session` while authenticated as `uid-b` — confirm rejection with `PERMISSION_DENIED`.

**User Action Steps (Manual Execution)**

1. Run `firebase deploy --only firestore:rules,firestore:indexes` to deploy the rules to production Firestore.
2. In Firebase console → Firestore → Rules → verify the deployed rules match the written rules.

**Data Impact**

- Schema changes: Firestore schema established (not Room schema — Android Room database is unaffected)
- Migration required: No

**Test Plan**

- Emulator test: start `firebase emulators:start --only firestore`. Write a test script that: (a) reads `users/uid-a/session` while authenticated as `uid-a` — succeeds; (b) reads `users/uid-a/session` while authenticated as `uid-b` — rejected with `PERMISSION_DENIED`; (c) writes `users/uid-a/profile` as unauthenticated — rejected.

**Acceptance Criteria**

- `firestore.rules` deployed without syntax error.
- Cross-user read rejected with `PERMISSION_DENIED` in emulator.
- Unauthenticated read rejected with `PERMISSION_DENIED`.
- Authenticated same-user read/write succeeds.
- `firestoreSchema.ts` TypeScript compiles without error.

**Rollback Strategy:** `firebase deploy --only firestore:rules` with the previous rules file (or a permissive `allow read, write: if request.auth != null` as a temporary rollback).

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Firestore Security Rules syntax is declarative; schema is directly derived from the migration design.

**Context Strategy**

- Start new chat? No
- Required files: `functions/src/index.ts`, `firebase.json`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-006 — KITE_API_SECRET Migration to Firebase Secret Manager

**Phase:** 1 — Cloud Functions Foundation
**Subsystem:** Security / Cloud Functions

**Description:**
Store `KITE_API_SECRET` in Firebase Secret Manager so Cloud Functions can access it at runtime via `defineSecret()`. Verify the secret is accessible within a Cloud Function. Remove `KITE_API_SECRET` from `secrets.properties.template` to prevent future developers from adding it to BuildConfig.

**Scope Boundaries**

- Files affected: `functions/src/utils/secrets.ts` (new — `defineSecret` declarations), `secrets.properties.template` (remove `KITE_API_SECRET` entry), `functions/src/index.ts` (add secret references to function exports)
- Modules affected: None (Android modules unaffected)
- Explicitly NOT touching: `app/build.gradle.kts`, Android `BuildConfig`, any Kite API source files

**Implementation Steps**

1. In `functions/src/utils/secrets.ts`, declare the secret using the Firebase Functions v2 API:
   - `export const kiteApiSecret = defineSecret("KITE_API_SECRET");`
   - `export const kiteApiKey = defineSecret("KITE_API_KEY");` — also move the API key to Secret Manager to remove it from BuildConfig in a future hardening task (FM-032).
2. Document in `secrets.ts` via comments: what each secret is, where it originates (Kite developer console), and that it must NEVER be committed to source control or added to `secrets.properties`.
3. In `secrets.properties.template`, remove the `KITE_API_SECRET=your_kite_api_secret_here` line. Add a comment: `# KITE_API_SECRET is stored in Firebase Secret Manager only. Do NOT add it here.`
4. Add `functions/` build step to `ci-release.yml` mirroring what was done for `ci-staging.yml` in FM-004.
5. Create a minimal validation Cloud Function `validateSecretAccess` that reads `kiteApiSecret.value()` and returns `{ configured: true }` if non-empty (deployed to staging only, removed in FM-034).

**User Action Steps (Manual Execution)**

1. In Firebase console → **Build** → **Functions** → enable the Secret Manager API if prompted.
2. Run: `firebase functions:secrets:set KITE_API_SECRET` → when prompted, paste the value from `secrets.properties` (local only, never committed).
3. Run: `firebase functions:secrets:set KITE_API_KEY` → paste the Kite API key value.
4. Verify via: `firebase functions:secrets:access KITE_API_SECRET` — should print the value (do not leave this in shell history; clear with `history -c` if needed).
5. In local `secrets.properties`, remove the `KITE_API_SECRET` line after confirming it is stored in Secret Manager.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Deploy `validateSecretAccess` to staging: call it via `firebase functions:shell` → confirm `{ configured: true }` returned.
- Confirm `strings.xml` and `BuildConfig.java` in `assembleDebug` output contain no `KITE_API_SECRET` string (run `grep -r "KITE_API_SECRET" app/build/` — zero matches expected).

**Acceptance Criteria**

- `KITE_API_SECRET` accessible in Cloud Functions runtime via `kiteApiSecret.value()`.
- `secrets.properties.template` no longer contains `KITE_API_SECRET`.
- `grep -r "KITE_API_SECRET" app/build/generated/` returns zero matches on a fresh `assembleDebug`.
- `validateSecretAccess` returns `{ configured: true }` from Firebase console.

**Rollback Strategy:** Re-add `KITE_API_SECRET` to `secrets.properties.template` with a comment. Secret Manager retains the value; no data is lost.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Secret Manager configuration follows Firebase v2 Functions documented API.

**Context Strategy**

- Start new chat? No
- Required files: `functions/src/index.ts`, `functions/src/utils/`, `secrets.properties.template`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-007 — Cloud Functions Authentication Middleware

**Phase:** 1 — Cloud Functions Foundation
**Subsystem:** Cloud Functions / Security

**Description:**
Implement the shared authentication middleware used by all Kite proxy Cloud Functions. Since all functions are Firebase HTTPS Callable functions, Firebase automatically validates the caller's Firebase ID token. This task implements the additional layer: a `getAuthenticatedUid` utility that extracts the verified `uid` from `CallableRequest.auth`, rejects unauthenticated calls with a structured error, and provides typed helper functions for Firestore document access using the verified UID.

**Scope Boundaries**

- Files affected: `functions/src/middleware/authMiddleware.ts` (new), `functions/src/utils/firestoreUtils.ts` (new)
- Modules affected: None (Android unaffected)
- Explicitly NOT touching: Any Android source, Firestore rules, existing function stubs

**Implementation Steps**

1. Implement `functions/src/middleware/authMiddleware.ts`:
   - `export function getAuthenticatedUid(request: CallableRequest): string` — checks `request.auth?.uid`; if absent, throws `new HttpsError("unauthenticated", "Authentication required.")`.
   - `export function requireUid(uid: string | undefined): asserts uid is string` — same guard, used for internal calls.
2. Implement `functions/src/utils/firestoreUtils.ts`:
   - `export function getUserProfileRef(uid: string)` — returns `db.collection("users").doc(uid).collection("profile").doc("data")`.
   - `export function getUserSessionRef(uid: string)` — returns `db.collection("users").doc(uid).collection("session").doc("data")`.
   - `export function getUserSyncStateRef(uid: string)` — returns `db.collection("users").doc(uid).collection("syncState").doc("data")`.
   - `export const db = admin.firestore()` — initialised Firebase Admin instance (shared singleton).
3. Write unit tests in `functions/src/middleware/authMiddleware.test.ts`:
   - `getAuthenticatedUid` with `auth: undefined` → throws `HttpsError` with code `"unauthenticated"`.
   - `getAuthenticatedUid` with `auth: { uid: "test-uid" }` → returns `"test-uid"`.
4. Run `npm test` in `functions/` — all middleware tests pass.

**User Action Steps (Manual Execution)**

None — fully automated implementation and test.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: `authMiddleware.test.ts` — both cases above pass.
- Integration (emulator): deploy a test callable function that calls `getAuthenticatedUid`; invoke without auth token → get `unauthenticated` error; invoke with valid Firebase test token → get UID string.

**Acceptance Criteria**

- `getAuthenticatedUid` throws `HttpsError("unauthenticated")` for all calls with missing or invalid `auth`.
- `getAuthenticatedUid` returns `uid` string for authenticated calls.
- All unit tests pass.
- `functions/npm run build` succeeds with middleware included.

**Rollback Strategy:** Remove `authMiddleware.ts` and `firestoreUtils.ts`. No deployed functions depend on them yet.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Middleware pattern is straightforward; Firebase Callable function auth access is documented.

**Context Strategy**

- Start new chat? No
- Required files: `functions/src/utils/firestoreSchema.ts`, `functions/src/index.ts`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-008 — Cloud Function: storeApiKey

**Phase:** 1 — Cloud Functions Foundation
**Subsystem:** Cloud Functions / Onboarding

**Description:**
Implement the `storeApiKey` HTTPS Callable Cloud Function. During onboarding, after Firebase Auth sign-in, the app calls this function with the user's Kite `apiKey`. The function stores the key in `users/{uid}/profile` in Firestore. This is the only Cloud Function the app calls during onboarding that does NOT involve the Kite API.

**Scope Boundaries**

- Files affected: `functions/src/auth/storeApiKey.ts` (new), `functions/src/index.ts` (export `storeApiKey`)
- Modules affected: None (Android unaffected in this task)
- Explicitly NOT touching: `exchangeKiteToken`, proxy functions, any Android source

**Implementation Steps**

1. Implement `functions/src/auth/storeApiKey.ts`:
   - Input schema: `{ apiKey: string }`.
   - Call `getAuthenticatedUid(request)` — rejects if unauthenticated.
   - Validate `apiKey`: non-empty string, length between 4 and 64 characters; throw `HttpsError("invalid-argument", ...)` if invalid.
   - Write to `getUserProfileRef(uid)` via `set({ apiKey, updatedAt: FieldValue.serverTimestamp() }, { merge: true })`.
   - Return `{ success: true }`.
2. Export `storeApiKey` from `functions/src/index.ts`.
3. Write unit tests: (a) unauthenticated call → `unauthenticated` error; (b) empty `apiKey` → `invalid-argument` error; (c) valid `apiKey` → `success: true` and Firestore write verified via emulator.
4. Deploy to staging: `firebase deploy --only functions:storeApiKey`.

**User Action Steps (Manual Execution)**

1. After deployment, test via Firebase console → Functions → `storeApiKey` → test function with a mock authenticated request containing a sample `apiKey`.

**Data Impact**

- Schema changes: Creates `users/{uid}/profile/data` document in Firestore on first call
- Migration required: No

**Test Plan**

- Unit: all three cases above.
- Emulator: authenticated call with `apiKey="test1234"` → document `users/test-uid/profile/data` contains `{ apiKey: "test1234" }`.

**Acceptance Criteria**

- `storeApiKey` deployed and visible in Firebase console.
- Unauthenticated call returns `unauthenticated` error.
- Invalid `apiKey` (empty string) returns `invalid-argument` error.
- Valid call writes `apiKey` to correct Firestore path.
- Unit tests and emulator tests pass.

**Rollback Strategy:** `firebase deploy --only functions` re-deploying without the `storeApiKey` export, or delete the function from Firebase console.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Straightforward Firestore write with input validation; follows established Callable function pattern.

**Context Strategy**

- Start new chat? No
- Required files: `functions/src/middleware/authMiddleware.ts`, `functions/src/utils/firestoreUtils.ts`, `functions/src/utils/firestoreSchema.ts`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-009 — Cloud Function: exchangeKiteToken (OAuth Token Exchange)

**Phase:** 1 — Cloud Functions Foundation
**Subsystem:** Cloud Functions / Authentication

**Description:**
Implement the `exchangeKiteToken` HTTPS Callable Cloud Function. This is the most security-critical function in the migration. It receives the Kite `request_token` from the app (after the user completes the OAuth web flow), computes the SHA-256 checksum server-side using `KITE_API_SECRET` from Secret Manager, calls Kite's `/session/token` endpoint, and stores the resulting `access_token` in Firestore. It returns `{ success: true }` to the app — the `access_token` is NEVER returned to the device.

**Scope Boundaries**

- Files affected: `functions/src/auth/exchangeKiteToken.ts` (new), `functions/src/utils/kiteClient.ts` (new — shared Kite API HTTP client), `functions/src/index.ts` (export `exchangeKiteToken`)
- Modules affected: None (Android unaffected in this task)
- Explicitly NOT touching: Any Android source, `BindAccountUseCase`, `CredentialStore`, Retrofit services

**Implementation Steps**

1. Implement `functions/src/utils/kiteClient.ts`:
   - Export `makeKiteRequest(endpoint: string, method: string, body?: object, accessToken?: string): Promise<object>` using Node.js `https` module (no external HTTP library to keep bundle size minimal).
   - Base URL: `https://api.kite.trade`.
   - When `accessToken` provided, set header `Authorization: token {apiKey}:{accessToken}`. `apiKey` is read from `kiteApiKey.value()` (Secret Manager).
   - Parse JSON response; throw `KiteApiError` if `status !== "success"`.
   - Define `KiteApiError(message: string, kiteErrorType: string, httpStatus: number)`.
2. Implement `functions/src/auth/exchangeKiteToken.ts`:
   - Annotate with `runWith({ secrets: ["KITE_API_SECRET", "KITE_API_KEY"] })`.
   - Input schema: `{ requestToken: string }`.
   - Call `getAuthenticatedUid(request)`.
   - Validate `requestToken`: non-empty string.
   - Fetch `apiKey` from Firestore `users/{uid}/profile/data` — throw `not-found` if absent (user must call `storeApiKey` first during onboarding).
   - Compute checksum: `sha256(apiKey + requestToken + kiteApiSecret.value())` using Node.js `crypto.createHash("sha256")`.
   - Call Kite POST `/session/token` with `{ api_key: apiKey, request_token: requestToken, checksum }`.
   - On success: write `{ accessToken: response.data.access_token, tokenObtainedAt: FieldValue.serverTimestamp(), tokenExpired: false }` to `getUserSessionRef(uid)`.
   - Write `{ kiteUserId: response.data.user_id, kiteUserName: response.data.user_name }` to `getUserProfileRef(uid)` (merge).
   - Return `{ success: true, kiteUserId: response.data.user_id, kiteUserName: response.data.user_name }`.
   - On Kite API error: throw `HttpsError("unauthenticated", "Kite token exchange failed.")` — do NOT include the raw Kite error message in the response (information leakage).
3. Export `exchangeKiteToken` from `functions/src/index.ts`.
4. Write unit tests with mocked `kiteClient` and mocked Firestore:
   - Unauthenticated call → `unauthenticated` error.
   - Missing profile (no `storeApiKey` called) → `not-found` error.
   - Kite API returns error → `unauthenticated` error.
   - Kite API returns success → Firestore session document written; returns `{ success: true }`.
   - Verify checksum is computed with `KITE_API_SECRET` (not returned to caller).
5. Deploy: `firebase deploy --only functions:exchangeKiteToken,functions:storeApiKey`.

**User Action Steps (Manual Execution)**

1. After deployment, perform an end-to-end test using a real Kite developer account:
   a. Call `storeApiKey` with your API key.
   b. Open `https://kite.zerodha.com/connect/login?api_key={yourApiKey}` in a browser.
   c. Log in → copy the `request_token` from the redirect URL.
   d. Call `exchangeKiteToken` from Firebase console with `{ requestToken: "..." }` while authenticated.
   e. Verify `users/{uid}/session/data` contains `accessToken` in Firestore console.
   f. Verify `access_token` does NOT appear in the Cloud Function response.

**Data Impact**

- Schema changes: Writes `users/{uid}/session/data` and updates `users/{uid}/profile/data` in Firestore
- Migration required: No (Android Room database unaffected)

**Test Plan**

- Unit: all four cases above.
- End-to-end (production Firebase): real `request_token` → `access_token` appears in Firestore → NOT in function response.

**Acceptance Criteria**

- `access_token` stored in Firestore `users/{uid}/session/data.accessToken`.
- `access_token` absent from function response payload.
- SHA-256 checksum uses `KITE_API_SECRET` from Secret Manager (verified by checking Cloud Function logs show no secret values).
- Unauthenticated call rejected.
- Missing profile rejected with `not-found`.
- All unit tests pass.
- End-to-end test succeeds with a real Kite account.

**Rollback Strategy:** Remove `exchangeKiteToken` export from `functions/src/index.ts`. Android app still uses the old `BindAccountUseCase` path (gated by `FEATURE_FIREBASE_AUTH = false`). Firestore session documents can be deleted manually. No Android code is changed in this task.

**Estimated Complexity:** L

---

**LLM Execution Assignment**

- Recommended Model: Claude Opus
- Recommended Mode: Thinking
- Reason: The SHA-256 checksum computation, the Kite `/session/token` call, and the Firestore write sequence are financially and security-critical. An error in the checksum formula causes complete authentication failure. The decision to never return `access_token` to the caller must be structurally enforced, not left to convention.

**Context Strategy**

- Start new chat? Yes (new subsystem: security-critical token exchange)
- Required files: `functions/src/middleware/authMiddleware.ts`, `functions/src/utils/firestoreUtils.ts`, `functions/src/utils/firestoreSchema.ts`, `functions/src/utils/secrets.ts`
- Architecture docs to reference: `07_SECURITY_MODEL.md` (token storage and auth flow sections)
- Documents NOT required: All Android architecture documents, domain engine documents

---

## Detailed Tasks — Phase 2: OAuth Flow Migration {#phase-2-tasks}

**Objective:** Replace the device-side Kite OAuth token exchange with the Firebase-backed flow. Introduce Firebase Auth sign-in before the Kite OAuth step. Wire the app to call `storeApiKey` and `exchangeKiteToken` Cloud Functions. Gate all new paths behind `FEATURE_FIREBASE_AUTH`. The old `BindAccountUseCase` path remains operational while `FEATURE_FIREBASE_AUTH = false`.

**Duration:** ~3 days
**Risk Level:** High — incorrect auth flow leaves the user unable to log in; rollback must be immediate
**Depends On:** Phase 1 complete; `exchangeKiteToken` deployed and end-to-end tested

**Completion Criteria:**

- `FEATURE_FIREBASE_AUTH = true` in `staging` build type.
- End-to-end Kite OAuth completes via Firebase Cloud Function on a physical device.
- `users/{uid}/session/data.accessToken` populated in Firestore after successful login.
- `access_token` absent from `EncryptedSharedPreferences` after Firebase-path login.
- Existing login path still works when `FEATURE_FIREBASE_AUTH = false` (regression guard).

---

### FM-010 — Android: Firebase Callable Function Client

**Phase:** 2 — OAuth Flow Migration
**Subsystem:** `:infra-firebase`

**Description:**
Implement `FirebaseFunctionsClient` in `:infra-firebase` — a thin, injectable wrapper around the Firebase Cloud Functions SDK that exposes each Cloud Function as a typed suspend function. This client is the single integration point between the Android app and all Cloud Functions. It replaces the Retrofit `KiteConnectApiService` calls progressively, one function at a time. It is introduced in this task before any callers exist.

**Scope Boundaries**

- Files affected: `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseFunctionsClient.kt` (new), `infra/firebase/src/main/java/com/kitewatch/infra/firebase/di/FirebaseModule.kt` (update — add `FirebaseFunctions` binding)
- Modules affected: `:infra-firebase`
- Explicitly NOT touching: `:core-network`, `KiteConnectApiService`, any existing interceptors, `CredentialStore`, feature modules

**Implementation Steps**

1. Add `firebase-functions-ktx` to `infra/firebase/build.gradle.kts` (already declared in BOM from FM-001).
2. Implement `FirebaseFunctionsClient`:
   - Constructor-injected `FirebaseFunctions` instance (provided by `FirebaseModule`).
   - `suspend fun storeApiKey(apiKey: String): Result<Unit>` — calls `functions.getHttpsCallable("storeApiKey").call(mapOf("apiKey" to apiKey)).await()`. Wraps `FirebaseFunctionsException` → `AppError.NetworkError`.
   - `suspend fun exchangeKiteToken(requestToken: String): Result<KiteBindingResult>` — calls `exchangeKiteToken` function. Parses response data map into `KiteBindingResult(kiteUserId: String, kiteUserName: String)`.
   - Error mapping: `FirebaseFunctionsException.Code.UNAUTHENTICATED` → `AppError.AuthError.TokenExpired`; `UNAVAILABLE` or `DEADLINE_EXCEEDED` → `AppError.NetworkError.Timeout`; `NOT_FOUND` → `AppError.AuthError.NotBound`.
   - All proxy functions (orders, holdings, etc.) are stubbed as `TODO()` for now — they will be implemented in Phase 3.
3. Update `FirebaseModule`: add `@Singleton @Provides fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()`.
4. Write unit tests for `FirebaseFunctionsClient` with a mocked `FirebaseFunctions`: `storeApiKey` success, `storeApiKey` with `FirebaseFunctionsException(UNAUTHENTICATED)` → `AppError.AuthError`, `exchangeKiteToken` success → `KiteBindingResult` with correct fields.

**User Action Steps (Manual Execution)**

None — fully automated implementation.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: mocked `FirebaseFunctions`; all three cases above.
- Manual: Confirm module compiles and `:infra-firebase` is included in `assembleDebug` without crash.

**Acceptance Criteria**

- `FirebaseFunctionsClient` compiles without error.
- `storeApiKey` and `exchangeKiteToken` are callable methods with correct return types.
- Error mapping from `FirebaseFunctionsException` to `AppError` is correct.
- Unit tests pass.
- Proxy function stubs (`TODO()`) do not crash the module compilation.

**Rollback Strategy:** Remove `FirebaseFunctionsClient.kt`. No existing caller references it yet.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Wrapper class around Firebase SDK with error mapping; follows established `ApiResultAdapter` pattern in `:core-network`.

**Context Strategy**

- Start new chat? Yes (new phase: Android Firebase integration)
- Required files: `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseAuthManager.kt`, `core/domain/src/main/java/com/kitewatch/domain/error/AppError.kt`, `core/network/src/main/java/com/kitewatch/network/kiteconnect/adapter/ApiResultAdapter.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-011 — Android: Update BindAccountUseCase for Firebase Auth Path

**Phase:** 2 — OAuth Flow Migration
**Subsystem:** `:core-domain`

**Description:**
Modify `BindAccountUseCase` to support two execution paths gated by the `useFirebase: Boolean` flag passed in by the caller (which reads `BuildConfig.FEATURE_FIREBASE_AUTH`). The Firebase path: (1) calls `storeApiKey` via `FirebaseFunctionsClient`, (2) returns a pending state that waits for the deep link with `request_token`, (3) after receiving `request_token`, calls `exchangeKiteToken`. The old path (SHA-256 on device) is preserved intact and runs when `useFirebase = false`. The `apiSecret` parameter is marked `@Deprecated` — it is only used in the old path.

**Scope Boundaries**

- Files affected: `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountUseCase.kt`, `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountState.kt` (new sealed class)
- Modules affected: `:core-domain`
- Explicitly NOT touching: Any `CredentialStore`, `AccountBindingStore`, Retrofit service, feature screens (no UI change yet)

**Implementation Steps**

1. Define `BindAccountState` sealed interface in `BindAccountState.kt`:
   - `AwaitingRequestToken` — Firebase path only: API key stored, waiting for user to complete Kite web login.
   - `Success(accountBinding: AccountBinding)` — both paths: binding complete.
   - `Error(appError: AppError)` — both paths.
2. Update `BindAccountUseCase`:
   - Add constructor parameter `firebaseFunctionsClient: FirebaseFunctionsClient?` (nullable so it can be omitted in `:core-domain` unit tests using fakes).
   - Add function `suspend fun initiateFirebaseBind(apiKey: String): BindAccountState` — calls `firebaseFunctionsClient.storeApiKey(apiKey)`; returns `AwaitingRequestToken` on success.
   - Add function `suspend fun completeFirebaseBind(requestToken: String): BindAccountState` — calls `firebaseFunctionsClient.exchangeKiteToken(requestToken)`; on success, calls `accountBindingRepository.bind(...)` using the returned `kiteUserId` and `kiteUserName`.
   - Rename existing `suspend fun execute(apiKey, requestToken, apiSecret)` to `suspend fun executeDeviceSide(apiKey, requestToken, apiSecret)` and annotate `@Deprecated("Use Firebase path. Will be removed in FM-034.")`.
3. Update the Hilt binding in `:core-domain` to inject `FirebaseFunctionsClient` via the `:infra-firebase` module dependency.
4. Update unit tests: `BindAccountUseCaseTest` — add tests for `initiateFirebaseBind` (success → `AwaitingRequestToken`, `storeApiKey` error → `BindAccountState.Error`) and `completeFirebaseBind` (success → `BindAccountState.Success`, `exchangeKiteToken` unauthenticated → `Error`).

**User Action Steps (Manual Execution)**

None — fully automated implementation.

**Data Impact**

- Schema changes: None (Room database unaffected)
- Migration required: No

**Test Plan**

- Unit: all four new test cases above. Existing `executeDeviceSide` tests must still pass.
- Manual: `assembleDebug` passes with no compilation errors.

**Acceptance Criteria**

- `executeDeviceSide` still compiles and tests pass (backward compatibility during transition).
- `initiateFirebaseBind` returns `AwaitingRequestToken` on `storeApiKey` success.
- `completeFirebaseBind` returns `BindAccountState.Success` with populated `AccountBinding` on `exchangeKiteToken` success.
- Zero `apiSecret` usage in `initiateFirebaseBind` or `completeFirebaseBind`.
- All new and existing `BindAccountUseCaseTest` tests pass.

**Rollback Strategy:** Revert `BindAccountUseCase.kt` to its pre-FM-011 state. `BindAccountState.kt` is new — delete it. No callers have been updated yet.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Dual-path extension of an existing use case; no new business logic, only routing.

**Context Strategy**

- Start new chat? No
- Required files: `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountUseCase.kt`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseFunctionsClient.kt`, `core/domain/src/main/java/com/kitewatch/domain/model/AccountBinding.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-012 — Android: Onboarding Flow Update (Firebase Auth Path)

**Phase:** 2 — OAuth Flow Migration
**Subsystem:** `:feature-onboarding`

**Description:**
Update the onboarding ViewModel and screen to support the Firebase auth path when `BuildConfig.FEATURE_FIREBASE_AUTH = true`. The new onboarding sequence is: (1) Google Sign-In via `FirebaseAuthManager`, (2) API key entry (API secret field is removed), (3) Kite web OAuth, (4) deep link received → `completeFirebaseBind`. The old onboarding flow (API key + API secret + SHA-256 on device) is preserved in a separate branch for `FEATURE_FIREBASE_AUTH = false`.

**Scope Boundaries**

- Files affected: `feature/onboarding/src/main/java/com/kitewatch/feature/onboarding/OnboardingViewModel.kt`, `feature/onboarding/src/main/java/com/kitewatch/feature/onboarding/OnboardingScreen.kt`, `feature/onboarding/src/main/java/com/kitewatch/feature/onboarding/OnboardingState.kt`
- Modules affected: `:feature-onboarding`
- Explicitly NOT touching: Domain engine, database, WorkManager, other feature modules

**Implementation Steps**

1. Add `FirebaseAuthManager` and `BindAccountUseCase` (already injected) to `OnboardingViewModel` constructor.
2. In `OnboardingViewModel`, add a new `UiState` variant `GoogleSignInRequired` and `AwaitingKiteCallback`.
3. Add `fun onGoogleSignInResult(idToken: String)` — calls `FirebaseAuthManager.signInWithGoogle(idToken)`. On success: transitions to API key entry step.
4. Add `fun onApiKeySubmitted(apiKey: String)` — calls `BindAccountUseCase.initiateFirebaseBind(apiKey)`. On `AwaitingRequestToken`: transitions state to `AwaitingKiteCallback`. Constructs Kite OAuth URL: `https://kite.zerodha.com/connect/login?api_key={apiKey}&v=3` and emits it as a `UiEffect` for the screen to open in a browser.
5. Add `fun onKiteDeepLinkReceived(requestToken: String)` — calls `BindAccountUseCase.completeFirebaseBind(requestToken)`. On `BindAccountState.Success`: transitions to bound state.
6. In `OnboardingScreen.kt`: add a `GoogleSignInButton` composable that triggers the Google Sign-In intent. **Remove the API secret `TextField` from the screen when `BuildConfig.FEATURE_FIREBASE_AUTH = true`** using a compile-time `if` block (not a runtime check — Kotlin `if` on `const val` is resolved at compile time by R8).
7. Both the old (`FEATURE_FIREBASE_AUTH = false`) and new paths share the same deep link handler in `MainActivity` — `onKiteDeepLinkReceived` is called regardless of path.
8. Write ViewModel unit tests: Google Sign-In success → transitions to API key step; `initiateFirebaseBind` success → `AwaitingKiteCallback`; `completeFirebaseBind` success → bound state.

**User Action Steps (Manual Execution)**

None — code change only. Manual testing on device after implementation.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: ViewModel state machine — all three transitions above.
- Manual (staging with `FEATURE_FIREBASE_AUTH = true`): full onboarding flow — Google Sign-In → API key → Kite browser login → deep link → account bound. Verify `users/{uid}/session` exists in Firestore console. Verify no `kite_access_token` in `EncryptedSharedPreferences`.

**Acceptance Criteria**

- API secret input field is absent from the `staging` build's onboarding screen.
- Google Sign-In step completes and `FirebaseAuth.currentUser` is non-null.
- After deep link receipt, `BindAccountState.Success` is emitted.
- Old path (`FEATURE_FIREBASE_AUTH = false`) still compiles and onboards successfully in `debug` builds.
- All ViewModel unit tests pass.

**Rollback Strategy:** Set `FEATURE_FIREBASE_AUTH = false` in `staging` build type in `app/build.gradle.kts`. The old onboarding path becomes active immediately. Firebase-path ViewModel methods exist but are never called.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: ViewModel state machine extension; Compose UI conditional rendering.

**Context Strategy**

- Start new chat? No
- Required files: `feature/onboarding/src/main/java/com/kitewatch/feature/onboarding/OnboardingViewModel.kt`, `feature/onboarding/src/main/java/com/kitewatch/feature/onboarding/OnboardingScreen.kt`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseAuthManager.kt`, `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountUseCase.kt`
- Architecture docs to reference: `05_APPLICATION_STRUCTURE.md` §3 (Navigation Architecture)
- Documents NOT required: Domain engine, database, security documents

---

### FM-013 — Enable FEATURE_FIREBASE_AUTH in Staging and Phase 2 Validation

**Phase:** 2 — OAuth Flow Migration
**Subsystem:** Build System / Validation

**Description:**
Enable `FEATURE_FIREBASE_AUTH = true` in the `staging` build type after the end-to-end OAuth flow is validated on a physical device. Update `secrets.properties.template` with the new Firebase-related entries. Execute the Phase 2 checkpoint checklist.

**Scope Boundaries**

- Files affected: `app/build.gradle.kts` (update `staging` build type flag), `secrets.properties.template` (add Firebase notes)
- Modules affected: `:app`
- Explicitly NOT touching: Any source logic — this is a validation and flag-flip task only

**Implementation Steps**

1. On `staging` build type in `app/build.gradle.kts`: change `buildConfigField("Boolean", "FEATURE_FIREBASE_AUTH", "false")` to `"true"`.
2. Add to `secrets.properties.template`:

   ```
   # Firebase: no secrets in this file. All Firebase config is in google-services.json.
   # KITE_API_SECRET is in Firebase Secret Manager only.
   ```

3. Run `./gradlew assembleStagingRelease` — confirm success.
4. Install staging APK on physical device. Execute full Phase 2 checkpoint:
   - Google Sign-In completes.
   - Kite OAuth URL opens in browser.
   - Deep link returns `request_token`.
   - `exchangeKiteToken` Cloud Function called and succeeds.
   - `users/{uid}/session/data.accessToken` present in Firestore.
   - No `kite_access_token` in device `EncryptedSharedPreferences`.
   - Old onboarding path works in `debug` build.
5. Push to `develop` — confirm `ci-staging.yml` passes.

**User Action Steps (Manual Execution)**

1. Perform full onboarding on staging APK with a real Zerodha account.
2. Check Firebase console → Firestore → `users/{uid}/session/data` for `accessToken` presence.
3. On rooted device or emulator: dump `EncryptedSharedPreferences` contents — confirm `kite_access_token` is absent.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- End-to-end on physical device as described in Implementation Steps 4.
- CI pipeline passes on push to `develop`.

**Acceptance Criteria**

- Every Phase 2 completion criterion listed above is verified.
- `./gradlew assembleStagingRelease` passes.
- CI passes.
- Zero blocking issues before Phase 3 begins.

**Rollback Strategy:** Set `FEATURE_FIREBASE_AUTH = false` in staging build type. Re-push to develop. No data migration needed.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (clean context before API proxy work)
- Required files: `app/build.gradle.kts`
- Architecture docs to reference: None
- Documents NOT required: All

---

## Detailed Tasks — Phase 3: API Proxy Implementation {#phase-3-tasks}

**Objective:** Implement Cloud Functions for all 7 Kite API endpoints currently called by the Android app. Wire `FirebaseFunctionsClient` with concrete implementations for each proxy function. Gate the new proxy path behind `FEATURE_FIREBASE_PROXY`. The existing `KiteConnectRepositoryImpl` Retrofit path remains operational.

**Duration:** ~4 days
**Risk Level:** High — API proxy correctness directly affects portfolio data accuracy; incorrect response mapping causes data corruption in Room
**Depends On:** Phase 2 complete; `exchangeKiteToken` works; `access_token` available in Firestore

**Completion Criteria:**

- All 7 Kite API endpoints proxied through Cloud Functions.
- `OrderSyncWorker` completes successfully via proxy path (`FEATURE_FIREBASE_PROXY = true`) on staging.
- GTT create/update/delete operations succeed via proxy.
- All Cloud Function proxy unit tests pass.
- Response DTOs match what the Android app currently expects from `KiteConnectApiService`.

---

### FM-014 — Cloud Functions: getOrders and getHoldings Proxies

**Phase:** 3 — API Proxy Implementation
**Subsystem:** Cloud Functions / Proxy

**Description:**
Implement the `getOrders` and `getHoldings` HTTPS Callable Cloud Functions. Each function retrieves the user's Kite `access_token` from Firestore, calls the corresponding Kite API endpoint, and returns the response data to the app. The response structure must exactly match what `KiteConnectApiService` currently returns so that existing DTOs and mappers in `:core-data` require no changes.

**Scope Boundaries**

- Files affected: `functions/src/proxy/orders.ts` (new), `functions/src/proxy/holdings.ts` (new), `functions/src/index.ts` (export both)
- Modules affected: None (Android unaffected in this task)
- Explicitly NOT touching: Android DTOs, mappers, repository implementations, Room entities

**Implementation Steps**

1. Implement `functions/src/utils/sessionUtils.ts`:
   - `export async function getAccessToken(uid: string): Promise<string>` — reads `getUserSessionRef(uid)`; throws `HttpsError("unauthenticated", "Session expired.")` if document absent or `tokenExpired === true`; returns `accessToken` string.
   - If `tokenExpired === true`, also writes `tokenExpired: true` to the session document (idempotent) so the app can observe it via Firestore listener.
2. Implement `functions/src/proxy/orders.ts`:
   - HTTPS Callable function `getOrders`.
   - Annotate with `runWith({ secrets: ["KITE_API_KEY"] })`.
   - Call `getAuthenticatedUid(request)`.
   - Call `getAccessToken(uid)`.
   - Call `makeKiteRequest("/orders", "GET", undefined, accessToken)`.
   - If Kite returns HTTP 403: call `markTokenExpired(uid)` (sets `tokenExpired: true` in Firestore); throw `HttpsError("unauthenticated", "Kite session expired.")`.
   - Return `{ data: kiteResponse.data }` — the array of order objects as returned by Kite API.
3. Implement `functions/src/proxy/holdings.ts` with identical structure but calling `/portfolio/holdings`.
4. Export both from `functions/src/index.ts`.
5. Write unit tests for each: (a) success path — returns Kite response data; (b) `tokenExpired = true` in Firestore → `unauthenticated` error; (c) Kite 403 → `unauthenticated` error + `tokenExpired` marked in Firestore; (d) unauthenticated Firebase call → `unauthenticated` error.
6. Deploy both functions to staging.

**User Action Steps (Manual Execution)**

1. After deployment, test via Firebase console → Functions shell:
   - Authenticated call to `getOrders` → verify response contains today's order array.
   - Authenticated call to `getHoldings` → verify response contains holdings array.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: all four cases for each function (8 tests total).
- Manual: Real Kite account → real order data returned from `getOrders` function.

**Acceptance Criteria**

- `getOrders` returns `{ data: [...] }` with same structure as Kite API `/orders` response.
- `getHoldings` returns `{ data: [...] }` with same structure as Kite API `/portfolio/holdings` response.
- Token expired path sets `tokenExpired: true` in Firestore.
- All 8 unit tests pass.

**Rollback Strategy:** Remove exports from `functions/src/index.ts`. Re-deploy. Android app still uses Retrofit path (`FEATURE_FIREBASE_PROXY = false`).

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Proxy function follows a repeatable pattern; correctness is verified by comparing response shape against existing Kite API responses.

**Context Strategy**

- Start new chat? No
- Required files: `functions/src/utils/kiteClient.ts`, `functions/src/middleware/authMiddleware.ts`, `functions/src/utils/firestoreUtils.ts`, `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectApiService.kt` (for reference on response structure)
- Architecture docs to reference: None
- Documents NOT required: All Android architecture documents

---

### FM-015 — Cloud Functions: getFundBalance and getGttOrders Proxies

**Phase:** 3 — API Proxy Implementation
**Subsystem:** Cloud Functions / Proxy

**Description:**
Implement the `getFundBalance` and `getGttOrders` HTTPS Callable Cloud Functions, following the identical pattern established in FM-014. `getFundBalance` maps to `GET /user/margins`. `getGttOrders` maps to `GET /gtt`.

**Scope Boundaries**

- Files affected: `functions/src/proxy/fundBalance.ts` (new), `functions/src/proxy/gtt.ts` (new — initially only `getGttOrders`), `functions/src/index.ts`
- Modules affected: None (Android unaffected)
- Explicitly NOT touching: GTT create/update/delete (handled in FM-016), existing Cloud Functions

**Implementation Steps**

1. Implement `functions/src/proxy/fundBalance.ts`: HTTPS Callable `getFundBalance`. Call `GET /user/margins` on Kite API. Return `{ data: kiteResponse.data }`. Apply identical token expiry handling from FM-014's `sessionUtils.ts`.
2. Implement the read portion of `functions/src/proxy/gtt.ts`: HTTPS Callable `getGttOrders`. Call `GET /gtt` on Kite API. Return `{ data: kiteResponse.data }`.
3. Export both from `functions/src/index.ts`.
4. Write unit tests for each (same 4-case pattern as FM-014).
5. Deploy both.

**User Action Steps (Manual Execution)**

1. Test `getFundBalance` via Firebase console → confirm equity margins data returned.
2. Test `getGttOrders` → confirm GTT list returned (may be empty for a fresh account).

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: 4 cases per function (8 tests total).
- Manual: real account data confirmed from each function.

**Acceptance Criteria**

- `getFundBalance` returns Kite `/user/margins` response structure.
- `getGttOrders` returns Kite `/gtt` response structure.
- All 8 unit tests pass.
- Token expiry handling consistent with FM-014.

**Rollback Strategy:** Remove exports from `functions/src/index.ts`. Re-deploy.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Repeatable pattern; structurally identical to FM-014.

**Context Strategy**

- Start new chat? No
- Required files: `functions/src/proxy/orders.ts`, `functions/src/utils/sessionUtils.ts`, `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectApiService.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-016 — Cloud Functions: GTT Mutation Proxies (Create, Update, Delete)

**Phase:** 3 — API Proxy Implementation
**Subsystem:** Cloud Functions / Proxy

**Description:**
Implement three GTT mutation Cloud Functions: `createGttOrder`, `updateGttOrder`, and `deleteGttOrder`. These correspond to `POST /gtt`, `PUT /gtt/{id}`, and `DELETE /gtt/{id}` on the Kite API. Mutation functions require extra input validation — malformed GTT payloads sent to Kite may result in funds being allocated to incorrect orders. Input schemas are validated strictly before forwarding to Kite.

**Scope Boundaries**

- Files affected: `functions/src/proxy/gtt.ts` (extend with 3 mutation functions), `functions/src/index.ts`
- Modules affected: None (Android unaffected)
- Explicitly NOT touching: `getGttOrders` function (already in FM-015), domain GTT automation engine

**Implementation Steps**

1. Implement `createGttOrder` in `gtt.ts`:
   - Input schema: `{ tradingsymbol: string, exchange: "NSE"|"BSE", trigger_type: "single", trigger_values: number[], last_price: number, orders: GttOrderItem[] }` where `GttOrderItem = { transaction_type: "BUY"|"SELL", quantity: number, order_type: "LIMIT", product: "CNC", price: number }`.
   - Validate: all required fields present; `quantity > 0`; `price > 0`; throw `HttpsError("invalid-argument", ...)` on failure.
   - Call `POST /gtt` on Kite API with validated body.
   - Return `{ data: { trigger_id: number } }`.
2. Implement `updateGttOrder`:
   - Input schema: `{ triggerId: number, ...same fields as createGttOrder }`.
   - Validate `triggerId > 0` before forwarding.
   - Call `PUT /gtt/{triggerId}`.
   - Return `{ data: { trigger_id: number } }`.
3. Implement `deleteGttOrder`:
   - Input schema: `{ triggerId: number }`.
   - Validate `triggerId > 0`.
   - Call `DELETE /gtt/{triggerId}`.
   - Return `{ data: { trigger_id: number } }`.
4. Write unit tests: for each mutation — (a) valid input → Kite response forwarded; (b) missing required field → `invalid-argument`; (c) Kite API error → `internal` error with sanitised message; (d) unauthenticated → `unauthenticated`.
5. Deploy all three functions.

**User Action Steps (Manual Execution)**

1. Test `createGttOrder` via Firebase console with a real stock (`NSE:SBIN`) with a price far from current market (to avoid unintended execution) → confirm `trigger_id` returned.
2. Verify GTT appears in Kite web console.
3. Test `deleteGttOrder` with the returned `trigger_id` → confirm removal from Kite console.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: 4 cases per function (12 tests total).
- Manual: GTT lifecycle test via Firebase console.

**Acceptance Criteria**

- All three GTT mutation functions deployed and functional.
- Input validation rejects malformed payloads before reaching Kite.
- All 12 unit tests pass.
- GTT created via `createGttOrder` visible in Kite console.
- GTT deleted via `deleteGttOrder` removed from Kite console.

**Rollback Strategy:** Remove mutation function exports from `functions/src/index.ts`. `getGttOrders` read function unaffected.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: GTT mutation payloads are financially consequential. Input schema validation logic and the forwarding of correct Kite API request bodies require careful verification against Kite Connect GTT API documentation.

**Context Strategy**

- Start new chat? No
- Required files: `functions/src/proxy/orders.ts`, `functions/src/utils/kiteClient.ts`, `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectApiService.kt` (for GTT request/response shapes)
- Architecture docs to reference: None
- Documents NOT required: All Android architecture documents

---

### FM-017 — Android: FirebaseFunctionsClient — Proxy Method Implementations

**Phase:** 3 — API Proxy Implementation
**Subsystem:** `:infra-firebase`

**Description:**
Implement the seven proxy methods in `FirebaseFunctionsClient` that were stubbed as `TODO()` in FM-010. Each method calls the corresponding Cloud Function and maps the response to the same DTO type that `KiteConnectApiService` currently returns. The return types must be identical so that no changes are needed in `KiteConnectRepositoryImpl` data mappers.

**Scope Boundaries**

- Files affected: `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseFunctionsClient.kt`
- Modules affected: `:infra-firebase`
- Explicitly NOT touching: `KiteConnectApiService`, existing DTOs in `:core-network`, data mappers in `:core-data`, `KiteConnectRepositoryImpl`

**Implementation Steps**

1. Implement `suspend fun getOrders(): Result<List<OrderDto>>` — calls `functions.getHttpsCallable("getOrders").call(null).await()`. Parses `data` as `List<Map<String, Any?>>`. Deserialises each map entry into `OrderDto` using Moshi or manual field extraction. Returns `Result.success(dtos)` or maps `FirebaseFunctionsException` to `AppError`.
2. Implement `suspend fun getHoldings(): Result<List<HoldingDto>>` — same pattern for `getHoldings` function.
3. Implement `suspend fun getFundBalance(): Result<FundBalanceDto>` — same pattern for `getFundBalance`.
4. Implement `suspend fun getGttOrders(): Result<List<GttDto>>` — same pattern.
5. Implement `suspend fun createGttOrder(request: GttCreateRequest): Result<GttTriggerResponse>` — serialises request to map, calls `createGttOrder` function, parses `trigger_id` from response.
6. Implement `suspend fun updateGttOrder(triggerId: Long, request: GttCreateRequest): Result<GttTriggerResponse>` — adds `triggerId` to map before calling `updateGttOrder` function.
7. Implement `suspend fun deleteGttOrder(triggerId: Long): Result<Unit>` — calls `deleteGttOrder` function.
8. All methods: map `FirebaseFunctionsException.Code.UNAUTHENTICATED` → `AppError.AuthError.TokenExpired`.
9. Write unit tests: for each proxy method — success deserialization test; UNAUTHENTICATED exception → `AppError.AuthError.TokenExpired`; UNAVAILABLE exception → `AppError.NetworkError.Timeout`.

**User Action Steps (Manual Execution)**

None — fully automated implementation.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: success deserialization for each of the 7 methods. Verify `OrderDto` fields map correctly from the `Map<String, Any?>` response. Exception mapping tests.
- Manual: `assembleDebug` passes; no compilation errors.

**Acceptance Criteria**

- All 7 proxy methods implemented (no `TODO()` stubs remaining).
- `getOrders()` returns `List<OrderDto>` with correctly mapped fields.
- Exception mapping correct for all methods.
- Unit tests pass.
- `:infra-firebase` module compiles and is included in debug APK.

**Rollback Strategy:** Revert `FirebaseFunctionsClient.kt` to stub state. `KiteConnectRepositoryImpl` is still using Retrofit path (`FEATURE_FIREBASE_PROXY = false`).

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: DTO deserialization from Maps follows a repeatable pattern; return types are defined by existing `KiteConnectApiService` interfaces.

**Context Strategy**

- Start new chat? No
- Required files: `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseFunctionsClient.kt`, `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectApiService.kt`, all DTO files in `core/network/src/main/java/com/kitewatch/network/kiteconnect/dto/`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-018 — Android: FirebaseKiteRepository (Proxy-backed Repository Implementation)

**Phase:** 3 — API Proxy Implementation
**Subsystem:** `:core-data`

**Description:**
Implement `FirebaseKiteRepository` — a new implementation of the existing `KiteConnectRepository` interface (or equivalent domain repository interfaces) that delegates all remote calls to `FirebaseFunctionsClient` instead of `KiteConnectApiService`. This class is provided by Hilt alongside the existing `KiteConnectRepositoryImpl`, selected at runtime based on `BuildConfig.FEATURE_FIREBASE_PROXY`.

**Scope Boundaries**

- Files affected: `core/data/src/main/java/com/kitewatch/data/repository/FirebaseKiteRepository.kt` (new), `core/data/src/main/java/com/kitewatch/data/di/DataModule.kt` (update Hilt bindings for conditional provision)
- Modules affected: `:core-data`
- Explicitly NOT touching: `KiteConnectRepositoryImpl` (unchanged), domain use cases, Room DAOs, data mappers

**Implementation Steps**

1. Implement `FirebaseKiteRepository` implementing the same repository interface(s) as `KiteConnectRepositoryImpl`. Constructor parameters: `FirebaseFunctionsClient`, all relevant DAOs, all mappers.
2. For each interface method: delegate to the corresponding `FirebaseFunctionsClient` method, apply the same mapper to convert DTOs to domain entities, persist via DAOs — identical logic to `KiteConnectRepositoryImpl` except the remote source is `FirebaseFunctionsClient` instead of `KiteConnectApiService`.
3. Update `DataModule.kt` Hilt binding for the repository interface:

   ```kotlin
   @Provides @Singleton
   fun provideKiteRepository(
       firebaseRepo: FirebaseKiteRepository,
       retrofitRepo: KiteConnectRepositoryImpl,
   ): KiteConnectRepository =
       if (BuildConfig.FEATURE_FIREBASE_PROXY) firebaseRepo else retrofitRepo
   ```

4. Write unit tests for `FirebaseKiteRepository`: mock `FirebaseFunctionsClient`; verify `getOrders()` call → DTO mapped → persisted via DAO. Test the `AppError.AuthError.TokenExpired` path: repository converts to domain `AppError` and emits it correctly.

**User Action Steps (Manual Execution)**

None — fully automated implementation.

**Data Impact**

- Schema changes: None (Room schema is unaffected — same DAOs and mappers are used)
- Migration required: No

**Test Plan**

- Unit: success path for `getOrders` (mock DTO → correct domain entity in DAO). `TokenExpired` path. DAO interaction verified with in-memory Room.
- Manual: staging build with `FEATURE_FIREBASE_PROXY = true` → launch app → Portfolio screen loads data from Cloud Functions via `FirebaseKiteRepository`.

**Acceptance Criteria**

- `FirebaseKiteRepository` implements all methods of `KiteConnectRepository`.
- `DataModule.kt` selects `FirebaseKiteRepository` when `FEATURE_FIREBASE_PROXY = true`.
- `KiteConnectRepositoryImpl` still selected when `FEATURE_FIREBASE_PROXY = false`.
- Unit tests pass.
- Staging build with flag enabled shows correct portfolio data.

**Rollback Strategy:** Set `FEATURE_FIREBASE_PROXY = false` in staging build type. `KiteConnectRepositoryImpl` becomes active. `FirebaseKiteRepository` compiles but is never instantiated.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Repository implementation follows the exact same structure as `KiteConnectRepositoryImpl`; only the remote data source changes.

**Context Strategy**

- Start new chat? No
- Required files: `core/data/src/main/java/com/kitewatch/data/repository/KiteConnectRepositoryImpl.kt`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseFunctionsClient.kt`, `core/data/src/main/java/com/kitewatch/data/di/DataModule.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-019 — Enable FEATURE_FIREBASE_PROXY in Staging and Phase 3 Validation

**Phase:** 3 — API Proxy Implementation
**Subsystem:** Build System / Validation

**Description:**
Enable `FEATURE_FIREBASE_PROXY = true` in the `staging` build type after the proxy path is validated end-to-end. Execute the Phase 3 checkpoint checklist. Confirm `OrderSyncWorker` completes a full sync cycle via the Firebase proxy.

**Scope Boundaries**

- Files affected: `app/build.gradle.kts` (update `staging` flag)
- Modules affected: `:app`
- Explicitly NOT touching: Any source logic

**Implementation Steps**

1. On `staging` build type: change `FEATURE_FIREBASE_PROXY` from `"false"` to `"true"`.
2. Run `./gradlew assembleStagingRelease`.
3. Install staging APK. Execute Phase 3 checkpoint:
   - Portfolio screen loads orders from Cloud Function proxy.
   - Holdings screen loads holdings from Cloud Function proxy.
   - GTT screen shows GTTs from Cloud Function.
   - Create a test GTT via the app → confirm via `createGttOrder` Cloud Function log.
   - `OrderSyncWorker` fires and completes (check WorkManager status via `adb shell dumpsys jobscheduler`).
   - Token expired scenario: manually set `tokenExpired: true` in Firestore → confirm app shows re-auth screen.
4. Confirm `KiteConnectApiService` is still imported and compiles (it is not deleted until FM-034).
5. Push to `develop` — CI passes.

**User Action Steps (Manual Execution)**

1. Check Firebase console → Functions → Logs → confirm `getOrders` was called during app startup.
2. Set `tokenExpired: true` manually in Firestore → observe app behaviour.
3. Reset `tokenExpired: false` and repeat Kite OAuth to restore session.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- End-to-end as described above.
- CI passes.

**Acceptance Criteria**

- All Phase 3 completion criteria verified.
- `./gradlew assembleStagingRelease` passes.
- CI passes.
- Zero P0 or P1 bugs before Phase 4 begins.

**Rollback Strategy:** Set `FEATURE_FIREBASE_PROXY = false` in staging. Re-push to develop.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (clean context before refactor phase)
- Required files: `app/build.gradle.kts`
- Architecture docs to reference: None
- Documents NOT required: All

---

## Detailed Tasks — Phase 4: Android App Refactor {#phase-4-tasks}

**Objective:** Replace the device-side auth infrastructure that is now redundant. Update `KiteConnectAuthInterceptor` to inject Firebase ID tokens instead of Kite access tokens (for the proxy layer). Update `SessionManager` to react to Firestore `tokenExpired` flag rather than HTTP 403 responses. Remove Kite API direct-call interceptors. Update `OrderSyncWorker` to use Firebase Auth tokens.

**Duration:** ~3 days
**Risk Level:** Medium — changes to auth infrastructure affect all API-dependent flows; WorkManager background sync must continue to function
**Depends On:** Phase 3 complete and stable; `FEATURE_FIREBASE_PROXY = true` in staging

**Completion Criteria:**

- `KiteConnectAuthInterceptor` updated to inject Firebase ID tokens (used for the Cloud Functions HTTPS calls, not directly to Kite).
- `SessionManager` observes Firestore `tokenExpired` flag rather than `TokenExpiredInterceptor`.
- `OrderSyncWorker` obtains Firebase ID token for background calls.
- All feature screens unaffected (no ViewModel or UI changes).
- Debug build with `FEATURE_FIREBASE_AUTH = false` still compiles and runs.

---

### FM-020 — Android: FirebaseIdTokenInterceptor (Replaces KiteConnectAuthInterceptor for Proxy Calls)

**Phase:** 4 — Android App Refactor
**Subsystem:** `:core-network`

**Description:**
Implement `FirebaseIdTokenInterceptor` — an OkHttp interceptor that adds the Firebase ID token as a Bearer token header to requests destined for Firebase Cloud Functions. This interceptor is NOT used for direct Kite API calls (which will be deleted in FM-034). It is added to the Firebase-specific `OkHttpClient` in `FirebaseModule`. The existing `KiteConnectAuthInterceptor` is not deleted in this task (still used when `FEATURE_FIREBASE_PROXY = false`).

**Scope Boundaries**

- Files affected: `infra/firebase/src/main/java/com/kitewatch/infra/firebase/interceptor/FirebaseIdTokenInterceptor.kt` (new), `infra/firebase/src/main/java/com/kitewatch/infra/firebase/di/FirebaseModule.kt` (update — add interceptor to Functions client)
- Modules affected: `:infra-firebase`
- Explicitly NOT touching: `KiteConnectAuthInterceptor`, `KiteConnectRateLimitInterceptor`, `TokenExpiredInterceptor`, `:core-network` module

**Implementation Steps**

1. Implement `FirebaseIdTokenInterceptor(private val firebaseAuthManager: FirebaseAuthManager)`:
   - `intercept(chain)`: call `firebaseAuthManager.getIdToken()`.
   - On success: proceed with request adding header `Authorization: Bearer {idToken}`.
   - On `AppError.AuthError.TokenExpired` (user not signed in): do not add header; proceed without auth (the Firebase SDK on the server side will reject with `unauthenticated` — the app's error handler will catch this and trigger re-auth).
   - On other errors: proceed without auth (defensive — never block a request on token fetch failure).
2. Note: Firebase Callable functions called via the Firebase Android SDK do NOT go through OkHttp interceptors — they use the Firebase SDK's own transport. `FirebaseIdTokenInterceptor` is intended for any direct REST calls to Firebase (e.g., if future tasks use REST endpoints instead of Callable functions). Document this explicitly in the class Kdoc.
3. Add `FirebaseIdTokenInterceptor` to `FirebaseModule` as a singleton binding.
4. Write unit tests: (a) `getIdToken()` returns token → `Authorization: Bearer ...` header present; (b) `getIdToken()` returns `TokenExpired` → no auth header, request proceeds.

**User Action Steps (Manual Execution)**

None — fully automated implementation.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: both cases above. Confirm no header added when unauthenticated.

**Acceptance Criteria**

- `FirebaseIdTokenInterceptor` compiles and is registered in `FirebaseModule`.
- Auth header added when `FirebaseAuthManager.getIdToken()` succeeds.
- Request proceeds (not blocked) when token unavailable.
- Unit tests pass.

**Rollback Strategy:** Remove `FirebaseIdTokenInterceptor` binding from `FirebaseModule`. No existing interceptors affected.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Interceptor follows established OkHttp pattern; identical structure to `KiteConnectAuthInterceptor`.

**Context Strategy**

- Start new chat? No
- Required files: `core/network/src/main/java/com/kitewatch/network/kiteconnect/interceptor/KiteConnectAuthInterceptor.kt`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseAuthManager.kt`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/di/FirebaseModule.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-021 — Android: SessionManager Update (Firestore Token Expiry Observer)

**Phase:** 4 — Android App Refactor
**Subsystem:** `:infra-firebase` / `:infra-auth`

**Description:**
Update `SessionManager` (or implement `FirebaseSessionManager`) to observe the `tokenExpired` boolean field in Firestore `users/{uid}/session/data` using a real-time Firestore listener. When `tokenExpired` transitions to `true`, the manager emits `ReAuthRequiredEvent` exactly as it currently does when `TokenExpiredInterceptor` fires. This replaces the HTTP-layer session expiry detection with a Firestore-based push notification. The existing `TokenExpiredInterceptor` and `SessionExpiredFlow` remain in place while `FEATURE_FIREBASE_PROXY = false`.

**Scope Boundaries**

- Files affected: `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseSessionManager.kt` (new), `infra/auth/src/main/java/com/kitewatch/infra/auth/SessionManager.kt` (update to delegate to `FirebaseSessionManager` when `FEATURE_FIREBASE_PROXY = true`), `app/src/main/java/com/kitewatch/app/KiteWatchApplication.kt` (update `observe()` call)
- Modules affected: `:infra-firebase`, `:infra-auth`, `:app`
- Explicitly NOT touching: `TokenExpiredInterceptor`, `KiteConnectApiService`, feature ViewModels

**Implementation Steps**

1. Implement `FirebaseSessionManager`:
   - Constructor: `FirebaseAuthManager`, `FirebaseFirestore`, application `CoroutineScope`.
   - `fun observe()`: launches a coroutine that listens to `getUserSessionRef(uid).snapshots()`. On each snapshot: if `tokenExpired == true`, emit `ReAuthRequiredEvent` via a `SharedFlow`.
   - `val reAuthRequired: SharedFlow<Unit>`.
   - `fun clearTokenExpired()`: writes `tokenExpired: false` to Firestore (called after user re-authenticates).
   - Handle `currentUser == null` (not signed in): stop Firestore listener silently.
2. In `SessionManager.kt`, add a delegation check: if `BuildConfig.FEATURE_FIREBASE_PROXY` is `true`, delegate `observe()` to `FirebaseSessionManager.observe()` and merge its `reAuthRequired` flow with the existing `sessionExpiredFlow`.
3. In `KiteWatchApplication.onCreate()`, call `FirebaseSessionManager.observe()` in the application scope when `FEATURE_FIREBASE_PROXY = true`.
4. Write unit tests for `FirebaseSessionManager`: Firestore snapshot with `tokenExpired = false` → no event emitted; snapshot with `tokenExpired = true` → `ReAuthRequiredEvent` emitted.

**User Action Steps (Manual Execution)**

None — automated. Integration tested manually in FM-019 checkpoint.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: both Firestore snapshot cases above using mocked Firestore snapshots.
- Manual: set `tokenExpired: true` in Firestore → observe re-auth screen appears in app within 2 seconds (Firestore real-time listener latency).

**Acceptance Criteria**

- `FirebaseSessionManager.observe()` emits `ReAuthRequiredEvent` within 5 seconds of `tokenExpired: true` appearing in Firestore.
- `clearTokenExpired()` resets `tokenExpired: false` after re-auth.
- Existing `TokenExpiredInterceptor` path remains functional when `FEATURE_FIREBASE_PROXY = false`.
- Unit tests pass.

**Rollback Strategy:** Remove `FirebaseSessionManager` observer registration from `KiteWatchApplication`. The `TokenExpiredInterceptor` path handles session expiry as before.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Firestore real-time listener pattern is well-documented; the `SharedFlow` emission pattern mirrors the existing `SessionManager` design.

**Context Strategy**

- Start new chat? No
- Required files: `infra/auth/src/main/java/com/kitewatch/infra/auth/SessionManager.kt`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseAuthManager.kt`, `app/src/main/java/com/kitewatch/app/KiteWatchApplication.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-022 — Android: OrderSyncWorker Update for Firebase Auth

**Phase:** 4 — Android App Refactor
**Subsystem:** `:infra-worker`

**Description:**
Update `OrderSyncWorker` to obtain a Firebase ID token before executing the sync, when `FEATURE_FIREBASE_PROXY = true`. The sync use case (`SyncOrdersUseCase`) itself is unchanged — it calls `KiteConnectRepository` methods which are now backed by `FirebaseKiteRepository`. The worker update is limited to ensuring `FirebaseAuth.currentUser` is non-null before proceeding and handling the case where the user is signed out (return `Result.failure()`).

**Scope Boundaries**

- Files affected: `infra/worker/src/main/java/com/kitewatch/infra/worker/OrderSyncWorker.kt`
- Modules affected: `:infra-worker`
- Explicitly NOT touching: `SyncOrdersUseCase`, `ChargeRateSyncWorker`, `WorkSchedulerRepository`, domain engines

**Implementation Steps**

1. Add `FirebaseAuthManager` as a constructor-injected parameter in `OrderSyncWorker` (Hilt `@AssistedInject` pattern — already used for WorkManager workers).
2. At the top of `doWork()`, when `BuildConfig.FEATURE_FIREBASE_PROXY = true`:
   - Call `firebaseAuthManager.getIdToken()` (forces a token refresh if needed).
   - If result is `AppError.AuthError.TokenExpired` (user signed out): log warning via `Timber.w`; return `Result.failure()` with a `workDataOf("error" to "USER_SIGNED_OUT")`.
   - If token obtained: proceed with existing sync logic (no change to the sync path itself).
3. Update `WorkerModule` (or equivalent Hilt worker factory) to inject `FirebaseAuthManager` into `OrderSyncWorker`.
4. Write unit tests: (a) `FEATURE_FIREBASE_PROXY = false` → `getIdToken()` not called; (b) `FEATURE_FIREBASE_PROXY = true`, user signed in → `getIdToken()` called, sync proceeds; (c) `FEATURE_FIREBASE_PROXY = true`, user signed out → `Result.failure()` returned immediately.

**User Action Steps (Manual Execution)**

None — automated.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: all three cases above. Mock `FirebaseAuthManager`.
- Manual: trigger `OrderSyncWorker` manually via WorkManager → observe successful sync with Firebase proxy active.

**Acceptance Criteria**

- `OrderSyncWorker` returns `Result.failure()` (not retry) when user is signed out in Firebase.
- `OrderSyncWorker` proceeds with sync when Firebase user is signed in.
- `getIdToken()` not called when `FEATURE_FIREBASE_PROXY = false`.
- Unit tests pass.
- Existing `SyncOrdersUseCase` tests are unaffected.

**Rollback Strategy:** Revert `OrderSyncWorker.kt` changes. Worker returns to its pre-FM-022 state. `FEATURE_FIREBASE_PROXY = false` prevents the new code paths from executing.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Minimal change to an existing worker; defensive guard added before existing logic.

**Context Strategy**

- Start new chat? No
- Required files: `infra/worker/src/main/java/com/kitewatch/infra/worker/OrderSyncWorker.kt`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseAuthManager.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-023 — Android: AppLockStateManager Update for Firebase Sign-Out

**Phase:** 4 — Android App Refactor
**Subsystem:** `:infra-auth` / `:infra-firebase`

**Description:**
Update the account reset flow (accessible from Settings → Reset Account) to also sign out of Firebase Auth when `FEATURE_FIREBASE_AUTH = true`. Currently, `CredentialStore.clearAll()` is called on account reset. After migration, `FirebaseAuthManager.signOut()` must also be called so the Firebase UID is cleared. Additionally, the `AppLockStateManager` biometric lock must re-lock when the Firebase session is invalidated (Firestore `tokenExpired: true`).

**Scope Boundaries**

- Files affected: `infra/auth/src/main/java/com/kitewatch/infra/auth/AppLockStateManager.kt`, `feature/settings` reset use case or ViewModel (update `clearAll` call site to include Firebase sign-out)
- Modules affected: `:infra-auth`, `:feature-settings`
- Explicitly NOT touching: `BiometricAuthManager` internals, `MasterKeyProvider`, SQLCipher passphrase

**Implementation Steps**

1. In the account reset flow (locate the call to `CredentialStore.clearAll()` or `AccountBindingRepository.clear()` in `feature/settings`): add a call to `FirebaseAuthManager.signOut()` when `BuildConfig.FEATURE_FIREBASE_AUTH = true`.
2. In `AppLockStateManager`, add an observer for `FirebaseSessionManager.reAuthRequired`: when `ReAuthRequiredEvent` is received, call `biometricAuthManager.lockNow()` in addition to the normal session expiry handling. This ensures the biometric lock is re-applied when the Kite session expires.
3. Write unit tests: account reset → `FirebaseAuthManager.signOut()` called (mocked); `ReAuthRequiredEvent` received → `biometricAuthManager.lockNow()` called.

**User Action Steps (Manual Execution)**

None — automated.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Unit: both cases above.
- Manual: trigger account reset from Settings → confirm `FirebaseAuth.currentUser` is null after reset.

**Acceptance Criteria**

- `FirebaseAuthManager.signOut()` called on account reset when `FEATURE_FIREBASE_AUTH = true`.
- `biometricAuthManager.lockNow()` called when `ReAuthRequiredEvent` received.
- Unit tests pass.

**Rollback Strategy:** Revert the call site additions. Sign-out and lock behaviour returns to pre-FM-023 state.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Small targeted additions to two existing classes.

**Context Strategy**

- Start new chat? No
- Required files: `infra/auth/src/main/java/com/kitewatch/infra/auth/AppLockStateManager.kt`, the account reset use case or ViewModel in `feature/settings`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseAuthManager.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-024 — Phase 4 Validation Milestone

**Phase:** 4 — Android App Refactor
**Subsystem:** All

**Description:**
Execute the Phase 4 checkpoint checklist. Confirm all Android refactoring tasks are complete and integrated. Confirm `debug` build still works with both flags `false` (old paths). Confirm `staging` build works with both flags `true` (new paths).

**Scope Boundaries**

- Files affected: None (validation task only)
- Modules affected: None

**Implementation Steps**

1. `./gradlew :infra-firebase:testDebugUnitTest` — all FM-020 to FM-023 unit tests pass.
2. `./gradlew assembleDebug` — debug APK with both flags `false`; install and manually verify old onboarding path works.
3. `./gradlew assembleStagingRelease` — staging APK with both flags `true`; install and verify full flow.
4. Staging checklist:
   - Google Sign-In → Firebase user authenticated.
   - Kite OAuth via `exchangeKiteToken` → session active.
   - Portfolio/Holdings/Orders/Transactions screens load data.
   - GTT screen shows active GTTs.
   - Create one test GTT → visible in Kite console.
   - Trigger WorkManager sync manually → `OrderSyncWorker` completes `Result.success()`.
   - Set `tokenExpired: true` in Firestore → re-auth screen appears → complete Kite OAuth → `tokenExpired: false` → screens reload.
   - Account reset → `FirebaseAuth.currentUser` is null → app shows onboarding.
5. Push to `develop` → CI passes.

**Acceptance Criteria**

- All checklist items pass.
- Zero P0/P1 bugs before Phase 5.
- Both `debug` (old paths) and `staging` (new paths) APKs are functional.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (clean context before token cleanup)
- Required files: None
- Architecture docs to reference: None
- Documents NOT required: All

---

## Detailed Tasks — Phase 5: Token Migration and Cleanup {#phase-5-tasks}

**Objective:** Remove all on-device Kite token and secret storage infrastructure now that the Firebase proxy path is the only active path in `staging` and `release` builds. Purge `kite_access_token`, `kite_api_key` (secret usage), and `KITE_API_SECRET` from `CredentialStore`. Update `AccountBindingStore` to store only the Firebase UID and Kite username. This phase has no fallback — it must only begin after Phase 4 is fully stable.

**Duration:** ~2 days
**Risk Level:** Medium — removal of existing infrastructure; must not regress the fallback debug path inadvertently
**Depends On:** Phase 4 complete and stable; `FEATURE_FIREBASE_PROXY = true` and `FEATURE_FIREBASE_AUTH = true` validated in staging

**Completion Criteria:**

- `CredentialStore.saveAccessToken()`, `getAccessToken()`, and all Kite-secret storage methods removed.
- `kite_access_token` key absent from `EncryptedSharedPreferences` on any newly onboarded device.
- `AccountBindingStore` stores only `{ firebaseUid, kiteUserId, kiteUserName, boundAt }` — no `apiKey` or `apiSecret`.
- `KiteConnectAuthInterceptor` reads no token from `CredentialStore` (it is removed in FM-034; in this phase its dependency on `CredentialStore` is severed).

---

### FM-025 — Android: Remove Kite Access Token from CredentialStore

**Phase:** 5 — Token Migration and Cleanup
**Subsystem:** `:infra-auth`

**Description:**
Remove all methods from `CredentialStore` that store, read, or clear the Kite `access_token`. The `kite_access_token` key must no longer be written to `EncryptedSharedPreferences` by any code path. Add a one-time migration step that deletes the `kite_access_token` key from existing `EncryptedSharedPreferences` on app upgrade, so that tokens from before the migration are wiped from existing installations.

**Scope Boundaries**

- Files affected: `infra/auth/src/main/java/com/kitewatch/infra/auth/CredentialStore.kt`, `app/src/main/java/com/kitewatch/app/KiteWatchApplication.kt` (add one-time migration call)
- Modules affected: `:infra-auth`, `:app`
- Explicitly NOT touching: `MasterKeyProvider`, `AccountBindingStore`, `BiometricAuthManager`, SQLCipher passphrase storage

**Implementation Steps**

1. In `CredentialStore.kt`:
   - Remove `fun saveAccessToken(token: String)`.
   - Remove `fun getAccessToken(): String?`.
   - Remove `fun clearAccessToken()`.
   - Remove constant `KEY_ACCESS_TOKEN = "kite_access_token"`.
   - Verify all call sites of these methods: `KiteConnectAuthInterceptor` (will be deleted in FM-034 — add `@Suppress("DEPRECATION")` comment for now), `BindAccountUseCase.executeDeviceSide()` (the deprecated path — add compile-time guard `if (!BuildConfig.FEATURE_FIREBASE_AUTH)`).
2. Add `fun migrateRemoveLegacyKiteToken()` to `CredentialStore`:
   - Calls `encryptedPrefs.edit().remove("kite_access_token").apply()`.
   - Safe to call multiple times (idempotent).
3. In `KiteWatchApplication.onCreate()`, call `credentialStore.migrateRemoveLegacyKiteToken()` unconditionally (runs on every launch for one release, then the call is a no-op since the key is absent).
4. Update `CredentialStoreTest`: remove tests for the deleted methods. Add test for `migrateRemoveLegacyKiteToken()`: insert `kite_access_token` key manually → call method → confirm key absent.

**User Action Steps (Manual Execution)**

None — automated. The migration runs on app launch.

**Data Impact**

- Schema changes: None (Room database unaffected)
- Migration required: EncryptedSharedPreferences key `kite_access_token` deleted on upgrade (non-destructive — the token is no longer needed)

**Test Plan**

- Unit: `migrateRemoveLegacyKiteToken()` removes the key. Confirm `CredentialStore` no longer has `saveAccessToken` / `getAccessToken` methods (compile-time verification).
- Manual: install the pre-migration APK, onboard, confirm `kite_access_token` exists in prefs. Update to the post-migration APK → confirm key deleted on first launch.

**Acceptance Criteria**

- `CredentialStore` contains no `access_token` methods.
- `kite_access_token` key removed from `EncryptedSharedPreferences` on first app launch after upgrade.
- `./gradlew assembleDebug assembleStagingRelease` both pass.
- `CredentialStoreTest` passes (no reference to deleted methods).

**Rollback Strategy:** Re-add `saveAccessToken`, `getAccessToken`, `clearAccessToken` to `CredentialStore`. Remove `migrateRemoveLegacyKiteToken()` call from `KiteWatchApplication`. The token will not be re-populated automatically — the user would need to re-authenticate on the Retrofit path.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Method removal and idempotent migration step; no new logic.

**Context Strategy**

- Start new chat? Yes (new phase: token cleanup)
- Required files: `infra/auth/src/main/java/com/kitewatch/infra/auth/CredentialStore.kt`, `app/src/main/java/com/kitewatch/app/KiteWatchApplication.kt`
- Architecture docs to reference: `07_SECURITY_MODEL.md` (token storage section, for reference only)
- Documents NOT required: All others

---

### FM-026 — Android: Remove Kite API Secret from Onboarding and CredentialStore

**Phase:** 5 — Token Migration and Cleanup
**Subsystem:** `:infra-auth` / `:core-domain`

**Description:**
Remove all remaining infrastructure that stores or processes `KITE_API_SECRET` on the device. This includes: removing the `apiSecret` parameter from `BindAccountUseCase.executeDeviceSide()` entirely (the method itself is deprecated and will be deleted in FM-034), removing any `saveApiSecret()` / `getApiSecret()` methods from `CredentialStore` if present, and removing the `apiSecret` field from `AccountBindingStore` if stored there.

**Scope Boundaries**

- Files affected: `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountUseCase.kt`, `infra/auth/src/main/java/com/kitewatch/infra/auth/CredentialStore.kt`, `infra/auth/src/main/java/com/kitewatch/infra/auth/AccountBindingStore.kt`
- Modules affected: `:core-domain`, `:infra-auth`
- Explicitly NOT touching: `MasterKeyProvider`, `BiometricAuthManager`, Firebase modules

**Implementation Steps**

1. In `CredentialStore.kt`: search for any `saveApiSecret`, `getApiSecret`, `clearApiSecret`, or `KEY_API_SECRET` definitions → remove all. Confirm none exist (the audit in Part 1 §3.1 identified this as a risk path during onboarding but the key may not have been persisted — verify by reading the actual source).
2. In `AccountBindingStore.kt`: inspect all stored keys. If `bound_api_key` or any secret-equivalent key is present: remove the `saveApiKey()` method and the `KEY_BOUND_API_KEY` constant. Add `migrateRemoveBoundApiKey()` (same idempotent pattern as FM-025) and call it from `KiteWatchApplication.onCreate()`.
3. In `BindAccountUseCase.executeDeviceSide()`: since `apiSecret` is still a parameter of the deprecated method (used in `debug` builds with `FEATURE_FIREBASE_AUTH = false`), add a `@Deprecated` `@Throws` annotation with message "Firebase path does not use apiSecret. Remove in FM-034." Leave the parameter in place for now — it is removed with the method in FM-034.
4. Update all unit tests to remove references to API secret storage.

**User Action Steps (Manual Execution)**

None — automated.

**Data Impact**

- Schema changes: None
- Migration required: EncryptedSharedPreferences `bound_api_key` key deleted on upgrade (if it exists)

**Test Plan**

- Compile-time: `grep -r "apiSecret\|API_SECRET\|api_secret" infra/auth/src/` — zero hits expected (except deprecated `executeDeviceSide` parameter).
- Unit: `AccountBindingStoreTest` confirms `bound_api_key` is absent after migration call.

**Acceptance Criteria**

- No `apiSecret` storage methods remain in `CredentialStore` or `AccountBindingStore`.
- `bound_api_key` deleted from `EncryptedSharedPreferences` on upgrade.
- `grep -r "apiSecret\|KEY_API_SECRET\|bound_api_key" infra/auth/src/` returns zero matches (excluding deprecated method signature).
- All unit tests pass.

**Rollback Strategy:** Re-add removed methods to `CredentialStore` and `AccountBindingStore`. The deprecated path in `executeDeviceSide` is unchanged.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Method removal and audit; no new logic.

**Context Strategy**

- Start new chat? No
- Required files: `infra/auth/src/main/java/com/kitewatch/infra/auth/CredentialStore.kt`, `infra/auth/src/main/java/com/kitewatch/infra/auth/AccountBindingStore.kt`, `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountUseCase.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-027 — Android: Update AccountBindingStore to Store Firebase UID

**Phase:** 5 — Token Migration and Cleanup
**Subsystem:** `:infra-auth`

**Description:**
Update `AccountBindingStore` to add the Firebase UID as a stored binding field and remove the Kite `apiKey` from the stored binding. After this task, `AccountBindingStore` holds: `{ firebaseUid, kiteUserId, kiteUserName, boundAt }`. The `apiKey` is no longer stored on device — it is stored in Firestore `users/{uid}/profile`. This change propagates to `AccountBinding` domain model and `AccountBindingRepository`.

**Scope Boundaries**

- Files affected: `infra/auth/src/main/java/com/kitewatch/infra/auth/AccountBindingStore.kt`, `core/domain/src/main/java/com/kitewatch/domain/model/AccountBinding.kt`, `core/data/src/main/java/com/kitewatch/data/repository/AccountBindingRepositoryImpl.kt`
- Modules affected: `:infra-auth`, `:core-domain`, `:core-data`
- Explicitly NOT touching: Room `AccountBindingEntity` (schema change out of scope — the Room entity stores the Kite user ID and name, which are unchanged), feature modules

**Implementation Steps**

1. In `AccountBinding.kt` (domain model): add `firebaseUid: String` field. Mark `apiKey: String` as `@Deprecated` with removal target FM-034. Both coexist temporarily.
2. In `AccountBindingStore.kt`:
   - Add constant `KEY_FIREBASE_UID = "firebase_uid"`.
   - Add `fun saveFirebaseUid(uid: String)` / `fun getFirebaseUid(): String?`.
   - Retain `KEY_BOUND_API_KEY` and its read method as a deprecated read-only getter (existing installations may still have it populated until FM-026 migration runs; FM-026 deletes the write path).
3. In `AccountBindingRepositoryImpl.kt`: update `bind(accountBinding)` to call `accountBindingStore.saveFirebaseUid(accountBinding.firebaseUid)`. Update `getBinding()` to populate `AccountBinding.firebaseUid` from the store.
4. Update `BindAccountUseCase.completeFirebaseBind()` (from FM-011): populate `AccountBinding.firebaseUid` with `FirebaseAuthManager.currentUser?.uid` when constructing the `AccountBinding` result.
5. Update unit tests for `AccountBindingRepositoryImpl`: verify `firebaseUid` is saved and retrieved correctly.

**User Action Steps (Manual Execution)**

None — automated.

**Data Impact**

- Schema changes: None (Room `account_binding` table adds no new columns — `firebaseUid` is stored in `EncryptedSharedPreferences`, not Room)
- Migration required: No

**Test Plan**

- Unit: `AccountBindingRepositoryImpl` bind/retrieve round-trip includes `firebaseUid`.
- Manual: onboard on staging APK → check `EncryptedSharedPreferences` `firebase_uid` key is populated.

**Acceptance Criteria**

- `AccountBinding` domain model has `firebaseUid` field.
- `AccountBindingStore` saves and retrieves `firebaseUid`.
- `AccountBindingRepositoryImpl` populates `firebaseUid` on bind.
- Unit tests pass.
- Room schema version does NOT change (this is not a database migration).

**Rollback Strategy:** Revert `AccountBindingStore.kt`, `AccountBinding.kt`, and `AccountBindingRepositoryImpl.kt`. The `firebaseUid` field is additive; reverting removes it with no data loss to the Room database.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Additive field to an existing model and store; no new logic.

**Context Strategy**

- Start new chat? No
- Required files: `infra/auth/src/main/java/com/kitewatch/infra/auth/AccountBindingStore.kt`, `core/domain/src/main/java/com/kitewatch/domain/model/AccountBinding.kt`, `core/data/src/main/java/com/kitewatch/data/repository/AccountBindingRepositoryImpl.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-028 — Phase 5 Validation: Token Absence Audit

**Phase:** 5 — Token Migration and Cleanup
**Subsystem:** All

**Description:**
Execute a targeted security audit to confirm that no Kite tokens or secrets remain on the device after the Phase 5 changes. Verify via APK analysis and on-device runtime inspection. This is a verification-only task — no new code.

**Scope Boundaries**

- Files affected: None (verification task only)
- Modules affected: None

**Implementation Steps**

1. Build `stagingRelease` APK: `./gradlew assembleStagingRelease`.
2. Run APK string extraction: `apktool d app-staging-release.apk -o apktool_out`. Run `grep -rn "KITE_API_SECRET\|kite_access_token\|api_secret" apktool_out/` — expect zero matches.
3. Search for `access_token` string literals in the APK resources and smali code — expect zero matches in any financial or authentication context.
4. On a rooted emulator: install staging APK, complete onboarding. Run `adb shell run-as com.kitewatch.app cat shared_prefs/kitewatch_secrets.xml` — confirm no `kite_access_token` or `api_secret` keys.
5. Confirm `BuildConfig.KITE_API_KEY` is present (expected — public identifier) and `BuildConfig.KITE_API_SECRET` is absent (verified by `grep "KITE_API_SECRET" BuildConfig.java` → zero matches).
6. Confirm Firestore `users/{uid}/session/data.accessToken` is present (correct — this is the server-side location).
7. Document findings in a brief audit note committed to `docs/SECURITY_AUDIT_MIGRATION_CHECKPOINT.md`.

**User Action Steps (Manual Execution)**

1. Run all `apktool` and `grep` commands above on the latest staging APK.
2. On rooted emulator: inspect `EncryptedSharedPreferences` file.
3. Write and commit the audit checkpoint document.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Static: APK string scan for secret keywords → zero matches.
- Runtime: `EncryptedSharedPreferences` key audit on rooted device.

**Acceptance Criteria**

- `KITE_API_SECRET` absent from APK binary (all variants).
- `kite_access_token` key absent from `EncryptedSharedPreferences` post-migration.
- `KITE_API_KEY` present in `BuildConfig` (expected; documented as acceptable in §3.4 of Part 1).
- Audit checkpoint document committed.
- Zero blocking findings before Phase 6.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (new phase: security hardening)
- Required files: None
- Architecture docs to reference: `07_SECURITY_MODEL.md`
- Documents NOT required: All others

---

## Detailed Tasks — Phase 6: Security Hardening {#phase-6-tasks}

**Objective:** Harden the Firebase layer against abuse and misuse. Enforce per-user data isolation at every layer. Apply rate limiting to Cloud Functions. Update Android network security configuration for Firebase endpoints. Audit and remove any remaining direct Kite API call infrastructure from the APK.

**Duration:** ~2 days
**Risk Level:** High — security configurations must not introduce regressions or lock out legitimate users
**Depends On:** Phase 5 complete; token audit passed; APK binary is clean

**Completion Criteria:**

- Firestore Security Rules tested and confirmed with multi-user isolation.
- Cloud Functions reject >10 requests/minute per UID (rate limiting active).
- `network_security_config.xml` updated for Firebase domains.
- `KiteConnectCertificatePinner` scope updated to exclude device-level pinning for `api.kite.trade` (now server-side only).
- ProGuard rules updated to keep Firebase classes.
- All security hardening tests pass.

---

### FM-029 — Cloud Functions: Rate Limiting and Abuse Prevention

**Phase:** 6 — Security Hardening
**Subsystem:** Cloud Functions / Security

**Description:**
Implement per-UID rate limiting in Cloud Functions to prevent abuse of the Kite API proxy. Use Firestore-based counters (token bucket pattern) to limit each user to a maximum of 10 Cloud Function proxy calls per minute. Exempt `exchangeKiteToken` from per-minute rate limiting (it is naturally rate-limited by the Kite OAuth flow) but apply a per-hour limit of 5 to prevent token exchange abuse.

**Scope Boundaries**

- Files affected: `functions/src/middleware/rateLimiter.ts` (new), `functions/src/proxy/orders.ts`, `functions/src/proxy/holdings.ts`, `functions/src/proxy/fundBalance.ts`, `functions/src/proxy/gtt.ts` (add rate limiting call to each), `functions/src/auth/exchangeKiteToken.ts` (add hourly limit)
- Modules affected: None (Android unaffected)
- Explicitly NOT touching: Firestore Security Rules (separate from rate limiter), Android app code

**Implementation Steps**

1. Implement `functions/src/middleware/rateLimiter.ts`:
   - `export async function checkRateLimit(uid: string, limitKey: string, maxCalls: number, windowSeconds: number): Promise<void>` — uses a Firestore document `users/{uid}/rateLimits/{limitKey}` to store call count and window start timestamp.
   - On each call: read the document. If window has expired (now > windowStart + windowSeconds): reset count to 1, update windowStart. If within window: increment count. If count exceeds `maxCalls`: throw `HttpsError("resource-exhausted", "Rate limit exceeded. Please wait before retrying.")`.
   - Use Firestore transactions for atomic read-modify-write.
2. Add `await checkRateLimit(uid, "proxy", 10, 60)` at the top of each proxy function (after `getAuthenticatedUid` and before `getAccessToken`).
3. Add `await checkRateLimit(uid, "tokenExchange", 5, 3600)` at the top of `exchangeKiteToken`.
4. Add `users/{uid}/rateLimits/{document=**}` to Firestore Security Rules with server-only write access (only Cloud Functions via Admin SDK can write rate limit documents — client SDK cannot).
5. Write unit tests for `rateLimiter`:
   - First call within window → proceeds.
   - 10th call within window → proceeds.
   - 11th call within window → `resource-exhausted` error.
   - Call after window expires → count resets, proceeds.

**User Action Steps (Manual Execution)**

1. Deploy updated functions: `firebase deploy --only functions`.
2. Test rate limiting by calling `getOrders` 11 times in rapid succession via Firebase console → confirm 11th call returns `resource-exhausted`.

**Data Impact**

- Schema changes: Adds `users/{uid}/rateLimits/` subcollection in Firestore
- Migration required: No

**Test Plan**

- Unit: all four cases above.
- Manual: rate limit triggered after 10 rapid calls.

**Acceptance Criteria**

- 11th proxy call within 60 seconds returns `resource-exhausted`.
- 6th `exchangeKiteToken` call within 1 hour returns `resource-exhausted`.
- Rate limit resets after the window expires.
- Firestore Security Rules block client-SDK writes to `rateLimits/` documents.
- Unit tests pass.

**Rollback Strategy:** Remove `checkRateLimit` calls from all functions. Re-deploy. Rate limiting is disabled; no other functionality affected.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Token bucket pattern with Firestore transactions is a well-documented pattern; limits are straightforward constants.

**Context Strategy**

- Start new chat? No
- Required files: `functions/src/utils/firestoreUtils.ts`, `functions/src/middleware/authMiddleware.ts`, `functions/src/proxy/orders.ts`, `firestore.rules`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-030 — Firestore Security Rules: Hardening and Multi-User Isolation Test

**Phase:** 6 — Security Hardening
**Subsystem:** Firestore / Security

**Description:**
Extend the Firestore Security Rules established in FM-005 with additional rules that prevent client-side writes to security-sensitive fields (`accessToken`, `tokenExpired`), enforce that `rateLimits/` is server-write only, and confirm that the `session/data` document's `accessToken` field cannot be read by the client SDK. Add a `firestore.test.ts` test suite using the Firebase emulator that verifies all isolation and write-restriction rules.

**Scope Boundaries**

- Files affected: `firestore.rules` (update), `firestore.test.ts` (new — at repository root alongside `firebase.json`), `package.json` (root — add test script)
- Modules affected: None (Android unaffected)
- Explicitly NOT touching: Cloud Functions logic, Android source code, rate limiter implementation

**Implementation Steps**

1. Update `firestore.rules`:
   - Add explicit rule: `match /users/{userId}/session/{doc}` — `allow read: if request.auth != null && request.auth.uid == userId && !("accessToken" in resource.data)` — this prevents the Android app from reading `accessToken` directly. (The app does not need to read `accessToken` — it calls Cloud Functions instead.)
   - Actually: since `session/data` contains `tokenExpired` (which the app DOES need to read via Firestore listener), separate the document: create `users/{uid}/sessionFlags/data = { tokenExpired, lastSyncedAt }` for client read access, and keep `users/{uid}/session/data = { accessToken }` as server-write-only (no client read).
   - Update `firestoreSchema.ts` and `firestoreUtils.ts` to use `sessionFlags` for the `tokenExpired` field.
   - Rule for `session/`: `allow read: if false;` — Admin SDK only.
   - Rule for `sessionFlags/`: `allow read: if request.auth != null && request.auth.uid == userId; allow write: if false;` — client can read, only Admin SDK writes.
   - Rule for `rateLimits/`: `allow read, write: if false;` — Admin SDK only.
   - Rule for `profile/`: `allow read: if request.auth != null && request.auth.uid == userId; allow write: if false;` — client reads only, Admin SDK writes.
2. Update `functions/src/utils/firestoreUtils.ts` to expose `getUserSessionFlagsRef(uid)` pointing to `users/{uid}/sessionFlags/data`.
3. Update `FM-021`'s `FirebaseSessionManager` to observe `getUserSessionFlagsRef(uid)` instead of `getUserSessionRef(uid)`.
4. Write `firestore.test.ts` test suite:
   - User A cannot read `users/uid-b/session/data`.
   - User A cannot read `users/uid-b/sessionFlags/data`.
   - User A can read `users/uid-a/sessionFlags/data` (own flags).
   - User A cannot write `users/uid-a/sessionFlags/data` (server-write only).
   - User A cannot read `users/uid-a/session/data` (token is server-only).
   - User A cannot write `users/uid-a/rateLimits/proxy` (rate limit is server-only).
   - Unauthenticated user cannot read any document.
5. Deploy updated rules: `firebase deploy --only firestore:rules`.

**User Action Steps (Manual Execution)**

1. Run `firebase emulators:start --only firestore` then `npm test` at project root to execute `firestore.test.ts`.
2. Deploy: `firebase deploy --only firestore:rules`.
3. Verify in Firebase console → Firestore → Rules → confirm updated rules are active.

**Data Impact**

- Schema changes: Adds `users/{uid}/sessionFlags/` document path in Firestore; moves `tokenExpired` from `session` to `sessionFlags`
- Migration required: Update existing Firestore data (Cloud Function `exchangeKiteToken` must write `tokenExpired` to `sessionFlags` going forward — update in same commit)

**Test Plan**

- Automated: `firestore.test.ts` all 7 isolation cases pass in emulator.
- Manual: Android app observes `tokenExpired` change from `sessionFlags` path (re-test FM-021 scenario).

**Acceptance Criteria**

- `users/{uid}/session/data.accessToken` is NOT readable by the Android client SDK.
- `users/{uid}/sessionFlags/data.tokenExpired` IS readable by the authenticated owner.
- All 7 `firestore.test.ts` cases pass.
- `FirebaseSessionManager` observes `sessionFlags` path successfully.
- Rules deployed to production Firestore.

**Rollback Strategy:** Revert `firestore.rules` to the FM-005 version. `firebase deploy --only firestore:rules`. Update `firestoreUtils.ts` to revert `sessionFlags` path back to `session`. The Android `FirebaseSessionManager` resumes observing the `session` path.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Thinking)
- Recommended Mode: Thinking
- Reason: Firestore Security Rules for partial-document field access restrictions and the split of `session` vs `sessionFlags` paths require careful reasoning to avoid locking out legitimate app reads while protecting the `accessToken` field.

**Context Strategy**

- Start new chat? No
- Required files: `firestore.rules`, `functions/src/utils/firestoreUtils.ts`, `functions/src/utils/firestoreSchema.ts`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseSessionManager.kt`
- Architecture docs to reference: `07_SECURITY_MODEL.md`
- Documents NOT required: All Android architecture documents, domain engine documents

---

### FM-031 — Android: Network Security Config Update for Firebase Domains

**Phase:** 6 — Security Hardening
**Subsystem:** `:app`

**Description:**
Update `network_security_config.xml` to include Firebase domains in the cleartext traffic prohibition and to remove `api.kite.trade` from the Android-level certificate pinning configuration (certificate pinning for `api.kite.trade` now occurs in the Cloud Functions server environment, not on the Android device). Ensure that `*.googleapis.com` and `*.cloudfunctions.net` are covered by the existing "no cleartext" rule. Add a comment documenting that certificate pinning for Firebase endpoints is handled by the Firebase SDK.

**Scope Boundaries**

- Files affected: `app/src/main/res/xml/network_security_config.xml`, `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectCertificatePinner.kt` (remove or comment out Android-side Kite pins)
- Modules affected: `:app`, `:core-network`
- Explicitly NOT touching: `OkHttpClient` configuration (pins are removed from the client builder in FM-034), Firebase SDK network configuration (managed by Firebase)

**Implementation Steps**

1. In `network_security_config.xml`: verify the existing configuration prohibits cleartext for all domains (it should already do so — confirm `<base-config cleartextTrafficPermitted="false">`). Add an XML comment: `<!-- Firebase SDK (*.googleapis.com, *.firebaseio.com, *.cloudfunctions.net) uses HTTPS only. Firebase SDK manages its own TLS validation. -->`.
2. Remove the `<pin-set>` block for `api.kite.trade` from `network_security_config.xml` if it is defined there. The Android-level SPKI pins for `api.kite.trade` are being replaced by server-side validation.
3. In `KiteConnectCertificatePinner.kt`: add a Kdoc comment: `@Deprecated("Kite API is no longer called directly from the device. This certificate pinner is inactive as of FM-031 and will be deleted in FM-034.")`. Do NOT delete the class in this task — FM-034 handles all deletion.
4. Confirm `OkHttpClient` for `:core-network` still references `KiteConnectCertificatePinner` (it does — this is fine; the client is still present and will be deleted in FM-034). The annotation is informational only.
5. Run `./gradlew assembleDebug assembleStagingRelease` — confirm both pass.

**User Action Steps (Manual Execution)**

None — automated build verification.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Manual: install staging APK on a device with a configured HTTP proxy (e.g., Charles Proxy). Attempt to intercept traffic to `*.googleapis.com` → HTTPS connection; no cleartext traffic.
- Confirm the app cannot be MITM'd on Firebase traffic (the Firebase SDK rejects connections to unknown CAs regardless of Android-level config).

**Acceptance Criteria**

- `network_security_config.xml` has no `api.kite.trade` pin set.
- `<base-config cleartextTrafficPermitted="false">` is present.
- `KiteConnectCertificatePinner` is annotated `@Deprecated`.
- Both APK builds pass.

**Rollback Strategy:** Revert `network_security_config.xml` to re-add the `api.kite.trade` pin set. Remove `@Deprecated` annotation from `KiteConnectCertificatePinner`.

**Estimated Complexity:** XS

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: XML configuration change and annotation; no logic.

**Context Strategy**

- Start new chat? No
- Required files: `app/src/main/res/xml/network_security_config.xml`, `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectCertificatePinner.kt`
- Architecture docs to reference: `07_SECURITY_MODEL.md` (certificate pinning section)
- Documents NOT required: All

---

### FM-032 — Android: ProGuard Rules Update for Firebase

**Phase:** 6 — Security Hardening
**Subsystem:** `:app`

**Description:**
Add ProGuard/R8 keep rules for all Firebase SDK classes to `app/proguard-rules.pro`. Validate the release APK with Firebase SDK under full R8 obfuscation — confirm no `ClassNotFoundException` on Firebase Auth initialisation, Firestore queries, or Cloud Functions calls. Remove ProGuard rules that are now redundant (Kite-specific rules that kept DTOs used only in direct API calls — these DTOs will be deleted in FM-034, but rules can be removed now without breakage since the `:core-network` DTOs are not yet deleted).

**Scope Boundaries**

- Files affected: `app/proguard-rules.pro`
- Modules affected: `:app`
- Explicitly NOT touching: Any source code, Firebase SDK version, R8 configuration

**Implementation Steps**

1. Add to `proguard-rules.pro`:
   - `-keep class com.google.firebase.** { *; }` (broad keep for Firebase SDK)
   - `-keep class com.google.android.gms.** { *; }` (Google Play Services)
   - `-dontwarn com.google.firebase.**`
   - `-keepattributes Signature` (required for Firebase Generics reflection)
   - `-keepattributes *Annotation*`
   - Comment indicating these rules added for Firebase SDK in FM-032.
2. Comment out (do not delete yet) the Kite DTO wildcard keep: `-keep class com.kitewatch.core.network.dto.** { *; }` → comment: `# To be removed in FM-034 after KiteConnectApiService is deleted.`
3. Run `./gradlew assembleRelease` — confirm zero R8 errors.
4. Install release APK on API 26 device. Open app → Firebase Auth initialises → no crash. Navigate to Portfolio screen → Cloud Function call succeeds → no `ClassNotFoundException`.

**User Action Steps (Manual Execution)**

1. Install release APK on a physical device running API 26 (minimum SDK).
2. Complete full onboarding flow with real Zerodha account.
3. Confirm Portfolio screen loads without crash or missing data.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Build: `./gradlew assembleRelease` zero R8 errors.
- Manual: release APK on API 26 completes full onboarding flow without crash.

**Acceptance Criteria**

- `./gradlew assembleRelease` succeeds with zero errors.
- Release APK on API 26 device: Firebase Auth initialises, Cloud Functions callable, no `ClassNotFoundException`.
- Kite DTO keep rule commented out (not deleted — FM-034 handles deletion).

**Rollback Strategy:** Revert `proguard-rules.pro` to previous state. R8 obfuscation may cause Firebase-related issues in release builds until reverted.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: ProGuard rule addition from Firebase documentation; release APK smoke test.

**Context Strategy**

- Start new chat? No
- Required files: `app/proguard-rules.pro`
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §2.2 (ProGuard validation process)
- Documents NOT required: All others

---

### FM-033 — Phase 6 Validation: Security Hardening Checklist

**Phase:** 6 — Security Hardening
**Subsystem:** All

**Description:**
Execute the complete Phase 6 security hardening checklist. This is a verification task — no new code. Confirms all hardening measures from FM-029 to FM-032 are correctly deployed and functional.

**Scope Boundaries**

- Files affected: `docs/SECURITY_AUDIT_MIGRATION_CHECKPOINT.md` (update with Phase 6 findings)
- Modules affected: None

**Implementation Steps**

1. Rate limiting: call `getOrders` 11 times in 60 seconds → confirm 11th call returns `resource-exhausted`.
2. Firestore isolation: `firestore.test.ts` all 7 tests pass (`firebase emulators:start && npm test`).
3. `session/data.accessToken` unreadable by client SDK (test in emulator: client SDK read of `session/data` → `PERMISSION_DENIED`).
4. `sessionFlags/data.tokenExpired` readable by authenticated owner (emulator test passes).
5. APK binary clean (from FM-028 — re-confirm on Phase 6 build): `KITE_API_SECRET` absent.
6. Release APK on API 26: Firebase initialises, no crash, Portfolio loads data.
7. `network_security_config.xml`: no `api.kite.trade` pin set present.
8. `KiteConnectCertificatePinner` has `@Deprecated` annotation.
9. `./gradlew assembleRelease` zero R8 errors.
10. Update `SECURITY_AUDIT_MIGRATION_CHECKPOINT.md` with all confirmations and sign off.

**Acceptance Criteria**

- All 10 checklist items confirmed and documented.
- Zero new blocking security findings.
- Phase 7 may begin.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (final phase: cleanup and validation)
- Required files: `docs/SECURITY_AUDIT_MIGRATION_CHECKPOINT.md`
- Architecture docs to reference: `07_SECURITY_MODEL.md`
- Documents NOT required: All others

---

## Detailed Tasks — Phase 7: Cleanup and Validation {#phase-7-tasks}

**Objective:** Delete all dead code that existed only to support the old direct-Kite-API path. Enable both `FEATURE_FIREBASE_AUTH` and `FEATURE_FIREBASE_PROXY` flags in the `release` build type. Execute the full test suite, performance check, and produce the final validated release APK. This phase is irreversible — once dead code is deleted and the release APK is produced, the migration is complete.

**Duration:** ~3 days
**Risk Level:** Low (systemic) — all issues found here are caught before the APK is distributed
**Depends On:** All previous phases complete; Phase 6 security checklist signed off

**Completion Criteria:**

- `KiteConnectApiService.kt` deleted.
- All Kite-specific OkHttp interceptors deleted.
- `BindAccountUseCase.executeDeviceSide()` deleted.
- `FEATURE_FIREBASE_AUTH = true` and `FEATURE_FIREBASE_PROXY = true` in `release` build type.
- `./gradlew assembleRelease` passes with zero warnings related to the migration.
- All unit and integration tests pass.
- Release APK passes security audit (no secrets in binary).

---

### FM-034 — Delete KiteConnectApiService and Kite-Specific Interceptors

**Phase:** 7 — Cleanup and Validation
**Subsystem:** `:core-network`

**Description:**
Delete the four files in `:core-network` that existed solely to make direct Kite API calls: `KiteConnectApiService.kt`, `KiteConnectAuthInterceptor.kt`, `TokenExpiredInterceptor.kt`, `KiteConnectRateLimitInterceptor.kt`. Delete `KiteConnectCertificatePinner.kt`. Remove all Kite-specific `OkHttpClient` and `Retrofit` bindings from `NetworkModule`. Delete the deprecated `BindAccountUseCase.executeDeviceSide()` method. Delete Kite response DTOs that are no longer used.

**Scope Boundaries**

- Files affected (DELETE):
  - `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectApiService.kt`
  - `core/network/src/main/java/com/kitewatch/network/kiteconnect/interceptor/KiteConnectAuthInterceptor.kt`
  - `core/network/src/main/java/com/kitewatch/network/kiteconnect/interceptor/TokenExpiredInterceptor.kt`
  - `core/network/src/main/java/com/kitewatch/network/kiteconnect/interceptor/KiteConnectRateLimitInterceptor.kt`
  - `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectCertificatePinner.kt`
- Files affected (MODIFY):
  - `core/network/src/main/java/com/kitewatch/network/di/NetworkModule.kt` (remove Kite `OkHttpClient` + `Retrofit` provisions; remove `KiteConnectApiService` provision)
  - `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountUseCase.kt` (remove `executeDeviceSide()`)
  - `app/proguard-rules.pro` (remove the now-uncommented Kite DTO keep rule — it was commented in FM-032)
- Modules affected: `:core-network`, `:core-domain`
- Explicitly NOT touching: Google API clients (`GmailApiClient`, `GoogleDriveApiClient`), `GoogleApiModule`, `:infra-firebase`, domain engines

**Implementation Steps**

1. Delete the five `:core-network` files listed above.
2. In `NetworkModule.kt`: remove all bindings that depended on the deleted files. Confirm `GoogleApiModule` Retrofit instance and `GmailApiClient` / `GoogleDriveApiClient` are untouched.
3. In `BindAccountUseCase.kt`: delete `fun executeDeviceSide(apiKey, requestToken, apiSecret)` entirely. Remove the `@Deprecated` annotation from `apiKey` field in `AccountBinding` (it is now the only path — but actually `apiKey` itself is also deprecated since it is stored in Firestore. Update `AccountBinding` to remove `apiKey` field entirely).
4. Delete the commented-out Kite DTO keep rule from `proguard-rules.pro`.
5. Run `./gradlew :core-network:testDebugUnitTest` — confirm no reference to deleted files.
6. Run `./gradlew assembleDebug` — confirm compilation succeeds.
7. Remove `FEATURE_FIREBASE_AUTH` and `FEATURE_FIREBASE_PROXY` checks that branch to the old (now-deleted) code paths — these `if` blocks now have a dead branch, which can be cleaned up by removing the `false` branch entirely.

**User Action Steps (Manual Execution)**

None — code deletion and build verification.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Build: `./gradlew assembleDebug assembleStagingRelease assembleRelease` — all pass.
- Unit: `./gradlew :core-network:testDebugUnitTest :core-domain:test` — all pass.
- Manual: install debug APK → app launches → Portfolio screen loads (via Firebase proxy) → no crash.

**Acceptance Criteria**

- `KiteConnectApiService.kt` does not exist in the repository.
- All three interceptors deleted.
- `KiteConnectCertificatePinner.kt` deleted.
- `BindAccountUseCase.executeDeviceSide()` does not exist.
- `./gradlew assembleRelease` passes.
- All unit tests pass.
- No `import com.kitewatch.network.kiteconnect` statements remain in any non-test file.

**Rollback Strategy:** `git revert` of this commit. Since this is a single large deletion commit, reverting it restores all deleted files. This rollback is only acceptable if a critical regression is found — all phases should be complete and stable before FM-034 is executed.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: File deletion and reference cleanup; no new logic.

**Context Strategy**

- Start new chat? No
- Required files: `core/network/src/main/java/com/kitewatch/network/di/NetworkModule.kt`, `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountUseCase.kt`, `app/proguard-rules.pro`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-035 — Enable Firebase Flags in Release Build and CI Update

**Phase:** 7 — Cleanup and Validation
**Subsystem:** Build System / CI

**Description:**
Enable `FEATURE_FIREBASE_AUTH = true` and `FEATURE_FIREBASE_PROXY = true` in the `release` build type. Update all three GitHub Actions workflow files to include Cloud Functions build and deployment steps. Add the `validateSecretAccess` function deletion (deployed in FM-006 for staging only — it must not exist in production). Remove `KITE_API_SECRET` from the GitHub Actions secrets (it is no longer needed in the APK build pipeline).

**Scope Boundaries**

- Files affected: `app/build.gradle.kts` (release build type flags), `.github/workflows/ci-pr.yml`, `.github/workflows/ci-staging.yml`, `.github/workflows/ci-release.yml`, `functions/src/index.ts` (remove `validateSecretAccess` export)
- Modules affected: `:app`
- Explicitly NOT touching: Any source logic, Firestore rules, existing Cloud Functions (other than deleting `validateSecretAccess`)

**Implementation Steps**

1. In `app/build.gradle.kts` `release` build type: change both feature flags to `"true"`.
2. In `functions/src/index.ts`: remove the `validateSecretAccess` export added in FM-006.
3. Update `ci-pr.yml`: add `functions/` build step (`cd functions && npm ci && npm run build && npm test`) before Android build. This ensures Cloud Functions TypeScript compiles and all unit tests pass on every PR.
4. Update `ci-staging.yml`: add `firebase deploy --only functions` step after the staging APK artifact upload (uses `FIREBASE_TOKEN` secret — add to GitHub repository secrets).
5. Update `ci-release.yml`: add `firebase deploy --only functions,firestore:rules` step after release APK creation and before draft release attachment (to ensure production Cloud Functions are deployed atomically with the APK release).
6. Add `FIREBASE_TOKEN` to GitHub repository secrets (generated via `firebase login:ci`).
7. Run `./gradlew assembleRelease` — confirm release APK with both flags `true`.
8. Deploy `validateSecretAccess` deletion: `firebase deploy --only functions` (the export is now removed from `index.ts`).

**User Action Steps (Manual Execution)**

1. Run `firebase login:ci` → copy the CI token.
2. Add GitHub repository secret `FIREBASE_TOKEN` with the CI token value.
3. Remove `KITE_API_SECRET` from GitHub repository secrets (navigate to Settings → Secrets → delete it).
4. Open a test PR → confirm `ci-pr.yml` now includes Cloud Functions build step and passes.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Build: `./gradlew assembleRelease` succeeds with both flags `true`.
- CI: test PR triggers updated `ci-pr.yml` with Cloud Functions unit tests → passes.
- Staging deployment: push to `develop` → `ci-staging.yml` deploys Cloud Functions.

**Acceptance Criteria**

- `BuildConfig.FEATURE_FIREBASE_AUTH = true` and `FEATURE_FIREBASE_PROXY = true` in release builds.
- `validateSecretAccess` function no longer deployed (absent from Firebase console).
- `ci-pr.yml` includes Cloud Functions `npm test` step.
- `ci-staging.yml` deploys Cloud Functions on push to `develop`.
- `ci-release.yml` deploys Cloud Functions on release tag.
- `KITE_API_SECRET` removed from GitHub repository secrets.

**Rollback Strategy:** Set both flags back to `"false"` in the release build type. Revert CI workflow changes. Re-add `KITE_API_SECRET` to GitHub secrets if needed for the old path.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: CI YAML update and build flag change follow established patterns from existing workflow files.

**Context Strategy**

- Start new chat? No
- Required files: `app/build.gradle.kts`, `.github/workflows/ci-pr.yml`, `.github/workflows/ci-staging.yml`, `.github/workflows/ci-release.yml`, `functions/src/index.ts`
- Architecture docs to reference: `10_DEPLOYMENT_WORKFLOW.md` §3.2
- Documents NOT required: All domain, schema documents

---

### FM-036 — Integration Tests for Firebase-Proxied Flows

**Phase:** 7 — Cleanup and Validation
**Subsystem:** `:core-data` / `:infra-firebase`

**Description:**
Write integration tests that cover the full Firebase-proxied data flow end-to-end using MockWebServer for the Kite API responses and the Firebase emulator for Cloud Functions and Firestore. These tests run in `androidTest` (instrumented) using the Firebase emulator suite. The key flows to cover: orders sync via `getOrders` proxy, GTT creation via `createGttOrder` proxy, and token expiry detection via Firestore `sessionFlags`.

**Scope Boundaries**

- Files affected: `core/data/src/androidTest/java/com/kitewatch/data/FirebaseProxyIntegrationTest.kt` (new), `infra/firebase/src/androidTest/java/com/kitewatch/infra/firebase/FirebaseSessionIntegrationTest.kt` (new)
- Modules affected: `:core-data`, `:infra-firebase`
- Explicitly NOT touching: Domain engine tests (unaffected by migration), `:core-database` tests

**Implementation Steps**

1. Configure instrumented tests to use the Firebase emulator:
   - In test setup: `FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)` (emulator IP for Android emulator).
   - `FirebaseFunctions.getInstance().useEmulator("10.0.2.2", 5001)`.
   - `FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)`.
2. Implement `FirebaseProxyIntegrationTest`:
   - `testOrdersSyncViaProxy()`: start `MockWebServer` for Kite API. Deploy test Cloud Functions pointing to the mock server (or use emulator functions). Authenticate as a test Firebase user. Call `FirebaseFunctionsClient.getOrders()` → assert `List<OrderDto>` non-empty → assert DAOs contain the expected orders.
   - `testGttCreationViaProxy()`: call `FirebaseFunctionsClient.createGttOrder(...)` → assert `GttTriggerResponse` contains non-zero `trigger_id`.
3. Implement `FirebaseSessionIntegrationTest`:
   - `testTokenExpiryDetectedViaFirestore()`: write `tokenExpired: true` to Firestore `sessionFlags/data`. Collect first emission from `FirebaseSessionManager.reAuthRequired`. Assert it emits within 5 seconds.
   - `testTokenExpiryCleared()`: after `clearTokenExpired()`, confirm `tokenExpired: false` in Firestore.

**User Action Steps (Manual Execution)**

1. Before running tests: `firebase emulators:start --only auth,firestore,functions`.
2. Run `./gradlew :core-data:connectedAndroidTest` on a connected emulator.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Instrumented: all integration test methods pass on a connected Android emulator with Firebase emulator suite running.

**Acceptance Criteria**

- `testOrdersSyncViaProxy()` passes.
- `testGttCreationViaProxy()` passes.
- `testTokenExpiryDetectedViaFirestore()` passes (emission within 5 seconds).
- `testTokenExpiryCleared()` passes.
- All tests run in < 90 seconds total.

**Rollback Strategy:** Delete the integration test files. No production code is changed.

**Estimated Complexity:** M

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast
- Reason: Test wiring to Firebase emulator suite follows documented Firebase testing patterns.

**Context Strategy**

- Start new chat? No
- Required files: `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseFunctionsClient.kt`, `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseSessionManager.kt`, `core/data/src/main/java/com/kitewatch/data/repository/FirebaseKiteRepository.kt`
- Architecture docs to reference: None
- Documents NOT required: All

---

### FM-037 — Security Audit: APK Binary and Dependency Verification

**Phase:** 7 — Cleanup and Validation
**Subsystem:** All

**Description:**
Execute the final pre-release security audit. Verify the release APK contains no Kite secrets, no direct Kite API call infrastructure, and correctly declares all Firebase permissions. Verify Cloud Functions source code contains no hardcoded credentials. Sign off on the migration security checklist.

**Scope Boundaries**

- Files affected: `docs/SECURITY_AUDIT_MIGRATION_CHECKPOINT.md` (final update)
- Modules affected: None

**Implementation Steps**

1. Build release APK: `./gradlew assembleRelease`.
2. APK binary scan:
   - `apktool d app-release.apk -o apktool_release`
   - `grep -rn "KITE_API_SECRET\|api_secret\|kite_access_token\|access_token" apktool_release/` → zero matches expected.
   - `grep -rn "api.kite.trade" apktool_release/` → zero matches expected (Kite API URL must not appear in the APK).
3. Confirm `BuildConfig.FEATURE_FIREBASE_AUTH = true` and `BuildConfig.FEATURE_FIREBASE_PROXY = true` in release BuildConfig class.
4. Confirm `KITE_API_KEY` is the only Kite-related constant in `BuildConfig` (acceptable — documented public identifier).
5. Cloud Functions scan: `grep -rn "apiSecret\|KITE_API_SECRET\|api_secret" functions/src/` → zero hardcoded secrets found (only `defineSecret("KITE_API_SECRET")` reference is expected — confirm it is the Secret Manager call, not a hardcoded value).
6. Verify `apksigner verify --print-certs app-release.apk` completes successfully.
7. Verify SHA-256 of the release APK matches what CI would produce.
8. Update and sign off `SECURITY_AUDIT_MIGRATION_CHECKPOINT.md` with all findings.

**User Action Steps (Manual Execution)**

1. Run all `apktool` and `grep` commands above.
2. Review `SECURITY_AUDIT_MIGRATION_CHECKPOINT.md` and add final sign-off.

**Data Impact**

- Schema changes: None
- Migration required: No

**Test Plan**

- Static analysis: all grep commands above return zero matches for secrets.
- Build verification: `apksigner verify` passes.

**Acceptance Criteria**

- `api.kite.trade` URL absent from APK binary.
- `KITE_API_SECRET` absent from APK binary and Cloud Functions source.
- `kite_access_token` absent from APK binary.
- `BuildConfig` shows both Firebase flags as `true` in release.
- `apksigner verify` passes.
- `SECURITY_AUDIT_MIGRATION_CHECKPOINT.md` signed off.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? No
- Required files: `docs/SECURITY_AUDIT_MIGRATION_CHECKPOINT.md`, `app/build.gradle.kts`
- Architecture docs to reference: `07_SECURITY_MODEL.md`
- Documents NOT required: All others

---

### FM-038 — Phase 7 Final Validation Milestone

**Phase:** 7 — Cleanup and Validation
**Subsystem:** All

**Description:**
Execute the complete Phase 7 exit criteria checklist. This is the final validation gate before the migrated release APK is distributed. Every item must be confirmed on both CI and a physical device.

**Scope Boundaries**

- Files affected: None (validation task only)
- Modules affected: None

**Implementation Steps**

1. `./gradlew ktlintCheck` — zero violations.
2. `./gradlew detekt` — zero violations.
3. `./gradlew :core-domain:test :core-database:testDebugUnitTest :core-data:testDebugUnitTest :infra-firebase:testDebugUnitTest :infra-auth:testDebugUnitTest :infra-worker:testDebugUnitTest` — all pass.
4. `./gradlew assembleDebug assembleRelease assembleStagingRelease` — all pass.
5. `./gradlew :core-data:connectedAndroidTest` — Firebase integration tests pass (FM-036).
6. Install release APK on API 26 device — full onboarding and sync cycle completes.
7. Install release APK on API 34 device — full onboarding and sync cycle completes.
8. Confirm `KiteConnectApiService.kt` does not exist: `ls core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectApiService.kt` → file not found.
9. Confirm all three Kite interceptors deleted.
10. Confirm Cloud Functions deployed to production (not just emulator).
11. Confirm Firestore Security Rules deployed to production.
12. Confirm `KITE_API_SECRET` removed from GitHub repository secrets.
13. Open test PR → `ci-pr.yml` passes (includes Cloud Functions unit tests).
14. Push to `develop` → `ci-staging.yml` passes (deploys Cloud Functions + produces staging APK).
15. `apksigner verify app-release.apk` passes.
16. Document all findings; declare migration complete.

**Acceptance Criteria**

- Every item above confirmed.
- Zero P0 or P1 bugs.
- Release APK produced, signed, and SHA-256 committed to GitHub release draft.
- Migration declared complete in `SECURITY_AUDIT_MIGRATION_CHECKPOINT.md`.

**Estimated Complexity:** S

---

**LLM Execution Assignment**

- Recommended Model: Claude Sonnet (Fast)
- Recommended Mode: Fast

**Context Strategy**

- Start new chat? Yes (final, clean context for validation)
- Required files: None
- Architecture docs to reference: None
- Documents NOT required: All

---

## 8. Codebase Impact Analysis {#codebase-impact-analysis}

### 8.1 Affected Android Modules

| Module | Impact Type | Summary |
|---|---|---|
| `:app` | Modified | `BuildConfig` flags added; Firebase SDK plugin applied; `KiteWatchApplication` updated with migration calls and Firebase session observer |
| `:core-domain` | Modified | `BindAccountUseCase` updated and deprecated method deleted; `AccountBinding` updated; `BindAccountState` sealed class added |
| `:core-network` | Modified (major) | `KiteConnectApiService`, 3 interceptors, `KiteConnectCertificatePinner` deleted; `NetworkModule` stripped of Kite bindings; Google API modules untouched |
| `:core-data` | Modified | `FirebaseKiteRepository` added; `KiteConnectRepositoryImpl` retained (used only in debug/fallback); `DataModule` updated for conditional Hilt provision |
| `:core-database` | **Unaffected** | Zero schema, entity, DAO, or migration changes |
| `:core-ui` | **Unaffected** | Zero changes |
| `:infra-auth` | Modified | `CredentialStore` stripped of access token and API secret storage; `AccountBindingStore` adds Firebase UID; `AppLockStateManager` wired to Firebase session events |
| `:infra-firebase` | **New module** | `FirebaseAuthManager`, `FirebaseFunctionsClient`, `FirebaseSessionManager`, `FirebaseIdTokenInterceptor`, `FirebaseModule` |
| `:infra-worker` | Modified | `OrderSyncWorker` guards on Firebase Auth state when `FEATURE_FIREBASE_PROXY = true` |
| `:infra-backup` | **Unaffected** | Google Drive backup uses OAuth token path unrelated to Kite credentials |
| `:infra-csv` | **Unaffected** | CSV import and Excel export have no Kite API dependency |
| `:feature-onboarding` | Modified | API secret input field removed; Google Sign-In step added; `BindAccountUseCase` call site updated |
| `:feature-auth` | **Unaffected** | Biometric lock screen unaffected |
| `:feature-portfolio` | **Unaffected** | ViewModel observes Room data; data source is transparent |
| `:feature-holdings` | **Unaffected** | Same as portfolio |
| `:feature-orders` | **Unaffected** | Same as portfolio |
| `:feature-transactions` | **Unaffected** | Same as portfolio |
| `:feature-gtt` | **Unaffected** | Same as portfolio |
| `:feature-settings` | Modified | Account reset flow calls `FirebaseAuthManager.signOut()` |

### 8.2 Files to Delete

| File Path | Reason |
|---|---|
| `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectApiService.kt` | Direct Kite API calls replaced by Cloud Functions |
| `core/network/src/main/java/com/kitewatch/network/kiteconnect/interceptor/KiteConnectAuthInterceptor.kt` | Reads `access_token` from device — removed |
| `core/network/src/main/java/com/kitewatch/network/kiteconnect/interceptor/TokenExpiredInterceptor.kt` | Token expiry now detected via Firestore |
| `core/network/src/main/java/com/kitewatch/network/kiteconnect/interceptor/KiteConnectRateLimitInterceptor.kt` | Rate limiting moved to Cloud Functions |
| `core/network/src/main/java/com/kitewatch/network/kiteconnect/KiteConnectCertificatePinner.kt` | Android-side Kite certificate pinning removed |

### 8.3 Files to Create

| File Path | Created In |
|---|---|
| `infra/firebase/build.gradle.kts` | FM-002 |
| `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseAuthManager.kt` | FM-002 |
| `infra/firebase/src/main/java/com/kitewatch/infra/firebase/di/FirebaseModule.kt` | FM-002 |
| `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseFunctionsClient.kt` | FM-010 |
| `infra/firebase/src/main/java/com/kitewatch/infra/firebase/interceptor/FirebaseIdTokenInterceptor.kt` | FM-020 |
| `infra/firebase/src/main/java/com/kitewatch/infra/firebase/FirebaseSessionManager.kt` | FM-021 |
| `core/data/src/main/java/com/kitewatch/data/repository/FirebaseKiteRepository.kt` | FM-018 |
| `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountState.kt` | FM-011 |
| `functions/` (entire directory) | FM-004 through FM-033 |
| `firestore.rules` | FM-005 |
| `firestore.indexes.json` | FM-005 |
| `firebase.json` | FM-004 |
| `.firebaserc` | FM-004 |
| `docs/SECURITY_AUDIT_MIGRATION_CHECKPOINT.md` | FM-028 |

### 8.4 Files Requiring Significant Modification

| File Path | Nature of Change |
|---|---|
| `app/build.gradle.kts` | Firebase plugin, feature flags, Firebase SDK dependencies |
| `gradle/libs.versions.toml` | Firebase BOM, Firebase SDK version entries |
| `settings.gradle.kts` | Add `:infra-firebase` module |
| `core/network/src/main/java/com/kitewatch/network/di/NetworkModule.kt` | Remove Kite `OkHttpClient`, `Retrofit`, `KiteConnectApiService` bindings |
| `core/data/src/main/java/com/kitewatch/data/di/DataModule.kt` | Conditional Hilt provision: Firebase vs Retrofit repository |
| `core/domain/src/main/java/com/kitewatch/domain/usecase/auth/BindAccountUseCase.kt` | Dual path → then delete deprecated path; `apiSecret` parameter removed |
| `infra/auth/src/main/java/com/kitewatch/infra/auth/CredentialStore.kt` | Remove `access_token` methods; add legacy migration call |
| `infra/auth/src/main/java/com/kitewatch/infra/auth/AccountBindingStore.kt` | Remove `bound_api_key`; add `firebase_uid` |
| `infra/auth/src/main/java/com/kitewatch/infra/auth/AppLockStateManager.kt` | Wire Firebase session events to biometric lock |
| `infra/worker/src/main/java/com/kitewatch/infra/worker/OrderSyncWorker.kt` | Firebase Auth guard before sync |
| `app/src/main/java/com/kitewatch/app/KiteWatchApplication.kt` | Legacy migration calls; Firebase session observer registration |
| `feature/onboarding` ViewModel and Screen | Remove API secret input; add Google Sign-In; new bind state machine |
| `app/proguard-rules.pro` | Add Firebase keep rules; remove Kite DTO keep rule |
| `app/src/main/res/xml/network_security_config.xml` | Remove `api.kite.trade` pin set |
| `secrets.properties.template` | Remove `KITE_API_SECRET`; add Firebase notes |
| `.github/workflows/ci-pr.yml` | Add Cloud Functions build + test step; add `google-services.json` injection |
| `.github/workflows/ci-staging.yml` | Add Cloud Functions deploy step; add `google-services.json` injection |
| `.github/workflows/ci-release.yml` | Add Cloud Functions deploy step; add `google-services.json` injection |

### 8.5 Domain Engines — No Changes Required

The following domain engines are completely unaffected by this migration. They are pure Kotlin functions with no Android or network dependencies and operate only on data already in the Room database:

- `ChargeCalculator` — unaffected
- `FifoLotMatcher` — unaffected
- `HoldingsComputationEngine` — unaffected
- `PnlCalculator` — unaffected
- `TargetPriceCalculator` — unaffected
- `GttAutomationEngine` — unaffected
- `SyncOrdersUseCase` — unaffected (calls `KiteConnectRepository` interface; implementation swapped transparently)

### 8.6 Features Not Requiring Change

- All five main feature screens (Portfolio, Holdings, Orders, Transactions, GTT) — ViewModels observe Room `Flow<>` data; the data source change is transparent.
- Google Drive backup and restore — uses OAuth token path independent of Kite credentials; unaffected.
- Gmail fund detection — uses Google OAuth token; unaffected.
- CSV import and Excel export — local operations; unaffected.
- Biometric app lock — operates on app lifecycle and its own timer; only the trigger source for re-lock is extended (FM-023).
- SQLCipher database encryption — passphrase generation and storage in `MasterKeyProvider` is unaffected.

---
