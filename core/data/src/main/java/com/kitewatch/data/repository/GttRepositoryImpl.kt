package com.kitewatch.data.repository

import com.kitewatch.data.mapper.encodeGttStatus
import com.kitewatch.data.mapper.toDomain
import com.kitewatch.data.mapper.toEntity
import com.kitewatch.database.dao.GttRecordDao
import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.repository.GttRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GttRepositoryImpl
    @Inject
    constructor(
        private val gttRecordDao: GttRecordDao,
    ) : GttRepository {
        override suspend fun upsert(record: GttRecord) {
            gttRecordDao.upsert(record.toEntity())
        }

        override suspend fun updateStatus(
            id: Long,
            status: GttStatus,
            updatedAt: Instant,
        ) {
            gttRecordDao.updateStatus(
                id = id,
                status = encodeGttStatus(status),
                updatedAt = updatedAt.toEpochMilli(),
            )
        }

        override suspend fun archive(
            id: Long,
            updatedAt: Instant,
        ) {
            gttRecordDao.archive(id = id, updatedAt = updatedAt.toEpochMilli())
        }

        override fun observeActive(): Flow<List<GttRecord>> =
            gttRecordDao.observeActive().map { entities -> entities.map { it.toDomain() } }

        override suspend fun getByStockCode(stockCode: String): GttRecord? =
            gttRecordDao.getActiveByStockCode(stockCode)?.toDomain()
    }
