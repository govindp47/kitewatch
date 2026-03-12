package com.kitewatch.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Domain alert types emitted by engine subsystems and displayed to the user.
 */
enum class AlertType {
    HOLDINGS_MISMATCH,
    FUND_MISMATCH,
    GTT_CREATION_FAILED,
    GTT_VERIFICATION_FAILED,
    GTT_MANUAL_OVERRIDE,
    CHARGE_RATES_MISSING,
}

enum class AlertSeverity { CRITICAL, WARNING, INFO }

data class PersistentAlert(
    val id: Long = 0L,
    val alertType: AlertType,
    val severity: AlertSeverity,
    val payload: String,
    val acknowledged: Boolean = false,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
)

interface AlertRepository {
    suspend fun insert(alert: PersistentAlert): Long

    fun observeUnacknowledged(): Flow<List<PersistentAlert>>

    suspend fun acknowledge(
        id: Long,
        resolvedAt: Instant,
    )
}
