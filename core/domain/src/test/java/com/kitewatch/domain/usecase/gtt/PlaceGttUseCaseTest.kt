package com.kitewatch.domain.usecase.gtt

import com.kitewatch.domain.engine.GttAction
import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.AlertRepository
import com.kitewatch.domain.repository.AlertType
import com.kitewatch.domain.repository.GttRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.PersistentAlert
import com.kitewatch.domain.usecase.AppException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PlaceGttUseCaseTest {
    private val kiteConnectRepo = mockk<KiteConnectRepository>()
    private val gttRepo = mockk<GttRepository>()
    private val alertRepo = mockk<AlertRepository>()
    private val fixedClock = Clock.fixed(Instant.parse("2024-03-04T10:00:00Z"), ZoneOffset.UTC)

    private val useCase =
        PlaceGttUseCase(
            kiteConnectRepo = kiteConnectRepo,
            gttRepo = gttRepo,
            alertRepo = alertRepo,
            clock = fixedClock,
        )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun activeGttRecord(
        gttId: Long = 1L,
        stockCode: String = "INFY",
        zerodhaGttId: String? = "zerodha-gtt-1",
        status: GttStatus = GttStatus.ACTIVE,
    ) = GttRecord(
        gttId = gttId,
        zerodhaGttId = zerodhaGttId,
        stockCode = stockCode,
        triggerPrice = Paisa(150_000_00L),
        quantity = 10,
        status = status,
        isAppManaged = true,
        lastSyncedAt = Instant.now(fixedClock),
    )

    private fun notFoundException() = AppException(AppError.NetworkError.HttpError(code = 404, message = "Not Found"))

    // ── CreateGtt — success path ──────────────────────────────────────────────

    @Test
    fun `CreateGtt success inserts ACTIVE record with zerodhaGttId`() =
        runTest {
            coEvery { gttRepo.observeActive() } returns flowOf(emptyList())
            coEvery {
                kiteConnectRepo.createGtt(any(), any(), any())
            } returns Result.success("zerodha-new-gtt-99")
            val upsertSlot = slot<GttRecord>()
            coEvery { gttRepo.upsert(capture(upsertSlot)) } just Runs

            val result = useCase.execute(listOf(GttAction.CreateGtt("RELIANCE", 5, Paisa(2500_00L))))

            assertEquals(1, result.succeeded.size)
            assertTrue(result.failed.isEmpty())
            with(upsertSlot.captured) {
                assertEquals("zerodha-new-gtt-99", zerodhaGttId)
                assertEquals(GttStatus.ACTIVE, status)
                assertEquals("RELIANCE", stockCode)
                assertEquals(5, quantity)
                assertTrue(isAppManaged)
            }
        }

    // ── CreateGtt — retry exhaustion ──────────────────────────────────────────

    @Test
    fun `CreateGtt sets PENDING_CREATION after 3 failed attempts`() =
        runTest {
            coEvery { gttRepo.observeActive() } returns flowOf(emptyList())
            coEvery {
                kiteConnectRepo.createGtt(any(), any(), any())
            } returns Result.failure(Exception("network error"))
            val upsertSlot = slot<GttRecord>()
            coEvery { gttRepo.upsert(capture(upsertSlot)) } just Runs

            val result = useCase.execute(listOf(GttAction.CreateGtt("INFY", 10, Paisa(1500_00L))))

            assertEquals(1, result.failed.size)
            assertTrue(result.succeeded.isEmpty())
            // Verify exactly 3 API attempts made
            coVerify(exactly = 3) { kiteConnectRepo.createGtt(any(), any(), any()) }
            with(upsertSlot.captured) {
                assertEquals(GttStatus.PENDING_CREATION, status)
                assertEquals(null, zerodhaGttId)
                assertEquals("INFY", stockCode)
            }
        }

    // ── UpdateGtt — success path ──────────────────────────────────────────────

    @Test
    fun `UpdateGtt success updates local record to ACTIVE`() =
        runTest {
            val record = activeGttRecord(gttId = 1L, zerodhaGttId = "zerodha-gtt-1")
            coEvery { gttRepo.observeActive() } returns flowOf(listOf(record))
            coEvery {
                kiteConnectRepo.updateGtt(any(), any(), any())
            } returns Result.success(Unit)
            coEvery { gttRepo.updateStatus(any(), any(), any()) } just Runs

            val result = useCase.execute(listOf(GttAction.UpdateGtt(1L, 15, Paisa(1600_00L))))

            assertEquals(1, result.succeeded.size)
            assertTrue(result.failed.isEmpty())
            coVerify(exactly = 1) {
                gttRepo.updateStatus(1L, GttStatus.ACTIVE, Instant.now(fixedClock))
            }
        }

    // ── UpdateGtt — 404 deletes local record and inserts alert ────────────────

    @Test
    fun `UpdateGtt on 404 archives local record and inserts GTT_VERIFICATION_FAILED alert`() =
        runTest {
            val record = activeGttRecord(gttId = 2L, stockCode = "TCS", zerodhaGttId = "zerodha-gtt-2")
            coEvery { gttRepo.observeActive() } returns flowOf(listOf(record))
            coEvery {
                kiteConnectRepo.updateGtt(any(), any(), any())
            } returns Result.failure(notFoundException())
            coEvery { gttRepo.archive(any(), any()) } just Runs
            val alertSlot = slot<PersistentAlert>()
            coEvery { alertRepo.insert(capture(alertSlot)) } returns 1L

            val result = useCase.execute(listOf(GttAction.UpdateGtt(2L, 15, Paisa(3900_00L))))

            // Treated as handled — goes to succeeded, not failed
            assertEquals(1, result.succeeded.size)
            assertTrue(result.failed.isEmpty())
            coVerify(exactly = 1) { gttRepo.archive(2L, Instant.now(fixedClock)) }
            coVerify(exactly = 0) { gttRepo.updateStatus(any(), any(), any()) }
            assertEquals(AlertType.GTT_VERIFICATION_FAILED, alertSlot.captured.alertType)
        }

    // ── UpdateGtt — missing zerodhaGttId ─────────────────────────────────────

    @Test
    fun `UpdateGtt with null zerodhaGttId sets PENDING_UPDATE without API call`() =
        runTest {
            val record = activeGttRecord(gttId = 3L, zerodhaGttId = null, status = GttStatus.PENDING_CREATION)
            coEvery { gttRepo.observeActive() } returns flowOf(listOf(record))
            coEvery { gttRepo.updateStatus(any(), any(), any()) } just Runs

            val result = useCase.execute(listOf(GttAction.UpdateGtt(3L, 10, Paisa(500_00L))))

            assertEquals(1, result.failed.size)
            coVerify(exactly = 0) { kiteConnectRepo.updateGtt(any(), any(), any()) }
            coVerify(exactly = 1) {
                gttRepo.updateStatus(3L, GttStatus.PENDING_UPDATE, Instant.now(fixedClock))
            }
        }

    // ── FlagManualOverride — no API call ──────────────────────────────────────

    @Test
    fun `FlagManualOverride makes zero API calls, updates status, and inserts alert`() =
        runTest {
            val record = activeGttRecord(gttId = 4L, stockCode = "HDFC", zerodhaGttId = "zerodha-gtt-4")
            coEvery { gttRepo.observeActive() } returns flowOf(listOf(record))
            coEvery { gttRepo.updateStatus(any(), any(), any()) } just Runs
            val alertSlot = slot<PersistentAlert>()
            coEvery { alertRepo.insert(capture(alertSlot)) } returns 2L

            val action =
                GttAction.FlagManualOverride(
                    gttId = 4L,
                    appTargetPrice = Paisa(1700_00L),
                    zerodhaActualPrice = Paisa(1800_00L),
                )
            val result = useCase.execute(listOf(action))

            assertTrue(result.succeeded.isEmpty())
            assertTrue(result.failed.isEmpty())
            assertEquals(1, result.flagged.size)
            assertEquals(action, result.flagged.first())
            // No API calls
            coVerify(exactly = 0) { kiteConnectRepo.createGtt(any(), any(), any()) }
            coVerify(exactly = 0) { kiteConnectRepo.updateGtt(any(), any(), any()) }
            coVerify(exactly = 0) { kiteConnectRepo.deleteGtt(any()) }
            // Local state updated
            coVerify(exactly = 1) {
                gttRepo.updateStatus(4L, GttStatus.MANUAL_OVERRIDE_DETECTED, Instant.now(fixedClock))
            }
            assertEquals(AlertType.GTT_MANUAL_OVERRIDE, alertSlot.captured.alertType)
        }

    // ── ArchiveGtt — success path ─────────────────────────────────────────────

    @Test
    fun `ArchiveGtt deletes from Kite and archives locally`() =
        runTest {
            val record = activeGttRecord(gttId = 5L, stockCode = "WIPRO", zerodhaGttId = "zerodha-gtt-5")
            coEvery { gttRepo.observeActive() } returns flowOf(listOf(record))
            coEvery { kiteConnectRepo.deleteGtt(any()) } returns Result.success(Unit)
            coEvery { gttRepo.archive(any(), any()) } just Runs

            val result = useCase.execute(listOf(GttAction.ArchiveGtt(5L)))

            assertEquals(1, result.succeeded.size)
            coVerify(exactly = 1) { kiteConnectRepo.deleteGtt("zerodha-gtt-5") }
            coVerify(exactly = 1) { gttRepo.archive(5L, Instant.now(fixedClock)) }
        }

    // ── NoAction — ignored ────────────────────────────────────────────────────

    @Test
    fun `NoAction produces empty result lists`() =
        runTest {
            coEvery { gttRepo.observeActive() } returns flowOf(emptyList())

            val result = useCase.execute(listOf(GttAction.NoAction("BAJAJ")))

            assertTrue(result.succeeded.isEmpty())
            assertTrue(result.failed.isEmpty())
            assertTrue(result.flagged.isEmpty())
            coVerify(exactly = 0) { kiteConnectRepo.createGtt(any(), any(), any()) }
        }

    // ── Mixed actions ─────────────────────────────────────────────────────────

    @Test
    fun `mixed actions produce correct partitioned result`() =
        runTest {
            val updateRecord = activeGttRecord(gttId = 10L, stockCode = "INFY", zerodhaGttId = "gtt-10")
            val flagRecord = activeGttRecord(gttId = 11L, stockCode = "TCS", zerodhaGttId = "gtt-11")
            coEvery { gttRepo.observeActive() } returns flowOf(listOf(updateRecord, flagRecord))

            // CreateGtt — success
            coEvery { kiteConnectRepo.createGtt(any(), any(), any()) } returns Result.success("gtt-new")
            coEvery { gttRepo.upsert(any()) } just Runs

            // UpdateGtt — 404
            coEvery { kiteConnectRepo.updateGtt(any(), any(), any()) } returns Result.failure(notFoundException())
            coEvery { gttRepo.archive(any(), any()) } just Runs
            coEvery { alertRepo.insert(any()) } returns 1L

            // FlagManualOverride
            coEvery { gttRepo.updateStatus(any(), any(), any()) } just Runs

            val actions =
                listOf(
                    GttAction.CreateGtt("RELIANCE", 5, Paisa(2500_00L)),
                    GttAction.UpdateGtt(10L, 15, Paisa(1600_00L)),
                    GttAction.FlagManualOverride(11L, Paisa(3900_00L), Paisa(4000_00L)),
                    GttAction.NoAction("WIPRO"),
                )

            val result = useCase.execute(actions)

            assertEquals(2, result.succeeded.size) // Create + Update(404)
            assertEquals(0, result.failed.size)
            assertEquals(1, result.flagged.size)
        }
}
