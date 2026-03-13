package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTO for GET /user/margins response.
 * The Kite API returns: { equity: { available: { live_balance, opening_balance }, net }, commodity: {...} }
 * We map the equity segment only.
 */
@JsonClass(generateAdapter = true)
data class FundBalanceDto(
    @Json(name = "equity") val equity: FundSegmentDto?,
)

@JsonClass(generateAdapter = true)
data class FundSegmentDto(
    @Json(name = "available") val available: FundAvailableDto?,
    @Json(name = "net") val net: Double?,
)

@JsonClass(generateAdapter = true)
data class FundAvailableDto(
    @Json(name = "live_balance") val liveBalance: Double?,
    @Json(name = "opening_balance") val openingBalance: Double?,
)
