package com.kitewatch.domain.repository

import java.time.Instant

/**
 * Audit log for background sync operations.
 * Events are written in two phases: INSERT when the operation starts (status=RUNNING),
 * then UPDATE when it completes (status=SUCCESS/FAILED/PARTIAL/SKIPPED).
 */
interface SyncEventRepository {
    /** Phase 1: insert a new RUNNING event; returns the generated row id. */
    suspend fun beginEvent(
        eventType: String,
        startedAt: Instant,
        workerTag: String? = null,
    ): Long

    /** Phase 2: finalize the event with its outcome. */
    suspend fun finishEvent(
        id: Long,
        completedAt: Instant,
        status: String,
        details: String? = null,
        errorMessage: String? = null,
    )
}
