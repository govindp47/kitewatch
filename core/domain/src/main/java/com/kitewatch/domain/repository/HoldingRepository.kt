package com.kitewatch.domain.repository

import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Paisa
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface HoldingRepository {
    suspend fun upsert(holding: Holding)

    suspend fun updateQuantityAndPrice(
        stockCode: String,
        quantity: Int,
        avgBuyPrice: Paisa,
        investedAmount: Paisa,
        updatedAt: Instant,
    )

    fun observeAll(): Flow<List<Holding>>

    suspend fun getByStockCode(stockCode: String): Holding?

    suspend fun getAll(): List<Holding>
}
