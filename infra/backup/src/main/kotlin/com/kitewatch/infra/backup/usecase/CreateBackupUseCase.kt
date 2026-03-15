package com.kitewatch.infra.backup.usecase

import android.content.Context
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
import com.kitewatch.infra.backup.BackupHistoryRecorder
import com.kitewatch.infra.backup.BackupIntegrityUtil
import com.kitewatch.infra.backup.PreferencesExporter
import com.kitewatch.infra.backup.datasource.GoogleDriveRemoteDataSource
import com.kitewatch.infra.backup.mapper.toProto
import com.kitewatch.infra.backup.model.BackupDestination
import com.kitewatch.infra.backup.model.BackupResult
import com.kitewatch.infra.backup.proto.BackupHeader
import com.kitewatch.infra.backup.proto.KiteWatchBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Assembles, compresses, checksums, and writes a complete `.kwbackup` file.
 *
 * Execution flow:
 * 1. Read all 12 tables + preferences via the injected DAOs.
 * 2. Build [KiteWatchBackup] protobuf message and serialize to bytes.
 * 3. GZIP-compress the protobuf bytes.
 * 4. Write the binary `.kwbackup` file (148-byte header + compressed payload).
 * 5. Persist to Google Drive via [GoogleDriveRemoteDataSource] (T-064).
 *    On Drive failure: fall back to local storage.
 * 6. Enforce 5-file local retention (oldest file deleted when limit exceeded).
 * 7. Record the outcome via [BackupHistoryRecorder].
 */
