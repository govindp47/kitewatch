package com.kitewatch.domain.engine

import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget

/**
 * Computes the GTT trigger price per share that, when filled via a full sell,
 * delivers net profit equal to or exceeding the configured [ProfitTarget].
 *
 * Sell-side charges are accounted for using a **closed-form approximation**:
 * NSE exchange rates are used as the default (most common exchange for equity delivery).
 * This avoids the circular dependency that would arise from an iterative approach
 * (sell charges depend on sell value; sell value depends on sell charges).
 *
 * For delivery orders, brokerage = ₹0, so GST base = exchange txn + SEBI only.
 * SEBI (~₹10/crore) is approximated as a uniform milli-bps rate; see implementation comments.
 */
object TargetPriceCalculator {
    /**
     * Compute the target sell price per share.
     *
     * @param avgBuyPrice      Average buy price per share in Paisa (informational)
     * @param quantity         Number of shares held — must be > 0
     * @param profitTarget     Target profit configuration (percentage or absolute)
     * @param investedAmount   Total cost basis (buy value of remaining shares) in Paisa
     * @param buyCharges       Cumulative buy-side charges in Paisa
     * @param chargeRates      Current charge rate snapshot
     * @return Target sell price per share in Paisa, ceiling-rounded to the nearest paisa
     *         so that net profit at the target price meets or exceeds [profitTarget]
     */
    @Suppress("LongParameterList") // All 6 params are required by the domain contract; no natural grouping.
    fun compute(
        @Suppress("UNUSED_PARAMETER") avgBuyPrice: Paisa,
        quantity: Int,
        profitTarget: ProfitTarget,
        investedAmount: Paisa,
        buyCharges: Paisa,
        chargeRates: ChargeRateSnapshot,
    ): Paisa {
        require(quantity > 0) { "Quantity must be positive, was $quantity" }

        val targetNetProfit = profitTarget.computeTargetProfit(investedAmount)

        // Gross sell value that must be recovered before sell charges are deducted.
        val breakEvenSellValue = investedAmount + buyCharges + targetNetProfit

        // Combined sell-side charge rate in milli-bps (NSE, delivery, no brokerage):
        //   STT sell           : sttSellMilliBps       (e.g., 10_000 = 0.10%)
        //   Exchange txn (NSE) : exchangeNseMilliBps   (e.g.,    297 = 0.00297%)
        //   SEBI               : sebiChargePerCrorePaisa × 10_000_000 / 1_000_000_000
        //                        (₹10/crore → 10 milli-bps; uniform-rate approximation)
        //   GST on (exchange + SEBI): mirrors ChargeCalculator GST base
        //                        18% of (297 + 10) ≈ 55 milli-bps
        //   Stamp duty         : ZERO on sell side
        val exchangeMilliBps = chargeRates.exchangeNseMilliBps
        val sebiMilliBps =
            (chargeRates.sebiChargePerCrorePaisa.value * 10_000_000L / 1_000_000_000L).toInt()
        val gstBaseMilliBps = exchangeMilliBps + sebiMilliBps
        val gstOnBaseMilliBps =
            (chargeRates.gstMilliBps.toLong() * gstBaseMilliBps / 10_000_000L).toInt()
        val sellRateMilliBps =
            chargeRates.sttSellMilliBps + exchangeMilliBps + sebiMilliBps + gstOnBaseMilliBps

        // Closed-form derivation:
        //   netProceeds = requiredSellValue × (1 − sellRate) ≥ breakEvenSellValue
        //   ⟹ requiredSellValue = breakEvenSellValue / (1 − sellRate)
        //
        // In milli-bps integer arithmetic (sellRate = sellRateMilliBps / 10_000_000):
        //   requiredSellValue = ⌈breakEvenSellValue × 10_000_000 / (10_000_000 − sellRateMilliBps)⌉
        val scaledNumerator = breakEvenSellValue.value * 10_000_000L
        val effectiveDivisor = 10_000_000L - sellRateMilliBps
        val requiredSellValuePaisa =
            (scaledNumerator + effectiveDivisor - 1) / effectiveDivisor // ceiling division

        // Price per share, ceiling-rounded so totalSellValue ≥ requiredSellValue.
        return Paisa((requiredSellValuePaisa + quantity - 1) / quantity)
    }
}
