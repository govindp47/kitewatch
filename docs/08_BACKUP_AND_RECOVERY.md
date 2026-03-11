# 08 — Backup and Recovery

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-10

---

## 1. Backup Architecture Overview

```
┌────────────────────────────────────────────────────────────────┐
│                      BACKUP SYSTEM                             │
│                                                                │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐     │
│  │  Data Layer  │───▶│ Serialization│───▶│ Compression  │     │
│  │  (Room DB)   │    │ (Protobuf)   │    │ (GZIP)       │     │
│  └──────────────┘    └──────────────┘    └──────┬───────┘     │
│                                                  │             │
│                                           ┌──────▼───────┐    │
│                                           │ Integrity    │    │
│                                           │ (SHA-256)    │    │
│                                           └──────┬───────┘    │
│                                                  │             │
│                                    ┌─────────────┼─────────┐  │
│                                    ▼                       ▼  │
│                            ┌──────────────┐       ┌──────────┐│
│                            │ Google Drive │       │  Local   ││
│                            │ (primary)    │       │ Storage  ││
│                            └──────────────┘       │(fallback)││
│                                                   └──────────┘│
└────────────────────────────────────────────────────────────────┘
```

---

## 2. Backup File Format

### 2.1 File Structure

```
.kwbackup file layout:
┌──────────────────────────────────┐
│  HEADER (fixed-size, plaintext)  │
│  ├── Magic bytes: "KWBK"        │
│  ├── Format version: uint32     │
│  ├── Schema version: uint32     │
│  ├── Created at: ISO-8601 (64b) │
│  ├── Account ID: string (32b)   │
│  ├── Payload size: uint64       │
│  └── SHA-256 checksum: 32 bytes │
├──────────────────────────────────┤
│  PAYLOAD (GZIP-compressed       │
│           Protobuf binary)      │
│  ├── orders[]                   │
│  ├── holdings[]                 │
│  ├── transactions[]             │
│  ├── fund_entries[]             │
│  ├── charge_rates[]             │
│  ├── gtt_records[]              │
│  ├── order_holdings_links[]     │
│  ├── pnl_monthly_cache[]       │
│  ├── gmail_scan_cache[]         │
│  ├── gmail_filters[]            │
│  ├── persistent_alerts[]        │
│  └── user_preferences{}         │
└──────────────────────────────────┘
```

### 2.2 Why Protobuf?

| Consideration | JSON | Protobuf | Decision |
|---|---|---|---|
| Size (3-year dataset ~5MB DB) | ~8 MB | ~3 MB | **Protobuf** — 60% smaller |
| Compression (GZIP) | ~2 MB | ~1 MB | Protobuf compresses better |
| Schema evolution | Fragile (field renames break) | Forward + backward compatible | **Protobuf** — built-in versioning |
| Parse speed | Slower (reflection-based) | Faster (generated code) | **Protobuf** |
| Human readability | Yes | No | JSON (but not needed for backup) |

### 2.3 Protobuf Schema Definition

