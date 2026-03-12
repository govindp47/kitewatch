package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.PnlMonthlyCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PnlMonthlyCacheDao {
    /** Insert or replace on year_month conflict. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PnlMonthlyCacheEntity)

    /** Emits all monthly P&L rows, newest month first. Re-emits on table change. */
    @Query("SELECT * FROM pnl_monthly_cache ORDER BY year_month DESC")
    fun observeAll(): Flow<List<PnlMonthlyCacheEntity>>

    /** Prune cache rows older than the given cutoff (exclusive, 'YYYY-MM' format). */
    @Query("DELETE FROM pnl_monthly_cache WHERE year_month < :cutoffYearMonth")
    suspend fun pruneOlderThan(cutoffYearMonth: String)
}
