package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    /** Returns the new row id, or -1L if the zerodha_order_id already exists (IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(order: OrderEntity): Long

    /** Returns a list of row ids; -1L for any duplicate zerodha_order_id (IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(orders: List<OrderEntity>): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM orders WHERE zerodha_order_id = :zerodhaOrderId)")
    suspend fun existsByZerodhaId(zerodhaOrderId: String): Boolean

    /** Emits all orders, newest first. Re-emits on any table change. */
    @Query("SELECT * FROM orders ORDER BY trade_date DESC, id DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    /** Emits orders with trade_date in [from, to] inclusive, newest first. */
    @Query(
        """
        SELECT * FROM orders
        WHERE trade_date >= :from AND trade_date <= :to
        ORDER BY trade_date DESC, id DESC
        """,
    )
    fun observeByDateRange(
        from: String,
        to: String,
    ): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders ORDER BY trade_date DESC, id DESC")
    suspend fun getAll(): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE stock_code = :stockCode ORDER BY trade_date DESC, id DESC")
    suspend fun getByStockCode(stockCode: String): List<OrderEntity>

    @Query(
        """
        SELECT * FROM orders
        WHERE stock_code = :stockCode AND order_type = 'BUY'
        ORDER BY trade_date ASC, id ASC
        """,
    )
    suspend fun getBuyOrdersByStockCode(stockCode: String): List<OrderEntity>

    /** Deletes all rows; used during backup restore. */
    @Query("DELETE FROM orders")
    suspend fun deleteAll()
}
