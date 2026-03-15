package com.kitewatch.domain.usecase.gmail

import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.FundRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConfirmGmailEntryUseCaseTest {
    private val fundRepo: FundRepository = mockk()
    private val gmailCachePort: GmailCachePort = mockk()

    private val useCase = ConfirmGmailEntryUseCase(fundRepo, gmailCachePort)

    private fun cacheEntry(
        messageId: String,
        amountPaisa: Long = 5_000_000L,
    ) = GmailCacheEntry(
        messageId = messageId,
        amountPaisa = amountPaisa,
        date = LocalDate.of(2024, 3, 1),
        subject = "Funds added to Kite",
        status = "PENDING_REVIEW",
        linkedFundEntryId = null,
    )

    // ── confirm ───────────────────────────────────────────────────────────────

    @Test
    fun `confirm - valid entry creates FundEntry and marks confirmed`() =
        runTest {
            val entry = cacheEntry("msg001")
            coEvery { gmailCachePort.getByMessageId("msg001") } returns entry
            coEvery { fundRepo.insertEntry(any()) } returns 42L
            coEvery { gmailCachePort.markConfirmed("msg001", 42L) } returns Unit

            val result = useCase.confirm("msg001")

            assertTrue(result.isSuccess)
            val fundEntry = result.getOrNull()!!
            assertEquals(42L, fundEntry.entryId)
            assertEquals(FundEntryType.GMAIL_DETECTED, fundEntry.entryType)
            assertEquals(Paisa(5_000_000L), fundEntry.amount)
            assertEquals("msg001", fundEntry.gmailMessageId)

            coVerify { gmailCachePort.markConfirmed("msg001", 42L) }
        }

    @Test
    fun `confirm - missing cache entry returns failure`() =
        runTest {
            coEvery { gmailCachePort.getByMessageId("missing") } returns null

            val result = useCase.confirm("missing")

            assertTrue(result.isFailure)
        }

    @Test
    fun `confirm - zero amount returns failure`() =
        runTest {
            coEvery { gmailCachePort.getByMessageId("msg001") } returns cacheEntry("msg001", amountPaisa = 0L)

            val result = useCase.confirm("msg001")

            assertTrue(result.isFailure)
        }

    // ── dismiss ───────────────────────────────────────────────────────────────

    @Test
    fun `dismiss - calls markDismissed on cache port`() =
        runTest {
            coEvery { gmailCachePort.getByMessageId("msg001") } returns cacheEntry("msg001")
            coEvery { gmailCachePort.markDismissed("msg001") } returns Unit

            val result = useCase.dismiss("msg001")

            assertTrue(result.isSuccess)
            coVerify { gmailCachePort.markDismissed("msg001") }
        }

    @Test
    fun `dismiss - missing cache entry returns failure`() =
        runTest {
            coEvery { gmailCachePort.getByMessageId("missing") } returns null

            val result = useCase.dismiss("missing")

            assertTrue(result.isFailure)
        }
}
