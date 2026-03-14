package com.kitewatch.data.repository

import com.kitewatch.database.dao.SyncEventDao
import com.kitewatch.database.entity.SyncEventEntity
import com.kitewatch.domain.repository.SyncEventRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEventRepositoryImpl
    @Inject
    constructor(
        private val syncEventDao: SyncEventDao,
    ) : SyncEventRepository {
        override suspend fun beginEvent(
            eventType: String,
            startedAt: Instant,
            workerTag: String?,
        ): Long =
            syncEventDao.insert(
                SyncEventEntity(
                    eventType = eventType,
                    startedAt = startedAt.toEpochMilli(),
                    status = "RUNNING",
                    workerTag = workerTag,
                ),
            )

        override suspend fun finishEvent(
            id: Long,
            completedAt: Instant,
            status: String,
            details: String?,
            errorMessage: String?,
        ) {
            syncEventDao.update(
                id = id,
                completedAt = completedAt.toEpochMilli(),
                status = status,
                details = details,
                errorMessage = errorMessage,
            )
        }
    }
