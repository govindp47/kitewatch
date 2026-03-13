package com.kitewatch.domain.engine

import com.kitewatch.domain.model.ChargeBreakdown
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa

/**
 * Derived holdings state for a single instrument.
 *
 * @param stockCode       Instrument identifier
 * @param quantity        Total remaining shares (0 after full exit — still included in output)
 * @param avgBuyPrice     Weighted average buy price of remaining lots; [Paisa.ZERO] when quantity is 0
 * @param investedAmount  Sum of [BuyLot.totalValue] across all remaining lots
 * @param remainingLots   Unconsumed buy lots in FIFO order
 * @param totalBuyCharges Sum of [ChargeBreakdown.total] for buy orders whose lots are still in [remainingLots]
 */
data class ComputedHolding(
    val stockCode: String,
    val quantity: Int,
    val avgBuyPrice: Paisa,
    val investedAmount: Paisa,
    val remainingLots: List<BuyLot>,
    val totalBuyCharges: Paisa,
)

/**
 * Pure, stateless engine that derives the current holdings state from a complete order history.
 *
 * Uses [FifoLotMatcher] internally to attribute cost basis to remaining inventory.
 * Holdings with [ComputedHolding.quantity] == 0 (full exits) are included in the output;
 * filtering is the responsibility of the caller.
 */
object HoldingsComputationEngine {
    /**
     * Compute holdings for every instrument present in [orders].
     *
     * @param orders           Full order history (BUY and SELL, any order)
     * @param chargesByOrderId Map of domain [Order.orderId] to [ChargeBreakdown] for BUY orders
     * @return One [ComputedHolding] per distinct [Order.stockCode], including zero-quantity exits
     */
    fun compute(
        orders: List<Order>,
        chargesByOrderId: Map<Long, ChargeBreakdown>,
    ): List<ComputedHolding> =
        orders
            .groupBy { it.stockCode }
            .map { (stockCode, stockOrders) ->
                computeForStock(stockCode, stockOrders, chargesByOrderId)
            }

    private fun computeForStock(
        stockCode: String,
        orders: List<Order>,
        chargesByOrderId: Map<Long, ChargeBreakdown>,
    ): ComputedHolding {
        val sorted = orders.sortedBy { it.tradeDate }

        val buyLots =
            sorted
                .filter { it.orderType == OrderType.BUY }
                .map { order ->
                    BuyLot(
                        orderId = order.orderId,
                        quantity = order.quantity,
                        pricePerUnit = order.price,
                        totalValue = order.totalValue,
                        tradeDate = order.tradeDate,
                    )
                }

        val sellQuantities =
            sorted
                .filter { it.orderType == OrderType.SELL }
                .map { it.quantity }

        val remainingLots = FifoLotMatcher.computeHoldings(buyLots, sellQuantities).remainingLots

        val quantity = remainingLots.sumOf { it.quantity }
        val investedAmount = remainingLots.fold(Paisa.ZERO) { acc, lot -> acc + lot.totalValue }
        val avgBuyPrice = if (quantity > 0) investedAmount / quantity else Paisa.ZERO

        val remainingOrderIds = remainingLots.map { it.orderId }.toSet()
        val totalBuyCharges =
            remainingOrderIds.fold(Paisa.ZERO) { acc, orderId ->
                acc + (chargesByOrderId[orderId]?.total() ?: Paisa.ZERO)
            }

        return ComputedHolding(
            stockCode = stockCode,
            quantity = quantity,
            avgBuyPrice = avgBuyPrice,
            investedAmount = investedAmount,
            remainingLots = remainingLots,
            totalBuyCharges = totalBuyCharges,
        )
    }
}
