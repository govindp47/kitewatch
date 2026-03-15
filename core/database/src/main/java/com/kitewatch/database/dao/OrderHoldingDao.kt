package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.OrderHoldingEntity

@Dao
interface OrderHoldingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: OrderHoldingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<OrderHoldingEntity>)

    /** Returns all order IDs associated with a given holding. */
    @Query("SELECT order_id FROM order_holdings WHERE holding_id = :holdingId")
    suspend fun getOrderIdsByHoldingId(holdingId: Long): List<Long>

    /** Returns all junction rows; used for full backup data assembly. */
    @Query("SELECT * FROM order_holdings")
    suspend fun getAll(): List<OrderHoldingEntity>

    /** Deletes all rows; used during backup restore. */
    @Query("DELETE FROM order_holdings")
    suspend fun deleteAll()
}
