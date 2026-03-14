package com.kitewatch.feature.orders.model

data class OrderUiModel(
    val orderId: Long,
    val date: String,
    val stockCode: String,
    val stockName: String,
    val type: String,
    val isBuy: Boolean,
    val quantity: String,
    val price: String,
    val totalValue: String,
    val charges: String,
)
