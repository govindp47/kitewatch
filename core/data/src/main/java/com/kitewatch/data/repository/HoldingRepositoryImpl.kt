package com.kitewatch.data.repository

import com.kitewatch.data.mapper.toDomain
import com.kitewatch.data.mapper.toEntity
import com.kitewatch.database.dao.HoldingDao
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.HoldingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HoldingRepositoryImpl
    @Inject
    constructor(
        private val holdingDao: HoldingDao,
    ) : HoldingRepository {
        override suspend fun upsert(holding: Holding) {
            holdingDao.upsert(holding.toEntity())
        }

        override suspend fun updateQuantityAndPrice(
            stockCode: String,
            quantity: Int,
            avgBuyPrice: Paisa,
            investedAmount: Paisa,
            updatedAt: Instant,
        ) {
            holdingDao.updateQuantityAndPrice(
                stockCode = stockCode,
                quantity = quantity,
                avgBuyPricePaisa = avgBuyPrice.value,
                investedAmountPaisa = investedAmount.value,
                updatedAt = updatedAt.toEpochMilli(),
            )
        }

        override fun observeAll(): Flow<List<Holding>> =
            holdingDao.observeActive().map { entities -> entities.map { it.toDomain() } }

        override suspend fun getByStockCode(stockCode: String): Holding? =
            holdingDao.getByStockCode(stockCode)?.toDomain()

        override suspend fun getAll(): List<Holding> = holdingDao.getAll().map { it.toDomain() }
    }
