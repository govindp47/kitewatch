package com.kitewatch.domain.engine

import com.kitewatch.domain.model.ChargeBreakdown
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [HoldingsComputationEngine.compute] (T-027).
 *
 * Covers: multi-buy/sell sequences, full exit (quantity=0 in output), re-buy after full exit,
 * multi-stock isolation, charge attribution to remaining lots, and no-sell baseline.
 */
class HoldingsComputationEngineTest {
    private val jan1 = LocalDate.of(2024, 1, 1)
    private val jan2 = LocalDate.of(2024, 1, 2)
    private val jan3 = LocalDate.of(2024, 1, 3)
    private val jan4 = LocalDate.of(2024, 1, 4)
    private val jan5 = LocalDate.of(2024, 1, 5)

    private fun buy(
        orderId: Long,
        stockCode: String,
        qty: Int,
        pricePerUnit: Long,
        date: LocalDate,
    ) = Order(
        orderId = orderId,
        zerodhaOrderId = "Z$orderId",
        stockCode = stockCode,
        stockName = stockCode,
        orderType = OrderType.BUY,
        quantity = qty,
        price = Paisa(pricePerUnit),
        totalValue = Paisa(pricePerUnit * qty),
        tradeDate = date,
        exchange = Exchange.NSE,
        settlementId = null,
        source = OrderSource.SYNC,
    )

    private fun sell(
        orderId: Long,
        stockCode: String,
        qty: Int,
        pricePerUnit: Long,
        date: LocalDate,
    ) = Order(
        orderId = orderId,
        zerodhaOrderId = "Z$orderId",
        stockCode = stockCode,
        stockName = stockCode,
        orderType = OrderType.SELL,
        quantity = qty,
        price = Paisa(pricePerUnit),
        totalValue = Paisa(pricePerUnit * qty),
        tradeDate = date,
        exchange = Exchange.NSE,
        settlementId = null,
        source = OrderSource.SYNC,
    )

    private fun charges(vararg pairs: Pair<Long, Int>): Map<Long, ChargeBreakdown> =
        pairs.associate { (orderId, total) ->
            orderId to
                ChargeBreakdown(
                    stt = Paisa(total.toLong()),
                    exchangeTxn = Paisa.ZERO,
                    sebiCharges = Paisa.ZERO,
                    stampDuty = Paisa.ZERO,
                    gst = Paisa.ZERO,
                )
        }

