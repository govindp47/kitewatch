package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `pnl_monthly_cache` table.
 * Pre-aggregated P&L data per month for fast Portfolio screen rendering.
 * Incrementally updated: only the row for the affected month is recalculated on new order sync.
 * Full recalculation triggered only on CSV import or restore.
 */
@Entity(
    tableName = "pnl_monthly_cache",
    indices = [
        Index(value = ["year_month"], unique = true),
    ],
)
data class PnlMonthlyCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 'YYYY-MM' format; UNIQUE — one row per calendar month */
    @ColumnInfo(name = "year_month")
    val yearMonth: String,
    /** Total sell proceeds for the month (paisa) */
    @ColumnInfo(name = "total_sell_value_paisa")
    val totalSellValuePaisa: Long = 0,
    /** Cost basis of sold positions in this month (paisa) */
    @ColumnInfo(name = "total_buy_cost_sold_paisa")
    val totalBuyCostSoldPaisa: Long = 0,
    /** Buy-side charges for sold positions (paisa) */
    @ColumnInfo(name = "total_buy_charges_paisa")
    val totalBuyChargesPaisa: Long = 0,
    /** Sell-side charges for this month (paisa) */
    @ColumnInfo(name = "total_sell_charges_paisa")
    val totalSellChargesPaisa: Long = 0,
    /** sell_value - buy_cost - buy_charges - sell_charges (paisa) */
    @ColumnInfo(name = "realized_pnl_paisa")
    val realizedPnlPaisa: Long = 0,
    /** Total buy value for period including unsold positions (paisa) */
    @ColumnInfo(name = "invested_value_paisa")
    val investedValuePaisa: Long = 0,
    @ColumnInfo(name = "order_count")
    val orderCount: Int = 0,
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: Long = System.currentTimeMillis(),
)
