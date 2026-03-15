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
import com.kitewatch.infra.backup.PreferencesRestorer
import com.kitewatch.infra.backup.datasource.GoogleDriveRemoteDataSource
import com.kitewatch.infra.backup.proto.KiteWatchBackup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreBackupUseCaseTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val orderDao = mockk<OrderDao>(relaxed = true)
    private val holdingDao = mockk<HoldingDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val fundEntryDao = mockk<FundEntryDao>(relaxed = true)
    private val chargeRateDao = mockk<ChargeRateDao>(relaxed = true)
    private val gttRecordDao = mockk<GttRecordDao>(relaxed = true)
    private val orderHoldingDao = mockk<OrderHoldingDao>(relaxed = true)
    private val pnlDao = mockk<PnlMonthlyCacheDao>(relaxed = true)
    private val gmailCacheDao = mockk<GmailScanCacheDao>(relaxed = true)
    private val gmailFilterDao = mockk<GmailFilterDao>(relaxed = true)
    private val alertDao = mockk<AlertDao>(relaxed = true)
    private val driveDataSource = mockk<GoogleDriveRemoteDataSource>()
    private val preferencesRestorer = mockk<PreferencesRestorer>(relaxed = true)

    /** Executes the transaction block directly — no real database needed. */
    private val transactionRunner = DatabaseTransactionRunner { block -> block() }

    private lateinit var useCase: RestoreBackupUseCase

    @Before
    fun setUp() {
        useCase =
            RestoreBackupUseCase(
                orderDao = orderDao,
                holdingDao = holdingDao,
                transactionDao = transactionDao,
                fundEntryDao = fundEntryDao,
                chargeRateDao = chargeRateDao,
                gttRecordDao = gttRecordDao,
                orderHoldingDao = orderHoldingDao,
                pnlMonthlyCacheDao = pnlDao,
                gmailScanCacheDao = gmailCacheDao,
                gmailFilterDao = gmailFilterDao,
                alertDao = alertDao,
                driveDataSource = driveDataSource,
                preferencesRestorer = preferencesRestorer,
                transactionRunner = transactionRunner,
            )
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private fun buildValidBackupBytes(
        accountId: String = "ZD1234",
        schemaVersion: Int = CreateBackupUseCase.SCHEMA_VERSION,
    ): ByteArray {
        val protoBytes = KiteWatchBackup.newBuilder().build().toByteArray()
        val compressed = BackupIntegrityUtil.gzipCompress(protoBytes)
        return BackupFileWriter.buildFile(schemaVersion, accountId, compressed)
    }

    // -----------------------------------------------------------------------
    // Round-trip restore — local source
    // -----------------------------------------------------------------------

    @Test
    fun `execute local source returns Success with correct metadata`() =
        runTest {
            val accountId = "ZD1234"
            val fileBytes = buildValidBackupBytes(accountId)
            val backupFile = tempFolder.newFile("test.kwbackup").also { it.writeBytes(fileBytes) }

            val result = useCase.execute(RestoreSource.Local(backupFile.absolutePath), accountId)

            assertTrue(result is RestoreResult.Success)
            val success = result as RestoreResult.Success
            assertEquals(accountId, success.accountId)
            assertEquals(CreateBackupUseCase.SCHEMA_VERSION, success.schemaVersion)
            assertEquals(0L, success.recordCount) // empty proto
        }

    // -----------------------------------------------------------------------
    // Round-trip restore — Drive source
    // -----------------------------------------------------------------------

    @Test
    fun `execute Drive source downloads file and restores successfully`() =
        runTest {
            val accountId = "ZD5678"
            val fileBytes = buildValidBackupBytes(accountId)
            coEvery { driveDataSource.downloadBackup("drive-file-id") } returns fileBytes

            val result = useCase.execute(RestoreSource.Drive("drive-file-id"), accountId)

            assertTrue(result is RestoreResult.Success)
            coVerify(exactly = 1) { driveDataSource.downloadBackup("drive-file-id") }
        }

    // -----------------------------------------------------------------------
    // Preferences restored
    // -----------------------------------------------------------------------

    @Test
    fun `execute calls preferencesRestorer after database restore`() =
        runTest {
            val accountId = "ZD1234"
            val fileBytes = buildValidBackupBytes(accountId)
            val backupFile = tempFolder.newFile("prefs.kwbackup").also { it.writeBytes(fileBytes) }

            useCase.execute(RestoreSource.Local(backupFile.absolutePath), accountId)

            coVerify(exactly = 1) { preferencesRestorer.importAll(any()) }
        }

    // -----------------------------------------------------------------------
    // clearAllTables — all 11 tables deleted
    // -----------------------------------------------------------------------

    @Test
    fun `clearAllTables deletes all 11 data tables before inserting restored data`() =
        runTest {
            val accountId = "ZD1234"
            val fileBytes = buildValidBackupBytes(accountId)
            val backupFile = tempFolder.newFile("fk.kwbackup").also { it.writeBytes(fileBytes) }

            useCase.execute(RestoreSource.Local(backupFile.absolutePath), accountId)

            coVerify(exactly = 1) { alertDao.deleteAll() }
            coVerify(exactly = 1) { gmailFilterDao.deleteAll() }
            coVerify(exactly = 1) { gmailCacheDao.deleteAll() }
            coVerify(exactly = 1) { pnlDao.deleteAll() }
            coVerify(exactly = 1) { orderHoldingDao.deleteAll() }
            coVerify(exactly = 1) { gttRecordDao.deleteAll() }
            coVerify(exactly = 1) { chargeRateDao.deleteAll() }
            coVerify(exactly = 1) { fundEntryDao.deleteAll() }
            coVerify(exactly = 1) { transactionDao.deleteAll() }
            coVerify(exactly = 1) { holdingDao.deleteAll() }
            coVerify(exactly = 1) { orderDao.deleteAll() }
        }

    // -----------------------------------------------------------------------
    // Account mismatch rejection
    // -----------------------------------------------------------------------

    @Test
    fun `execute throws AccountMismatchException when account ID does not match`() =
        runTest {
            val fileBytes = buildValidBackupBytes(accountId = "ZD1234")
            val backupFile = tempFolder.newFile("mismatch.kwbackup").also { it.writeBytes(fileBytes) }

            var threw = false
            try {
                useCase.execute(RestoreSource.Local(backupFile.absolutePath), accountId = "ZD9999")
            } catch (e: AccountMismatchException) {
                threw = true
                assertEquals("ZD9999", e.expected)
                assertEquals("ZD1234", e.found)
            }
            assertTrue("Expected AccountMismatchException", threw)
        }

    @Test
    fun `execute succeeds when accountId is null (no device binding)`() =
        runTest {
            val fileBytes = buildValidBackupBytes(accountId = "ZD1234")
            val backupFile = tempFolder.newFile("unbound.kwbackup").also { it.writeBytes(fileBytes) }

            val result = useCase.execute(RestoreSource.Local(backupFile.absolutePath), accountId = null)

            assertTrue(result is RestoreResult.Success)
        }

    // -----------------------------------------------------------------------
    // Checksum mismatch rejection
    // -----------------------------------------------------------------------

    @Test
    fun `execute throws ChecksumMismatchException when payload byte is flipped`() =
        runTest {
            val fileBytes = buildValidBackupBytes().toMutableList()
            // Corrupt one byte in the payload (after the 148-byte header)
            fileBytes[BackupFileWriter.HEADER_SIZE] =
                (fileBytes[BackupFileWriter.HEADER_SIZE].toInt() xor 0xFF).toByte()
            val backupFile =
                tempFolder.newFile("corrupt.kwbackup").also { it.writeBytes(fileBytes.toByteArray()) }

            var threw = false
            try {
                useCase.execute(RestoreSource.Local(backupFile.absolutePath), accountId = null)
            } catch (ignored: ChecksumMismatchException) {
                threw = true
            }
            assertTrue("Expected ChecksumMismatchException", threw)
        }

    // -----------------------------------------------------------------------
    // Invalid file rejection
    // -----------------------------------------------------------------------

    @Test
    fun `execute throws InvalidBackupFileException for garbage bytes`() =
        runTest {
            val junkFile =
                tempFolder.newFile("junk.kwbackup").also { it.writeBytes(ByteArray(200) { 0x42 }) }

            var threw = false
            try {
                useCase.execute(RestoreSource.Local(junkFile.absolutePath), accountId = null)
            } catch (ignored: InvalidBackupFileException) {
                threw = true
            }
            assertTrue("Expected InvalidBackupFileException", threw)
        }

    @Test
    fun `execute throws InvalidBackupFileException for file too short for header`() =
        runTest {
            val shortFile =
                tempFolder.newFile("short.kwbackup").also { it.writeBytes(ByteArray(10)) }

            var threw = false
            try {
                useCase.execute(RestoreSource.Local(shortFile.absolutePath), accountId = null)
            } catch (ignored: InvalidBackupFileException) {
                threw = true
            }
            assertTrue("Expected InvalidBackupFileException", threw)
        }

    // -----------------------------------------------------------------------
    // No database writes on validation failure
    // -----------------------------------------------------------------------

    @Test
    fun `no database writes occur when account mismatch is detected`() =
        runTest {
            val fileBytes = buildValidBackupBytes(accountId = "ZD1234")
            val backupFile =
                tempFolder.newFile("no-write.kwbackup").also { it.writeBytes(fileBytes) }

            try {
                useCase.execute(RestoreSource.Local(backupFile.absolutePath), accountId = "ZD9999")
            } catch (_: AccountMismatchException) {
                // expected
            }

            coVerify(exactly = 0) { orderDao.deleteAll() }
            coVerify(exactly = 0) { orderDao.insert(any()) }
        }

    @Test
    fun `no database writes occur when checksum mismatch is detected`() =
        runTest {
            val fileBytes = buildValidBackupBytes().toMutableList()
            fileBytes[BackupFileWriter.HEADER_SIZE] =
                (fileBytes[BackupFileWriter.HEADER_SIZE].toInt() xor 0xFF).toByte()
            val backupFile =
                tempFolder
                    .newFile("no-write-chk.kwbackup")
                    .also { it.writeBytes(fileBytes.toByteArray()) }

            try {
                useCase.execute(RestoreSource.Local(backupFile.absolutePath), accountId = null)
            } catch (_: ChecksumMismatchException) {
                // expected
            }

            coVerify(exactly = 0) { orderDao.deleteAll() }
        }
}