```protobuf
syntax = "proto3";
package com.kitewatch.backup;

message KiteWatchBackup {
    BackupHeader header = 1;
    repeated Order orders = 2;
    repeated Holding holdings = 3;
    repeated Transaction transactions = 4;
    repeated FundEntry fund_entries = 5;
    repeated ChargeRate charge_rates = 6;
    repeated GttRecord gtt_records = 7;
    repeated OrderHoldingsLink order_holdings_links = 8;
    repeated PnlMonthlyCache pnl_cache = 9;
    repeated GmailScanCache gmail_cache = 10;
    repeated GmailFilter gmail_filters = 11;
    repeated PersistentAlert alerts = 12;
    UserPreferences preferences = 13;
}

message BackupHeader {
    uint32 format_version = 1;       // Backup format version (independent of schema)
    uint32 schema_version = 2;       // Room DB schema version at time of backup
    string created_at = 3;           // ISO-8601 UTC
    string account_id = 4;           // Zerodha user ID
    string app_version = 5;          // e.g., "1.2.0"
    uint64 record_count = 6;         // Total records across all tables
}

message Order {
    int64 id = 1;
    string zerodha_order_id = 2;
    string stock_code = 3;
    string stock_name = 4;
    string exchange = 5;
    string order_type = 6;           // "BUY" or "SELL"
    int32 quantity = 7;
    int64 price_paisa = 8;
    int64 total_value_paisa = 9;
    string trade_date = 10;
    string trade_timestamp = 11;
    string settlement_id = 12;
    int32 instrument_token = 13;
    string source = 14;
    string created_at = 15;
}

message Holding {
    int64 id = 1;
    string stock_code = 2;
    string stock_name = 3;
    string exchange = 4;
    int32 quantity = 5;
    int64 invested_amount_paisa = 6;
    int64 avg_buy_price_paisa = 7;
    int64 total_buy_charges_paisa = 8;
    string profit_target_type = 9;
    int32 profit_target_value = 10;
    int64 target_sell_price_paisa = 11;
    int32 instrument_token = 12;
    string created_at = 13;
    string updated_at = 14;
}

message Transaction {
    int64 id = 1;
    string type = 2;
    string sub_type = 3;
    string reference_id = 4;
    string reference_type = 5;
    string stock_code = 6;
    int64 amount_paisa = 7;
    int64 running_fund_balance_paisa = 8;
    string description = 9;
    string transaction_date = 10;
    string source = 11;
    string created_at = 12;
}

message FundEntry {
    int64 id = 1;
    string entry_type = 2;
    int64 amount_paisa = 3;
    string entry_date = 4;
    string note = 5;
    bool is_gmail_detected = 6;
    string gmail_message_id = 7;
    string reconciliation_id = 8;
    string created_at = 9;
}

message ChargeRate {
    int64 id = 1;
    string rate_type = 2;
    int64 rate_value = 3;
    string rate_unit = 4;
    string effective_from = 5;
    string fetched_at = 6;
    bool is_current = 7;
}

message GttRecord {
    int64 id = 1;
    int32 zerodha_gtt_id = 2;
    string stock_code = 3;
    string trigger_type = 4;
    int64 trigger_price_paisa = 5;
    int32 sell_quantity = 6;
    string gtt_status = 7;
    bool is_app_managed = 8;
    int64 app_calculated_price = 9;
    bool manual_override_detected = 10;
    int64 holding_id = 11;
    string last_synced_at = 12;
    bool is_archived = 13;
    string created_at = 14;
    string updated_at = 15;
}

message OrderHoldingsLink {
    int64 id = 1;
    int64 order_id = 2;
    int64 holding_id = 3;
    int32 quantity = 4;
}

message PnlMonthlyCache {
    int64 id = 1;
    string year_month = 2;
    int64 total_sell_value_paisa = 3;
    int64 total_buy_cost_sold_paisa = 4;
    int64 total_buy_charges_paisa = 5;
    int64 total_sell_charges_paisa = 6;
    int64 realized_pnl_paisa = 7;
    int64 invested_value_paisa = 8;
    int32 order_count = 9;
    string last_updated_at = 10;
}

message GmailScanCache {
    int64 id = 1;
    string gmail_message_id = 2;
    string detected_type = 3;
    int64 detected_amount_paisa = 4;
    string email_date = 5;
    string email_subject = 6;
    string email_sender = 7;
    string status = 8;
    int64 linked_fund_entry_id = 9;
    string scanned_at = 10;
}

message GmailFilter {
    int64 id = 1;
    string filter_type = 2;
    string filter_value = 3;
    bool is_active = 4;
    string created_at = 5;
}

message PersistentAlert {
    int64 id = 1;
    string alert_type = 2;
    string severity = 3;
    string payload = 4;
    bool acknowledged = 5;
    string created_at = 6;
    string resolved_at = 7;
    string resolved_by = 8;
}

message UserPreferences {
    string theme_mode = 1;
    repeated string order_sync_times = 2;
    string reconciliation_time = 3;
    int32 charge_rate_refresh_days = 4;
    int64 reconciliation_tolerance_paisa = 5;
    bool gmail_enabled = 6;
    bool scheduled_backup_enabled = 7;
    int32 backup_interval_days = 8;
    string backup_destination = 9;
    int32 lock_timeout_minutes = 10;
    string default_profit_target_type = 11;
    int32 default_profit_target_value = 12;
}
```

---

## 3. Backup Engine Implementation

### 3.1 CreateBackupUseCase

