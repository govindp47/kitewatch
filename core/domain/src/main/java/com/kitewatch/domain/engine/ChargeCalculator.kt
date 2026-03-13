package com.kitewatch.domain.engine

import com.kitewatch.domain.model.ChargeBreakdown
import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa

/**
 * Stateless, pure charge computation for a single equity delivery order.
 *
 * All arithmetic uses [Paisa] (Long) — zero floating-point in any path.
 * Each intermediate result uses round-half-up via [Paisa.applyMilliBasisPoints].
 *
 * Zerodha equity delivery charges (2024):
 * 1. Brokerage: ₹0 (excluded from computation)
 * 2. STT: 0.1% on both buy and sell
 * 3. Exchange transaction charges: NSE 0.00297%, BSE 0.00375%
 * 4. SEBI turnover fees: ₹10 per crore
 * 5. Stamp duty: 0.015% on BUY only, capped at ₹1,500
 * 6. GST: 18% on (exchange charges + SEBI charges)
 */
object ChargeCalculator {
    /** Stamp duty cap: ₹1,500 = 150,000 paisa. */
    private val STAMP_DUTY_CAP = Paisa(150_000L)

    /** 1 crore rupees in paisa: 10,000,000 × 100 = 1,000,000,000. */
    private const val ONE_CRORE_PAISA = 1_000_000_000L

    /**
     * Calculate all charges for a single equity delivery order.
     *
     * @param tradeValue Total trade value (price × quantity) in [Paisa]
     * @param orderType  BUY or SELL
     * @param exchange   NSE or BSE
     * @param rates      Current charge rate snapshot
     * @return [ChargeBreakdown] with individual charge amounts
     */
    fun calculate(
        tradeValue: Paisa,
        orderType: OrderType,
        exchange: Exchange,
        rates: ChargeRateSnapshot,
    ): ChargeBreakdown {
        // 1. STT (Securities Transaction Tax)
        val sttMilliBps =
            when (orderType) {
                OrderType.BUY -> rates.sttBuyMilliBps
                OrderType.SELL -> rates.sttSellMilliBps
            }
        val stt = tradeValue.applyMilliBasisPoints(sttMilliBps)

        // 2. Exchange Transaction Charges
        val exchangeMilliBps =
            when (exchange) {
                Exchange.NSE -> rates.exchangeNseMilliBps
                Exchange.BSE -> rates.exchangeBseMilliBps
            }
        val exchangeTxn = tradeValue.applyMilliBasisPoints(exchangeMilliBps)

        // 3. SEBI Turnover Fees: rate per crore of turnover
        // Formula: (turnover_paisa × rate_paisa_per_crore) / crore_in_paisa
        // Round half-up: add half-divisor before integer division
        val sebiCharges =
            Paisa(
                (tradeValue.value * rates.sebiChargePerCrorePaisa.value + ONE_CRORE_PAISA / 2) / ONE_CRORE_PAISA,
            )

        // 4. Stamp Duty: BUY side only, capped at ₹1,500
        val stampDuty =
            when (orderType) {
                OrderType.BUY -> {
                    val uncapped = tradeValue.applyMilliBasisPoints(rates.stampDutyBuyMilliBps)
                    minOf(uncapped, STAMP_DUTY_CAP)
                }
                OrderType.SELL -> Paisa.ZERO
            }

        // 5. GST: 18% on (exchange charges + SEBI charges)
        // Brokerage = ₹0 for Zerodha equity delivery, so GST base excludes it
        val gstBase = exchangeTxn + sebiCharges
        val gst = gstBase.applyMilliBasisPoints(rates.gstMilliBps)

        return ChargeBreakdown(
            stt = stt,
            exchangeTxn = exchangeTxn,
            sebiCharges = sebiCharges,
            stampDuty = stampDuty,
            gst = gst,
        )
    }
}
