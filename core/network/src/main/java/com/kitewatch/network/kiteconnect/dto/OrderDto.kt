package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTO for a single order from GET /orders.
 * All fields are nullable — the Kite API omits fields for pending/rejected orders.
 */
@JsonClass(generateAdapter = true)
data class OrderDto(
    @Json(name = "order_id") val orderId: String?,
    @Json(name = "tradingsymbol") val tradingSymbol: String?,
    @Json(name = "transaction_type") val transactionType: String?,
    @Json(name = "quantity") val quantity: Int?,
    @Json(name = "average_price") val averagePrice: Double?,
    @Json(name = "status") val status: String?,
    @Json(name = "product") val product: String?,
    @Json(name = "exchange") val exchange: String?,
    @Json(name = "fill_timestamp") val fillTimestamp: String?,
)