```kotlin
class CreateBackupUseCase @Inject constructor(
    private val database: KiteWatchDatabase,
    private val driveDataSource: GoogleDriveRemoteDataSource,
    private val preferencesRepo: PreferencesRepository,
    private val backupHistoryDao: BackupHistoryDao,
    private val syncEventRepo: SyncEventRepository,
    private val mutexRegistry: MutexRegistry,
) {
    suspend fun execute(
        destination: BackupDestination = BackupDestination.GOOGLE_DRIVE,
    ): Result<BackupResult> = mutexRegistry.get("backup").withLock {

        val eventId = syncEventRepo.logStart(SyncEventType.BACKUP)

        try {
            // 1. Assemble data from all tables
            val backupData = assembleBackupData()

            // 2. Serialize to Protobuf
            val protoBytes = backupData.toByteArray()

            // 3. Compress with GZIP
            val compressed = gzipCompress(protoBytes)

            // 4. Compute SHA-256 checksum of compressed payload
            val checksum = sha256(compressed)

            // 5. Build header
            val header = buildHeader(
                schemaVersion = database.openHelper.readableDatabase.version,
                accountId = database.accountBindingDao().get()?.zerodhaUserId ?: "",
                payloadSize = compressed.size.toLong(),
                checksum = checksum,
            )

            // 6. Combine header + payload into .kwbackup file
            val fileBytes = header + compressed
            val fileName = "kitewatch_backup_${Instant.now().toFileName()}.kwbackup"

            // 7. Upload / save
            val result = when (destination) {
                BackupDestination.GOOGLE_DRIVE -> uploadToDrive(fileName, fileBytes)
                BackupDestination.LOCAL -> saveLocally(fileName, fileBytes)
            }

            // 8. Record in backup_history
            backupHistoryDao.insert(BackupHistoryEntity(
                backupType = "FULL",
                destination = destination.name,
                fileName = fileName,
                fileSizeBytes = fileBytes.size.toLong(),
                schemaVersion = database.openHelper.readableDatabase.version,
                driveFileId = (result as? UploadResult.DriveSuccess)?.fileId,
                status = "SUCCESS",
            ))

            syncEventRepo.logComplete(eventId, SyncStatus.SUCCESS,
                "Backup created: $fileName (${fileBytes.size} bytes)")

            return Result.success(BackupResult.Success(fileName, fileBytes.size.toLong()))

        } catch (e: Exception) {
            // Fallback to local on Drive failure
            if (destination == BackupDestination.GOOGLE_DRIVE) {
                Timber.w(e, "Drive backup failed, falling back to local")
                return execute(BackupDestination.LOCAL).also {
                    backupHistoryDao.insert(BackupHistoryEntity(
                        // ... status = "LOCAL_FALLBACK"
                        status = "LOCAL_FALLBACK",
                        errorMessage = e.message,
                        destination = "LOCAL",
                        /* ... */
                    ))
                }
            }
            syncEventRepo.logComplete(eventId, SyncStatus.FAILED, e.message)
            return Result.failure(e)
        }
    }

    private suspend fun assembleBackupData(): KiteWatchBackupProto {
        return database.withTransaction {
            KiteWatchBackupProto(
                header = buildProtoHeader(),
                orders = database.orderDao().getAll().map { it.toProto() },
                holdings = database.holdingDao().getAll().map { it.toProto() },
                transactions = database.transactionDao().getAll().map { it.toProto() },
                fundEntries = database.fundEntryDao().getAll().map { it.toProto() },
                chargeRates = database.chargeRateDao().getAll().map { it.toProto() },
                gttRecords = database.gttRecordDao().getAll().map { it.toProto() },
                orderHoldingsLinks = database.orderHoldingsLinkDao().getAll().map { it.toProto() },
                pnlCache = database.pnlCacheDao().getAll().map { it.toProto() },
                gmailCache = database.gmailScanCacheDao().getAll().map { it.toProto() },
                gmailFilters = database.gmailFilterDao().getAll().map { it.toProto() },
                alerts = database.alertDao().getAll().map { it.toProto() },
                preferences = preferencesRepo.exportAll().toProto(),
            )
        }
    }
}
```

### 3.2 GZIP Compression

```kotlin
fun gzipCompress(data: ByteArray): ByteArray {
    val baos = ByteArrayOutputStream()
    GZIPOutputStream(baos).use { gzip ->
        gzip.write(data)
    }
    return baos.toByteArray()
}

fun gzipDecompress(data: ByteArray): ByteArray {
    return GZIPInputStream(ByteArrayInputStream(data)).use { gzip ->
        gzip.readBytes()
    }
}
```

### 3.3 SHA-256 Integrity Check

