# 06 — Automation and Integration Design

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-10

---

> **Scope Note:** KiteWatch does not incorporate machine learning, inference models, or AI classification. This document covers the **automation pipelines** (background task orchestration, Gmail-based fund detection, GTT order automation) and **external API integration** strategies that constitute the product's automation layer.

---

## 1. Automation Architecture Overview

```
┌───────────────────────────────────────────────────────────┐
│                    AUTOMATION LAYER                        │
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ Order Sync   │  │    Fund      │  │  Charge Rate │   │
│  │  Pipeline    │  │ Reconciliation│  │   Refresh    │   │
│  │              │  │  Pipeline    │  │  Pipeline    │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
│         │                 │                  │            │
│  ┌──────▼───────┐  ┌──────▼───────┐         │            │
│  │ GTT Update   │  │ Gmail Scan   │         │            │
│  │  Pipeline    │  │  Pipeline    │         │            │
│  └──────────────┘  └──────────────┘         │            │
│         │                 │                  │            │
│    ┌────▼─────────────────▼──────────────────▼────┐      │
│    │          WorkManager Scheduler               │      │
│    │  (Mon–Fri enforcement, retry policies,       │      │
│    │   constraint management, chaining)           │      │
│    └──────────────────────────────────────────────┘      │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

---

## 2. Pipeline Definitions

### 2.1 Order Sync Pipeline

**Trigger:** WorkManager periodic task (default 4 PM, Mon–Fri) OR manual user action.

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│  Weekday     │────▶│ Fetch Today's│────▶│ Filter Equity│
│  Guard       │     │ Orders (API) │     │ Delivery Only│
└─────────────┘     └──────────────┘     └──────┬───────┘
                                                 │
                           ┌─────────────────────┤ new orders?
                           │ NO                  │ YES
                           ▼                     ▼
                    ┌──────────┐         ┌──────────────┐
                    │ Log:     │         │ Fetch Remote │
                    │ No new   │         │ Holdings     │
                    │ orders   │         │ (API)        │
                    └──────────┘         └──────┬───────┘
                                                │
                                         ┌──────▼───────┐
                                         │   Verify     │
                                         │ Holdings     │
                                         │ (local vs    │
                                         │  remote)     │
                                         └──────┬───────┘
                                                │
                              ┌─────────────────┤
                              │ MISMATCH        │ MATCH
                              ▼                 ▼
                       ┌────────────┐    ┌─────────────────┐
                       │ CRITICAL   │    │ BEGIN DB TXN:   │
                       │ Alert.     │    │ • Insert orders │
                       │ Abort sync.│    │ • Update holdngs│
                       └────────────┘    │ • Calc charges  │
                                         │ • Insert txns   │
                                         │ • Update P&L $  │
                                         │ COMMIT          │
                                         └────────┬────────┘
                                                   │
                                    ┌──────────────┤
                                    ▼              ▼
                             ┌───────────┐  ┌───────────────┐
                             │ Enqueue   │  │ Enqueue       │
                             │ GTT Update│  │ Reconciliation│
                             │ Worker    │  │ Worker        │
                             └───────────┘  └───────────────┘
```

**Failure Handling:**

| Failure Point | Behavior |
|---|---|
| Weekday guard fails (weekend) | Worker completes as SUCCESS. No-op. |
| Orders API call fails | Worker returns `Result.retry()`. Exponential backoff: 30s, 60s, 120s. Max 3. |
| Holdings API call fails (after orders received) | Entire sync aborted. No orders persisted. Amber warning. |
| Holdings verification mismatch | CRITICAL alert persisted. Sync halted. Orders NOT persisted. |
| DB transaction fails | Room auto-rollback. Error logged. Worker returns FAILURE. |
| GTT worker enqueue fails | Non-blocking. GTT update deferred to next manual or scheduled trigger. |

---

### 2.2 GTT Automation Pipeline

**Trigger:** Chained from Order Sync Worker (only when new buy orders detected) OR manual profit target edit.

