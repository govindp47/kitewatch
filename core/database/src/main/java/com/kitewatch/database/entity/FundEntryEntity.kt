package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `fund_entries` table.
 * Represents user-initiated fund additions and withdrawals (manual or Gmail-detected).
 * amount_paisa is always positive; entry_type determines direction.
 * Never physically deleted.
 */
@Entity(
    tableName = "fund_entries",
    indices = [
        Index(value = ["entry_date"]),
        Index(value = ["entry_type"]),
        Index(value = ["gmail_message_id"]),
    ],
)
data class FundEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 'ADDITION' or 'WITHDRAWAL' */
    @ColumnInfo(name = "entry_type")
    val entryType: String,
    /** Always positive; entry_type determines direction (paisa) */
    @ColumnInfo(name = "amount_paisa")
    val amountPaisa: Long,
    /** ISO-8601 date: YYYY-MM-DD */
    @ColumnInfo(name = "entry_date")
    val entryDate: String,
    /** User-provided note, optional */
    val note: String? = null,
    /**
     * 1 = confirmed/active, 0 = pending user review (Gmail-detected, not yet confirmed).
     * Only confirmed entries contribute to the running fund balance.
     */
    @ColumnInfo(name = "is_confirmed")
    val isConfirmed: Int = 1,
    /** Gmail message ID for dedup; null for manually entered entries */
    @ColumnInfo(name = "gmail_message_id")
    val gmailMessageId: String? = null,
    /** Links to a reconciliation event if auto-generated */
    @ColumnInfo(name = "reconciliation_id")
    val reconciliationId: String? = null,
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
