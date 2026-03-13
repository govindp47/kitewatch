package com.kitewatch.domain.engine

import com.kitewatch.domain.model.Paisa
import java.time.LocalDate

/**
 * A single buy lot representing shares acquired in one order.
 *
 * @param orderId       Identifier of the originating buy order
 * @param quantity      Number of shares in this lot
 * @param pricePerUnit  Price per share in [Paisa]
 * @param totalValue    Total cost of the lot (pricePerUnit × quantity) in [Paisa]
 * @param tradeDate     Date the buy order was executed (used for FIFO ordering)
 */
data class BuyLot(
    val orderId: Long,
    val quantity: Int,
    val pricePerUnit: Paisa,
    val totalValue: Paisa,
    val tradeDate: LocalDate,
) {
    init {
        require(quantity > 0) { "BuyLot quantity must be > 0, was $quantity" }
        require(pricePerUnit.value > 0) { "BuyLot pricePerUnit must be > 0" }
        require(totalValue.value > 0) { "BuyLot totalValue must be > 0" }
    }
}

/**
 * The portion of a [BuyLot] consumed by a sell.
 *
 * @param orderId                 Identifier of the originating buy order
 * @param matchedQty              Number of shares consumed from this lot
 * @param costBasisForMatchedQty  Proportional cost of the matched shares in [Paisa]
 */
data class MatchedLot(
    val orderId: Long,
    val matchedQty: Int,
    val costBasisForMatchedQty: Paisa,
)

/**
 * Result of a FIFO match operation.
 *
 * @param matchedLots      Lots (or portions thereof) consumed by the sell, oldest-first
 * @param matchedCostBasis Sum of [MatchedLot.costBasisForMatchedQty] across all matched lots
 * @param remainingLots    Unconsumed lots after the sell, in original FIFO order
 * @param overSellQuantity Shares requested beyond available inventory; 0 when sell is fully covered
 */
data class FifoMatchResult(
    val matchedLots: List<MatchedLot>,
    val matchedCostBasis: Paisa,
    val remainingLots: List<BuyLot>,
    val overSellQuantity: Int,
)

/**
 * Pure FIFO lot-matching engine.
 *
 * Stateless object — all functions are pure with no I/O or mutable external state.
 * Used by both [HoldingsComputationEngine] (unrealised holdings) and PnlCalculator
 * (realised P&L cost basis attribution).
 *
 * Partial lot cost basis formula (integer arithmetic, no floating-point):
 * ```
 * costBasis = (lot.totalValue * matchedQty) / lot.quantity
 * ```
 * Integer division truncates; callers should be aware that rounding residuals
 * accumulate across many partial splits (this matches Zerodha's contract note
 * truncation behaviour for cost basis attribution).
 */
object FifoLotMatcher {
    /**
     * Match [sellQuantity] shares against [buyLots] using FIFO (oldest trade date first).
     *
     * The input list is sorted internally; callers do not need to pre-sort.
     *
     * @param buyLots      Available buy lots at the time of the sell
     * @param sellQuantity Number of shares being sold
     * @return [FifoMatchResult] with matched lots, remaining inventory, and over-sell count
     */
    fun match(
        buyLots: List<BuyLot>,
        sellQuantity: Int,
    ): FifoMatchResult {
        require(sellQuantity >= 0) { "sellQuantity must be >= 0, was $sellQuantity" }

        if (sellQuantity == 0) {
            return FifoMatchResult(
                matchedLots = emptyList(),
                matchedCostBasis = Paisa.ZERO,
                remainingLots = buyLots.sortedBy { it.tradeDate },
                overSellQuantity = 0,
            )
        }

        val sorted = buyLots.sortedBy { it.tradeDate }

        val matchedLots = mutableListOf<MatchedLot>()
        var matchedCostBasis = Paisa.ZERO
        val remainingLots = mutableListOf<BuyLot>()
        var remaining = sellQuantity

        for (lot in sorted) {
            if (remaining <= 0) {
                // Sell fully covered — carry forward this lot unchanged
                remainingLots.add(lot)
                continue
            }

            val consumedFromLot = minOf(lot.quantity, remaining)
            remaining -= consumedFromLot

            // Proportional cost basis for the consumed portion (integer arithmetic)
            val lotCostBasis = Paisa((lot.totalValue.value * consumedFromLot) / lot.quantity)
            matchedLots.add(MatchedLot(lot.orderId, consumedFromLot, lotCostBasis))
            matchedCostBasis += lotCostBasis

            val leftInLot = lot.quantity - consumedFromLot
            if (leftInLot > 0) {
                // Partial consumption — create a residual lot with adjusted totalValue
                val residualValue = lot.totalValue - lotCostBasis
                remainingLots.add(
                    lot.copy(
                        quantity = leftInLot,
                        totalValue = residualValue,
                    ),
                )
            }
        }

        val overSellQuantity = if (remaining > 0) remaining else 0

        return FifoMatchResult(
            matchedLots = matchedLots,
            matchedCostBasis = matchedCostBasis,
            remainingLots = remainingLots,
            overSellQuantity = overSellQuantity,
        )
    }

    /**
     * Convenience function that applies a sequence of sells against an initial pool of buy lots,
     * threading the remaining inventory through each sell in order.
     *
     * Sells are applied in the order provided; callers must supply them in chronological order
     * if FIFO semantics across multiple sell events is required.
     *
     * @param allBuyLots   Full history of buy lots for a single instrument
     * @param sellQuantities  Ordered list of sell quantities to apply sequentially
     * @return Final [FifoMatchResult] after all sells have been applied;
     *                     [FifoMatchResult.remainingLots] reflects inventory after the last sell
     */
    fun computeHoldings(
        allBuyLots: List<BuyLot>,
        sellQuantities: List<Int>,
    ): FifoMatchResult {
        var currentLots = allBuyLots
        var lastResult =
            FifoMatchResult(
                matchedLots = emptyList(),
                matchedCostBasis = Paisa.ZERO,
                remainingLots = currentLots.sortedBy { it.tradeDate },
                overSellQuantity = 0,
            )

        for (qty in sellQuantities) {
            lastResult = match(currentLots, qty)
            currentLots = lastResult.remainingLots
        }

        return lastResult
    }
}
