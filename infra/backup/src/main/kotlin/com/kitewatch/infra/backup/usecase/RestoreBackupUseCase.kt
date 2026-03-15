package com.kitewatch.infra.backup.usecase

import com.kitewatch.database.dao.AlertDao
import com.kitewatch.database.dao.ChargeRateDao
import com.kitewatch.database.dao.FundEntryDao
import com.kitewatch.database.dao.GmailFilterDao
import com.kitewatch.database.dao.GmailScanCacheDao
import com.kitewatch.database.dao.GttRecordDao
import com.kitewatch.database.dao.HoldingDao
import com.kitewatch.database.dao.OrderDao
import com.kitewatch.database.dao.OrderHoldingDao
import com.kitewatch.database.dao.PnlMonthlyCacheDao
import com.kitewatch.database.dao.TransactionDao
import com.kitewatch.infra.backup.BackupFileWriter
import com.kitewatch.infra.backup.BackupIntegrityUtil
import com.kitewatch.infra.backup.BackupMigrationEngine
import com.kitewatch.infra.backup.PreferencesRestorer
import com.kitewatch.infra.backup.datasource.GoogleDriveRemoteDataSource
import com.kitewatch.infra.backup.mapper.toDomain
import com.kitewatch.infra.backup.mapper.toEntity
import com.kitewatch.infra.backup.proto.KiteWatchBackup
import java.io.File
import javax.inject.Inject

/**
 * Restores a `.kwbackup` file to the local Room database.
 *
 * Execution flow:
 * 1. Fetch file bytes from the [RestoreSource] (Drive or local path).
 * 2. Parse and validate the 148-byte binary header (magic bytes, format version, account ID).
 * 3. Verify SHA-256 checksum of the compressed payload.
 * 4. GZIP-decompress the payload.
 * 5. Deserialise the [KiteWatchBackup] Protobuf message.
 * 6. Run [BackupMigrationEngine] if the backup schema version is older than current.
 * 7. Replace all data atomically via [transactionRunner] (clear → insert).
 * 8. Restore user preferences via [PreferencesRestorer].
 *
 * Suppressed: LongParameterList (one dependency per DAO), ThrowsCount (each throw is a distinct validation guard).
 */