```kotlin
fun sha256(data: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(data)
}

fun verifyChecksum(payload: ByteArray, expectedChecksum: ByteArray): Boolean {
    val actualChecksum = sha256(payload)
    return MessageDigest.isEqual(actualChecksum, expectedChecksum)
}
```

---

## 4. Restore Engine Implementation

### 4.1 RestoreBackupUseCase

```kotlin
class RestoreBackupUseCase @Inject constructor(
    private val database: KiteWatchDatabase,
    private val driveDataSource: GoogleDriveRemoteDataSource,
    private val preferencesRepo: PreferencesRepository,
    private val syncEventRepo: SyncEventRepository,
    private val mutexRegistry: MutexRegistry,
    private val accountValidator: AccountValidator,
) {
    suspend fun execute(source: RestoreSource): Result<RestoreResult> =
        mutexRegistry.get("backup").withLock {

            val eventId = syncEventRepo.logStart(SyncEventType.RESTORE)

            try {
                // 1. Read file bytes
                val fileBytes = when (source) {
                    is RestoreSource.Drive -> driveDataSource.downloadBackup(source.fileId)
                    is RestoreSource.Local -> File(source.filePath).readBytes()
                }

                // 2. Parse header (first N bytes)
                val header = parseHeader(fileBytes)

                // 3. Validate magic bytes
                if (!header.magicBytes.contentEquals("KWBK".toByteArray())) {
                    return Result.failure(InvalidBackupFile("Invalid file format"))
                }

                // 4. Validate account ID matches bound account
                val boundAccount = database.accountBindingDao().get()
                if (boundAccount != null && header.accountId != boundAccount.zerodhaUserId) {
                    return Result.failure(AccountMismatch(
                        expected = boundAccount.zerodhaUserId,
                        found = header.accountId,
                    ))
                }

                // 5. Extract and verify payload
                val compressedPayload = extractPayload(fileBytes, header)
                if (!verifyChecksum(compressedPayload, header.checksum)) {
                    return Result.failure(ChecksumMismatch("Backup file is corrupted"))
                }

                // 6. Decompress
                val protoBytes = gzipDecompress(compressedPayload)

                // 7. Deserialize Protobuf
                val backupData = KiteWatchBackupProto.parseFrom(protoBytes)

                // 8. Schema migration (if backup is from older schema version)
                val migratedData = if (header.schemaVersion < database.currentSchemaVersion) {
                    migrateBackupData(backupData, header.schemaVersion, database.currentSchemaVersion)
                } else {
                    backupData
                }

                // 9. Restore (full replacement within transaction)
                database.withTransaction {
                    // Clear all existing data
                    clearAllTables()

                    // Insert restored data
                    migratedData.orders.forEach { database.orderDao().insertForRestore(it.toEntity()) }
                    migratedData.holdings.forEach { database.holdingDao().insertForRestore(it.toEntity()) }
                    migratedData.transactions.forEach { database.transactionDao().insertForRestore(it.toEntity()) }
                    migratedData.fundEntries.forEach { database.fundEntryDao().insertForRestore(it.toEntity()) }
                    migratedData.chargeRates.forEach { database.chargeRateDao().insertForRestore(it.toEntity()) }
                    migratedData.gttRecords.forEach { database.gttRecordDao().insertForRestore(it.toEntity()) }
                    migratedData.orderHoldingsLinks.forEach { database.orderHoldingsLinkDao().insertForRestore(it.toEntity()) }
                    migratedData.pnlCache.forEach { database.pnlCacheDao().insertForRestore(it.toEntity()) }
                    migratedData.gmailCache.forEach { database.gmailScanCacheDao().insertForRestore(it.toEntity()) }
                    migratedData.gmailFilters.forEach { database.gmailFilterDao().insertForRestore(it.toEntity()) }
                    migratedData.alerts.forEach { database.alertDao().insertForRestore(it.toEntity()) }
                }

                // 10. Restore preferences
                preferencesRepo.importAll(migratedData.preferences.toDomain())

                // 11. Recalculate derived data
                recalculatePnlCache()
                recalculateTargetPrices()

                syncEventRepo.logComplete(eventId, SyncStatus.SUCCESS,
                    "Restored from ${source.description}: ${header.recordCount} records")

                return Result.success(RestoreResult.Success(
                    recordCount = header.recordCount.toInt(),
                    backupDate = Instant.parse(header.createdAt),
                    schemaVersion = header.schemaVersion,
                ))

            } catch (e: Exception) {
                syncEventRepo.logComplete(eventId, SyncStatus.FAILED, e.message)
                return Result.failure(e)
            }
        }

    private suspend fun clearAllTables() {
        // Clear in reverse dependency order
        database.workerHandoffDao().deleteAll()
        database.alertDao().deleteAll()
        database.gmailScanCacheDao().deleteAll()
        database.gmailFilterDao().deleteAll()
        database.pnlCacheDao().deleteAll()
        database.orderHoldingsLinkDao().deleteAll()
        database.gttRecordDao().deleteAll()
        database.chargeRateDao().deleteAll()
        database.fundEntryDao().deleteAll()
        database.transactionDao().deleteAll()
        database.holdingDao().deleteAll()
        database.orderDao().deleteAll()
        // Note: account_binding is NOT cleared — device remains bound
    }
}
```