```
┌───────────────────┐
│ Read Worker       │
│ Handoff Payload   │
│ (affected stocks) │
└────────┬──────────┘
         │
         ▼ for each stock
┌───────────────────────────────────────────────────┐
│                                                   │
│  ┌──────────────┐     ┌──────────────┐           │
│  │ Get Holding  │────▶│ Calculate    │           │
│  │ from local DB│     │ Target Sell  │           │
│  └──────────────┘     │ Price        │           │
│                       └──────┬───────┘           │
│                              │                    │
│                       ┌──────▼───────┐           │
│                       │ Check local  │           │
│                       │ GTT record   │           │
│                       └──────┬───────┘           │
│                              │                    │
│               ┌──────────────┼──────────────┐    │
│               │ No GTT       │ GTT exists   │    │
│               ▼              ▼              │    │
│        ┌────────────┐ ┌────────────────┐   │    │
│        │ Fetch GTT  │ │ Fetch specific │   │    │
│        │ list (API) │ │ GTT (API)      │   │    │
│        │ Check if   │ └──────┬─────────┘   │    │
│        │ one exists │        │              │    │
│        └─────┬──────┘        │              │    │
│              │        ┌──────▼──────┐       │    │
│              │        │ Override    │       │    │
│              │        │ detection:  │       │    │
│              │        │ remote !=   │       │    │
│              │        │ app_calc?   │       │    │
│              │        └──────┬──────┘       │    │
│              │               │              │    │
│              │     ┌─────────┤              │    │
│              │     │ YES     │ NO           │    │
│              │     ▼         ▼              │    │
│              │  ┌────────┐ ┌──────────┐    │    │
│              │  │WARNING │ │ Modify   │    │    │
│              │  │Alert.  │ │ GTT (API)│    │    │
│              │  │User    │ └────┬─────┘    │    │
│              │  │decides │      │          │    │
│              │  └────────┘      │          │    │
│              │                  │          │    │
│        ┌─────▼──────┐   ┌──────▼──────┐   │    │
│        │ Create GTT │   │ Verify GTT  │   │    │
│        │ (API)      │   │ (re-fetch)  │   │    │
│        └─────┬──────┘   └──────┬──────┘   │    │
│              │                 │           │    │
│        ┌─────▼──────┐   ┌─────▼───────┐   │    │
│        │ Verify GTT │   │ Update local│   │    │
│        │ (re-fetch) │   │ GTT record  │   │    │
│        └─────┬──────┘   └─────────────┘   │    │
│              │                             │    │
│        ┌─────▼──────┐                      │    │
│        │ Insert     │                      │    │
│        │ local GTT  │                      │    │
│        │ record     │                      │    │
│        └────────────┘                      │    │
│                                            │    │
└────────────────────────────────────────────┘    │
```

**API Call Sequence (per stock):**

1. `GET /gtt/triggers` — Fetch current GTT list to check if one exists for this stock.
2. `POST /gtt/triggers` (create) OR `PUT /gtt/triggers/{id}` (modify) — Place or update the GTT.
3. `GET /gtt/triggers/{id}` — Verification fetch to confirm the action took effect.

**Serialized Execution:** All Kite Connect API calls are serialized via `Semaphore(1)` to respect rate limits. GTT operations for multiple stocks execute sequentially, not in parallel.

---

### 2.3 Fund Reconciliation Pipeline

**Trigger:** After order sync, after manual fund entry, or scheduled daily.

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ Fetch Remote │────▶│ Compute Local    │────▶│ Compare:         │
│ Fund Balance │     │ Balance (from    │     │ |remote - local| │
│ (API)        │     │ transactions DB) │     │ vs tolerance     │
└──────────────┘     └──────────────────┘     └────────┬─────────┘
                                                       │
                                    ┌──────────────────┤
                                    │ ≤ tolerance     │ > tolerance
                                    ▼                  ▼
                             ┌────────────────┐  ┌────────────────┐
                             │ Auto-adjust:   │  │ CRITICAL Alert:│
                             │ Insert MISC_   │  │ No auto-adjust.│
                             │ ADJUSTMENT txn │  │ User must      │
                             │ Amber snackbar │  │ investigate.   │
                             └────────────────┘  └────────────────┘
```

**Tolerance Logic:**

```kotlin
fun reconcile(remoteBalance: Paisa, localBalance: Paisa, tolerance: Paisa): ReconciliationResult {
    val diff = remoteBalance - localBalance

    return when {
        diff.abs() <= tolerance && diff != Paisa.ZERO -> {
            // Auto-adjust
            val adjustment = Transaction(
                type = TransactionType.MISC_ADJUSTMENT,
                amount = diff,
                description = "Auto-reconciliation adjustment (${diff.toRupees()})",
                source = TransactionSource.RECONCILIATION,
            )
            transactionRepo.insert(adjustment)
            ReconciliationResult.WithinTolerance(adjustment = diff)
        }
        diff == Paisa.ZERO -> ReconciliationResult.ExactMatch
        else -> {
            // Exceeds tolerance
            alertRepo.insert(PersistentAlert(
                type = AlertType.FUND_MISMATCH,
                severity = Severity.CRITICAL,
                payload = FundMismatchPayload(localBalance, remoteBalance, diff, tolerance),
            ))
            ReconciliationResult.ExceedsTolerance(localBalance, remoteBalance, diff)
        }
    }
}
```

---

### 2.4 Gmail Scan Pipeline

**Trigger:** Scheduled (aligned with reconciliation worker) OR manual user action.

```
┌────────────────────┐
│ Check Gmail        │
│ permission granted │
└────────┬───────────┘
         │ YES
         ▼
