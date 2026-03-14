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
import com.kitewatch.infra.backup.model.BackupDestination
import com.kitewatch.infra.backup.model.BackupResult
import com.kitewatch.infra.backup.model.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CreateBackupUseCaseTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    // Mocks
    private val context = mockk<Context>()
    private val orderDao = mockk<OrderDao>()
    private val holdingDao = mockk<HoldingDao>()
    private val transactionDao = mockk<TransactionDao>()
    private val fundEntryDao = mockk<FundEntryDao>()
    private val chargeRateDao = mockk<ChargeRateDao>()
    private val gttRecordDao = mockk<GttRecordDao>()
    private val orderHoldingDao = mockk<OrderHoldingDao>()
    private val pnlMonthlyCacheDao = mockk<PnlMonthlyCacheDao>()
    private val gmailScanCacheDao = mockk<GmailScanCacheDao>()
    private val gmailFilterDao = mockk<GmailFilterDao>()
    private val alertDao = mockk<AlertDao>()
    private val driveDataSource = mockk<GoogleDriveRemoteDataSource>()
    private val preferencesExporter = mockk<PreferencesExporter>()
    private val historyRecorder = mockk<BackupHistoryRecorder>(relaxed = true)

    private lateinit var filesDir: File
    private lateinit var useCase: CreateBackupUseCase

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        every { context.filesDir } returns filesDir

        // Default: all tables empty
        coEvery { orderDao.getAll() } returns emptyList()
        coEvery { holdingDao.getAll() } returns emptyList()
        coEvery { transactionDao.getAll() } returns emptyList()
        coEvery { fundEntryDao.getAll() } returns emptyList()
        coEvery { chargeRateDao.getAll() } returns emptyList()
        coEvery { gttRecordDao.getAll() } returns emptyList()
        coEvery { orderHoldingDao.getAll() } returns emptyList()
        coEvery { pnlMonthlyCacheDao.getAll() } returns emptyList()
        coEvery { gmailScanCacheDao.getAll() } returns emptyList()
        coEvery { gmailFilterDao.getAll() } returns emptyList()
        coEvery { alertDao.getAll() } returns emptyList()
        coEvery { preferencesExporter.exportAll() } returns UserPreferences()

        useCase =
            CreateBackupUseCase(
                context = context,
                orderDao = orderDao,
                holdingDao = holdingDao,
                transactionDao = transactionDao,
                fundEntryDao = fundEntryDao,
                chargeRateDao = chargeRateDao,
                gttRecordDao = gttRecordDao,
                orderHoldingDao = orderHoldingDao,
                pnlMonthlyCacheDao = pnlMonthlyCacheDao,
                gmailScanCacheDao = gmailScanCacheDao,
                gmailFilterDao = gmailFilterDao,
                alertDao = alertDao,
                driveDataSource = driveDataSource,
                preferencesExporter = preferencesExporter,
                historyRecorder = historyRecorder,
            )
    }

    // -----------------------------------------------------------------------
    // File format correctness — capture bytes before execute()
    // -----------------------------------------------------------------------

    @Test
    fun `output file starts with KWBK magic bytes`() =
        runTest {
            val bytesSlot = slot<ByteArray>()
            coEvery { driveDataSource.upload(any(), capture(bytesSlot)) } returns "drive-file-id"

            useCase.execute(accountId = "ZD1234")

            val magic = bytesSlot.captured.copyOfRange(0, 4)
            assertArrayEquals(
                "First 4 bytes must be KWBK",
                "KWBK".toByteArray(Charsets.US_ASCII),
                magic,
            )
        }

    @Test
    fun `header contains correct format version`() =
        runTest {
            val bytesSlot = slot<ByteArray>()
            coEvery { driveDataSource.upload(any(), capture(bytesSlot)) } returns "drive-file-id"

            useCase.execute(accountId = "ZD1234")

            val parsed = BackupFileWriter.parseHeader(bytesSlot.captured)
            assertNotNull(parsed)
            assertEquals(BackupFileWriter.FORMAT_VERSION, parsed!!.formatVersion)
        }

    @Test
    fun `SHA-256 checksum in header verifies against compressed payload`() =
        runTest {
            val bytesSlot = slot<ByteArray>()
            coEvery { driveDataSource.upload(any(), capture(bytesSlot)) } returns "drive-file-id"

            useCase.execute(accountId = "ZD1234")

            val parsed = BackupFileWriter.parseHeader(bytesSlot.captured)
            val payload = BackupFileWriter.extractPayload(bytesSlot.captured)
            assertNotNull(parsed)
            assertTrue(
                "Checksum must verify against payload",
                BackupIntegrityUtil.verifyChecksum(payload, parsed!!.checksum),
            )
        }

    @Test
    fun `header embeds account ID`() =
        runTest {
            val bytesSlot = slot<ByteArray>()
            coEvery { driveDataSource.upload(any(), capture(bytesSlot)) } returns "drive-file-id"

            useCase.execute(accountId = "AB9999")

            val parsed = BackupFileWriter.parseHeader(bytesSlot.captured)
            assertEquals("AB9999", parsed!!.accountId)
        }

    @Test
    fun `magic bytes valid per parseHeader`() =
        runTest {
            val bytesSlot = slot<ByteArray>()
            coEvery { driveDataSource.upload(any(), capture(bytesSlot)) } returns "drive-file-id"

            useCase.execute(accountId = "ZD1234")

            val parsed = BackupFileWriter.parseHeader(bytesSlot.captured)
            assertTrue(parsed!!.isMagicValid())
        }

    // -----------------------------------------------------------------------
    // Drive success path
    // -----------------------------------------------------------------------

    @Test
    fun `returns Success with GOOGLE_DRIVE destination when upload succeeds`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } returns "drive-file-id"

            val result = useCase.execute(accountId = "ZD1234")

            assertTrue(result is BackupResult.Success)
            assertEquals(BackupDestination.GOOGLE_DRIVE, (result as BackupResult.Success).destination)
        }

    @Test
    fun `file name contains account ID and kwbackup extension`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } returns "drive-file-id"

            val result = useCase.execute(accountId = "ZD1234") as BackupResult.Success

            assertTrue(result.fileName.contains("ZD1234"))
            assertTrue(result.fileName.endsWith(".kwbackup"))
        }

    @Test
    fun `file size is positive when database is empty`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } returns "drive-file-id"

            val result = useCase.execute(accountId = "ZD1234") as BackupResult.Success

            assertTrue("File size must be > 0", result.fileSizeBytes > 0)
        }

    @Test
    fun `history recorder called with SUCCESS status on Drive upload`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } returns "drive-file-id"

            useCase.execute(accountId = "ZD1234")

            coVerify {
                historyRecorder.record(
                    fileName = any(),
                    fileSizeBytes = any(),
                    destination = BackupDestination.GOOGLE_DRIVE,
                    status = "SUCCESS",
                    errorMessage = any(),
                )
            }
        }

    // -----------------------------------------------------------------------
    // Drive failure → local fallback
    // -----------------------------------------------------------------------

    @Test
    fun `Drive failure returns LocalFallback`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } throws RuntimeException("network timeout")

            val result = useCase.execute(accountId = "ZD1234")

            assertTrue(result is BackupResult.LocalFallback)
        }

    @Test
    fun `Drive failure preserves error message in LocalFallback`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } throws RuntimeException("network timeout")

            val result = useCase.execute(accountId = "ZD1234") as BackupResult.LocalFallback

            assertEquals("network timeout", result.driveError)
        }

    @Test
    fun `Drive failure writes file to local backups directory`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } throws RuntimeException("auth revoked")

            useCase.execute(accountId = "ZD1234")

            val backupDir = File(filesDir, "backups")
            val files = backupDir.listFiles { f -> f.name.endsWith(".kwbackup") }
            assertNotNull(files)
            assertEquals(1, files!!.size)
        }

    @Test
    fun `local fallback file starts with KWBK magic bytes`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } throws RuntimeException("fail")

            useCase.execute(accountId = "ZD1234")

            val backupDir = File(filesDir, "backups")
            val fileBytes =
                backupDir
                    .listFiles { f -> f.name.endsWith(".kwbackup") }!!
                    .first()
                    .readBytes()
            assertArrayEquals(
                "First 4 bytes must be KWBK",
                "KWBK".toByteArray(Charsets.US_ASCII),
                fileBytes.copyOfRange(0, 4),
            )
        }

    @Test
    fun `local fallback checksum verifies correctly`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } throws RuntimeException("fail")

            useCase.execute(accountId = "ZD1234")

            val backupDir = File(filesDir, "backups")
            val fileBytes =
                backupDir
                    .listFiles { f -> f.name.endsWith(".kwbackup") }!!
                    .first()
                    .readBytes()
            val parsed = BackupFileWriter.parseHeader(fileBytes)!!
            val payload = BackupFileWriter.extractPayload(fileBytes)
            assertTrue(BackupIntegrityUtil.verifyChecksum(payload, parsed.checksum))
        }

    @Test
    fun `history recorder called with LOCAL_FALLBACK status on Drive failure`() =
        runTest {
            coEvery { driveDataSource.upload(any(), any()) } throws RuntimeException("quota exceeded")

            useCase.execute(accountId = "ZD1234")

            coVerify {
                historyRecorder.record(
                    fileName = any(),
                    fileSizeBytes = any(),
                    destination = BackupDestination.LOCAL,
                    status = "LOCAL_FALLBACK",
                    errorMessage = "quota exceeded",
                )
            }
        }

    // -----------------------------------------------------------------------
    // LOCAL destination
    // -----------------------------------------------------------------------

    @Test
    fun `LOCAL destination writes file without calling Drive`() =
        runTest {
            val result = useCase.execute(accountId = "ZD1234", destination = BackupDestination.LOCAL)

            assertTrue(result is BackupResult.LocalFallback)
            coVerify(exactly = 0) { driveDataSource.upload(any(), any()) }
        }

    @Test
    fun `LOCAL destination null driveError in result`() =
        runTest {
            val result =
                useCase.execute(
                    accountId = "ZD1234",
                    destination = BackupDestination.LOCAL,
                ) as BackupResult.LocalFallback

            assertEquals(null, result.driveError)
        }

    // -----------------------------------------------------------------------
    // 5-file local retention
    // -----------------------------------------------------------------------

    @Test
    fun `retention deletes oldest file when 6 backups exist`() =
        runTest {
            val backupDir = File(filesDir, "backups").also { it.mkdirs() }

            // Pre-populate 5 existing backup files with staggered modification times
            for (i in 1..5) {
                val file = File(backupDir, "kitewatch_OLD_2026010${i}_000000.kwbackup")
                file.writeBytes(ByteArray(10) { i.toByte() })
                file.setLastModified(1_000L * i) // oldest = 1000ms epoch
            }

            coEvery { driveDataSource.upload(any(), any()) } throws RuntimeException("fail")
            useCase.execute(accountId = "ZD1234") // writes 6th file → triggers deletion

            val remaining = backupDir.listFiles { f -> f.name.endsWith(".kwbackup") }
            assertNotNull(remaining)
            assertEquals(CreateBackupUseCase.MAX_LOCAL_FILES, remaining!!.size)
        }

    @Test
    fun `retention keeps exactly MAX_LOCAL_FILES when already at limit`() =
        runTest {
            val backupDir = File(filesDir, "backups").also { it.mkdirs() }

            // Pre-populate MAX_LOCAL_FILES - 1 files; adding 1 more must not trigger deletion
            val existingCount = CreateBackupUseCase.MAX_LOCAL_FILES - 1
            for (i in 1..existingCount) {
                val file = File(backupDir, "kitewatch_OLD_2026020${i}_000000.kwbackup")
                file.writeBytes(ByteArray(8))
                file.setLastModified(2_000L * i)
            }

            coEvery { driveDataSource.upload(any(), any()) } throws RuntimeException("fail")
            useCase.execute(accountId = "ZD1234")

            val remaining = backupDir.listFiles { f -> f.name.endsWith(".kwbackup") }
            assertEquals(CreateBackupUseCase.MAX_LOCAL_FILES, remaining!!.size)
        }
}
