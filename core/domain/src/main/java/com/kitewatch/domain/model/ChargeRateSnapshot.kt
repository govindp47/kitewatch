package com.kitewatch.domain.model

import java.time.Instant

/**
 * Current charge rates used for computing equity delivery transaction costs.
 * All percentage-based rates are stored as basis points (1 bps = 0.01%).
 * Flat fees are stored as [Paisa].
 */
data class ChargeRateSnapshot(
    /** Brokerage for equity delivery. Zerodha charges ₹0; stored as 0 bps. */
    val brokerageDeliveryBps: Int,
    /** STT on BUY side for equity delivery. Standard: 10 bps (0.1%). */
    val sttBuyBps: Int,
    /** STT on SELL side for equity delivery. Standard: 25 bps (0.025%). */
    val sttSellBps: Int,
    /** NSE exchange transaction charge in bps. ~2.97 bps. */
    val exchangeNseBps: Int,
    /** BSE exchange transaction charge in bps. ~3 bps. */
    val exchangeBseBps: Int,
    /** GST rate in bps. 1800 bps = 18%. */
    val gstBps: Int,
    /** SEBI turnover fee in Paisa per crore of turnover. Standard: Paisa(1000) = ₹10/crore. */
    val sebiChargePerCrorePaisa: Paisa,
    /** Stamp duty rate for BUY side in bps. ~1.5 bps. */
    val stampDutyBps: Int,
    /** DP charges per script per SELL day, as a flat Paisa amount. e.g., Paisa(1580) = ₹15.80. */
    val dpChargesPerScriptPaisa: Paisa,
    /** Timestamp when these rates were last fetched from the remote source. */
    val fetchedAt: Instant,
)
