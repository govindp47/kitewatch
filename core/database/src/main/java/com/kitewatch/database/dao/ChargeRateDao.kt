package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.ChargeRateEntity

@Dao
interface ChargeRateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rates: List<ChargeRateEntity>)

    /**
     * Returns the most recent rate set — all rows where effective_from equals the global maximum.
     * Historical rates (earlier effective_from) are excluded.
     */
    @Query(
        """
        SELECT * FROM charge_rates
        WHERE effective_from = (SELECT MAX(effective_from) FROM charge_rates)
        """,
    )
    suspend fun getLatest(): List<ChargeRateEntity>

    /** Prune historical rows older than the given epoch-millis cutoff. */
    @Query("DELETE FROM charge_rates WHERE fetched_at < :cutoffEpochMillis")
    suspend fun pruneOlderThan(cutoffEpochMillis: Long)

    /** Returns all charge rate rows (current and historical); used for full backup data assembly. */
    @Query("SELECT * FROM charge_rates ORDER BY effective_from DESC")
    suspend fun getAll(): List<ChargeRateEntity>

    /** Deletes all rows; used during backup restore. */
    @Query("DELETE FROM charge_rates")
    suspend fun deleteAll()
}
