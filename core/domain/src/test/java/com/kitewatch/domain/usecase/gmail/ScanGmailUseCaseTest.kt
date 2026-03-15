package com.kitewatch.domain.usecase.gmail

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScanGmailUseCaseTest {
    private val gmailScanPort: GmailScanPort = mockk()
    private val gmailCachePort: GmailCachePort = mockk()

    private val useCase = ScanGmailUseCase(gmailScanPort, gmailCachePort)

    private fun detection(id: String) =
        GmailFundDetection(
            messageId = id,
            subject = "Funds added to Kite",
            amountPaisa = 5_000_000L,
            date = LocalDate.of(2024, 3, 1),
        )

    @Test
    fun `new detections are inserted - returns count of new rows`() =
        runTest {
            every { gmailCachePort.observePending() } returns flowOf(emptyList())
            coEvery { gmailCachePort.getAllMessageIds() } returns emptySet()
            coEvery { gmailScanPort.scanForFundCredits(any(), any()) } returns
                listOf(
                    detection("msg001"),
                    detection("msg002"),
                )
            coEvery { gmailCachePort.insertPending(any()) } returnsMany listOf(1L, 2L)

            val result = useCase.execute()

            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrNull())
        }

    @Test
    fun `already seen IDs excluded from scan call`() =
        runTest {
            every { gmailCachePort.observePending() } returns flowOf(emptyList())
            coEvery { gmailCachePort.getAllMessageIds() } returns setOf("msg001")
            coEvery { gmailScanPort.scanForFundCredits(any(), eq(setOf("msg001"))) } returns emptyList()

            val result = useCase.execute()

            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull())
            coVerify { gmailScanPort.scanForFundCredits(any(), eq(setOf("msg001"))) }
        }

    @Test
    fun `insert returns -1L for duplicate - not counted as new`() =
        runTest {
            every { gmailCachePort.observePending() } returns flowOf(emptyList())
            coEvery { gmailCachePort.getAllMessageIds() } returns emptySet()
            coEvery { gmailScanPort.scanForFundCredits(any(), any()) } returns listOf(detection("msg001"))
            // IGNORE conflict returns -1L
            coEvery { gmailCachePort.insertPending(any()) } returns -1L

            val result = useCase.execute()

            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull())
        }

    @Test
    fun `scan port failure wraps in Result failure`() =
        runTest {
            every { gmailCachePort.observePending() } returns flowOf(emptyList())
            coEvery { gmailCachePort.getAllMessageIds() } returns emptySet()
            coEvery { gmailScanPort.scanForFundCredits(any(), any()) } throws RuntimeException("Network error")

            val result = useCase.execute()

            assertTrue(result.isFailure)
            assertEquals("Network error", result.exceptionOrNull()?.message)
        }

    @Test
    fun `empty detection list returns 0 new entries`() =
        runTest {
            every { gmailCachePort.observePending() } returns flowOf(emptyList())
            coEvery { gmailCachePort.getAllMessageIds() } returns emptySet()
            coEvery { gmailScanPort.scanForFundCredits(any(), any()) } returns emptyList()

            val result = useCase.execute()

            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull())
        }
}
