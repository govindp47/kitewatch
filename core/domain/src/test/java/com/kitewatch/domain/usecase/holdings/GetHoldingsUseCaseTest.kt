package com.kitewatch.domain.usecase.holdings

import app.cash.turbine.test
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.HoldingRepository
import com.kitewatch.domain.repository.OrderRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GetHoldingsUseCaseTest {
    private val orderRepo = mockk<OrderRepository>()
    private val holdingRepo = mockk<HoldingRepository>()
    private val chargeRateRepo = mockk<ChargeRateRepository>()
    private val useCase = GetHoldingsUseCase(orderRepo, holdingRepo, chargeRateRepo)

    // ── Filtering ─────────────────────────────────────────────────────────────

    @Test
    fun `fully-exited stock (quantity=0) is excluded from emissions`() =
        runTest {
            // INFY: 10 bought, 10 sold → quantity=0 (should be excluded)
            // TCS:  5 bought, 0 sold   → quantity=5 (should be included)
            val orders =
                listOf(
                    buy("INFY", qty = 10, date = LocalDate.of(2024, 1, 10)),
                    sell("INFY", qty = 10, date = LocalDate.of(2024, 3, 15)),
                    buy("TCS", qty = 5, date = LocalDate.of(2024, 1, 20)),
                )
            every { orderRepo.observeAll() } returns flowOf(orders)
            coEvery { holdingRepo.getAll() } returns emptyList()
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute().test {
                val holdings = awaitItem()
                assertEquals(1, holdings.size)
                assertEquals("TCS", holdings.first().stockCode)
                awaitComplete()
            }
        }

    @Test
    fun `only active holdings returned on new order emission`() =
        runTest {
            val firstBatch = listOf(buy("INFY", qty = 10, date = LocalDate.of(2024, 1, 10)))
            val secondBatch =
                firstBatch +
                    listOf(
                        sell("INFY", qty = 10, date = LocalDate.of(2024, 3, 15)),
                        buy("TCS", qty = 5, date = LocalDate.of(2024, 3, 16)),
                    )

            every { orderRepo.observeAll() } returns flowOf(firstBatch, secondBatch)
            coEvery { holdingRepo.getAll() } returns emptyList()
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute().test {
                val first = awaitItem()
                assertEquals(1, first.size) // INFY with qty=10
                assertEquals("INFY", first.first().stockCode)
                assertEquals(10, first.first().quantity)

                val second = awaitItem()
                assertEquals(1, second.size) // INFY sold out; TCS remains
                assertEquals("TCS", second.first().stockCode)
                awaitComplete()
            }
        }

    // ── Metadata merge ────────────────────────────────────────────────────────

    @Test
    fun `stored metadata (stockName, profitTarget, targetSellPrice) is applied from holdingRepo`() =
        runTest {
            val orders = listOf(buy("INFY", qty = 10, date = LocalDate.of(2024, 1, 10)))
            val storedHolding =
                Holding(
                    holdingId = 99L,
                    stockCode = "INFY",
                    stockName = "Infosys Ltd",
                    quantity = 10,
                    avgBuyPrice = Paisa(150_000L),
                    investedAmount = Paisa(1_500_000L),
                    totalBuyCharges = Paisa.ZERO,
                    profitTarget = ProfitTarget.Percentage(700),
                    targetSellPrice = Paisa(170_000L),
                    createdAt = Instant.parse("2024-01-10T00:00:00Z"),
                    updatedAt = Instant.parse("2024-01-10T00:00:00Z"),
                )
            every { orderRepo.observeAll() } returns flowOf(orders)
            coEvery { holdingRepo.getAll() } returns listOf(storedHolding)
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute().test {
                val holdings = awaitItem()
                val holding = holdings.first()
                assertEquals("Infosys Ltd", holding.stockName)
                assertEquals(ProfitTarget.Percentage(700), holding.profitTarget)
                assertEquals(Paisa(170_000L), holding.targetSellPrice)
                assertEquals(99L, holding.holdingId)
                awaitComplete()
            }
        }

    @Test
    fun `defaults applied when no stored holding exists for stock`() =
        runTest {
            val orders = listOf(buy("INFY", qty = 5, date = LocalDate.of(2024, 1, 10)))
            every { orderRepo.observeAll() } returns flowOf(orders)
            coEvery { holdingRepo.getAll() } returns emptyList()
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute().test {
                val holding = awaitItem().first()
                assertEquals("INFY", holding.stockName) // defaults to stockCode
                assertEquals(ProfitTarget.Percentage(500), holding.profitTarget) // 5% default
                assertEquals(Paisa.ZERO, holding.targetSellPrice)
                assertEquals(0L, holding.holdingId)
                awaitComplete()
            }
        }

    // ── Computed values ───────────────────────────────────────────────────────

    @Test
    fun `quantity and avgBuyPrice are taken from engine output not stored holding`() =
        runTest {
            // 10 bought at ₹1,500; 5 sold → 5 remaining, avgBuyPrice = ₹1,500
            val orders =
                listOf(
                    buy("INFY", qty = 10, price = 150_000L, date = LocalDate.of(2024, 1, 10)),
                    sell("INFY", qty = 5, date = LocalDate.of(2024, 3, 15)),
                )
            // Stored holding has stale data (will be overridden by computed values)
            val staleStored =
                Holding(
                    holdingId = 1L,
                    stockCode = "INFY",
                    stockName = "Infosys Ltd",
                    quantity = 10, // stale — should be overridden by computed=5
                    avgBuyPrice = Paisa(100_000L), // stale — should be overridden
                    investedAmount = Paisa(1_000_000L),
                    totalBuyCharges = Paisa.ZERO,
                    profitTarget = ProfitTarget.Percentage(500),
                    targetSellPrice = Paisa(120_000L),
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                )
            every { orderRepo.observeAll() } returns flowOf(orders)
            coEvery { holdingRepo.getAll() } returns listOf(staleStored)
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute().test {
                val holding = awaitItem().first()
                assertEquals(5, holding.quantity) // computed, not stored
                assertEquals(Paisa(150_000L), holding.avgBuyPrice) // computed from remaining lots
                awaitComplete()
            }
        }

    @Test
    fun `empty order list emits empty holdings list`() =
        runTest {
            every { orderRepo.observeAll() } returns flowOf(emptyList())
            coEvery { holdingRepo.getAll() } returns emptyList()
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute().test {
                assertTrue(awaitItem().isEmpty())
                awaitComplete()
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private var nextId = 1L

    private fun buy(
        stockCode: String,
        qty: Int,
        price: Long = 150_000L,
        date: LocalDate,
    ) = Order(
        orderId = nextId++,
        zerodhaOrderId = "Z$nextId",
        stockCode = stockCode,
        stockName = stockCode,
        orderType = OrderType.BUY,
        quantity = qty,
        price = Paisa(price),
        totalValue = Paisa(price * qty),
        tradeDate = date,
        exchange = Exchange.NSE,
        settlementId = null,
        source = OrderSource.SYNC,
    )

    private fun sell(
        stockCode: String,
        qty: Int,
        price: Long = 160_000L,
        date: LocalDate,
    ) = Order(
        orderId = nextId++,
        zerodhaOrderId = "Z$nextId",
        stockCode = stockCode,
        stockName = stockCode,
        orderType = OrderType.SELL,
        quantity = qty,
        price = Paisa(price),
        totalValue = Paisa(price * qty),
        tradeDate = date,
        exchange = Exchange.NSE,
        settlementId = null,
        source = OrderSource.SYNC,
    )
}
