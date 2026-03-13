package com.kitewatch.domain.usecase

import com.kitewatch.domain.engine.HoldingsVerificationResult
import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.repository.HoldingRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.RemoteHolding
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HoldingsVerificationUseCaseTest {
    private val kiteConnectRepo = mockk<KiteConnectRepository>()
    private val holdingRepo = mockk<HoldingRepository>()

    private val useCase = HoldingsVerificationUseCase(kiteConnectRepo, holdingRepo)

    // ─── Test 1: Happy path — quantities match → Verified ──────────────────────

    @Test
    fun `Verified result returned when remote and local holdings match`() =
        runTest {
            coEvery { kiteConnectRepo.fetchHoldings() } returns
                Result.success(listOf(RemoteHolding("INFY", 10, "CNC")))
            coEvery { holdingRepo.getAll() } returns listOf(local("INFY", 10))

            val result = useCase.execute()

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull() is HoldingsVerificationResult.Verified)
        }

    // ─── Test 2: Quantities differ → Mismatch ──────────────────────────────────

    @Test
    fun `Mismatch result returned when quantities differ`() =
        runTest {
            coEvery { kiteConnectRepo.fetchHoldings() } returns
                Result.success(listOf(RemoteHolding("INFY", 10, "CNC")))
            coEvery { holdingRepo.getAll() } returns listOf(local("INFY", 5))

            val result = useCase.execute()

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull() is HoldingsVerificationResult.Mismatch)
        }

    // ─── Test 3: Remote fetch failure → Result.failure with HoldingsFetchFailed ─

    @Test
    fun `Failure returned with HoldingsFetchFailed when remote fetch throws`() =
        runTest {
            coEvery { kiteConnectRepo.fetchHoldings() } returns
                Result.failure(Exception("network error"))

            val result = useCase.execute()

            assertTrue(result.isFailure)
            val error = (result.exceptionOrNull() as AppException).error
            assertTrue(error is AppError.DomainError.HoldingsFetchFailed)
        }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun local(
        stockCode: String,
        quantity: Int,
    ) = Holding(
        holdingId = 1L,
        stockCode = stockCode,
        stockName = "$stockCode Ltd",
        quantity = quantity,
        avgBuyPrice = Paisa(100_00L),
        investedAmount = Paisa(quantity * 100_00L),
        totalBuyCharges = Paisa.ZERO,
        profitTarget = ProfitTarget.Percentage(500),
        targetSellPrice = Paisa(105_00L),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