### 4.2 Backup Schema Migration

```kotlin
object BackupMigrationEngine {
    /**
     * Migrate backup data from an older schema version to current.
     * Migrations are applied sequentially (v1→v2, v2→v3, etc.)
     */
    fun migrate(
        data: KiteWatchBackupProto,
        fromVersion: Int,
        toVersion: Int,
    ): KiteWatchBackupProto {
        var migrated = data
        for (version in fromVersion until toVersion) {
            migrated = applyMigration(migrated, version, version + 1)
        }
        return migrated
    }

    private fun applyMigration(
        data: KiteWatchBackupProto,
        from: Int,
        to: Int,
    ): KiteWatchBackupProto {
        return when (from to to) {
            1 to 2 -> migrateV1ToV2(data)
            // Future migrations added here
            else -> throw UnsupportedMigration(from, to)
        }
    }

    private fun migrateV1ToV2(data: KiteWatchBackupProto): KiteWatchBackupProto {
        // Example: v2 adds instrument_token to holdings
        return data.copy(
            holdings = data.holdings.map { holding ->
                holding.copy(instrumentToken = 0) // Default value for new field
            },
            header = data.header.copy(schemaVersion = 2),
        )
    }
}
```

---

## 5. Backup Scheduling and Retention

### 5.1 Scheduling Strategy

| Backup Type | Trigger | Default Frequency | Configurable |
|---|---|---|---|
| Scheduled backup | WorkManager periodic | Disabled by default | Yes: 1–30 days |
| Manual backup | User action from Settings | On-demand | N/A |
| Pre-import backup | Automatic before CSV import | Always | No |
| Pre-restore backup | Automatic before restore | Always | No |

### 5.2 Retention Policy

**Google Drive:**

- Keep the last **10 backups** by default.
- When creating the 11th backup, delete the oldest.
- User can configure retention count: 5, 10, 20, or unlimited.
- Deletion is best-effort; failure to delete old backups does not block new backup creation.

```kotlin
suspend fun enforceRetention(maxBackups: Int) {
    val existingBackups = driveDataSource.listBackups()
    if (existingBackups.size >= maxBackups) {
        val toDelete = existingBackups
            .sortedBy { it.createdAt }
            .take(existingBackups.size - maxBackups + 1)
        toDelete.forEach { backup ->
            try {
                driveDataSource.deleteFile(backup.id)
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete old backup: ${backup.name}")
                // Non-blocking; continue with new backup
            }
        }
    }
}
```

**Local Storage:**

- Keep the last **5 backups** locally.
- Older local backups are automatically deleted.
- Local backups are stored in app-private storage: `getFilesDir()/backups/`.

### 5.3 Pre-Operation Safety Backups

Before any destructive or bulk operation, an automatic backup is created:

```kotlin
class ImportCsvUseCase @Inject constructor(
    private val createBackupUseCase: CreateBackupUseCase,
    // ...
) {
    suspend fun execute(csvFile: Uri): Result<ImportResult> {
        // Safety backup before import
        val backupResult = createBackupUseCase.execute(BackupDestination.LOCAL)
        if (backupResult.isFailure) {
            Timber.w("Pre-import backup failed; proceeding with import anyway")
            // Log warning but DO proceed — backup failure shouldn't block import
        }

        // ... proceed with import
    }
}
```

---

## 6. Recovery Scenarios

### 6.1 Scenario Matrix

