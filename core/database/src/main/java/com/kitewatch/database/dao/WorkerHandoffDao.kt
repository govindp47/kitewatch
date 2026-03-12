package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.WorkerHandoffEntity

@Dao
interface WorkerHandoffDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(handoff: WorkerHandoffEntity): Long

    /** Returns the oldest unconsumed handoff for the given worker tag, or null. */
    @Query(
        """
        SELECT * FROM worker_handoff
        WHERE worker_tag = :workerTag AND consumed = 0
        ORDER BY created_at ASC
        LIMIT 1
        """,
    )
    suspend fun getPending(workerTag: String): WorkerHandoffEntity?

    @Query("UPDATE worker_handoff SET consumed = 1 WHERE id = :id")
    suspend fun markConsumed(id: Long)
}
