package com.kitewatch.domain.usecase.portfolio

import com.kitewatch.domain.engine.ChargeCalculator
import com.kitewatch.domain.engine.PnlCalculator
import com.kitewatch.domain.model.ChargeBreakdown
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.PnlSummary
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Reactively computes realized P&L for a date range by delegating to [PnlCalculator].
 *
 * Emits a new [PnlSummary] whenever the order list changes (e.g. after a sync).
 * Charges are re-derived from the current [ChargeRateRepository] rates on each emission;
 * if no rates are stored the charge map is empty (charges treated as ₹0).
 */
class CalculatePnlUseCase(
    private val orderRepo: OrderRepository,
    private val chargeRateRepo: ChargeRateRepository,
) {
    /**
     * @param dateRange        Inclusive reporting period — only SELLs within this range
     *                         are included in realized P&L. The full order history is still
     *                         used for FIFO lot-pool depletion outside the range.
     * @param stockCodeFilter  When non-null, restricts P&L to a single instrument.
     */
    fun execute(
        dateRange: ClosedRange<LocalDate>,
        stockCodeFilter: String? = null,
    ): Flow<PnlSummary> =
        orderRepo.observeAll().map { allOrders ->
            val chargesByOrderId = buildChargesMap(allOrders)
            PnlCalculator.calculate(allOrders, chargesByOrderId, dateRange, stockCodeFilter)
        }

    private suspend fun buildChargesMap(orders: List<Order>): Map<Long, ChargeBreakdown> {
        val rates = chargeRateRepo.getCurrentRates() ?: return emptyMap()
        return orders
            .filter { it.orderType == OrderType.BUY }
            .associate { order ->
                order.orderId to
                    ChargeCalculator.calculate(
                        tradeValue = order.totalValue,
                        orderType = order.orderType,
                        exchange = order.exchange,
                        rates = rates,
                    )
            }
    }
}