@Suppress("LongParameterList") // Hilt injects one DAO per table; no sensible way to reduce
class CreateBackupUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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
        private val preferencesExporter: PreferencesExporter,
        private val historyRecorder: BackupHistoryRecorder,
    ) {
        companion object {
            const val SCHEMA_VERSION = 1
            const val APP_VERSION = "1.0.0"
            const val MAX_LOCAL_FILES = 5
            private val FILE_NAME_FORMATTER: DateTimeFormatter =
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)
        }

        /**
         * @param accountId the Zerodha client ID / account identifier embedded in the file header.
         * @param destination preferred upload target (defaults to [BackupDestination.GOOGLE_DRIVE]).
         * @return [BackupResult.Success] on Drive upload, [BackupResult.LocalFallback] when Drive fails.
         */
        suspend fun execute(
            accountId: String,
            destination: BackupDestination = BackupDestination.GOOGLE_DRIVE,
        ): BackupResult {
            val fileBytes = buildBackupFileBytes(accountId)
            val fileName = buildFileName(accountId)

            return when (destination) {
                BackupDestination.GOOGLE_DRIVE -> uploadToDriveOrFallback(fileName, fileBytes)
                BackupDestination.LOCAL -> {
                    writeLocalFile(fileName, fileBytes)
                    val result =
                        BackupResult.LocalFallback(
                            fileName = fileName,
                            fileSizeBytes = fileBytes.size.toLong(),
                            driveError = null,
                        )
                    historyRecorder.record(
                        fileName = fileName,
                        fileSizeBytes = fileBytes.size.toLong(),
                        destination = BackupDestination.LOCAL,
                        status = "SUCCESS",
                    )
                    result
                }
            }
        }

        // ---------------------------------------------------------------------------
        // Internal helpers
        // ---------------------------------------------------------------------------

        private suspend fun buildBackupFileBytes(accountId: String): ByteArray {
            val protoBytes = assembleProto(accountId).toByteArray()
            val compressed = BackupIntegrityUtil.gzipCompress(protoBytes)
            return BackupFileWriter.buildFile(SCHEMA_VERSION, accountId, compressed)
        }

        private suspend fun assembleProto(accountId: String): KiteWatchBackup {
            val orders = orderDao.getAll()
            val holdings = holdingDao.getAll()
            val transactions = transactionDao.getAll()
            val fundEntries = fundEntryDao.getAll()
            val chargeRates = chargeRateDao.getAll()
            val gttRecords = gttRecordDao.getAll()
            val orderHoldings = orderHoldingDao.getAll()
            val pnlCache = pnlMonthlyCacheDao.getAll()
            val gmailCache = gmailScanCacheDao.getAll()
            val gmailFilters = gmailFilterDao.getAll()
            val alerts = alertDao.getAll()
            val preferences = preferencesExporter.exportAll()

            val totalRecords =
                (
                    orders.size + holdings.size + transactions.size +
                        fundEntries.size + chargeRates.size + gttRecords.size +
                        orderHoldings.size + pnlCache.size + gmailCache.size +
                        gmailFilters.size + alerts.size
                ).toLong()

            val header =
                BackupHeader
                    .newBuilder()
                    .setFormatVersion(BackupFileWriter.FORMAT_VERSION)
                    .setSchemaVersion(SCHEMA_VERSION)
                    .setCreatedAt(Instant.now().toString())
                    .setAccountId(accountId)
                    .setAppVersion(APP_VERSION)
                    .setRecordCount(totalRecords)
                    .build()

            return KiteWatchBackup
                .newBuilder()
                .setHeader(header)
                .addAllOrders(orders.map { it.toProto() })
                .addAllHoldings(holdings.map { it.toProto() })
                .addAllTransactions(transactions.map { it.toProto() })
                .addAllFundEntries(fundEntries.map { it.toProto() })
                .addAllChargeRates(chargeRates.map { it.toProto() })
                .addAllGttRecords(gttRecords.map { it.toProto() })
                .addAllOrderHoldingsLinks(orderHoldings.map { it.toProto() })
                .addAllPnlCache(pnlCache.map { it.toProto() })
                .addAllGmailCache(gmailCache.map { it.toProto() })
                .addAllGmailFilters(gmailFilters.map { it.toProto() })
                .addAllAlerts(alerts.map { it.toProto() })
                .setPreferences(preferences.toProto())
                .build()
        }

        private suspend fun uploadToDriveOrFallback(
            fileName: String,
            fileBytes: ByteArray,
        ): BackupResult =
            try {
                driveDataSource.upload(fileName, fileBytes)
                val result =
                    BackupResult.Success(
                        fileName = fileName,
                        fileSizeBytes = fileBytes.size.toLong(),
                        destination = BackupDestination.GOOGLE_DRIVE,
                    )
                historyRecorder.record(
                    fileName = fileName,
                    fileSizeBytes = fileBytes.size.toLong(),
                    destination = BackupDestination.GOOGLE_DRIVE,
                    status = "SUCCESS",
                )
                result
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // Drive SDK throws varied exceptions
                val driveError = e.message
                writeLocalFile(fileName, fileBytes)
                historyRecorder.record(
                    fileName = fileName,
                    fileSizeBytes = fileBytes.size.toLong(),
                    destination = BackupDestination.LOCAL,
                    status = "LOCAL_FALLBACK",
                    errorMessage = driveError,
                )
                BackupResult.LocalFallback(
                    fileName = fileName,
                    fileSizeBytes = fileBytes.size.toLong(),
                    driveError = driveError,
                )
            }

        private fun writeLocalFile(
            fileName: String,
            fileBytes: ByteArray,
        ) {
            val backupDir = File(context.filesDir, "backups").also { it.mkdirs() }
            File(backupDir, fileName).writeBytes(fileBytes)
            enforceRetention(backupDir)
        }

        /** Deletes the oldest `.kwbackup` files when more than [MAX_LOCAL_FILES] exist. */
        private fun enforceRetention(backupDir: File) {
            val files =
                backupDir.listFiles { f -> f.name.endsWith(".kwbackup") }
                    ?: return
            if (files.size <= MAX_LOCAL_FILES) return
            files
                .sortedBy { it.lastModified() }
                .take(files.size - MAX_LOCAL_FILES)
                .forEach { it.delete() }
        }

        private fun buildFileName(accountId: String): String {
            val timestamp = FILE_NAME_FORMATTER.format(Instant.now())
            return "kitewatch_${accountId}_$timestamp.kwbackup"
        }
    }