┌────────────────────┐     ┌────────────────────┐
│ Load user-defined  │────▶│ Build Gmail API    │
│ filters from DB    │     │ query string       │
│ (sender, subject)  │     │ (combined filters) │
└────────────────────┘     └────────┬───────────┘
                                    │
                             ┌──────▼───────────┐
                             │ Fetch matching   │
                             │ messages (API)   │
                             │ since last scan  │
                             └──────┬───────────┘
                                    │
                             ┌──────▼───────────┐
                             │ For each message: │
                             │ • Check gmail_   │
                             │   scan_cache     │
                             │   (already seen?)│
                             │ • Parse body for │
                             │   amount + type  │
                             └──────┬───────────┘
                                    │
                      ┌─────────────┤
                      │ already     │ new message
                      │ scanned     │
                      ▼             ▼
                   [skip]    ┌──────────────────┐
                             │ Insert into      │
                             │ gmail_scan_cache │
                             │ status: PENDING  │
                             │ _REVIEW          │
                             └──────┬───────────┘
                                    │
                             ┌──────▼───────────┐
                             │ Emit event:      │
                             │ GmailEntries     │
                             │ PendingReview    │
                             └──────────────────┘
```

**Gmail Query Construction:**

```kotlin
fun buildGmailQuery(filters: List<GmailFilter>, lastScanDate: LocalDate): String {
    val filterClauses = filters.mapNotNull { filter ->
        when (filter.type) {
            FilterType.SENDER -> "from:${filter.value}"
            FilterType.SUBJECT_CONTAINS -> "subject:${filter.value}"
        }
    }
    val dateClause = "after:${lastScanDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}"

    // Combine: (from:sender1 OR from:sender2) AND (subject:keyword1 OR ...) AND after:date
    val senderFilters = filterClauses.filter { it.startsWith("from:") }
    val subjectFilters = filterClauses.filter { it.startsWith("subject:") }

    val parts = mutableListOf<String>()
    if (senderFilters.isNotEmpty()) parts.add("(${senderFilters.joinToString(" OR ")})")
    if (subjectFilters.isNotEmpty()) parts.add("(${subjectFilters.joinToString(" OR ")})")
    parts.add(dateClause)

    return parts.joinToString(" ")
}
```

**Amount Parsing from Email Body:**

```kotlin
object FundAmountParser {
    // Patterns for common Zerodha fund transfer confirmation emails
    private val amountPatterns = listOf(
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{2})?)"""),
        Regex("""(?:amount|credited|debited|transferred)\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
    )

    private val typePatterns = mapOf(
        FundEntryType.ADDITION to listOf("credited", "received", "deposit", "added"),
        FundEntryType.WITHDRAWAL to listOf("debited", "withdrawn", "withdrawal", "payout"),
    )

    fun parse(emailBody: String): ParseResult? {
        // Extract amount
        val amount = amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(emailBody)?.groupValues?.get(1)
                ?.replace(",", "")
                ?.toBigDecimalOrNull()
                ?.let { Paisa.fromRupees(it) }
        } ?: return null

        // Determine type
        val bodyLower = emailBody.lowercase()
        val type = typePatterns.entries.firstOrNull { (_, keywords) ->
            keywords.any { keyword -> bodyLower.contains(keyword) }
        }?.key ?: return null  // Cannot determine type

        return ParseResult(amount = amount, type = type)
    }
}
```

**Duplicate Detection with Existing Fund Entries:**

```kotlin
fun checkDuplicate(detected: ParseResult, existingEntries: List<FundEntry>): DuplicateStatus {
    val potentialDuplicates = existingEntries.filter { entry ->
        entry.type == detected.type
            && entry.amount == detected.amount
            && abs(entry.date.toEpochDay() - detected.emailDate.toEpochDay()) <= 1
    }
    return when {
        potentialDuplicates.isEmpty() -> DuplicateStatus.NoDuplicate
        else -> DuplicateStatus.PotentialDuplicate(potentialDuplicates)
    }
}
```

**User Confirmation Flow:**

1. Gmail scan results are stored in `gmail_scan_cache` with `status = PENDING_REVIEW`.
2. When the user opens the Fund section or Transactions, pending entries are displayed in a review card.
3. Each entry shows: detected amount, type (addition/withdrawal), email date, email subject, and a duplicate warning if applicable.
4. User taps **Confirm** → entry is moved to `fund_entries` table, `gmail_scan_cache.status` updated to `CONFIRMED`, and a corresponding `Transaction` is created.
5. User taps **Dismiss** → `gmail_scan_cache.status` updated to `REJECTED`. Entry is not logged.
6. **No bulk confirm.** Each entry is confirmed individually per FR-FUND-04.

---

### 2.5 Charge Rate Refresh Pipeline

**Trigger:** Periodic (default every 15 days) OR manual from Settings.

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ Fetch charge │────▶│ Parse response   │────▶│ Mark old rates   │
│ rates (API)  │     │ into rate entries│     │ is_current = 0   │
└──────────────┘     └──────────────────┘     └────────┬─────────┘
                                                       │
                                                ┌──────▼──────┐
                                                │ Insert new  │
                                                │ rates with  │
                                                │ is_current=1│
                                                └──────┬──────┘
                                                       │
                                                ┌──────▼──────┐
                                                │ Update last │
                                                │ fetch date  │
                                                │ in DataStore│
                                                └─────────────┘
```

