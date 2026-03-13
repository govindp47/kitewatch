package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTO for the POST /session/token response.
 */
@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "public_token") val publicToken: String?,
    @Json(name = "user_id") val userId: String?,
    @Json(name = "user_name") val userName: String?,
)
