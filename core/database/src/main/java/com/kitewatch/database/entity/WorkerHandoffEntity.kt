package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `worker_handoff` table.
 * Transient data transfer between chained WorkManager workers.
 * Rows with consumed=1 are pruned after 7 days via maintenance task.
 */
@Entity(
    tableName = "worker_handoff",
    indices = [
        Index(value = ["worker_tag"]),
    ],
)
data class WorkerHandoffEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** WorkManager unique work name that should consume this payload */
    @ColumnInfo(name = "worker_tag")
    val workerTag: String,
    /** JSON payload: {"affected_stock_codes": ["INFY", "TCS"]} */
    val payload: String,
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    /** 0 = pending; 1 = already read and processed by the target worker */
    val consumed: Int = 0,
)