**Fallback:** If the API does not provide structured charge rates (see Open Question Q4 in PRD), the app falls back to a hardcoded rate table shipped with the APK. The "refresh" then checks for updated rates bundled in app updates, rather than fetching from an API.

```kotlin
object DefaultChargeRates {
    val RATES_2025 = ChargeRateSnapshot(
        brokerageDeliveryBps = 0,           // ₹0 (zero brokerage on delivery)
        sttBuyBps = 10,                     // 0.1%
        sttSellBps = 25,                    // 0.025%
        exchangeNseBps = 297,               // 0.00297% (stored as custom scale)
        exchangeBseBps = 375,               // 0.00375%
        gstBps = 1800,                      // 18%
        sebiFeePerCrorePaisa = 1000,        // ₹10 per crore
        stampDutyBps = 15,                  // 0.015%
        dpChargesPerScriptPaisa = 1580,     // ₹15.80
        effectiveFrom = LocalDate.of(2025, 1, 1),
    )
}
```

---

### 2.6 Backup Automation Pipeline

**Trigger:** Scheduled (user-configurable) OR manual from Settings.

```
┌──────────────────┐
│ Assemble metadata│
│ accountId, ver,  │
│ schemaVer, time  │
└────────┬─────────┘
         │
┌────────▼─────────┐     ┌──────────────────┐
│ Serialize data   │────▶│ Compress (GZIP)  │
│ (Protobuf)       │     └────────┬─────────┘
│ • orders         │              │
│ • transactions   │     ┌────────▼─────────┐
│ • holdings       │     │ Compute SHA-256  │
│ • fund_entries   │     │ checksum         │
│ • gtt_records    │     └────────┬─────────┘
│ • charge_rates   │              │
│ • settings       │     ┌────────▼─────────┐
└──────────────────┘     │ Write header +   │
                         │ payload to file  │
                         └────────┬─────────┘
                                  │
                    ┌─────────────┤
                    │ Drive       │ Local
                    ▼             ▼
             ┌────────────┐ ┌───────────┐
             │Upload to   │ │ Save to   │
             │Google Drive│ │ app dir   │
             │ app folder │ └───────────┘
             └─────┬──────┘
                   │
              ┌────┤
              │FAIL│ SUCCESS
              ▼    ▼
        ┌──────────┐ ┌──────────────┐
        │ Fallback │ │ Log backup   │
        │ to local │ │ history      │
        │ + notify │ │ Success      │
        └──────────┘ └──────────────┘
```

---

## 3. Worker Registration and Scheduling

### 3.1 Initial Registration (Post-Onboarding)

