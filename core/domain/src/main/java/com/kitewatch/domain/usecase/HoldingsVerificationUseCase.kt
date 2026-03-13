package com.kitewatch.domain.usecase

import com.kitewatch.domain.engine.HoldingsVerificationResult
import com.kitewatch.domain.engine.HoldingsVerifier
import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.repository.HoldingRepository
import com.kitewatch.domain.repository.KiteConnectRepository

/**
 * Fetches remote and local holdings and delegates to [HoldingsVerifier] for BR-07 comparison.
 *
 * Returns [Result.failure] wrapping [AppException] on network error;
 * returns [Result.success] with the verification result otherwise.
 */
class HoldingsVerificationUseCase(
    private val kiteConnectRepo: KiteConnectRepository,
    private val holdingRepo: HoldingRepository,
) {
    suspend fun execute(): Result<HoldingsVerificationResult> {
        val remoteResult = kiteConnectRepo.fetchHoldings()
        if (remoteResult.isFailure) {
            return Result.failure(AppException(AppError.DomainError.HoldingsFetchFailed))
        }
        val remoteHoldings = remoteResult.getOrThrow()
        val localHoldings = holdingRepo.getAll()
        return Result.success(HoldingsVerifier.verify(remoteHoldings, localHoldings))
    }
}