    // ──────────────────────────────────────────────────────────────────────────
    // TC1: 3 buys + 2 partial sells — verify remaining qty and avgBuyPrice
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC1 - three buys two partial sells - remaining quantity and avgBuyPrice correct`() {
        // Buy 10@100=1000 (jan1), Buy 10@200=2000 (jan2), Buy 10@300=3000 (jan3)
        // Sell 5 (jan4): consumes 5 from jan1 → lotCostBasis=(1000*5)/10=500, jan1 remaining tv=500
        // Sell 8 (jan5): consumes remaining 5 from jan1 (costBasis=(500*5)/5=500),
        //                then 3 from jan2 (costBasis=(2000*3)/10=600), jan2 remaining tv=2000-600=1400
        // Remaining: jan2 qty=7 tv=1400, jan3 qty=10 tv=3000
        // investedAmount = 4400, quantity = 17, avgBuyPrice = 4400/17 = 258
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1),
                buy(2L, "INFY", 10, 200L, jan2),
                buy(3L, "INFY", 10, 300L, jan3),
                sell(4L, "INFY", 5, 150L, jan4),
                sell(5L, "INFY", 8, 160L, jan5),
            )

        val result = HoldingsComputationEngine.compute(orders, emptyMap())
        assertEquals(1, result.size)
        val h = result[0]

        assertEquals("INFY", h.stockCode)
        assertEquals(17, h.quantity)
        assertEquals(Paisa(4400L), h.investedAmount)
        assertEquals(Paisa(258L), h.avgBuyPrice) // 4400 / 17 = 258 (integer truncation)
        assertEquals(2, h.remainingLots.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC2: Full exit — quantity=0 holding included in output
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC2 - full exit - quantity zero holding present in output`() {
        val orders =
            listOf(
                buy(1L, "TCS", 5, 1000L, jan1),
                sell(2L, "TCS", 5, 1200L, jan2),
            )

        val result = HoldingsComputationEngine.compute(orders, emptyMap())
        assertEquals(1, result.size)
        val h = result[0]

        assertEquals("TCS", h.stockCode)
        assertEquals(0, h.quantity)
        assertEquals(Paisa.ZERO, h.investedAmount)
        assertEquals(Paisa.ZERO, h.avgBuyPrice)
        assertTrue(h.remainingLots.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC3: Re-buy after full exit — avgBuyPrice reflects new lot only
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC3 - rebuy after full exit - avgBuyPrice reflects new lot price not old`() {
        // Buy 10@100 (jan1), Sell 10 (jan2) → full exit
        // Buy 5@500 (jan3) → new pool contains only new lot
        val orders =
            listOf(
                buy(1L, "WIPRO", 10, 100L, jan1),
                sell(2L, "WIPRO", 10, 120L, jan2),
                buy(3L, "WIPRO", 5, 500L, jan3),
            )

        val result = HoldingsComputationEngine.compute(orders, emptyMap())
        assertEquals(1, result.size)
        val h = result[0]

        assertEquals("WIPRO", h.stockCode)
        assertEquals(5, h.quantity)
        assertEquals(Paisa(2500L), h.investedAmount) // 500 * 5
        assertEquals(Paisa(500L), h.avgBuyPrice) // 2500 / 5
        assertEquals(1, h.remainingLots.size)
        assertEquals(3L, h.remainingLots[0].orderId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC4: Multiple stocks — each computed independently
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC4 - multiple stocks - each stock computed in isolation`() {
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1), // tv=1000
                buy(2L, "TCS", 5, 200L, jan2), // tv=1000
                sell(3L, "INFY", 3, 120L, jan3), // consumes 3 from jan1: (1000*3)/10=300, remaining tv=700
            )

        val result =
            HoldingsComputationEngine
                .compute(orders, emptyMap())
                .associateBy { it.stockCode }

        assertEquals(2, result.size)

        val infy = result["INFY"]!!
        assertEquals(7, infy.quantity)
        assertEquals(Paisa(700L), infy.investedAmount)
        assertEquals(Paisa(100L), infy.avgBuyPrice) // 700 / 7 = 100

        val tcs = result["TCS"]!!
        assertEquals(5, tcs.quantity)
        assertEquals(Paisa(1000L), tcs.investedAmount)
        assertEquals(Paisa(200L), tcs.avgBuyPrice)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC5: Charges attributed only to buy orders whose lots are still remaining
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC5 - charges summed for remaining buy orderIds only - consumed lot charges excluded`() {
        // Buy 10@100 orderId=1 (charge=50), Buy 10@200 orderId=2 (charge=100)
        // Sell 10 (jan3) → fully consumes orderId=1 lot
        // Remaining: orderId=2 only → totalBuyCharges = 100
        val orders =
            listOf(
                buy(1L, "HDFC", 10, 100L, jan1),
                buy(2L, "HDFC", 10, 200L, jan2),
                sell(3L, "HDFC", 10, 120L, jan3),
            )
        val chargeMap = charges(1L to 50, 2L to 100)

        val result = HoldingsComputationEngine.compute(orders, chargeMap)
        assertEquals(1, result.size)
        val h = result[0]

        assertEquals("HDFC", h.stockCode)
        assertEquals(10, h.quantity)
        assertEquals(Paisa(100L), h.totalBuyCharges) // only orderId=2 remains
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC6: No sell orders — all buy lots remain unchanged
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC6 - no sell orders - all buy lots retained investedAmount and avgBuyPrice correct`() {
        // Buy 10@500=5000 (jan1), Buy 5@600=3000 (jan2)
        // investedAmount = 8000, quantity = 15, avgBuyPrice = 8000/15 = 533
        val orders =
            listOf(
                buy(1L, "RELIANCE", 10, 500L, jan1),
                buy(2L, "RELIANCE", 5, 600L, jan2),
            )

        val result = HoldingsComputationEngine.compute(orders, emptyMap())
        assertEquals(1, result.size)
        val h = result[0]

        assertEquals(15, h.quantity)
        assertEquals(Paisa(8000L), h.investedAmount)
        assertEquals(Paisa(533L), h.avgBuyPrice) // 8000/15=533 (integer truncation)
        assertEquals(2, h.remainingLots.size)
        assertEquals(Paisa.ZERO, h.totalBuyCharges)
    }
}