```kotlin
class WorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val preferencesRepo: PreferencesRepository,
) {
    suspend fun registerAllWorkers() {
        val prefs = preferencesRepo.getSchedulePreferences()

        // Order Sync — daily at configured time(s)
        prefs.orderSyncTimes.forEach { time ->
            scheduleDaily(
                uniqueName = "order_sync_${time.hour}_${time.minute}",
                workerClass = OrderSyncWorker::class,
                targetTime = time,
                tag = "order_sync",
                requiresNetwork = true,
            )
        }

        // Fund Reconciliation — daily at configured time
        scheduleDaily(
            uniqueName = "fund_reconciliation_daily",
            workerClass = ReconciliationWorker::class,
            targetTime = prefs.reconciliationTime,
            tag = "fund_reconciliation",
            requiresNetwork = true,
        )

        // Charge Rate Refresh — every N days
        schedulePeriodic(
            uniqueName = "charge_rate_refresh",
            workerClass = ChargeRateRefreshWorker::class,
            intervalDays = prefs.chargeRateRefreshIntervalDays,
            tag = "charge_rate_refresh",
            requiresNetwork = true,
        )

        // Gmail Scan — aligned with reconciliation
        if (prefs.isGmailEnabled) {
            scheduleDaily(
                uniqueName = "gmail_scan_daily",
                workerClass = GmailScanWorker::class,
                targetTime = prefs.reconciliationTime.minusMinutes(15),
                tag = "gmail_scan",
                requiresNetwork = true,
            )
        }

        // Scheduled Backup (if configured)
        if (prefs.isScheduledBackupEnabled) {
            schedulePeriodic(
                uniqueName = "scheduled_backup",
                workerClass = BackupWorker::class,
                intervalDays = prefs.backupIntervalDays,
                tag = "backup",
                requiresNetwork = prefs.backupDestination == BackupDestination.GOOGLE_DRIVE,
            )
        }
    }

    private fun scheduleDaily(
        uniqueName: String,
        workerClass: KClass<out CoroutineWorker>,
        targetTime: LocalTime,
        tag: String,
        requiresNetwork: Boolean,
    ) {
        val delay = calculateDelayUntilNext(targetTime)

        val request = OneTimeWorkRequestBuilder(workerClass)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().apply {
                if (requiresNetwork) setRequiredNetworkType(NetworkType.CONNECTED)
            }.build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(tag)
            .build()

        workManager.enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun calculateDelayUntilNext(targetTime: LocalTime): Duration {
        val now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
        var targetDateTime = now.toLocalDate().atTime(targetTime)
        if (targetDateTime.isBefore(now) || targetDateTime.isEqual(now)) {
            targetDateTime = targetDateTime.plusDays(1)
        }
        // Skip weekends
        while (targetDateTime.dayOfWeek == DayOfWeek.SATURDAY ||
               targetDateTime.dayOfWeek == DayOfWeek.SUNDAY) {
            targetDateTime = targetDateTime.plusDays(1)
        }
        return Duration.between(now, targetDateTime)
    }
}
```

### 3.2 Self-Rescheduling Pattern

Since WorkManager's `PeriodicWorkRequest` doesn't support "daily at exact time X," each daily worker reschedules itself upon completion:

```kotlin
@HiltWorker
class OrderSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncOrdersUseCase: SyncOrdersUseCase,
    private val workerScheduler: WorkerScheduler,
    private val syncEventRepo: SyncEventRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 1. Weekday guard
        if (!WeekdayGuard.isTradingDay()) {
            rescheduleForNextTradingDay()
            return Result.success()
        }

        // 2. Log start
        val eventId = syncEventRepo.logStart(SyncEventType.ORDER_SYNC)

        // 3. Execute
        return when (val result = syncOrdersUseCase.execute()) {
            is DomainResult.Success -> {
                syncEventRepo.logComplete(eventId, SyncStatus.SUCCESS, result.value.toDetails())
                rescheduleForTomorrow()
                Result.success()
            }
            is DomainResult.Failure -> {
                if (result.error is AppError.Transient && runAttemptCount < 3) {
                    syncEventRepo.logComplete(eventId, SyncStatus.FAILED, result.error.toString())
                    Result.retry()
                } else {
                    syncEventRepo.logComplete(eventId, SyncStatus.FAILED, result.error.toString())
                    rescheduleForTomorrow()
                    Result.failure()
                }
            }
        }
    }

    private suspend fun rescheduleForTomorrow() {
        val scheduledTime = preferencesRepo.getOrderSyncTime()
        workerScheduler.scheduleDaily(
            uniqueName = tags.first(),
            workerClass = OrderSyncWorker::class,
            targetTime = scheduledTime,
            tag = "order_sync",
            requiresNetwork = true,
        )
    }

    private suspend fun rescheduleForNextTradingDay() {
        // Same as rescheduleForTomorrow — calculateDelayUntilNext skips weekends
        rescheduleForTomorrow()
    }
}
```

