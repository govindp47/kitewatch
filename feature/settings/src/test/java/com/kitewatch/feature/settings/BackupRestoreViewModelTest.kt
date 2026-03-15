package com.kitewatch.feature.settings

import android.content.Context
import com.kitewatch.domain.model.AccountBinding
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.infra.backup.model.BackupResult
import com.kitewatch.infra.backup.usecase.AccountMismatchException
import com.kitewatch.infra.backup.usecase.CreateBackupUseCase
import com.kitewatch.infra.backup.usecase.RestoreBackupUseCase
import com.kitewatch.infra.backup.usecase.RestoreResult
import com.kitewatch.infra.backup.usecase.RestoreSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import com.kitewatch.infra.backup.model.BackupDestination as InfraBackupDestination

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val createBackupUseCase = mockk<CreateBackupUseCase>(relaxed = true)
    private val restoreBackupUseCase = mockk<RestoreBackupUseCase>(relaxed = true)
    private val accountBindingRepository = mockk<AccountBindingRepository>()
    private val backupDriveRepository = mockk<BackupDriveRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val boundAccount =
        AccountBinding(
            userId = "ZD1234",
            userName = "Test User",
            apiKey = "key",
            boundAt = Instant.EPOCH,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { accountBindingRepository.getBinding() } returns boundAccount
        coEvery { backupDriveRepository.listBackups() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        BackupRestoreViewModel(
            createBackupUseCase = createBackupUseCase,
            restoreBackupUseCase = restoreBackupUseCase,
            accountBindingRepository = accountBindingRepository,
            backupDriveRepository = backupDriveRepository,
            context = context,
        )

    // ── ListDriveBackups ──────────────────────────────────────────────────────

    @Test
    fun `init triggers ListDriveBackups and populates driveBackups`() =
        runTest {
            val entries =
                listOf(
                    DriveBackupEntry(
                        fileId = "f1",
                        fileName = "kitewatch_ZD1234_20260101.kwbackup",
                        createdAt = "2026-01-01T00:00:00Z",
                        accountId = "ZD1234",
                        fileSizeBytes = 1024L,
                    ),
                )
            coEvery { backupDriveRepository.listBackups() } returns entries

            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(entries, vm.state.value.driveBackups)
        }

    @Test
    fun `ListDriveBackups failure leaves driveBackups empty and clears loading flag`() =
        runTest {
            coEvery { backupDriveRepository.listBackups() } throws RuntimeException("Network error")

            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(
                vm.state.value.driveBackups
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isLoadingDriveBackups)
        }

    // ── CreateBackup ──────────────────────────────────────────────────────────

    @Test
    fun `CreateBackup LOCAL calls createBackupUseCase with LOCAL destination`() =
        runTest {
            coEvery {
                createBackupUseCase.execute(any(), any())
            } returns BackupResult.LocalFallback("file.kwbackup", 512L, null)

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.processIntent(BackupRestoreIntent.CreateBackup(BackupDestination.LOCAL))
            advanceUntilIdle()

            coVerify { createBackupUseCase.execute("ZD1234", InfraBackupDestination.LOCAL) }
            assertFalse(vm.state.value.isBackingUp)
        }

    @Test
    fun `CreateBackup DRIVE calls createBackupUseCase with GOOGLE_DRIVE destination`() =
        runTest {
            coEvery {
                createBackupUseCase.execute(any(), any())
            } returns BackupResult.Success("file.kwbackup", 512L, InfraBackupDestination.GOOGLE_DRIVE)

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.processIntent(BackupRestoreIntent.CreateBackup(BackupDestination.DRIVE))
            advanceUntilIdle()

            coVerify { createBackupUseCase.execute("ZD1234", InfraBackupDestination.GOOGLE_DRIVE) }
        }

    @Test
    fun `CreateBackup failure sets error state`() =
        runTest {
            coEvery { createBackupUseCase.execute(any(), any()) } throws RuntimeException("Drive quota exceeded")

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.processIntent(BackupRestoreIntent.CreateBackup(BackupDestination.DRIVE))
            advanceUntilIdle()

            assertEquals("Drive quota exceeded", vm.state.value.error)
            assertFalse(vm.state.value.isBackingUp)
        }

    @Test
    fun `CreateBackup with no bound account sets error`() =
        runTest {
            coEvery { accountBindingRepository.getBinding() } returns null

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.processIntent(BackupRestoreIntent.CreateBackup(BackupDestination.LOCAL))
            advanceUntilIdle()

            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.isBackingUp)
        }

    // ── CancelRestore ─────────────────────────────────────────────────────────

    @Test
    fun `CancelRestore clears pendingRestoreSource and pendingBackupPreview`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            // Directly set pending state by mocking drive download for preview
            coEvery { backupDriveRepository.downloadBackupBytes(any()) } throws RuntimeException("skip")

            vm.processIntent(BackupRestoreIntent.CancelRestore)

            assertNull(vm.state.value.pendingRestoreSource)
            assertNull(vm.state.value.pendingBackupPreview)
        }

    // ── ConfirmRestore ────────────────────────────────────────────────────────

    @Test
    fun `ConfirmRestore calls safety backup then restoreBackupUseCase`() =
        runTest {
            coEvery { createBackupUseCase.execute(any(), any()) } returns
                BackupResult.LocalFallback("safe.kwbackup", 100L, null)
            coEvery { restoreBackupUseCase.execute(any(), any()) } returns
                RestoreResult.Success(
                    recordCount = 42L,
                    schemaVersion = 1,
                    backupCreatedAt = "2026-01-01T00:00:00Z",
                    accountId = "ZD1234",
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            val source = RestoreSource.Drive("file-id-1")
            vm.processIntent(BackupRestoreIntent.ConfirmRestore(source))
            advanceUntilIdle()

            // Safety backup uses LOCAL destination
            coVerify { createBackupUseCase.execute("ZD1234", InfraBackupDestination.LOCAL) }
            coVerify { restoreBackupUseCase.execute(source, "ZD1234") }
            assertFalse(vm.state.value.isRestoring)
            assertNull(vm.state.value.error)
        }

    @Test
    fun `ConfirmRestore proceeds even when safety backup fails`() =
        runTest {
            coEvery { createBackupUseCase.execute(any(), any()) } throws RuntimeException("Disk full")
            coEvery { restoreBackupUseCase.execute(any(), any()) } returns
                RestoreResult.Success(
                    recordCount = 5L,
                    schemaVersion = 1,
                    backupCreatedAt = "2026-01-01T00:00:00Z",
                    accountId = "ZD1234",
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.processIntent(BackupRestoreIntent.ConfirmRestore(RestoreSource.Drive("fid")))
            advanceUntilIdle()

            // Restore still called despite safety backup failure
            coVerify { restoreBackupUseCase.execute(any(), any()) }
            assertFalse(vm.state.value.isRestoring)
        }

    @Test
    fun `ConfirmRestore failure sets error state`() =
        runTest {
            coEvery { createBackupUseCase.execute(any(), any()) } returns
                BackupResult.LocalFallback("safe.kwbackup", 100L, null)
            coEvery { restoreBackupUseCase.execute(any(), any()) } throws
                AccountMismatchException(expected = "ZD1234", found = "ZD9999")

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.processIntent(BackupRestoreIntent.ConfirmRestore(RestoreSource.Drive("fid")))
            advanceUntilIdle()

            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.isRestoring)
        }

    // ── DismissError ──────────────────────────────────────────────────────────

    @Test
    fun `DismissError clears error state`() =
        runTest {
            coEvery { createBackupUseCase.execute(any(), any()) } throws RuntimeException("oops")

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.processIntent(BackupRestoreIntent.CreateBackup(BackupDestination.LOCAL))
            advanceUntilIdle()
            assertNotNull(vm.state.value.error)

            vm.processIntent(BackupRestoreIntent.DismissError)

            assertNull(vm.state.value.error)
        }
}
