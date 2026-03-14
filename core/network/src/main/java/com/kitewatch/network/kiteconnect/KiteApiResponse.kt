package com.kitewatch.network.kiteconnect

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Standard Kite Connect API envelope.
 * All responses are wrapped as: { "status": "success"|"error", "data": T, "message": "..." }
 */
@JsonClass(generateAdapter = true)
data class KiteApiResponse<T>(
    @Json(name = "status") val status: String?,
    @Json(name = "data") val data: T?,
    @Json(name = "message") val message: String?,
)
