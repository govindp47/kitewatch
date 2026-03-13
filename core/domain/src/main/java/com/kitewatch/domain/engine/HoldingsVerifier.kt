package com.kitewatch.domain.engine

import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.repository.RemoteHolding

/**
 * Typed mismatch descriptor for a single stock.
 */
sealed class HoldingDiff {
    /** Stock exists remotely (CNC) but has no local holding record. */
    data class MissingLocal(
        val stockCode: String,
        val remoteQty: Int,
    ) : HoldingDiff()

    /** Stock exists locally (qty > 0) but is absent from remote CNC holdings. */
    data class MissingRemote(
        val stockCode: String,
        val localQty: Int,
    ) : HoldingDiff()

    /** Stock exists on both sides but quantities differ. */
    data class QuantityMismatch(
        val stockCode: String,
        val remoteQty: Int,
        val localQty: Int,
    ) : HoldingDiff()
}

/**
 * Result of a holdings verification run.
 */
sealed class HoldingsVerificationResult {
    /** Remote CNC holdings match local active holdings. */
    object Verified : HoldingsVerificationResult()

    /** One or more stocks have differing quantities. */
    data class Mismatch(
        val diffs: List<HoldingDiff>,
    ) : HoldingsVerificationResult()
}

/**
 * Stateless holdings comparison engine (BR-07).
 *
 * Filters remote holdings to CNC product only and local holdings to quantity > 0
 * before comparing. Non-CNC products (MIS, NRML) are ignored.
 */
object HoldingsVerifier {
    private const val CNC_PRODUCT = "CNC"

    /**
     * Compare [remoteHoldings] against [localHoldings].
     *
     * @return [HoldingsVerificationResult.Verified] when all quantities match,
     *         [HoldingsVerificationResult.Mismatch] with typed diffs otherwise.
     */
    fun verify(
        remoteHoldings: List<RemoteHolding>,
        localHoldings: List<Holding>,
    ): HoldingsVerificationResult {
        val remoteMap =
            remoteHoldings
                .filter { it.product == CNC_PRODUCT }
                .associate { it.tradingSymbol to it.quantity }
        val localMap =
            localHoldings
                .filter { it.quantity > 0 }
                .associate { it.stockCode to it.quantity }

        val diffs =
            (remoteMap.keys + localMap.keys).toSet().mapNotNull { stockCode ->
                val remoteQty = remoteMap[stockCode]
                val localQty = localMap[stockCode]
                when {
                    remoteQty != null && localQty == null ->
                        HoldingDiff.MissingLocal(stockCode, remoteQty)
                    remoteQty == null && localQty != null ->
                        HoldingDiff.MissingRemote(stockCode, localQty)
                    remoteQty != null && localQty != null && remoteQty != localQty ->
                        HoldingDiff.QuantityMismatch(stockCode, remoteQty, localQty)
                    else -> null
                }
            }

        return if (diffs.isEmpty()) {
            HoldingsVerificationResult.Verified
        } else {
            HoldingsVerificationResult.Mismatch(diffs)
        }
    }
}
