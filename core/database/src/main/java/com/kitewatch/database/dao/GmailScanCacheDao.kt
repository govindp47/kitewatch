package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.GmailScanCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GmailScanCacheDao {
    /** Returns the new row id, or -1L if the gmail_message_id already exists (IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: GmailScanCacheEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM gmail_scan_cache WHERE gmail_message_id = :messageId)")
    suspend fun exists(messageId: String): Boolean

    /** Emits rows with status = 'PENDING_REVIEW'. Re-emits on table change. */
    @Query(
        """
        SELECT * FROM gmail_scan_cache
        WHERE status = 'PENDING_REVIEW'
        ORDER BY scanned_at DESC
        """,
    )
    fun observePending(): Flow<List<GmailScanCacheEntity>>

    /** Returns a single row by Gmail message ID, or null if not found. */
    @Query("SELECT * FROM gmail_scan_cache WHERE gmail_message_id = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): GmailScanCacheEntity?

    /** Updates status and optionally the linked fund entry ID for a given message. */
    @Query(
        """
        UPDATE gmail_scan_cache
        SET status = :status, linked_fund_entry_id = :linkedFundEntryId
        WHERE gmail_message_id = :messageId
        """,
    )
    suspend fun updateStatus(
        messageId: String,
        status: String,
        linkedFundEntryId: Long? = null,
    )

    /** Returns all scan cache rows; used for full backup data assembly. */
    @Query("SELECT * FROM gmail_scan_cache ORDER BY scanned_at DESC")
    suspend fun getAll(): List<GmailScanCacheEntity>

    /** Deletes all rows; used during backup restore. */
    @Query("DELETE FROM gmail_scan_cache")
    suspend fun deleteAll()
}
