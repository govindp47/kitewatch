package com.kitewatch.network.kiteconnect

import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.SessionCredentials
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.RemoteHolding
import com.kitewatch.network.AppException
import com.kitewatch.network.kiteconnect.dto.AuthResponseDto
import com.kitewatch.network.kiteconnect.dto.HoldingDto
import com.kitewatch.network.kiteconnect.dto.OrderDto
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KiteConnectRepositoryImpl
    @Inject
    constructor(
        private val api: KiteConnectApiService,
    ) : KiteConnectRepository {
        override suspend fun fetchTodaysOrders(): Result<List<Order>> =
            unwrap(api.getOrders()) { dtos -> dtos.mapNotNull { it.toDomainOrNull() } }

        override suspend fun fetchHoldings(): Result<List<RemoteHolding>> =
            unwrap(api.getHoldings()) { dtos -> dtos.mapNotNull { it.toDomainOrNull() } }

        override suspend fun generateSession(
            apiKey: String,
            requestToken: String,
            checksum: String,
        ): Result<SessionCredentials> =
            unwrap(api.generateSession(apiKey, requestToken, checksum)) { dto -> dto.toDomain() }

        // -------------------------------------------------------------------------
        // Helpers
        // -------------------------------------------------------------------------

        private fun <R, T> unwrap(
            response: retrofit2.Response<KiteApiResponse<R>>,
            map: (R) -> T,
        ): Result<T> {
            if (!response.isSuccessful) {
                return Result.failure(
                    AppException(AppError.NetworkError.HttpError(response.code(), response.message())),
                )
            }
            val body = response.body()
            val data = body?.data
            return if (data != null) {
                Result.success(map(data))
            } else {
                Result.failure(
                    AppException(AppError.NetworkError.HttpError(response.code(), "Empty response body")),
                )
            }
        }
    }

private fun String.toOrderType(): OrderType? =
    when (uppercase()) {
        "BUY" -> OrderType.BUY
        "SELL" -> OrderType.SELL
        else -> null
    }

@Suppress("CyclomaticComplexMethod")
private fun OrderDto.toDomainOrNull(): Order? {
    val isComplete = status?.uppercase() == "COMPLETE"
    val isCnc = product?.uppercase() == "CNC"
    val id = orderId
    val symbol = tradingSymbol
    val qty = quantity?.takeIf { it > 0 }
    val price = averagePrice?.takeIf { it > 0.0 }
    val orderType = transactionType?.toOrderType()

    val statusOk = isComplete && isCnc
    val fieldsPresent = id != null && symbol != null && qty != null && price != null && orderType != null
    if (!statusOk || !fieldsPresent) return null

    val exchangeEnum = if (exchange?.uppercase() == "BSE") Exchange.BSE else Exchange.NSE
    val tradeDate =
        fillTimestamp
            ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?: LocalDate.now()
    val unitPrice = Paisa.fromRupees(price)
    return Order(
        orderId = 0L,
        zerodhaOrderId = id,
        stockCode = symbol,
        stockName = symbol,
        orderType = orderType,
        quantity = qty,
        price = unitPrice,
        totalValue = unitPrice * qty,
        tradeDate = tradeDate,
        exchange = exchangeEnum,
        settlementId = null,
        source = OrderSource.SYNC,
    )
}

private fun HoldingDto.toDomainOrNull(): RemoteHolding? =
    tradingSymbol?.let { symbol ->
        quantity?.let { qty -> RemoteHolding(tradingSymbol = symbol, quantity = qty, product = "CNC") }
    }

private fun AuthResponseDto.toDomain(): SessionCredentials =
    SessionCredentials(
        accessToken = accessToken ?: error("Missing access_token in session response"),
        userId = userId ?: error("Missing user_id in session response"),
        userName = userName ?: "",
    )
