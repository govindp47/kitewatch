package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTO for a single holding from GET /portfolio/holdings.
 */
@JsonClass(generateAdapter = true)
data class HoldingDto(
    @Json(name = "tradingsymbol") val tradingSymbol: String?,
    @Json(name = "quantity") val quantity: Int?,
    @Json(name = "average_price") val averagePrice: Double?,
    @Json(name = "t1_quantity") val t1Quantity: Int?,
    @Json(name = "exchange") val exchange: String?,
    @Json(name = "isin") val isin: String?,
)