@Suppress("LongParameterList", "ThrowsCount")
class RestoreBackupUseCase
    @Inject
    constructor(
        private val orderDao: OrderDao,
        private val holdingDao: HoldingDao,
        private val transactionDao: TransactionDao,
        private val fundEntryDao: FundEntryDao,
        private val chargeRateDao: ChargeRateDao,
        private val gttRecordDao: GttRecordDao,
        private val orderHoldingDao: OrderHoldingDao,
        private val pnlMonthlyCacheDao: PnlMonthlyCacheDao,
        private val gmailScanCacheDao: GmailScanCacheDao,
        private val gmailFilterDao: GmailFilterDao,
        private val alertDao: AlertDao,
        private val driveDataSource: GoogleDriveRemoteDataSource,
        private val preferencesRestorer: PreferencesRestorer,
        private val transactionRunner: DatabaseTransactionRunner,
    ) {
        /**
         * @param source      where to read the backup file from.
         * @param accountId   the currently bound Zerodha account ID, or null if no account is bound.
         * @return [RestoreResult.Success] on success, wrapping record counts and backup metadata.
         * @throws InvalidBackupFileException        if magic bytes are wrong or header is malformed.
         * @throws UnsupportedFormatVersionException if the file format version is newer than supported.
         * @throws AccountMismatchException          if the backup belongs to a different account.
         * @throws ChecksumMismatchException         if the payload is corrupted.
         */
        suspend fun execute(
            source: RestoreSource,
            accountId: String?,
        ): RestoreResult {
            // 1. Fetch bytes
            val fileBytes =
                when (source) {
                    is RestoreSource.Drive -> driveDataSource.downloadBackup(source.fileId)
                    is RestoreSource.Local -> File(source.filePath).readBytes()
                }

            // 2. Parse header
            val header =
                BackupFileWriter.parseHeader(fileBytes)
                    ?: throw InvalidBackupFileException("File too short to contain a valid header")

            // 3. Validate magic bytes
            if (!header.isMagicValid()) {
                throw InvalidBackupFileException("Not a .kwbackup file — magic bytes invalid")
            }

            // 4. Validate format version (only FORMAT_VERSION=1 supported)
            if (header.formatVersion != BackupFileWriter.FORMAT_VERSION) {
                throw UnsupportedFormatVersionException(header.formatVersion)
            }

            // 5. Validate account ID (skipped when device has no bound account)
            if (accountId != null && header.accountId != accountId) {
                throw AccountMismatchException(expected = accountId, found = header.accountId)
            }

            // 6. Extract payload and verify checksum
            val compressedPayload = BackupFileWriter.extractPayload(fileBytes)
            if (!BackupIntegrityUtil.verifyChecksum(compressedPayload, header.checksum)) {
                throw ChecksumMismatchException("Backup payload is corrupted — SHA-256 mismatch")
            }

            // 7. Decompress and deserialise
            val protoBytes = BackupIntegrityUtil.gzipDecompress(compressedPayload)
            var backupData = KiteWatchBackup.parseFrom(protoBytes)

            // 8. Apply schema migrations when backup is from an older schema version
            val currentSchemaVersion = CreateBackupUseCase.SCHEMA_VERSION
            if (header.schemaVersion < currentSchemaVersion) {
                backupData =
                    BackupMigrationEngine.migrate(
                        data = backupData,
                        fromVersion = header.schemaVersion,
                        toVersion = currentSchemaVersion,
                    )
            }

            // 9. Atomic clear + insert inside a Room transaction
            val snapshot = backupData
            transactionRunner {
                clearAllTables()
                insertAll(snapshot)
            }

            // 10. Restore preferences (outside transaction; DataStore writes are async)
            preferencesRestorer.importAll(backupData.preferences.toDomain())

            return RestoreResult.Success(
                recordCount = backupData.header.recordCount,
                schemaVersion = header.schemaVersion,
                backupCreatedAt = header.createdAt,
                accountId = header.accountId,
            )
        }

        // ---------------------------------------------------------------------------
        // Private helpers
        // ---------------------------------------------------------------------------

        /** Clears all 11 data tables in reverse FK order. `account_binding` is never touched. */
        private suspend fun clearAllTables() {
            alertDao.deleteAll()
            gmailFilterDao.deleteAll()
            gmailScanCacheDao.deleteAll()
            pnlMonthlyCacheDao.deleteAll()
            orderHoldingDao.deleteAll()
            gttRecordDao.deleteAll()
            chargeRateDao.deleteAll()
            fundEntryDao.deleteAll()
            transactionDao.deleteAll()
            holdingDao.deleteAll()
            orderDao.deleteAll()
        }

        private suspend fun insertAll(data: KiteWatchBackup) {
            data.ordersList.forEach { orderDao.insert(it.toEntity()) }
            data.holdingsList.forEach { holdingDao.upsert(it.toEntity()) }
            data.transactionsList.forEach { transactionDao.insert(it.toEntity()) }
            data.fundEntriesList.forEach { fundEntryDao.insert(it.toEntity()) }
            chargeRateDao.insertAll(data.chargeRatesList.map { it.toEntity() })
            data.gttRecordsList.forEach { gttRecordDao.upsert(it.toEntity()) }
            orderHoldingDao.insertAll(data.orderHoldingsLinksList.map { it.toEntity() })
            data.pnlCacheList.forEach { pnlMonthlyCacheDao.upsert(it.toEntity()) }
            data.gmailCacheList.forEach { gmailScanCacheDao.insert(it.toEntity()) }
            data.gmailFiltersList.forEach { gmailFilterDao.insert(it.toEntity()) }
            data.alertsList.forEach { alertDao.insert(it.toEntity()) }
        }
    }

// ---------------------------------------------------------------------------
// Supporting types
// ---------------------------------------------------------------------------

/**
 * Executes a suspend block within a database transaction.
 * The production implementation wraps `database.withTransaction { }`.
 * A test double can execute the block directly without a real database.
 */
fun interface DatabaseTransactionRunner {
    suspend operator fun invoke(block: suspend () -> Unit)
}

/** Where to read the backup file from. */
sealed class RestoreSource {
    /** Download the file from Google Drive using the given [fileId]. */
    data class Drive(
        val fileId: String,
    ) : RestoreSource()

    /** Read the file from a local absolute [filePath]. */
    data class Local(
        val filePath: String,
    ) : RestoreSource()
}

/** Outcome of a successful restore. */
sealed class RestoreResult {
    data class Success(
        val recordCount: Long,
        val schemaVersion: Int,
        val backupCreatedAt: String,
        val accountId: String,
    ) : RestoreResult()
}

// ---------------------------------------------------------------------------
// Exceptions
// ---------------------------------------------------------------------------

class InvalidBackupFileException(
    message: String,
) : Exception(message)

class AccountMismatchException(
    val expected: String,
    val found: String,
) : Exception("Account mismatch: expected '$expected', found '$found' in backup")

class ChecksumMismatchException(
    message: String,
) : Exception(message)

class UnsupportedFormatVersionException(
    val version: Int,
) : Exception("Unsupported backup format version: $version (max supported: ${BackupFileWriter.FORMAT_VERSION})")