| # | Scenario | Detection | Recovery Procedure |
|---|---|---|---|
| R-01 | User switches to new device | Manual action | Install app → onboard → restore from Drive backup |
| R-02 | User reinstalls app | Manual action | Re-onboard → restore from Drive backup |
| R-03 | Database corruption | SQLCipher integrity check fails on open | Prompt user to restore from most recent backup |
| R-04 | Accidental data modification | User notices incorrect data | Restore from backup created before the modification |
| R-05 | Bad CSV import | Garbled/wrong data imported | Import creates pre-import backup; restore that backup |
| R-06 | Failed restore | Restore crashes mid-transaction | Room transaction auto-rollback; previous data intact |
| R-07 | Google Drive access revoked | Drive API returns 403 | Fallback to local backup; prompt user to re-authorize |
| R-08 | Old backup on new app version | Schema version mismatch | BackupMigrationEngine applies sequential migrations |
| R-09 | Backup file corrupted | SHA-256 checksum mismatch | Abort restore; prompt to try a different backup file |
| R-10 | Backup from different account | Account ID mismatch in header | Reject restore with clear error message |

### 6.2 Recovery Flow: New Device

```
1. Install KiteWatch on new device
2. Complete onboarding: T&C → biometric → Zerodha login → Google Sign-In
3. Navigate to Settings → Backup & Restore
4. App lists available backups from Google Drive (sorted newest first)
5. User selects a backup:
   a. Account ID is verified against newly bound account
   b. If mismatch → error "This backup belongs to a different account"
   c. If match → show backup details (date, record count, app version)
6. User confirms restore
7. Full database restore within a single Room transaction
8. On success:
   a. All screens refresh from restored data
   b. WorkManager workers re-register with restored schedule preferences
   c. GTT records are verified against Zerodha on next sync
   d. Toast: "Restored {N} records from backup dated {date}"
9. On failure:
   a. Transaction rolled back — new device has empty DB
   b. Error shown: "Restore failed: {reason}. Please try another backup."
```

### 6.3 Recovery Flow: Database Corruption

```
1. App opens → SQLCipher attempts to open database
2. Integrity check fails → PRAGMA integrity_check returns error
3. App enters degraded mode:
   a. Shows error screen: "Database Error Detected"
   b. Provides two options:
      • "Restore from Backup" → navigates to backup list
      • "Start Fresh" → clears database, re-runs onboarding
4. If user chooses restore:
   a. A new empty database is created (SQLCipher, same passphrase)
   b. Restore process runs as per normal restore flow
   c. Corrupted database file is renamed to `kitewatch_corrupted_{timestamp}.db`
      and kept for 7 days (for potential forensic analysis)
5. If user chooses start fresh:
   a. Database file deleted → new empty encrypted database created
   b. Account binding preserved from EncryptedSharedPreferences
   c. User can import historical data via CSV later
```

---

## 7. Backup Verification

### 7.1 Post-Backup Verification

After every backup creation, the backup file is partially verified:

```kotlin
suspend fun verifyBackup(fileBytes: ByteArray): VerificationResult {
    // 1. Parse header
    val header = parseHeader(fileBytes)
    if (!header.magicBytes.contentEquals("KWBK".toByteArray())) {
        return VerificationResult.Invalid("Bad magic bytes")
    }

    // 2. Verify checksum
    val payload = extractPayload(fileBytes, header)
    if (!verifyChecksum(payload, header.checksum)) {
        return VerificationResult.Invalid("Checksum mismatch")
    }

    // 3. Attempt partial deserialization (header + first record of each table)
    val decompressed = gzipDecompress(payload)
    val backup = KiteWatchBackupProto.parseFrom(decompressed)

    // 4. Verify record counts match expected
    val totalRecords = backup.orders.size + backup.holdings.size +
                       backup.transactions.size + backup.fundEntries.size +
                       backup.chargeRates.size + backup.gttRecords.size

    return VerificationResult.Valid(
        recordCount = totalRecords,
        schemaVersion = header.schemaVersion,
        createdAt = header.createdAt,
    )
}
```

### 7.2 Periodic Backup Health Check

A maintenance task (weekly) verifies the most recent backup is accessible:

