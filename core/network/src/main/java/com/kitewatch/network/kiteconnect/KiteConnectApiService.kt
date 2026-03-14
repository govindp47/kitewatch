package com.kitewatch.network.kiteconnect

import com.kitewatch.network.kiteconnect.dto.AuthResponseDto
import com.kitewatch.network.kiteconnect.dto.FundBalanceDto
import com.kitewatch.network.kiteconnect.dto.GttCreateRequestDto
import com.kitewatch.network.kiteconnect.dto.GttDto
import com.kitewatch.network.kiteconnect.dto.GttTriggerIdDto
import com.kitewatch.network.kiteconnect.dto.HoldingDto
import com.kitewatch.network.kiteconnect.dto.OrderDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Retrofit interface for the Kite Connect REST API.
 * Base URL: https://api.kite.trade/
 *
 * All methods return Response<KiteApiResponse<T>> to allow callers to inspect
 * HTTP status codes independently of the Kite envelope status field.
 */
interface KiteConnectApiService {
    /** Fetch today's order book. */
    @GET("orders")
    suspend fun getOrders(): Response<KiteApiResponse<List<OrderDto>>>

    /** Fetch current portfolio holdings. */
    @GET("portfolio/holdings")
    suspend fun getHoldings(): Response<KiteApiResponse<List<HoldingDto>>>

    /** Fetch equity fund margins / balance. */
    @GET("user/margins")
    suspend fun getFundBalance(): Response<KiteApiResponse<FundBalanceDto>>

    /** Fetch all active GTT orders. */
    @GET("gtt")
    suspend fun getGttOrders(): Response<KiteApiResponse<List<GttDto>>>

    /** Create a new GTT order. */
    @POST("gtt")
    suspend fun createGttOrder(
        @Body request: GttCreateRequestDto,
    ): Response<KiteApiResponse<GttTriggerIdDto>>

    /** Modify an existing GTT order. */
    @PUT("gtt/{id}")
    suspend fun updateGttOrder(
        @Path("id") gttId: Int,
        @Body request: GttCreateRequestDto,
    ): Response<KiteApiResponse<GttTriggerIdDto>>

    /** Delete a GTT order. */
    @DELETE("gtt/{id}")
    suspend fun deleteGttOrder(
        @Path("id") gttId: Int,
    ): Response<KiteApiResponse<GttTriggerIdDto>>

    /**
     * Exchange request token for access token.
     * The Kite API expects form-encoded fields.
     */
    @FormUrlEncoded
    @POST("session/token")
    suspend fun generateSession(
        @Field("api_key") apiKey: String,
        @Field("request_token") requestToken: String,
        @Field("checksum") checksum: String,
    ): Response<KiteApiResponse<AuthResponseDto>>
}
