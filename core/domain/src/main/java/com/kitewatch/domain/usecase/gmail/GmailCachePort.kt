package com.kitewatch.domain.usecase.gmail

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Port interface that abstracts Gmail scan cache persistence from the domain layer.
 *
 * Implemented by `:core-data`'s adapter backed by `GmailScanCacheDao` so that
 * `:core-domain` does not depend on `:core-database` or Room directly.
 */
interface GmailCachePort {
    /** Emits rows with status = PENDING_REVIEW; re-emits on table change. */
    fun observePending(): Flow<List<GmailCacheEntry>>

    /** All message IDs currently stored in the cache (any status). */
    suspend fun getAllMessageIds(): Set<String>

    /**
     * Inserts a new pending detection into the cache.
     * Returns the new row id, or -1L if the [messageId] already exists (IGNORE conflict).
     */
    suspend fun insertPending(detection: GmailFundDetection): Long

    /** Loads a single cache entry by [messageId], or null if not found. */
    suspend fun getByMessageId(messageId: String): GmailCacheEntry?

    /** Updates the status of [messageId] to CONFIRMED and records the linked fund entry id. */
    suspend fun markConfirmed(
        messageId: String,
        linkedFundEntryId: Long,
    )

    /** Updates the status of [messageId] to DISMISSED (stored as REJECTED in the DB). */
    suspend fun markDismissed(messageId: String)
}

/**
 * Domain-level view of a `gmail_scan_cache` row.
 */
data class GmailCacheEntry(
    val messageId: String,
    val amountPaisa: Long,
    val date: LocalDate,
    val subject: String?,
    val status: String,
    val linkedFundEntryId: Long?,
)
