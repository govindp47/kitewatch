package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `persistent_alerts` table.
 * Alerts that survive app restarts and require user acknowledgment or auto-resolution.
 * Soft-deleted via resolved_at; pruned after 90 days via maintenance task.
 */
@Entity(
    tableName = "persistent_alerts",
    indices = [
        Index(value = ["acknowledged"]),
        Index(value = ["alert_type"]),
    ],
)
data class PersistentAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /**
     * One of: HOLDINGS_MISMATCH, FUND_MISMATCH, GTT_VERIFICATION_FAILED,
     * GTT_MANUAL_OVERRIDE, SYNC_FAILED, CHARGE_RATES_OUTDATED, SESSION_EXPIRED
     */
    @ColumnInfo(name = "alert_type")
    val alertType: String,
    /** One of: CRITICAL, WARNING, INFO */
    val severity: String,
    /** JSON blob with alert-specific structured data */
    val payload: String,
    /** 0 = unacknowledged; 1 = acknowledged by user */
    val acknowledged: Int = 0,
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    /** Epoch milliseconds UTC; null = still active */
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
    /** One of: USER_ACK, AUTO_RESOLVED, SUPERSEDED — null when unresolved */
    @ColumnInfo(name = "resolved_by")
    val resolvedBy: String? = null,
)