---

## 4. Schedule Configuration Management

### 4.1 User-Configurable Parameters

| Parameter | Default | Range | Storage |
|---|---|---|---|
| Order sync times | `[16:00]` | Any time(s), Mon–Fri only | DataStore |
| Reconciliation time | `16:30` | Any time, Mon–Fri only | DataStore |
| Charge rate refresh interval | 15 days | 1–90 days | DataStore |
| Gmail scan time | 15 min before reconciliation | Derived | Computed |
| Scheduled backup interval | Disabled | 1–30 days | DataStore |
| Backup destination | Google Drive | Google Drive / Local | DataStore |
| App lock timeout | 5 minutes | 1–30 minutes | DataStore |
| Reconciliation tolerance | ₹50 (5000 paisa) | ₹0–₹10,000 | DataStore |

### 4.2 Schedule Change Propagation

When user changes a schedule parameter in Settings:

1. New value is written to DataStore.
2. `SettingsViewModel` calls `WorkerScheduler.updateSchedule(changedParam)`.
3. `WorkerScheduler` cancels the existing worker for that task: `workManager.cancelUniqueWork(uniqueName)`.
4. New worker is registered with updated parameters.
5. Change takes effect from the **next day's schedule** (per FR-SCH-04), not immediately.

```kotlin
suspend fun updateOrderSyncSchedule(newTimes: List<LocalTime>) {
    // Cancel all existing order sync workers
    workManager.cancelAllWorkByTag("order_sync").await()

    // Register new workers for each time
    newTimes.forEach { time ->
        scheduleDaily(
            uniqueName = "order_sync_${time.hour}_${time.minute}",
            workerClass = OrderSyncWorker::class,
            targetTime = time,
            tag = "order_sync",
            requiresNetwork = true,
        )
    }
}
```

---

## 5. External API Integration Strategy

### 5.1 Kite Connect API Integration

**Base URL:** `https://api.kite.trade`

**Authentication:** API key + access token in headers.

```kotlin
interface KiteConnectApiService {
    @GET("user/margins/equity")
    suspend fun getFundBalance(
        @Header("X-Kite-Version") version: String = "3",
    ): ApiResponse<FundBalanceDto>

    @GET("orders")
    suspend fun getTodaysOrders(): ApiResponse<List<OrderDto>>

    @GET("portfolio/holdings")
    suspend fun getHoldings(): ApiResponse<List<HoldingDto>>

    @GET("gtt/triggers")
    suspend fun getGttList(): ApiResponse<List<GttDto>>

    @POST("gtt/triggers")
    suspend fun placeGtt(@Body request: GttPlaceRequest): ApiResponse<GttPlaceResponse>

    @PUT("gtt/triggers/{triggerId}")
    suspend fun modifyGtt(
        @Path("triggerId") triggerId: Int,
        @Body request: GttModifyRequest,
    ): ApiResponse<GttModifyResponse>

    @DELETE("gtt/triggers/{triggerId}")
    suspend fun deleteGtt(@Path("triggerId") triggerId: Int): ApiResponse<GttDeleteResponse>
}
```

**Authentication Interceptor:**

```kotlin
class KiteConnectAuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val session = sessionManager.currentSession
            ?: throw SessionExpiredException("No active Kite Connect session")

        val request = chain.request().newBuilder()
            .addHeader("X-Kite-Version", "3")
            .addHeader("Authorization", "token ${session.apiKey}:${session.accessToken}")
            .build()

        val response = chain.proceed(request)

        if (response.code == 403) {
            // Session expired
            sessionManager.invalidateSession()
            throw SessionExpiredException("Kite Connect session expired (403)")
        }

        return response
    }
}
```

**Rate Limit Handling:**

```kotlin
class RateLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")?.toIntOrNull()
            throw ApiRateLimitedException(
                retryAfterSeconds = retryAfter ?: 60,
                endpoint = chain.request().url.encodedPath,
            )
        }

        return response
    }
}
```

### 5.2 Kite Connect Session Management

