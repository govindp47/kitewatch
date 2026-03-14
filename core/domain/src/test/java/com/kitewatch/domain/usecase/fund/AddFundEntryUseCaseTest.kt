package com.kitewatch.domain.usecase.fund

import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.FundRepository
import com.kitewatch.domain.usecase.AppException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AddFundEntryUseCaseTest {
    private val fundRepo = mockk<FundRepository>()
    private val useCase = AddFundEntryUseCase(fundRepo)

    private val today = LocalDate.of(2024, 3, 4)

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `zero amount returns ValidationError_NegativeAmount`() =
        runTest {
            val result = useCase.execute(Paisa.ZERO, today, null, FundEntryType.DEPOSIT)

            assertTrue(result.isFailure)
            val error = (result.exceptionOrNull() as AppException).error
            assertEquals(AppError.ValidationError.NegativeAmount, error)
            coVerify(exactly = 0) { fundRepo.insertEntry(any()) }
        }

    @Test
    fun `negative amount returns ValidationError_NegativeAmount`() =
        runTest {
            val result = useCase.execute(Paisa(-1L), today, null, FundEntryType.DEPOSIT)

            assertTrue(result.isFailure)
            val error = (result.exceptionOrNull() as AppException).error
            assertEquals(AppError.ValidationError.NegativeAmount, error)
            coVerify(exactly = 0) { fundRepo.insertEntry(any()) }
        }

    // ── Happy paths ───────────────────────────────────────────────────────────

    @Test
    fun `valid DEPOSIT returns inserted FundEntry with repo-assigned id`() =
        runTest {
            val amount = Paisa(100_000L) // ₹1,000
            coEvery { fundRepo.insertEntry(any()) } returns 42L

            val result = useCase.execute(amount, today, "Initial deposit", FundEntryType.DEPOSIT)

            assertTrue(result.isSuccess)
            val entry = result.getOrThrow()
            assertEquals(42L, entry.entryId)
            assertEquals(amount, entry.amount)
            assertEquals(today, entry.entryDate)
            assertEquals(FundEntryType.DEPOSIT, entry.entryType)
            assertEquals("Initial deposit", entry.note)
        }

    @Test
    fun `valid WITHDRAWAL is inserted correctly`() =
        runTest {
            coEvery { fundRepo.insertEntry(any()) } returns 7L

            val result = useCase.execute(Paisa(50_000L), today, null, FundEntryType.WITHDRAWAL)

            assertTrue(result.isSuccess)
            assertEquals(FundEntryType.WITHDRAWAL, result.getOrThrow().entryType)
        }

    @Test
    fun `null note is accepted`() =
        runTest {
            coEvery { fundRepo.insertEntry(any()) } returns 1L

            val result = useCase.execute(Paisa(1_000L), today, null, FundEntryType.DIVIDEND)

            assertTrue(result.isSuccess)
            assertEquals(null, result.getOrThrow().note)
        }

    @Test
    fun `gmailMessageId is always null for manual entries`() =
        runTest {
            coEvery { fundRepo.insertEntry(any()) } returns 1L

            val result = useCase.execute(Paisa(1_000L), today, null, FundEntryType.DEPOSIT)

            assertTrue(result.isSuccess)
            assertEquals(null, result.getOrThrow().gmailMessageId)
        }

    @Test
    fun `insertEntry is called with entryId zero so repo assigns the id`() =
        runTest {
            coEvery { fundRepo.insertEntry(match { it.entryId == 0L }) } returns 99L

            val result = useCase.execute(Paisa(5_000L), today, null, FundEntryType.DEPOSIT)

            assertTrue(result.isSuccess)
            assertEquals(99L, result.getOrThrow().entryId)
        }
}
