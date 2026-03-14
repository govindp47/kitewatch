package com.kitewatch.domain.model

import java.time.Instant

data class PersistentAlert(
    val alertId: Long,
    val alertType: AlertType,
    val severity: AlertSeverity,
    val payload: String,
    val acknowledged: Boolean,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val resolvedBy: AlertResolution?,
)

enum class AlertType {
    HOLDINGS_MISMATCH,
    FUND_MISMATCH,
    GTT_VERIFICATION_FAILED,
    GTT_MANUAL_OVERRIDE,
    SYNC_FAILED,
    CHARGE_RATES_OUTDATED,
    SESSION_EXPIRED,
}

enum class AlertSeverity { CRITICAL, WARNING, INFO }

enum class AlertResolution { USER_ACK, AUTO_RESOLVED, SUPERSEDED }
