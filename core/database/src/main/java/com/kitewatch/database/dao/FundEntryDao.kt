package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.FundEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FundEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FundEntryEntity): Long

    /** Emits confirmed fund entries (is_confirmed=1), newest first. */
    @Query(
        """
        SELECT * FROM fund_entries
        WHERE is_confirmed = 1
        ORDER BY entry_date DESC, id DESC
        """,
    )
    fun observeConfirmed(): Flow<List<FundEntryEntity>>

    /** Returns Gmail-detected entries awaiting user review (is_confirmed=0). */
    @Query("SELECT * FROM fund_entries WHERE is_confirmed = 0 ORDER BY entry_date DESC, id DESC")
    suspend fun getPendingGmailEntries(): List<FundEntryEntity>

    /** Confirms a single pending entry by setting is_confirmed=1. */
    @Query("UPDATE fund_entries SET is_confirmed = 1 WHERE id = :id")
    suspend fun confirm(id: Long)

    /** Sum of confirmed fund amounts in paisa. Null when no confirmed entries exist. */
    @Query("SELECT SUM(amount_paisa) FROM fund_entries WHERE is_confirmed = 1")
    suspend fun getTotalConfirmedFunds(): Long?

    /** Returns all fund entries (confirmed and pending); used for full backup data assembly. */
    @Query("SELECT * FROM fund_entries ORDER BY entry_date DESC, id DESC")
    suspend fun getAll(): List<FundEntryEntity>

    /** Deletes all rows; used during backup restore. */
    @Query("DELETE FROM fund_entries")
    suspend fun deleteAll()
}