```kotlin
class SessionManager @Inject constructor(
    private val encryptedPrefs: EncryptedSharedPreferences,
    private val eventBus: MutableSharedFlow<AppEvent>,
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    val currentSession: KiteSession?
        get() = (_sessionState.value as? SessionState.Active)?.session

    fun onLoginSuccess(requestToken: String, apiKey: String, accessToken: String, userId: String) {
        val session = KiteSession(apiKey, accessToken, userId)
        encryptedPrefs.edit()
            .putString("api_key", apiKey)
            .putString("access_token", accessToken)
            .putString("user_id", userId)
            .apply()
        _sessionState.value = SessionState.Active(session)
    }

    fun invalidateSession() {
        encryptedPrefs.edit().remove("access_token").apply()
        _sessionState.value = SessionState.Expired
        viewModelScope.launch { eventBus.emit(AppEvent.SessionExpired) }
    }

    fun restoreSession(): SessionState {
        val apiKey = encryptedPrefs.getString("api_key", null)
        val accessToken = encryptedPrefs.getString("access_token", null)
        val userId = encryptedPrefs.getString("user_id", null)

        return if (apiKey != null && accessToken != null && userId != null) {
            val session = KiteSession(apiKey, accessToken, userId)
            _sessionState.value = SessionState.Active(session)
            SessionState.Active(session)
        } else if (apiKey != null && userId != null) {
            _sessionState.value = SessionState.Expired
            SessionState.Expired
        } else {
            _sessionState.value = SessionState.NotBound
            SessionState.NotBound
        }
    }
}

sealed interface SessionState {
    data object Unknown : SessionState
    data object NotBound : SessionState
    data class Active(val session: KiteSession) : SessionState
    data object Expired : SessionState
}
```

### 5.3 Gmail API Integration

```kotlin
class GmailRemoteDataSource @Inject constructor(
    private val credential: GoogleAccountCredential,
) {
    private val gmailService: Gmail by lazy {
        Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName("KiteWatch").build()
    }

    suspend fun fetchMatchingMessages(
        query: String,
        maxResults: Long = 50,
    ): List<GmailMessage> = withContext(Dispatchers.IO) {
        val response = gmailService.users().messages().list("me")
            .setQ(query)
            .setMaxResults(maxResults)
            .execute()

        response.messages?.map { messageMeta ->
            val full = gmailService.users().messages().get("me", messageMeta.id)
                .setFormat("full")
                .execute()
            GmailMessage(
                id = full.id,
                subject = full.payload.headers.find { it.name == "Subject" }?.value ?: "",
                sender = full.payload.headers.find { it.name == "From" }?.value ?: "",
                date = Instant.ofEpochMilli(full.internalDate),
                bodyText = extractPlainTextBody(full.payload),
            )
        } ?: emptyList()
    }

    private fun extractPlainTextBody(payload: MessagePart): String {
        // Recursive extraction of text/plain parts
        if (payload.mimeType == "text/plain" && payload.body?.data != null) {
            return String(Base64.getUrlDecoder().decode(payload.body.data))
        }
        return payload.parts?.firstNotNullOfOrNull { extractPlainTextBody(it) } ?: ""
    }
}
```

### 5.4 Google Drive API Integration

```kotlin
class GoogleDriveRemoteDataSource @Inject constructor(
    private val credential: GoogleAccountCredential,
) {
    private val driveService: Drive by lazy {
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName("KiteWatch").build()
    }

    private val appFolderName = "KiteWatch Backups"

    suspend fun uploadBackup(fileName: String, fileContent: ByteArray): String = withContext(Dispatchers.IO) {
        val folderId = getOrCreateAppFolder()

        val fileMetadata = com.google.api.services.drive.model.File().apply {
            name = fileName
            parents = listOf(folderId)
            mimeType = "application/octet-stream"
        }

        val mediaContent = ByteArrayContent("application/octet-stream", fileContent)

        val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
            .setFields("id, name, size, createdTime")
            .execute()

        uploadedFile.id
    }

    suspend fun listBackups(): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        val folderId = getOrCreateAppFolder()

        val result = driveService.files().list()
            .setQ("'$folderId' in parents and trashed = false")
            .setFields("files(id, name, size, createdTime)")
            .setOrderBy("createdTime desc")
            .execute()

        result.files?.map { file ->
            DriveBackupFile(
                id = file.id,
                name = file.name,
                sizeBytes = file.getSize()?.toLong() ?: 0,
                createdAt = Instant.parse(file.createdTime.toStringRfc3339()),
            )
        } ?: emptyList()
    }

    suspend fun downloadBackup(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        outputStream.toByteArray()
    }

    private suspend fun getOrCreateAppFolder(): String {
        // Check if folder exists
        val existing = driveService.files().list()
            .setQ("name = '$appFolderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            .setFields("files(id)")
            .execute()

        return existing.files?.firstOrNull()?.id ?: run {
            // Create folder
            val folderMetadata = com.google.api.services.drive.model.File().apply {
                name = appFolderName
                mimeType = "application/vnd.google-apps.folder"
            }
            driveService.files().create(folderMetadata).setFields("id").execute().id
        }
    }
}
```

