package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.GmailFilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GmailFilterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(filter: GmailFilterEntity): Long

    /** Emits active filters (is_active=1). Re-emits on table change. */
    @Query("SELECT * FROM gmail_filters WHERE is_active = 1 ORDER BY created_at ASC")
    fun observeActive(): Flow<List<GmailFilterEntity>>

    @Delete
    suspend fun delete(filter: GmailFilterEntity)

    /** Returns all filter rows (active and inactive); used for full backup data assembly. */
    @Query("SELECT * FROM gmail_filters ORDER BY created_at ASC")
    suspend fun getAll(): List<GmailFilterEntity>
}
