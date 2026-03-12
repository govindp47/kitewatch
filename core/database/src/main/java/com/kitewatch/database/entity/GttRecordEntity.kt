package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `gtt_records` table.
 * Local mirror of GTT (Good-Till-Triggered) orders in Zerodha.
 * Soft-deleted via is_archived = 1; never physically deleted.
 */
@Entity(
    tableName = "gtt_records",
    indices = [
        Index(value = ["stock_code"]),
        Index(value = ["zerodha_gtt_id"]),
        Index(value = ["status"]),
        Index(value = ["is_archived"]),
    ],
)
data class GttRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Zerodha's GTT ID; null when pending creation (not yet submitted) */
    @ColumnInfo(name = "zerodha_gtt_id")
    val zerodhaGttId: Long? = null,
    @ColumnInfo(name = "stock_code")
    val stockCode: String,
    /** Trigger price in paisa */
    @ColumnInfo(name = "trigger_price_paisa")
    val triggerPricePaisa: Long,
    /** Sell quantity */
    val quantity: Int,
    /**
     * One of: PENDING_CREATION, ACTIVE, TRIGGERED, CANCELLED,
     * REJECTED, EXPIRED, PENDING_UPDATE
     */
    val status: String = "PENDING_CREATION",
    /** 1 = created/managed by KiteWatch; 0 = externally created */
    @ColumnInfo(name = "is_app_managed")
    val isAppManaged: Int = 1,
    /**
     * App-calculated expected trigger price (paisa).
     * Used to detect manual overrides when syncing from Zerodha.
     */
    @ColumnInfo(name = "app_calculated_price")
    val appCalculatedPrice: Long? = null,
    /** 1 = Zerodha trigger price differs from app-calculated price */
    @ColumnInfo(name = "manual_override_detected")
    val manualOverrideDetected: Int = 0,
    /** FK to holdings.id; SET NULL when holding is deleted */
    @ColumnInfo(name = "holding_id")
    val holdingId: Long? = null,
    /** Epoch milliseconds UTC of last verified-against-Zerodha-API timestamp; null if never synced */
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null,
    /** 1 = holding fully sold or GTT completed; soft delete flag */
    @ColumnInfo(name = "is_archived")
    val isArchived: Int = 0,
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
