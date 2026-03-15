package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.GttRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GttRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: GttRecordEntity): Long

    @Query(
        """
        UPDATE gtt_records
        SET status = :status, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        updatedAt: Long,
    )

    /** Soft-deletes a GTT record by setting is_archived=1. */
    @Query("UPDATE gtt_records SET is_archived = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun archive(
        id: Long,
        updatedAt: Long,
    )

    /** Emits active (non-archived) GTT records, ordered by stock_code. Re-emits on table change. */
    @Query("SELECT * FROM gtt_records WHERE is_archived = 0 ORDER BY stock_code ASC")
    fun observeActive(): Flow<List<GttRecordEntity>>

    @Query(
        """
        SELECT * FROM gtt_records
        WHERE stock_code = :stockCode AND is_archived = 0
        LIMIT 1
        """,
    )
    suspend fun getActiveByStockCode(stockCode: String): GttRecordEntity?

    /** Returns all GTT records including archived; used for full backup data assembly. */
    @Query("SELECT * FROM gtt_records ORDER BY stock_code ASC, id ASC")
    suspend fun getAll(): List<GttRecordEntity>

    /** Deletes all rows; used during backup restore. */
    @Query("DELETE FROM gtt_records")
    suspend fun deleteAll()
}
