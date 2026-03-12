package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the `gmail_filters` table.
 * User-defined email filters for fund transaction detection.
 */
@Entity(tableName = "gmail_filters")
data class GmailFilterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** One of: SENDER, SUBJECT_CONTAINS */
    @ColumnInfo(name = "filter_type")
    val filterType: String,
    /** The filter value to match against (e.g. sender address or subject keyword) */
    @ColumnInfo(name = "filter_value")
    val filterValue: String,
    /** 1 = active; 0 = disabled by user */
    @ColumnInfo(name = "is_active")
    val isActive: Int = 1,
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
