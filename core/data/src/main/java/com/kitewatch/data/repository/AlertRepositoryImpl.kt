package com.kitewatch.data.repository

import com.kitewatch.database.dao.AlertDao
import com.kitewatch.database.entity.PersistentAlertEntity
import com.kitewatch.domain.repository.AlertRepository
import com.kitewatch.domain.repository.AlertSeverity
import com.kitewatch.domain.repository.AlertType
import com.kitewatch.domain.repository.PersistentAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepositoryImpl
    @Inject
    constructor(
        private val alertDao: AlertDao,
    ) : AlertRepository {
        override suspend fun insert(alert: PersistentAlert): Long = alertDao.insert(alert.toEntity())

        override fun observeUnacknowledged(): Flow<List<PersistentAlert>> =
            alertDao.observeUnacknowledged().map { entities -> entities.map { it.toDomainAlert() } }

        override suspend fun acknowledge(
            id: Long,
            resolvedAt: Instant,
        ) {
            alertDao.acknowledge(id = id, resolvedAt = resolvedAt.toEpochMilli())
        }

        private fun PersistentAlert.toEntity(): PersistentAlertEntity =
            PersistentAlertEntity(
                id = id,
                alertType = alertType.name,
                severity = severity.name,
                payload = payload,
                acknowledged = if (acknowledged) 1 else 0,
                createdAt = createdAt.toEpochMilli(),
                resolvedAt = resolvedAt?.toEpochMilli(),
                resolvedBy = if (acknowledged) "USER_ACK" else null,
            )

        private fun PersistentAlertEntity.toDomainAlert(): PersistentAlert =
            PersistentAlert(
                id = id,
                alertType = entityAlertTypeToDomain(alertType),
                severity = entitySeverityToDomain(severity),
                payload = payload,
                acknowledged = acknowledged == 1,
                createdAt = Instant.ofEpochMilli(createdAt),
                resolvedAt = resolvedAt?.let { Instant.ofEpochMilli(it) },
            )

        private fun entityAlertTypeToDomain(type: String): AlertType =
            when (type) {
                "HOLDINGS_MISMATCH" -> AlertType.HOLDINGS_MISMATCH
                "FUND_MISMATCH" -> AlertType.FUND_MISMATCH
                "GTT_CREATION_FAILED" -> AlertType.GTT_CREATION_FAILED
                "GTT_VERIFICATION_FAILED" -> AlertType.GTT_VERIFICATION_FAILED
                "GTT_MANUAL_OVERRIDE" -> AlertType.GTT_MANUAL_OVERRIDE
                "CHARGE_RATES_OUTDATED", "CHARGE_RATES_MISSING" -> AlertType.CHARGE_RATES_MISSING
                else -> AlertType.GTT_VERIFICATION_FAILED // safe fallback
            }

        private fun entitySeverityToDomain(severity: String): AlertSeverity =
            when (severity) {
                "CRITICAL" -> AlertSeverity.CRITICAL
                "WARNING" -> AlertSeverity.WARNING
                else -> AlertSeverity.INFO
            }
    }
