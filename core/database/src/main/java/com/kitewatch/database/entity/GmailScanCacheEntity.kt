package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `gmail_scan_cache` table.
 * Tracks Gmail messages already scanned to prevent re-processing.
 * Rows with status IGNORED or REJECTED are pruned after 90 days.
 */
@Entity(
    tableName = "gmail_scan_cache",
    indices = [
        Index(value = ["gmail_message_id"], unique = true),
        Index(value = ["status"]),
    ],
)
data class GmailScanCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Gmail message ID; UNIQUE — prevents re-processing the same email */
    @ColumnInfo(name = "gmail_message_id")
    val gmailMessageId: String,
    /** One of: ADDITION, WITHDRAWAL — or null if type undetermined */
    @ColumnInfo(name = "detected_type")
    val detectedType: String? = null,
    /** Detected amount in paisa; null if parsing failed */
    @ColumnInfo(name = "detected_amount_paisa")
    val detectedAmountPaisa: Long? = null,
    /** ISO-8601 date: YYYY-MM-DD */
    @ColumnInfo(name = "email_date")
    val emailDate: String,
    @ColumnInfo(name = "email_subject")
    val emailSubject: String? = null,
    @ColumnInfo(name = "email_sender")
    val emailSender: String? = null,
    /**
     * One of: PENDING_REVIEW, CONFIRMED, REJECTED, IGNORED
     * PENDING_REVIEW = detected but awaiting user confirmation
     */
    val status: String = "PENDING_REVIEW",
    /** FK to fund_entries.id; set after user confirms this Gmail detection */
    @ColumnInfo(name = "linked_fund_entry_id")
    val linkedFundEntryId: Long? = null,
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "scanned_at")
    val scannedAt: Long = System.currentTimeMillis(),
)
