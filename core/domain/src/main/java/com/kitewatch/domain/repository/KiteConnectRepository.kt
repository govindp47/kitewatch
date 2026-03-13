package com.kitewatch.domain.repository

import com.kitewatch.domain.model.Order

/**
 * Remote holding as returned by the Kite Connect holdings API.
 * Used solely for BR-07 holdings verification before order persistence.
 *
 * @param tradingSymbol  The instrument trading symbol (e.g., "INFY", "RELIANCE")
 * @param quantity       Number of shares held at Zerodha
 * @param product        Product type: "CNC" = equity delivery, "MIS" = intraday, "NRML" = F&O
 */
data class RemoteHolding(
    val tradingSymbol: String,
    val quantity: Int,
    val product: String,
)

/**
 * Interface for the Kite Connect API remote data source.
 * Implemented in :core-network (Phase 3); injected into use cases via Hilt.
 *
 * Zero Android dependencies — all return types are pure domain types or [Result].
 */
interface KiteConnectRepository {
    /** Fetches today's executed equity delivery orders from the Kite API. */
    suspend fun fetchTodaysOrders(): Result<List<Order>>

    /** Fetches current holdings from Kite for BR-07 pre-sync holdings verification. */
    suspend fun fetchHoldings(): Result<List<RemoteHolding>>
}
