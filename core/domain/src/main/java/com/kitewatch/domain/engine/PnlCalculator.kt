package com.kitewatch.domain.engine

import com.kitewatch.domain.model.ChargeBreakdown
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.PnlSummary
import java.time.LocalDate

/**
 * Pure, stateless P&L calculation engine.
 *
 * Computes realized P&L for a reporting period using FIFO cost basis attribution.
 * Sells outside the date range are still processed chronologically to correctly
 * deplete the buy lot pool before in-range sells are matched.
 */
object PnlCalculator {
    /**
     * Compute realized P&L for SELL orders within [dateRange].
     *
     * Cost basis for each in-range SELL is derived from the **complete** buy lot
     * history (all dates) so that earlier sells — even those before the reporting
     * period — correctly reduce the available lot pool before in-range sells are
     * matched.
     *
     * @param allOrders         Complete order history (BUY and SELL, all dates, all stocks)
     * @param chargesByOrderId  Map of [Order.orderId] → [ChargeBreakdown] for any order that
     *                          incurred charges; missing entries are treated as zero-charge
     * @param dateRange         Inclusive date range defining the P&L reporting period
     * @param stockCodeFilter   When non-null, restricts computation to the named instrument
     * @return [PnlSummary] with realized P&L, charge totals, and component breakdowns
     */
    @Suppress("NestedBlockDepth") // FIFO lot-matching loop requires per-stock, per-order nesting by design.
    fun calculate(
        allOrders: List<Order>,
        chargesByOrderId: Map<Long, ChargeBreakdown>,
        dateRange: ClosedRange<LocalDate>,
        stockCodeFilter: String? = null,
    ): PnlSummary {
        val orders =
            if (stockCodeFilter != null) {
                allOrders.filter { it.stockCode == stockCodeFilter }
            } else {
                allOrders
            }

        // Aggregate charge components for every order whose trade date falls in the period
        var aggStt = Paisa.ZERO
        var aggExchangeTxn = Paisa.ZERO
        var aggSebiCharges = Paisa.ZERO
        var aggStampDuty = Paisa.ZERO
        var aggGst = Paisa.ZERO

        orders
            .filter { it.tradeDate in dateRange }
            .forEach { order ->
                chargesByOrderId[order.orderId]?.let { bd ->
                    aggStt += bd.stt
                    aggExchangeTxn += bd.exchangeTxn
                    aggSebiCharges += bd.sebiCharges
                    aggStampDuty += bd.stampDuty
                    aggGst += bd.gst
                }
            }

        // Per-stock FIFO matching: apply sells in chronological order so prior sells
        // (outside the range) consume lots before in-range sells are matched.
        var totalSellValue = Paisa.ZERO
        var totalBuyCostOfSoldLots = Paisa.ZERO

        orders
            .groupBy { it.stockCode }
            .forEach { (_, stockOrders) ->
                val sorted = stockOrders.sortedBy { it.tradeDate }
                val currentLots = mutableListOf<BuyLot>()

                for (order in sorted) {
                    when (order.orderType) {
                        OrderType.BUY ->
                            currentLots.add(
                                BuyLot(
                                    orderId = order.orderId,
                                    quantity = order.quantity,
                                    pricePerUnit = order.price,
                                    totalValue = order.totalValue,
                                    tradeDate = order.tradeDate,
                                ),
                            )
                        OrderType.SELL -> {
                            val matchResult = FifoLotMatcher.match(currentLots, order.quantity)
                            // Advance pool regardless of whether this sell is in-range
                            currentLots.clear()
                            currentLots.addAll(matchResult.remainingLots)

                            if (order.tradeDate in dateRange) {
                                totalSellValue += order.totalValue
                                totalBuyCostOfSoldLots += matchResult.matchedCostBasis
                            }
                        }
                    }
                }
            }

        val chargeBreakdown =
            ChargeBreakdown(
                stt = aggStt,
                exchangeTxn = aggExchangeTxn,
                sebiCharges = aggSebiCharges,
                stampDuty = aggStampDuty,
                gst = aggGst,
            )
        val totalCharges = chargeBreakdown.total()
        val realizedPnl = totalSellValue - totalBuyCostOfSoldLots - totalCharges

        return PnlSummary(
            realizedPnl = realizedPnl,
            totalSellValue = totalSellValue,
            totalBuyCostOfSoldLots = totalBuyCostOfSoldLots,
            totalCharges = totalCharges,
            chargeBreakdown = chargeBreakdown,
            dateRange = dateRange,
        )
    }
}
