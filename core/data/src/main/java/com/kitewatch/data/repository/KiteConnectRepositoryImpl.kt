package com.kitewatch.data.repository

import com.kitewatch.data.mapper.toDomain
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.SessionCredentials
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.RemoteHolding
import com.kitewatch.network.kiteconnect.KiteConnectApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KiteConnectRepositoryImpl
    @Inject
    constructor(
        private val apiService: KiteConnectApiService,
    ) : KiteConnectRepository {
        override suspend fun fetchTodaysOrders(): Result<List<Order>> =
            runCatching {
                val response = apiService.getOrders()
                val body =
                    response.body()
                        ?: return Result.failure(Exception("Empty response: ${response.code()}"))
                if (body.status != "success") {
                    return Result.failure(Exception("API error: ${body.message}"))
                }
                val dtos = body.data ?: emptyList()
                // Filter to CNC (equity delivery) orders only; non-parseable DTOs return null
                dtos.mapNotNull { it.toDomain() }
            }

        override suspend fun fetchHoldings(): Result<List<RemoteHolding>> =
            runCatching {
                val response = apiService.getHoldings()
                val body =
                    response.body()
                        ?: return Result.failure(Exception("Empty response: ${response.code()}"))
                if (body.status != "success") {
                    return Result.failure(Exception("API error: ${body.message}"))
                }
                val dtos = body.data ?: emptyList()
                dtos.mapNotNull { dto ->
                    val symbol = dto.tradingSymbol ?: return@mapNotNull null
                    val qty = dto.quantity ?: return@mapNotNull null
                    RemoteHolding(
                        tradingSymbol = symbol,
                        quantity = qty + (dto.t1Quantity ?: 0),
                        product = "CNC",
                    )
                }
            }

        override suspend fun generateSession(
            apiKey: String,
            requestToken: String,
            checksum: String,
        ): Result<SessionCredentials> =
            runCatching {
                val response = apiService.generateSession(apiKey, requestToken, checksum)
                val body =
                    response.body()
                        ?: return Result.failure(Exception("Empty response: ${response.code()}"))
                if (body.status != "success") {
                    return Result.failure(Exception("API error: ${body.message}"))
                }
                val dto = body.data ?: return Result.failure(Exception("No session data returned"))
                val accessToken =
                    dto.accessToken ?: return Result.failure(Exception("Missing access_token"))
                val userId = dto.userId ?: return Result.failure(Exception("Missing user_id"))
                val userName = dto.userName ?: userId
                SessionCredentials(
                    accessToken = accessToken,
                    userId = userId,
                    userName = userName,
                )
            }
    }
