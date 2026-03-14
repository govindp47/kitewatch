package com.kitewatch.domain.usecase.holdings

import com.kitewatch.domain.engine.ChargeCalculator
import com.kitewatch.domain.engine.HoldingsComputationEngine
import com.kitewatch.domain.model.ChargeBreakdown
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.HoldingRepository
import com.kitewatch.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Reactively produces the active holdings list for the Holdings screen.
 *
 * On each order-list emission, the [HoldingsComputationEngine] derives fresh quantity /
 * average-price / cost-basis values from the full order history. These computed values
 * are merged with the metadata (stock name, profit target, target sell price, timestamps)
 * that is stored in [HoldingRepository] by the sync write path.
 *
 * Zero-quantity holdings (fully exited positions) are **excluded** from the emitted list.
 */
class GetHoldingsUseCase(
    private val orderRepo: OrderRepository,
    private val holdingRepo: HoldingRepository,
    private val chargeRateRepo: ChargeRateRepository,
) {
    fun execute(): Flow<List<Holding>> =
        orderRepo.observeAll().map { allOrders ->
            val chargesByOrderId = buildChargesMap(allOrders)
            val computed = HoldingsComputationEngine.compute(allOrders, chargesByOrderId)

            // Load stored metadata once and index by stockCode for O(1) lookup.
            val storedByCode = holdingRepo.getAll().associateBy { it.stockCode }
            val now = Instant.now()

            computed
                .filter { it.quantity > 0 }
                .map { ch ->
                    val stored = storedByCode[ch.stockCode]
                    Holding(
                        holdingId = stored?.holdingId ?: 0L,
                        stockCode = ch.stockCode,
                        stockName = stored?.stockName ?: ch.stockCode,
                        quantity = ch.quantity,
                        avgBuyPrice = ch.avgBuyPrice,
                        investedAmount = ch.investedAmount,
                        totalBuyCharges = ch.totalBuyCharges,
                        profitTarget = stored?.profitTarget ?: ProfitTarget.Percentage(DEFAULT_PROFIT_TARGET_BPS),
                        targetSellPrice = stored?.targetSellPrice ?: Paisa.ZERO,
                        createdAt = stored?.createdAt ?: now,
                        updatedAt = stored?.updatedAt ?: now,
                    )
                }
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

    private companion object {
        const val DEFAULT_PROFIT_TARGET_BPS = 500 // 5%
    }
}
