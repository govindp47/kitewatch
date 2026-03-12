package com.kitewatch.domain.repository

import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface GttRepository {
    suspend fun upsert(record: GttRecord)

    suspend fun updateStatus(
        id: Long,
        status: GttStatus,
        updatedAt: Instant,
    )

    suspend fun archive(
        id: Long,
        updatedAt: Instant,
    )

    fun observeActive(): Flow<List<GttRecord>>

    suspend fun getByStockCode(stockCode: String): GttRecord?
}
