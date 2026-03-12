package com.kitewatch.domain.model

import java.time.Instant

data class Holding(
    val holdingId: Long,
    val stockCode: String,
    val stockName: String,
    val quantity: Int,
    val avgBuyPrice: Paisa,
    val investedAmount: Paisa,
    val totalBuyCharges: Paisa,
    val profitTarget: ProfitTarget,
    val targetSellPrice: Paisa,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(quantity >= 0) { "Holding quantity must be >= 0, was $quantity" }
    }
}
