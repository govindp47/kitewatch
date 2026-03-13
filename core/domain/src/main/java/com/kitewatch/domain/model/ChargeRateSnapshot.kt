package com.kitewatch.domain.model

import java.time.Instant

/**
 * Current charge rates used for computing equity delivery transaction costs.
 *
 * All percentage-based rates are stored as **milli-basis-points**
 * (1 milli-bps = 0.001 bps = 0.00001% = 1 per 10,000,000).
 * This unit provides sufficient precision for all Indian equity delivery charges.
 *
 * Flat fees are stored as [Paisa].
 *
 * Standard Zerodha equity delivery rates (as of 2024):
 * | Charge            | Rate       | Milli-bps value |
 * |-------------------|------------|-----------------|
 * | Brokerage         | ₹0         | 0               |
 * | STT (buy & sell)  | 0.1%       | 10_000          |
 * | Exchange NSE      | 0.00297%   | 297             |
 * | Exchange BSE      | 0.00375%   | 375             |
 * | GST               | 18%        | 1_800_000       |
 * | Stamp duty (buy)  | 0.015%     | 1_500           |
 */
data class ChargeRateSnapshot(
    /** Brokerage for equity delivery in milli-bps. Zerodha charges ₹0; stored as 0. */
    val brokerageDeliveryMilliBps: Int,
    /** STT on BUY side for equity delivery in milli-bps. Standard: 10_000 (0.1%). */
    val sttBuyMilliBps: Int,
    /** STT on SELL side for equity delivery in milli-bps. Standard: 10_000 (0.1%). */
    val sttSellMilliBps: Int,
    /** NSE exchange transaction charge in milli-bps. Standard: 297 (0.00297%). */
    val exchangeNseMilliBps: Int,
    /** BSE exchange transaction charge in milli-bps. Standard: 375 (0.00375%). */
    val exchangeBseMilliBps: Int,
    /** GST rate in milli-bps. Standard: 1_800_000 (18%). */
    val gstMilliBps: Int,
    /** SEBI turnover fee in Paisa per crore of turnover. Standard: Paisa(1000) = ₹10/crore. */
    val sebiChargePerCrorePaisa: Paisa,
    /** Stamp duty rate for BUY side in milli-bps. Standard: 1_500 (0.015%). */
    val stampDutyBuyMilliBps: Int,
    /** DP charges per script per SELL day, as a flat Paisa amount. e.g., Paisa(1580) = ₹15.80. */
    val dpChargesPerScriptPaisa: Paisa,
    /** Timestamp when these rates were last fetched from the remote source. */
    val fetchedAt: Instant,
)
