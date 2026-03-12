package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `sync_event_log` table.
 * Audit trail for every background operation.
 * Rows are pruned after 180 days via maintenance task; no soft delete.
 */
@Entity(
    tableName = "sync_event_log",
    indices = [
        Index(value = ["event_type"]),
        Index(value = ["status"]),
        Index(value = ["started_at"]),
    ],
)
data class SyncEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /**
     * One of: ORDER_SYNC, FUND_RECONCILIATION, GTT_UPDATE, GTT_CREATE,
     * CHARGE_RATE_REFRESH, BACKUP, RESTORE, CSV_IMPORT, GMAIL_SCAN
     */
    @ColumnInfo(name = "event_type")
    val eventType: String,
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    /** Epoch milliseconds UTC; null while RUNNING */
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    /** One of: RUNNING, SUCCESS, FAILED, PARTIAL, SKIPPED */
    val status: String = "RUNNING",
    /** JSON blob: {"new_orders": 3, "stocks_affected": ["INFY", "TCS"]} */
    val details: String? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    /** WorkManager unique work name for correlation */
    @ColumnInfo(name = "worker_tag")
    val workerTag: String? = null,
)
