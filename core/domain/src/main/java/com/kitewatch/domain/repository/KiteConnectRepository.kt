package com.kitewatch.domain.repository

import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.SessionCredentials

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

    /**
     * Exchanges a Kite Connect [requestToken] for a session [SessionCredentials].
     *
     * @param apiKey       The registered Kite Connect API key.
     * @param requestToken One-time token from the OAuth redirect.
     * @param checksum     SHA-256 hex of "{apiKey}{requestToken}{apiSecret}".
     */
    suspend fun generateSession(
        apiKey: String,
        requestToken: String,
        checksum: String,
    ): Result<SessionCredentials>

    // ── GTT Operations ────────────────────────────────────────────────────────

    /**
     * Creates a single-leg GTT order on Kite Connect.
     *
     * @return The Zerodha-assigned GTT trigger ID (string representation) on success,
     *         or a failure wrapping [com.kitewatch.domain.error.AppError.NetworkError.HttpError].
     */
    suspend fun createGtt(
        stockCode: String,
        quantity: Int,
        triggerPrice: Paisa,
    ): Result<String>

    /**
     * Modifies an existing GTT on Kite Connect.
     *
     * Failure with HTTP 404 means the GTT was cancelled or triggered externally.
     */
    suspend fun updateGtt(
        zerodhaGttId: String,
        quantity: Int,
        triggerPrice: Paisa,
    ): Result<Unit>

    /**
     * Deletes a GTT from Kite Connect.
     *
     * A 404 response is treated as success (GTT already absent on remote).
     */
    suspend fun deleteGtt(zerodhaGttId: String): Result<Unit>
}
