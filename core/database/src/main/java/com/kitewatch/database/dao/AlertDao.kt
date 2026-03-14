package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.PersistentAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alert: PersistentAlertEntity): Long

    /** Emits unacknowledged, unresolved alerts ordered by severity then newest first. */
    @Query(
        """
        SELECT * FROM persistent_alerts
        WHERE acknowledged = 0 AND resolved_at IS NULL
        ORDER BY
            CASE severity WHEN 'CRITICAL' THEN 1 WHEN 'WARNING' THEN 2 ELSE 3 END,
            created_at DESC
        """,
    )
    fun observeUnacknowledged(): Flow<List<PersistentAlertEntity>>

    @Query(
        """
        UPDATE persistent_alerts
        SET acknowledged = 1, resolved_at = :resolvedAt, resolved_by = 'USER_ACK'
        WHERE id = :id
        """,
    )
    suspend fun acknowledge(
        id: Long,
        resolvedAt: Long,
    )

    /** Returns all alert rows; used for full backup data assembly. */
    @Query("SELECT * FROM persistent_alerts ORDER BY created_at DESC")
    suspend fun getAll(): List<PersistentAlertEntity>
}
