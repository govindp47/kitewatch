package com.kitewatch.domain.usecase.holdings

import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.repository.HoldingRepository
import java.time.Instant

/**
 * Updates the profit target for a holding identified by [stockCode].
 *
 * Reads the current holding record, applies the new [ProfitTarget], and persists
 * the updated record. No-ops silently if the holding is not found.
 */
class UpdateProfitTargetUseCase(
    private val holdingRepository: HoldingRepository,
) {
    suspend fun execute(
        stockCode: String,
        newTarget: ProfitTarget,
    ) {
        val holding = holdingRepository.getByStockCode(stockCode) ?: return
        holdingRepository.upsert(
            holding.copy(
                profitTarget = newTarget,
                updatedAt = Instant.now(),
            ),
        )
    }
}
