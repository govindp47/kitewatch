package com.kitewatch.data.mapper

import com.kitewatch.database.entity.PersistentAlertEntity
import com.kitewatch.domain.model.AlertResolution
import com.kitewatch.domain.model.AlertSeverity
import com.kitewatch.domain.model.AlertType
import com.kitewatch.domain.model.PersistentAlert
import java.time.Instant

fun PersistentAlertEntity.toDomain(): PersistentAlert =
    PersistentAlert(
        alertId = id,
        alertType = AlertType.valueOf(alertType),
        severity = AlertSeverity.valueOf(severity),
        payload = payload,
        acknowledged = acknowledged == 1,
        createdAt = Instant.ofEpochMilli(createdAt),
        resolvedAt = resolvedAt?.let { Instant.ofEpochMilli(it) },
        resolvedBy = resolvedBy?.let { AlertResolution.valueOf(it) },
    )

fun PersistentAlert.toEntity(): PersistentAlertEntity =
    PersistentAlertEntity(
        id = alertId,
        alertType = alertType.name,
        severity = severity.name,
        payload = payload,
        acknowledged = if (acknowledged) 1 else 0,
        createdAt = createdAt.toEpochMilli(),
        resolvedAt = resolvedAt?.toEpochMilli(),
        resolvedBy = resolvedBy?.name,
    )
