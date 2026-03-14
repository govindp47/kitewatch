package com.kitewatch.data.repository

import com.kitewatch.data.mapper.toDomain
import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.SessionCredentials
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.RemoteHolding
import com.kitewatch.domain.usecase.AppException
import com.kitewatch.network.kiteconnect.KiteConnectApiService
import com.kitewatch.network.kiteconnect.dto.GttCreateRequestDto
import com.kitewatch.network.kiteconnect.dto.GttOrderRequestDto
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

        // ── GTT Operations ────────────────────────────────────────────────────────

        override suspend fun createGtt(
            stockCode: String,
            quantity: Int,
            triggerPrice: Paisa,
        ): Result<String> =
            runCatching {
                val triggerPriceRupees = triggerPrice.value / 100.0
                val request =
                    GttCreateRequestDto(
                        type = GTT_TYPE_SINGLE,
                        tradingSymbol = stockCode,
                        exchange = EXCHANGE_NSE,
                        triggerValues = listOf(triggerPriceRupees),
                        lastPrice = triggerPriceRupees,
                        orders =
                            listOf(
                                GttOrderRequestDto(
                                    transactionType = TRANSACTION_SELL,
                                    quantity = quantity,
                                    product = PRODUCT_CNC,
                                    orderType = ORDER_TYPE_LIMIT,
                                    price = triggerPriceRupees,
                                ),
                            ),
                    )
                val response = apiService.createGttOrder(request)
                if (response.code() == HTTP_NOT_FOUND) {
                    return Result.failure(
                        AppException(AppError.NetworkError.HttpError(HTTP_NOT_FOUND, "Not found")),
                    )
                }
                val body =
                    response.body()
                        ?: return Result.failure(Exception("Empty response: ${response.code()}"))
                if (body.status != "success") {
                    return Result.failure(
                        AppException(AppError.NetworkError.HttpError(response.code(), body.message ?: "API error")),
                    )
                }
                val triggerId =
                    body.data?.triggerId
                        ?: return Result.failure(Exception("Missing trigger_id in createGtt response"))
                triggerId.toString()
            }

        override suspend fun updateGtt(
            zerodhaGttId: String,
            quantity: Int,
            triggerPrice: Paisa,
        ): Result<Unit> =
            runCatching {
                val gttId =
                    zerodhaGttId.toIntOrNull()
                        ?: return Result.failure(Exception("Invalid GTT id: $zerodhaGttId"))
                val triggerPriceRupees = triggerPrice.value / 100.0
                val request =
                    GttCreateRequestDto(
                        type = GTT_TYPE_SINGLE,
                        tradingSymbol = "",
                        exchange = EXCHANGE_NSE,
                        triggerValues = listOf(triggerPriceRupees),
                        lastPrice = triggerPriceRupees,
                        orders =
                            listOf(
                                GttOrderRequestDto(
                                    transactionType = TRANSACTION_SELL,
                                    quantity = quantity,
                                    product = PRODUCT_CNC,
                                    orderType = ORDER_TYPE_LIMIT,
                                    price = triggerPriceRupees,
                                ),
                            ),
                    )
                val response = apiService.updateGttOrder(gttId, request)
                if (response.code() == HTTP_NOT_FOUND) {
                    return Result.failure(
                        AppException(AppError.NetworkError.HttpError(HTTP_NOT_FOUND, "Not found")),
                    )
                }
                val body =
                    response.body()
                        ?: return Result.failure(Exception("Empty response: ${response.code()}"))
                if (body.status != "success") {
                    return Result.failure(
                        AppException(AppError.NetworkError.HttpError(response.code(), body.message ?: "API error")),
                    )
                }
            }

        override suspend fun deleteGtt(zerodhaGttId: String): Result<Unit> =
            runCatching {
                val gttId =
                    zerodhaGttId.toIntOrNull()
                        ?: return Result.failure(Exception("Invalid GTT id: $zerodhaGttId"))
                val response = apiService.deleteGttOrder(gttId)
                // 404 = GTT already absent remotely; treat as success per domain contract.
                if (response.code() == HTTP_NOT_FOUND) return Result.success(Unit)
                val body =
                    response.body()
                        ?: return Result.failure(Exception("Empty response: ${response.code()}"))
                if (body.status != "success") {
                    return Result.failure(
                        AppException(AppError.NetworkError.HttpError(response.code(), body.message ?: "API error")),
                    )
                }
            }

        private companion object {
            const val GTT_TYPE_SINGLE = "single"
            const val EXCHANGE_NSE = "NSE"
            const val TRANSACTION_SELL = "SELL"
            const val PRODUCT_CNC = "CNC"
            const val ORDER_TYPE_LIMIT = "LIMIT"
            const val HTTP_NOT_FOUND = 404
        }
    }
