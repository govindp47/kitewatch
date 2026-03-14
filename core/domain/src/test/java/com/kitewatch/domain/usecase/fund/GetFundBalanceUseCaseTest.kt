package com.kitewatch.domain.usecase.fund

import app.cash.turbine.test
import com.kitewatch.domain.model.FundEntry
import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.FundRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GetFundBalanceUseCaseTest {
    private val fundRepo = mockk<FundRepository>()
    private val useCase = GetFundBalanceUseCase(fundRepo)

    private val today = LocalDate.of(2024, 3, 4)

    // ── Balance computation ───────────────────────────────────────────────────

    @Test
    fun `empty entry list emits Paisa_ZERO`() =
        runTest {
            every { fundRepo.observeEntries() } returns flowOf(emptyList())

            useCase.execute().test {
                assertEquals(Paisa.ZERO, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `three deposits sum correctly`() =
        runTest {
            val entries =
                listOf(
                    deposit(100_000L),
                    deposit(200_000L),
                    deposit(50_000L),
                )
            every { fundRepo.observeEntries() } returns flowOf(entries)

            useCase.execute().test {
                assertEquals(Paisa(350_000L), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `withdrawals are subtracted from balance`() =
        runTest {
            val entries =
                listOf(
                    deposit(500_000L),
                    withdrawal(100_000L),
                    withdrawal(50_000L),
                )
            every { fundRepo.observeEntries() } returns flowOf(entries)

            useCase.execute().test {
                assertEquals(Paisa(350_000L), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `dividend adds to balance`() =
        runTest {
            val entries =
                listOf(
                    deposit(100_000L),
                    dividend(10_000L),
                )
            every { fundRepo.observeEntries() } returns flowOf(entries)

            useCase.execute().test {
                assertEquals(Paisa(110_000L), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `misc_adjustment adds to balance`() =
        runTest {
            val entries =
                listOf(
                    deposit(100_000L),
                    adjustment(500L),
                )
            every { fundRepo.observeEntries() } returns flowOf(entries)

            useCase.execute().test {
                assertEquals(Paisa(100_500L), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `new emission recomputes balance`() =
        runTest {
            val first = listOf(deposit(100_000L))
            val second = listOf(deposit(100_000L), deposit(200_000L))
            every { fundRepo.observeEntries() } returns flowOf(first, second)

            useCase.execute().test {
                assertEquals(Paisa(100_000L), awaitItem())
                assertEquals(Paisa(300_000L), awaitItem())
                awaitComplete()
            }
        }

    // ── Unconfirmed entries are excluded (enforced by FundRepository.observeEntries) ──
    //
    // observeEntries() returns only confirmed entries (is_confirmed=1). This is a
    // contract of FundRepository; GetFundBalanceUseCase relies on it and does not
    // need to filter again. The following test documents the assumption.

    @Test
    fun `only entries returned by observeEntries are included in balance`() =
        runTest {
            // Repository returns only 1 confirmed entry even if 2 were inserted
            every { fundRepo.observeEntries() } returns flowOf(listOf(deposit(100_000L)))

            useCase.execute().test {
                assertEquals(Paisa(100_000L), awaitItem())
                awaitComplete()
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun deposit(amountPaisa: Long) = entry(FundEntryType.DEPOSIT, amountPaisa)

    private fun withdrawal(amountPaisa: Long) = entry(FundEntryType.WITHDRAWAL, amountPaisa)

    private fun dividend(amountPaisa: Long) = entry(FundEntryType.DIVIDEND, amountPaisa)

    private fun adjustment(amountPaisa: Long) = entry(FundEntryType.MISC_ADJUSTMENT, amountPaisa)

    private fun entry(
        type: FundEntryType,
        amountPaisa: Long,
    ) = FundEntry(
        entryId = 0L,
        entryType = type,
        amount = Paisa(amountPaisa),
        entryDate = today,
        note = null,
        gmailMessageId = null,
    )
}
