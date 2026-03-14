package com.kitewatch.data.repository

import com.kitewatch.data.mapper.toDomain
import com.kitewatch.data.mapper.toEntity
import com.kitewatch.database.dao.OrderDao
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl
    @Inject
    constructor(
        private val orderDao: OrderDao,
    ) : OrderRepository {
        override suspend fun insert(order: Order): Long = orderDao.insert(order.toEntity())

        override suspend fun insertAll(orders: List<Order>): List<Long> =
            orderDao.insertAll(orders.map { it.toEntity() })

        override suspend fun existsByZerodhaId(zerodhaOrderId: String): Boolean =
            orderDao.existsByZerodhaId(zerodhaOrderId)

        override fun observeAll(): Flow<List<Order>> =
            orderDao.observeAll().map { entities -> entities.map { it.toDomain() } }

        override fun observeByDateRange(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<Order>> =
            orderDao
                .observeByDateRange(from.toString(), to.toString())
                .map { entities -> entities.map { it.toDomain() } }

        override suspend fun getAll(): List<Order> = orderDao.getAll().map { it.toDomain() }

        override suspend fun getByStockCode(stockCode: String): List<Order> =
            orderDao.getByStockCode(stockCode).map { it.toDomain() }
    }
