package com.kitewatch.data.repository

import com.kitewatch.data.mapper.toChargeRateSnapshot
import com.kitewatch.data.mapper.toEntities
import com.kitewatch.database.dao.ChargeRateDao
import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.repository.ChargeRateRepository
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChargeRateRepositoryImpl
    @Inject
    constructor(
        private val chargeRateDao: ChargeRateDao,
    ) : ChargeRateRepository {
        override suspend fun saveRates(snapshot: ChargeRateSnapshot) {
            val effectiveFrom = effectiveFromDate(snapshot.fetchedAt)
            val entities = snapshot.toEntities(effectiveFrom)
            chargeRateDao.insertAll(entities)
        }

        override suspend fun getCurrentRates(): ChargeRateSnapshot? {
            val entities = chargeRateDao.getLatest()
            if (entities.isEmpty()) return null
            return runCatching { entities.toChargeRateSnapshot() }.getOrNull()
        }

        private fun effectiveFromDate(fetchedAt: Instant): String =
            DateTimeFormatter.ISO_LOCAL_DATE.format(fetchedAt.atZone(ZoneOffset.UTC))
    }
