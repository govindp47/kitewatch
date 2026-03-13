package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTO for the Kite charges API response.
 * All fields nullable — this endpoint may not be available on all API tiers.
 * The fallback-ready design ensures missing fields do not crash deserialization.
 */
@JsonClass(generateAdapter = true)
data class ChargeRateDto(
    @Json(name = "equity") val equity: EquityChargeRateDto?,
)

@JsonClass(generateAdapter = true)
data class EquityChargeRateDto(
    @Json(name = "delivery") val delivery: DeliveryChargeRateDto?,
)

@JsonClass(generateAdapter = true)
data class DeliveryChargeRateDto(
    /** STT rate in percentage (e.g., 0.1 = 0.1%). Nullable for fallback. */
    @Json(name = "stt_ctt") val sttCtt: Double?,
    /** Exchange transaction charge in percentage. Nullable for fallback. */
    @Json(name = "exchange_turnover_charge") val exchangeTurnoverCharge: Double?,
    /** GST rate in percentage. Nullable for fallback. */
    @Json(name = "gst") val gst: Double?,
    /** SEBI charges in ₹ per crore. Nullable for fallback. */
    @Json(name = "sebi_turnover_charge") val sebiTurnoverCharge: Double?,
    /** Stamp duty in percentage. Nullable for fallback. */
    @Json(name = "stamp_duty") val stampDuty: Double?,
)
