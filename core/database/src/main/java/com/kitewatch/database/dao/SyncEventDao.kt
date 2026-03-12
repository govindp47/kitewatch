package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.SyncEventEntity

@Dao
interface SyncEventDao {
    /** Phase 1: insert a new RUNNING event; returns the generated row id. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: SyncEventEntity): Long

    /** Phase 2: finalize an existing event with its outcome. */
    @Query(
        """
        UPDATE sync_event_log
        SET completed_at = :completedAt,
            status = :status,
            details = :details,
            error_message = :errorMessage
        WHERE id = :id
        """,
    )
    suspend fun update(
        id: Long,
        completedAt: Long,
        status: String,
        details: String?,
        errorMessage: String?,
    )

    @Query(
        "SELECT * FROM sync_event_log ORDER BY started_at DESC LIMIT 20",
    )
    suspend fun getRecent(): List<SyncEventEntity>
}
