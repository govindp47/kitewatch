package com.kitewatch.data.mapper

import com.kitewatch.database.entity.GttRecordEntity
import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.model.Paisa
import java.time.Instant

fun GttRecordEntity.toDomain(): GttRecord =
    GttRecord(
        gttId = id,
        zerodhaGttId = zerodhaGttId?.toString(),
        stockCode = stockCode,
        triggerPrice = Paisa(triggerPricePaisa),
        quantity = quantity,
        status = deriveGttStatus(status, isArchived, manualOverrideDetected),
        isAppManaged = isAppManaged == 1,
        lastSyncedAt = lastSyncedAt?.let { Instant.ofEpochMilli(it) },
    )

fun GttRecord.toEntity(): GttRecordEntity =
    GttRecordEntity(
        id = gttId,
        zerodhaGttId = zerodhaGttId?.toLongOrNull(),
        stockCode = stockCode,
        triggerPricePaisa = triggerPrice.value,
        quantity = quantity,
        status = encodeGttStatus(status),
        isAppManaged = if (isAppManaged) 1 else 0,
        manualOverrideDetected = if (status == GttStatus.MANUAL_OVERRIDE_DETECTED) 1 else 0,
        isArchived = if (status == GttStatus.ARCHIVED) 1 else 0,
        lastSyncedAt = lastSyncedAt?.toEpochMilli(),
    )

/**
 * Derives the domain [GttStatus] from entity columns.
 * The archived and manual-override flags take precedence over the raw status string.
 */
private fun deriveGttStatus(
    status: String,
    isArchived: Int,
    manualOverrideDetected: Int,
): GttStatus =
    when {
        isArchived == 1 -> GttStatus.ARCHIVED
        manualOverrideDetected == 1 -> GttStatus.MANUAL_OVERRIDE_DETECTED
        else ->
            when (status) {
                "PENDING_CREATION" -> GttStatus.PENDING_CREATION
                "ACTIVE" -> GttStatus.ACTIVE
                "TRIGGERED" -> GttStatus.TRIGGERED
                "PENDING_UPDATE" -> GttStatus.PENDING_UPDATE
                // CANCELLED, REJECTED, EXPIRED — treat as archived at domain boundary
                else -> GttStatus.ARCHIVED
            }
    }

internal fun encodeGttStatus(status: GttStatus): String =
    when (status) {
        GttStatus.PENDING_CREATION -> "PENDING_CREATION"
        GttStatus.ACTIVE -> "ACTIVE"
        GttStatus.TRIGGERED -> "TRIGGERED"
        GttStatus.MANUAL_OVERRIDE_DETECTED -> "ACTIVE"
        GttStatus.PENDING_UPDATE -> "PENDING_UPDATE"
        GttStatus.ARCHIVED -> "TRIGGERED"
    }
