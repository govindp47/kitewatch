package com.kitewatch.network.gmail

import com.kitewatch.network.gmail.dto.GmailMessageDto
import com.kitewatch.network.gmail.dto.GmailMessageListDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Gmail REST API v1.
 *
 * Base URL: https://www.googleapis.com/
 *
 * The `Authorization` header (`Bearer {token}`) must be supplied by the caller
 * on every request. No interceptor is used — Gmail auth is independent of the
 * Kite Connect auth chain.
 */
interface GmailApiClient {
    /**
     * Lists messages matching [query].
     *
     * @param auth    `Bearer {google_oauth_token}`
     * @param query   Gmail search query (e.g. `from:no-reply@zerodha.com after:2024/01/01`)
     * @param max     Maximum number of messages to return (default 50, Gmail max 500).
     */
    @GET("gmail/v1/users/me/messages")
    suspend fun listMessages(
        @Header("Authorization") auth: String,
        @Query("q") query: String,
        @Query("maxResults") max: Int = 50,
    ): GmailMessageListDto

    /**
     * Fetches the full message resource for [id].
     *
     * @param auth   `Bearer {google_oauth_token}`
     * @param id     Gmail message ID from [listMessages].
     * @param format Must be `"full"` to receive header and body payload.
     */
    @GET("gmail/v1/users/me/messages/{id}")
    suspend fun getMessage(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Query("format") format: String = "full",
    ): GmailMessageDto
}
