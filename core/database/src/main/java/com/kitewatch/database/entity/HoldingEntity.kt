package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `holdings` table.
 * One row per instrument. quantity = 0 is retained for historical reference.
 * profit_target_value encoding: for PERCENTAGE type, value = basis points (500 = 5.00%).
 *                                for ABSOLUTE type, value = paisa.
 */
@Entity(
    tableName = "holdings",
    indices = [
        Index(value = ["stock_code"], unique = true),
    ],
)
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "stock_code")
    val stockCode: String,
    @ColumnInfo(name = "stock_name")
    val stockName: String,
    /** Remaining quantity; 0 when fully sold */
    val quantity: Int,
    /** Weighted average buy price in paisa */
    @ColumnInfo(name = "avg_buy_price_paisa")
    val avgBuyPricePaisa: Long,
    /** Total cost basis of remaining lots in paisa */
    @ColumnInfo(name = "invested_amount_paisa")
    val investedAmountPaisa: Long,
    /** Sum of buy-side charges for remaining lots in paisa */
    @ColumnInfo(name = "total_buy_charges_paisa")
    val totalBuyChargesPaisa: Long = 0,
    /** 'PERCENTAGE' or 'ABSOLUTE' */
    @ColumnInfo(name = "profit_target_type")
    val profitTargetType: String = "PERCENTAGE",
    /**
     * 500 = 5.00% when type is PERCENTAGE (basis points × 100).
     * paisa when type is ABSOLUTE.
     */
    @ColumnInfo(name = "profit_target_value")
    val profitTargetValue: Int = 500,
    /** Denormalised computed sell target in paisa; recalculated on holdings update */
    @ColumnInfo(name = "target_sell_price_paisa")
    val targetSellPricePaisa: Long,
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
