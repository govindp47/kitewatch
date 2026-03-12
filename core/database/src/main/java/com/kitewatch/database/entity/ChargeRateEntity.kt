package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `charge_rates` table.
 * Locally stored Zerodha charge rate table, versioned by fetch timestamp.
 * Historical rates are never deleted — preserves audit of past charge calculations.
 * rate_value encoding: BASIS_POINTS for %, PAISA_FLAT for flat fees.
 */
@Entity(
    tableName = "charge_rates",
    indices = [
        Index(value = ["rate_type"]),
        Index(value = ["effective_from"]),
        Index(value = ["rate_type", "effective_from"], unique = true),
    ],
)
data class ChargeRateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /**
     * One of: BROKERAGE_DELIVERY, STT_BUY, STT_SELL, EXCHANGE_NSE, EXCHANGE_BSE,
     * GST, SEBI, STAMP_DUTY, DP_CHARGES_PER_SCRIPT
     */
    @ColumnInfo(name = "rate_type")
    val rateType: String,
    /** Basis points (for %) or paisa (for flat fees) */
    @ColumnInfo(name = "rate_value")
    val rateValue: Int,
    /** One of: BASIS_POINTS, PAISA_FLAT, PAISA_PER_UNIT */
    @ColumnInfo(name = "rate_unit")
    val rateUnit: String,
    /** ISO-8601 date: YYYY-MM-DD */
    @ColumnInfo(name = "effective_from")
    val effectiveFrom: String,
    /** Epoch milliseconds UTC of fetch timestamp */
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
    /** 1 = current active rate; 0 = superseded historical rate */
    @ColumnInfo(name = "is_current")
    val isCurrent: Int = 1,
)
