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
 * Fixture-driven tests for [PnlCalculator.calculate] (T-028 smoke tests + T-029 full suite).
 *
 * pricePerUnit in the [buy]/[sell] helpers is in rupees (₹); the helpers multiply by 100 to
 * produce paisa. Example: pricePerUnit=100 → price = 10,000 paisa = ₹100/share.
 *
 * reportRange = jan10..jan20 (inclusive on both ends).
 */
class PnlCalculatorTest {
    private val jan1 = LocalDate.of(2024, 1, 1)
    private val jan3 = LocalDate.of(2024, 1, 3)
    private val jan5 = LocalDate.of(2024, 1, 5)
    private val jan8 = LocalDate.of(2024, 1, 8)
    private val jan9 = LocalDate.of(2024, 1, 9)
    private val jan10 = LocalDate.of(2024, 1, 10)
    private val jan12 = LocalDate.of(2024, 1, 12)
    private val jan15 = LocalDate.of(2024, 1, 15)
    private val jan20 = LocalDate.of(2024, 1, 20)
    private val jan21 = LocalDate.of(2024, 1, 21)

    private val reportRange = jan10..jan20

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
        price = Paisa(pricePerUnit * 100L),
        totalValue = Paisa(pricePerUnit * qty * 100L),
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
        price = Paisa(pricePerUnit * 100L),
        totalValue = Paisa(pricePerUnit * qty * 100L),
        tradeDate = date,
        exchange = Exchange.NSE,
        settlementId = null,
        source = OrderSource.SYNC,
    )

    /** Flat charge breakdown whose total() equals [total] paisa, applied entirely as STT. */
    private fun flatCharge(total: Long) =
        ChargeBreakdown(
            stt = Paisa(total),
            exchangeTxn = Paisa.ZERO,
            sebiCharges = Paisa.ZERO,
            stampDuty = Paisa.ZERO,
            gst = Paisa.ZERO,
        )

    // ──────────────────────────────────────────────────────────────────────────
    // TC1: Smoke — 1 buy + 1 sell in range → profit = sell − cost − charges
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC1 - smoke buy then sell in range - correct profit`() {
        // Buy 100 @ ₹100 = ₹10,000 (jan1), charge = ₹100
        // Sell 100 @ ₹120 = ₹12,000 (jan15, in range), charge = ₹120
        // Gross P&L = ₹12,000 − ₹10,000 = ₹2,000
        // Net P&L   = ₹2,000 − (₹100 buy charge + ₹120 sell charge) = ₹1,780
        val orders =
            listOf(
                buy(1L, "INFY", 100, 100L, jan1),
                sell(2L, "INFY", 100, 120L, jan15),
            )
        val charges =
            mapOf(
                1L to flatCharge(10_000L), // ₹100 buy charge in paisa
                2L to flatCharge(12_000L), // ₹120 sell charge in paisa
            )

        val result = PnlCalculator.calculate(orders, charges, reportRange)

        assertEquals(Paisa(1_200_000L), result.totalSellValue) // 100 × 12,000 paisa = 1,200,000
        assertEquals(Paisa(1_000_000L), result.totalBuyCostOfSoldLots) // 100 × 10,000 paisa = 1,000,000
        // Only orders in reportRange (jan10..jan20) contribute charges.
        // jan1 buy is outside range; jan15 sell is in range → only sell charge counted.
        assertEquals(Paisa(12_000L), result.totalCharges)
        assertEquals(Paisa(1_200_000L - 1_000_000L - 12_000L), result.realizedPnl) // 188,000
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC2: No sells in range → realizedPnl = Paisa.ZERO
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC2 - no sells in range - realizedPnl is zero`() {
        // Sell is outside the reportRange (jan5 < jan10)
        val orders =
            listOf(
                buy(1L, "TCS", 10, 200L, jan1),
                sell(2L, "TCS", 10, 250L, jan5),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa.ZERO, result.totalSellValue)
        assertEquals(Paisa.ZERO, result.totalBuyCostOfSoldLots)
        assertEquals(Paisa.ZERO, result.totalCharges)
        assertEquals(Paisa.ZERO, result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC3: Loss scenario → negative realizedPnl
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC3 - sell below cost basis - negative realizedPnl`() {
        // Buy 10 @ ₹500 = ₹5,000 (jan1), sell 10 @ ₹400 = ₹4,000 (jan15)
        // Gross P&L = ₹4,000 − ₹5,000 = −₹1,000
        // No charges for simplicity
        val orders =
            listOf(
                buy(1L, "WIPRO", 10, 500L, jan1),
                sell(2L, "WIPRO", 10, 400L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertTrue(result.realizedPnl.isNegative())
        assertEquals(Paisa(400_000L), result.totalSellValue)
        assertEquals(Paisa(500_000L), result.totalBuyCostOfSoldLots)
        assertEquals(Paisa(-100_000L), result.realizedPnl) // 400_000 − 500_000 − 0
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC4: Prior sell (outside range) depletes lot pool before in-range sell
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC4 - prior sell outside range depletes pool - in-range sell uses correct remaining lots`() {
        // Buy 10 @ ₹100 = ₹1,000 (jan1)
        // Sell 5 (jan5, OUTSIDE range) → consumes 5 lots @ ₹100 = ₹500 cost basis
        // Buy 5 @ ₹200 = ₹1,000 (jan8, outside range)
        // Sell 5 (jan15, IN range) → remaining pool: [5 @ ₹100=₹500, 5 @ ₹200=₹1,000]
        //   FIFO picks 5 @ ₹100 → cost basis = ₹500
        //   Sell at ₹150 × 5 = ₹750
        //   P&L = ₹750 − ₹500 = ₹250 (no charges)
        val orders =
            listOf(
                buy(1L, "HDFC", 10, 100L, jan1),
                sell(2L, "HDFC", 5, 120L, jan5),
                buy(3L, "HDFC", 5, 200L, jan8),
                sell(4L, "HDFC", 5, 150L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(75_000L), result.totalSellValue) // 5 × 15,000 paisa
        assertEquals(Paisa(50_000L), result.totalBuyCostOfSoldLots) // 5 × 10,000 paisa (FIFO from jan1 lot remainder)
        assertEquals(Paisa(25_000L), result.realizedPnl) // 75,000 − 50,000
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC5: stockCodeFilter — only the filtered stock is included
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC5 - stockCodeFilter - only matching stock contributes to result`() {
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1),
                sell(2L, "INFY", 10, 120L, jan15),
                buy(3L, "TCS", 5, 200L, jan1),
                sell(4L, "TCS", 5, 300L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange, stockCodeFilter = "INFY")

        // Only INFY orders; TCS ignored
        assertEquals(Paisa(120_000L), result.totalSellValue) // 10 × 12,000 paisa
        assertEquals(Paisa(100_000L), result.totalBuyCostOfSoldLots) // 10 × 10,000 paisa
        assertEquals(Paisa(20_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC6: Charge aggregation includes both buy and sell orders in range
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `TC6 - charge aggregation sums all order charges within dateRange`() {
        // Both buy and sell fall within reportRange (jan10..jan20)
        val orders =
            listOf(
                buy(1L, "RELIANCE", 5, 100L, jan10), // in range
                sell(2L, "RELIANCE", 5, 120L, jan15), // in range
            )
        val charges =
            mapOf(
                1L to
                    ChargeBreakdown(
                        stt = Paisa(500L),
                        exchangeTxn = Paisa(100L),
                        sebiCharges = Paisa(50L),
                        stampDuty = Paisa(75L),
                        gst = Paisa(25L),
                    ),
                // total = 750
                2L to
                    ChargeBreakdown(
                        stt = Paisa(600L),
                        exchangeTxn = Paisa(120L),
                        sebiCharges = Paisa(60L),
                        stampDuty = Paisa.ZERO,
                        gst = Paisa(30L),
                    ),
                // total = 810
            )

        val result = PnlCalculator.calculate(orders, charges, reportRange)

        assertEquals(Paisa(750L + 810L), result.totalCharges)
        // Verify breakdown aggregation
        assertEquals(Paisa(500L + 600L), result.chargeBreakdown.stt)
        assertEquals(Paisa(100L + 120L), result.chargeBreakdown.exchangeTxn)
        assertEquals(Paisa(75L + 0L), result.chargeBreakdown.stampDuty)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // T-029 — Comprehensive fixture suite
    // ══════════════════════════════════════════════════════════════════════════

    // ──────────────────────────────────────────────────────────────────────────
    // FT-01: Empty order list → all results zero
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT01 - empty order list - all results are zero`() {
        val result = PnlCalculator.calculate(emptyList(), emptyMap(), reportRange)

        assertEquals(Paisa.ZERO, result.realizedPnl)
        assertEquals(Paisa.ZERO, result.totalSellValue)
        assertEquals(Paisa.ZERO, result.totalBuyCostOfSoldLots)
        assertEquals(Paisa.ZERO, result.totalCharges)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-02: Only BUYs in range, no sells → realizedPnl = 0
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT02 - only buys in range no sells - realizedPnl is zero`() {
        val orders =
            listOf(
                buy(1L, "INFY", 5, 200L, jan12), // in range, no corresponding sell
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa.ZERO, result.totalSellValue)
        assertEquals(Paisa.ZERO, result.totalBuyCostOfSoldLots)
        assertEquals(Paisa.ZERO, result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-03: Partial sell in range → proportional cost basis deducted
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT03 - partial sell in range - proportional FIFO cost basis deducted`() {
        // Buy 10 @ ₹100 → totalValue = 100,000p
        // Sell 4 @ ₹150 → sellValue = 60,000p
        // Cost basis for 4 = (100,000 × 4) / 10 = 40,000p
        // P&L = 60,000 − 40,000 = 20,000p
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1),
                sell(2L, "INFY", 4, 150L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(60_000L), result.totalSellValue)
        assertEquals(Paisa(40_000L), result.totalBuyCostOfSoldLots) // (100,000 × 4) / 10
        assertEquals(Paisa(20_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-04: Multiple stocks in range → correct cross-stock aggregation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT04 - multiple stocks in range - P&L aggregated across all stocks`() {
        // INFY: buy 10@₹100, sell 10@₹120 → P&L = 20,000p
        // TCS:  buy 5@₹200,  sell 5@₹300  → P&L = 50,000p
        // Total P&L = 70,000p
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1),
                sell(2L, "INFY", 10, 120L, jan15),
                buy(3L, "TCS", 5, 200L, jan1),
                sell(4L, "TCS", 5, 300L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(270_000L), result.totalSellValue) // 120,000 + 150,000
        assertEquals(Paisa(200_000L), result.totalBuyCostOfSoldLots) // 100,000 + 100,000
        assertEquals(Paisa(70_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-05: Full exit outside range + re-buy + in-range sell uses re-buy cost
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT05 - full exit outside range then rebuy - in-range sell uses rebuy cost basis only`() {
        // Buy 10@₹100 (jan1), sell 10 (jan5, outside) → full exit; lots cleared
        // Buy 5@₹200 (jan8, outside range)
        // Sell 5@₹250 (jan15, in range) → cost basis = 5 × ₹200 = 100,000p
        val orders =
            listOf(
                buy(1L, "WIPRO", 10, 100L, jan1),
                sell(2L, "WIPRO", 10, 110L, jan5), // outside range; depletes original lot
                buy(3L, "WIPRO", 5, 200L, jan8),
                sell(4L, "WIPRO", 5, 250L, jan15), // in range
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(125_000L), result.totalSellValue) // 5 × 25,000p
        assertEquals(Paisa(100_000L), result.totalBuyCostOfSoldLots) // 5 × 20,000p (re-buy lot only)
        assertEquals(Paisa(25_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-06: Date boundary — sell on first day of range (inclusive)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT06 - sell on first day of range inclusive - included in P&L`() {
        // jan10 is the first day of reportRange (jan10..jan20)
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1),
                sell(2L, "INFY", 10, 120L, jan10), // exactly on range start
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(120_000L), result.totalSellValue)
        assertEquals(Paisa(100_000L), result.totalBuyCostOfSoldLots)
        assertEquals(Paisa(20_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-07: Date boundary — sell on last day of range (inclusive)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT07 - sell on last day of range inclusive - included in P&L`() {
        // jan20 is the last day of reportRange (jan10..jan20)
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1),
                sell(2L, "INFY", 10, 120L, jan20), // exactly on range end
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(120_000L), result.totalSellValue)
        assertEquals(Paisa(100_000L), result.totalBuyCostOfSoldLots)
        assertEquals(Paisa(20_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-08: Date boundary — sell one day before range start → excluded
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT08 - sell one day before range start - excluded from P&L`() {
        // jan9 is one day before the jan10 range start
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1),
                sell(2L, "INFY", 10, 120L, jan9), // just before range
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa.ZERO, result.totalSellValue)
        assertEquals(Paisa.ZERO, result.totalBuyCostOfSoldLots)
        assertEquals(Paisa.ZERO, result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-09: Date boundary — sell one day after range end → excluded
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT09 - sell one day after range end - excluded from P&L`() {
        // jan21 is one day after the jan20 range end
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1),
                sell(2L, "INFY", 10, 120L, jan21), // just after range
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa.ZERO, result.totalSellValue)
        assertEquals(Paisa.ZERO, result.totalBuyCostOfSoldLots)
        assertEquals(Paisa.ZERO, result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-10: FIFO sell spans two lots — oldest consumed first
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT10 - sell spans two FIFO lots - oldest lot consumed first`() {
        // Buy 5@₹100 (jan1): tv = 50,000p
        // Buy 5@₹200 (jan3): tv = 100,000p
        // Sell 8@₹300 (jan15): tv = 240,000p
        // FIFO: all 5 from lot1 (50,000p) + 3 from lot2 (100,000p × 3/5 = 60,000p)
        // matchedCostBasis = 110,000p; P&L = 240,000 − 110,000 = 130,000p
        val orders =
            listOf(
                buy(1L, "INFY", 5, 100L, jan1),
                buy(2L, "INFY", 5, 200L, jan3),
                sell(3L, "INFY", 8, 300L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(240_000L), result.totalSellValue)
        assertEquals(Paisa(110_000L), result.totalBuyCostOfSoldLots) // 50,000 + 60,000
        assertEquals(Paisa(130_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-11: Two in-range sells from same lot pool — FIFO applied sequentially
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT11 - two in-range sells from same lot pool - FIFO applied sequentially`() {
        // Buy 20@₹100 (jan1): tv = 200,000p
        // Sell 5@₹120 (jan10): sellValue = 60,000p; costBasis = (200,000×5)/20 = 50,000p; remaining tv = 150,000p
        // Sell 5@₹130 (jan15): sellValue = 65,000p; costBasis = (150,000×5)/15 = 50,000p
        // totalSellValue = 125,000p; totalCost = 100,000p; P&L = 25,000p
        val orders =
            listOf(
                buy(1L, "INFY", 20, 100L, jan1),
                sell(2L, "INFY", 5, 120L, jan10),
                sell(3L, "INFY", 5, 130L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(125_000L), result.totalSellValue) // 60,000 + 65,000
        assertEquals(Paisa(100_000L), result.totalBuyCostOfSoldLots) // 50,000 + 50,000
        assertEquals(Paisa(25_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-12: Pre-range partial sell depletes lot; in-range sell uses remainder
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT12 - pre-range partial sell depletes lot pool before in-range sell`() {
        // Buy 15@₹100 (jan1): tv = 150,000p
        // Sell 5 (jan5, outside): costBasis = (150,000×5)/15 = 50,000p; remaining tv = 100,000p
        // Sell 5@₹120 (jan15, in range): sellValue = 60,000p; costBasis = (100,000×5)/10 = 50,000p
        val orders =
            listOf(
                buy(1L, "RELIANCE", 15, 100L, jan1),
                sell(2L, "RELIANCE", 5, 110L, jan5), // outside range; depletes partial lot
                sell(3L, "RELIANCE", 5, 120L, jan15), // in range
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(60_000L), result.totalSellValue)
        assertEquals(Paisa(50_000L), result.totalBuyCostOfSoldLots)
        assertEquals(Paisa(10_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-13: stockCodeFilter with no matching orders → all results zero
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT13 - stockCodeFilter with no matching orders - all results zero`() {
        val orders =
            listOf(
                buy(1L, "TCS", 10, 200L, jan1),
                sell(2L, "TCS", 10, 250L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange, stockCodeFilter = "INFY")

        assertEquals(Paisa.ZERO, result.totalSellValue)
        assertEquals(Paisa.ZERO, result.totalBuyCostOfSoldLots)
        assertEquals(Paisa.ZERO, result.totalCharges)
        assertEquals(Paisa.ZERO, result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-14: Loss scenario with sell charges → realizedPnl more negative
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT14 - loss with sell charge - realizedPnl more negative than gross loss`() {
        // Buy 10@₹300 (jan1): tv = 300,000p; sell 10@₹250 (jan15, in range): tv = 250,000p
        // Gross P&L = −50,000p; sell charge = 5,000p; net P&L = −55,000p
        val orders =
            listOf(
                buy(1L, "WIPRO", 10, 300L, jan1),
                sell(2L, "WIPRO", 10, 250L, jan15),
            )
        val charges = mapOf(2L to flatCharge(5_000L))

        val result = PnlCalculator.calculate(orders, charges, reportRange)

        assertTrue(result.realizedPnl.isNegative())
        assertEquals(Paisa(250_000L), result.totalSellValue)
        assertEquals(Paisa(300_000L), result.totalBuyCostOfSoldLots)
        assertEquals(Paisa(5_000L), result.totalCharges)
        assertEquals(Paisa(-55_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-15: Large quantity trade — P&L computed correctly at scale
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT15 - large quantity trade - P&L computed correctly at scale`() {
        // Buy 1000@₹200 = ₹2,00,000; sell 1000@₹210 = ₹2,10,000
        // P&L = ₹10,000 = 1,000,000p
        val orders =
            listOf(
                buy(1L, "INFY", 1000, 200L, jan1),
                sell(2L, "INFY", 1000, 210L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(21_000_000L), result.totalSellValue)
        assertEquals(Paisa(20_000_000L), result.totalBuyCostOfSoldLots)
        assertEquals(Paisa(1_000_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-16: stockCodeFilter isolates P&L and charges for the filtered stock
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT16 - stockCodeFilter isolates both P&L and charges for filtered stock only`() {
        // INFY and TCS both have in-range buy + sell + charges.
        // Filter to INFY → only INFY orders and their charges contribute.
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan10), // in range
                sell(2L, "INFY", 10, 120L, jan15), // in range
                buy(3L, "TCS", 5, 200L, jan10), // in range (excluded by filter)
                sell(4L, "TCS", 5, 300L, jan15), // in range (excluded by filter)
            )
        val charges =
            mapOf(
                1L to flatCharge(500L),
                2L to flatCharge(600L),
                3L to flatCharge(700L), // TCS — must not appear in result
                4L to flatCharge(800L), // TCS — must not appear in result
            )

        val result = PnlCalculator.calculate(orders, charges, reportRange, stockCodeFilter = "INFY")

        assertEquals(Paisa(120_000L), result.totalSellValue)
        assertEquals(Paisa(100_000L), result.totalBuyCostOfSoldLots)
        assertEquals(Paisa(1_100L), result.totalCharges) // 500 + 600 only
        assertEquals(Paisa(18_900L), result.realizedPnl) // 120,000 − 100,000 − 1,100
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-17: Three buy lots; sell consumes first fully and second partially
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT17 - sell spans three lots consuming first fully and second partially`() {
        // Buy 10@₹100 (jan1): tv = 100,000p
        // Buy 10@₹200 (jan3): tv = 200,000p
        // Buy 10@₹300 (jan5): tv = 300,000p
        // Sell 15@₹250 (jan15): tv = 375,000p
        // FIFO: all 10 from lot1 (100,000p) + 5 from lot2 (200,000p × 5/10 = 100,000p)
        // matchedCostBasis = 200,000p; P&L = 375,000 − 200,000 = 175,000p
        val orders =
            listOf(
                buy(1L, "HDFC", 10, 100L, jan1),
                buy(2L, "HDFC", 10, 200L, jan3),
                buy(3L, "HDFC", 10, 300L, jan5),
                sell(4L, "HDFC", 15, 250L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(375_000L), result.totalSellValue)
        assertEquals(Paisa(200_000L), result.totalBuyCostOfSoldLots) // 100,000 + 100,000
        assertEquals(Paisa(175_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-18: Break-even trade — sell at exactly buy price, no charges
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT18 - sell at exactly buy price no charges - realizedPnl is zero`() {
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan1),
                sell(2L, "INFY", 10, 100L, jan15), // same price as buy
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(100_000L), result.totalSellValue)
        assertEquals(Paisa(100_000L), result.totalBuyCostOfSoldLots)
        assertEquals(Paisa.ZERO, result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-19: Re-buy at higher price after full exit → in-range sell uses new cost
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT19 - rebuy at higher price after full exit - in-range sell uses new cost basis`() {
        // Buy 10@₹100 (jan1), sell 10 (jan3, outside) → full exit
        // Buy 10@₹300 (jan8, outside range)
        // Sell 10@₹400 (jan15, in range): cost basis = 10 × ₹300 = 300,000p
        val orders =
            listOf(
                buy(1L, "HDFC", 10, 100L, jan1),
                sell(2L, "HDFC", 10, 150L, jan3), // outside range; full exit
                buy(3L, "HDFC", 10, 300L, jan8),
                sell(4L, "HDFC", 10, 400L, jan15), // in range
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(400_000L), result.totalSellValue) // 10 × 40,000p
        assertEquals(Paisa(300_000L), result.totalBuyCostOfSoldLots) // 10 × 30,000p (re-buy cost)
        assertEquals(Paisa(100_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-20: chargeBreakdown aggregates each component correctly
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT20 - chargeBreakdown aggregates each component across in-range orders`() {
        val orders =
            listOf(
                buy(1L, "INFY", 10, 100L, jan10), // in range
                sell(2L, "INFY", 10, 120L, jan15), // in range
            )
        val charges =
            mapOf(
                1L to
                    ChargeBreakdown(
                        stt = Paisa(100L),
                        exchangeTxn = Paisa(50L),
                        sebiCharges = Paisa(25L),
                        stampDuty = Paisa(75L),
                        gst = Paisa(10L),
                    ),
                2L to
                    ChargeBreakdown(
                        stt = Paisa(120L),
                        exchangeTxn = Paisa(60L),
                        sebiCharges = Paisa(30L),
                        stampDuty = Paisa.ZERO,
                        gst = Paisa(12L),
                    ),
            )

        val result = PnlCalculator.calculate(orders, charges, reportRange)

        assertEquals(Paisa(220L), result.chargeBreakdown.stt) // 100 + 120
        assertEquals(Paisa(110L), result.chargeBreakdown.exchangeTxn) // 50 + 60
        assertEquals(Paisa(55L), result.chargeBreakdown.sebiCharges) // 25 + 30
        assertEquals(Paisa(75L), result.chargeBreakdown.stampDuty) // 75 + 0
        assertEquals(Paisa(22L), result.chargeBreakdown.gst) // 10 + 12
        assertEquals(Paisa(482L), result.totalCharges) // 220+110+55+75+22
        assertEquals(Paisa(120_000L - 100_000L - 482L), result.realizedPnl) // 19,518
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-21: Three sequential in-range sells — cumulative P&L correct
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT21 - three in-range sells from same lot - cumulative P&L correct`() {
        // Buy 30@₹100 (jan1): tv = 300,000p
        // Sell 10@₹120 (jan10): cost = (300,000×10)/30 = 100,000p; rem tv = 200,000p
        // Sell 10@₹130 (jan15): cost = (200,000×10)/20 = 100,000p; rem tv = 100,000p
        // Sell 10@₹140 (jan20): cost = (100,000×10)/10 = 100,000p; rem = empty
        // totalSellValue = 120,000+130,000+140,000 = 390,000p; totalCost = 300,000p; P&L = 90,000p
        val orders =
            listOf(
                buy(1L, "INFY", 30, 100L, jan1),
                sell(2L, "INFY", 10, 120L, jan10),
                sell(3L, "INFY", 10, 130L, jan15),
                sell(4L, "INFY", 10, 140L, jan20),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(390_000L), result.totalSellValue) // 120,000+130,000+140,000
        assertEquals(Paisa(300_000L), result.totalBuyCostOfSoldLots) // 100,000 × 3
        assertEquals(Paisa(90_000L), result.realizedPnl)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FT-22: Single share buy and sell — minimal trade P&L
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FT22 - single share trade in range - P&L computed correctly`() {
        // Buy 1@₹500 = 50,000p; Sell 1@₹600 = 60,000p; P&L = 10,000p
        val orders =
            listOf(
                buy(1L, "INFY", 1, 500L, jan1),
                sell(2L, "INFY", 1, 600L, jan15),
            )

        val result = PnlCalculator.calculate(orders, emptyMap(), reportRange)

        assertEquals(Paisa(60_000L), result.totalSellValue) // 1 × 60,000p
        assertEquals(Paisa(50_000L), result.totalBuyCostOfSoldLots) // 1 × 50,000p
        assertEquals(Paisa(10_000L), result.realizedPnl)
    }
}
