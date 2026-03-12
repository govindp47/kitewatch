package com.kitewatch.domain.model

import java.time.Instant

data class GttRecord(
    val gttId: Long,
    val zerodhaGttId: String?,
    val stockCode: String,
    val triggerPrice: Paisa,
    val quantity: Int,
    val status: GttStatus,
    val isAppManaged: Boolean,
    val lastSyncedAt: Instant?,
)

enum class GttStatus {
    PENDING_CREATION,
    ACTIVE,
    TRIGGERED,
    MANUAL_OVERRIDE_DETECTED,
    PENDING_UPDATE,
    ARCHIVED,
}
