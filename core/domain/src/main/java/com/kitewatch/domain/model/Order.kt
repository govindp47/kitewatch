package com.kitewatch.domain.model

import java.time.LocalDate

data class Order(
    val orderId: Long,
    val zerodhaOrderId: String,
    val stockCode: String,
    val stockName: String,
    val orderType: OrderType,
    val quantity: Int,
    val price: Paisa,
    val totalValue: Paisa,
    val tradeDate: LocalDate,
    val exchange: Exchange,
    val settlementId: String?,
    val source: OrderSource,
) {
    init {
        require(quantity > 0) { "Order quantity must be > 0, was $quantity" }
        require(price.value > 0) { "Order price must be > 0, was ${price.value}" }
    }
}

enum class OrderType { BUY, SELL }

enum class Exchange { NSE, BSE }

enum class OrderSource { SYNC, CSV_IMPORT }