```kotlin
class BackupHealthCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveDataSource: GoogleDriveRemoteDataSource,
    private val alertRepo: AlertRepository,
    private val backupHistoryDao: BackupHistoryDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val lastBackup = backupHistoryDao.getMostRecentSuccess()
            ?: return Result.success() // No backups exist yet

        val daysSinceBackup = Duration.between(
            Instant.parse(lastBackup.createdAt),
            Instant.now(),
        ).toDays()

        if (daysSinceBackup > 30) {
            alertRepo.insert(PersistentAlert(
                alertType = "BACKUP_STALE",
                severity = "WARNING",
                payload = """{"days_since_backup": $daysSinceBackup, "last_backup": "${lastBackup.createdAt}"}""",
            ))
        }

        // Verify Drive backup is still accessible (if Drive is the destination)
        if (lastBackup.destination == "GOOGLE_DRIVE" && lastBackup.driveFileId != null) {
            try {
                driveDataSource.getFileMetadata(lastBackup.driveFileId!!)
            } catch (e: Exception) {
                alertRepo.insert(PersistentAlert(
                    alertType = "BACKUP_INACCESSIBLE",
                    severity = "CRITICAL",
                    payload = """{"drive_file_id": "${lastBackup.driveFileId}", "error": "${e.message}"}""",
                ))
            }
        }

        return Result.success()
    }
}
```

---

## 8. Excel Export

### 8.1 Export Scope

Excel export (`.xlsx`) is a **reporting** feature, not a backup feature. It exports human-readable financial reports, not raw database records.

| Report Type | Sheets | Content |
|---|---|---|
| Portfolio Report | Summary, Holdings, P&L Monthly | Current portfolio state with calculations |
| Transaction Report | All Transactions, Charge Breakdown | Filtered or full transaction history |
| Order Report | Buy Orders, Sell Orders | Filtered or full order history |

### 8.2 Excel Generator

```kotlin
class ExcelReportGenerator @Inject constructor() {

    fun generatePortfolioReport(
        holdings: List<Holding>,
        pnlSummary: PnlSummary,
        chargeBreakdown: Map<String, ChargeBreakdown>,
    ): ByteArray {
        val workbook = SXSSFWorkbook(100) // Streaming: keeps 100 rows in memory

        // Summary sheet
        val summarySheet = workbook.createSheet("Summary")
        writeSummary(summarySheet, pnlSummary)

        // Holdings sheet
        val holdingsSheet = workbook.createSheet("Holdings")
        writeHoldings(holdingsSheet, holdings, workbook)

        // P&L sheet
        // ...

        val baos = ByteArrayOutputStream()
        workbook.write(baos)
        workbook.dispose() // Clean up temp files (SXSSFWorkbook)
        return baos.toByteArray()
    }

    private fun writeHoldings(sheet: SXSSFSheet, holdings: List<Holding>, workbook: SXSSFWorkbook) {
        // Header row
        val headerStyle = createHeaderStyle(workbook)
        val headerRow = sheet.createRow(0)
        val headers = listOf("Stock", "Qty", "Avg Price (₹)", "Invested (₹)",
            "Buy Charges (₹)", "Target Price (₹)", "Target %")
        headers.forEachIndexed { i, h ->
            headerRow.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        // Data rows
        holdings.forEachIndexed { index, holding ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(holding.stockCode)
            row.createCell(1).setCellValue(holding.quantity.toDouble())
            row.createCell(2).setCellValue(holding.avgBuyPrice.toRupees().toDouble())
            row.createCell(3).setCellValue(holding.investedAmount.toRupees().toDouble())
            row.createCell(4).setCellValue(holding.totalBuyCharges.toRupees().toDouble())
            row.createCell(5).setCellValue(holding.targetSellPrice.toRupees().toDouble())
            // ... etc.
        }

        // Auto-size columns
        headers.indices.forEach { sheet.setColumnWidth(it, 4000) }
    }
}
```

### 8.3 Export Sharing

```kotlin
// After generating the Excel file
val file = File(context.cacheDir, "KiteWatch_Portfolio_${LocalDate.now()}.xlsx")
file.writeBytes(excelBytes)

val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

val shareIntent = Intent(Intent.ACTION_SEND).apply {
    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
context.startActivity(Intent.createChooser(shareIntent, "Export Portfolio Report"))
```

---

## 9. Data Tables Not Included in Backup

| Table | Reason |
|---|---|
| `sync_event_log` | Operational audit — device-specific, not portable |
| `worker_handoff` | Transient work state — irrelevant after restore |
| `backup_history` | Records are device-specific; new history starts on restore |
| `account_binding` | Restored from EncryptedSharedPreferences; re-established during onboarding on new device |
