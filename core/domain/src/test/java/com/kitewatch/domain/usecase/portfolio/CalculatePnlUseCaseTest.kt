package com.kitewatch.domain.usecase.portfolio

import app.cash.turbine.test
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.OrderRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class CalculatePnlUseCaseTest {
    private val orderRepo = mockk<OrderRepository>()
    private val chargeRateRepo = mockk<ChargeRateRepository>()
    private val useCase = CalculatePnlUseCase(orderRepo, chargeRateRepo)

    private val dateRange = LocalDate.of(2024, 1, 1)..LocalDate.of(2024, 12, 31)

    // ── Basic flow emission ───────────────────────────────────────────────────

    @Test
    fun `emits PnlSummary for given orders`() =
        runTest {
            val orders =
                listOf(
                    buy("INFY", qty = 10, price = 150_000L, date = LocalDate.of(2024, 1, 10)),
                    sell("INFY", qty = 5, price = 180_000L, date = LocalDate.of(2024, 3, 15)),
                )
            every { orderRepo.observeAll() } returns flowOf(orders)
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute(dateRange).test {
                val summary = awaitItem()
                assertNotNull(summary)
                // Sell value = 5 × ₹1,800 = ₹9,000 = 900_000 paisa
                assertEquals(Paisa(900_000L), summary.totalSellValue)
                awaitComplete()
            }
        }

    @Test
    fun `new order emission triggers new PnlSummary`() =
        runTest {
            val firstBatch =
                listOf(
                    buy("INFY", qty = 10, price = 150_000L, date = LocalDate.of(2024, 1, 10)),
                )
            val secondBatch =
                firstBatch +
                    listOf(
                        sell("INFY", qty = 5, price = 180_000L, date = LocalDate.of(2024, 3, 15)),
                    )

            every { orderRepo.observeAll() } returns flowOf(firstBatch, secondBatch)
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute(dateRange).test {
                val first = awaitItem()
                val second = awaitItem()
                // First batch has no sells — zero sell value
                assertEquals(Paisa.ZERO, first.totalSellValue)
                // Second batch has a sell
                assertEquals(Paisa(900_000L), second.totalSellValue)
                awaitComplete()
            }
        }

    @Test
    fun `stockCodeFilter restricts PnL to named instrument`() =
        runTest {
            val orders =
                listOf(
                    buy("INFY", qty = 10, price = 150_000L, date = LocalDate.of(2024, 1, 10)),
                    sell("INFY", qty = 5, price = 180_000L, date = LocalDate.of(2024, 3, 15)),
                    buy("TCS", qty = 5, price = 350_000L, date = LocalDate.of(2024, 1, 20)),
                    sell("TCS", qty = 5, price = 400_000L, date = LocalDate.of(2024, 4, 10)),
                )
            every { orderRepo.observeAll() } returns flowOf(orders)
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute(dateRange, stockCodeFilter = "INFY").test {
                val summary = awaitItem()
                // Only INFY sell: 5 × ₹1,800 = 900_000 paisa
                assertEquals(Paisa(900_000L), summary.totalSellValue)
                awaitComplete()
            }
        }

    @Test
    fun `sells outside dateRange do not contribute to sell value but deplete FIFO pool`() =
        runTest {
            // Buy 10, sell 5 in 2023 (outside range), sell 5 in 2024 (in range)
            val orders =
                listOf(
                    buy("INFY", qty = 10, price = 150_000L, date = LocalDate.of(2023, 6, 1)),
                    sell("INFY", qty = 5, price = 160_000L, date = LocalDate.of(2023, 9, 1)),
                    sell("INFY", qty = 5, price = 180_000L, date = LocalDate.of(2024, 3, 15)),
                )
            every { orderRepo.observeAll() } returns flowOf(orders)
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute(dateRange).test {
                val summary = awaitItem()
                // Only the 2024 sell is in-range
                assertEquals(Paisa(900_000L), summary.totalSellValue)
                // Cost basis for in-range sell = 5 × ₹1,500 = 750_000 (FIFO from original buy)
                assertEquals(Paisa(750_000L), summary.totalBuyCostOfSoldLots)
                awaitComplete()
            }
        }

    @Test
    fun `null charge rates produces zero-charge PnlSummary`() =
        runTest {
            val orders =
                listOf(
                    buy("INFY", qty = 10, price = 150_000L, date = LocalDate.of(2024, 1, 10)),
                    sell("INFY", qty = 10, price = 200_000L, date = LocalDate.of(2024, 3, 15)),
                )
            every { orderRepo.observeAll() } returns flowOf(orders)
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute(dateRange).test {
                val summary = awaitItem()
                assertEquals(Paisa.ZERO, summary.totalCharges)
                awaitComplete()
            }
        }

    @Test
    fun `empty order list emits zero PnlSummary`() =
        runTest {
            every { orderRepo.observeAll() } returns flowOf(emptyList())
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            useCase.execute(dateRange).test {
                val summary = awaitItem()
                assertEquals(Paisa.ZERO, summary.realizedPnl)
                assertEquals(Paisa.ZERO, summary.totalSellValue)
                awaitComplete()
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private var nextId = 1L

    private fun buy(
        stockCode: String,
        qty: Int,
        price: Long,
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
        price: Long,
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
