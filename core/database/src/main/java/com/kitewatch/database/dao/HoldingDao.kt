package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.HoldingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldingDao {
    /** Insert or replace on stock_code conflict. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(holding: HoldingEntity): Long

    @Query(
        """
        UPDATE holdings
        SET quantity = :quantity,
            avg_buy_price_paisa = :avgBuyPricePaisa,
            invested_amount_paisa = :investedAmountPaisa,
            updated_at = :updatedAt
        WHERE stock_code = :stockCode
        """,
    )
    suspend fun updateQuantityAndPrice(
        stockCode: String,
        quantity: Int,
        avgBuyPricePaisa: Long,
        investedAmountPaisa: Long,
        updatedAt: Long,
    )

    /** Emits holdings with quantity > 0, ordered by stock_code. Re-emits on any table change. */
    @Query("SELECT * FROM holdings WHERE quantity > 0 ORDER BY stock_code ASC")
    fun observeActive(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings WHERE stock_code = :stockCode LIMIT 1")
    suspend fun getByStockCode(stockCode: String): HoldingEntity?

    @Query("SELECT * FROM holdings ORDER BY stock_code ASC")
    suspend fun getAll(): List<HoldingEntity>
}
