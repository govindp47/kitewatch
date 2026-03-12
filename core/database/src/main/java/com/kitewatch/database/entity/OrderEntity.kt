package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `orders` table.
 * Immutable after insertion — no updated_at column.
 * Monetary values stored as Long (paisa). Dates stored as ISO-8601 TEXT.
 */
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["zerodha_order_id"], unique = true),
        Index(value = ["stock_code"]),
        Index(value = ["trade_date"]),
        Index(value = ["order_type"]),
    ],
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "zerodha_order_id")
    val zerodhaOrderId: String,
    @ColumnInfo(name = "stock_code")
    val stockCode: String,
    @ColumnInfo(name = "stock_name")
    val stockName: String,
    /** 'BUY' or 'SELL' */
    @ColumnInfo(name = "order_type")
    val orderType: String,
    val quantity: Int,
    /** Per-unit price in paisa */
    @ColumnInfo(name = "price_paisa")
    val pricePaisa: Long,
    /** quantity × price in paisa */
    @ColumnInfo(name = "total_value_paisa")
    val totalValuePaisa: Long,
    /** ISO-8601 date: YYYY-MM-DD */
    @ColumnInfo(name = "trade_date")
    val tradeDate: String,
    /** 'NSE' or 'BSE' */
    val exchange: String,
    @ColumnInfo(name = "settlement_id")
    val settlementId: String? = null,
    /** 'SYNC' or 'CSV_IMPORT' */
    val source: String = "SYNC",
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
