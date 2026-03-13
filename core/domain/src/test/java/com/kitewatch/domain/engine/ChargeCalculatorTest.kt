package com.kitewatch.domain.engine

import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChargeCalculatorTest {
    /** Standard Zerodha equity delivery rates (2024). */
    private val standardRates =
        ChargeRateSnapshot(
            brokerageDeliveryMilliBps = 0,
            sttBuyMilliBps = 10_000, // 0.1%
            sttSellMilliBps = 10_000, // 0.1%
            exchangeNseMilliBps = 297, // 0.00297%
            exchangeBseMilliBps = 375, // 0.00375%
            gstMilliBps = 1_800_000, // 18%
            sebiChargePerCrorePaisa = Paisa(1_000L), // ₹10/crore
            stampDutyBuyMilliBps = 1_500, // 0.015%
            dpChargesPerScriptPaisa = Paisa(1_580L), // ₹15.80
            fetchedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    // -------------------------------------------------------------------------
    // Smoke test: ₹1,00,000 NSE BUY
    // -------------------------------------------------------------------------

    @Test
    fun `smoke test - 1 lakh NSE BUY delivery`() {
        val tradeValue = Paisa.fromRupees(100_000.0) // Paisa(10_000_000)
        val result =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )

        // STT: 0.1% of ₹1,00,000 = ₹100 = 10,000 paisa
        assertEquals(Paisa(10_000L), result.stt)

        // Exchange NSE: 0.00297% of ₹1,00,000 = ₹2.97 = 297 paisa
        assertEquals(Paisa(297L), result.exchangeTxn)

        // SEBI: ₹10/crore × ₹1L = ₹0.10 = 10 paisa
        assertEquals(Paisa(10L), result.sebiCharges)

        // Stamp duty: 0.015% of ₹1,00,000 = ₹15 = 1,500 paisa (under cap)
        assertEquals(Paisa(1_500L), result.stampDuty)

        // GST: 18% of (₹2.97 + ₹0.10) = 18% of ₹3.07 = ₹0.5526 → 55 paisa (rounded)
        assertEquals(Paisa(55L), result.gst)

        // Total: 10,000 + 297 + 10 + 1,500 + 55 = 11,862 paisa = ₹118.62
        assertEquals(Paisa(11_862L), result.total())
    }

    // -------------------------------------------------------------------------
    // SELL order: stamp duty must be ZERO
    // -------------------------------------------------------------------------

    @Test
    fun `SELL order has zero stamp duty`() {
        val tradeValue = Paisa.fromRupees(100_000.0)
        val result =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.SELL,
                exchange = Exchange.NSE,
                rates = standardRates,
            )

        assertEquals(Paisa.ZERO, result.stampDuty)
    }

    @Test
    fun `SELL order STT uses sell rate`() {
        val tradeValue = Paisa.fromRupees(100_000.0)
        val result =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.SELL,
                exchange = Exchange.NSE,
                rates = standardRates,
            )

        // STT sell = 0.1% = 10,000 paisa for ₹1,00,000
        assertEquals(Paisa(10_000L), result.stt)
    }

    // -------------------------------------------------------------------------
    // BSE vs NSE exchange rate differentiation
    // -------------------------------------------------------------------------

    @Test
    fun `BSE uses BSE exchange rate`() {
        val tradeValue = Paisa.fromRupees(100_000.0)
        val nseResult =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )
        val bseResult =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.BUY,
                exchange = Exchange.BSE,
                rates = standardRates,
            )

        // NSE: 297 milli-bps → 297 paisa
        assertEquals(Paisa(297L), nseResult.exchangeTxn)
        // BSE: 375 milli-bps → 375 paisa
        assertEquals(Paisa(375L), bseResult.exchangeTxn)
    }

    // -------------------------------------------------------------------------
    // Stamp duty cap at ₹1,500
    // -------------------------------------------------------------------------

    @Test
    fun `stamp duty capped at 1500 rupees for large BUY`() {
        // ₹2 crore trade → uncapped stamp = 0.015% × ₹2Cr = ₹3,000
        val tradeValue = Paisa.fromRupees(20_000_000.0) // ₹2 crore
        val result =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )

        // Should be capped at ₹1,500 = 150,000 paisa
        assertEquals(Paisa(150_000L), result.stampDuty)
    }

    @Test
    fun `stamp duty exactly at cap for 1 crore BUY`() {
        // ₹1 crore → uncapped stamp = 0.015% × ₹1Cr = ₹1,500
        val tradeValue = Paisa.fromRupees(10_000_000.0) // ₹1 crore
        val result =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )

        // Exactly at cap
        assertEquals(Paisa(150_000L), result.stampDuty)
    }

    @Test
    fun `stamp duty below cap for sub-crore BUY`() {
        // ₹50,00,000 → uncapped stamp = 0.015% × ₹50L = ₹750 = 75,000 paisa
        val tradeValue = Paisa.fromRupees(5_000_000.0)
        val result =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )

        assertEquals(Paisa(75_000L), result.stampDuty)
    }

    // -------------------------------------------------------------------------
    // GST base excludes brokerage (which is ₹0 for Zerodha delivery)
    // -------------------------------------------------------------------------

    @Test
    fun `GST computed on exchange plus SEBI only`() {
        val tradeValue = Paisa.fromRupees(100_000.0)
        val result =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )

        // GST base = exchangeTxn + sebiCharges = 297 + 10 = 307 paisa
        // GST = 18% of 307 = 55.26 → 55 paisa (round half-up)
        val expectedGstBase = result.exchangeTxn + result.sebiCharges
        val expectedGst = expectedGstBase.applyMilliBasisPoints(standardRates.gstMilliBps)
        assertEquals(expectedGst, result.gst)
    }

    // -------------------------------------------------------------------------
    // Very small trade
    // -------------------------------------------------------------------------

    @Test
    fun `very small trade - 50 rupees`() {
        val tradeValue = Paisa.fromRupees(50.0) // Paisa(5_000)
        val result =
            ChargeCalculator.calculate(
                tradeValue = tradeValue,
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )

        // STT: 0.1% of ₹50 = ₹0.05 = 5 paisa
        assertEquals(Paisa(5L), result.stt)

        // Exchange NSE: 0.00297% of ₹50 ≈ 0 paisa (5000 * 297 + 5_000_000) / 10_000_000
        // = (1_485_000 + 5_000_000) / 10_000_000 = 6_485_000 / 10_000_000 = 0
        assertEquals(Paisa(0L), result.exchangeTxn)

        // SEBI: effectively 0 for small trades
        assertEquals(Paisa(0L), result.sebiCharges)

        // Stamp duty: 0.015% of ₹50 ≈ 0 paisa
        assertEquals(Paisa(1L), result.stampDuty)
    }

    // -------------------------------------------------------------------------
    // F10: NSE BUY ₹10,000 — all 6 components verified individually
    // tradeValue = 1_000_000 paisa
    // STT   : (1_000_000 × 10_000 + 5_000_000) / 10_000_000 = 1_000
    // NSE   : (1_000_000 × 297    + 5_000_000) / 10_000_000 = 30
    // SEBI  : (1_000_000 × 1_000  + 500_000_000) / 1_000_000_000 = 1
    // Stamp : (1_000_000 × 1_500  + 5_000_000) / 10_000_000 = 150  (well under cap)
    // GST   : (31 × 1_800_000     + 5_000_000) / 10_000_000 = 6
    // -------------------------------------------------------------------------

    @Test
    fun `NSE BUY 10k - all components verified individually`() {
        val result =
            ChargeCalculator.calculate(
                tradeValue = Paisa.fromRupees(10_000.0),
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )
        assertEquals(Paisa(1_000L), result.stt)
        assertEquals(Paisa(30L), result.exchangeTxn)
        assertEquals(Paisa(1L), result.sebiCharges)
        assertEquals(Paisa(150L), result.stampDuty)
        assertEquals(Paisa(6L), result.gst)
        assertEquals(Paisa(1_187L), result.total())
    }

    // -------------------------------------------------------------------------
    // F11: NSE SELL ₹10,000 — stamp duty zero, all other components verified
    // -------------------------------------------------------------------------

    @Test
    fun `NSE SELL 10k - stamp duty zero and all components verified`() {
        val result =
            ChargeCalculator.calculate(
                tradeValue = Paisa.fromRupees(10_000.0),
                orderType = OrderType.SELL,
                exchange = Exchange.NSE,
                rates = standardRates,
            )
        assertEquals(Paisa(1_000L), result.stt)
        assertEquals(Paisa(30L), result.exchangeTxn)
        assertEquals(Paisa(1L), result.sebiCharges)
        assertEquals(Paisa.ZERO, result.stampDuty)
        assertEquals(Paisa(6L), result.gst)
        assertEquals(Paisa(1_037L), result.total())
    }

    // -------------------------------------------------------------------------
    // F12: BSE BUY ₹10,000 — full breakdown showing BSE rate is higher than NSE
    // BSE exchange: (1_000_000 × 375 + 5_000_000) / 10_000_000 = 38
    // GST base = 38 + 1 = 39; GST: (39 × 1_800_000 + 5_000_000) / 10_000_000 = 7
    // -------------------------------------------------------------------------

    @Test
    fun `BSE BUY 10k - full breakdown with higher exchange rate than NSE`() {
        val result =
            ChargeCalculator.calculate(
                tradeValue = Paisa.fromRupees(10_000.0),
                orderType = OrderType.BUY,
                exchange = Exchange.BSE,
                rates = standardRates,
            )
        assertEquals(Paisa(1_000L), result.stt)
        assertEquals(Paisa(38L), result.exchangeTxn) // 375 milli-bps vs NSE 297
        assertEquals(Paisa(1L), result.sebiCharges)
        assertEquals(Paisa(150L), result.stampDuty)
        assertEquals(Paisa(7L), result.gst)
        assertEquals(Paisa(1_196L), result.total())
    }

    // -------------------------------------------------------------------------
    // F13: NSE BUY ₹99,99,900 — stamp duty just below cap (149_999 paisa)
    // tradeValue = 999_990_000 paisa  (₹99,99,900 = 9_999_900 rupees)
    // Stamp uncapped: (999_990_000 × 1_500 + 5_000_000) / 10_000_000 = 149_999
    // 149_999 < 150_000 cap → stamp is NOT capped
    // -------------------------------------------------------------------------

    @Test
    fun `NSE BUY 9999900 rupees - stamp duty just below cap at 149999 paisa`() {
        val result =
            ChargeCalculator.calculate(
                tradeValue = Paisa.fromRupees(9_999_900.0),
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )
        assertEquals(Paisa(149_999L), result.stampDuty)
        assertTrue("stamp duty must be below cap of 150_000", result.stampDuty < Paisa(150_000L))
    }

    // -------------------------------------------------------------------------
    // F14: NSE BUY ₹99,98,900 — stamp duty further below cap (149_984 paisa)
    // tradeValue = 999_890_000 paisa  (₹99,98,900 = 9_998_900 rupees)
    // Stamp uncapped: (999_890_000 × 1_500 + 5_000_000) / 10_000_000 = 149_984
    // -------------------------------------------------------------------------

    @Test
    fun `NSE BUY 9998900 rupees - stamp duty below cap at 149984 paisa`() {
        val result =
            ChargeCalculator.calculate(
                tradeValue = Paisa.fromRupees(9_998_900.0),
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )
        assertEquals(Paisa(149_984L), result.stampDuty)
        assertTrue("stamp duty must be below cap of 150_000", result.stampDuty < Paisa(150_000L))
    }

    // -------------------------------------------------------------------------
    // F15: NSE SELL ₹50 — all components including zero stamp duty on SELL
    // -------------------------------------------------------------------------

    @Test
    fun `NSE SELL 50 rupees - stamp duty zero on sell side`() {
        val result =
            ChargeCalculator.calculate(
                tradeValue = Paisa.fromRupees(50.0),
                orderType = OrderType.SELL,
                exchange = Exchange.NSE,
                rates = standardRates,
            )
        assertEquals(Paisa(5L), result.stt)
        assertEquals(Paisa(0L), result.exchangeTxn)
        assertEquals(Paisa(0L), result.sebiCharges)
        assertEquals(Paisa.ZERO, result.stampDuty)
        assertEquals(Paisa(0L), result.gst)
        assertEquals(Paisa(5L), result.total())
    }

    // -------------------------------------------------------------------------
    // F16: BSE SELL ₹1,00,000 — full breakdown: BSE txn rate, no stamp duty
    // BSE exchange: (10_000_000 × 375 + 5_000_000) / 10_000_000 = 375
    // GST base = 375 + 10 = 385; GST: (385 × 1_800_000 + 5_000_000) / 10_000_000 = 69
    // -------------------------------------------------------------------------

    @Test
    fun `BSE SELL 1 lakh - no stamp duty and BSE exchange rate`() {
        val result =
            ChargeCalculator.calculate(
                tradeValue = Paisa.fromRupees(100_000.0),
                orderType = OrderType.SELL,
                exchange = Exchange.BSE,
                rates = standardRates,
            )
        assertEquals(Paisa(10_000L), result.stt)
        assertEquals(Paisa(375L), result.exchangeTxn)
        assertEquals(Paisa(10L), result.sebiCharges)
        assertEquals(Paisa.ZERO, result.stampDuty)
        assertEquals(Paisa(69L), result.gst)
        assertEquals(Paisa(10_454L), result.total())
    }

    // -------------------------------------------------------------------------
    // F17: NSE BUY ₹5,00,000 — mid-size trade, stamp well under cap
    // tradeValue = 50_000_000 paisa
    // STT   : 50_000   NSE: 1_485   SEBI: 50   Stamp: 7_500   GST: 276
    // -------------------------------------------------------------------------

    @Test
    fun `NSE BUY 5 lakh - mid-size trade all components`() {
        val result =
            ChargeCalculator.calculate(
                tradeValue = Paisa.fromRupees(500_000.0),
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )
        assertEquals(Paisa(50_000L), result.stt)
        assertEquals(Paisa(1_485L), result.exchangeTxn)
        assertEquals(Paisa(50L), result.sebiCharges)
        assertEquals(Paisa(7_500L), result.stampDuty)
        assertEquals(Paisa(276L), result.gst)
        assertEquals(Paisa(59_311L), result.total())
    }

    // -------------------------------------------------------------------------
    // F18: NSE BUY ₹2 crore — full breakdown confirming cap at all trade sizes
    // tradeValue = 2_000_000_000 paisa
    // STT: 2_000_000  NSE: 59_400  SEBI: 2_000  Stamp: 150_000(cap)  GST: 11_052
    // -------------------------------------------------------------------------

    @Test
    fun `NSE BUY 2 crore - full breakdown with stamp duty capped`() {
        val result =
            ChargeCalculator.calculate(
                tradeValue = Paisa.fromRupees(20_000_000.0),
                orderType = OrderType.BUY,
                exchange = Exchange.NSE,
                rates = standardRates,
            )
        assertEquals(Paisa(2_000_000L), result.stt)
        assertEquals(Paisa(59_400L), result.exchangeTxn)
        assertEquals(Paisa(2_000L), result.sebiCharges)
        assertEquals(Paisa(150_000L), result.stampDuty)
        assertEquals(Paisa(11_052L), result.gst)
        assertEquals(Paisa(2_222_452L), result.total())
    }
}
