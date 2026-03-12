package com.kitewatch.domain.repository

import com.kitewatch.domain.model.Order
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface OrderRepository {
    suspend fun insert(order: Order): Long

    suspend fun insertAll(orders: List<Order>): List<Long>

    suspend fun existsByZerodhaId(zerodhaOrderId: String): Boolean

    fun observeAll(): Flow<List<Order>>

    fun observeByDateRange(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Order>>

    suspend fun getAll(): List<Order>

    suspend fun getByStockCode(stockCode: String): List<Order>
}
