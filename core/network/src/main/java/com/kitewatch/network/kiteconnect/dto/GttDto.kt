package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTO for a single GTT from GET /gtt.
 */
@JsonClass(generateAdapter = true)
data class GttDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "type") val type: String?,
    @Json(name = "status") val status: String?,
    @Json(name = "condition") val condition: GttConditionDto?,
    @Json(name = "orders") val orders: List<GttOrderDto>?,
)

@JsonClass(generateAdapter = true)
data class GttConditionDto(
    @Json(name = "tradingsymbol") val tradingSymbol: String?,
    @Json(name = "trigger_values") val triggerValues: List<Double>?,
    @Json(name = "exchange") val exchange: String?,
)

@JsonClass(generateAdapter = true)
data class GttOrderDto(
    @Json(name = "quantity") val quantity: Int?,
    @Json(name = "transaction_type") val transactionType: String?,
    @Json(name = "product") val product: String?,
    @Json(name = "order_type") val orderType: String?,
    @Json(name = "price") val price: Double?,
)

/** Response body for GTT create/update/delete — returns the affected GTT trigger id. */
@JsonClass(generateAdapter = true)
data class GttTriggerIdDto(
    @Json(name = "trigger_id") val triggerId: Int?,
)

/** Request body for POST /gtt and PUT /gtt/{id}. */
@JsonClass(generateAdapter = true)
data class GttCreateRequestDto(
    @Json(name = "type") val type: String,
    @Json(name = "tradingsymbol") val tradingSymbol: String,
    @Json(name = "exchange") val exchange: String,
    @Json(name = "trigger_values") val triggerValues: List<Double>,
    @Json(name = "last_price") val lastPrice: Double,
    @Json(name = "orders") val orders: List<GttOrderRequestDto>,
)

@JsonClass(generateAdapter = true)
data class GttOrderRequestDto(
    @Json(name = "transaction_type") val transactionType: String,
    @Json(name = "quantity") val quantity: Int,
    @Json(name = "product") val product: String,
    @Json(name = "order_type") val orderType: String,
    @Json(name = "price") val price: Double,
)