---

## 6. Privacy Safeguards

### 6.1 Data Minimization

| Integration | Data Read | Data Stored | Data Transmitted |
|---|---|---|---|
| Kite Connect | Orders, holdings, fund balance, GTT list, charge rates | All fetched data stored locally | Auth credentials only |
| Gmail | Emails matching user-defined filters | Message ID, subject, date, detected amount | None (read-only, no forwarding) |
| Google Drive | Backup files from app folder | Backup metadata (ID, name, date) | Backup file content (user's own data to user's own account) |

### 6.2 Gmail Privacy Controls

1. **Filter-scoped access only.** The app constructs Gmail API queries using only user-defined filters. No full-mailbox scan is ever performed.
2. **No email content storage.** Email body text is parsed in-memory for amount detection. Only the Gmail message ID, subject line, detected amount, and type are stored in `gmail_scan_cache`. The email body is never persisted.
3. **User confirmation required.** No Gmail-detected entry is ever added to the financial ledger without explicit per-entry user confirmation.
4. **Revocable at any time.** Gmail integration can be completely disabled from Settings, which clears the stored filters and stops all future scans.

### 6.3 Data Transmission Audit

```kotlin
// Enforced via OkHttp interceptor on debug builds
class DataTransmissionAuditInterceptor : Interceptor {
    private val allowedHosts = setOf(
        "api.kite.trade",
        "kite.zerodha.com",
        "www.googleapis.com",
        "oauth2.googleapis.com",
        "accounts.google.com",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        check(host in allowedHosts) {
            "SECURITY: Attempted network request to unauthorized host: $host"
        }
        return chain.proceed(chain.request())
    }
}
```

---

## 7. Performance Constraints

### 7.1 Worker Execution Budgets

| Worker | Max Execution Time | Justification |
|---|---|---|
| OrderSyncWorker | 60 seconds | 3 API calls + DB transaction + event emission |
| GttUpdateWorker | 90 seconds | Up to 50 stocks × 3 API calls each (serialized), but typically 1-3 stocks |
| ReconciliationWorker | 15 seconds | 1 API call + DB query + optional insert |
| GmailScanWorker | 45 seconds | Gmail API query + message fetches (up to 50) |
| ChargeRateRefreshWorker | 15 seconds | 1 API call + DB insert |
| BackupWorker | 120 seconds | DB serialization + GZIP + Drive upload |

### 7.2 API Call Budget Per Sync Cycle

| API Call | Count | Rate Limit Concern |
|---|---|---|
| Fetch today's orders | 1 | Low |
| Fetch holdings | 1 | Low |
| Fetch fund balance | 1 | Low |
| Fetch GTT list | 1 | Low |
| GTT create/modify per stock | 1 per affected stock | Medium (if many stocks affected) |
| GTT verification fetch per stock | 1 per affected stock | Medium |
| Gmail message list | 1 | Low |
| Gmail message get (per message) | N (up to 50) | Medium |

**Worst-case scenario** (day with 10 new buy orders for 10 different stocks):

- Total Kite API calls: 1 (orders) + 1 (holdings) + 1 (fund) + 1 (GTT list) + 10 (GTT create) + 10 (GTT verify) = **24 calls**.
- All serialized via `Semaphore(1)` to prevent burst rate limiting.

---

## 8. Offline Behavior

Since KiteWatch is a local-first app, most functionality works offline:

| Feature | Online Required | Offline Behavior |
|---|---|---|
| Portfolio screen | No | Renders from local DB |
| Holdings screen | No | Renders from local DB |
| Orders screen | No | Renders from local DB |
| Transactions screen | No | Renders from local DB |
| Add fund entry (manual) | No | Saved locally; reconciliation deferred |
| Edit profit target | No | Saved locally; GTT update queued for next online sync |
| Order sync | Yes | Worker waits for network (WorkManager constraint) |
| Fund reconciliation | Yes | Worker waits for network |
| GTT operations | Yes | Deferred; marked as PENDING |
| Gmail scan | Yes | Worker waits for network |
| Backup to Drive | Yes | Fallback to local backup |
| Backup local | No | Works offline |
| CSV import | No | Fully local |
| Excel export | No | Fully local |
