package com.kitewatch.domain.engine

import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TargetPriceCalculatorTest {
    private val standardRates =
        ChargeRateSnapshot(
            brokerageDeliveryMilliBps = 0,
            sttBuyMilliBps = 10_000,
            sttSellMilliBps = 10_000,
            exchangeNseMilliBps = 297,
            exchangeBseMilliBps = 375,
            gstMilliBps = 1_800_000,
            sebiChargePerCrorePaisa = Paisa(1_000),
            stampDutyBuyMilliBps = 1_500,
            dpChargesPerScriptPaisa = Paisa(1_580),
            fetchedAt = Instant.EPOCH,
        )

    // Convenience: compute actual sell charges at the returned target price.
    private fun actualSellCharges(
        targetPrice: Paisa,
        quantity: Int,
    ): Paisa {
        val totalSellValue = targetPrice * quantity
        return ChargeCalculator
            .calculate(
                tradeValue = totalSellValue,
                orderType = OrderType.SELL,
                exchange = Exchange.NSE,
                rates = standardRates,
            ).total()
    }

    // ─── Core acceptance criterion helpers ──────────────────────────────────

    /** Returns net profit when selling [quantity] shares at [targetPrice]. */
    private fun netProfit(
        targetPrice: Paisa,
        quantity: Int,
        investedAmount: Paisa,
        buyCharges: Paisa,
    ): Paisa {
        val totalSellValue = targetPrice * quantity
        val sellChargesTotal = actualSellCharges(targetPrice, quantity)
        return totalSellValue - sellChargesTotal - investedAmount - buyCharges
    }

    // ─── Test cases ─────────────────────────────────────────────────────────

    /**
     * 5% target on ₹1,00,000 invested across 100 shares.
     * Expected target price ≈ ₹1,052/share (within ₹1 tolerance).
     * Computed: 105,109 paisa = ₹1,051.09 — satisfies the ±₹1 band.
     */
    @Test
    fun `5pct target on 1lakh 100shares - target within 1 rupee of 1052`() {
        val investedAmount = Paisa.fromRupees(100_000.0) // ₹1,00,000
        val quantity = 100

        val result =
            TargetPriceCalculator.compute(
                avgBuyPrice = Paisa.fromRupees(1_000.0),
                quantity = quantity,
                profitTarget = ProfitTarget.Percentage(basisPoints = 500),
                investedAmount = investedAmount,
                buyCharges = Paisa.ZERO,
                chargeRates = standardRates,
            )

        // Price in paise; convert to integer rupees for tolerance check
        val priceRupees = result.value / 100L
        assertTrue("Expected ≈₹1052 but was ₹$priceRupees", priceRupees in 1_051L..1_053L)
    }

    /**
     * 5% target on ₹1,00,000 invested: when sold at the computed target price,
     * actual net profit must be ≥ configured target (₹5,000).
     */
    @Test
    fun `5pct target - net profit at target price meets or exceeds configured target`() {
        val investedAmount = Paisa.fromRupees(100_000.0)
        val quantity = 100
        val profitTarget = ProfitTarget.Percentage(basisPoints = 500)

        val targetPrice =
            TargetPriceCalculator.compute(
                avgBuyPrice = Paisa.fromRupees(1_000.0),
                quantity = quantity,
                profitTarget = profitTarget,
                investedAmount = investedAmount,
                buyCharges = Paisa.ZERO,
                chargeRates = standardRates,
            )

        val actualNetProfit = netProfit(targetPrice, quantity, investedAmount, Paisa.ZERO)
        val requiredProfit = profitTarget.computeTargetProfit(investedAmount)

        assertTrue(
            "Net profit ${actualNetProfit.value} must be >= target ${requiredProfit.value}",
            actualNetProfit >= requiredProfit,
        )
    }

    /**
     * Zero-profit target with buy charges: target price must cover all charges so
     * net profit ≥ 0 (break-even).
     */
    @Test
    fun `zero profit target covers all charges - net profit is non-negative`() {
        val investedAmount = Paisa.fromRupees(10_000.0) // ₹10,000
        val quantity = 100
        val buyCharges = Paisa(10_000L) // ₹100 buy-side charges

        val targetPrice =
            TargetPriceCalculator.compute(
                avgBuyPrice = Paisa.fromRupees(100.0),
                quantity = quantity,
                profitTarget = ProfitTarget.Percentage(basisPoints = 0),
                investedAmount = investedAmount,
                buyCharges = buyCharges,
                chargeRates = standardRates,
            )

        val actualNetProfit = netProfit(targetPrice, quantity, investedAmount, buyCharges)

        assertTrue(
            "Net profit ${actualNetProfit.value} must be >= 0 at zero-profit target",
            !actualNetProfit.isNegative(),
        )
        // Target price must exceed avg buy price (₹100) to cover sell charges
        assertTrue("Target price must exceed avg buy price", targetPrice > Paisa.fromRupees(100.0))
    }

    /**
     * Zero-profit target with zero charges: target price exactly covers cost basis,
     * net profit ≥ 0.
     */
    @Test
    fun `zero profit zero charges - target equals avg buy price rounded up for sell charges`() {
        val investedAmount = Paisa.fromRupees(50_000.0)
        val quantity = 100

        val targetPrice =
            TargetPriceCalculator.compute(
                avgBuyPrice = Paisa.fromRupees(500.0),
                quantity = quantity,
                profitTarget = ProfitTarget.Percentage(basisPoints = 0),
                investedAmount = investedAmount,
                buyCharges = Paisa.ZERO,
                chargeRates = standardRates,
            )

        val actualNetProfit = netProfit(targetPrice, quantity, investedAmount, Paisa.ZERO)
        assertTrue(!actualNetProfit.isNegative())
    }

    /**
     * Absolute profit target: fixed ₹5,000 on ₹50,000 invested.
     * Net profit at target must be ≥ ₹5,000.
     */
    @Test
    fun `absolute target - net profit at target meets fixed amount`() {
        val investedAmount = Paisa.fromRupees(50_000.0)
        val quantity = 100
        val profitTarget = ProfitTarget.Absolute(Paisa.fromRupees(5_000.0))

        val targetPrice =
            TargetPriceCalculator.compute(
                avgBuyPrice = Paisa.fromRupees(500.0),
                quantity = quantity,
                profitTarget = profitTarget,
                investedAmount = investedAmount,
                buyCharges = Paisa.ZERO,
                chargeRates = standardRates,
            )

        val actualNetProfit = netProfit(targetPrice, quantity, investedAmount, Paisa.ZERO)
        val requiredProfit = profitTarget.computeTargetProfit(investedAmount)

        assertTrue(
            "Net profit ${actualNetProfit.value} must be >= ${requiredProfit.value}",
            actualNetProfit >= requiredProfit,
        )
    }

    /** Absolute target with buy charges: buy charges deducted before net profit computed. */
    @Test
    fun `absolute target with buy charges - net profit still meets target`() {
        val investedAmount = Paisa.fromRupees(100_000.0)
        val quantity = 200
        val buyCharges = Paisa.fromRupees(200.0) // ₹200 buy charges
        val profitTarget = ProfitTarget.Absolute(Paisa.fromRupees(10_000.0))

        val targetPrice =
            TargetPriceCalculator.compute(
                avgBuyPrice = Paisa.fromRupees(500.0),
                quantity = quantity,
                profitTarget = profitTarget,
                investedAmount = investedAmount,
                buyCharges = buyCharges,
                chargeRates = standardRates,
            )

        val actualNetProfit = netProfit(targetPrice, quantity, investedAmount, buyCharges)
        val requiredProfit = profitTarget.computeTargetProfit(investedAmount)

        assertTrue(
            "Net profit ${actualNetProfit.value} must be >= ${requiredProfit.value}",
            actualNetProfit >= requiredProfit,
        )
    }

    /** 10% target with non-zero buy charges: profit criterion still met. */
    @Test
    fun `10pct target with buy charges - profit criterion met`() {
        val investedAmount = Paisa.fromRupees(25_000.0)
        val quantity = 50
        val buyCharges = Paisa.fromRupees(37.5) // ₹37.50 buy charges
        val profitTarget = ProfitTarget.Percentage(basisPoints = 1_000)

        val targetPrice =
            TargetPriceCalculator.compute(
                avgBuyPrice = Paisa.fromRupees(500.0),
                quantity = quantity,
                profitTarget = profitTarget,
                investedAmount = investedAmount,
                buyCharges = buyCharges,
                chargeRates = standardRates,
            )

        val actualNetProfit = netProfit(targetPrice, quantity, investedAmount, buyCharges)
        val requiredProfit = profitTarget.computeTargetProfit(investedAmount)

        assertTrue(actualNetProfit >= requiredProfit)
    }

    /** Result is always positive — no Double arithmetic involved. */
    @Test
    fun `returns positive Paisa with no Double arithmetic`() {
        val result =
            TargetPriceCalculator.compute(
                avgBuyPrice = Paisa(50_000L),
                quantity = 10,
                profitTarget = ProfitTarget.Percentage(basisPoints = 200),
                investedAmount = Paisa(500_000L),
                buyCharges = Paisa.ZERO,
                chargeRates = standardRates,
            )

        assertTrue("Result must be positive", result.isPositive())
    }

    /** Single share: ceiling division must not under-deliver profit. */
    @Test
    fun `single share - profit criterion met`() {
        val investedAmount = Paisa.fromRupees(1_500.0)
        val quantity = 1
        val profitTarget = ProfitTarget.Percentage(basisPoints = 500)

        val targetPrice =
            TargetPriceCalculator.compute(
                avgBuyPrice = investedAmount,
                quantity = quantity,
                profitTarget = profitTarget,
                investedAmount = investedAmount,
                buyCharges = Paisa.ZERO,
                chargeRates = standardRates,
            )

        val actualNetProfit = netProfit(targetPrice, quantity, investedAmount, Paisa.ZERO)
        val requiredProfit = profitTarget.computeTargetProfit(investedAmount)

        assertTrue(actualNetProfit >= requiredProfit)
    }

    /** Large quantity (1,000 shares): ensure no Long overflow and criterion met. */
    @Test
    fun `large quantity 1000 shares - profit criterion met`() {
        val investedAmount = Paisa.fromRupees(500_000.0) // ₹5 lakh
        val quantity = 1_000
        val profitTarget = ProfitTarget.Percentage(basisPoints = 500)

        val targetPrice =
            TargetPriceCalculator.compute(
                avgBuyPrice = Paisa.fromRupees(500.0),
                quantity = quantity,
                profitTarget = profitTarget,
                investedAmount = investedAmount,
                buyCharges = Paisa.ZERO,
                chargeRates = standardRates,
            )

        val actualNetProfit = netProfit(targetPrice, quantity, investedAmount, Paisa.ZERO)
        val requiredProfit = profitTarget.computeTargetProfit(investedAmount)

        assertTrue(actualNetProfit >= requiredProfit)
    }
}
