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
}
